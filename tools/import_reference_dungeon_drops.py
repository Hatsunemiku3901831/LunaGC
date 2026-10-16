#!/usr/bin/env python3
"""Audit a pinned reference catalog and export compatible optional reward tables."""

import argparse
import copy
from collections import defaultdict
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


def repair_row(row, specification, resources, catalogs):
    items = {key: value for name in ("Material", "Reliquary", "Weapon") for key, value in resources[name].items()}
    identifier = row["dungeonId"]
    issues = problems(row, resources["Dungeon"], resources["RewardPreview"], resources["DisplayItem"], items)
    if identifier != specification["dungeonId"] or not issues or issues != specification["expectedIssues"]:
        raise ValueError(f"Repair preconditions changed for dungeon {identifier}")
    action = specification["action"]
    if action not in ("replace-physical", "remap-sets", "fill-empty"):
        raise ValueError(f"Unknown repair action {action}")
    source_id = specification["sourceDungeonId"]
    try:
        source = catalogs[specification["source"]][source_id]
    except KeyError as error:
        raise ValueError(f"Missing repair source {source_id}") from error
    dungeon = resources["Dungeon"][identifier]
    result = copy.deepcopy(row)
    mapping, item_map = {}, {}
    if action == "fill-empty":
        index = specification["groupIndex"]
        if index < 0 or index >= len(result["drops"]) or index >= len(source["drops"]):
            raise ValueError(f"Missing empty group source for {identifier}")
        original, replacement = result["drops"][index], source["drops"][index]
        if original["items"] or not replacement["items"]:
            raise ValueError(f"Expected an empty candidate group for {identifier}")
        for field in ("counts", "probabilities", "mpDouble"):
            if original.get(field) != replacement.get(field):
                raise ValueError(f"Empty group {field} differs for {identifier}")
        if any(item_id not in resources["Reliquary"] or items[item_id]["rankLevel"] != specification["rarity"]
               for item_id in replacement["items"]):
            raise ValueError(f"Unexpected empty group rarity for {identifier}")
        original["items"] = list(replacement["items"])
        if "itemProbabilities" in replacement:
            weights = replacement["itemProbabilities"]
            original["itemProbabilities"] = None if weights is None else list(weights)
    else:
        if action == "replace-physical":
            source_dungeon = resources["Dungeon"][source_id]
            if source_dungeon["subType"] != "DUNGEON_SUB_RELIQUARY" or source_dungeon["showLevel"] != dungeon["showLevel"]:
                raise ValueError(f"Incompatible prototype difficulty for {identifier}")
            if problems(source, resources["Dungeon"], resources["RewardPreview"], resources["DisplayItem"], items):
                raise ValueError(f"Invalid reference prototype {source_id}")
        elif specification["source"] != "reference" or source_id != identifier:
            raise ValueError("A set-only repair must retain its own reference groups")
        physical = [group for group in source["drops"] if any(item_id in resources["Reliquary"] for item_id in group["items"])]
        physical, mapping, item_map = remap_relic_groups(physical, dungeon, resources)
        virtual = [group for group in result["drops"] if group["items"] and set(group["items"]).issubset({102, 105, 202})]
        result["drops"] = virtual + physical
    remaining = problems(result, resources["Dungeon"], resources["RewardPreview"], resources["DisplayItem"], items)
    if remaining:
        raise ValueError(f"Repair still invalid for {identifier}: {remaining}")
    canonical = lambda value: hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()
    trace = dict(dungeonId=identifier, action=action, source=specification["source"], sourceDungeonId=source_id,
                 originalIssues=issues, setMap=mapping, itemMap=item_map,
                 originalRowSha256=canonical(row), sourceRowSha256=canonical(source), repairedRowSha256=canonical(result))
    return result, trace


