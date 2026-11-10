package dev.brokoli.task.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskNamesTest {

    @Test
    void splitsACamelCaseMethodNameIntoTitleCaseWords() {
        assertThat(TaskNames.displayName("dailyRollup")).isEqualTo("Daily Rollup");
        assertThat(TaskNames.displayName("score")).isEqualTo("Score");
        assertThat(TaskNames.displayName("x")).isEqualTo("X");
    }

    @Test
    void treatsUnderscoresAsPythonDoesBeforeTitleCasing() {
        assertThat(TaskNames.displayName("daily_rollup")).isEqualTo("Daily Rollup");
    }

    @Test
    void keepsAnAcronymAsOneWordAndTitleCasesItLikePython() {
        assertThat(TaskNames.displayName("parseHTTPResponse")).isEqualTo("Parse Http Response");
    }

    @Test
    void capitalizesALetterThatFollowsADigitLikePythonsTitle() {
        assertThat(TaskNames.displayName("load2Db")).isEqualTo("Load2 Db");
        assertThat(TaskNames.displayName("load2db")).isEqualTo("Load2Db");
    }
}
