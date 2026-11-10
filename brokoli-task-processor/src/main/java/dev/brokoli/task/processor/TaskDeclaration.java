package dev.brokoli.task.processor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

record TaskDeclaration(String method, String name, Map<String, Object> nodeInterface,
        Map<String, Object> parameters) {

    TaskDeclaration {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parameters, "parameters");
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }
}
