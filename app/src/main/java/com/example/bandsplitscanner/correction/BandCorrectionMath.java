package com.example.bandsplitscanner.correction;

import android.graphics.PointF;

import com.example.bandsplitscanner.model.BoundaryLine;
import com.example.bandsplitscanner.model.PageCorners;

public class BandCorrectionMath {

    public static float estimateAspectRatio(PageCorners corners) {
        float topWidth = distance(corners.topLeft, corners.topRight);
        float bottomWidth = distance(corners.bottomLeft, corners.bottomRight);
        float leftHeight = distance(corners.topLeft, corners.bottomLeft);
        float rightHeight = distance(corners.topRight, corners.bottomRight);

        float estimatedWidth = (topWidth + bottomWidth) / 2f;
        float estimatedHeight = (leftHeight + rightHeight) / 2f;

        if (estimatedHeight <= 0f) {
            return 1f;
        }

        float ratio = estimatedWidth / estimatedHeight;
        return clamp(ratio, 0.2f, 5.0f);
    }

    public static boolean isValidBand(BoundaryLine left, BoundaryLine right) {
        float area = polygonArea(
                left.inputTop,
                right.inputTop,
                right.inputBottom,
                left.inputBottom
        );

        return Math.abs(area) > 1f;
    }

    public static float polygonArea(PointF a, PointF b, PointF c, PointF d) {
        return 0.5f * (
                a.x * b.y - a.y * b.x +
                        b.x * c.y - b.y * c.x +
                        c.x * d.y - c.y * d.x +
                        d.x * a.y - d.y * a.x
        );
    }

    public static PointF lerp(PointF a, PointF b, float t) {
        return new PointF(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t
        );
    }

    public static float distance(PointF a, PointF b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}