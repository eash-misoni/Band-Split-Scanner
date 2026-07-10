package com.example.bandsplitscanner;

import android.view.View;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bandsplitscanner.correction.BandCorrectionEngine;
import com.example.bandsplitscanner.correction.ScanlineBandRenderer;
import com.example.bandsplitscanner.model.PageCorners;
import com.example.bandsplitscanner.view.CornerEditView;
import com.example.bandsplitscanner.view.ResultPreviewView;
import com.example.bandsplitscanner.view.WidthDistributionBarView;
import com.example.bandsplitscanner.model.BoundaryPair;
import com.example.bandsplitscanner.model.BoundaryMarker;
import com.example.bandsplitscanner.correction.BandCorrectionMath;


import java.util.List;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private FrameLayout imageContainer;
    private Button selectButton;
    private Button correctButton;
    private Button backButton;

    private Bitmap sourceBitmap;
    private Bitmap correctedBitmap;

    private CornerEditView cornerEditView;
    private WidthDistributionBarView widthDistributionBarView;
    private ResultPreviewView resultPreviewView;

    private boolean showingResult = false;

    private long nextBoundaryId = 1L;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }

                try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                    sourceBitmap = BitmapFactory.decodeStream(inputStream);
                    correctedBitmap = null;

                    if (sourceBitmap == null) {
                        Toast.makeText(this, "画像を読み込めませんでした", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    nextBoundaryId = 1L;
                    cornerEditView = new CornerEditView(this, sourceBitmap);
                    showCornerEditView();

                } catch (Exception e) {
                    Toast.makeText(this, "画像の読み込みに失敗しました", Toast.LENGTH_SHORT).show();
                }
            });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        imageContainer = findViewById(R.id.imageContainer);
        selectButton = findViewById(R.id.selectButton);
        correctButton = findViewById(R.id.correctButton);
        backButton = findViewById(R.id.backButton);

        correctButton.setEnabled(false);
        backButton.setEnabled(false);

        selectButton.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        correctButton.setOnClickListener(v -> {
            createCorrectionResult();
        });

        backButton.setOnClickListener(v -> {
            if (cornerEditView != null) {
                showCornerEditView();
            }
        });

        widthDistributionBarView = findViewById(R.id.widthDistributionBarView);
        widthDistributionBarView.setOnBoundaryOutputChangedListener(
                new WidthDistributionBarView.OnBoundaryOutputChangedListener() {
                    @Override
                    public void onBoundaryOutputChanged(BoundaryMarker marker, boolean isFinished) {
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

        widthDistributionBarView
                .setOnBoundaryDeleteRequestedListener(
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
            widthDistributionBarView.setMarkersFromBoundaryPairs(cornerEditView.getBoundaryPairs());

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
            Toast.makeText(this, "先に画像を選択してください", Toast.LENGTH_SHORT).show();
            return false;
        }

        PageCorners corners = cornerEditView.getPageCorners();
        List<BoundaryPair> boundaryPairs = cornerEditView.getBoundaryPairs();

        BandCorrectionEngine engine = new BandCorrectionEngine(
                new ScanlineBandRenderer()
        );

        correctedBitmap = engine.createResult(
                sourceBitmap,
                corners,
                boundaryPairs,
                1200
        );

        return true;
    }

    private void showResultView() {
        if (correctedBitmap == null || cornerEditView == null) {
            return;
        }

        imageContainer.removeAllViews();

        resultPreviewView = new ResultPreviewView(this);
        resultPreviewView.setBitmap(correctedBitmap);
        resultPreviewView.setBoundaryPairs(cornerEditView.getBoundaryPairs());
        resultPreviewView.setShowOutputBoundaryLines(true);

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
        widthDistributionBarView.setMarkersFromBoundaryPairs(cornerEditView.getBoundaryPairs());

        correctButton.setEnabled(false);
        backButton.setEnabled(true);
    }

    private void regenerateResultPreview() {
        if (!generateCorrectedBitmap()) {
            return;
        }

        if (resultPreviewView != null) {
            resultPreviewView.setBitmap(correctedBitmap);
            resultPreviewView.setBoundaryPairs(cornerEditView.getBoundaryPairs());
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

        widthDistributionBarView
                .setMarkersFromBoundaryPairs(boundaryPairs);

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

        widthDistributionBarView
                .setMarkersFromBoundaryPairs(boundaryPairs);

        if (showingResult) {
            regenerateResultPreview();
        }
    }
}