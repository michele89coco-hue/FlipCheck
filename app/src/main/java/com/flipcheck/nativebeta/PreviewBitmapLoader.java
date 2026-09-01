package com.flipcheck.nativebeta;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Size;
import java.io.InputStream;

/** Memory-bounded image loader used only for on-screen thumbnails. */
final class PreviewBitmapLoader {
    private PreviewBitmapLoader() {
    }

    static Bitmap load(Context context, Uri uri, int width, int height) throws Exception {
        int safeWidth = Math.max(64, Math.min(512, width));
        int safeHeight = Math.max(64, Math.min(512, height));
        if (Build.VERSION.SDK_INT >= 29) {
            return context.getContentResolver().loadThumbnail(uri,
                    new Size(safeWidth, safeHeight), null);
        }
        return decodeSampled(context, uri, Math.max(safeWidth, safeHeight));
    }

    private static Bitmap decodeSampled(Context context, Uri uri, int maxSide)
            throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest > 0 && largest / sample > maxSide && sample < 128) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, options);
        }
    }
}
