package com.flipcheck.nativebeta;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Minimal single-purpose provider for full-resolution camera captures.
 *
 * Keeping the destination inside the app cache avoids the OEM-specific
 * MediaStore pending-row behaviour seen on Samsung Camera. Only files created
 * under cache/camera-captures are ever exposed.
 */
public final class CameraCaptureProvider extends ContentProvider {
    private static final String DIRECTORY = "camera-captures";
    private static final String PATH = "capture";

    static Uri createDestination(Context context) throws IOException {
        File directory = new File(context.getCacheDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Cannot create camera capture directory");
        }
        File file = new File(directory,
                "FlipCheck_" + System.currentTimeMillis() + ".jpg");
        if (!file.createNewFile()) {
            throw new IOException("Cannot create camera destination");
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".camera")
                .appendPath(PATH)
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        return context != null && (new File(context.getCacheDir(), DIRECTORY).exists()
                || new File(context.getCacheDir(), DIRECTORY).mkdirs());
    }

    @Override
    public String getType(Uri uri) {
        resolve(uri);
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = resolve(uri);
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = file.length();
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        File file = resolve(uri);
        int flags;
        if (mode != null && mode.contains("w")) {
            flags = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            flags = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        return ParcelFileDescriptor.open(file, flags);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return resolve(uri).delete() ? 1 : 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }

    private File resolve(Uri uri) {
        Context context = getContext();
        if (context == null || uri == null
                || !"content".equals(uri.getScheme())
                || !(context.getPackageName() + ".camera").equals(uri.getAuthority())
                || uri.getPathSegments().size() != 2
                || !PATH.equals(uri.getPathSegments().get(0))) {
            throw new SecurityException("Invalid camera URI");
        }
        try {
            File directory = new File(context.getCacheDir(), DIRECTORY).getCanonicalFile();
            File file = new File(directory, uri.getPathSegments().get(1)).getCanonicalFile();
            if (!file.getParentFile().equals(directory)) {
                throw new SecurityException("Invalid camera path");
            }
            return file;
        } catch (IOException error) {
            throw new SecurityException("Invalid camera path", error);
        }
    }
}
