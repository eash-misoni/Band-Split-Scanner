package com.example.bandsplitscanner.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.example.bandsplitscanner.model.BoundaryMarker;
import com.example.bandsplitscanner.model.BoundaryPair;

import java.util.ArrayList;
import java.util.List;

public class ResultPreviewView extends View {

    public interface OnOutputAspectRatioChangedListener {
        void onOutputAspectRatioChanged(
                float aspectRatio,
                boolean isFinished
        );
    }

    private static final float MIN_PREVIEW_ZOOM = 0.25f;
    private static final float MAX_PREVIEW_ZOOM = 5f;
    private static final float MIN_VISIBLE_IMAGE_DP = 48f;

    private static final float MIN_OUTPUT_ASPECT_RATIO = 0.4f;
    private static final float MAX_OUTPUT_ASPECT_RATIO = 3.0f;

    private static final int OUTPUT_FRAME_EDGE_NONE = 0;
    private static final int OUTPUT_FRAME_EDGE_LEFT = 1;
    private static final int OUTPUT_FRAME_EDGE_RIGHT = 2;
    private static final float OUTPUT_FRAME_EDGE_HIT_RADIUS = 36f;

    private Bitmap bitmap;

    private final Matrix bitmapToViewMatrix = new Matrix();
    private final Matrix viewToBitmapMatrix = new Matrix();

    private final Paint imagePaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boundaryLinePaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outputFramePaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<BoundaryPair> boundaryPairs =
            new ArrayList<>();

    private final RectF draggingOutputFrameRect =
            new RectF();

    private int draggingOutputFrameEdge =
            OUTPUT_FRAME_EDGE_NONE;
    private float edgeTouchOffset = 0f;
    private float dragStartAspectRatio = 1f;

    private OnOutputAspectRatioChangedListener
            outputAspectRatioChangedListener;

    private boolean showOutputBoundaryLines = true;

    private ScaleGestureDetector scaleGestureDetector;
    private float minVisibleImagePx;

    private float previewZoomScale = 1f;
    private float previewPanX = 0f;
    private float previewPanY = 0f;

    private boolean isScaling = false;
    private PointF lastPanTouchPoint = null;

    public ResultPreviewView(Context context) {
        super(context);
        init();
    }

    public ResultPreviewView(
            Context context,
            AttributeSet attrs
    ) {
        super(context, attrs);
        init();
    }

    public ResultPreviewView(
            Context context,
            AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        imagePaint.setFilterBitmap(true);

        boundaryLinePaint.setColor(0xFFFF4444);
        boundaryLinePaint.setStyle(Paint.Style.STROKE);
        boundaryLinePaint.setStrokeWidth(5f);

        outputFramePaint.setColor(0xFF00BCD4);
        outputFramePaint.setStyle(Paint.Style.STROKE);
        outputFramePaint.setStrokeWidth(6f);

        minVisibleImagePx =
                MIN_VISIBLE_IMAGE_DP
                        * getResources()
                        .getDisplayMetrics()
                        .density;

        scaleGestureDetector =
                new ScaleGestureDetector(
                        getContext(),
                        new ScaleGestureDetector
                                .SimpleOnScaleGestureListener() {

                            @Override
                            public boolean onScaleBegin(
                                    ScaleGestureDetector detector
                            ) {
                                isScaling = true;
                                cancelSingleFingerOperationForScale();
                                return true;
                            }

                            @Override
                            public boolean onScale(
                                    ScaleGestureDetector detector
                            ) {
                                updateBitmapMatrix();

                                float focusX =
                                        detector.getFocusX();
                                float focusY =
                                        detector.getFocusY();

                                PointF focusInBitmap =
                                        mapViewPointToBitmap(
                                                focusX,
                                                focusY
                                        );

                                float newZoomScale =
                                        clamp(
                                                previewZoomScale
                                                        * detector
                                                        .getScaleFactor(),
                                                MIN_PREVIEW_ZOOM,
                                                MAX_PREVIEW_ZOOM
                                        );

                                if (newZoomScale
                                        == previewZoomScale) {
                                    return true;
                                }

                                previewZoomScale =
                                        newZoomScale;

                                updateBitmapMatrix();

                                PointF focusAfterScale =
                                        mapBitmapPointToView(
                                                focusInBitmap.x,
                                                focusInBitmap.y
                                        );

                                previewPanX +=
                                        focusX
                                                - focusAfterScale.x;
                                previewPanY +=
                                        focusY
                                                - focusAfterScale.y;

                                constrainPan();
                                updateBitmapMatrix();
                                invalidate();
                                return true;
                            }

                            @Override
                            public void onScaleEnd(
                                    ScaleGestureDetector detector
                            ) {
                                isScaling = false;
                                constrainPan();
                                updateBitmapMatrix();
                                invalidate();
                            }
                        }
                );
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;

        if (bitmap == null) {
            previewZoomScale = 1f;
            previewPanX = 0f;
            previewPanY = 0f;
        } else {
            constrainPan();
            updateBitmapMatrix();
        }

        invalidate();
    }

