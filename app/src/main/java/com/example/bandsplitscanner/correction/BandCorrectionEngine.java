package com.example.bandsplitscanner.correction;

import android.graphics.Bitmap;
import android.graphics.PointF;

import com.example.bandsplitscanner.model.BoundaryLine;
import com.example.bandsplitscanner.model.BoundaryPair;
import com.example.bandsplitscanner.model.OutputSettings;
import com.example.bandsplitscanner.model.PageCorners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BandCorrectionEngine {

    private final BandRenderer renderer;

    public BandCorrectionEngine(BandRenderer renderer) {
        this.renderer = renderer;
    }

    public Bitmap createResult(
            Bitmap source,
            PageCorners corners,
            List<BoundaryPair> boundaryPairs,
            int outputWidth
    ) {
        float aspectRatio = BandCorrectionMath.estimateAspectRatio(corners);
        int outputHeight = Math.max(1, Math.round(outputWidth / aspectRatio));

        OutputSettings settings = new OutputSettings(outputWidth, outputHeight);
        List<BoundaryLine> boundaries = createBoundaryLines(corners, boundaryPairs);

        return renderer.render(source, boundaries, settings);
    }

    public Bitmap createFixedThreeBandResult(
            Bitmap source,
            PageCorners corners,
            int outputWidth
    ) {
        return createResult(
                source,
                corners,
                createDefaultBoundaryPairs(corners),
                outputWidth
        );
    }

    private List<BoundaryLine> createBoundaryLines(
            PageCorners corners,
            List<BoundaryPair> boundaryPairs
    ) {
        List<BoundaryLine> lines = new ArrayList<>();

        lines.add(new BoundaryLine(
                0f,
                new PointF(corners.topLeft.x, corners.topLeft.y),
                new PointF(corners.bottomLeft.x, corners.bottomLeft.y)
        ));

        List<BoundaryPair> sortedPairs = new ArrayList<>();
        if (boundaryPairs != null) {
            for (BoundaryPair pair : boundaryPairs) {
                sortedPairs.add(pair.copy());
            }
        }

        Collections.sort(sortedPairs, new Comparator<BoundaryPair>() {
            @Override
            public int compare(BoundaryPair a, BoundaryPair b) {
                return Float.compare(a.outputX, b.outputX);
            }
        });

        for (BoundaryPair pair : sortedPairs) {
            float outputX = BandCorrectionMath.clamp(pair.outputX, 0f, 1f);

            if (outputX <= 0f || outputX >= 1f) {
                continue;
            }

            lines.add(new BoundaryLine(
                    outputX,
                    new PointF(pair.inputTop.x, pair.inputTop.y),
                    new PointF(pair.inputBottom.x, pair.inputBottom.y)
            ));
        }

        lines.add(new BoundaryLine(
                1f,
                new PointF(corners.topRight.x, corners.topRight.y),
                new PointF(corners.bottomRight.x, corners.bottomRight.y)
        ));

        return lines;
    }

    public static List<BoundaryPair> createDefaultBoundaryPairs(PageCorners corners) {
        List<BoundaryPair> pairs = new ArrayList<>();

        float[] outputXs = new float[]{1f / 3f, 2f / 3f};

        for (int i = 0; i < outputXs.length; i++) {
            float t = outputXs[i];

            PointF inputTop = BandCorrectionMath.lerp(corners.topLeft, corners.topRight, t);
            PointF inputBottom = BandCorrectionMath.lerp(corners.bottomLeft, corners.bottomRight, t);

            pairs.add(new BoundaryPair(
                    i + 1,
                    t,
                    inputTop,
                    inputBottom
            ));
        }

        return pairs;
    }
}