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

    private Bitmap bitmap;

    private final Matrix bitmapToViewMatrix = new Matrix();

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boundaryLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outputFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<BoundaryPair> boundaryPairs = new ArrayList<>();

    private static final float MIN_OUTPUT_ASPECT_RATIO = 0.4f;
    private static final float MAX_OUTPUT_ASPECT_RATIO = 3.0f;
    private static final int OUTPUT_FRAME_EDGE_NONE = 0;
    private static final int OUTPUT_FRAME_EDGE_LEFT = 1;
    private static final int OUTPUT_FRAME_EDGE_RIGHT = 2;

    private static final float OUTPUT_FRAME_EDGE_HIT_RADIUS =
            36f;

    private final RectF draggingOutputFrameRect =
            new RectF();

    private int draggingOutputFrameEdge =
            OUTPUT_FRAME_EDGE_NONE;

    private float edgeTouchOffset = 0f;

    private float dragStartAspectRatio = 1f;
    private OnOutputAspectRatioChangedListener outputAspectRatioChangedListener;
    private boolean showOutputBoundaryLines = true;

    public ResultPreviewView(Context context) {
        super(context);
        init();
    }

    public ResultPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ResultPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
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
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        invalidate();
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

    public void setOnOutputAspectRatioChangedListener(
            OnOutputAspectRatioChangedListener listener
    ) {
        this.outputAspectRatioChangedListener =
                listener;
    }

    public void applyOutputXFromWidthBar(BoundaryMarker marker) {
        long boundaryId = marker.boundaryId;
        float outputX = marker.outputX;
        BoundaryPair pair = getBoundaryPairById(boundaryId);

        if (pair != null) {
            pair.outputX = outputX;
            invalidate();
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


    public void setShowOutputBoundaryLines(boolean showOutputBoundaryLines) {
        this.showOutputBoundaryLines = showOutputBoundaryLines;
        invalidate();
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
        RectF frameRect = getCurrentOutputFrameRect();

        float halfStroke = outputFramePaint.getStrokeWidth() / 2f;

        RectF drawableRect = new RectF(frameRect);

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

        RectF rect = new RectF(
                0f,
                0f,
                bitmap.getWidth(),
                bitmap.getHeight()
        );

        bitmapToViewMatrix.mapRect(rect);

        return rect;
    }

    private void updateBitmapMatrix() {
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

        bitmapToViewMatrix.reset();
        bitmapToViewMatrix.postScale(scale, scale);
        bitmapToViewMatrix.postTranslate(dx, dy);
    }

    private void drawOutputBoundaryLines(Canvas canvas) {
        for (BoundaryPair pair : boundaryPairs) {
            float outputX = clamp(pair.outputX, 0f, 1f);
            float x = outputX * bitmap.getWidth();

            PointF top = mapBitmapPointToView(x, 0f);
            PointF bottom = mapBitmapPointToView(x, bitmap.getHeight());

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
                viewY >= frameRect.top
                        - OUTPUT_FRAME_EDGE_HIT_RADIUS
                        && viewY <= frameRect.bottom
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

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                int touchedEdge =
                        findTouchedOutputFrameEdge(
                                event.getX(),
                                event.getY()
                        );

                if (touchedEdge
                        == OUTPUT_FRAME_EDGE_NONE) {
                    return false;
                }

                startOutputFrameEdgeDrag(
                        touchedEdge,
                        event.getX()
                );

                getParent()
                        .requestDisallowInterceptTouchEvent(
                                true
                        );

                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (draggingOutputFrameEdge
                        == OUTPUT_FRAME_EDGE_NONE) {
                    return false;
                }

                updateOutputFrameEdgeDrag(
                        event.getX(),
                        false
                );

                return true;

            case MotionEvent.ACTION_UP:
                if (draggingOutputFrameEdge
                        == OUTPUT_FRAME_EDGE_NONE) {
                    return false;
                }

                updateOutputFrameEdgeDrag(
                        event.getX(),
                        true
                );

                finishOutputFrameEdgeDrag();

                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (draggingOutputFrameEdge
                        == OUTPUT_FRAME_EDGE_NONE) {
                    return false;
                }

                cancelOutputFrameEdgeDrag();
                return true;

            default:
                return false;
        }
    }

    private void startOutputFrameEdgeDrag(
            int edge,
            float touchX
    ) {
        RectF frameRect =
                getDisplayedBitmapRect();

        draggingOutputFrameRect.set(
                frameRect
        );

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
        float minLeftByAspectRatio =
                draggingOutputFrameRect.right
                        - frameHeight
                        * MAX_OUTPUT_ASPECT_RATIO;

        float minLeftByView =
                outputFramePaint
                        .getStrokeWidth()
                        / 2f;

        float minLeft = Math.max(
                minLeftByAspectRatio,
                minLeftByView
        );

        float maxLeft =
                draggingOutputFrameRect.right
                        - frameHeight
                        * MIN_OUTPUT_ASPECT_RATIO;

        if (minLeft > maxLeft) {
            maxLeft = minLeft;
        }

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

        float maxRightByAspectRatio =
                draggingOutputFrameRect.left
                        + frameHeight
                        * MAX_OUTPUT_ASPECT_RATIO;

        float maxRightByView =
                getWidth()
                        - outputFramePaint
                        .getStrokeWidth()
                        / 2f;

        float maxRight = Math.min(
                maxRightByAspectRatio,
                maxRightByView
        );

        if (maxRight < minRight) {
            minRight = maxRight;
        }

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

    private PointF mapBitmapPointToView(float x, float y) {
        float[] values = new float[]{x, y};
        bitmapToViewMatrix.mapPoints(values);
        return new PointF(values[0], values[1]);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}