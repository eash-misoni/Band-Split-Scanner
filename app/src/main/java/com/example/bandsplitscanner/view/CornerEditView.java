package com.example.bandsplitscanner.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

import com.example.bandsplitscanner.model.PageCorners;

public class CornerEditView extends View {

    private static final float HIT_RADIUS = 60f;

    private final Bitmap bitmap;

    private final Matrix imageToViewMatrix = new Matrix();
    private final Matrix viewToImageMatrix = new Matrix();

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private PageCorners corners;
    private int activeCornerIndex = -1;

    public CornerEditView(Context context, Bitmap bitmap) {
        super(context);
        this.bitmap = bitmap;

        imagePaint.setFilterBitmap(true);

        linePaint.setColor(0xFFFFCC00);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);

        pointPaint.setColor(0xFF00AAFF);
        pointPaint.setStyle(Paint.Style.FILL);

        activePointPaint.setColor(0xFFFF4444);
        activePointPaint.setStyle(Paint.Style.FILL);

        initDefaultCorners();
    }

    private void initDefaultCorners() {
        float w = bitmap.getWidth();
        float h = bitmap.getHeight();

        float marginX = w * 0.1f;
        float marginY = h * 0.1f;

        corners = new PageCorners(
                new PointF(marginX, marginY),
                new PointF(w - marginX, marginY),
                new PointF(w - marginX, h - marginY),
                new PointF(marginX, h - marginY)
        );
    }

    public PageCorners getPageCorners() {
        return new PageCorners(
                new PointF(corners.topLeft.x, corners.topLeft.y),
                new PointF(corners.topRight.x, corners.topRight.y),
                new PointF(corners.bottomRight.x, corners.bottomRight.y),
                new PointF(corners.bottomLeft.x, corners.bottomLeft.y)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        updateImageMatrix();

        canvas.drawBitmap(bitmap, imageToViewMatrix, imagePaint);

        float[] points = getCornerPointsInView();

        drawCornerLines(canvas, points);
        drawCornerPoints(canvas, points);
    }

    private void updateImageMatrix() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float scale = Math.min(viewWidth / bitmapWidth, viewHeight / bitmapHeight);

        float displayedWidth = bitmapWidth * scale;
        float displayedHeight = bitmapHeight * scale;

        float dx = (viewWidth - displayedWidth) / 2f;
        float dy = (viewHeight - displayedHeight) / 2f;

        imageToViewMatrix.reset();
        imageToViewMatrix.postScale(scale, scale);
        imageToViewMatrix.postTranslate(dx, dy);

        imageToViewMatrix.invert(viewToImageMatrix);
    }

    private float[] getCornerPointsInView() {
        float[] points = new float[]{
                corners.topLeft.x, corners.topLeft.y,
                corners.topRight.x, corners.topRight.y,
                corners.bottomRight.x, corners.bottomRight.y,
                corners.bottomLeft.x, corners.bottomLeft.y
        };

        imageToViewMatrix.mapPoints(points);
        return points;
    }

    private void drawCornerLines(Canvas canvas, float[] points) {
        Path path = new Path();

        path.moveTo(points[0], points[1]);
        path.lineTo(points[2], points[3]);
        path.lineTo(points[4], points[5]);
        path.lineTo(points[6], points[7]);
        path.close();

        canvas.drawPath(path, linePaint);
    }

    private void drawCornerPoints(Canvas canvas, float[] points) {
        for (int i = 0; i < 4; i++) {
            float x = points[i * 2];
            float y = points[i * 2 + 1];

            Paint paint = i == activeCornerIndex ? activePointPaint : pointPaint;
            canvas.drawCircle(x, y, 18f, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        updateImageMatrix();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeCornerIndex = findTouchedCorner(event.getX(), event.getY());
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (activeCornerIndex != -1) {
                    moveActiveCorner(event.getX(), event.getY());
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeCornerIndex = -1;
                invalidate();
                return true;

            default:
                return true;
        }
    }

    private int findTouchedCorner(float viewX, float viewY) {
        float[] points = getCornerPointsInView();

        for (int i = 0; i < 4; i++) {
            float x = points[i * 2];
            float y = points[i * 2 + 1];

            float dx = viewX - x;
            float dy = viewY - y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance <= HIT_RADIUS) {
                return i;
            }
        }

        return -1;
    }

    private void moveActiveCorner(float viewX, float viewY) {
        float[] imagePoint = new float[]{viewX, viewY};
        viewToImageMatrix.mapPoints(imagePoint);

        float imageX = clamp(imagePoint[0], 0f, bitmap.getWidth());
        float imageY = clamp(imagePoint[1], 0f, bitmap.getHeight());

        PointF target = getCornerByIndex(activeCornerIndex);
        if (target == null) {
            return;
        }

        target.x = imageX;
        target.y = imageY;
    }

    private PointF getCornerByIndex(int index) {
        switch (index) {
            case 0:
                return corners.topLeft;
            case 1:
                return corners.topRight;
            case 2:
                return corners.bottomRight;
            case 3:
                return corners.bottomLeft;
            default:
                return null;
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}