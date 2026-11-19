package dev.brokoli.pipeline;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The options of a {@code sink_api} node, which sends its input's rows to an HTTP endpoint as JSON, in batches.
 * Start from {@link #options()} and change what differs:
 *
 * <pre>{@code
 * SinkApi.options().url("/events").connId("events_api").batchSize(200)
 * }</pre>
 *
 * @param url the URL the rows go to, empty unless set; the server refuses a {@code sink_api} without one
 * @param method the requests' HTTP method, {@code "POST"} unless set
 * @param headers the requests' headers, none unless set
 * @param connId the connection whose base URL and credentials the requests use, if set
 * @param batchSize how many rows each request carries, 100 on the server unless set
 * @param authUser the user the requests authenticate as with HTTP Basic, which only comes with
 *        {@link #authPassword()}
 * @param authPassword the password the requests authenticate with, which only comes with {@link #authUser()}
 * @param node the options every node has
 */
public record SinkApi(String url, String method, Map<String, String> headers, Optional<String> connId,
        OptionalInt batchSize, Optional<String> authUser, Optional<String> authPassword, NodeOptions node)
        implements WithPool<SinkApi> {

    private static final String POST = "POST";

    /**
     * Creates the options, with a copy of the headers.
     *
     * @param url the URL
     * @param method the HTTP method
     * @param headers the headers
     * @param connId the connection, if set
     * @param batchSize the rows per request, if set
     * @param authUser the Basic user, if set
     * @param authPassword the Basic password, if set
     * @param node the options every node has
     * @throws IllegalArgumentException if the batch size is not positive, or only one of the user and the password
     *         is set
     * @throws NullPointerException if a value, or a name or value in the headers, is {@code null}
     */
    public SinkApi {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(method, "method");
        headers = Map.copyOf(headers);
        Objects.requireNonNull(connId, "connId");
        Objects.requireNonNull(batchSize, "batchSize");
        Objects.requireNonNull(authUser, "authUser");
        Objects.requireNonNull(authPassword, "authPassword");
        Objects.requireNonNull(node, "node");
        batchSize.ifPresent(size -> {
            if (size <= 0) {
                throw new IllegalArgumentException("batch size " + size + " is not positive");
            }
        });
        if (authUser.isPresent() != authPassword.isPresent()) {
            throw new IllegalArgumentException("HTTP Basic needs both a user and a password; give them with"
                    + " basicAuth(user, password)");
        }
    }

    /**
     * The default options: no URL, {@code POST}, no headers, connection, batch size or credentials, and
     * {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static SinkApi options() {
        return new SinkApi("", POST, Map.of(), Optional.empty(), OptionalInt.empty(), Optional.empty(),
                Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with another URL.
     *
     * @param url the URL, such as {@code "https://sink.example.com/ingest"}, or a path such as {@code "/events"}
     *        added to the connection's base URL
     * @return the new options
     */
    public SinkApi url(String url) {
        return new SinkApi(url, method, headers, connId, batchSize, authUser, authPassword, node);
    }

    /**
     * These options with another HTTP method.
     *
     * @param method the method, such as {@code "PUT"}
     * @return the new options
     */
    public SinkApi method(String method) {
        return new SinkApi(url, method, headers, connId, batchSize, authUser, authPassword, node);
    }

    /**
     * These options with other headers.
     *
     * @param headers the headers by name, such as {@code Map.of("X-Source", "brokoli")}
     * @return the new options
     */
    public SinkApi headers(Map<String, String> headers) {
        return new SinkApi(url, method, headers, connId, batchSize, authUser, authPassword, node);
    }

    /**
     * These options with the connection whose base URL and credentials the requests use. A connection with
     * credentials takes the place of {@link #basicAuth(String, String)}.
     *
     * @param connId the connection's id, such as {@code "events_api"}
     * @return the new options
     */
    public SinkApi connId(String connId) {
        return new SinkApi(url, method, headers, Optional.of(Objects.requireNonNull(connId, "connId")), batchSize,
                authUser, authPassword, node);
    }

    /**
     * These options with how many rows each request carries.
     *
     * @param batchSize the rows per request
     * @return the new options
     * @throws IllegalArgumentException if the batch size is not positive
     */
    public SinkApi batchSize(int batchSize) {
        return new SinkApi(url, method, headers, connId, OptionalInt.of(batchSize), authUser, authPassword, node);
    }

    /**
     * These options with the HTTP Basic credentials the requests send, without a connection.
     *
     * @param user the user, such as {@code "loader"}
     * @param password the password, such as {@code "${secret.events_password}"}
     * @return the new options
     */
    public SinkApi basicAuth(String user, String password) {
        return new SinkApi(url, method, headers, connId, batchSize, Optional.of(Objects.requireNonNull(user, "user")),
                Optional.of(Objects.requireNonNull(password, "password")), node);
    }

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     */
    @Override
    public SinkApi node(NodeOptions node) {
        return new SinkApi(url, method, headers, connId, batchSize, authUser, authPassword, node);
    }
}
