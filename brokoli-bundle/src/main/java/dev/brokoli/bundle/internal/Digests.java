package dev.brokoli.bundle.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 digests in the {@code sha256:<hex>} form core uses for bundles, interfaces and pipelines. Used by the
 * bundle tooling and the pipeline builder; not part of the API.
 */
public final class Digests {

    /**
     * The prefix of every digest, {@code "sha256:"}.
     */
    public static final String PREFIX = "sha256:";

    private static final String ALGORITHM = "SHA-256";

    private Digests() {
    }

    /**
     * A new SHA-256 message digest.
     *
     * @return the message digest
     */
    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("every JDK provides " + ALGORITHM, missing);
        }
    }

    /**
     * The SHA-256 of bytes, as lowercase hex.
     *
     * @param body the bytes to hash
     * @return 64 lowercase hex characters
     */
    public static String sha256Hex(byte[] body) {
        return HexFormat.of().formatHex(sha256().digest(body));
    }

    /**
     * The digest of bytes, as {@code sha256:} and their SHA-256 in lowercase hex.
     *
     * @param body the bytes to hash
     * @return the digest
     */
    public static String digest(byte[] body) {
        return PREFIX + sha256Hex(body);
    }

    /**
     * The digest of what a message digest has read so far, as {@code sha256:} and its SHA-256 in lowercase hex.
     *
     * @param content a SHA-256 message digest
     * @return the digest
     */
    public static String digest(MessageDigest content) {
        return PREFIX + HexFormat.of().formatHex(content.digest());
    }
}