    public void setBoundaryPairs(
            List<BoundaryPair> boundaryPairs
    ) {
        this.boundaryPairs = new ArrayList<>();

        if (boundaryPairs != null) {
            for (BoundaryPair pair : boundaryPairs) {
                this.boundaryPairs.add(pair.copy());
            }
        }

        invalidate();
    }

    public void setOnOutputAspectRatioChangedListener(
            OnOutputAspectRatioChangedListener listener
    ) {
        this.outputAspectRatioChangedListener = listener;
    }

    public void applyOutputXFromWidthBar(
            BoundaryMarker marker
    ) {
        long boundaryId = marker.boundaryId;
        float outputX = marker.outputX;

        BoundaryPair pair =
                getBoundaryPairById(boundaryId);

        if (pair != null) {
            pair.outputX = outputX;
            invalidate();
        }
    }

    private BoundaryPair getBoundaryPairById(
            long boundaryId
    ) {
        for (BoundaryPair pair : boundaryPairs) {
            if (pair.id == boundaryId) {
                return pair;
            }
        }

        return null;
    }

    public void setShowOutputBoundaryLines(
            boolean showOutputBoundaryLines
    ) {
        this.showOutputBoundaryLines =
                showOutputBoundaryLines;
        invalidate();
    }

    @Override
    protected void onSizeChanged(
            int width,
            int height,
            int oldWidth,
            int oldHeight
    ) {
        super.onSizeChanged(
                width,
                height,
                oldWidth,
                oldHeight
        );

        constrainPan();
        updateBitmapMatrix();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap == null) {
            return;
        }

        updateBitmapMatrix();

        canvas.drawBitmap(
                bitmap,
                bitmapToViewMatrix,
                imagePaint
        );

        if (showOutputBoundaryLines) {
            drawOutputBoundaryLines(canvas);
        }

