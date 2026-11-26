package dev.brokoli.bundle;

import dev.brokoli.bundle.internal.Utf8;

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

    static long write(List<BundleFile> sortedFiles, Manifest manifest, OutputStream out) throws IOException {
        Unclosed counted = new Unclosed(out);
        try (GZIPOutputStream gzip = new GZIPOutputStream(counted, GZIP_BUFFER)) {
            writeTar(sortedFiles, manifest, gzip);
        }
        return counted.written;
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

    static SortedSet<String> directories(List<BundleFile> files) {
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

        private long written;

        Unclosed(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
            written += length;
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
