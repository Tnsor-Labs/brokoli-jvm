package dev.brokoli.pipeline;

import java.util.Locale;
import java.util.regex.Pattern;

final class NodeIds {

    private static final int LONGEST_BASE = 20;
    private static final String FALLBACK = "node";
    private static final char SPACE = ' ';
    private static final char UNDERSCORE = '_';
    private static final char HYPHEN = '-';
    private static final Pattern NOT_SLUG = Pattern.compile("[^a-z0-9-]");
    private static final Pattern HYPHENS = Pattern.compile("-+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    private NodeIds() {
    }

    static String base(String name) {
        StringBuilder kept = new StringBuilder();
        int count = 0;
        for (int codePoint : name.toLowerCase(Locale.ROOT).replace(SPACE, UNDERSCORE).codePoints().toArray()) {
            if (count == LONGEST_BASE) {
                break;
            }
            if (isAlphanumeric(codePoint) || codePoint == UNDERSCORE) {
                kept.appendCodePoint(codePoint);
                count++;
            }
        }
        return kept.isEmpty() ? FALLBACK : kept.toString();
    }

    static String pipelineId(String name) {
        String slug = NOT_SLUG.matcher(name.toLowerCase(Locale.ROOT).replace(SPACE, HYPHEN)).replaceAll("");
        return EDGE_HYPHENS.matcher(HYPHENS.matcher(slug).replaceAll(String.valueOf(HYPHEN))).replaceAll("");
    }

    private static boolean isAlphanumeric(int codePoint) {
        if (Character.isLetter(codePoint)) {
            return true;
        }
        int type = Character.getType(codePoint);
        return type == Character.DECIMAL_DIGIT_NUMBER || type == Character.LETTER_NUMBER
                || type == Character.OTHER_NUMBER;
    }
}
