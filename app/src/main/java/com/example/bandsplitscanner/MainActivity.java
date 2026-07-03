package com.example.bandsplitscanner;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import com.example.bandsplitscanner.model.BoundaryPair;

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

        correctButton.setEnabled(true);
        backButton.setEnabled(false);
    }

    private void createCorrectionResult() {
        if (sourceBitmap == null || cornerEditView == null) {
            Toast.makeText(this, "先に画像を選択してください", Toast.LENGTH_SHORT).show();
            return;
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

        showResultView();
    }

    private void showResultView() {
        if (correctedBitmap == null) {
            return;
        }

        imageContainer.removeAllViews();

        ImageView resultImageView = new ImageView(this);
        resultImageView.setImageBitmap(correctedBitmap);
        resultImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        resultImageView.setAdjustViewBounds(true);

        imageContainer.addView(
                resultImageView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        correctButton.setEnabled(false);
        backButton.setEnabled(true);
    }
}