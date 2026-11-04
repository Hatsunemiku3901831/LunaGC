"""Read-only installed-file verification against local launcher JSONL manifests.

MD5 here checks consistency with the supplied manifest, not authenticity. No
download, extraction, repair or execution is performed. Full hashing is default.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path, PureWindowsPath
import re


def safe_member(root, name):
    if not isinstance(name, str) or not name or "\x00" in name:
        raise ValueError("Missing/invalid remoteName")
    win = PureWindowsPath(name)
    parts = name.replace("\\", "/").split("/")
    if win.drive or win.root or any(
            not part or part in (".", "..") or ":" in part or part.endswith((".", " "))
            for part in parts):
        raise ValueError("Manifest path must be a normal relative file path")
    path = root.joinpath(*parts).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError("Manifest path escapes the client directory")
    return path


def digest(path, algorithm):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def verify_manifest(root, manifest, *, sizes_only=False):
    entries, seen = [], set()
    for line_number, line in enumerate(manifest.read_text("utf-8-sig").splitlines(), 1):
        if not line.strip():
            continue
        entry = {"line": line_number}
        try:
            row = json.loads(line)
            name = row["remoteName"]
            entry["path"] = name
            path = safe_member(root, name)
            size, md5 = row["fileSize"], row["md5"]
            if type(size) is not int or size < 0 or not isinstance(md5, str) or not re.fullmatch(r"[0-9a-fA-F]{1,32}", md5):
                raise ValueError("Invalid fileSize or MD5")
            # The local launcher writes some digests without leading zeroes.
            md5 = md5.lower().zfill(32)
            key = str(path).casefold()
            entry.update(expectedSize=size, expectedMD5=md5)
            if key in seen:
                entry["status"] = "duplicate-path"
            elif not path.is_file():
                entry["status"] = "missing"
            elif path.stat().st_size != size:
                entry.update(status="size-mismatch", actualSize=path.stat().st_size)
            elif sizes_only:
                entry["status"] = "size-matched-unhashed"
            else:
                actual = digest(path, "md5")
                entry.update(status="matched" if actual == md5 else "hash-mismatch", actualMD5=actual)
            seen.add(key)
        except (ValueError, KeyError, TypeError, OSError) as error:
            entry.update(status="invalid-entry", error=str(error))
        entries.append(entry)
    counts = dict(sorted(Counter(entry["status"] for entry in entries).items()))
    return {"manifest": manifest.name, "manifestSHA256": digest(manifest, "sha256"),
            "counts": counts, "complete": bool(entries) and all(e["status"] == "matched" for e in entries),
            "entries": entries}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--sizes-only", action="store_true", help="Triage only; never reports complete")
    args = parser.parse_args()
    root, output = args.client.resolve(), args.output.resolve()
    if output.is_relative_to(root):
        parser.error("Report must be outside the client directory")
    manifests = [root / "pkg_version", root / "beyond_pkg_version"]
    manifests.extend(sorted(root.glob("Audio_*_pkg_version")))
    report = {"scope": "Local manifest consistency only, not origin authentication or runtime acceptance",
              "clientPath": str(root), "sizesOnly": args.sizes_only, "manifests": []}
    for manifest in manifests:
        if not manifest.is_file():
            result = {"manifest": manifest.name, "complete": False, "counts": {"missing-manifest": 1}}
        else:
            result = verify_manifest(root, manifest, sizes_only=args.sizes_only)
        report["manifests"].append(result)
        print(f"{manifest.name}: {result['counts']}", flush=True)
    report["pendingDownloads"] = sorted(path.name for path in root.glob("*.zip_tmp"))
    report["complete"] = all(item["complete"] for item in report["manifests"])
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", "utf-8")
    raise SystemExit(0 if report["complete"] else 2)


if __name__ == "__main__":
    main()
