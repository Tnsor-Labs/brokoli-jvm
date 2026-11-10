package dev.brokoli.bundle;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

final class Utf8 {

    static final Comparator<String> ORDER = (left, right) -> Arrays.compareUnsigned(bytes(left), bytes(right));

    private Utf8() {
    }

    static void requireValid(String text) {
        bytes(text);
    }

    static byte[] bytes(String text) {
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
