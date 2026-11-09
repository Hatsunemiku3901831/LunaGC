"""Audit two frozen candidate JSON files against current Java loader declarations.

This is an offline source/JSON comparison. It does not emulate Gson, execute Java,
approve aliases, or establish target-client configuration/ability compatibility.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUN = "local/migration72/d02-parallel-20261109/upstream-reuse/branch-retrieval2-20261109T133247Z-terra"
OUTPUT = "local/migration72/d02-parallel-20261109/r3-gadget-ability-candidate/audit.private.json"
KEY = "Weapon_Sword_Metal"
ABILITY = "Avatar_Weapon_Metal"
JAVA = "src/main/java/emu/grasscutter/"
CONFIG = JAVA + "data/binout/config/"
PINS = {
    "AGENTS.md": "17eaf014ed41ff6206eee98345604005de46f3def12299f6dd382ac90b24bd7c",
    RUN + "/manifest.private.json": "70aec1cb722b71280393389216bc5bceaa7c9d59aa04e2345411b042a1c4a80d",
    RUN + "/requests/Q05/body.bin": "700d2c4cb1ea11d5b3036002159326250b3817c127de087b697515321eda75a8",
    RUN + "/requests/Q06/body.bin": "b3f7f417c47ff4f2d726728450ca2092e07bc060458d874f7b919c57c2c564d2",
    "local/migration72/upstream-review-20261108/AstaPS-Resource/tree.json": "7871959ac380937c6b691adbd6f77e99885ffabb9dc6baa829cc1e84374f5115",
    "local/migration72/upstream-review-20261108/AstaPS-Resource/commit.json": "04021f23858a90f76419675e945cabfb63d3543afefddaac7977e04937ef7ea1",
    "local/migration72/d02-resume-20261109/upstream/resource-selected/LICENSE": "5653a2bde6fd785bc82cd78fb96dd28c8ebd335e968e5b1a00584c5e0713dda2",
    "local/migration72/d02-resume-20261109/r3-java-gadget-key/approved-lookup-key.private.json": "f5601d314c51a6417aeb899d26c80cfe2ecdf490c5ff869de2b11ab09fca7398",
    "local/migration72/d02-resume-20261109/r3-gadget-config-key/main-third.json": "10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd",
    "resources/BinOutput/Gadget/ConfigGadget_Avatar_Equip.json": "3beef16104a81fabd4c0bd14c8339d6f8eb790cdff65d4fbca55019944ef5993",
    "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Weapon.json": "dc0f61c3a52dc8cbc34b76dab152cfe9c9e5b9b35edfe19b844758e450e7032a",
    JAVA + "data/ResourceLoader.java": "fa7b7d26ef234e92f6d7eac5790922d36dc76fb113b5a31cc80c7bad40d2a015",
    JAVA + "data/GameData.java": "518585689d8181a31bef0558ab730939e5186ecd2ae98b0b20753db2ee879364",
    CONFIG + "ConfigEntityBase.java": "b415df104ca87d5ae93214eb3e5effd247fe540969c4cf594a2611a84d849c7c",
    CONFIG + "ConfigEntityGadget.java": "82f7e11ff26c5bce38a715e2ba0a76ebbb69637b64985576eb9e7e9fc9745a87",
    CONFIG + "fields/ConfigAbilityData.java": "91b04209694bae1068d21679497835ac89758cbc44321cfb3f8028ed858e9cef",
    CONFIG + "fields/ConfigCombat.java": "8cd14b38a8d802c691e2f2cb414aaf2b7b38d374d9dc5e3eaef8d2fa43cd264a",
    CONFIG + "fields/ConfigCombatProperty.java": "8d01ce201405c20ed9ee0e5a5be829d6085de5d284e222187030783752fbf080",
    CONFIG + "fields/ConfigCombatDie.java": "54d17fa62c99dfa172cf4c61f9768f2326889cd59958d1c4d25cf55b5124ce57",
    CONFIG + "fields/ConfigCommon.java": "dfa275160c7b4c0387816f0ea49d5ff6c2373c4ce18a70250e1167d9abf07bd4",
    JAVA + "data/binout/AbilityData.java": "b359372c4effa828e86d4bfe23b8199b0a11621dd6ebbc9871d25ae6e7d796bc",
    JAVA + "utils/JsonUtils.java": "4f5051e0bbd4a53d5993ab7b2750bbfd14c1fa41e722a69cf5effd1887453abf",
    JAVA + "game/entity/EntityWeapon.java": "67e74b4ef6bc5e0e8f2ef91effa69065a07d6692f55bf6185d49341c400a2d7e",
    JAVA + "game/entity/EntityBaseGadget.java": "0ed0dbb29ddba22caca838473c5f89679381c367113f1522e35e03761d600f6e",
    JAVA + "game/ability/AbilityManager.java": "4b07a55831a4b7fabcc89742a228de73b0a36f88a7e795d45fb57ca4c78c0fa9",
    JAVA + "game/ability/Ability.java": "90cbe4ae576f2cd3b6dc5eec82eb4f483c48ec7249d780a95154968e143cee19",
    JAVA + "utils/Utils.java": "5e1f7afd65b57b209d116dab05eb19a5ec37e1537435ad20659412d7ca047664",
}

# Exact Java spelling only. These are audit paths, never new SerializedName aliases.
FIELDS = {
    "abilities/*/abilityName": ("String", "ConfigAbilityData", "abilityName", "getAbilityName"),
    "abilities/*/abilityID": ("String", "ConfigAbilityData", "abilityID", "getAbilityID"),
    "abilities/*/abilityOverride": ("String", "ConfigAbilityData", "abilityOverride", "getAbilityOverride"),
    "combat/property/HP": ("float", "ConfigCombatProperty", "HP", "getHP"),
    "combat/property/isLockHP": ("boolean", "ConfigCombatProperty", "isLockHP", "isLockHP"),
    "combat/property/isInvincible": ("boolean", "ConfigCombatProperty", "isInvincible", "isInvincible"),
    "combat/property/isGhostToAllied": ("boolean", "ConfigCombatProperty", "isGhostToAllied", "isGhostToAllied"),
    "combat/property/attack": ("float", "ConfigCombatProperty", "attack", "getAttack"),
    "combat/property/defence": ("float", "ConfigCombatProperty", "defence", "getDefence"),
    "combat/property/weight": ("float", "ConfigCombatProperty", "weight", "getWeight"),
    "combat/property/useCreatorProperty": ("boolean", "ConfigCombatProperty", "useCreatorProperty", "isUseCreatorProperty"),
    "combat/die/dieEndTime": ("double", "ConfigCombatDie", "dieEndTime", "getDieEndTime"),
    "combat/die/dieForceDisappearTime": ("double", "ConfigCombatDie", "dieForceDisappearTime", "getDieForceDisappearTime"),
    "combat/die/hasAnimatorDie": ("boolean", "ConfigCombatDie", "hasAnimatorDie", "isHasAnimatorDie"),
}


def audit(root: Path = ROOT) -> dict:
    files = verify_inputs(root)
    manifest = parse(files[RUN + "/manifest.private.json"])
    tree = parse(files["local/migration72/upstream-review-20261108/AstaPS-Resource/tree.json"])
    commit = parse(files["local/migration72/upstream-review-20261108/AstaPS-Resource/commit.json"])
    sources = {entry["id"]: entry for entry in manifest["sources"]}
    verify_source_objects(files, sources, tree, commit)
    approval = parse(files["local/migration72/d02-resume-20261109/r3-java-gadget-key/approved-lookup-key.private.json"])
    if (approval["lookupKeyFragmentApproved"] is not True
            or approval["nativeField"]["value"] != KEY
            or approval["staticDomainWitness"]["configurationType"] != 25270):
        raise ValueError("Frozen target lookup-key/type-domain witness does not match")
    declarations = verify_java_fields(files)
    gadget = parse(files[sources["S-GADGET"]["rawLocalPath"]])
    abilities = parse(files[sources["S-ABILITY"]["rawLocalPath"]])
    result = compare(gadget, abilities, declarations)
    old_gadget = parse(files["resources/BinOutput/Gadget/ConfigGadget_Avatar_Equip.json"])
    old_ability = parse(files["resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Weapon.json"])
    result.update({
        "schema": "client72-gadget-ability-candidate-audit/v1",
        "taskIds": ["E-01/R3-ITEM-ENTRY", "R3-GADGET-CONFIG/ABILITY"],
        "analysisKind": "offline exact JSON/source declaration comparison; Java execution pending",
        "implementationPins": {str(path.relative_to(root)).replace("\\", "/"): sha(path.read_bytes())
                               for path in (root / "tools/client72_gadget_ability_candidate_audit.py",
                                            root / "tools/test_client72_gadget_ability_candidate_audit.py")},
        "inputPins": [{"path": name, "bytes": len(data), "sha256": sha(data)} for name, data in files.items()],
        "sourceObjects": [{"id": key, "commit": sources[key]["pinnedCommit40"],
                           "path": sources[key]["path"], "blob": sources[key]["blob40"]}
                          for key in ("S-GADGET", "S-ABILITY")],
        "license": "Same-commit MIT bytes/blob verified; game-data rights chain remains unestablished",
        "targetWitness": {"gadgetId": 50011101, "owner": 40472, "field": 199649,
                          "offset": "0x58", "key": KEY, "configurationType": 25270,
                          "baseType": 37853, "entityType": 28174, "entityField": 138419,
                          "scope": "approved lookup-key role only; no candidate JSON-to-target content bridge"},
        "localComparison": {
            "oldSelectedConfigCanonicalSHA256": canonical_sha(old_gadget[KEY]),
            "newSelectedConfigCanonicalSHA256": canonical_sha(gadget[KEY]),
            "abilityReferenceNamesEqual": old_gadget[KEY]["abilities"][0]["abilityName"] == ABILITY,
            "oldMetalDefaultKeys": sorted(old_ability[0]["Default"]),
            "newMetalDefaultKeys": sorted(abilities[0]["Default"]),
            "increment": "new complete candidate exposes unmapped Metal object and additional declared combat fields; no alias approval",
        },
        "javaInvocationSpecification": java_spec(),
        "javaCallSites": call_sites(files),
        "boundaries": {name: False for name in (
            "javaExecutedByThisAudit", "targetConfigurationRegistrationVerified", "targetConfigurationHitVerified",
            "candidateContentsApprovedForTarget", "unknownFieldsOmissionApproved", "emptyAbilityApproved",
            "entityWeaponConstructed", "fullGadgetRowApproved", "naturalLoadAllVerified", "activeRevisionVerified",
            "sceneOrPersistenceVerified", "resourceReady", "d02Complete")},
    })
    return result


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_sha(value) -> str:
    return sha(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode())


def parse(data: bytes):
    return json.loads(data.decode("utf-8-sig"))


def verify_inputs(root: Path) -> dict[str, bytes]:
    files = {}
    for name, expected in PINS.items():
        data = (root / name).read_bytes()
        if sha(data) != expected:
            raise ValueError("Input SHA-256 mismatch: " + name)
        files[name] = data
    return files


def verify_source_objects(files: dict, sources: dict, tree: dict, commit: dict) -> None:
    if tree["truncated"] or tree["sha"] != "3cf83488a94b47a018934e5f91c1bf7e93357aae":
        raise ValueError("Wrong/truncated source tree")
    if commit["sha"] != "6cfecfb55f889f8e3b7156adb12809608db4f74b":
        raise ValueError("Wrong source commit")
    if commit["commit"]["tree"]["sha"] != tree["sha"]:
        raise ValueError("Commit/tree mismatch")
    entries = {entry["path"]: entry for entry in tree["tree"]}
    for source_id in ("S-GADGET", "S-ABILITY"):
        item = sources[source_id]
        raw = files[item["rawLocalPath"]]
        blob = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
        entry = entries[item["path"]]
        if blob != item["blob40"] or blob != entry["sha"] or len(raw) != entry["size"]:
            raise ValueError("Raw bytes/blob/tree mismatch: " + source_id)
    raw = files["local/migration72/d02-resume-20261109/upstream/resource-selected/LICENSE"]
    blob = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
    if entries["LICENSE"]["sha"] != blob:
        raise ValueError("License/tree blob mismatch")


def declaration(files: dict, file: str, field_type: str, field: str) -> dict:
    text = files[file].decode("utf-8-sig")
    match = re.search(r"\b" + re.escape(field_type) + r"\s+" + re.escape(field) + r"\s*;", text)
    if match is None:
        raise ValueError("Java declaration absent: " + file + "#" + field)
    return {"file": file, "line": text[:match.start()].count("\n") + 1,
            "type": field_type, "field": field}


def call_sites(files: dict) -> list[dict]:
    result = []
    for file, needle, role in [
        ("data/ResourceLoader.java", 'GameData.getGadgetConfigData(), "BinOutput/Gadget/", ConfigEntityGadget.class', "gadget directory -> typed map loader"),
        ("data/ResourceLoader.java", "var config = JsonUtils.decode(entry.getValue(), configClass);", "entry-value -> Gson/typed Java object"),
        ("data/ResourceLoader.java", "if (config != null) targetMap.put(entry.getKey(), config);", "exact aggregate entry-key registration"),
        ("data/ResourceLoader.java", "JsonUtils.loadToList(path, AbilityConfigData.class)", "ability root list -> typed Default wrapper"),
        ("data/ResourceLoader.java", "loadAbilityData(data.Default);", "Default -> actual ability registration"),
        ("data/ResourceLoader.java", "GameData.getAbilityDataMap().put(data.abilityName, data);", "exact ability name map registration"),
        ("data/ResourceLoader.java", "GameData.getAbilityHashes().put(Utils.abilityHash(data.abilityName), data.abilityName);", "signed Java base131 hash reverse registration"),
        ("game/entity/EntityWeapon.java", "GameData.getGadgetConfigData().get(gadgetData.getJsonName());", "GadgetData lookup-key consumer"),
        ("game/entity/EntityWeapon.java", "GameData.getAbilityData(abilityData.getAbilityName());", "configured ability reference consumer"),
        ("game/entity/EntityWeapon.java", "player.getAbilityManager().addAbilityToEntity(this, data);", "registered data -> entity ability consumer, not run"),
        ("game/entity/EntityBaseGadget.java", "var def = combatProperties.getDefence();", "Java defence field -> fight properties, not run"),
        ("game/ability/AbilityManager.java", "var ability = new Ability(abilityData, entity, this.player);", "Ability creation, not run"),
        ("game/ability/Ability.java", "data.initialize();", "ability content initialization, not run"),
    ]:
        source = files[JAVA + file].decode("utf-8-sig")
        offset = source.find(needle)
        if offset < 0:
            raise ValueError("Expected Java call absent: " + file + " " + needle)
        result.append({"file": JAVA + file, "line": source[:offset].count("\n") + 1,
                       "text": needle, "role": role})
    return result


def verify_java_fields(files: dict) -> dict:
    result = {}
    for path, (kind, owner, field, getter) in FIELDS.items():
        item = declaration(files, CONFIG + "fields/" + owner + ".java", kind, field)
        result[path] = {**item, "owner": owner, "getter": getter, "getterSource": "Lombok @Data"}
    result["Default/abilityName"] = declaration(files, JAVA + "data/binout/AbilityData.java", "String", "abilityName")
    # These container declarations establish the path, including inherited fields.
    for kind, field in [("ConfigCombat", "combat"), ("Collection<ConfigAbilityData>", "abilities"),
                        ("ConfigCommon", "configCommon")]:
        declaration(files, CONFIG + "ConfigEntityBase.java", kind, field)
    for kind, field in [("ConfigCombatProperty", "property"), ("ConfigCombatDie", "die")]:
        declaration(files, CONFIG + "fields/ConfigCombat.java", kind, field)
    declaration(files, JAVA + "data/ResourceLoader.java", "AbilityData", "Default")
    return result


def leaves(value, pointer=""):
    if isinstance(value, dict):
        if not value:
            yield pointer, value
        for key, item in value.items():
            yield from leaves(item, pointer + "/" + str(key).replace("~", "~0").replace("/", "~1"))
    elif isinstance(value, list):
        if not value:
            yield pointer, value
        for index, item in enumerate(value):
            yield from leaves(item, pointer + "/" + str(index))
    else:
        yield pointer, value


def typed_value(value, kind: str):
    if kind == "float":
        return struct.unpack("<f", struct.pack("<f", value))[0]
    return value


def ability_hash(name: str) -> int:
    value = 0
    for (unit,) in struct.iter_unpack("<H", name.encode("utf-16-le")):
        value = (unit + 131 * value) & 0xffffffff
    return value if value < 0x80000000 else value - 0x100000000


def compare(gadget: dict, abilities: list, declarations: dict) -> dict:
    selected = gadget[KEY]
    metal = [(index, item["Default"]) for index, item in enumerate(abilities)
             if item.get("Default", {}).get("abilityName") == ABILITY]
    if len(metal) != 1:
        raise ValueError("Expected exactly one complete Metal Default object")
    index, definition = metal[0]
    classified = []
    for pointer, value in leaves(selected):
        schema_path = re.sub(r"(?<=abilities/)\d+(?=/)", "*", pointer.lstrip("/"))
        destination = declarations.get(schema_path)
        row = {"pointer": "/" + KEY + pointer, "value": value,
               "status": "declared-java-field" if destination else "unmapped-java-input"}
        if destination:
            row.update({"java": destination, "predictedJavaValue": typed_value(value, destination["type"])})
        classified.append(row)
    ability_classified = []
    for pointer, value in leaves(definition):
        destination = declarations.get("Default" + pointer)
        ability_classified.append({"pointer": f"/{index}/Default" + pointer, "value": value,
                                   "status": "declared-java-field" if destination else "unmapped-java-input",
                                   **({"java": destination} if destination else {})})
    names = [item["Default"]["abilityName"] for item in abilities if item.get("Default", {}).get("abilityName")]
    refs = sorted({ability["abilityName"] for item in gadget.values()
                   for ability in item.get("abilities", []) if ability.get("abilityName")})
    selected_refs = [item["abilityName"] for item in selected.get("abilities", []) if item.get("abilityName")]
    missing = sorted(set(selected_refs) - set(names))
    return {
        "candidateConfigEntryCount": len(gadget), "candidateAbilityEntryCount": len(abilities),
        "selectedConfigPointer": "/" + KEY, "selectedConfigCanonicalSHA256": canonical_sha(selected),
        "selectedAbilityPointer": f"/{index}/Default", "selectedAbilityCanonicalSHA256": canonical_sha(definition),
        "selectedConfigFields": classified, "selectedAbilityFields": ability_classified,
        "classificationCounts": {"config": dict(Counter(row["status"] for row in classified)),
                                 "ability": dict(Counter(row["status"] for row in ability_classified))},
        "references": {"selected": selected_refs, "selectedMissing": missing,
                       "selectedExactNameClosure": not missing and bool(selected_refs),
                       "wholeFileNames": refs, "wholeFileMissing": sorted(set(refs) - set(names)),
                       "registeredNameCandidates": [{"name": name, "javaAbilityHashSigned": ability_hash(name)} for name in names],
                       "scope": "candidate exact-name linkage only, not target registration or semantic equivalence"},
        "javaDefaultObservations": [
            {"pointer": "/" + KEY + "/combat/property/defense", "sourceValue": selected["combat"]["property"]["defense"],
             "javaField": "ConfigCombatProperty.defence", "predictedJavaValue": 0.0,
             "reason": "different spelling; no defense alias in the pinned Java declaration"},
            {"pointer": "/" + KEY + "/common", "javaField": "ConfigEntityBase.configCommon",
             "predictedJavaValue": None, "reason": "common is not declared configCommon; no alias"},
            {"pointer": f"/{index}/Default/LKLPDIMKLKI", "javaField": None,
             "reason": "complete four-leaf object has no AbilityData destination; cannot infer empty ability"},
        ],
    }


def java_spec() -> dict:
    return {
        "resources": [
            {"source": RUN + "/requests/Q05/body.bin", "target": "R/BinOutput/Gadget/ConfigGadget_Avatar_Equip.json"},
            {"source": RUN + "/requests/Q06/body.bin", "target": "R/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Weapon.json"},
        ],
        "scripts": "S empty; no ScriptLoader or scene", "data": "D empty, no DB/cache reuse",
        "calls": [
            "private ResourceLoader.loadConfigMapFile(Map,Path,Class): actual GameData.gadgetConfigData, Q05, ConfigEntityGadget.class",
            "private ResourceLoader.loadAbilityModifiers(Path): Q06; actual GameData.abilityDataMap and abilityHashes",
            "ConfigEntityGadget.getAbilities -> ConfigAbilityData.getAbilityName -> GameData.getAbilityData(name)",
        ],
        "restoration": "snapshot/clear/finally restore three maps; assert loadedAll remains unchanged; no forced readiness",
        "assertions": [
            "34 config entries and 3 named abilities; exact selected key, unique Metal ability ID/name",
            "HP float9999999, isInvincible/isLockHP/useCreatorProperty true, dieForceDisappearTime double0.009999999776482582",
            "defence float0 and configCommon null are Java losses, not approved translations",
            "registered selected AbilityData is same object returned by exact reference; each signed hash reverses to its exact name",
            "raw Metal Default retains LKLPDIMKLKI with four leaves in source, absent from Java; no empty-ability approval",
            "rename config key/missing ability controls affect Java lookup; controls give no target omission permission",
        ],
        "consumerNotExecuted": "EntityWeapon constructor/initAbilities -> AbilityManager.addAbilityToEntity -> Ability constructor/initialize/onAdded",
        "reason": "complete target Gadget/config bytes and unknown ability-object semantics are unproved; local registration is independently testable",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, default=ROOT / OUTPUT)
    args = parser.parse_args()
    result = audit(args.root)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "sha256": sha(args.output.read_bytes()),
                      "classificationCounts": result["classificationCounts"], "javaExecuted": False}, ensure_ascii=False))


if __name__ == "__main__":
    main()
