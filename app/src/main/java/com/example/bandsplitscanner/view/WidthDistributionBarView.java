package com.example.bandsplitscanner.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.widget.PopupMenu;

import com.example.bandsplitscanner.R;
import com.example.bandsplitscanner.model.BoundaryPair;
import com.example.bandsplitscanner.model.BoundaryMarker;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WidthDistributionBarView extends View {

    public interface OnBoundaryOutputChangedListener {
        void onBoundaryOutputChanged(
                BoundaryMarker marker,
                boolean isFinished
        );
    }

    public interface OnBoundaryAddRequestedListener {
        void onBoundaryAddRequested(float outputX);
    }

    public interface OnBoundaryDeleteRequestedListener {
        void onBoundaryDeleteRequested(long boundaryId);
    }

    private static final float HIT_RADIUS = 48f;
    private static final float MARKER_RADIUS = 16f;
    private static final float MIN_GAP = 0.03f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint endPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<BoundaryMarker> markers = new ArrayList<>();

    private int activeMarkerIndex = -1;

    private GestureDetector gestureDetector;

    private OnBoundaryOutputChangedListener listener;
    private OnBoundaryAddRequestedListener addRequestedListener;
    private OnBoundaryDeleteRequestedListener deleteRequestedListener;

    private boolean longPressHandled = false;

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

        gestureDetector = new GestureDetector(
                getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        if (activeMarkerIndex != -1) {
                            if (activeMarkerIndex >= markers.size()) {
                                return;
                            }

                            longPressHandled = true;

                            long boundaryId =
                                    markers.get(activeMarkerIndex).boundaryId;

                            performHapticFeedback(
                                    HapticFeedbackConstants.LONG_PRESS
                            );

                            showDeleteBoundaryMenu(
                                    boundaryId,
                                    e.getX(),
                                    e.getY()
                            );

                            return;
                        }

                        if (!isInsideBarHitArea(e.getX(), e.getY())) {
                            return;
                        }

                        float outputX = viewXToOutputX(e.getX());

                        if (!canAddMarkerAt(outputX)) {
                            return;
                        }

                        longPressHandled = true;

                        performHapticFeedback(
                                HapticFeedbackConstants.LONG_PRESS
                        );

                        showAddBoundaryMenu(
                                outputX,
                                e.getX(),
                                e.getY()
                        );
                    }
                }
        );
    }

    private boolean isInsideBarHitArea(float viewX, float viewY) {
        float centerY = getHeight() / 2f;

        return viewX >= getBarStartX()
                && viewX <= getBarEndX()
                && Math.abs(viewY - centerY) <= HIT_RADIUS;
    }

    private boolean canAddMarkerAt(float outputX) {
        if (outputX < MIN_GAP || outputX > 1f - MIN_GAP) {
            return false;
        }

        for (BoundaryMarker marker : markers) {
            if (Math.abs(marker.outputX - outputX) < MIN_GAP) {
                return false;
            }
        }

        return true;
    }

    private void showAddBoundaryMenu(
            float outputX,
            float touchX,
            float touchY
    ) {
        PopupMenu popupMenu =
                createPopupMenuAt(touchX, touchY);

        if (popupMenu == null) {
            return;
        }

        popupMenu.getMenu().add(
                R.string.add_boundary_here
        );

        popupMenu.setOnMenuItemClickListener(item -> {
            if (addRequestedListener == null) {
                return false;
            }

            addRequestedListener
                    .onBoundaryAddRequested(outputX);

            return true;
        });

        popupMenu.show();
    }

    public void setMarkersFromBoundaryPairs(List<BoundaryPair> boundaryPairs) {
        List<BoundaryMarker> markers = new ArrayList<>();
        if (boundaryPairs != null) {
            for (BoundaryPair pair : boundaryPairs) {
                markers.add(BoundaryMarker.fromBoundaryPair(pair));
            }
        }

        this.markers = copyAndSort(markers);
        activeMarkerIndex = -1;
        invalidate();
    }

    private void showDeleteBoundaryMenu(
            long boundaryId,
            float touchX,
            float touchY
    ) {
        PopupMenu popupMenu =
                createPopupMenuAt(touchX, touchY);

        if (popupMenu == null) {
            return;
        }

        popupMenu.getMenu().add(
                R.string.delete_boundary
        );

        popupMenu.setOnMenuItemClickListener(item -> {
            if (deleteRequestedListener == null) {
                return false;
            }

            deleteRequestedListener
                    .onBoundaryDeleteRequested(boundaryId);

            return true;
        });

        popupMenu.show();
    }

    private PopupMenu createPopupMenuAt(
            float touchX,
            float touchY
    ) {
        View rootView = getRootView();

        if (!(rootView instanceof ViewGroup)) {
            return null;
        }

        ViewGroup root = (ViewGroup) rootView;

        int[] viewLocation = new int[2];
        int[] rootLocation = new int[2];

        getLocationOnScreen(viewLocation);
        root.getLocationOnScreen(rootLocation);

        int anchorX = Math.round(
                viewLocation[0]
                        - rootLocation[0]
                        + touchX
        );

        int anchorY = Math.round(
                viewLocation[1]
                        - rootLocation[1]
                        + touchY
        );

        View anchor = new View(getContext());

        anchor.layout(
                anchorX,
                anchorY,
                anchorX + 1,
                anchorY + 1
        );

        root.getOverlay().add(anchor);

        PopupMenu popupMenu =
                new PopupMenu(getContext(), anchor);

        popupMenu.setOnDismissListener(menu -> {
            root.getOverlay().remove(anchor);
        });

        return popupMenu;
    }

    public List<BoundaryMarker> getBoundaryMarkers() {
        return copyAndSort(markers);
    }

    public void setOnBoundaryOutputChangedListener(OnBoundaryOutputChangedListener listener) {
        this.listener = listener;
    }
    public void setOnBoundaryAddRequestedListener(OnBoundaryAddRequestedListener listener) {
        this.addRequestedListener = listener;
    }
    public void setOnBoundaryDeleteRequestedListener(OnBoundaryDeleteRequestedListener listener) {
        this.deleteRequestedListener = listener;
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

        for (int i = 0; i < markers.size(); i++) {
            BoundaryMarker marker = markers.get(i);
            float markerX = outputXToViewX(marker.outputX);

            Paint paint = i == activeMarkerIndex ? activeMarkerPaint : markerPaint;
            canvas.drawCircle(markerX, centerY, MARKER_RADIUS, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        if (event.getActionMasked()
                == MotionEvent.ACTION_DOWN) {
            longPressHandled = false;
        }

        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeMarkerIndex =
                        findTouchedMarker(
                                event.getX(),
                                event.getY()
                        );

                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (activeMarkerIndex != -1
                        && !longPressHandled) {
                    moveActiveMarker(event.getX());
                    notifyChanged(false);
                    invalidate();
                }

                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeMarkerIndex != -1
                        && !longPressHandled) {
                    moveActiveMarker(event.getX());
                    notifyChanged(true);
                }

                activeMarkerIndex = -1;
                longPressHandled = false;

                invalidate();
                return true;

            default:
                return true;
        }
    }

    private int findTouchedMarker(float viewX, float viewY) {
        float centerY = getHeight() / 2f;

        for (int i = 0; i < markers.size(); i++) {
            float markerX = outputXToViewX(markers.get(i).outputX);

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
        if (activeMarkerIndex < 0 || activeMarkerIndex >= markers.size()) {
            return;
        }

        float newOutputX = viewXToOutputX(viewX);

        float min = activeMarkerIndex == 0
                ? MIN_GAP
                : markers.get(activeMarkerIndex - 1).outputX + MIN_GAP;

        float max = activeMarkerIndex == markers.size() - 1
                ? 1f - MIN_GAP
                : markers.get(activeMarkerIndex + 1).outputX - MIN_GAP;

        newOutputX = clamp(newOutputX, min, max);

        markers.get(activeMarkerIndex).outputX = newOutputX;
    }

    private void notifyChanged(boolean isFinished) {
        if (listener != null) {
            BoundaryMarker marker = markers.get(activeMarkerIndex);
            listener.onBoundaryOutputChanged(marker, isFinished);
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

    private List<BoundaryMarker> copyAndSort(List<BoundaryMarker> source) {
        List<BoundaryMarker> copied = new ArrayList<>();

        if (source != null) {
            for (BoundaryMarker marker : source) {
                copied.add(marker.copy());
            }
        }

        Collections.sort(copied, new Comparator<BoundaryMarker>() {
            @Override
            public int compare(BoundaryMarker a, BoundaryMarker b) {
                return Float.compare(a.outputX, b.outputX);
            }
        });

        return copied;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}