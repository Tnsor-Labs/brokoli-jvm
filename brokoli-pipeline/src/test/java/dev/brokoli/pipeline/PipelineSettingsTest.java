package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineSettingsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId MAPUTO = ZoneId.of("Africa/Maputo");

    @Test
    void writesTheSettingsAfterTheDependenciesInThePythonSdksOrder() {
        Pipeline p = Pipeline.builder("Hourly")
                .webhook(true)
                .onFailure("https://hooks.example/oncall")
                .sla(LocalTime.of(7, 30), MAPUTO)
                .catchUp(true)
                .scheduleTimezone(MAPUTO)
                .dependsOn("upstream-etl", "lookups")
                .schedule("0 * * * *")
                .build();

        assertThat(p.toJson()).isEqualTo("""
                {"pipeline_id":"hourly","name":"Hourly","description":"","schedule":"0 * * * *","enabled":true,\
                "ir_version":"2.0","nodes":[],"edges":[],"tags":[],"depends_on":["upstream-etl","lookups"],\
                "schedule_timezone":"Africa/Maputo","catchup":true,"sla_deadline":"07:30",\
                "sla_timezone":"Africa/Maputo","hooks":{"on_failure":{"type":"webhook",\
                "url":"https://hooks.example/oncall","enabled":true}},"webhook_token":""}""");
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void writesTheHooksInTheOrderOfTheirEventsWithTheirSettings() {
        Pipeline p = Pipeline.builder("Hooks")
                .onFailure(Hook.email("https://mail.example/send").disabled())
                .onSuccess(Hook.slack("https://hooks.slack.example/x").extra(Map.of("channel", "#etl")))
                .onStart("https://hooks.example/start")
                .build();

        assertThat(ir(p).path("hooks").toString()).isEqualTo("""
                {"on_start":{"type":"webhook","url":"https://hooks.example/start","enabled":true},\
                "on_success":{"type":"slack","url":"https://hooks.slack.example/x","enabled":true,\
                "extra":{"channel":"#etl"}},\
                "on_failure":{"type":"email","url":"https://mail.example/send","enabled":false}}""");
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void leavesOutEverySettingThatIsNotSet() {
        JsonNode ir = ir(Pipeline.builder("Plain").build());

        assertThat(ir.propertyNames()).containsExactly("pipeline_id", "name", "description", "schedule", "enabled",
                "ir_version", "nodes", "edges", "tags", "depends_on");
    }

    @Test
    void readsAnSlaWithoutAZoneInUtcAsThePythonSdkDoes() {
        JsonNode ir = ir(Pipeline.builder("Sla").sla(LocalTime.of(6, 5)).build());

        assertThat(ir.path("sla_deadline").stringValue()).isEqualTo("06:05");
        assertThat(ir.path("sla_timezone").stringValue()).isEqualTo("UTC");
    }

    @Test
    void writesTheUtcOffsetAsTheUtcZone() {
        JsonNode ir = ir(Pipeline.builder("Utc").scheduleTimezone(ZoneOffset.UTC).build());

        assertThat(ir.path("schedule_timezone").stringValue()).isEqualTo("UTC");
    }

    @Test
    void refusesAnSlaDeadlineWithSeconds() {
        Pipeline.Builder builder = Pipeline.builder("Sla");

        assertThatThrownBy(() -> builder.sla(LocalTime.of(7, 30, 15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SLA deadline 07:30:15 has seconds; the server takes hours and minutes");
    }

    @Test
    void refusesAZoneThatIsNotAnIanaTimeZone() {
        Pipeline.Builder builder = Pipeline.builder("Zones");

        assertThatThrownBy(() -> builder.scheduleTimezone(ZoneOffset.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("schedule time zone +02:00 is not an IANA time zone, which the server takes, such as"
                        + " Africa/Maputo or UTC");
        assertThatThrownBy(() -> builder.sla(LocalTime.NOON, ZoneId.of("GMT+2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("SLA time zone GMT+02:00 is not an IANA time zone");
    }

    @Test
    void refusesCatchUpWithoutAScheduleAndTakesItWithOne() {
        Pipeline.Builder unscheduled = Pipeline.builder("Backfill").catchUp(true);

        assertThatThrownBy(unscheduled::build)
                .isInstanceOf(PipelineException.class)
                .hasMessage("pipeline Backfill catches up but has no schedule; catch-up runs the schedule intervals"
                        + " the server missed, so give it a schedule");
        assertThat(ir(unscheduled.schedule("@daily").build()).path("catchup").booleanValue()).isTrue();
    }

    @Test
    void refusesAHookOfAnotherTypeOrWithoutAUrl() {
        assertThatThrownBy(() -> new Hook("pagerduty", "https://events.example", true, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("hook type pagerduty is not one of webhook, slack, email");
        assertThatThrownBy(() -> Hook.slack(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a slack hook needs a url");
    }

    private static JsonNode ir(Pipeline p) {
        return JSON.readTree(p.toJson());
    }
}
