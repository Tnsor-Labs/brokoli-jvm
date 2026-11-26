package dev.brokoli.bundle;

import dev.brokoli.bundle.internal.Utf8;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class BundleCaps {

    private static final long MIB = 1024 * 1024;

    static final long MAX_ARCHIVE_BYTES = 64 * MIB;
    static final long MAX_TOTAL_BYTES = 64 * MIB;
    static final int MAX_ENTRIES = 10_000;

    private static final int LISTED = 5;
    private static final String TOP_LEVEL = "the top level";
    private static final String ADVICE = "; leave out what the tasks do not need, or mark it provided";

    private BundleCaps() {
    }

    record Sized(String path, long size) {
    }

    static int entries(List<BundleFile> files) {
        return BundleArchive.directories(files).size() + 1 + files.size();
    }

    static void requireWithinFileCaps(List<BundleFile> files, long manifestBytes) {
        int entries = entries(files);
        if (entries > MAX_ENTRIES) {
            throw new BundleException("the task bundles would have " + entries + " entries, over core's cap of "
                    + MAX_ENTRIES + "; the most are in " + mostEntries(files));
        }
        long total = manifestBytes;
        for (BundleFile file : files) {
            total += file.body().length;
        }
        if (total > MAX_TOTAL_BYTES) {
            throw new BundleException("the task bundles would hold " + total + " bytes of files (" + mib(total)
                    + "), over core's cap of " + bytes(MAX_TOTAL_BYTES) + "; the largest are "
                    + largest(files, manifestBytes) + ADVICE);
        }
    }

    static void requireWithinArchiveCap(String task, long archiveBytes, List<BundleFile> files, long manifestBytes) {
        if (archiveBytes > MAX_ARCHIVE_BYTES) {
            throw new BundleException("the task bundle of " + task + " would be " + bytes(archiveBytes)
                    + " compressed, over core's cap of " + bytes(MAX_ARCHIVE_BYTES) + "; the largest files are "
                    + largest(files, manifestBytes) + ADVICE);
        }
    }

    private static String mostEntries(List<BundleFile> files) {
        Map<String, Integer> counts = new TreeMap<>(Utf8.ORDER);
        counts.merge(TOP_LEVEL, 1, Integer::sum);
        for (BundleFile file : files) {
            counts.merge(parent(file.path()), 1, Integer::sum);
        }
        for (String directory : BundleArchive.directories(files)) {
            counts.merge(parent(directory), 1, Integer::sum);
        }
        List<String> listed = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(LISTED)
                .forEach(count -> listed.add(count.getKey() + " (" + count.getValue() + ")"));
        return String.join(", ", listed);
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? TOP_LEVEL : path.substring(0, slash + 1);
    }

    private static String largest(List<BundleFile> files, long manifestBytes) {
        List<Sized> sized = new ArrayList<>();
        sized.add(new Sized(BundleArchive.MANIFEST, manifestBytes));
        for (BundleFile file : files) {
            sized.add(new Sized(file.path(), file.body().length));
        }
        List<String> listed = new ArrayList<>();
        sized.stream()
                .sorted(Comparator.comparingLong(Sized::size).reversed())
                .limit(LISTED)
                .forEach(file -> listed.add(file.path() + " (" + file.size() + " bytes)"));
        return String.join(", ", listed);
    }

    private static String bytes(long count) {
        return count + " bytes (" + mib(count) + ")";
    }

    private static String mib(long count) {
        return count % MIB == 0
                ? count / MIB + " MiB"
                : String.format(Locale.ROOT, "%.1f MiB", (double) count / MIB);
    }
}
