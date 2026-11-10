package dev.brokoli.bundle;

import java.util.List;
import java.util.Objects;

record Manifest(String name, String interfaceDigest, String sourceDigest, String builder, List<Payload> payloads,
        List<FileEntry> files) {

    static final String FORMAT = "brokoli.task-bundle/v2";

    Manifest {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(interfaceDigest, "interfaceDigest");
        Objects.requireNonNull(sourceDigest, "sourceDigest");
        payloads = List.copyOf(payloads);
        files = List.copyOf(files);
    }

    record Payload(String id, String runtime, String os, String arch, String module, String symbol,
            String effects, String payloadDigest) {

        static final String JVM_ID = "jvm-any";
        static final String JVM_RUNTIME = "jvm";
        static final String ANY = "any";
        static final String PURE = "pure";

        Payload {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(os, "os");
            Objects.requireNonNull(arch, "arch");
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(payloadDigest, "payloadDigest");
        }

        static Payload jvm(String module, String symbol, String payloadDigest) {
            return new Payload(JVM_ID, JVM_RUNTIME, ANY, ANY, module, symbol, PURE, payloadDigest);
        }
    }

    record FileEntry(String path, long size, String sha256) {

        FileEntry {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(sha256, "sha256");
        }

        static FileEntry of(BundleFile file) {
            return new FileEntry(file.path(), file.body().length, file.sha256());
        }
    }
}
