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
import com.example.bandsplitscanner.model.BoundaryPair;
import com.example.bandsplitscanner.correction.BandCorrectionEngine;

import java.util.ArrayList;
import java.util.List;

import com.example.bandsplitscanner.model.PageCorners;

public class CornerEditView extends View {

    private static final float HIT_RADIUS = 60f;
    private static final float LINE_HIT_RADIUS = 45f;

    private static final int DRAG_NONE = 0;
    private static final int DRAG_CORNER = 1;
    private static final int DRAG_SPLIT_TOP = 2;
    private static final int DRAG_SPLIT_BOTTOM = 3;
    private static final int DRAG_SPLIT_LINE = 4;

    private final Bitmap bitmap;

    private final Matrix imageToViewMatrix = new Matrix();
    private final Matrix viewToImageMatrix = new Matrix();

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint splitLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeSplitLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private PageCorners corners;
    private List<BoundaryPair> boundaryPairs = new ArrayList<>();
    private int activeCornerIndex = -1;

    private int dragMode = DRAG_NONE;
    private int activeBoundaryIndex = -1;
    private PointF lastImageTouchPoint = null;

    public CornerEditView(Context context, Bitmap bitmap) {
        super(context);
        this.bitmap = bitmap;

        imagePaint.setFilterBitmap(true);

        linePaint.setColor(0xFFFFCC00);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);

        splitLinePaint.setColor(0xFF00FF99);
        splitLinePaint.setStyle(Paint.Style.STROKE);
        splitLinePaint.setStrokeWidth(4f);

        pointPaint.setColor(0xFF00AAFF);
        pointPaint.setStyle(Paint.Style.FILL);

        activePointPaint.setColor(0xFFFF4444);
        activePointPaint.setStyle(Paint.Style.FILL);

        activeSplitLinePaint.setColor(0xFFFF4444);
        activeSplitLinePaint.setStyle(Paint.Style.STROKE);
        activeSplitLinePaint.setStrokeWidth(6f);

        initDefaultCorners();
        resetBoundaryPairsFromCorners();
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
        drawFixedSplitLines(canvas);
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
                handleActionDown(event.getX(), event.getY());
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                handleActionMove(event.getX(), event.getY());
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                clearDragState();
                invalidate();
                return true;

