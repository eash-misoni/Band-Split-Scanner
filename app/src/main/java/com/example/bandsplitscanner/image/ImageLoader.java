package com.example.bandsplitscanner.image;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

public class ImageLoader {

    private final ContentResolver contentResolver;

    public ImageLoader(
            ContentResolver contentResolver
    ) {
        this.contentResolver = contentResolver;
    }

    public LoadedImage loadPreview(
            Uri imageUri,
            int maxEdge
    ) throws IOException {
        ImageSize imageSize =
                readBounds(imageUri);
        int inSampleSize =
                calculateInSampleSize(
                        imageSize.width,
                        imageSize.height,
                        maxEdge
                );
        Bitmap bitmap =
                loadBitmap(
                        imageUri,
                        inSampleSize
                );

        return new LoadedImage(
                bitmap,
                imageSize.width,
                imageSize.height
        );
    }

    public Bitmap loadOriginal(
            Uri imageUri
    ) throws IOException {
        return loadBitmap(
                imageUri,
                1
        );
    }

    public ImageSize readBounds(
            Uri imageUri
    ) throws IOException {
        BitmapFactory.Options options =
                new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        try (InputStream inputStream =
                     openInputStream(imageUri)) {
            BitmapFactory.decodeStream(
                    inputStream,
                    null,
                    options
            );
        }

        if (options.outWidth <= 0
                || options.outHeight <= 0) {
            throw new IOException(
                    "画像サイズを取得できませんでした"
            );
        }

        return new ImageSize(
                options.outWidth,
                options.outHeight
        );
    }

    private int calculateInSampleSize(
            int imageWidth,
            int imageHeight,
            int maxEdge
    ) {
        int safeMaxEdge =
                Math.max(1, maxEdge);
        int inSampleSize = 1;
        int longestEdge =
                Math.max(imageWidth, imageHeight);

        while (longestEdge / inSampleSize
                > safeMaxEdge) {
            inSampleSize *= 2;
        }

        return inSampleSize;
    }

    private Bitmap loadBitmap(
            Uri imageUri,
            int inSampleSize
    ) throws IOException {
        BitmapFactory.Options options =
                new BitmapFactory.Options();
        options.inSampleSize =
                Math.max(1, inSampleSize);

        try (InputStream inputStream =
                     openInputStream(imageUri)) {
            Bitmap bitmap =
                    BitmapFactory.decodeStream(
                            inputStream,
                            null,
                            options
                    );

            if (bitmap == null) {
                throw new IOException(
                        "画像をBitmapとして読み込めませんでした"
                );
            }

            return bitmap;
        }
    }

    private InputStream openInputStream(
            Uri imageUri
    ) throws IOException {
        InputStream inputStream =
                contentResolver.openInputStream(
                        imageUri
                );

        if (inputStream == null) {
            throw new IOException(
                    "画像の入力ストリームを開けませんでした"
            );
        }

        return inputStream;
    }

    public static class LoadedImage {

        public final Bitmap bitmap;
        public final int originalWidth;
        public final int originalHeight;

        public LoadedImage(
                Bitmap bitmap,
                int originalWidth,
                int originalHeight
        ) {
            this.bitmap = bitmap;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
        }
    }

    public static class ImageSize {

        public final int width;
        public final int height;

        public ImageSize(
                int width,
                int height
        ) {
            this.width = width;
            this.height = height;
        }
    }
}
