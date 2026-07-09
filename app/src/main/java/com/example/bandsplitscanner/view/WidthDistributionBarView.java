package com.example.bandsplitscanner.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import com.example.bandsplitscanner.model.BoundaryPair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WidthDistributionBarView extends View {

    public interface OnBoundaryPairsChangedListener {
        void onBoundaryPairsChanged(List<BoundaryPair> boundaryPairs, boolean isFinished);
    }

    private static final float HIT_RADIUS = 48f;
    private static final float MARKER_RADIUS = 16f;
    private static final float MIN_GAP = 0.03f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint endPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<BoundaryPair> boundaryPairs = new ArrayList<>();

    private int activeMarkerIndex = -1;
    private OnBoundaryPairsChangedListener listener;

    public WidthDistributionBarView(Context context) {
        super(context);
        init();
    }

    public WidthDistributionBarView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WidthDistributionBarView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setColor(0xFF888888);
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeWidth(6f);

        markerPaint.setColor(0xFF00AAFF);
        markerPaint.setStyle(Paint.Style.FILL);

        activeMarkerPaint.setColor(0xFFFF4444);
        activeMarkerPaint.setStyle(Paint.Style.FILL);

        endPointPaint.setColor(0xFF555555);
        endPointPaint.setStyle(Paint.Style.FILL);
    }

    public void setBoundaryPairs(List<BoundaryPair> boundaryPairs) {
        this.boundaryPairs = copyAndSort(boundaryPairs);
        activeMarkerIndex = -1;
        invalidate();
    }

    public List<BoundaryPair> getBoundaryPairs() {
        return copyAndSort(boundaryPairs);
    }

    public void setOnBoundaryPairsChangedListener(OnBoundaryPairsChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float startX = getBarStartX();
        float endX = getBarEndX();
        float centerY = getHeight() / 2f;

        canvas.drawLine(startX, centerY, endX, centerY, barPaint);

        canvas.drawCircle(startX, centerY, 8f, endPointPaint);
        canvas.drawCircle(endX, centerY, 8f, endPointPaint);

        for (int i = 0; i < boundaryPairs.size(); i++) {
            BoundaryPair pair = boundaryPairs.get(i);
            float markerX = outputXToViewX(pair.outputX);

            Paint paint = i == activeMarkerIndex ? activeMarkerPaint : markerPaint;
            canvas.drawCircle(markerX, centerY, MARKER_RADIUS, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeMarkerIndex = findTouchedMarker(event.getX(), event.getY());
                invalidate();
                return activeMarkerIndex != -1;

            case MotionEvent.ACTION_MOVE:
                if (activeMarkerIndex != -1) {
                    moveActiveMarker(event.getX());
                    notifyChanged(false);
                    invalidate();
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeMarkerIndex != -1) {
                    moveActiveMarker(event.getX());
                    notifyChanged(true);
                }
                activeMarkerIndex = -1;
                invalidate();
                return true;

            default:
                return true;
        }
    }

    private int findTouchedMarker(float viewX, float viewY) {
        float centerY = getHeight() / 2f;

        for (int i = 0; i < boundaryPairs.size(); i++) {
            float markerX = outputXToViewX(boundaryPairs.get(i).outputX);

            float dx = viewX - markerX;
            float dy = viewY - centerY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance <= HIT_RADIUS) {
                return i;
            }
        }

        return -1;
    }

    private void moveActiveMarker(float viewX) {
        if (activeMarkerIndex < 0 || activeMarkerIndex >= boundaryPairs.size()) {
            return;
        }

        float newOutputX = viewXToOutputX(viewX);

        float min = activeMarkerIndex == 0
                ? MIN_GAP
                : boundaryPairs.get(activeMarkerIndex - 1).outputX + MIN_GAP;

        float max = activeMarkerIndex == boundaryPairs.size() - 1
                ? 1f - MIN_GAP
                : boundaryPairs.get(activeMarkerIndex + 1).outputX - MIN_GAP;

        newOutputX = clamp(newOutputX, min, max);

        boundaryPairs.get(activeMarkerIndex).outputX = newOutputX;
    }

    private void notifyChanged(boolean isFinished) {
        if (listener != null) {
            listener.onBoundaryPairsChanged(getBoundaryPairs(), isFinished);
        }
    }

    private float outputXToViewX(float outputX) {
        float startX = getBarStartX();
        float endX = getBarEndX();
        return startX + clamp(outputX, 0f, 1f) * (endX - startX);
    }

    private float viewXToOutputX(float viewX) {
        float startX = getBarStartX();
        float endX = getBarEndX();

        if (endX <= startX) {
            return 0f;
        }

        return clamp((viewX - startX) / (endX - startX), 0f, 1f);
    }

    private float getBarStartX() {
        return getPaddingLeft() + 32f;
    }

    private float getBarEndX() {
        return getWidth() - getPaddingRight() - 32f;
    }

    private List<BoundaryPair> copyAndSort(List<BoundaryPair> source) {
        List<BoundaryPair> copied = new ArrayList<>();

        if (source != null) {
            for (BoundaryPair pair : source) {
                copied.add(pair.copy());
            }
        }

        Collections.sort(copied, new Comparator<BoundaryPair>() {
            @Override
            public int compare(BoundaryPair a, BoundaryPair b) {
                return Float.compare(a.outputX, b.outputX);
            }
        });

        return copied;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}