package dev.brokoli.bundle;

import java.util.Objects;

record BundleFile(String path, byte[] body) {

    BundleFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(body, "body");
    }

    String sha256() {
        return Digests.sha256Hex(body);
    }
}