        drawOutputFrame(canvas);
    }

    private void drawOutputFrame(Canvas canvas) {
        RectF frameRect =
                getCurrentOutputFrameRect();

        float halfStroke =
                outputFramePaint.getStrokeWidth()
                        / 2f;

        RectF drawableRect =
                new RectF(frameRect);

        drawableRect.inset(
                halfStroke,
                halfStroke
        );

        canvas.drawRect(
                drawableRect,
                outputFramePaint
        );
    }

    private RectF getCurrentOutputFrameRect() {
        if (draggingOutputFrameEdge
                != OUTPUT_FRAME_EDGE_NONE) {
            return new RectF(
                    draggingOutputFrameRect
            );
        }

        return getDisplayedBitmapRect();
    }

    private RectF getDisplayedBitmapRect() {
        if (bitmap == null) {
            return new RectF();
        }

        RectF rect =
                new RectF(
                        0f,
                        0f,
                        bitmap.getWidth(),
                        bitmap.getHeight()
                );

        bitmapToViewMatrix.mapRect(rect);
        return rect;
    }

    private void updateBitmapMatrix() {
        if (bitmap == null
                || getWidth() == 0
                || getHeight() == 0) {
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float baseScale =
                Math.min(
                        viewWidth / bitmapWidth,
                        viewHeight / bitmapHeight
                );

        float totalScale =
                baseScale * previewZoomScale;

        float displayedWidth =
                bitmapWidth * totalScale;
        float displayedHeight =
                bitmapHeight * totalScale;

        float dx =
                (viewWidth - displayedWidth) / 2f
                        + previewPanX;
        float dy =
                (viewHeight - displayedHeight) / 2f
                        + previewPanY;

        bitmapToViewMatrix.reset();
        bitmapToViewMatrix.postScale(
                totalScale,
                totalScale
        );
        bitmapToViewMatrix.postTranslate(dx, dy);

        bitmapToViewMatrix.invert(
                viewToBitmapMatrix
        );
    }

    private void constrainPan() {
        if (bitmap == null
                || getWidth() == 0
                || getHeight() == 0) {
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();

        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();

        float baseScale =
                Math.min(
                        viewWidth / bitmapWidth,
                        viewHeight / bitmapHeight
                );

        float totalScale =
                baseScale * previewZoomScale;

        float displayedWidth =
                bitmapWidth * totalScale;
        float displayedHeight =
                bitmapHeight * totalScale;

        float baseLeft =
                (viewWidth - displayedWidth) / 2f;
        float baseTop =
                (viewHeight - displayedHeight) / 2f;

        float requiredVisibleWidth =
                Math.min(
                        minVisibleImagePx,
                        Math.min(
                                displayedWidth,
                                viewWidth
                        )
                );

        float requiredVisibleHeight =
                Math.min(
                        minVisibleImagePx,
                        Math.min(
                                displayedHeight,
                                viewHeight
                        )
                );

        float minPanX =
                requiredVisibleWidth
                        - displayedWidth
                        - baseLeft;
        float maxPanX =
                viewWidth
                        - requiredVisibleWidth
                        - baseLeft;

        float minPanY =
                requiredVisibleHeight
                        - displayedHeight
                        - baseTop;
        float maxPanY =
                viewHeight
                        - requiredVisibleHeight
                        - baseTop;

        previewPanX =
                clamp(
                        previewPanX,
                        minPanX,
                        maxPanX
                );
        previewPanY =
                clamp(
                        previewPanY,
                        minPanY,
                        maxPanY
                );
    }

    private void drawOutputBoundaryLines(
            Canvas canvas
    ) {
        for (BoundaryPair pair : boundaryPairs) {
            float outputX =
                    clamp(pair.outputX, 0f, 1f);

            float x =
                    outputX * bitmap.getWidth();

            PointF top =
                    mapBitmapPointToView(x, 0f);
            PointF bottom =
                    mapBitmapPointToView(
                            x,
                            bitmap.getHeight()
                    );

            canvas.drawLine(
                    top.x,
                    top.y,
                    bottom.x,
                    bottom.y,
                    boundaryLinePaint
            );
        }
    }

    private int findTouchedOutputFrameEdge(
            float viewX,
            float viewY
    ) {
        RectF frameRect =
                getDisplayedBitmapRect();

        boolean insideVerticalRange =
                viewY
                        >= frameRect.top
                        - OUTPUT_FRAME_EDGE_HIT_RADIUS
                        && viewY
                        <= frameRect.bottom
                        + OUTPUT_FRAME_EDGE_HIT_RADIUS;

        if (!insideVerticalRange) {
            return OUTPUT_FRAME_EDGE_NONE;
        }

        float leftDistance =
                Math.abs(
                        viewX - frameRect.left
                );
        float rightDistance =
                Math.abs(
                        viewX - frameRect.right
                );

        boolean nearLeft =
                leftDistance
                        <= OUTPUT_FRAME_EDGE_HIT_RADIUS;
        boolean nearRight =
                rightDistance
                        <= OUTPUT_FRAME_EDGE_HIT_RADIUS;

        if (nearLeft && nearRight) {
            return leftDistance <= rightDistance
                    ? OUTPUT_FRAME_EDGE_LEFT
                    : OUTPUT_FRAME_EDGE_RIGHT;
        }

        if (nearLeft) {
            return OUTPUT_FRAME_EDGE_LEFT;
        }

        if (nearRight) {
            return OUTPUT_FRAME_EDGE_RIGHT;
        }

        return OUTPUT_FRAME_EDGE_NONE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return false;
        }

        updateBitmapMatrix();
        scaleGestureDetector.onTouchEvent(event);

        if (event.getActionMasked()
                == MotionEvent.ACTION_POINTER_DOWN) {
            cancelSingleFingerOperationForScale();

            getParent()
                    .requestDisallowInterceptTouchEvent(
                            true
                    );
        }

        if (scaleGestureDetector.isInProgress()
                || isScaling
                || event.getPointerCount() > 1) {
            invalidate();
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startSingleFingerOperation(
                        event.getX(),
                        event.getY()
                );

                getParent()
                        .requestDisallowInterceptTouchEvent(
                                true
                        );

                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (draggingOutputFrameEdge
                        != OUTPUT_FRAME_EDGE_NONE) {
                    updateOutputFrameEdgeDrag(
                            event.getX(),
                            false
                    );
                } else {
                    moveViewport(
                            event.getX(),
                            event.getY()
                    );
                }

                return true;

            case MotionEvent.ACTION_UP:
                if (draggingOutputFrameEdge
                        != OUTPUT_FRAME_EDGE_NONE) {
                    updateOutputFrameEdgeDrag(
                            event.getX(),
                            true
                    );
                    finishOutputFrameEdgeDrag();
                } else {
                    finishPan();
                }

                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (draggingOutputFrameEdge
                        != OUTPUT_FRAME_EDGE_NONE) {
                    cancelOutputFrameEdgeDrag();
                } else {
                    finishPan();
                }

                return true;

            default:
                return true;
        }
    }

    private void startSingleFingerOperation(
            float viewX,
            float viewY
    ) {
        int touchedEdge =
                findTouchedOutputFrameEdge(
                        viewX,
                        viewY
                );

        if (touchedEdge
                != OUTPUT_FRAME_EDGE_NONE) {
            startOutputFrameEdgeDrag(
                    touchedEdge,
                    viewX
            );
            lastPanTouchPoint = null;
            return;
        }

        lastPanTouchPoint =
                new PointF(viewX, viewY);
    }

    private void cancelSingleFingerOperationForScale() {
        if (draggingOutputFrameEdge
                != OUTPUT_FRAME_EDGE_NONE) {
            cancelOutputFrameEdgeDrag();
        }

        lastPanTouchPoint = null;
    }

    private void moveViewport(
            float viewX,
            float viewY
    ) {
        if (lastPanTouchPoint == null) {
            lastPanTouchPoint =
                    new PointF(viewX, viewY);
            return;
        }

        float dx =
                viewX - lastPanTouchPoint.x;
        float dy =
                viewY - lastPanTouchPoint.y;

        previewPanX += dx;
        previewPanY += dy;

        constrainPan();
        updateBitmapMatrix();

        lastPanTouchPoint.x = viewX;
        lastPanTouchPoint.y = viewY;

        invalidate();
    }

    private void finishPan() {
        lastPanTouchPoint = null;

        constrainPan();
        updateBitmapMatrix();

        getParent()
                .requestDisallowInterceptTouchEvent(
                        false
                );

        invalidate();
    }

    private void startOutputFrameEdgeDrag(
            int edge,
            float touchX
    ) {
        RectF frameRect =
                getDisplayedBitmapRect();

        draggingOutputFrameRect.set(frameRect);
        draggingOutputFrameEdge = edge;

        float edgeX;

        if (edge == OUTPUT_FRAME_EDGE_LEFT) {
            edgeX = frameRect.left;
        } else {
            edgeX = frameRect.right;
        }

        edgeTouchOffset =
                edgeX - touchX;

        dragStartAspectRatio =
                frameRect.width()
                        / frameRect.height();
    }

    private void updateOutputFrameEdgeDrag(
            float touchX,
            boolean isFinished
    ) {
        if (draggingOutputFrameEdge
                == OUTPUT_FRAME_EDGE_NONE) {
            return;
        }

        float frameHeight =
                draggingOutputFrameRect.height();

        if (frameHeight <= 0f) {
            return;
        }

        float requestedEdgeX =
                touchX + edgeTouchOffset;

        if (draggingOutputFrameEdge
                == OUTPUT_FRAME_EDGE_LEFT) {
            updateLeftEdge(
                    requestedEdgeX,
                    frameHeight
            );
        } else {
            updateRightEdge(
                    requestedEdgeX,
                    frameHeight
            );
        }

        float aspectRatio =
                draggingOutputFrameRect.width()
                        / frameHeight;

        if (outputAspectRatioChangedListener
                != null) {
            outputAspectRatioChangedListener
                    .onOutputAspectRatioChanged(
                            aspectRatio,
                            isFinished
                    );
        }

        invalidate();
    }

    private void updateLeftEdge(
            float requestedLeft,
            float frameHeight
    ) {
        float minLeft =
                draggingOutputFrameRect.right
                        - frameHeight
                        * MAX_OUTPUT_ASPECT_RATIO;

        float maxLeft =
                draggingOutputFrameRect.right
                        - frameHeight
                        * MIN_OUTPUT_ASPECT_RATIO;

        draggingOutputFrameRect.left =
                clamp(
                        requestedLeft,
                        minLeft,
                        maxLeft
                );
    }

    private void updateRightEdge(
            float requestedRight,
            float frameHeight
    ) {
        float minRight =
                draggingOutputFrameRect.left
                        + frameHeight
                        * MIN_OUTPUT_ASPECT_RATIO;

        float maxRight =
                draggingOutputFrameRect.left
                        + frameHeight
                        * MAX_OUTPUT_ASPECT_RATIO;

        draggingOutputFrameRect.right =
                clamp(
                        requestedRight,
                        minRight,
                        maxRight
                );
    }

    private void finishOutputFrameEdgeDrag() {
        draggingOutputFrameEdge =
                OUTPUT_FRAME_EDGE_NONE;

        lastPanTouchPoint = null;

        getParent()
                .requestDisallowInterceptTouchEvent(
                        false
                );

        invalidate();
    }

    private void cancelOutputFrameEdgeDrag() {
        draggingOutputFrameEdge =
                OUTPUT_FRAME_EDGE_NONE;

        if (outputAspectRatioChangedListener
                != null) {
            outputAspectRatioChangedListener
                    .onOutputAspectRatioChanged(
                            dragStartAspectRatio,
                            false
                    );
        }

        getParent()
                .requestDisallowInterceptTouchEvent(
                        false
                );

        invalidate();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private PointF mapBitmapPointToView(
            float x,
            float y
    ) {
        float[] values =
                new float[]{x, y};

        bitmapToViewMatrix.mapPoints(values);

        return new PointF(
                values[0],
                values[1]
        );
    }

    private PointF mapViewPointToBitmap(
            float viewX,
            float viewY
    ) {
        float[] values =
                new float[]{viewX, viewY};

        viewToBitmapMatrix.mapPoints(values);

        return new PointF(
                values[0],
                values[1]
        );
    }

    private float clamp(
            float value,
            float min,
            float max
    ) {
        return Math.max(
                min,
                Math.min(max, value)
        );
    }
}
