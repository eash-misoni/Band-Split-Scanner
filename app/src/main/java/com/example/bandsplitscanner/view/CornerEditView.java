package com.example.bandsplitscanner.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.example.bandsplitscanner.model.BoundaryMarker;
import com.example.bandsplitscanner.model.BoundaryPair;

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
    private static final int DRAG_LEFT_BOUNDARY = 5;
    private static final int DRAG_RIGHT_BOUNDARY = 6;
    private static final int DRAG_PAN = 7;

    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 5f;
    private static final float MIN_VISIBLE_IMAGE_DP = 48f;

    private final Bitmap bitmap;
    private final float minVisibleImagePx;

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

    private final ScaleGestureDetector scaleGestureDetector;

    private float zoomScale = 1f;
    private float panX = 0f;
    private float panY = 0f;

    private boolean isScaling = false;

    private PointF lastViewTouchPoint = null;

    public CornerEditView(Context context, Bitmap bitmap) {
        super(context);
        this.bitmap = bitmap;
        this.minVisibleImagePx = MIN_VISIBLE_IMAGE_DP * getResources().getDisplayMetrics().density;

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

        scaleGestureDetector = new ScaleGestureDetector(
                context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(
                            ScaleGestureDetector detector
                    ) {
                        isScaling = true;

                        // 1本指でラインを動かしている途中に
                        // 2本目の指が置かれた場合、編集操作を中断する。
                        clearDragState();

                        return true;
                    }

                    @Override
                    public boolean onScale(
                            ScaleGestureDetector detector
                    ) {
                        updateImageMatrix();

                        float focusX = detector.getFocusX();
                        float focusY = detector.getFocusY();

                        /*
                         * 拡大前に、ピンチ中心が画像上のどの点を
                         * 指しているか取得する。
                         */
                        PointF focusInImage =
                                mapViewPointToImage(
                                        focusX,
                                        focusY
                                );

                        float newZoomScale = clamp(
                                zoomScale
                                        * detector.getScaleFactor(),
                                MIN_ZOOM,
                                MAX_ZOOM
                        );

                        if (newZoomScale == zoomScale) {
                            return true;
                        }

                        zoomScale = newZoomScale;

                        /*
                         * いったん新しい倍率で行列を作る。
                         */
                        updateImageMatrix();

                        /*
                         * 拡大前に指していた画像上の点が、
                         * 拡大後に画面上のどこへ移動したか調べる。
                         */
                        PointF focusAfterScale =
                                mapImagePointToView(
                                        focusInImage
                                );

                        /*
                         * その点がピンチ中心に残るように
                         * パン量を補正する。
                         */
                        panX += focusX - focusAfterScale.x;
                        panY += focusY - focusAfterScale.y;

                        constrainPan();
                        updateImageMatrix();
                        invalidate();

                        return true;
                    }

                    @Override
                    public void onScaleEnd(
                            ScaleGestureDetector detector
                    ) {
                        isScaling = false;

                        constrainPan();
                        updateImageMatrix();
                        invalidate();
                    }
                }
        );

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

        drawBandFrameLines(canvas);
        drawSplitLines(canvas);
        drawCornerPoints(canvas, points);
    }

    private void updateImageMatrix() {
        if (bitmap == null
                || getWidth() == 0
                || getHeight() == 0) {
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        /*
         * 画像全体がView内に収まる基準倍率。
         */
        float baseScale = Math.min(
                viewWidth / bitmapWidth,
                viewHeight / bitmapHeight
        );

        float totalScale =
                baseScale * zoomScale;

        float displayedWidth =
                bitmapWidth * totalScale;

        float displayedHeight =
                bitmapHeight * totalScale;

        /*
         * fit-center位置にパン量を追加する。
         */
        float dx =
                (viewWidth - displayedWidth) / 2f
                        + panX;

        float dy =
                (viewHeight - displayedHeight) / 2f
                        + panY;

        imageToViewMatrix.reset();
        imageToViewMatrix.postScale(
                totalScale,
                totalScale
        );
        imageToViewMatrix.postTranslate(
                dx,
                dy
        );

        imageToViewMatrix.invert(
                viewToImageMatrix
        );
    }

    private void constrainPan() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float baseScale = Math.min(
                viewWidth / bitmapWidth,
                viewHeight / bitmapHeight
        );

        float totalScale = baseScale * zoomScale;

        float displayedWidth = bitmapWidth * totalScale;
        float displayedHeight = bitmapHeight * totalScale;

        float baseLeft = (viewWidth - displayedWidth) / 2f;
        float baseTop = (viewHeight - displayedHeight) / 2f;

        /*
         * 通常は48dpを最低可視量とする。
         * ただし、画像またはView自体が48dpより小さい場合は、
         * 実際に表示可能な長さを上限とする。
         */
        float requiredVisibleWidth = Math.min(
                minVisibleImagePx,
                Math.min(displayedWidth, viewWidth)
        );

        float requiredVisibleHeight = Math.min(
                minVisibleImagePx,
                Math.min(displayedHeight, viewHeight)
        );

        /*
         * 表示画像の右端が requiredVisibleWidth より左へ行かず、
         * 左端が viewWidth - requiredVisibleWidth より右へ行かない
         * 範囲にpanXを制限する。
         */
        float minPanX =
                requiredVisibleWidth
                        - displayedWidth
                        - baseLeft;

        float maxPanX =
                viewWidth
                        - requiredVisibleWidth
                        - baseLeft;

        /*
         * 縦方向も同様に、画像が最低 requiredVisibleHeight だけ
         * View内へ残る範囲に制限する。
         */
        float minPanY =
                requiredVisibleHeight
                        - displayedHeight
                        - baseTop;

        float maxPanY =
                viewHeight
                        - requiredVisibleHeight
                        - baseTop;

        panX = clamp(panX, minPanX, maxPanX);
        panY = clamp(panY, minPanY, maxPanY);
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

    private void drawBandFrameLines(Canvas canvas) {
        PointF topLeft = mapImagePointToView(corners.topLeft);
        PointF topRight = mapImagePointToView(corners.topRight);
        PointF bottomLeft = mapImagePointToView(corners.bottomLeft);
        PointF bottomRight = mapImagePointToView(corners.bottomRight);

        drawTopEndpointConnections(canvas, topLeft, topRight);
        drawBottomEndpointConnections(canvas, bottomLeft, bottomRight);

        canvas.drawLine(
                topLeft.x,
                topLeft.y,
                bottomLeft.x,
                bottomLeft.y,
                linePaint
        );

        canvas.drawLine(
                topRight.x,
                topRight.y,
                bottomRight.x,
                bottomRight.y,
                linePaint
        );
    }

    private void drawTopEndpointConnections(
            Canvas canvas,
            PointF topLeft,
            PointF topRight
    ) {
        Path path = new Path();

        path.moveTo(topLeft.x, topLeft.y);

        for (BoundaryPair pair : boundaryPairs) {
            PointF point = mapImagePointToView(pair.inputTop);
            path.lineTo(point.x, point.y);
        }

        path.lineTo(topRight.x, topRight.y);

        canvas.drawPath(path, linePaint);
    }

    private void drawBottomEndpointConnections(
            Canvas canvas,
            PointF bottomLeft,
            PointF bottomRight
    ) {
        Path path = new Path();

        path.moveTo(bottomLeft.x, bottomLeft.y);

        for (BoundaryPair pair : boundaryPairs) {
            PointF point = mapImagePointToView(pair.inputBottom);
            path.lineTo(point.x, point.y);
        }

        path.lineTo(bottomRight.x, bottomRight.y);

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

        scaleGestureDetector.onTouchEvent(event);

        /*
         * 2本目の指が置かれた時点で、
         * 進行中の1本指操作を解除する。
         */
        if (event.getActionMasked()
                == MotionEvent.ACTION_POINTER_DOWN) {
            clearDragState();
        }

        /*
         * 2本指操作中はズームだけを処理する。
         */
        if (scaleGestureDetector.isInProgress()
                || isScaling
                || event.getPointerCount() > 1) {
            invalidate();
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handleActionDown(
                        event.getX(),
                        event.getY()
                );

                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                handleActionMove(
                        event.getX(),
                        event.getY()
                );

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

        if (findTouchedSideBoundary(viewX, viewY)) {
            lastImageTouchPoint = mapViewPointToImage(viewX, viewY);
            return;
        }

        if (findTouchedSplitLine(viewX, viewY)) {
            lastImageTouchPoint = mapViewPointToImage(viewX, viewY);
            return;
        }

        /*
         * 操作点・左右境界・分割ラインの
         * いずれにも当たらなかった場合はパン。
         */
        dragMode = DRAG_PAN;

        lastViewTouchPoint = new PointF(
                viewX,
                viewY
        );
    }

    private void handleActionMove(
            float viewX,
            float viewY
    ) {
        if (dragMode == DRAG_CORNER
                && activeCornerIndex != -1) {
            moveActiveCorner(viewX, viewY);
            return;
        }

        if (dragMode == DRAG_LEFT_BOUNDARY
                || dragMode == DRAG_RIGHT_BOUNDARY) {
            moveActiveSideBoundary(viewX, viewY);
            return;
        }

        if (dragMode == DRAG_PAN) {
            moveViewport(viewX, viewY);
            return;
        }

        if (activeBoundaryIndex < 0
                || activeBoundaryIndex
                >= boundaryPairs.size()) {
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
        lastViewTouchPoint = null;
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

    private boolean findTouchedSideBoundary(float viewX, float viewY) {
        PointF topLeft = mapImagePointToView(corners.topLeft);
        PointF bottomLeft = mapImagePointToView(corners.bottomLeft);

        float leftDistance = pointToSegmentDistance(
                viewX,
                viewY,
                topLeft.x,
                topLeft.y,
                bottomLeft.x,
                bottomLeft.y
        );

        if (leftDistance <= LINE_HIT_RADIUS) {
            dragMode = DRAG_LEFT_BOUNDARY;
            return true;
        }

        PointF topRight = mapImagePointToView(corners.topRight);
        PointF bottomRight = mapImagePointToView(corners.bottomRight);

        float rightDistance = pointToSegmentDistance(
                viewX,
                viewY,
                topRight.x,
                topRight.y,
                bottomRight.x,
                bottomRight.y
        );

        if (rightDistance <= LINE_HIT_RADIUS) {
            dragMode = DRAG_RIGHT_BOUNDARY;
            return true;
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

        movePointPair(
                pair.inputTop,
                pair.inputBottom,
                dx,
                dy
        );

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
    }

    private void movePointPair(
            PointF first,
            PointF second,
            float dx,
            float dy
    ) {
        float minDx = Math.max(
                -first.x,
                -second.x
        );

        float maxDx = Math.min(
                bitmap.getWidth() - first.x,
                bitmap.getWidth() - second.x
        );

        float minDy = Math.max(
                -first.y,
                -second.y
        );

        float maxDy = Math.min(
                bitmap.getHeight() - first.y,
                bitmap.getHeight() - second.y
        );

        float actualDx = clamp(dx, minDx, maxDx);
        float actualDy = clamp(dy, minDy, maxDy);

        first.x += actualDx;
        first.y += actualDy;

        second.x += actualDx;
        second.y += actualDy;
    }

    private void moveActiveSideBoundary(float viewX, float viewY) {
        if (lastImageTouchPoint == null) {
            lastImageTouchPoint = mapViewPointToImage(viewX, viewY);
            return;
        }

        PointF currentImagePoint = mapViewPointToImage(viewX, viewY);

        float dx = currentImagePoint.x - lastImageTouchPoint.x;
        float dy = currentImagePoint.y - lastImageTouchPoint.y;

        PointF top;
        PointF bottom;

        if (dragMode == DRAG_LEFT_BOUNDARY) {
            top = corners.topLeft;
            bottom = corners.bottomLeft;
        } else {
            top = corners.topRight;
            bottom = corners.bottomRight;
        }

        movePointPair(top, bottom, dx, dy);

        lastImageTouchPoint = currentImagePoint;
    }

    private void moveViewport(
            float viewX,
            float viewY
    ) {
        if (lastViewTouchPoint == null) {
            lastViewTouchPoint =
                    new PointF(viewX, viewY);

            return;
        }

        float dx =
                viewX - lastViewTouchPoint.x;

        float dy =
                viewY - lastViewTouchPoint.y;

        panX += dx;
        panY += dy;

        constrainPan();
        updateImageMatrix();

        lastViewTouchPoint.x = viewX;
        lastViewTouchPoint.y = viewY;
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

    private void drawSplitLines(Canvas canvas) {
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
    public void applyOutputXFromWidthBar(BoundaryMarker marker) {
        long boundaryId = marker.boundaryId;
        float outputX = marker.outputX;
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
}