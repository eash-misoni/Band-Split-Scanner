
package com.example.bandsplitscanner.correction;

import android.graphics.Bitmap;

import com.example.bandsplitscanner.model.BoundaryLine;
import com.example.bandsplitscanner.model.OutputSettings;

import java.util.List;

public interface BandRenderer {
    Bitmap render(
            Bitmap source,
            List<BoundaryLine> boundaries,
            OutputSettings settings
    );
}