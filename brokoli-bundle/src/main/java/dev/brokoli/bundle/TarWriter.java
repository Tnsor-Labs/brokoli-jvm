package dev.brokoli.bundle;

import dev.brokoli.bundle.internal.Utf8;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class TarWriter {

    private static final int BLOCK = 512;
    private static final int END_BLOCKS = 2;

    private static final int NAME = 0;
    private static final int NAME_LENGTH = 100;
    private static final int MODE = 100;
    private static final int UID = 108;
    private static final int GID = 116;
    private static final int SMALL_NUMBER_LENGTH = 8;
    private static final int SIZE = 124;
    private static final int MTIME = 136;
    private static final int LARGE_NUMBER_LENGTH = 12;
    private static final int CHECKSUM = 148;
    private static final int CHECKSUM_DIGITS = 6;
    private static final int TYPEFLAG = 156;
    private static final int MAGIC = 257;
    private static final int DEVMAJOR = 329;
    private static final int DEVMINOR = 337;
    private static final int PREFIX = 345;
    private static final int PREFIX_LENGTH = 155;

    private static final byte[] USTAR_MAGIC_AND_VERSION = "ustar\u000000".getBytes(StandardCharsets.US_ASCII);
    private static final byte REGULAR = '0';
    private static final byte DIRECTORY = '5';
    private static final byte PAX_HEADER = 'x';
    private static final int FILE_MODE = 0600;
    private static final int DIRECTORY_MODE = 0700;
    private static final int OCTAL = 8;
    private static final byte SPACE = ' ';
    private static final byte SLASH = '/';
    private static final int FIRST_NON_ASCII = 0x80;

    private static final String PAX_DIRECTORY = "PaxHeaders.0/";
    private static final String PAX_PATH = "path";
    private static final int PAX_RECORD_PUNCTUATION = 3;

    private final OutputStream out;

    TarWriter(OutputStream out) {
        this.out = out;
    }

    void directory(String path) throws IOException {
        entry(path, DIRECTORY, DIRECTORY_MODE, new byte[0]);
    }

    void file(String path, byte[] body) throws IOException {
        entry(path, REGULAR, FILE_MODE, body);
    }

    void finish() throws IOException {
        out.write(new byte[BLOCK * END_BLOCKS]);
    }

    private void entry(String path, byte typeflag, int mode, byte[] body) throws IOException {
        byte[] name = Utf8.bytes(path);
        byte[] header;
        if (isAscii(name) && name.length <= NAME_LENGTH) {
            header = ustarHeader(name, new byte[0], typeflag, mode, body.length);
        } else {
            int split = ustarSplit(name);
            if (split > 0) {
                header = ustarHeader(Arrays.copyOfRange(name, split + 1, name.length),
                        Arrays.copyOfRange(name, 0, split), typeflag, mode, body.length);
            } else {
                writePaxHeader(path, name);
                header = ustarHeader(cut(asciiOnly(name)), new byte[0], typeflag, mode, body.length);
            }
        }
        out.write(header);
        writeData(body);
    }

    private static int ustarSplit(byte[] name) {
        if (name.length <= NAME_LENGTH || !isAscii(name)) {
            return -1;
        }
        int length = name.length;
        if (length > PREFIX_LENGTH + 1) {
            length = PREFIX_LENGTH + 1;
        } else if (name[length - 1] == SLASH) {
            length--;
        }
        int slash = lastSlash(name, length);
        int nameLength = name.length - slash - 1;
        if (slash <= 0 || nameLength > NAME_LENGTH || nameLength == 0 || slash > PREFIX_LENGTH) {
            return -1;
        }
        return slash;
    }

    private static int lastSlash(byte[] name, int before) {
        for (int i = before - 1; i >= 0; i--) {
            if (name[i] == SLASH) {
                return i;
            }
        }
        return -1;
    }

    private void writePaxHeader(String path, byte[] name) throws IOException {
        int lastSlash = path.lastIndexOf('/');
        String headerPath = path.substring(0, lastSlash + 1) + PAX_DIRECTORY + path.substring(lastSlash + 1);
        byte[] headerName = cut(asciiOnly(Utf8.bytes(headerPath)));
        int end = headerName.length;
        while (end > 0 && headerName[end - 1] == SLASH) {
            end--;
        }
        byte[] record = paxRecord(name);
        byte[] header = new byte[BLOCK];
        System.arraycopy(headerName, 0, header, NAME, end);
        octal(header, MODE, SMALL_NUMBER_LENGTH, 0);
        octal(header, UID, SMALL_NUMBER_LENGTH, 0);
        octal(header, GID, SMALL_NUMBER_LENGTH, 0);
        octal(header, SIZE, LARGE_NUMBER_LENGTH, record.length);
        octal(header, MTIME, LARGE_NUMBER_LENGTH, 0);
        header[TYPEFLAG] = PAX_HEADER;
        System.arraycopy(USTAR_MAGIC_AND_VERSION, 0, header, MAGIC, USTAR_MAGIC_AND_VERSION.length);
        checksum(header);
        out.write(header);
        writeData(record);
    }

    private static byte[] paxRecord(byte[] path) {
        int length = PAX_PATH.length() + path.length + PAX_RECORD_PUNCTUATION;
        length += Integer.toString(length).length();
        byte[] record = record(length, path);
        if (record.length != length) {
            record = record(record.length, path);
        }
        return record;
    }

    private static byte[] record(int length, byte[] path) {
        byte[] start = (length + " " + PAX_PATH + "=").getBytes(StandardCharsets.US_ASCII);
        byte[] record = Arrays.copyOf(start, start.length + path.length + 1);
        System.arraycopy(path, 0, record, start.length, path.length);
        record[record.length - 1] = '\n';
        return record;
    }

    private static byte[] ustarHeader(byte[] name, byte[] prefix, byte typeflag, int mode, long size) {
        byte[] header = new byte[BLOCK];
        System.arraycopy(name, 0, header, NAME, name.length);
        octal(header, MODE, SMALL_NUMBER_LENGTH, mode);
        octal(header, UID, SMALL_NUMBER_LENGTH, 0);
        octal(header, GID, SMALL_NUMBER_LENGTH, 0);
        octal(header, SIZE, LARGE_NUMBER_LENGTH, size);
        octal(header, MTIME, LARGE_NUMBER_LENGTH, 0);
        header[TYPEFLAG] = typeflag;
        System.arraycopy(USTAR_MAGIC_AND_VERSION, 0, header, MAGIC, USTAR_MAGIC_AND_VERSION.length);
        octal(header, DEVMAJOR, SMALL_NUMBER_LENGTH, 0);
        octal(header, DEVMINOR, SMALL_NUMBER_LENGTH, 0);
        System.arraycopy(prefix, 0, header, PREFIX, prefix.length);
        checksum(header);
        return header;
    }

    private static void octal(byte[] header, int offset, int length, long value) {
        String digits = Long.toOctalString(value);
        String padded = "0".repeat(Math.max(0, length - 1 - digits.length())) + digits;
        byte[] text = padded.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(text, 0, header, offset, text.length);
    }

    private static void checksum(byte[] header) {
        Arrays.fill(header, CHECKSUM, CHECKSUM + SMALL_NUMBER_LENGTH, SPACE);
        long sum = 0;
        for (byte b : header) {
            sum += b & 0xff;
        }
        octal(header, CHECKSUM, CHECKSUM_DIGITS + 1, sum);
        header[CHECKSUM + CHECKSUM_DIGITS] = 0;
    }

    private void writeData(byte[] body) throws IOException {
        out.write(body);
        int remainder = body.length % BLOCK;
        if (remainder != 0) {
            out.write(new byte[BLOCK - remainder]);
        }
    }

    private static boolean isAscii(byte[] text) {
        for (byte b : text) {
            if (b == 0 || (b & 0xff) >= FIRST_NON_ASCII) {
                return false;
            }
        }
        return true;
    }

    private static byte[] asciiOnly(byte[] text) {
        byte[] ascii = new byte[text.length];
        int length = 0;
        for (byte b : text) {
            if (b != 0 && (b & 0xff) < FIRST_NON_ASCII) {
                ascii[length++] = b;
            }
        }
        return Arrays.copyOf(ascii, length);
    }

    private static byte[] cut(byte[] name) {
        return name.length > NAME_LENGTH ? Arrays.copyOf(name, NAME_LENGTH) : name;
    }
}
