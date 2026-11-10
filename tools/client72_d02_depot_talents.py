"""Project approved birth Depot talents without inferring complete resources."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / "local/migration72/d02-resume-20261110/c-depot-talents-implementation"
INPUTS = {
    "mapping": ("local/migration72/d02-resume-20261108/r3-mapping/main-third.json", "d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e"),
    "payload": ("local/migration72/d02-active/resource-locator-astra/locator-final-1/AvatarSkillDepot.native-m_bytes.bin", "fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b"),
    "static": ("local/migration72/d02-resume-20261110/c-depot-talents-successor/first.private.json", "de0c23d2acb6565f6c413af183fd1e5772972e0764e462cde7f96a5d77846ad3"),
    "enum": ("local/migration72/d02-resume-20261110/c-depot-enhance-enum-main/main-review.private.json", "0f65d5f9324d51a25775ea3e39c0a829e75892925a992dd1213a336c36d371cb"),
    "avatar": ("local/migration72/d02-resume-20261110/c-birth-avatar-join/first.json", "2ba724156e1716b81a129b81eb3a6333a0be069e8df28dba20257a7cce9cb3a6"),
    "avatarManifest": ("local/migration72/d02-resume-20261110/c-birth-avatar-join/manifest-first.json", "5fc97224c06167be3e3ec0bcdb0993d6a4a46508c0c8779de06aefd5db50ab2b"),
    "spec": ("docs/CLIENT_72_D02_DEPOT_TALENTS_SUCCESSOR_20261110.md", "3e5263046dfd970ec9af3bc4866964beda98de74cd1fde6cd4dd824b9784220d"),
}
KEYS = (501, 701, 704)
SOURCES = {
    501: {"begin": 426, "end": 577, "sha256": "943f15501042ae657b27bf424e8bae2c0fbd225a70e80622fa453dada40037cf"},
    701: {"begin": 2196, "end": 2348, "sha256": "c8150bc63a7055b7a1aa466c8683ff1035c0aec58f68f1616d30f33f19103914"},
    704: {"begin": 2770, "end": 2988, "sha256": "6e6aeaac927901eec5b35009d2538bdaabff9d52179cc35d0e1ca31d667d05f6"},
}
AVATAR_FIELDS = {"id", "hpBase", "attackBase", "defenseBase", "propGrowCurves", "critical", "criticalHurt", "skillDepotId", "initialWeapon", "avatarPromoteId"}


def read_inputs():
    inputs = {}
    for name, (path, digest) in INPUTS.items():
        raw = (ROOT / path).read_bytes()
        if hashlib.sha256(raw).hexdigest() != digest:
            raise ValueError(f"{name} SHA-256 differs")
        inputs[name] = raw if name in ("payload", "spec") else json.loads(raw)
    return inputs


def unique(rows, predicate, label):
    matches = [row for row in rows if predicate(row)]
    if len(matches) != 1:
        raise ValueError(f"{label}: missing or duplicate")
    return matches[0]


def project(inputs):
    mapping, payload = inputs["mapping"], inputs["payload"]
    if len(payload) != 26024 or hashlib.sha256(payload).hexdigest() != INPUTS["payload"][1]:
        raise ValueError("payload identity differs")
    table = unique(mapping["native"]["parsedTables"], lambda row: row["name"] == "AvatarSkillDepot", "Depot table")
    if (table["count"] != 171 or len(table["rows"]) != 171 or table["cursor"] != 26024
            or table["payloadBytes"] != 26024 or table["payloadSHA256"] != INPUTS["payload"][1]):
        raise ValueError("Depot complete-table identity differs")
    keys = [row["key"] for row in table["rows"]]
    if any(type(key) is not int for key in keys) or len(set(keys)) != 171:
        raise ValueError("Depot complete-table duplicate or invalid key")
    if not inputs["enum"]["passed"] or inputs["static"]["semanticMappingClosed"] is not True:
        raise ValueError("static/enum evidence not accepted")
    fragments, merged = [], []
    for key in KEYS:
        original = unique(table["rows"], lambda row: row["key"] == key, f"Depot {key} original row")
        selected = unique(table["selected"], lambda row: row["key"] == key, f"Depot {key} selected row")
        source = SOURCES[key]
        if (original["source"] != source or selected["source"] != source or selected["found"] is not True
                or hashlib.sha256(payload[source["begin"]:source["end"]]).hexdigest() != source["sha256"]):
            raise ValueError(f"Depot {key}: source/span differs")
        field = unique(selected["fields"], lambda item: item["fieldIndex"] == 105357, f"Depot {key} typed field")
        typ = field["type"]
        if (field["owner"] != 22284 or field["offset"] != "0x28" or typ["kind"] != "szarray"
                or typ["rank"] != 1 or typ["elementType"]["kind"] != "valuetype"
                or typ["elementType"]["typeDefinition"] != 58209):
            raise ValueError(f"Depot {key}: typed identity differs")
        talents = field["value"]
        if not isinstance(talents, list) or any(type(x) is not int or not 0 <= x <= 0x7fffffff for x in talents):
            raise ValueError(f"Depot {key}: invalid talents list")
        bridge = unique(mapping["javaProjectionFragments"],
                        lambda row: row["table"] == "AvatarSkillDepot" and row["key"] == key,
                        f"Depot {key} bridge")
        fields = bridge["javaFields"]
        if (bridge["fragmentOnly"] is not True or bridge["source"] != source
                or set(fields) != {"id", "skills", "energySkill"} or type(fields["id"]) is not int or fields["id"] != key):
            raise ValueError(f"Depot {key}: bridge identity differs")
        fragment = {"table": "AvatarSkillDepot", "key": key, "source": source,
                    "javaFields": {"id": key, "talents": talents}, "fragmentOnly": True}
        fragments.append(fragment)
        merged.append({**fields, "talents": talents})
    avatars = inputs["avatar"]
    if (len(avatars) != 2 or [row.get("id") for row in avatars] != [10000005, 10000007]
            or any(set(row) != AVATAR_FIELDS for row in avatars)
            or inputs["avatarManifest"]["completeAvatar"] is not False):
        raise ValueError("approved birth Avatar join differs")
    return {
        "task": "E-01/R3-DEPOT-TALENTS", "partial": True, "completeAvatar": False,
        "resourceReady": False, "d02Complete": False, "staticThirdReviewed": False,
        "targetSemanticsPending": ["proud-skill fields and ability references", "Talent/OpenConfig for nonzero or saved unlocks", "full Avatar construction and resource revision", "manager persistence and D-02 client acceptance"],
        "inputs": [{"name": name, "path": path, "sha256": digest} for name, (path, digest) in INPUTS.items()],
        "depotFragments": fragments, "mergedDepots": merged, "birthAvatarInputs": avatars,
        "sourceNote": "Talents are existing decoded typed field values; no second codec, object hex or production JSON.",
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.parent != PRIVATE.resolve() or output.exists():
        raise ValueError("output must be a new file in the private task directory")
    result = project(read_inputs())
    raw = (json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode("utf-8")
    with output.open("xb") as stream:
        stream.write(raw)
    print(f"bytes={len(raw)} sha256={hashlib.sha256(raw).hexdigest()}")


if __name__ == "__main__":
    main()