def remap_relic_groups(groups, dungeon, resources):
    relics = resources["Reliquary"]
    shape_fields = ("rankLevel", "equipType", "mainPropDepotId", "appendPropDepotId", "appendPropNum", "maxLevel")
    shape = lambda item: tuple(item.get(field, 0) for field in shape_fields)
    set_id = lambda item: item.get("setId", item.get("setID", 0))
    source_ranks = defaultdict(set)
    for group in groups:
        for identifier in group["items"]:
            if identifier not in relics:
                raise ValueError(f"Non-relic repair candidate {identifier}")
            item = relics[identifier]
            source_ranks[set_id(item)].add(item["rankLevel"])
    if not source_ranks:
        raise ValueError("Missing relic prototype candidates")
    all_ranks = set().union(*source_ranks.values())
    target_ranks = defaultdict(set)
    preview = resources["RewardPreview"][dungeon["passRewardPreviewID"]]
    for row in preview["previewItems"]:
        display = resources["DisplayItem"].get(row.get("id", 0))
        if display and display.get("param", 0) > 0 and display.get("rankLevel") in all_ranks:
            target_ranks[display["param"]].add(display["rankLevel"])
    group_sets = lambda ranks: {
        band: {key for key, value in ranks.items() if tuple(sorted(value)) == band}
        for band in sorted({tuple(sorted(value)) for value in ranks.values()})
    }
    mapping = {}
    if not all(key in target_ranks and value.issubset(target_ranks[key]) for key, value in source_ranks.items()):
        old_groups, new_groups = group_sets(source_ranks), group_sets(target_ranks)
        if old_groups.keys() != new_groups.keys():
            raise ValueError(f"Incompatible relic rank groups for {dungeon['id']}")
        for band, old in old_groups.items():
            new = new_groups[band]
            left, right = sorted(old - new), sorted(new - old)
            if len(left) != len(right):
                raise ValueError(f"Incompatible relic set counts for {dungeon['id']}")
            mapping.update(zip(left, right))
    index = defaultdict(list)
    for item in relics.values():
        if set_id(item) in mapping.values():
            index[(set_id(item), shape(item))].append(item["id"])
    result = copy.deepcopy(groups)
    item_map = {}
    for group in result:
        for offset, identifier in enumerate(group["items"]):
            item = relics[identifier]
            target = mapping.get(set_id(item))
            if target is not None:
                candidates = index[(target, shape(item))]
                if len(candidates) != 1:
                    raise ValueError(f"Missing or ambiguous relic replacement for {identifier}")
                group["items"][offset] = candidates[0]
                item_map[identifier] = candidates[0]
        if len(group["items"]) != len(set(group["items"])):
            raise ValueError("A relic repair collapsed distinct candidate IDs")
    return result, mapping, item_map


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--reference-commit", required=True)
    parser.add_argument("--resources", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--repairs", type=Path)
    parser.add_argument("--ordinary", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    excel = args.resources / "ExcelBinOutput"
    names = ["Dungeon", "RewardPreview", "DisplayItem", "Material", "Reliquary", "Weapon"]
    paths = {name: excel / f"{name}ExcelConfigData.json" for name in names}
    resources = {name: {x["id"]: x for x in read(path)} for name, path in paths.items()}
    items = {key: value for name in ("Material", "Reliquary", "Weapon") for key, value in resources[name].items()}
    rows = read(args.reference)
    repair_specs, repaired = {}, []
    catalogs = {"reference": {row["dungeonId"]: row for row in rows}}
    if args.repairs:
        if not args.ordinary:
            raise ValueError("--ordinary is required with --repairs")
        plan = read(args.repairs)
        if plan.get("schemaVersion") != 1 or plan.get("referenceCommit") != args.reference_commit:
            raise ValueError("Repair plan version or pinned reference differs")
        repair_specs = {row["dungeonId"]: row for row in plan["repairs"]}
        if len(repair_specs) != len(plan["repairs"]) or not repair_specs.keys() <= catalogs["reference"].keys():
            raise ValueError("Duplicate or unknown repair target")
        ordinary_rows = read(args.ordinary)
        catalogs["ordinary"] = {row["dungeonId"]: row for row in ordinary_rows}
        if len(catalogs["ordinary"]) != len(ordinary_rows):
            raise ValueError("Duplicate ordinary source dungeon ID")
    seen, accepted, excluded = set(), [], []
    for row in sorted(rows, key=lambda x: x["dungeonId"]):
        identifier = row["dungeonId"]
        if not positive(identifier) or identifier in seen:
            raise ValueError(f"Invalid or duplicate dungeon ID {identifier}")
        seen.add(identifier)
        if identifier in repair_specs:
            row, trace = repair_row(row, repair_specs[identifier], resources, catalogs)
            repaired.append(trace)
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
    if args.repairs:
        audit.update(sourceAcceptedCount=len(accepted) - len(repaired), repairedCount=len(repaired), repaired=repaired,
                     repairPlanSha256=digest(args.repairs), ordinarySha256=digest(args.ordinary))
    for path, value in ((args.output, accepted), (args.audit, audit)):
        text = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
        if args.check:
            if path.read_text(encoding="utf-8-sig") != text:
                raise ValueError(f"Generated content differs: {path}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
    print(json.dumps({"sourceCount": len(rows), "acceptedCount": len(accepted), "repairedCount": len(repaired), "excludedCount": len(excluded), "checkOnly": args.check}))


if __name__ == "__main__":
    main()
