package dev.brokoli.bundle;

import dev.brokoli.bundle.Manifest.FileEntry;
import dev.brokoli.bundle.Manifest.Payload;

import java.util.List;
import java.util.function.BiConsumer;

final class ManifestJson {

    private static final int FIRST_PRINTABLE = 0x20;
    private static final char LINE_SEPARATOR = '\u2028';
    private static final char PARAGRAPH_SEPARATOR = '\u2029';

    private final StringBuilder json = new StringBuilder();

    private ManifestJson() {
    }

    static byte[] write(Manifest manifest) {
        ManifestJson writer = new ManifestJson();
        writer.manifest(manifest);
        return Utf8.bytes(writer.json.toString());
    }

    private void manifest(Manifest manifest) {
        json.append('{');
        field("format", Manifest.FORMAT);
        json.append(',');
        field("name", manifest.name());
        json.append(',');
        field("interface_digest", manifest.interfaceDigest());
        json.append(',');
        field("source_digest", manifest.sourceDigest());
        if (manifest.builder() != null && !manifest.builder().isEmpty()) {
            json.append(',');
            key("build");
            json.append('{');
            field("builder", manifest.builder());
            json.append('}');
        }
        json.append(',');
        key("payloads");
        array(manifest.payloads(), ManifestJson::payload);
        json.append(',');
        key("files");
        array(manifest.files(), ManifestJson::file);
        json.append('}');
    }

    private void payload(Payload payload) {
        json.append('{');
        field("id", payload.id());
        json.append(',');
        field("runtime", payload.runtime());
        json.append(',');
        field("os", payload.os());
        json.append(',');
        field("arch", payload.arch());
        json.append(',');
        key("entrypoint");
        json.append('{');
        field("module", payload.module());
        json.append(',');
        field("symbol", payload.symbol());
        json.append('}');
        json.append(',');
        field("effects", payload.effects());
        json.append(',');
        field("payload_digest", payload.payloadDigest());
        json.append('}');
    }

    private void file(FileEntry file) {
        json.append('{');
        field("path", file.path());
        json.append(',');
        key("size");
        json.append(file.size());
        json.append(',');
        field("sha256", file.sha256());
        json.append('}');
    }

    private <T> void array(List<T> items, BiConsumer<ManifestJson, T> item) {
        json.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            item.accept(this, items.get(i));
        }
        json.append(']');
    }

    private void field(String key, String value) {
        key(key);
        string(value);
    }

    private void key(String key) {
        string(key);
        json.append(':');
    }

    private void string(String text) {
        json.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                case '<', '>', '&', LINE_SEPARATOR, PARAGRAPH_SEPARATOR -> unicodeEscape(c);
                default -> {
                    if (c < FIRST_PRINTABLE) {
                        unicodeEscape(c);
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }

    private void unicodeEscape(char c) {
        json.append(String.format("\\u%04x", (int) c));
    }
}
