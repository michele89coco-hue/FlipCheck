package com.flipcheck.nativebeta;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Encodes a bounded copy of an image for the single multimodal request. */
final class ImageDataEncoder {
    private static final int MAX_SIDE = 2048;

    private ImageDataEncoder() {
    }

    static String toDataUrl(Context context, Uri uri) throws Exception {
        Bitmap bitmap = decodeSampled(context, uri, MAX_SIDE * 2);
        if (bitmap == null) {
            throw new IllegalArgumentException("Immagine non leggibile");
        }
        Bitmap scaled = scaleDown(bitmap, MAX_SIDE);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 84, out)) {
                throw new IllegalStateException("Compressione immagine non riuscita");
            }
            return "data:image/jpeg;base64," + Base64.encodeToString(
                    out.toByteArray(), Base64.NO_WRAP);
        } finally {
            scaled.recycle();
            out.close();
        }
    }

    /**
     * Enlarged diagnostic view of the left/lower edge of a portrait card's
     * illustration. On English Base Set cards this is where the tiny 1st
     * Edition icon is physically printed. It remains only an extra view of the
     * same photo: it is never treated as OCR or as independent evidence.
     */
    static String toCardStampDetailDataUrl(Context context, Uri uri) throws Exception {
        Bitmap bitmap = decodeSampled(context, uri, 4096);
        if (bitmap == null) {
            return "";
        }
        bitmap = orientFromExif(context, uri, bitmap);
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width < 320 || height < 420 || height <= width) {
                return "";
            }
            // Deliberately generous: preserves the artwork edge, attack-box
            // start and the complete canonical stamp position even when the
            // card has a moderate border around it in the camera frame.
            int left = Math.max(0, Math.round(width * 0.03f));
            int top = Math.max(0, Math.round(height * 0.25f));
            int right = Math.min(width, Math.round(width * 0.58f));
            int bottom = Math.min(height, Math.round(height * 0.72f));
            if (right - left < 180 || bottom - top < 180) {
                return "";
            }
            Bitmap crop = Bitmap.createBitmap(bitmap, left, top,
                    right - left, bottom - top);
            Bitmap enlarged = scaleUpTo(crop, 1536);
            if (enlarged != crop) {
                crop.recycle();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                if (!enlarged.compress(Bitmap.CompressFormat.JPEG, 94, out)) {
                    return "";
                }
                return "data:image/jpeg;base64," + Base64.encodeToString(
                        out.toByteArray(), Base64.NO_WRAP);
            } finally {
                enlarged.recycle();
                out.close();
            }
        } finally {
            bitmap.recycle();
        }
    }

    private static Bitmap decodeSampled(Context context, Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream first = context.getContentResolver().openInputStream(uri);
        try {
            BitmapFactory.decodeStream(first, null, bounds);
        } finally {
            if (first != null) {
                first.close();
            }
        }
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > maxSide && sample < 32) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        InputStream second = context.getContentResolver().openInputStream(uri);
        try {
            return BitmapFactory.decodeStream(second, null, options);
        } finally {
            if (second != null) {
                second.close();
            }
        }
    }

    private static Bitmap scaleDown(Bitmap src, int maxSide) {
        int max = Math.max(src.getWidth(), src.getHeight());
        if (max <= maxSide || max <= 0) {
            return src;
        }
        float scale = (float) maxSide / (float) max;
        return Bitmap.createScaledBitmap(src,
                Math.max(1, Math.round(src.getWidth() * scale)),
                Math.max(1, Math.round(src.getHeight() * scale)), true);
    }

    private static Bitmap scaleUpTo(Bitmap src, int targetSide) {
        int max = Math.max(src.getWidth(), src.getHeight());
        if (max <= 0 || max >= targetSide) {
            return src;
        }
        float scale = (float) targetSide / (float) max;
        return Bitmap.createScaledBitmap(src,
                Math.max(1, Math.round(src.getWidth() * scale)),
                Math.max(1, Math.round(src.getHeight() * scale)), true);
    }

    private static Bitmap orientFromExif(Context context, Uri uri, Bitmap src) {
        if (Build.VERSION.SDK_INT < 24) {
            return src;
        }
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            android.media.ExifInterface exif = new android.media.ExifInterface(in);
            int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL);
            float degrees = 0f;
            if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90) degrees = 90f;
            if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_180) degrees = 180f;
            if (orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270) degrees = 270f;
            if (degrees == 0f) {
                return src;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(),
                    matrix, true);
            if (rotated != src) {
                src.recycle();
            }
            return rotated;
        } catch (Throwable ignored) {
            return src;
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) { }
            }
        }
    }
}
