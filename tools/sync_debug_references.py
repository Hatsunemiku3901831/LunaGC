#!/usr/bin/env python3
"""Download public reference sources into the ignored debug directory.

Only Git source retrieval is performed. No third-party setup or build is run.
Existing changes are preserved; --update only advances clean checkouts by fast-forward.
"""
import argparse
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REFERENCES = {
    "AstaPS": "https://github.com/MeChen618/AstaPS.git",
    "LunaGC_7.1.0": "https://github.com/capyb2222/LunaGC_7.1.0.git",
    "HunkyMeow": "https://github.com/AzureXuanVerse/HunkyMeow.git",
    "genshin-miliastra-file-format": "https://github.com/script-1024/genshin-miliastra-file-format.git",
    "WebMiliastraNodesEditor": "https://github.com/Columbina-Dev/WebMiliastraNodesEditor.git",
    "UGC-File-Generate-Utils": "https://github.com/luern0313/UGC-File-Generate-Utils.git",
    "genshin-db": "https://github.com/theBowja/genshin-db.git",
    "mavuika-rs": "https://git.xeondev.com/mavuika-rs/mavuika-rs.git",
    "gcsim": "https://github.com/genshinsim/gcsim.git",
}
ADAPTED_SOURCES = {
    "gcsim": {
        "release": "v2.47.2",
        "commit": "caf738dc807a646e2eee7e7b572f225dd6deb510",
        "files": ["pkg/core/stacks/refresh.go", "pkg/core/stacks/refresh_test.go"],
    }
}


def git(*args):
    return subprocess.run(["git", *map(str, args)], check=True, capture_output=True,
                          text=True, encoding="utf-8").stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update", action="store_true")
    parser.add_argument("--manifest-only", action="store_true")
    options = parser.parse_args()
    directory = ROOT / "debug"
    directory.mkdir(exist_ok=True)
    records = []
    errors = []
    for name, url in REFERENCES.items():
        checkout = directory / name
        try:
            if not checkout.exists():
                if options.manifest_only:
                    raise RuntimeError("checkout has not been downloaded")
                git("clone", "--filter=blob:none", url, checkout)
            if git("-C", checkout, "rev-parse", "--show-toplevel").replace("\\", "/").lower() != checkout.as_posix().lower():
                raise RuntimeError("directory is not a standalone Git checkout")
            if git("-C", checkout, "remote", "get-url", "origin") != url:
                raise RuntimeError("origin differs from the expected reference")
            sparse = git("-C", checkout, "config", "--bool", "--default", "false",
                         "--get", "core.sparseCheckout") == "true"
            if sparse and not options.manifest_only:
                if git("-C", checkout, "status", "--porcelain"):
                    raise RuntimeError("checkout has local changes; full materialization skipped")
                git("-C", checkout, "sparse-checkout", "disable")
                sparse = False
            if options.update:
                if git("-C", checkout, "status", "--porcelain"):
                    raise RuntimeError("checkout has local changes; update skipped")
                git("-C", checkout, "pull", "--ff-only")
            if not options.manifest_only:
                git("-C", checkout, "submodule", "update", "--init", "--recursive")
            sha = git("-C", checkout, "rev-parse", "HEAD")
            record = {"name": name, "url": url, "commit": sha,
                      "commit_date": git("-C", checkout, "show", "-s", "--format=%cI", "HEAD"),
                      "branch": git("-C", checkout, "branch", "--show-current"),
                      "submodules": git("-C", checkout, "submodule", "status", "--recursive").splitlines()}
            missing = git("-C", checkout, "ls-files", "--deleted").splitlines()
            record["worktree_clean"] = not git("-C", checkout, "status", "--porcelain")
            record["checkout_complete"] = not sparse and not missing and all(
                line[:1] not in ("-", "U") for line in record["submodules"])
            if missing:
                record["missing_tracked_files"] = missing
            if sparse:
                record["checkout_scope"] = git("-C", checkout, "sparse-checkout", "list").splitlines()
            if name in ADAPTED_SOURCES:
                adapted = ADAPTED_SOURCES[name]
                record["adapted_source"] = {
                    **adapted,
                    "current_commit_files_identical": not git(
                        "-C", checkout, "diff", adapted["commit"], sha, "--", *adapted["files"]),
                }
            records.append(record)
            print(name, sha[:12])
        except (subprocess.CalledProcessError, RuntimeError) as error:
            errors.append({"name": name, "error": str(error)})
            print(name, "FAILED:", str(error))
    manifest = {"retrieved_at": datetime.now(timezone.utc).isoformat(), "sources": records,
                "errors": errors, "nonpublic_sources": [{"name": "YuukiPS GC/GIO", "url":
                    "https://ps-new.yuuki.me/downloads/genshin-impact",
                    "status": "public service information only; no source repository was verified"}]}
    (directory / "references.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
