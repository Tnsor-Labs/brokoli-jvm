package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"

	"github.com/Tnsor-Labs/brokoli/pkg/taskbundlev2"
	"github.com/Tnsor-Labs/brokoli/pkg/taskinterface"
)

type task struct {
	Name      string      `json:"name"`
	Module    string      `json:"module"`
	Symbol    string      `json:"symbol"`
	Interface interface{} `json:"interface"`
}

func main() {
	if len(os.Args) != 3 {
		fmt.Fprintln(os.Stderr, "usage: assemble <cases directory> <output directory>")
		os.Exit(2)
	}
	cases, err := os.ReadDir(os.Args[1])
	if err != nil {
		fail(err)
	}
	for _, entry := range cases {
		if !entry.IsDir() {
			continue
		}
		in := filepath.Join(os.Args[1], entry.Name())
		out := filepath.Join(os.Args[2], "python-"+entry.Name()+".tar.gz")
		if err := assemble(in, out); err != nil {
			fail(fmt.Errorf("%s: %w", in, err))
		}
	}
}

func assemble(in, out string) error {
	spec, err := os.ReadFile(filepath.Join(in, "task.json"))
	if err != nil {
		return err
	}
	var t task
	if err := json.Unmarshal(spec, &t); err != nil {
		return err
	}
	sources, err := os.ReadDir(filepath.Join(in, "src"))
	if err != nil {
		return err
	}
	files := map[string]string{}
	for _, source := range sources {
		body, err := os.ReadFile(filepath.Join(in, "src", source.Name()))
		if err != nil {
			return err
		}
		files[source.Name()] = string(body)
	}
	interfaceDigest, err := taskinterface.InterfaceDigest(t.Interface)
	if err != nil {
		return err
	}
	sourceDigest := contentDigest(files)
	archive, err := taskbundlev2.Assemble(files, &taskbundlev2.Manifest{
		Format:          taskbundlev2.Format,
		Name:            t.Name,
		InterfaceDigest: interfaceDigest,
		SourceDigest:    sourceDigest,
		Payloads: []taskbundlev2.Payload{{
			ID:            "python-any",
			Runtime:       taskbundlev2.RuntimePython,
			OS:            "any",
			Arch:          "any",
			Entrypoint:    taskbundlev2.Entrypoint{Module: t.Module, Symbol: t.Symbol},
			Effects:       taskbundlev2.EffectPure,
			PayloadDigest: sourceDigest,
		}},
	})
	if err != nil {
		return err
	}
	return os.WriteFile(out, archive, 0o644)
}

func contentDigest(files map[string]string) string {
	paths := make([]string, 0, len(files))
	for path := range files {
		paths = append(paths, path)
	}
	sort.Strings(paths)
	content := sha256.New()
	for _, path := range paths {
		content.Write([]byte(path))
		content.Write([]byte{0})
		content.Write([]byte(files[path]))
	}
	return "sha256:" + hex.EncodeToString(content.Sum(nil))
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
