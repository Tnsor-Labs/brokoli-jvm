package dev.brokoli.bundle.internal;

import dev.brokoli.bundle.BundleException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Text as UTF-8, and the order of text by Unicode code point that core's canonical forms use. Used by the bundle
 * tooling and the pipeline builder; not part of the API.
 */
public final class Utf8 {

    /**
     * Orders text by Unicode code point, which is the order of its UTF-8 bytes. A character above U+FFFF sorts by
     * its code point, not by its UTF-16 surrogates as {@link String#compareTo(String)} would sort it.
     */
    public static final Comparator<String> ORDER =
            (left, right) -> Arrays.compareUnsigned(bytes(left), bytes(right));

    private Utf8() {
    }

    /**
     * Refuses text that cannot be written as UTF-8.
     *
     * @param text the text to check
     * @throws BundleException if the text has an unpaired surrogate
     */
    public static void requireValid(String text) {
        bytes(text);
    }

    /**
     * Encodes text as UTF-8.
     *
     * @param text the text to encode
     * @return its UTF-8 bytes
     * @throws BundleException if the text has an unpaired surrogate
     */
    public static byte[] bytes(String text) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text));
            return Arrays.copyOf(encoded.array(), encoded.limit());
        } catch (CharacterCodingException unpaired) {
            throw new BundleException("text is not valid Unicode, it has an unpaired surrogate: " + escaped(text));
        }
    }

    private static String escaped(String text) {
        StringBuilder out = new StringBuilder();
        text.chars().forEach(c -> {
            if (Character.isSurrogate((char) c)) {
                out.append(String.format("\\u%04x", c));
            } else {
                out.append((char) c);
            }
        });
        return out.toString();
    }
}
