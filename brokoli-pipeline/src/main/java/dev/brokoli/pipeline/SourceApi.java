package dev.brokoli.pipeline;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The options of a {@code source_api} node, which reads the records an HTTP endpoint returns as a dataset. Start
 * from {@link #options()} and change what differs:
 *
 * <pre>{@code
 * SourceApi.options()
 *         .url("https://api.example.com/items")
 *         .params(Map.of("status", "active"))
 *         .records("data.items")
 *         .timeout(Duration.ofSeconds(30))
 * }</pre>
 *
 * <p>The node writes {@code "response": "dataset"}: the body is, or holds at {@link #records()}, the list of
 * records. Without a records path, the server finds the records in the body itself.
 *
 * @param url the URL the request goes to, empty unless set; the server refuses a {@code source_api} without one
 * @param method the request's HTTP method, {@code "GET"} unless set
 * @param headers the request's headers, none unless set
 * @param params the query parameters the server adds to the URL, none unless set
 * @param body the request's body, if set: a {@code String}, a {@code Boolean}, an {@code Integer}, a {@code Long}, a
 *        {@code BigInteger}, a finite {@code Double}, a {@code List}, or a {@code Map} with {@code String} keys,
 *        whose values may also be {@code null}
 * @param connId the connection whose base URL and credentials the request uses, if set
 * @param records the dot path to the list of records in the body, such as {@code "data.items"}, if set
 * @param sourceType the fetcher the server reads the endpoint with, {@code "rest"} on the server unless set
 * @param schema the columns of the records, if declared
 * @param node the options every node has
 */
public record SourceApi(String url, String method, Map<String, String> headers, Map<String, String> params,
        Optional<Object> body, Optional<String> connId, Optional<String> records, Optional<String> sourceType,
        Optional<DatasetSchema> schema, NodeOptions node) implements WithPool<SourceApi> {

    private static final String GET = "GET";

    /**
     * Creates the options, with a copy of the headers and the query parameters.
     *
     * @param url the URL
     * @param method the HTTP method
     * @param headers the headers
     * @param params the query parameters
     * @param body the body, if set
     * @param connId the connection, if set
     * @param records the records path, if set
     * @param sourceType the fetcher, if set
     * @param schema the columns of the records, if declared
     * @param node the options every node has
     * @throws NullPointerException if a value, or a name or value in the headers or the query parameters, is
     *         {@code null}
     */
    public SourceApi {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(method, "method");
        headers = Map.copyOf(headers);
        params = Map.copyOf(params);
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(connId, "connId");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(node, "node");
    }

    /**
     * The default options: no URL, {@code GET}, no headers, query parameters, body, connection, records path,
     * fetcher or schema, and {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static SourceApi options() {
        return new SourceApi("", GET, Map.of(), Map.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with another URL.
     *
     * @param url the URL, such as {@code "https://api.example.com/items"}, or a path such as {@code "/items"}
     *        added to the connection's base URL
     * @return the new options
     */
    public SourceApi url(String url) {
        return new SourceApi(url, method, headers, params, body, connId, records, sourceType, schema, node);
    }

    /**
     * These options with another HTTP method.
     *
     * @param method the method, such as {@code "POST"}
     * @return the new options
     */
    public SourceApi method(String method) {
        return new SourceApi(url, method, headers, params, body, connId, records, sourceType, schema, node);
    }

    /**
     * These options with other headers.
     *
     * @param headers the headers by name, such as {@code Map.of("Accept", "application/json")}
     * @return the new options
     */
    public SourceApi headers(Map<String, String> headers) {
        return new SourceApi(url, method, headers, params, body, connId, records, sourceType, schema, node);
    }

    /**
     * These options with other query parameters.
     *
     * @param params the query parameters by name, such as {@code Map.of("status", "active")}
     * @return the new options
     */
    public SourceApi params(Map<String, String> params) {
        return new SourceApi(url, method, headers, params, body, connId, records, sourceType, schema, node);
    }

    /**
     * These options with a request body, written as JSON.
     *
     * @param body the body, such as {@code Map.of("query", "{ items { id } }")}
     * @return the new options
     */
    public SourceApi body(Object body) {
        return new SourceApi(url, method, headers, params, Optional.of(Objects.requireNonNull(body, "body")), connId,
                records, sourceType, schema, node);
    }

    /**
     * These options with the connection whose base URL and credentials the request uses.
     *
     * @param connId the connection's id, such as {@code "items_api"}
     * @return the new options
     */
    public SourceApi connId(String connId) {
        return new SourceApi(url, method, headers, params, body, Optional.of(Objects.requireNonNull(connId,
                "connId")), records, sourceType, schema, node);
    }

    /**
     * These options with the dot path to the list of records in the body.
     *
     * @param records the path, such as {@code "results"} or {@code "data.items"}
     * @return the new options
     */
    public SourceApi records(String records) {
        return new SourceApi(url, method, headers, params, body, connId, Optional.of(Objects.requireNonNull(records,
                "records")), sourceType, schema, node);
    }

    /**
     * These options with the fetcher the server reads the endpoint with.
     *
     * @param sourceType the fetcher's name, such as {@code "rest"}
     * @return the new options
     */
    public SourceApi sourceType(String sourceType) {
        return new SourceApi(url, method, headers, params, body, connId, records,
                Optional.of(Objects.requireNonNull(sourceType, "sourceType")), schema, node);
    }

    /**
     * These options with the columns of the records the endpoint returns. A {@code join} of two nodes that declare
     * a schema writes the schema of its output.
     *
     * @param schema the columns, such as {@code DatasetSchema.of(Column.of("id", DataType.int64())).closed()}
     * @return the new options
     */
    public SourceApi schema(DatasetSchema schema) {
        return new SourceApi(url, method, headers, params, body, connId, records, sourceType,
                Optional.of(Objects.requireNonNull(schema, "schema")), node);
    }

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     */
    @Override
    public SourceApi node(NodeOptions node) {
        return new SourceApi(url, method, headers, params, body, connId, records, sourceType, schema, node);
    }
}
