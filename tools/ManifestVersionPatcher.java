import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Minimal Android binary-XML patcher for same-length version name + version code. */
public final class ManifestVersionPatcher {
    private static final int STRING_POOL = 0x0001;
    private static final int START_ELEMENT = 0x0102;

    public static void main(String[] args) throws Exception {
        if (args.length != 5 && args.length != 7) {
            throw new IllegalArgumentException(
                    "manifest oldCode newCode oldName newName [oldLabel newLabel]");
        }
        Path path = Path.of(args[0]);
        int oldCode = Integer.parseInt(args[1]);
        int newCode = Integer.parseInt(args[2]);
        String oldName = args[3];
        String newName = args[4];
        if (oldName.length() != newName.length()) {
            throw new IllegalArgumentException("version names must have equal length");
        }
        byte[] data = Files.readAllBytes(path);
        StringPool pool = null;
        boolean codePatched = false;
        int offset = u16(data, 2);
        while (offset + 8 <= data.length) {
            int type = u16(data, offset);
            int headerSize = u16(data, offset + 2);
            int size = i32(data, offset + 4);
            if (size < headerSize || offset + size > data.length) {
                throw new IOException("invalid binary XML chunk at " + offset);
            }
            if (type == STRING_POOL) {
                pool = parsePool(data, offset);
            } else if (type == START_ELEMENT && pool != null) {
                int nameIndex = i32(data, offset + 20);
                if ("manifest".equals(pool.get(nameIndex))) {
                    int attributeStart = u16(data, offset + 24);
                    int attributeSize = u16(data, offset + 26);
                    int attributeCount = u16(data, offset + 28);
                    int start = offset + 16 + attributeStart;
                    for (int i = 0; i < attributeCount; i++) {
                        int at = start + i * attributeSize;
                        int attrNameIndex = i32(data, at + 4);
                        if ("versionCode".equals(pool.get(attrNameIndex))) {
                            int value = i32(data, at + 16);
                            if (value != oldCode) {
                                throw new IOException("unexpected old versionCode " + value);
                            }
                            put32(data, at + 16, newCode);
                            codePatched = true;
                        }
                    }
                }
            }
            offset += size;
        }
        boolean namePatched = pool != null && pool.replaceSameLength(oldName, newName);
        boolean labelPatched = args.length != 7
                || (pool != null && pool.replaceSameLength(args[5], args[6]));
        if (pool == null || !namePatched || !labelPatched || !codePatched) {
            throw new IOException("version fields not found");
        }
        Files.write(path, data);
    }

    private static StringPool parsePool(byte[] data, int offset) {
        int headerSize = u16(data, offset + 2);
        int stringCount = i32(data, offset + 8);
        int flags = i32(data, offset + 16);
        int stringsStart = i32(data, offset + 20);
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < stringCount; i++) {
            positions.add(offset + stringsStart + i32(data, offset + headerSize + i * 4));
        }
        return new StringPool(data, positions, (flags & 0x100) != 0);
    }

    private static final class StringPool {
        final byte[] data;
        final List<Integer> positions;
        final boolean utf8;

        StringPool(byte[] data, List<Integer> positions, boolean utf8) {
            this.data = data;
            this.positions = positions;
            this.utf8 = utf8;
        }

        String get(int index) {
            if (index < 0 || index >= positions.size()) return "";
            int p = positions.get(index);
            if (utf8) {
                int[] a = length8(data, p);
                int[] b = length8(data, a[1]);
                return new String(data, b[1], b[0], StandardCharsets.UTF_8);
            }
            int[] len = length16(data, p);
            return new String(data, len[1], len[0] * 2, StandardCharsets.UTF_16LE);
        }

        boolean replaceSameLength(String oldValue, String newValue) {
            for (int i = 0; i < positions.size(); i++) {
                if (!oldValue.equals(get(i))) continue;
                int p = positions.get(i);
                if (utf8) {
                    int[] chars = length8(data, p);
                    int[] bytes = length8(data, chars[1]);
                    byte[] replacement = newValue.getBytes(StandardCharsets.UTF_8);
                    if (replacement.length != bytes[0]) return false;
                    System.arraycopy(replacement, 0, data, bytes[1], replacement.length);
                } else {
                    int[] len = length16(data, p);
                    byte[] replacement = newValue.getBytes(StandardCharsets.UTF_16LE);
                    if (replacement.length != len[0] * 2) return false;
                    System.arraycopy(replacement, 0, data, len[1], replacement.length);
                }
                return true;
            }
            return false;
        }
    }

    private static int[] length8(byte[] data, int p) {
        int first = data[p] & 0xff;
        return (first & 0x80) == 0
                ? new int[]{first, p + 1}
                : new int[]{((first & 0x7f) << 8) | (data[p + 1] & 0xff), p + 2};
    }

    private static int[] length16(byte[] data, int p) {
        int first = u16(data, p);
        return (first & 0x8000) == 0
                ? new int[]{first, p + 2}
                : new int[]{((first & 0x7fff) << 16) | u16(data, p + 2), p + 4};
    }

    private static int u16(byte[] d, int p) {
        return (d[p] & 0xff) | ((d[p + 1] & 0xff) << 8);
    }

    private static int i32(byte[] d, int p) {
        return u16(d, p) | (u16(d, p + 2) << 16);
    }

    private static void put32(byte[] d, int p, int value) {
        d[p] = (byte) value;
        d[p + 1] = (byte) (value >>> 8);
        d[p + 2] = (byte) (value >>> 16);
        d[p + 3] = (byte) (value >>> 24);
    }
}
