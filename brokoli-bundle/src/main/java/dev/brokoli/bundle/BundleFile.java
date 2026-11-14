package dev.brokoli.bundle;

import dev.brokoli.bundle.internal.Digests;
import dev.brokoli.bundle.internal.Utf8;

import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

record BundleFile(String path, byte[] body) {

    private static final byte PATH_END = 0;

    BundleFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(body, "body");
    }

    static String sourceDigest(List<BundleFile> sortedFiles) {
        MessageDigest content = Digests.sha256();
        for (BundleFile file : sortedFiles) {
            content.update(Utf8.bytes(file.path()));
            content.update(PATH_END);
            content.update(file.body());
        }
        return Digests.digest(content);
    }

    String sha256() {
        return Digests.sha256Hex(body);
    }
}
