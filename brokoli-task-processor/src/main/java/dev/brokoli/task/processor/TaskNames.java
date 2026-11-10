package dev.brokoli.task.processor;

final class TaskNames {

    private static final char UNDERSCORE = '_';
    private static final char SPACE = ' ';

    private TaskNames() {
    }

    static String displayName(String methodName) {
        return titleCase(splitWords(methodName));
    }

    private static String splitWords(String methodName) {
        StringBuilder words = new StringBuilder(methodName.length() + 8);
        for (int i = 0; i < methodName.length(); i++) {
            char current = methodName.charAt(i);
            if (current == UNDERSCORE) {
                words.append(SPACE);
                continue;
            }
            if (i > 0 && startsWord(methodName, i)) {
                words.append(SPACE);
            }
            words.append(current);
        }
        return words.toString();
    }

    private static boolean startsWord(String name, int index) {
        char current = name.charAt(index);
        char previous = name.charAt(index - 1);
        if (!Character.isUpperCase(current)) {
            return false;
        }
        if (Character.isLowerCase(previous) || Character.isDigit(previous)) {
            return true;
        }
        boolean nextIsLower = index + 1 < name.length() && Character.isLowerCase(name.charAt(index + 1));
        return Character.isUpperCase(previous) && nextIsLower;
    }

    private static String titleCase(String words) {
        StringBuilder titled = new StringBuilder(words.length());
        boolean previousIsLetter = false;
        for (int i = 0; i < words.length(); i++) {
            char current = words.charAt(i);
            boolean letter = Character.isLetter(current);
            if (letter) {
                titled.append(previousIsLetter ? Character.toLowerCase(current) : Character.toUpperCase(current));
            } else {
                titled.append(current);
            }
            previousIsLetter = letter;
        }
        return titled.toString();
    }
}
