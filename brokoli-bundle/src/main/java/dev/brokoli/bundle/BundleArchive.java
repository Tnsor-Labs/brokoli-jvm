package dev.brokoli.bundle;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

final class BundleArchive {

    static final String MANIFEST = "manifest.json";

    private static final int GZIP_BUFFER = 64 * 1024;

    private BundleArchive() {
    }

    static void write(List<BundleFile> sortedFiles, Manifest manifest, OutputStream out) throws IOException {
        try (GZIPOutputStream gzip = new GZIPOutputStream(new Unclosed(out), GZIP_BUFFER)) {
            writeTar(sortedFiles, manifest, gzip);
        }
    }

    static void writeTar(List<BundleFile> sortedFiles, Manifest manifest, OutputStream out) throws IOException {
        TarWriter tar = new TarWriter(out);
        for (String directory : directories(sortedFiles)) {
            tar.directory(directory);
        }
        tar.file(MANIFEST, ManifestJson.write(manifest));
        for (BundleFile file : sortedFiles) {
            tar.file(file.path(), file.body());
        }
        tar.finish();
    }

    private static SortedSet<String> directories(List<BundleFile> files) {
        SortedSet<String> directories = new TreeSet<>(Utf8.ORDER);
        for (BundleFile file : files) {
            String path = file.path();
            for (int slash = path.lastIndexOf('/'); slash > 0; slash = path.lastIndexOf('/', slash - 1)) {
                directories.add(path.substring(0, slash));
            }
        }
        return directories;
    }

    private static final class Unclosed extends FilterOutputStream {

        Unclosed(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
