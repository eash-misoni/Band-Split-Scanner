package com.example.bandsplitscanner.model;

public class BoundaryMarker {
    public long boundaryId;
    public float outputX;

    public BoundaryMarker(long boundaryId, float outputX) {
        this.boundaryId = boundaryId;
        this.outputX = outputX;
    }

    public static BoundaryMarker fromBoundaryPair(BoundaryPair pair) {
        return new BoundaryMarker(pair.id, pair.outputX);
    }

    public BoundaryMarker copy() {
        return new BoundaryMarker(boundaryId, outputX);
    }
}