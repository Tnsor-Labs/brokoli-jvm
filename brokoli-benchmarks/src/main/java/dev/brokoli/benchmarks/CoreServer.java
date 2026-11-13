package dev.brokoli.benchmarks;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

final class CoreServer {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON_TYPE = "application/json";
    private static final String ARCHIVE_TYPE = "application/octet-stream";
    private static final String POST = "POST";
    private static final String GET = "GET";
    private static final String ID = "id";
    private static final String STATUS = "status";
    private static final String ERROR = "error";
    private static final String SUCCESS = "success";
    private static final Set<String> FINISHED = Set.of(SUCCESS, "failed", "cancelled");
    private static final int FIRST_SUCCESS_CODE = 200;
    private static final int LAST_SUCCESS_CODE = 299;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
    private static final String DIGEST_PREFIX = "sha256:";
    private static final String SHA_256 = "SHA-256";
    private static final String SERVER = "BROKOLI_E2E_SERVER";
    private static final String API_KEY = "BROKOLI_E2E_API_KEY";

    private final HttpClient http = HttpClient.newHttpClient();
    private final String server;
    private final String apiKey;

    CoreServer(String server, String apiKey) {
        this.server = server;
        this.apiKey = apiKey;
    }

    static CoreServer fromEnvironment() {
        return new CoreServer(requiredEnv(SERVER), requiredEnv(API_KEY));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new BenchmarkException(name + " is not set: the benchmarks on core need a running core server");
        }
        return value;
    }

    String upload(byte[] archive) throws IOException, InterruptedException {
        String digest = DIGEST_PREFIX + HexFormat.of().formatHex(sha256(archive));
        send(POST, "/api/task-bundles-v2/" + digest, HttpRequest.BodyPublishers.ofByteArray(archive), ARCHIVE_TYPE);
        return digest;
    }

    private static byte[] sha256(byte[] archive) {
        try {
            return MessageDigest.getInstance(SHA_256).digest(archive);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(SHA_256 + " is not available", missing);
        }
    }

    String createPipeline(Map<String, Object> pipeline) throws IOException, InterruptedException {
        return post("/api/pipelines", pipeline).path(ID).asString();
    }

    String run(String pipelineId) throws IOException, InterruptedException {
        return post("/api/pipelines/" + pipelineId + "/run", Map.of()).path(ID).asString();
    }

    JsonNode awaitSuccess(String runId, Duration wait) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + wait.toNanos();
        JsonNode run = get("/api/runs/" + runId);
        while (!FINISHED.contains(run.path(STATUS).asString()) && System.nanoTime() < deadline) {
            Thread.sleep(POLL_INTERVAL.toMillis());
            run = get("/api/runs/" + runId);
        }
        String status = run.path(STATUS).asString();
        if (!FINISHED.contains(status)) {
            throw new BenchmarkException("run " + runId + " did not finish within " + wait.toSeconds()
                    + " s, its last status was " + status);
        }
        if (!SUCCESS.equals(status)) {
            throw new BenchmarkException("run " + runId + " ended " + status + ": " + run.path(ERROR).asString());
        }
        return run;
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        return send(GET, path, HttpRequest.BodyPublishers.noBody(), JSON_TYPE);
    }

    private JsonNode post(String path, Object body) throws IOException, InterruptedException {
        return send(POST, path, HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)), JSON_TYPE);
    }

    private JsonNode send(String method, String path, HttpRequest.BodyPublisher body, String contentType)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(server + path))
                .timeout(REQUEST_TIMEOUT)
                .header(AUTHORIZATION, BEARER + apiKey)
                .header(CONTENT_TYPE, contentType)
                .method(method, body)
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < FIRST_SUCCESS_CODE || response.statusCode() > LAST_SUCCESS_CODE) {
            throw new BenchmarkException(method + " " + path + " returned " + response.statusCode() + ": "
                    + response.body());
        }
        return JSON.readTree(response.body());
    }
}
