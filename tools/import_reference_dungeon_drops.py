#!/usr/bin/env python3
"""Audit a pinned reference catalog and export compatible optional reward tables."""

import argparse
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def positive(value):
    return type(value) is int and 0 < value < 2**31 - 1


def weights_valid(weights, size):
    return weights is None or (
        len(weights) == size
        and all(type(x) is int and x >= 0 for x in weights)
        and 0 < sum(weights) < 2**31 - 1
    )


def problems(row, dungeons, previews, displays, items):
    errors = set()
    dungeon = dungeons.get(row.get("dungeonId"))
    if dungeon is None:
        return ["missing-dungeon"]
    groups = row.get("drops")
    if not isinstance(groups, list) or not groups:
        return ["empty-drops"]
    preview = previews.get(dungeon.get("passRewardPreviewID"), {})
    preview_items = {x.get("id", 0) for x in preview.get("previewItems", [])}
    allowed_sets = {
        displays[x]["param"]
        for x in preview_items & displays.keys()
        if displays[x].get("param", 0) > 0 and displays[x].get("rankLevel", 0) > 0
    }
    subtype = dungeon.get("subType")
    if subtype not in ("DUNGEON_SUB_TALENT", "DUNGEON_SUB_WEAPON", "DUNGEON_SUB_RELIQUARY"):
        errors.add("unsupported-subtype")
    has_progression = False
    for group in groups:
        counts = group.get("counts", [])
        if not counts or any(type(x) is not int or x < 0 or x >= 2**31 - 1 for x in counts):
            errors.add("invalid-counts")
        elif counts != sorted(set(counts)) or not weights_valid(group.get("probabilities"), counts[-1] - counts[0] + 1):
            errors.add("invalid-count-weights")
        candidates = group.get("items", [])
        if not candidates:
            errors.add("empty-items")
        if not weights_valid(group.get("itemProbabilities"), len(candidates)):
            errors.add("invalid-item-weights")
        for item_id in candidates:
            item = items.get(item_id) if positive(item_id) else None
            if item is None:
                errors.add("missing-item")
                continue
            kind = item.get("itemType")
            if kind == "ITEM_VIRTUAL":
                if item_id not in (102, 105, 202):
                    errors.add("unsupported-currency")
            elif kind == "ITEM_RELIQUARY":
                has_progression = True
                if subtype != "DUNGEON_SUB_RELIQUARY":
                    errors.add("relic-in-material-domain")
                if item.get("setId", item.get("setID", 0)) not in allowed_sets:
                    errors.add("unexpected-set")
            elif kind == "ITEM_MATERIAL":
                has_progression = True
                if subtype not in ("DUNGEON_SUB_TALENT", "DUNGEON_SUB_WEAPON"):
                    errors.add("material-in-relic-domain")
                if item_id not in preview_items:
                    errors.add("unexpected-material")
            else:
                errors.add("unsupported-item-type")
    if not has_progression:
        errors.add("missing-progression-reward")
    return sorted(errors)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--reference-commit", required=True)
    parser.add_argument("--resources", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    excel = args.resources / "ExcelBinOutput"
    names = ["Dungeon", "RewardPreview", "DisplayItem", "Material", "Reliquary", "Weapon"]
    paths = {name: excel / f"{name}ExcelConfigData.json" for name in names}
    resources = {name: {x["id"]: x for x in read(path)} for name, path in paths.items()}
    items = {key: value for name in ("Material", "Reliquary", "Weapon") for key, value in resources[name].items()}
    rows = read(args.reference)
    seen, accepted, excluded = set(), [], []
    for row in sorted(rows, key=lambda x: x["dungeonId"]):
        identifier = row["dungeonId"]
        if not positive(identifier) or identifier in seen:
            raise ValueError(f"Invalid or duplicate dungeon ID {identifier}")
        seen.add(identifier)
        issues = problems(row, resources["Dungeon"], resources["RewardPreview"], resources["DisplayItem"], items)
        if issues:
            excluded.append({"dungeonId": identifier, "reasons": issues})
        else:
            accepted.append(row)
    audit = {
        "referenceCommit": args.reference_commit,
        "hashNormalization": "LF line endings",
        "referenceSha256": digest(args.reference),
        "resourceSha256": {path.name: digest(path) for path in paths.values()},
        "sourceCount": len(rows),
        "acceptedCount": len(accepted),
        "acceptedIds": [x["dungeonId"] for x in accepted],
        "excluded": excluded,
        "enabledByDefault": False,
    }
    for path, value in ((args.output, accepted), (args.audit, audit)):
        text = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
        if args.check:
            if path.read_text(encoding="utf-8-sig") != text:
                raise ValueError(f"Generated content differs: {path}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
    print(json.dumps({"sourceCount": len(rows), "acceptedCount": len(accepted), "excludedCount": len(excluded), "checkOnly": args.check}))


if __name__ == "__main__":
    main()
