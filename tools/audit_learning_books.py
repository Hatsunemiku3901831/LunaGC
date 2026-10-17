"""Inventory learning-book resource shapes; no gameplay or player database mutation."""

import argparse
import collections
import hashlib
import json
import pathlib

OPERATIONS = {
    "ITEM_USE_UNLOCK_COMBINE",
    "ITEM_USE_UNLOCK_FORGE",
    "ITEM_USE_UNLOCK_COOK_RECIPE",
    "ITEM_USE_UNLOCK_FURNITURE_FORMULA",
    "ITEM_USE_UNLOCK_FURNITURE_SUITE",
    "ITEM_USE_UNLOCK_HOME_BGM",
}


def audit(resources):
    relative = "ExcelBinOutput/MaterialExcelConfigData.json"
    data = (resources / relative).read_bytes().replace(b"\r\n", b"\n")
    rows = json.loads(data)
    counts, targets = collections.Counter(), collections.Counter()
    mixed, invalid, on_gain, non_material = [], [], [], []
    books = 0
    for row in rows:
        actions = [
            action for action in row.get("itemUse", [])
            if action.get("useOp") and action["useOp"] != "ITEM_USE_NONE"
        ]
        learning = [action for action in actions if action["useOp"] in OPERATIONS]
        if not learning:
            continue
        books += 1
        item = row["id"]
        counts.update(action["useOp"] for action in learning)
        targets.update([row.get("useTarget", "ITEM_USE_TARGET_NONE")])
        if len(actions) != 1:
            mixed.append(item)
        if row.get("useOnGain", False):
            on_gain.append(item)
        if row.get("itemType") != "ITEM_MATERIAL":
            non_material.append(item)
        for action in learning:
            params = action.get("useParam", [])
            try:
                target = int(params[0])
                if target <= 0 or target > 2**31 - 1:
                    raise ValueError()
            except (ValueError, TypeError, IndexError):
                invalid.append({"itemId": item, "operation": action["useOp"]})
    return dict(schemaVersion=1, source=relative, sha256Lf=hashlib.sha256(data).hexdigest(),
                books=books, operations=dict(sorted(counts.items())), targets=dict(sorted(targets.items())),
                mixedActions=sorted(mixed), invalidParameters=invalid,
                onGainBooks=sorted(on_gain), nonMaterialBooks=sorted(non_material))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=pathlib.Path, default=pathlib.Path("resources"))
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("docs/data/learning-books-2026-10-17.json"))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = audit(args.resources)
    if args.check:
        if json.loads(args.output.read_text("utf-8")) != result:
            raise SystemExit("Learning resource audit differs; regenerate and review the changes.")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", "utf-8")
    print(json.dumps({key: result[key] for key in ["books", "operations", "targets", "mixedActions", "invalidParameters", "onGainBooks", "nonMaterialBooks"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
