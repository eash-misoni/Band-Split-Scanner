package com.example.bandsplitscanner;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bandsplitscanner.correction.BandCorrectionEngine;
import com.example.bandsplitscanner.correction.BandCorrectionMath;
import com.example.bandsplitscanner.correction.ScanlineBandRenderer;
import com.example.bandsplitscanner.model.BoundaryMarker;
import com.example.bandsplitscanner.model.BoundaryPair;
import com.example.bandsplitscanner.model.OutputSettings;
import com.example.bandsplitscanner.model.PageCorners;
import com.example.bandsplitscanner.view.CornerEditView;
import com.example.bandsplitscanner.view.ResultPreviewView;
import com.example.bandsplitscanner.view.WidthDistributionBarView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PREVIEW_OUTPUT_WIDTH = 1200;
    private static final int PREVIEW_SOURCE_MAX_EDGE = 2048;
    private static final long SAVE_MAX_OUTPUT_PIXELS = 4_000_000L;
    private static final int SAVE_MAX_OUTPUT_EDGE = 4096;
    private static final int SAVE_JPEG_QUALITY = 95;
    private static final String SAVE_DIRECTORY_NAME = "BandSplitScanner";
    private static final String SAVE_FILENAME_PATTERN = "yyyyMMdd_HHmmss";

    private FrameLayout imageContainer;
    private Button selectButton;
    private Button correctButton;
    private Button backButton;
    private Button saveButton;
    private SwitchCompat outputBoundarySwitch;

    private Uri selectedImageUri;
    private int selectedImageWidth;
    private int selectedImageHeight;
    private Bitmap sourceBitmap;
    private Bitmap correctedBitmap;

    private CornerEditView cornerEditView;
    private WidthDistributionBarView widthDistributionBarView;
    private ResultPreviewView resultPreviewView;

    private final ExecutorService saveExecutor =
            Executors.newSingleThreadExecutor();

    private float outputAspectRatio = Float.NaN;
    private boolean showingResult = false;
    private boolean showOutputBoundaryLines = true;
    private boolean saveInProgress = false;
    private long nextBoundaryId = 1L;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri == null) {
                            return;
                        }

                        try {
                            BitmapFactory.Options imageBounds =
                                    readBitmapBounds(uri);
                            int previewInSampleSize =
                                    calculatePreviewInSampleSize(
                                            imageBounds.outWidth,
                                            imageBounds.outHeight
                                    );
                            Bitmap selectedBitmap =
                                    loadBitmapFromUri(
                                            uri,
                                            previewInSampleSize
                                    );

                            selectedImageUri = uri;
                            selectedImageWidth = imageBounds.outWidth;
                            selectedImageHeight = imageBounds.outHeight;
                            sourceBitmap = selectedBitmap;
                            correctedBitmap = null;

                            nextBoundaryId = 1L;
                            outputAspectRatio = Float.NaN;
                            cornerEditView =
                                    new CornerEditView(
                                            this,
                                            sourceBitmap
                                    );
                            showCornerEditView();
                        } catch (Exception e) {
                            Toast.makeText(
                                    this,
                                    "画像の読み込みに失敗しました",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );

    private final ActivityResultLauncher<String> writeStoragePermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            saveCurrentResult();
                        } else {
                            Toast.makeText(
                                    this,
                                    "画像を保存するにはストレージ権限が必要です",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.rootLayout),
                (v, insets) -> {
                    Insets systemBars =
                            insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );
                    return insets;
                }
        );

        imageContainer = findViewById(R.id.imageContainer);
        selectButton = findViewById(R.id.selectButton);
        correctButton = findViewById(R.id.correctButton);
        backButton = findViewById(R.id.backButton);
        saveButton = findViewById(R.id.saveButton);
        outputBoundarySwitch = findViewById(R.id.outputBoundarySwitch);

        correctButton.setEnabled(false);
        backButton.setEnabled(false);
        saveButton.setEnabled(false);

        selectButton.setOnClickListener(
                v -> imagePickerLauncher.launch("image/*")
        );

        correctButton.setOnClickListener(
                v -> createCorrectionResult()
        );

        backButton.setOnClickListener(
                v -> {
                    if (cornerEditView != null) {
                        showCornerEditView();
                    }
                }
        );

        saveButton.setOnClickListener(
                v -> requestSaveCurrentResult()
        );

        outputBoundarySwitch.setChecked(showOutputBoundaryLines);
        outputBoundarySwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    showOutputBoundaryLines = isChecked;
                    if (resultPreviewView != null) {
                        resultPreviewView.setShowOutputBoundaryLines(isChecked);
                    }
                }
        );

        widthDistributionBarView =
                findViewById(R.id.widthDistributionBarView);
        widthDistributionBarView.setOnBoundaryOutputChangedListener(
                new WidthDistributionBarView.OnBoundaryOutputChangedListener() {
                    @Override
                    public void onBoundaryOutputChanged(
                            BoundaryMarker marker,
                            boolean isFinished
                    ) {
                        applyOutputXFromWidthBar(marker);
                        if (showingResult && isFinished) {
                            regenerateResultPreview();
                        }
                    }
                }
        );
        widthDistributionBarView.setOnBoundaryAddRequestedListener(
                this::addBoundaryAtOutputX
        );
        widthDistributionBarView.setOnBoundaryDeleteRequestedListener(
                this::deleteBoundary
        );
    }

    private void showCornerEditView() {
        imageContainer.removeAllViews();
        imageContainer.addView(
                cornerEditView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        resultPreviewView = null;
        showingResult = false;

        widthDistributionBarView.setVisibility(View.VISIBLE);
        widthDistributionBarView.setEnabled(true);
        widthDistributionBarView.setMarkersFromBoundaryPairs(
                cornerEditView.getBoundaryPairs()
        );

        outputBoundarySwitch.setVisibility(View.GONE);
        saveButton.setVisibility(View.GONE);
        saveButton.setEnabled(false);

        correctButton.setEnabled(true);
        backButton.setEnabled(false);
    }

    private void createCorrectionResult() {
        if (!generateCorrectedBitmap()) {
            return;
        }

        showResultView();
    }

    private boolean generateCorrectedBitmap() {
        if (sourceBitmap == null || cornerEditView == null) {
            Toast.makeText(
                    this,
                    "先に画像を選択してください",
                    Toast.LENGTH_SHORT
            ).show();
            return false;
        }

        PageCorners corners = cornerEditView.getPageCorners();
        List<BoundaryPair> boundaryPairs =
                cornerEditView.getBoundaryPairs();

        if (Float.isNaN(outputAspectRatio)) {
            outputAspectRatio =
                    BandCorrectionMath.estimateAspectRatio(corners);
        }

        int outputHeight = Math.max(
                1,
                Math.round(PREVIEW_OUTPUT_WIDTH / outputAspectRatio)
        );

        OutputSettings settings =
                new OutputSettings(
                        PREVIEW_OUTPUT_WIDTH,
                        outputHeight
                );

        BandCorrectionEngine engine =
                new BandCorrectionEngine(
                        new ScanlineBandRenderer()
                );

        correctedBitmap = engine.createResult(
                sourceBitmap,
                corners,
                boundaryPairs,
                settings
        );
        return true;
    }

    private void showResultView() {
        if (correctedBitmap == null || cornerEditView == null) {
            return;
        }

        imageContainer.removeAllViews();

        resultPreviewView =
                new ResultPreviewView(this);
        resultPreviewView.setOnOutputAspectRatioChangedListener(
                (aspectRatio, isFinished) -> {
                    outputAspectRatio = aspectRatio;
                    if (isFinished) {
                        regenerateResultPreview();
                    }
                }
        );
        resultPreviewView.setBitmap(correctedBitmap);
        resultPreviewView.setBoundaryPairs(
                cornerEditView.getBoundaryPairs()
        );
        resultPreviewView.setShowOutputBoundaryLines(
                showOutputBoundaryLines
        );

        imageContainer.addView(
                resultPreviewView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        showingResult = true;

        widthDistributionBarView.setVisibility(View.VISIBLE);
        widthDistributionBarView.setEnabled(true);
        widthDistributionBarView.setMarkersFromBoundaryPairs(
                cornerEditView.getBoundaryPairs()
        );

        outputBoundarySwitch.setChecked(showOutputBoundaryLines);
        outputBoundarySwitch.setVisibility(View.VISIBLE);

        saveButton.setVisibility(View.VISIBLE);
        saveButton.setEnabled(true);

        correctButton.setEnabled(false);
        backButton.setEnabled(true);
    }

    private void regenerateResultPreview() {
        if (!generateCorrectedBitmap()) {
            return;
        }

        if (resultPreviewView != null) {
            resultPreviewView.setBitmap(correctedBitmap);
            resultPreviewView.setBoundaryPairs(
                    cornerEditView.getBoundaryPairs()
            );
        }
    }

    private void requestSaveCurrentResult() {
        if (!showingResult
                || correctedBitmap == null
                || selectedImageUri == null
                || sourceBitmap == null
                || cornerEditView == null) {
            Toast.makeText(
                    this,
                    "保存する補正結果がありません",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED) {
            writeStoragePermissionLauncher.launch(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            );
            return;
        }

        saveCurrentResult();
    }

    private void saveCurrentResult() {
        if (saveInProgress) {
            return;
        }

        SaveRequest saveRequest =
                createSaveRequest();

        saveInProgress = true;
        saveButton.setEnabled(false);

        Toast.makeText(
                this,
                "画像を保存しています",
                Toast.LENGTH_SHORT
        ).show();

        saveExecutor.execute(
                () -> executeSave(saveRequest)
        );
    }

    private SaveRequest createSaveRequest() {
        return new SaveRequest(
                selectedImageUri,
                selectedImageWidth,
                selectedImageHeight,
                sourceBitmap.getWidth(),
                sourceBitmap.getHeight(),
                cornerEditView.getPageCorners(),
                cornerEditView.getBoundaryPairs(),
                outputAspectRatio
        );
    }

    private void executeSave(
            SaveRequest saveRequest
    ) {
        Bitmap bitmapToSave = null;

        try {
            bitmapToSave =
                    createSaveBitmap(saveRequest);
            saveBitmapToMediaStore(bitmapToSave);

            int savedWidth =
                    bitmapToSave.getWidth();
            int savedHeight =
                    bitmapToSave.getHeight();

            postSaveCompleted(
                    savedWidth,
                    savedHeight
            );
        } catch (OutOfMemoryError e) {
            postSaveFailed(
                    "保存画像を生成するためのメモリが不足しました",
                    Toast.LENGTH_LONG
            );
        } catch (Exception e) {
            postSaveFailed(
                    "画像の保存に失敗しました",
                    Toast.LENGTH_SHORT
            );
        } finally {
            if (bitmapToSave != null
                    && !bitmapToSave.isRecycled()) {
                bitmapToSave.recycle();
            }
        }
    }

    private void postSaveCompleted(
            int savedWidth,
            int savedHeight
    ) {
        runOnUiThread(
                () -> {
                    finishSaveOperation();

                    if (isFinishing()
                            || isDestroyed()) {
                        return;
                    }

                    showSaveCompletedMessage(
                            savedWidth,
                            savedHeight
                    );
                }
        );
    }

    private void postSaveFailed(
            String message,
            int duration
    ) {
        runOnUiThread(
                () -> {
                    finishSaveOperation();

                    if (isFinishing()
                            || isDestroyed()) {
                        return;
                    }

                    Toast.makeText(
                            this,
                            message,
                            duration
                    ).show();
                }
        );
    }

    private void finishSaveOperation() {
        saveInProgress = false;

        if (saveButton != null) {
            saveButton.setEnabled(
                    showingResult
                            && correctedBitmap != null
            );
        }
    }

    private Bitmap createSaveBitmap(
            SaveRequest saveRequest
    ) throws IOException {
        Bitmap saveSourceBitmap =
                loadBitmapFromUri(
                        saveRequest.imageUri,
                        1
                );

        try {
            validateSaveSourceDimensions(
                    saveSourceBitmap,
                    saveRequest.originalImageWidth,
                    saveRequest.originalImageHeight
            );

            float scaleX =
                    saveSourceBitmap.getWidth()
                            / (float) saveRequest.previewImageWidth;
            float scaleY =
                    saveSourceBitmap.getHeight()
                            / (float) saveRequest.previewImageHeight;

            PageCorners saveCorners =
                    scalePageCorners(
                            saveRequest.previewCorners,
                            scaleX,
                            scaleY
                    );
            List<BoundaryPair> saveBoundaryPairs =
                    scaleBoundaryPairs(
                            saveRequest.previewBoundaryPairs,
                            scaleX,
                            scaleY
                    );

            float saveOutputAspectRatio =
                    saveRequest.outputAspectRatio;
            if (Float.isNaN(saveOutputAspectRatio)
                    || Float.isInfinite(saveOutputAspectRatio)
                    || saveOutputAspectRatio <= 0f) {
                saveOutputAspectRatio =
                        BandCorrectionMath.estimateAspectRatio(
                                saveCorners
                        );
            }

            OutputSettings settings =
                    createSaveOutputSettings(
                            saveCorners,
                            saveOutputAspectRatio
                    );

            BandCorrectionEngine engine =
                    new BandCorrectionEngine(
                            new ScanlineBandRenderer()
                    );

            return engine.createResult(
                    saveSourceBitmap,
                    saveCorners,
                    saveBoundaryPairs,
                    settings
            );
        } finally {
            if (!saveSourceBitmap.isRecycled()) {
                saveSourceBitmap.recycle();
            }
        }
    }

    private BitmapFactory.Options readBitmapBounds(
            Uri imageUri
    ) throws IOException {
        BitmapFactory.Options options =
                new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        try (InputStream inputStream =
                     getContentResolver().openInputStream(imageUri)) {
            if (inputStream == null) {
                throw new IOException(
                        "画像の入力ストリームを開けませんでした"
                );
            }

            BitmapFactory.decodeStream(
                    inputStream,
                    null,
                    options
            );
        }

        if (options.outWidth <= 0
                || options.outHeight <= 0) {
            throw new IOException(
                    "画像サイズを取得できませんでした"
            );
        }

        return options;
    }

    private int calculatePreviewInSampleSize(
            int imageWidth,
            int imageHeight
    ) {
        int inSampleSize = 1;
        int longestEdge =
                Math.max(imageWidth, imageHeight);

        while (longestEdge / inSampleSize
                > PREVIEW_SOURCE_MAX_EDGE) {
            inSampleSize *= 2;
        }

        return inSampleSize;
    }

    private Bitmap loadBitmapFromUri(
            Uri imageUri,
            int inSampleSize
    ) throws IOException {
        BitmapFactory.Options options =
                new BitmapFactory.Options();
        options.inSampleSize =
                Math.max(1, inSampleSize);

        try (InputStream inputStream =
                     getContentResolver().openInputStream(imageUri)) {
            if (inputStream == null) {
                throw new IOException(
                        "画像の入力ストリームを開けませんでした"
                );
            }

            Bitmap bitmap =
                    BitmapFactory.decodeStream(
                            inputStream,
                            null,
                            options
                    );
            if (bitmap == null) {
                throw new IOException(
                        "画像をBitmapとして読み込めませんでした"
                );
            }

            return bitmap;
        }
    }

    private void validateSaveSourceDimensions(
            Bitmap saveSourceBitmap,
            int expectedWidth,
            int expectedHeight
    ) throws IOException {
        if (saveSourceBitmap.getWidth()
                != expectedWidth
                || saveSourceBitmap.getHeight()
                != expectedHeight) {
            throw new IOException(
                    "保存時に再読込した画像サイズが選択時と一致しません"
            );
        }
    }

    private PageCorners scalePageCorners(
            PageCorners corners,
            float scaleX,
            float scaleY
    ) {
        return new PageCorners(
                scalePoint(corners.topLeft, scaleX, scaleY),
                scalePoint(corners.topRight, scaleX, scaleY),
                scalePoint(corners.bottomRight, scaleX, scaleY),
                scalePoint(corners.bottomLeft, scaleX, scaleY)
        );
    }

    private List<BoundaryPair> scaleBoundaryPairs(
            List<BoundaryPair> boundaryPairs,
            float scaleX,
            float scaleY
    ) {
        List<BoundaryPair> scaledPairs =
                new ArrayList<>(boundaryPairs.size());

        for (BoundaryPair pair : boundaryPairs) {
            scaledPairs.add(
                    new BoundaryPair(
                            pair.id,
                            pair.outputX,
                            scalePoint(
                                    pair.inputTop,
                                    scaleX,
                                    scaleY
                            ),
                            scalePoint(
                                    pair.inputBottom,
                                    scaleX,
                                    scaleY
                            )
                    )
            );
        }

        return scaledPairs;
    }

    private PointF scalePoint(
            PointF point,
            float scaleX,
            float scaleY
    ) {
        return new PointF(
                point.x * scaleX,
                point.y * scaleY
        );
    }

    private OutputSettings createSaveOutputSettings(
            PageCorners corners,
            float aspectRatio
    ) {
        float topWidth =
                BandCorrectionMath.distance(
                        corners.topLeft,
                        corners.topRight
                );
        float bottomWidth =
                BandCorrectionMath.distance(
                        corners.bottomLeft,
                        corners.bottomRight
                );

        int outputWidth = Math.max(
                1,
                Math.round((topWidth + bottomWidth) / 2f)
        );
        int outputHeight = Math.max(
                1,
                Math.round(outputWidth / aspectRatio)
        );

        float scale = 1f;
        int longestEdge = Math.max(outputWidth, outputHeight);

        if (longestEdge > SAVE_MAX_OUTPUT_EDGE) {
            scale = Math.min(
                    scale,
                    SAVE_MAX_OUTPUT_EDGE / (float) longestEdge
            );
        }

        long outputPixels =
                (long) outputWidth * outputHeight;
        if (outputPixels > SAVE_MAX_OUTPUT_PIXELS) {
            scale = Math.min(
                    scale,
                    (float) Math.sqrt(
                            SAVE_MAX_OUTPUT_PIXELS
                                    / (double) outputPixels
                    )
            );
        }

        if (scale < 1f) {
            outputWidth = Math.max(
                    1,
                    Math.round(outputWidth * scale)
            );
            outputHeight = Math.max(
                    1,
                    Math.round(outputHeight * scale)
            );
        }

        return new OutputSettings(
                outputWidth,
                outputHeight
        );
    }

    private void saveBitmapToMediaStore(
            Bitmap bitmapToSave
    ) throws IOException {
        ContentResolver resolver = getContentResolver();
        Uri imageUri = null;

        try {
            String timestamp =
                    new SimpleDateFormat(
                            SAVE_FILENAME_PATTERN,
                            Locale.US
                    ).format(new Date());
            String displayName =
                    "BandSplitScanner_" + timestamp + ".jpg";

            ContentValues values = new ContentValues();
            values.put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    displayName
            );
            values.put(
                    MediaStore.Images.Media.MIME_TYPE,
                    "image/jpeg"
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES
                                + "/"
                                + SAVE_DIRECTORY_NAME
                );
                values.put(
                        MediaStore.Images.Media.IS_PENDING,
                        1
                );
            }

            imageUri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
            );
            if (imageUri == null) {
                throw new IOException(
                        "MediaStoreへの登録に失敗しました"
                );
            }

            try (OutputStream outputStream =
                         resolver.openOutputStream(imageUri)) {
                if (outputStream == null) {
                    throw new IOException(
                            "保存先を開けませんでした"
                    );
                }

                boolean compressed = bitmapToSave.compress(
                        Bitmap.CompressFormat.JPEG,
                        SAVE_JPEG_QUALITY,
                        outputStream
                );
                if (!compressed) {
                    throw new IOException(
                            "JPEGへの変換に失敗しました"
                    );
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues publishValues =
                        new ContentValues();
                publishValues.put(
                        MediaStore.Images.Media.IS_PENDING,
                        0
                );

                int updatedRows = resolver.update(
                        imageUri,
                        publishValues,
                        null,
                        null
                );
                if (updatedRows == 0) {
                    throw new IOException(
                            "保存画像を公開できませんでした"
                    );
                }
            }
        } catch (OutOfMemoryError e) {
            if (imageUri != null) {
                resolver.delete(
                        imageUri,
                        null,
                        null
                );
            }
            throw e;
        } catch (Exception e) {
            if (imageUri != null) {
                resolver.delete(
                        imageUri,
                        null,
                        null
                );
            }

            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException(
                    "画像の保存に失敗しました",
                    e
            );
        }
    }

    private void showSaveCompletedMessage(
            int savedWidth,
            int savedHeight
    ) {
        String saveLocation;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveLocation =
                    "Pictures/" + SAVE_DIRECTORY_NAME;
        } else {
            saveLocation =
                    "端末の画像ライブラリ";
        }

        Toast.makeText(
                this,
                saveLocation
                        + " に "
                        + savedWidth
                        + " × "
                        + savedHeight
                        + " px で保存しました",
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onDestroy() {
        saveExecutor.shutdownNow();
        super.onDestroy();
    }

    private static class SaveRequest {

        final Uri imageUri;
        final int originalImageWidth;
        final int originalImageHeight;
        final int previewImageWidth;
        final int previewImageHeight;
        final PageCorners previewCorners;
        final List<BoundaryPair> previewBoundaryPairs;
        final float outputAspectRatio;

        SaveRequest(
                Uri imageUri,
                int originalImageWidth,
                int originalImageHeight,
                int previewImageWidth,
                int previewImageHeight,
                PageCorners previewCorners,
                List<BoundaryPair> previewBoundaryPairs,
                float outputAspectRatio
        ) {
            this.imageUri = imageUri;
            this.originalImageWidth =
                    originalImageWidth;
            this.originalImageHeight =
                    originalImageHeight;
            this.previewImageWidth =
                    previewImageWidth;
            this.previewImageHeight =
                    previewImageHeight;
            this.previewCorners =
                    previewCorners;
            this.previewBoundaryPairs =
                    previewBoundaryPairs;
            this.outputAspectRatio =
                    outputAspectRatio;
        }
    }

    private void applyOutputXFromWidthBar(BoundaryMarker marker) {
        if (cornerEditView != null) {
            cornerEditView.applyOutputXFromWidthBar(marker);
        }

        if (resultPreviewView != null) {
            resultPreviewView.applyOutputXFromWidthBar(marker);
        }
    }

    private void addBoundaryAtOutputX(float outputX) {
        if (cornerEditView == null) {
            return;
        }

        PageCorners corners =
                cornerEditView.getPageCorners();
        List<BoundaryPair> boundaryPairs =
                cornerEditView.getBoundaryPairs();
        boundaryPairs.sort(
                (a, b) -> Float.compare(
                        a.outputX,
                        b.outputX
                )
        );

        BoundaryPair leftPair = null;
        BoundaryPair rightPair = null;

        for (BoundaryPair pair : boundaryPairs) {
            if (pair.outputX < outputX) {
                leftPair = pair;
            } else {
                rightPair = pair;
                break;
            }
        }

        float leftOutputX;
        PointF leftTop;
        PointF leftBottom;

        if (leftPair != null) {
            leftOutputX = leftPair.outputX;
            leftTop = leftPair.inputTop;
            leftBottom = leftPair.inputBottom;
        } else {
            leftOutputX = 0f;
            leftTop = corners.topLeft;
            leftBottom = corners.bottomLeft;
        }

        float rightOutputX;
        PointF rightTop;
        PointF rightBottom;

        if (rightPair != null) {
            rightOutputX = rightPair.outputX;
            rightTop = rightPair.inputTop;
            rightBottom = rightPair.inputBottom;
        } else {
            rightOutputX = 1f;
            rightTop = corners.topRight;
            rightBottom = corners.bottomRight;
        }

        float t =
                (outputX - leftOutputX)
                        / (rightOutputX - leftOutputX);
        t = BandCorrectionMath.clamp(t, 0f, 1f);

        BoundaryPair newPair = new BoundaryPair(
                nextBoundaryId++,
                outputX,
                BandCorrectionMath.lerp(
                        leftTop,
                        rightTop,
                        t
                ),
                BandCorrectionMath.lerp(
                        leftBottom,
                        rightBottom,
                        t
                )
        );

        boundaryPairs.add(newPair);
        boundaryPairs.sort(
                (a, b) -> Float.compare(
                        a.outputX,
                        b.outputX
                )
        );

        cornerEditView.setBoundaryPairs(boundaryPairs);
        widthDistributionBarView.setMarkersFromBoundaryPairs(
                boundaryPairs
        );

        if (showingResult) {
            regenerateResultPreview();
        }
    }

    private void deleteBoundary(long boundaryId) {
        if (cornerEditView == null) {
            return;
        }

        List<BoundaryPair> boundaryPairs =
                cornerEditView.getBoundaryPairs();
        boolean removed = false;

        for (int i = 0; i < boundaryPairs.size(); i++) {
            if (boundaryPairs.get(i).id == boundaryId) {
                boundaryPairs.remove(i);
                removed = true;
                break;
            }
        }

        if (!removed) {
            return;
        }

        cornerEditView.setBoundaryPairs(boundaryPairs);
        widthDistributionBarView.setMarkersFromBoundaryPairs(
                boundaryPairs
        );

        if (showingResult) {
            regenerateResultPreview();
        }
    }
}
