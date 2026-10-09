#!/usr/bin/env python3
"""Extract literal block palette data without importing or executing the reference code."""
import ast
import hashlib
import json
import subprocess
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[1]
    reference = root / "debug/UGC-File-Generate-Utils"
    path = reference / "config/block_config.py"
    source = path.read_text(encoding="utf-8")
    body = next(node for node in ast.parse(source).body if isinstance(node, ast.ClassDef) and node.name == "BlockConfig")
    array = next(node.value for node in body.body if isinstance(node, ast.AnnAssign)
                 and isinstance(node.target, ast.Name) and node.target.id == "AVAILABLE_BLOCKS")
    rows = []
    for node in array.elts:
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Name) or node.func.id != "BlockTemplate" or node.args:
            raise ValueError("Only literal BlockTemplate keyword data is supported")
        data = {field.arg: ast.literal_eval(field.value) for field in node.keywords}
        if set(data) - {"template_id", "color_tuple", "size_units", "default_scale_tuple"}:
            raise ValueError("Unknown block palette field")
        name = source.splitlines()[node.lineno - 2].strip().removeprefix("#").strip()
        rows.append({"id": data["template_id"], "name": name, "color": data["color_tuple"],
                     "size": data["size_units"], "scale": data.get("default_scale_tuple", (1.0, 1.0, 1.0))})
    if len({row["id"] for row in rows}) != len(rows):
        raise ValueError("Duplicate template IDs")
    commit = subprocess.check_output(["git", "-C", str(reference), "rev-parse", "HEAD"], text=True).strip()
    result = {"source": "https://github.com/luern0313/UGC-File-Generate-Utils", "commit": commit,
              "sourceSha256": hashlib.sha256(path.read_bytes()).hexdigest(), "license": "Apache-2.0",
              "verifiedClientVersion": None, "blocks": rows}
    output = root / "src/main/resources/miliastra/block-palette.json"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"blocks": len(rows), "commit": commit, "output": str(output)}))


if __name__ == "__main__":
    main()
