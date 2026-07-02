package com.example.bandsplitscanner.correction;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.example.bandsplitscanner.model.BoundaryLine;
import com.example.bandsplitscanner.model.OutputSettings;

import java.util.List;

public class PerspectiveBandRenderer implements BandRenderer {

    @Override
    public Bitmap render(
            Bitmap source,
            List<BoundaryLine> boundaries,
            OutputSettings settings
    ) {
        Bitmap result = Bitmap.createBitmap(
                settings.outputWidth,
                settings.outputHeight,
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(result);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);

        for (int i = 0; i < boundaries.size() - 1; i++) {
            BoundaryLine left = boundaries.get(i);
            BoundaryLine right = boundaries.get(i + 1);

            float leftOutputX = left.outputX * settings.outputWidth;
            float rightOutputX = right.outputX * settings.outputWidth;

            if (!BandCorrectionMath.isValidBand(left, right)) {
                drawErrorBand(canvas, leftOutputX, rightOutputX, settings.outputHeight);
                continue;
            }

            float[] src = new float[]{
                    left.inputTop.x, left.inputTop.y,
                    right.inputTop.x, right.inputTop.y,
                    right.inputBottom.x, right.inputBottom.y,
                    left.inputBottom.x, left.inputBottom.y
            };

            float[] dst = new float[]{
                    leftOutputX, 0f,
                    rightOutputX, 0f,
                    rightOutputX, settings.outputHeight,
                    leftOutputX, settings.outputHeight
            };

            Matrix matrix = new Matrix();
            boolean ok = matrix.setPolyToPoly(src, 0, dst, 0, 4);

            if (ok) {
                canvas.save();
                canvas.clipRect(
                        leftOutputX - 1f,
                        0f,
                        rightOutputX + 1f,
                        settings.outputHeight
                );
                canvas.drawBitmap(source, matrix, paint);
                canvas.restore();
            } else {
                drawErrorBand(canvas, leftOutputX, rightOutputX, settings.outputHeight);
            }
        }

        return result;
    }

    private void drawErrorBand(Canvas canvas, float leftX, float rightX, int height) {
        Paint paint = new Paint();
        paint.setColor(Color.MAGENTA);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(leftX, 0f, rightX, height, paint);
    }
}