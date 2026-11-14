package dev.brokoli.bundle;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class TaskFile {

    static final String DIRECTORY = "META-INF/brokoli/tasks/";
    static final String FORMAT = "brokoli.jvm-tasks/v1";

    private static final String FORMAT_KEY = "format";
    private static final String CLASS = "class";
    private static final String TASKS = "tasks";
    private static final String METHOD = "method";
    private static final String ENTRYPOINT = "entrypoint";
    private static final String NAME = "name";
    private static final String INTERFACE = "interface";
    private static final String PARAMETERS = "parameters";
    private static final char ENTRYPOINT_SEPARATOR = '#';

    static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private TaskFile() {
    }

    static List<TaskEntry> read(String path, byte[] body) {
        JsonNode document = parse(path, body);
        if (!FORMAT.equals(document.path(FORMAT_KEY).asString(null))) {
            throw new BundleException("task file " + path + " has format " + document.path(FORMAT_KEY)
                    + ", expected \"" + FORMAT + "\"; rebuild with the brokoli-task-processor of this version");
        }
        String owner = text(path, document, CLASS);
        JsonNode tasks = document.path(TASKS);
        if (!tasks.isArray()) {
            throw new BundleException("task file " + path + " has no \"" + TASKS + "\" list");
        }
        List<TaskEntry> entries = new ArrayList<>();
        for (JsonNode task : tasks.values()) {
            entries.add(entry(path, owner, task));
        }
        return entries;
    }

    private static TaskEntry entry(String path, String owner, JsonNode task) {
        String method = text(path, task, METHOD);
        String entrypoint = text(path, task, ENTRYPOINT);
        int separator = entrypoint.indexOf(ENTRYPOINT_SEPARATOR);
        if (separator <= 0 || separator != entrypoint.lastIndexOf(ENTRYPOINT_SEPARATOR)
                || separator == entrypoint.length() - 1) {
            throw new BundleException("task " + owner + "." + method + " in " + path + " has entrypoint \""
                    + entrypoint + "\", expected Class#method");
        }
        return new TaskEntry(owner, method, entrypoint.substring(0, separator), entrypoint.substring(separator + 1),
                text(path, task, NAME), objectOrNull(path, owner, method, task, INTERFACE),
                objectOrNull(path, owner, method, task, PARAMETERS));
    }

    private static JsonNode objectOrNull(String path, String owner, String method, JsonNode task, String key) {
        JsonNode value = task.path(key);
        if (value.isNull()) {
            return null;
        }
        if (!value.isObject()) {
            throw new BundleException("task " + owner + "." + method + " in " + path + " has an \"" + key
                    + "\" that is neither an object nor null");
        }
        return value;
    }

    private static JsonNode parse(String path, byte[] body) {
        try {
            JsonNode document = JSON.readTree(body);
            if (document == null || !document.isObject()) {
                throw new BundleException("task file " + path + " is not a JSON object");
            }
            return document;
        } catch (JacksonException notJson) {
            throw new BundleException("task file " + path + " is not valid JSON: " + notJson.getOriginalMessage(),
                    notJson);
        }
    }

    private static String text(String path, JsonNode object, String key) {
        JsonNode value = object.path(key);
        if (!value.isString() || value.stringValue().isEmpty()) {
            throw new BundleException("task file " + path + " has no \"" + key + "\" text");
        }
        return value.stringValue();
    }

    record TaskEntry(String owner, String method, String module, String symbol, String name,
            JsonNode nodeInterface, JsonNode parameters) {

        TaskEntry {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(name, "name");
        }
    }
}
