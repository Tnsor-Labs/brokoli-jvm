package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotifyTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SLACK = "https://hooks.slack.com/services/T0/B0/x";

    @Test
    void writesASlackMessageInThePythonSdksOrderWithItsChannel() {
        Pipeline p = Pipeline.builder("Alert").build();

        p.notify("Alert", Notify.slack(SLACK)
                .timeout(Duration.ofSeconds(10))
                .retries(2)
                .channel("#data-alerts")
                .message("Pipeline {{pipeline}} pushed {{rows}} rows"));

        assertThat(config(p).toString()).isEqualTo("""
                {"notify_type":"slack","webhook_url":"https://hooks.slack.com/services/T0/B0/x",\
                "message":"Pipeline {{pipeline}} pushed {{rows}} rows","channel":"#data-alerts","max_retries":2,\
                "retry_backoff":"exponential","timeout":10}""");
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void postsToAWebhookAfterItsInputAsAComputeNode() {
        Pipeline p = Pipeline.builder("Alert").build();
        DatasetRef rows = p.sourceDb("Orders", SourceDb.options().query("SELECT 1"));

        p.notify("Tell", rows, Notify.webhook("https://hooks.example.com/notify"));

        JsonNode ir = JSON.readTree(p.toJson());
        assertThat(ir.path("nodes").get(1).path("config").toString()).isEqualTo("""
                {"notify_type":"webhook","webhook_url":"https://hooks.example.com/notify"}""");
        assertThat(ir.path("nodes").get(1).path("capabilities")).isEqualTo(JSON.readTree("""
                ["compute"]"""));
        assertThat(ir.path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"orders_1","to":"tell_1"}]"""));
    }

    @Test
    void refusesAChannelForAWebhook() {
        assertThatThrownBy(() -> Notify.webhook("https://hooks.example.com/notify").channel("#data-alerts"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the channel #data-alerts is only sent to Slack, but the notify type is webhook");
    }

    @Test
    void refusesAnEmptyUrl() {
        assertThatThrownBy(() -> Notify.slack(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a slack notify node needs a url");
        assertThatThrownBy(() -> Notify.webhook(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a webhook notify node needs a url");
    }

    @Test
    void refusesAPoolAsThePythonSdkHasNone() {
        assertThatThrownBy(() -> Notify.webhook("https://hooks.example.com/notify")
                .node(NodeOptions.defaults().pool("alerts")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a notify node runs in no pool, but its options have the pool alerts");
        assertThatThrownBy(() -> new Notify(NotifyType.SLACK, SLACK, Optional.empty(), Optional.empty(),
                NodeOptions.defaults().pool("alerts")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static JsonNode config(Pipeline p) {
        return JSON.readTree(p.toJson()).path("nodes").get(0).path("config");
    }
}
