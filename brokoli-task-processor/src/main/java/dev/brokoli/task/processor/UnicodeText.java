package dev.brokoli.task.processor;

import java.util.OptionalInt;

final class UnicodeText {

    private UnicodeText() {
    }

    static OptionalInt unpairedSurrogate(String text) {
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            if (Character.getType(codePoint) == Character.SURROGATE) {
                return OptionalInt.of(index);
            }
            index += Character.charCount(codePoint);
        }
        return OptionalInt.empty();
    }
}
