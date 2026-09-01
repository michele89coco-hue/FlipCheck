package com.flipcheck.nativebeta;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/** Durable hand-off between the foreground service and recreated Activities. */
final class AnalysisResultStore {
    static final String IDLE = "idle";
    static final String RUNNING = "running";
    static final String COMPLETE = "complete";
    static final String FAILED = "failed";
    private static final String FILE_NAME = "analysis-result-v084.bin";
    private static Snapshot memory;

    private AnalysisResultStore() {
    }

    static synchronized void markRunning(Context context) {
        save(context, new Snapshot(RUNNING, "", null, null,
                System.currentTimeMillis()));
    }

    static synchronized void saveSuccess(Context context, Models.Identification identification,
                                         Models.Usage usage) {
        save(context, new Snapshot(COMPLETE, "", identification, usage,
                System.currentTimeMillis()));
    }

    static synchronized void saveFailure(Context context, String message) {
        save(context, new Snapshot(FAILED, message == null ? "Errore sconosciuto" : message,
                null, null, System.currentTimeMillis()));
    }

    static synchronized Snapshot load(Context context) {
        if (memory != null) {
            return memory;
        }
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile()) {
            return new Snapshot(IDLE, "", null, null, 0L);
        }
        try {
            ObjectInputStream input = new ObjectInputStream(new FileInputStream(file));
            try {
                Object value = input.readObject();
                if (value instanceof Snapshot) {
                    memory = (Snapshot) value;
                    return memory;
                }
            } finally {
                input.close();
            }
        } catch (Exception ignored) {
        }
        return new Snapshot(IDLE, "", null, null, 0L);
    }

    static synchronized void reset(Context context) {
        memory = new Snapshot(IDLE, "", null, null, System.currentTimeMillis());
        File file = new File(context.getFilesDir(), FILE_NAME);
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        if (file.isFile()) {
            file.delete();
        }
        if (temp.isFile()) {
            temp.delete();
        }
    }

    private static void save(Context context, Snapshot snapshot) {
        memory = snapshot;
        File target = new File(context.getFilesDir(), FILE_NAME);
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try {
            ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(temp));
            try {
                output.writeObject(snapshot);
                output.flush();
            } finally {
                output.close();
            }
            if (!temp.renameTo(target)) {
                ObjectOutputStream fallback = new ObjectOutputStream(new FileOutputStream(target));
                try {
                    fallback.writeObject(snapshot);
                } finally {
                    fallback.close();
                }
            }
        } catch (Exception ignored) {
        }
    }

    static final class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        final String state;
        final String error;
        final Models.Identification identification;
        final Models.Usage usage;
        final long updatedAt;

        Snapshot(String state, String error, Models.Identification identification,
                 Models.Usage usage, long updatedAt) {
            this.state = state;
            this.error = error;
            this.identification = identification;
            this.usage = usage;
            this.updatedAt = updatedAt;
        }
    }
}
