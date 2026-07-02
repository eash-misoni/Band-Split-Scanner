package com.example.bandsplitscanner.correction;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;

import com.example.bandsplitscanner.model.BoundaryLine;
import com.example.bandsplitscanner.model.OutputSettings;

import java.util.Arrays;
import java.util.List;

public class ScanlineBandRenderer implements BandRenderer {

    @Override
    public Bitmap render(
            Bitmap source,
            List<BoundaryLine> boundaries,
            OutputSettings settings
    ) {
        int outputWidth = settings.outputWidth;
        int outputHeight = settings.outputHeight;

        Bitmap result = Bitmap.createBitmap(
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888
        );

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        int[] sourcePixels = new int[sourceWidth * sourceHeight];
        source.getPixels(sourcePixels, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight);

        int[] outputPixels = new int[outputWidth * outputHeight];
        Arrays.fill(outputPixels, Color.WHITE);

        for (int i = 0; i < boundaries.size() - 1; i++) {
            BoundaryLine left = boundaries.get(i);
            BoundaryLine right = boundaries.get(i + 1);

            int xStart = Math.round(left.outputX * outputWidth);
            int xEnd = Math.round(right.outputX * outputWidth);

            xStart = BandCorrectionMath.clamp(xStart, 0, outputWidth);
            xEnd = BandCorrectionMath.clamp(xEnd, 0, outputWidth);

            if (i == boundaries.size() - 2) {
                xEnd = outputWidth;
            }

            if (xEnd <= xStart || !BandCorrectionMath.isValidBand(left, right)) {
                fillBand(outputPixels, outputWidth, outputHeight, xStart, xEnd, Color.MAGENTA);
                continue;
            }

            float leftOutputX = left.outputX * outputWidth;
            float rightOutputX = right.outputX * outputWidth;
            float bandWidth = rightOutputX - leftOutputX;

            if (bandWidth <= 0f) {
                fillBand(outputPixels, outputWidth, outputHeight, xStart, xEnd, Color.MAGENTA);
                continue;
            }

            for (int y = 0; y < outputHeight; y++) {
                float v = outputHeight == 1
                        ? 0f
                        : y / (float) (outputHeight - 1);

                PointF leftPoint = BandCorrectionMath.lerp(left.inputTop, left.inputBottom, v);
                PointF rightPoint = BandCorrectionMath.lerp(right.inputTop, right.inputBottom, v);

                for (int x = xStart; x < xEnd; x++) {
                    float u = ((x + 0.5f) - leftOutputX) / bandWidth;
                    u = BandCorrectionMath.clamp(u, 0f, 1f);

                    PointF sourcePoint = BandCorrectionMath.lerp(leftPoint, rightPoint, u);

                    int color = sampleBilinear(
                            sourcePixels,
                            sourceWidth,
                            sourceHeight,
                            sourcePoint.x,
                            sourcePoint.y
                    );

                    outputPixels[y * outputWidth + x] = color;
                }
            }
        }

        result.setPixels(outputPixels, 0, outputWidth, 0, 0, outputWidth, outputHeight);
        return result;
    }

    private int sampleBilinear(
            int[] pixels,
            int width,
            int height,
            float x,
            float y
    ) {
        x = BandCorrectionMath.clamp(x, 0f, width - 1f);
        y = BandCorrectionMath.clamp(y, 0f, height - 1f);

        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = Math.min(x0 + 1, width - 1);
        int y1 = Math.min(y0 + 1, height - 1);

        float tx = x - x0;
        float ty = y - y0;

        int c00 = pixels[y0 * width + x0];
        int c10 = pixels[y0 * width + x1];
        int c01 = pixels[y1 * width + x0];
        int c11 = pixels[y1 * width + x1];

        int a = bilerp(Color.alpha(c00), Color.alpha(c10), Color.alpha(c01), Color.alpha(c11), tx, ty);
        int r = bilerp(Color.red(c00), Color.red(c10), Color.red(c01), Color.red(c11), tx, ty);
        int g = bilerp(Color.green(c00), Color.green(c10), Color.green(c01), Color.green(c11), tx, ty);
        int b = bilerp(Color.blue(c00), Color.blue(c10), Color.blue(c01), Color.blue(c11), tx, ty);

        return Color.argb(a, r, g, b);
    }

    private int bilerp(
            int c00,
            int c10,
            int c01,
            int c11,
            float tx,
            float ty
    ) {
        float top = c00 + (c10 - c00) * tx;
        float bottom = c01 + (c11 - c01) * tx;
        return Math.round(top + (bottom - top) * ty);
    }

    private void fillBand(
            int[] pixels,
            int width,
            int height,
            int xStart,
            int xEnd,
            int color
    ) {
        xStart = BandCorrectionMath.clamp(xStart, 0, width);
        xEnd = BandCorrectionMath.clamp(xEnd, 0, width);

        for (int y = 0; y < height; y++) {
            for (int x = xStart; x < xEnd; x++) {
                pixels[y * width + x] = color;
            }
        }
    }
}