package dev.brokoli.bundle;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class Digests {

    static final String PREFIX = "sha256:";

    private static final String ALGORITHM = "SHA-256";
    private static final byte PATH_END = 0;

    private Digests() {
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("every JDK provides " + ALGORITHM, missing);
        }
    }

    static String sha256Hex(byte[] body) {
        return HexFormat.of().formatHex(sha256().digest(body));
    }

    static String digest(byte[] body) {
        return PREFIX + sha256Hex(body);
    }

    static String sourceDigest(List<BundleFile> sortedFiles) {
        MessageDigest content = sha256();
        for (BundleFile file : sortedFiles) {
            content.update(Utf8.bytes(file.path()));
            content.update(PATH_END);
            content.update(file.body());
        }
        return PREFIX + HexFormat.of().formatHex(content.digest());
    }
}
