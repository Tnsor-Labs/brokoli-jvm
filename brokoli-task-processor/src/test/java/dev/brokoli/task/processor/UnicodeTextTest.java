package dev.brokoli.task.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnicodeTextTest {

    @Test
    void acceptsPlainTextAndSurrogatePairs() {
        assertThat(UnicodeText.unpairedSurrogate("")).isEmpty();
        assertThat(UnicodeText.unpairedSurrogate("eu-west")).isEmpty();
        assertThat(UnicodeText.unpairedSurrogate("😀 and 😀")).isEmpty();
    }

    @Test
    void findsTheIndexOfAnUnpairedHighOrLowSurrogate() {
        assertThat(UnicodeText.unpairedSurrogate("a\uD800b")).hasValue(1);
        assertThat(UnicodeText.unpairedSurrogate("ab\uDC00")).hasValue(2);
        assertThat(UnicodeText.unpairedSurrogate("😀\uD83D")).hasValue(2);
        assertThat(UnicodeText.unpairedSurrogate("\uDE00\uD83D")).hasValue(0);
    }
}
