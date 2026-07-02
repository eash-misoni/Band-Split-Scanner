package com.example.bandsplitscanner.model;

import android.graphics.PointF;

public class PageCorners {
    public PointF topLeft;
    public PointF topRight;
    public PointF bottomRight;
    public PointF bottomLeft;

    public PageCorners(PointF topLeft, PointF topRight, PointF bottomRight, PointF bottomLeft) {
        this.topLeft = topLeft;
        this.topRight = topRight;
        this.bottomRight = bottomRight;
        this.bottomLeft = bottomLeft;
    }
}