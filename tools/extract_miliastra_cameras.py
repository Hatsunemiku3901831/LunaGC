#!/usr/bin/env python3
"""Read pinned camera template literals with AST; never import reference code."""
import ast
import hashlib
import json
import subprocess
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[1]
    reference = root / "debug/UGC-File-Generate-Utils"
    path = reference / "model/camera_model.py"
    tree = ast.parse(path.read_text(encoding="utf-8"))
    manager = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == "CameraTemplateManager")
    mode_class = next(n for n in manager.body if isinstance(n, ast.ClassDef) and n.name == "CameraMode")
    modes = {n.targets[0].id: ast.literal_eval(n.value) for n in mode_class.body if isinstance(n, ast.Assign)}
    names = {"CAMERA_2_5D_TEMPLATE": "twoPointFiveD", "BACK_CAMERA_TEMPLATE": "backCamera",
             "FIRST_PERSON_TEMPLATE": "firstPerson", "CLASSIC_TEMPLATE": "classic", "THIRD_PERSON_TEMPLATE": "thirdPerson"}
    def camel(key):
        a, *b = key.split("_")
        return a + "".join(p.capitalize() for p in b)
    def literal(value):
        if isinstance(value, ast.Attribute) and isinstance(value.value, ast.Name) and value.value.id == "CameraMode":
            return modes[value.attr]
        if isinstance(value, ast.Call) and isinstance(value.func, ast.Name) and value.func.id == "ViewpointOffset" and not value.args:
            return {field.arg: ast.literal_eval(field.value) for field in value.keywords}
        return ast.literal_eval(value)
    rows = []
    for n in manager.body:
        if isinstance(n, ast.Assign) and n.targets[0].id in names:
            if not isinstance(n.value, ast.Call) or not isinstance(n.value.func, ast.Name) or n.value.func.id != "CameraTemplate" or n.value.args:
                raise ValueError("Expected literal CameraTemplate keyword data")
            data = {camel(f.arg): literal(f.value) for f in n.value.keywords}
            editable = {k: data.pop(k) for k in list(data) if k.startswith("canEdit")}
            data["wireCameraMode"] = 0 if names[n.targets[0].id] == "classic" else data["cameraMode"]
            rows.append({"id": names[n.targets[0].id], "defaults": data, "editorHints": editable})
    if len(rows) != 5:
        raise ValueError("Expected all five camera templates")
    commit = subprocess.check_output(["git", "-C", str(reference), "rev-parse", "HEAD"], text=True).strip()
    result = {"source": "https://github.com/luern0313/UGC-File-Generate-Utils", "commit": commit,
              "sourceSha256": hashlib.sha256(path.read_bytes()).hexdigest(), "license": "Apache-2.0",
              "verifiedClientVersion": None, "templates": rows}
    output = root / "src/main/resources/miliastra/camera-templates.json"
    output.write_bytes((json.dumps(result, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
    (output.parent / "LICENSE.camera-templates").write_bytes((reference / "LICENSE").read_bytes())
    print(json.dumps({"templates": len(rows), "commit": commit}))


if __name__ == "__main__":
    main()
