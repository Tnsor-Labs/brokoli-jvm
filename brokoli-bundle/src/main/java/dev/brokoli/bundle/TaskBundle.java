package dev.brokoli.bundle;

import dev.brokoli.bundle.Manifest.FileEntry;
import dev.brokoli.bundle.Manifest.Payload;
import dev.brokoli.bundle.TaskFile.TaskEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * The task-bundle/v2 archive of one task, ready to be written. Writing the same project twice gives the same
 * bytes.
 */
public final class TaskBundle {

    private final TaskEntry task;
    private final List<BundleFile> files;
    private final Manifest manifest;

    TaskBundle(TaskEntry task, List<BundleFile> files, String sourceDigest, String builder) {
        this.task = task;
        this.files = files;
        this.manifest = new Manifest(task.name(), TaskInterfaces.interfaceDigest(task.nodeInterface()), sourceDigest,
                builder, List.of(Payload.jvm(task.module(), task.symbol(), sourceDigest)),
                files.stream().map(FileEntry::of).toList());
    }

    /**
     * The task's display name, which is the manifest's {@code name}.
     *
     * @return the display name, such as {@code "Daily Rollup"}
     */
    public String name() {
        return task.name();
    }

    /**
     * The binary name of the class that declares the task.
     *
     * @return the class name, such as {@code "com.example.Transforms"}
     */
    public String className() {
        return task.owner();
    }

    /**
     * The name of the {@code @Task} method.
     *
     * @return the method name, such as {@code "dailyRollup"}
     */
    public String method() {
        return task.method();
    }

    /**
     * The entry method core's harness calls, as the manifest's entrypoint names it.
     *
     * @return {@code Class#method}, such as {@code "com.example.Transforms_BrokoliTasks#dailyRollup"}
     */
    public String entrypoint() {
        return task.module() + "#" + task.symbol();
    }

    /**
     * The manifest's {@code interface_digest}: the {@code sha256:} digest of the task's interface in its canonical
     * rendering, with an {@code input} and a {@code result} dataset of unknown rows when the task declares no
     * row types.
     *
     * @return the interface digest
     */
    public String interfaceDigest() {
        return manifest.interfaceDigest();
    }

    /**
     * The manifest's {@code source_digest}, the {@code sha256:} digest of the files in the bundle. It is the same
     * for every task of a project.
     *
     * @return the source digest
     */
    public String sourceDigest() {
        return manifest.sourceDigest();
    }

    /**
     * Writes the archive, a gzip-compressed tar, and leaves the stream open.
     *
     * @param out where to write the archive
     * @return the bundle's digest, {@code sha256:} and the SHA-256 of the bytes written, which a pipeline uses to
     *         reference the bundle
     * @throws IOException if the stream cannot be written
     */
    public String writeTo(OutputStream out) throws IOException {
        MessageDigest digest = Digests.sha256();
        BundleArchive.write(files, manifest, new DigestOutputStream(out, digest));
        return Digests.PREFIX + HexFormat.of().formatHex(digest.digest());
    }
}
