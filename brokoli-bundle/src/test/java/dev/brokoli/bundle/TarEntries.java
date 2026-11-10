package dev.brokoli.bundle;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TarEntries {

    private static final int BLOCK = 512;
    private static final String PAX_PATH = " path=";

    private TarEntries() {
    }

    record Entry(String path, char type, String mode, byte[] body) {
    }

    static List<Entry> read(byte[] tar) {
        List<Entry> entries = new ArrayList<>();
        String paxPath = null;
        int offset = 0;
        while (offset + BLOCK <= tar.length && tar[offset] != 0) {
            byte[] header = Arrays.copyOfRange(tar, offset, offset + BLOCK);
            int size = Integer.parseInt(field(header, 124, 12).trim(), 8);
            byte[] body = Arrays.copyOfRange(tar, offset + BLOCK, offset + BLOCK + size);
            offset += BLOCK + (size + BLOCK - 1) / BLOCK * BLOCK;
            char type = (char) header[156];
            if (type == 'x') {
                String record = new String(body, StandardCharsets.UTF_8);
                paxPath = record.substring(record.indexOf(PAX_PATH) + PAX_PATH.length(), record.length() - 1);
                continue;
            }
            String prefix = field(header, 345, 155);
            String name = field(header, 0, 100);
            String path = paxPath != null ? paxPath : prefix.isEmpty() ? name : prefix + "/" + name;
            paxPath = null;
            entries.add(new Entry(path, type, field(header, 100, 8), body));
        }
        return entries;
    }

    static Map<String, byte[]> files(byte[] tar) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (Entry entry : read(tar)) {
            if (entry.type() == '0') {
                files.put(entry.path(), entry.body());
            }
        }
        return files;
    }

    private static String field(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }
}
