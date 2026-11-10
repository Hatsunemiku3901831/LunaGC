"""Bounded, read-only coverage of a frozen old-account projection."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
import os
from pathlib import Path
import subprocess

BASE = Path("local/migration72/d02-resume-20261110b")
SNAPSHOT = BASE / "old-account-snapshot/run-20261110-2"
CANDIDATE = BASE / "c-old-items"
TARGET = Path("local/migration72/d02-active/main/avatar-natural-tables-third.json")
COMMIT = "6cfecfb55f889f8e3b7156adb12809608db4f74b"
ROOT = "3cf83488a94b47a018934e5f91c1bf7e93357aae"
EXCEL = "a4957e38123fed121a3fb0b27519c14644b39b29"
PINS = {
    "dependencies": (SNAPSHOT / "selected-dependencies.private.json", "3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7"),
    "manifest": (SNAPSHOT / "selected-manifest.private.json", "9fffdc25ddb24376552e3163db9ed6de441548d843fbda82649996fb59d4910b"),
    "target": (TARGET, "1e9817cbea6da6db5ee564d5c3f3a64436fa3319e6d9b8ccf5aef0014cf85a86"),
    "prior_manifest": (Path("local/migration72/d02-parallel-20261109/upstream-reuse/branch-retrieval2-20261109T133247Z-terra/manifest.private.json"), "70aec1cb722b71280393389216bc5bceaa7c9d59aa04e2345411b042a1c4a80d"),
}
FILES = {
    "Weapon": ("WeaponExcelConfigData.json", "0686f5f0682565bf99b3dd66cc09a532260eb152", "7829d71be03251c5282b27a8d5a670104e59c9ad31d03ee4dcac9b4a8a639e24"),
    "Reliquary": ("ReliquaryExcelConfigData.json", "dc617e939a0ad4864f88e32dbf98ace9ac11649f", "00efb17747cfea347ac7d95bf381c8607c0de7d4887bfd41051841b04c7b5e85"),
    "Material": ("MaterialExcelConfigData.json", "25767f747465f7a9abf184c99a53767fb32cb954", "c2898b0f2a22870f831544033c92021fa2017475cbaf3bb277278019574a0b0b"),
    "Furniture": ("HomeWorldFurnitureExcelConfigData.json", "6dc06fed99eb5038ef63f38e08138e953d208af8", "afe28eca0e44aa02f137c725421748ae63d5653b3495dd88aee837bd21a6e012"),
}
README = ("42c45a8f83fffbed61edb6122b96c051b8a3ae22", "ee8ae32510108af279496a5328ab90d277fb33d5e622434573aee08a8f5b4013")
FIELDS = ("itemId", "count", "deleted", "level", "promoteLevel", "equipCharacter")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def strict_json(data):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("duplicate JSON key: " + key)
            result[key] = value
        return result
    return json.loads(data, object_pairs_hook=pairs)


def pinned(path, expected, blob=None):
    data = path.read_bytes()
    if digest(data) != expected:
        raise ValueError("SHA-256 mismatch: " + str(path))
    if blob and hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest() != blob:
        raise ValueError("Git blob mismatch: " + str(path))
    return data


def git_object(clone, oid):
    env = dict(os.environ, GIT_NO_LAZY_FETCH="1", GIT_TERMINAL_PROMPT="0")
    return subprocess.check_output(["git", "-C", str(clone), "cat-file", "-p", oid], env=env).decode("utf-8")


def check_tree(clone):
    commit = git_object(clone, COMMIT)
    root = git_object(clone, ROOT).splitlines()
    excel = git_object(clone, EXCEL).splitlines()
    if not commit.startswith("tree " + ROOT + "\n") or ("040000 tree " + EXCEL + "\tExcelBinOutput") not in root:
        raise ValueError("commit/tree mismatch")
    if ("100644 blob " + README[0] + "\tREADME.md") not in root:
        raise ValueError("README tree mismatch")
    for name, blob, _ in FILES.values():
        if ("100644 blob " + blob + "\t" + name) not in excel:
            raise ValueError("Excel tree mismatch: " + name)


def target_keys(report):
    tables = [t for t in report["tables"] if t["name"] == "Weapon"]
    if len(tables) != 1 or tables[0]["count"] != len(tables[0]["rows"]):
        raise ValueError("invalid target Weapon table")
    keys = [r["key"] for r in tables[0]["rows"]]
    if any(type(x) is not int or x <= 0 for x in keys) or len(keys) != len(set(keys)):
        raise ValueError("invalid or duplicate target keys")
    return set(keys)


def candidate_index(tables):
    matches = defaultdict(list)
    for family, rows in tables.items():
        if not isinstance(rows, list):
            raise ValueError("candidate table is not a list")
        counts = Counter()
        for row in rows:
            value = row.get("id")
            if type(value) is not int or value <= 0:
                raise ValueError("missing or invalid candidate id: " + family)
            counts[value] += 1
        for value, count in counts.items():
            matches[value].append({"family": family, "rows": count})
    return matches


def analyze(deps, target, tables):
    keys = target_keys(target)
    candidates = candidate_index(tables)
    groups = defaultdict(list)
    inactive = []
    missing = Counter()
    for index, row in enumerate(deps["items"]):
        fields = row["fields"]
        absent = [key for key in FIELDS if key not in fields]
        if set(row["missing"]) != set(absent) | (set(row["missing"]) - set(FIELDS)):
            raise ValueError("projection missing list disagrees with fields")
        missing.update(row["missing"])
        active = fields.get("deleted") is not True and type(fields.get("count")) is int and fields["count"] > 0
        view = {key: fields[key] for key in FIELDS if key in fields}
        view["missing"] = row["missing"]
        if not active:
            inactive.append({"rowIndex": index, "projection": row})
        groups[(active, fields.get("itemId"))].append(view)
    result = []
    for (active, item_id), rows in sorted(groups.items(), key=lambda x: (not x[0][0], str(x[0][1]))):
        distributions = {}
        for field in ("count", "deleted", "level", "promoteLevel", "equipCharacter"):
            counter = Counter(json.dumps(r[field], sort_keys=True) if field in r else "<missing>" for r in rows)
            distributions[field] = [{"value": json.loads(k) if k != "<missing>" else "<missing>", "rows": v} for k, v in sorted(counter.items())]
        hits = candidates.get(item_id, []) if type(item_id) is int and item_id > 0 else []
        ambiguity = len(hits) > 1 or any(hit["rows"] > 1 for hit in hits)
        result.append({"active": active, "itemId": item_id, "rows": len(rows), "distribution": distributions,
                       "targetWeaponKey": item_id in keys if type(item_id) is int else False,
                       "candidateStatus": "ambiguous" if ambiguity else "present" if hits else "unknown",
                       "candidateMatches": hits})
    active = [g for g in result if g["active"]]
    return {"summary": {"savedRows": len(deps["items"]), "activeRows": sum(g["rows"] for g in active),
                        "inactiveRows": len(inactive), "activeDistinctPositiveIds": sum(type(g["itemId"]) is int and g["itemId"] > 0 for g in active),
                        "activeEquippedRows": sum(d["rows"] for g in active for d in g["distribution"]["equipCharacter"] if type(d["value"]) is int and d["value"] > 0),
                        "targetWeaponKeys": len(keys), "targetWeaponMatchedIds": sum(g["targetWeaponKey"] for g in active),
                        "targetWeaponMatchedRows": sum(g["rows"] for g in active if g["targetWeaponKey"]),
                        "candidateStatusIds": dict(Counter(g["candidateStatus"] for g in active)),
                        "candidateStatusRows": dict(Counter({status: sum(g["rows"] for g in active if g["candidateStatus"] == status) for status in ("present", "unknown", "ambiguous")})),
                        "missingFields": dict(missing)},
            "groups": result, "inactiveAudit": inactive,
            "limitations": ["Target Weapon intersection is key-only; no target item semantics or type inferred.", "Candidate presence is not target equivalence, resource readiness, or client acceptance.", "No alias, item type, ability, field omission or production change is approved."]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--clone", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    check_tree(args.clone)
    inputs = {name: pinned(path, sha) for name, (path, sha) in PINS.items()}
    pinned(CANDIDATE / "README.md", README[1], README[0])
    tables = {family: strict_json(pinned(CANDIDATE / name, sha, blob)) for family, (name, blob, sha) in FILES.items()}
    report = analyze(strict_json(inputs["dependencies"]), strict_json(inputs["target"]), tables)
    report["inputs"] = {name: sha for name, (_, sha) in PINS.items()}
    report["candidateInputs"] = {family: {"path": "ExcelBinOutput/" + name, "blob": blob, "sha256": sha} for family, (name, blob, sha) in FILES.items()}
    report["source"] = {"commit": COMMIT, "rootTree": ROOT, "excelTree": EXCEL, "readmeBlob": README[0], "readmeSHA256": README[1]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(json.dumps(report["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
