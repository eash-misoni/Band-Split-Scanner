package com.example.bandsplitscanner.model;

import android.graphics.PointF;

public class BoundaryPair {
    public long id;
    public float outputX;
    public PointF inputTop;
    public PointF inputBottom;

    public BoundaryPair(long id, float outputX, PointF inputTop, PointF inputBottom) {
        this.id = id;
        this.outputX = outputX;
        this.inputTop = inputTop;
        this.inputBottom = inputBottom;
    }

    public BoundaryPair copy() {
        return new BoundaryPair(
                id,
                outputX,
                new PointF(inputTop.x, inputTop.y),
                new PointF(inputBottom.x, inputBottom.y)
        );
    }
}