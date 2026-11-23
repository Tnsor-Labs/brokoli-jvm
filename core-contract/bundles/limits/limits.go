package main

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"encoding/json"
	"fmt"
	"os"

	"github.com/Tnsor-Labs/brokoli/pkg/taskbundlev2"
)

type limits struct {
	MaxArchiveBytes int64 `json:"max_archive_bytes"`
	MaxTotalBytes   int64 `json:"max_total_bytes"`
	MaxEntries      int   `json:"max_entries"`
}

type entry struct {
	name      string
	size      int64
	directory bool
}

func main() {
	if len(os.Args) != 2 {
		fmt.Fprintln(os.Stderr, "usage: limits <output file>")
		os.Exit(2)
	}
	found := limits{
		MaxArchiveBytes: taskbundlev2.MaxArchiveBytes,
		MaxTotalBytes:   taskbundlev2.MaxArchiveBytes,
		MaxEntries:      taskbundlev2.MaxArchiveEntries,
	}
	if err := probeArchiveBytes(found.MaxArchiveBytes); err != nil {
		fail(err)
	}
	if err := probeTotalBytes(found.MaxTotalBytes); err != nil {
		fail(err)
	}
	if err := probeEntries(found.MaxEntries); err != nil {
		fail(err)
	}
	text, err := json.MarshalIndent(found, "", "  ")
	if err != nil {
		fail(err)
	}
	if err := os.WriteFile(os.Args[1], append(text, '\n'), 0o644); err != nil {
		fail(err)
	}
}

func probeArchiveBytes(limit int64) error {
	refusal := "task bundle archive %d bytes exceeds the %d-byte cap"
	return probe("archive bytes", fmt.Sprintf(refusal, limit, limit), fmt.Sprintf(refusal, limit+1, limit),
		func() []byte { return make([]byte, limit) },
		func() []byte { return make([]byte, limit+1) })
}

func probeTotalBytes(limit int64) error {
	refused := fmt.Sprintf("extract task bundle: archive exceeds the %d-byte total limit", limit)
	return probe("total bytes", refused, refused,
		func() []byte {
			return archive([]entry{{name: "a", size: limit / 2}, {name: "b", size: limit - limit/2}})
		},
		func() []byte {
			return archive([]entry{{name: "a", size: limit / 2}, {name: "b", size: limit - limit/2 + 1}})
		})
}

func probeEntries(limit int) error {
	refused := fmt.Sprintf("extract task bundle: archive has more than %d entries", limit)
	return probe("entries", refused, refused,
		func() []byte { return archive(directories(limit)) },
		func() []byte { return archive(directories(limit + 1)) })
}

func probe(name, refusedAt, refusedOver string, atLimit, overLimit func() []byte) error {
	if err := extract(atLimit()); err != nil && err.Error() == refusedAt {
		return fmt.Errorf("%s: core refuses a bundle at the limit: %s", name, refusedAt)
	}
	err := extract(overLimit())
	if err == nil || err.Error() != refusedOver {
		return fmt.Errorf("%s: one over the limit, core says %v, expected %s", name, err, refusedOver)
	}
	return nil
}

func extract(archive []byte) error {
	scratch, err := os.MkdirTemp("", "brokoli-jvm-limits-*")
	if err != nil {
		fail(err)
	}
	defer os.RemoveAll(scratch)
	_, err = taskbundlev2.Extract(archive, scratch)
	return err
}

func directories(count int) []entry {
	entries := make([]entry, count)
	for i := range entries {
		entries[i] = entry{name: fmt.Sprintf("d%05d/", i), directory: true}
	}
	return entries
}

func archive(entries []entry) []byte {
	var out bytes.Buffer
	zipped := gzip.NewWriter(&out)
	writer := tar.NewWriter(zipped)
	for _, e := range entries {
		header := &tar.Header{Name: e.name, Mode: 0o644, Typeflag: tar.TypeReg, Size: e.size}
		if e.directory {
			header = &tar.Header{Name: e.name, Mode: 0o755, Typeflag: tar.TypeDir}
		}
		if err := writer.WriteHeader(header); err != nil {
			fail(err)
		}
		if e.size > 0 {
			if _, err := writer.Write(make([]byte, e.size)); err != nil {
				fail(err)
			}
		}
	}
	if err := writer.Close(); err != nil {
		fail(err)
	}
	if err := zipped.Close(); err != nil {
		fail(err)
	}
	return out.Bytes()
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
