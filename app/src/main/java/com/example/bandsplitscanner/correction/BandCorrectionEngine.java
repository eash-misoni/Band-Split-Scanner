package com.example.bandsplitscanner.correction;

import android.graphics.Bitmap;
import android.graphics.PointF;

import com.example.bandsplitscanner.model.BoundaryLine;
import com.example.bandsplitscanner.model.OutputSettings;
import com.example.bandsplitscanner.model.PageCorners;

import java.util.ArrayList;
import java.util.List;

public class BandCorrectionEngine {

    private final BandRenderer renderer;

    public BandCorrectionEngine(BandRenderer renderer) {
        this.renderer = renderer;
    }

    public Bitmap createFixedThreeBandResult(
            Bitmap source,
            PageCorners corners,
            int outputWidth
    ) {
        float aspectRatio = BandCorrectionMath.estimateAspectRatio(corners);
        int outputHeight = Math.max(1, Math.round(outputWidth / aspectRatio));

        OutputSettings settings = new OutputSettings(outputWidth, outputHeight);
        List<BoundaryLine> boundaries = createFixedBoundaries(corners);

        return renderer.render(source, boundaries, settings);
    }

    private List<BoundaryLine> createFixedBoundaries(PageCorners corners) {
        List<BoundaryLine> list = new ArrayList<>();

        float[] xs = new float[]{0f, 1f / 3f, 2f / 3f, 1f};

        for (float t : xs) {
            PointF inputTop = BandCorrectionMath.lerp(corners.topLeft, corners.topRight, t);
            PointF inputBottom = BandCorrectionMath.lerp(corners.bottomLeft, corners.bottomRight, t);
            list.add(new BoundaryLine(t, inputTop, inputBottom));
        }

        return list;
    }
}