            default:
                return true;
        }
    }

    private void handleActionDown(float viewX, float viewY) {
        clearDragState();

        activeCornerIndex = findTouchedCorner(viewX, viewY);
        if (activeCornerIndex != -1) {
            dragMode = DRAG_CORNER;
            return;
        }

        if (findTouchedSplitEndpoint(viewX, viewY)) {
            return;
        }

        if (findTouchedSplitLine(viewX, viewY)) {
            lastImageTouchPoint = mapViewPointToImage(viewX, viewY);
        }
    }

    private void handleActionMove(float viewX, float viewY) {
        if (dragMode == DRAG_CORNER && activeCornerIndex != -1) {
            moveActiveCorner(viewX, viewY);
            return;
        }

        if (activeBoundaryIndex < 0 || activeBoundaryIndex >= boundaryPairs.size()) {
            return;
        }

        switch (dragMode) {
            case DRAG_SPLIT_TOP:
                moveActiveSplitTop(viewX, viewY);
                break;

            case DRAG_SPLIT_BOTTOM:
                moveActiveSplitBottom(viewX, viewY);
                break;

            case DRAG_SPLIT_LINE:
                moveActiveSplitLine(viewX, viewY);
                break;

            default:
                break;
        }
    }

    private void clearDragState() {
        dragMode = DRAG_NONE;
        activeCornerIndex = -1;
        activeBoundaryIndex = -1;
        lastImageTouchPoint = null;
    }

    private boolean findTouchedSplitEndpoint(float viewX, float viewY) {
        for (int i = 0; i < boundaryPairs.size(); i++) {
            BoundaryPair pair = boundaryPairs.get(i);

            PointF topInView = mapImagePointToView(pair.inputTop);
            PointF bottomInView = mapImagePointToView(pair.inputBottom);

            if (distance(viewX, viewY, topInView.x, topInView.y) <= HIT_RADIUS) {
                activeBoundaryIndex = i;
                dragMode = DRAG_SPLIT_TOP;
                return true;
            }

            if (distance(viewX, viewY, bottomInView.x, bottomInView.y) <= HIT_RADIUS) {
                activeBoundaryIndex = i;
                dragMode = DRAG_SPLIT_BOTTOM;
                return true;
            }
        }

        return false;
    }

    private boolean findTouchedSplitLine(float viewX, float viewY) {
        for (int i = 0; i < boundaryPairs.size(); i++) {
            BoundaryPair pair = boundaryPairs.get(i);

            PointF topInView = mapImagePointToView(pair.inputTop);
            PointF bottomInView = mapImagePointToView(pair.inputBottom);

            float distance = pointToSegmentDistance(
                    viewX,
                    viewY,
                    topInView.x,
                    topInView.y,
                    bottomInView.x,
                    bottomInView.y
            );

            if (distance <= LINE_HIT_RADIUS) {
                activeBoundaryIndex = i;
                dragMode = DRAG_SPLIT_LINE;
                return true;
            }
        }

        return false;
    }

    private void moveActiveSplitTop(float viewX, float viewY) {
        BoundaryPair pair = boundaryPairs.get(activeBoundaryIndex);
        PointF imagePoint = mapViewPointToImage(viewX, viewY);

        pair.inputTop.x = clamp(imagePoint.x, 0f, bitmap.getWidth());
        pair.inputTop.y = clamp(imagePoint.y, 0f, bitmap.getHeight());
    }

    private void moveActiveSplitBottom(float viewX, float viewY) {
        BoundaryPair pair = boundaryPairs.get(activeBoundaryIndex);
        PointF imagePoint = mapViewPointToImage(viewX, viewY);

        pair.inputBottom.x = clamp(imagePoint.x, 0f, bitmap.getWidth());
        pair.inputBottom.y = clamp(imagePoint.y, 0f, bitmap.getHeight());
    }

    private void moveActiveSplitLine(float viewX, float viewY) {
        if (lastImageTouchPoint == null) {
            lastImageTouchPoint = mapViewPointToImage(viewX, viewY);
            return;
        }

        PointF currentImagePoint = mapViewPointToImage(viewX, viewY);

        float dx = currentImagePoint.x - lastImageTouchPoint.x;
        float dy = currentImagePoint.y - lastImageTouchPoint.y;

        BoundaryPair pair = boundaryPairs.get(activeBoundaryIndex);

        pair.inputTop.x = clamp(pair.inputTop.x + dx, 0f, bitmap.getWidth());
        pair.inputTop.y = clamp(pair.inputTop.y + dy, 0f, bitmap.getHeight());

        pair.inputBottom.x = clamp(pair.inputBottom.x + dx, 0f, bitmap.getWidth());
        pair.inputBottom.y = clamp(pair.inputBottom.y + dy, 0f, bitmap.getHeight());

        lastImageTouchPoint = currentImagePoint;
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

        resetBoundaryPairsFromCorners();
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

    private void drawFixedSplitLines(Canvas canvas) {
        for (int i = 0; i < boundaryPairs.size(); i++) {
            BoundaryPair pair = boundaryPairs.get(i);

            PointF topInView = mapImagePointToView(pair.inputTop);
            PointF bottomInView = mapImagePointToView(pair.inputBottom);

            Paint paint = i == activeBoundaryIndex
                    ? activeSplitLinePaint
                    : splitLinePaint;

            canvas.drawLine(
                    topInView.x,
                    topInView.y,
                    bottomInView.x,
                    bottomInView.y,
                    paint
            );

            canvas.drawCircle(topInView.x, topInView.y, 12f, paint);
            canvas.drawCircle(bottomInView.x, bottomInView.y, 12f, paint);
        }
    }

    private PointF mapImagePointToView(PointF point) {
        float[] values = new float[]{point.x, point.y};
        imageToViewMatrix.mapPoints(values);
        return new PointF(values[0], values[1]);
    }

    private PointF mapViewPointToImage(float viewX, float viewY) {
        float[] values = new float[]{viewX, viewY};
        viewToImageMatrix.mapPoints(values);
        return new PointF(values[0], values[1]);
    }

    private PointF lerp(PointF a, PointF b, float t) {
        return new PointF(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t
        );
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float pointToSegmentDistance(
            float px,
            float py,
            float ax,
            float ay,
            float bx,
            float by
    ) {
        float dx = bx - ax;
        float dy = by - ay;

        if (dx == 0f && dy == 0f) {
            return distance(px, py, ax, ay);
        }

        float t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = clamp(t, 0f, 1f);

        float nearestX = ax + t * dx;
        float nearestY = ay + t * dy;

        return distance(px, py, nearestX, nearestY);
    }

    public List<BoundaryPair> getBoundaryPairs() {
        List<BoundaryPair> copied = new ArrayList<>();

        for (BoundaryPair pair : boundaryPairs) {
            copied.add(pair.copy());
        }

        return copied;
    }

    public void setBoundaryPairs(List<BoundaryPair> boundaryPairs) {
        this.boundaryPairs = new ArrayList<>();

        if (boundaryPairs != null) {
            for (BoundaryPair pair : boundaryPairs) {
                this.boundaryPairs.add(pair.copy());
            }
        }

        invalidate();
    }
    public void applyOutputXFromWidthBar(long boundaryId, float outputX) {
        BoundaryPair pair = getBoundaryPairById(boundaryId);
        if (pair != null) {
            pair.outputX = outputX;
        }
    }

    private BoundaryPair getBoundaryPairById(long boundaryId) {
        for (BoundaryPair pair : boundaryPairs) {
            if (pair.id == boundaryId) {
                return pair;
            }
        }
        return null;
    }

    private void resetBoundaryPairsFromCorners() {
        boundaryPairs = BandCorrectionEngine.createDefaultBoundaryPairs(corners);
    }
}