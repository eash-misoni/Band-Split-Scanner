package com.example.bandsplitscanner.model;

import android.graphics.PointF;

public class BoundaryLine {
    public float outputX;
    public PointF inputTop;
    public PointF inputBottom;

    public BoundaryLine(float outputX, PointF inputTop, PointF inputBottom) {
        this.outputX = outputX;
        this.inputTop = inputTop;
        this.inputBottom = inputBottom;
    }
}