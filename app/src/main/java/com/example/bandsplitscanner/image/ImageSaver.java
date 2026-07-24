package com.example.bandsplitscanner.image;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;

public class ImageSaver {

    private static final String JPEG_MIME_TYPE =
            "image/jpeg";

    private final ContentResolver contentResolver;

    public ImageSaver(
            ContentResolver contentResolver
    ) {
        this.contentResolver = contentResolver;
    }

    public Uri saveJpeg(
            Bitmap bitmap,
            int quality,
            String directoryName,
            String displayName
    ) throws IOException {
        Uri imageUri = null;

        try {
            ContentValues values =
                    createContentValues(
                            directoryName,
                            displayName
                    );

            imageUri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
            );
            if (imageUri == null) {
                throw new IOException(
                        "MediaStoreへの登録に失敗しました"
                );
            }

            writeJpeg(
                    imageUri,
                    bitmap,
                    quality
            );

            publishImage(imageUri);

            return imageUri;
        } catch (OutOfMemoryError e) {
            deleteIfCreated(imageUri);
            throw e;
        } catch (Exception e) {
            deleteIfCreated(imageUri);

            if (e instanceof IOException) {
                throw (IOException) e;
            }

            throw new IOException(
                    "画像の保存に失敗しました",
                    e
            );
        }
    }

    private ContentValues createContentValues(
            String directoryName,
            String displayName
    ) {
        ContentValues values =
                new ContentValues();

        values.put(
                MediaStore.Images.Media.DISPLAY_NAME,
                displayName
        );
        values.put(
                MediaStore.Images.Media.MIME_TYPE,
                JPEG_MIME_TYPE
        );

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.Q) {
            values.put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES
                            + "/"
                            + directoryName
            );
            values.put(
                    MediaStore.Images.Media.IS_PENDING,
                    1
            );
        }

        return values;
    }

    private void writeJpeg(
            Uri imageUri,
            Bitmap bitmap,
            int quality
    ) throws IOException {
        try (OutputStream outputStream =
                     contentResolver.openOutputStream(
                             imageUri
                     )) {
            if (outputStream == null) {
                throw new IOException(
                        "保存先を開けませんでした"
                );
            }

            boolean compressed =
                    bitmap.compress(
                            Bitmap.CompressFormat.JPEG,
                            quality,
                            outputStream
                    );
            if (!compressed) {
                throw new IOException(
                        "JPEGへの変換に失敗しました"
                );
            }
        }
    }

    private void publishImage(
            Uri imageUri
    ) throws IOException {
        if (Build.VERSION.SDK_INT
                < Build.VERSION_CODES.Q) {
            return;
        }

        ContentValues values =
                new ContentValues();
        values.put(
                MediaStore.Images.Media.IS_PENDING,
                0
        );

        int updatedRows =
                contentResolver.update(
                        imageUri,
                        values,
                        null,
                        null
                );
        if (updatedRows == 0) {
            throw new IOException(
                    "保存画像を公開できませんでした"
            );
        }
    }

    private void deleteIfCreated(
            Uri imageUri
    ) {
        if (imageUri == null) {
            return;
        }

        contentResolver.delete(
                imageUri,
                null,
                null
        );
    }
}
