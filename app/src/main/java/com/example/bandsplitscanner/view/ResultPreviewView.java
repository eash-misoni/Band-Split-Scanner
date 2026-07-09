package com.example.bandsplitscanner.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import com.example.bandsplitscanner.model.BoundaryMarker;
import com.example.bandsplitscanner.model.BoundaryPair;

import java.util.ArrayList;
import java.util.List;

public class ResultPreviewView extends View {

    private Bitmap bitmap;

    private final Matrix bitmapToViewMatrix = new Matrix();

    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boundaryLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<BoundaryPair> boundaryPairs = new ArrayList<>();
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

        canvas.drawBitmap(bitmap, bitmapToViewMatrix, imagePaint);

        if (showOutputBoundaryLines) {
            drawOutputBoundaryLines(canvas);
        }
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

    private PointF mapBitmapPointToView(float x, float y) {
        float[] values = new float[]{x, y};
        bitmapToViewMatrix.mapPoints(values);
        return new PointF(values[0], values[1]);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}