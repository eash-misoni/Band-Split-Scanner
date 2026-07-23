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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PREVIEW_OUTPUT_WIDTH = 1200;
    private static final int SAVE_JPEG_QUALITY = 95;
    private static final String SAVE_DIRECTORY_NAME = "BandSplitScanner";
    private static final String SAVE_FILENAME_PATTERN = "yyyyMMdd_HHmmss";

    private FrameLayout imageContainer;
    private Button selectButton;
    private Button correctButton;
    private Button backButton;
    private Button saveButton;
    private SwitchCompat outputBoundarySwitch;

    private Bitmap sourceBitmap;
    private Bitmap correctedBitmap;

    private CornerEditView cornerEditView;
    private WidthDistributionBarView widthDistributionBarView;
    private ResultPreviewView resultPreviewView;

    private float outputAspectRatio = Float.NaN;
    private boolean showingResult = false;
    private boolean showOutputBoundaryLines = true;
    private long nextBoundaryId = 1L;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri == null) {
                            return;
                        }

                        try (InputStream inputStream =
                                     getContentResolver().openInputStream(uri)) {
                            sourceBitmap = BitmapFactory.decodeStream(inputStream);
                            correctedBitmap = null;

                            if (sourceBitmap == null) {
                                Toast.makeText(
                                        this,
                                        "画像を読み込めませんでした",
                                        Toast.LENGTH_SHORT
                                ).show();
                                return;
                            }

                            nextBoundaryId = 1L;
                            outputAspectRatio = Float.NaN;
                            cornerEditView = new CornerEditView(this, sourceBitmap);
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
                            saveCorrectedBitmapToMediaStore();
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
        if (!showingResult || correctedBitmap == null) {
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

        saveCorrectedBitmapToMediaStore();
    }

    private void saveCorrectedBitmapToMediaStore() {
        Bitmap bitmapToSave = correctedBitmap;
        if (bitmapToSave == null) {
            return;
        }

        saveButton.setEnabled(false);

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

            String savedMessage;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                savedMessage =
                        "Pictures/"
                                + SAVE_DIRECTORY_NAME
                                + " に保存しました";
            } else {
                savedMessage =
                        "端末の画像ライブラリに保存しました";
            }

            Toast.makeText(
                    this,
                    savedMessage,
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception e) {
            if (imageUri != null) {
                resolver.delete(
                        imageUri,
                        null,
                        null
                );
            }

            Toast.makeText(
                    this,
                    "画像の保存に失敗しました",
                    Toast.LENGTH_SHORT
            ).show();
        } finally {
            saveButton.setEnabled(
                    showingResult && correctedBitmap != null
            );
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
