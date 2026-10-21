"""Export reproducible Abyss evidence, never infer waves from monster previews.

Lua remains the executable source for waves/tides/AI. Suite membership is exported
as suite membership (a suite is NOT necessarily a wave). No Lua is executed here.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def table(text, name):
    match = re.search(r"(?m)^\s*" + re.escape(name) + r"\s*=\s*\{", text)
    if not match:
        return ""
    start = match.end() - 1
    depth = 0
    # These configuration tables contain only literals. Strip comments first.
    for i in range(start, len(text)):
        if text[i] == "{": depth += 1
        if text[i] == "}": depth -= 1
        if depth == 0: return text[start:i + 1]
    raise ValueError(f"unclosed {name} table")


def entries(text):
    depth, start = 0, None
    for i, ch in enumerate(text):
        if ch == "{":
            depth += 1
            if depth == 2: start = i
        elif ch == "}":
            if depth == 2 and start is not None: yield text[start:i + 1]
            depth -= 1


def integer(text, key, default=0):
    m = re.search(r"\b" + key + r"\s*=\s*(-?\d+)", text)
    return int(m[1]) if m else default


def vector(text, key):
    m = re.search(r"\b" + key + r"\s*=\s*\{([^}]+)\}", text)
    return {k: float(v) for k, v in re.findall(r"([xyz])\s*=\s*(-?[\d.]+)", m[1])} if m else {}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def export(root):
    resources = root / "resources"
    inputs = {}
    def read(name, key=None):
        path = resources / "ExcelBinOutput" / (name + "ExcelConfigData.json")
        inputs[path.relative_to(resources).as_posix()] = sha(path)
        rows = json.loads(path.read_text(encoding="utf-8-sig"))
        return {r[key]: r for r in rows} if key else rows
    schedules = read("TowerSchedule")
    floors = read("TowerFloor", "floorId")
    levels = read("TowerLevel", "levelId")
    dungeons = read("Dungeon", "id")
    monsters = read("Monster", "id")
    curves = read("MonsterCurve", "level")
    scene_cache = {}

    def scene(scene_id):
        if scene_id in scene_cache: return scene_cache[scene_id]
        directory = resources / "Scripts" / "Scene" / str(scene_id)
        missing, files, groups = [], {}, []
        main = directory / f"scene{scene_id}.lua"
        if not main.exists(): missing.append(main.relative_to(resources).as_posix())
        else:
            files[main.relative_to(resources).as_posix()] = sha(main)
            body = main.read_text(encoding="utf-8-sig")
            block_ids = re.findall(r"\d+", table(body, "blocks"))
            if not block_ids: missing.append("no scene blocks")
            for block_id in block_ids:
                block = directory / f"scene{scene_id}_block{block_id}.lua"
                if not block.exists(): missing.append(block.relative_to(resources).as_posix()); continue
                files[block.relative_to(resources).as_posix()] = sha(block)
                b = block.read_text(encoding="utf-8-sig")
                for gid in re.findall(r"\bid\s*=\s*(\d+)", table(b, "groups")):
                    path = directory / f"scene{scene_id}_group{gid}.lua"
                    if not path.exists(): missing.append(path.relative_to(resources).as_posix()); continue
                    files[path.relative_to(resources).as_posix()] = sha(path)
                    body = re.sub(r"--[^\n]*", "", path.read_text(encoding="utf-8-sig"))
                    definitions = []
                    for entry in entries(table(body, "monsters")):
                        mid = integer(entry, "monster_id")
                        if mid not in monsters: missing.append(f"monster:{mid}")
                        definitions.append({"configId": integer(entry, "config_id"), "monsterId": mid,
                            "scriptLevel": integer(entry, "level"), "position": vector(entry, "pos"),
                            "rotation": vector(entry, "rot"), "poseId": integer(entry, "pose_id"),
                            "aiConfigId": integer(entry, "ai_config_id", -1),
                            "affixes": [int(x) for x in re.findall(r"\d+", table("affix = " + (re.search(r"affix\s*=\s*(\{[^}]*\})", entry)[1] if re.search(r"affix\s*=\s*(\{[^}]*\})", entry) else "{}"), "affix"))],
                            "isElite": bool(re.search(r"isElite\s*=\s*true", entry)),
                            "disableWander": bool(re.search(r"disableWander\s*=\s*true", entry))})
                    suites = []
                    for index, entry in enumerate(entries(table(body, "suites")), 1):
                        m = re.search(r"monsters\s*=\s*\{([^}]*)\}", entry)
                        suites.append({"suite": index, "monsterConfigIds": [int(x) for x in re.findall(r"\d+", m[1])] if m else []})
                    groups.append({"groupId": int(gid), "monsters": definitions, "suites": suites,
                        "script": path.relative_to(resources).as_posix(),
                        "challengeCalls": re.findall(r"ScriptLib\.ActiveChallenge\([^\n]+", body),
                        "tideCalls": re.findall(r"ScriptLib\.(?:AutoMonsterTide|AutoPoolMonsterTide)\([^\n]+", body),
                        "scriptApis": sorted(set(re.findall(r"ScriptLib\.(\w+)", body)))})
        if not groups or not any(g["monsters"] for g in groups): missing.append("no monster groups")
        result = {"sceneId": scene_id, "files": files, "groups": groups, "missing": sorted(set(missing))}
        scene_cache[scene_id] = result
        return result

    floor_cache = {}
    for floor in floors.values():
        if not 8 <= floor["floorIndex"] <= 12: continue
        chambers = []
        for level in sorted((x for x in levels.values() if x["levelGroupId"] == floor["levelGroupId"]), key=lambda x: x["levelIndex"]):
            dungeon = dungeons.get(level["dungeonId"], {})
            sid = dungeon.get("sceneId", 0)
            scene(sid)
            chambers.append({"levelId": level["levelId"], "chamber": level["levelIndex"],
                "dungeonId": level["dungeonId"], "sceneId": sid, "monsterLevel": level["monsterLevel"] + 1,
                "starConditions": level["conds"], "previewOnly": {k: v for k, v in level.items() if "monster" in k.lower() and k != "monsterLevel"}})
        floor_cache[str(floor["floorId"])] = {"floorId": floor["floorId"], "floor": floor["floorIndex"],
            "teamCount": floor["teamNum"], "baseHpMultiplier": 2.5 if floor["floorIndex"] == 12 else 2.0,
            "floorLevelConfigId": floor.get("floorLevelConfigId", 0), "chambers": chambers}

    periods = []
    for schedule in schedules:
        for detail in schedule.get("schedules", schedule.get("LHOGNLPBILP", [])):
            opening = detail.get("openTime", "")
            if not opening or not "2022" <= opening[:4] <= "2026" or not detail.get("floorList"): continue
            ids = [1008] + detail["floorList"]
            problems = []
            for fid in ids:
                f = floor_cache.get(str(fid))
                if not f: problems.append(f"floor:{fid}"); continue
                if len(f["chambers"]) != 3: problems.append(f"floor:{fid}:expected-three-chambers")
                for c in f["chambers"]: problems.extend(scene_cache[c["sceneId"]]["missing"])
            periods.append({"scheduleId": schedule["scheduleId"], "startsAt": opening,
                "endsAt": schedule.get("closeTime", ""), "timezone": "Asia/Shanghai",
                "floorIds": ids, "monthlyLevelConfigId": schedule.get("monthlyLevelConfigId", 0),
                "status": "scripted" if not problems else "unavailable",
                "missing": sorted(set(problems)), "fidelity": "resource-derived; live-client combat not certified"})

    used_monsters = {m["monsterId"] for s in scene_cache.values() for g in s["groups"] for m in g["monsters"]}
    stats = {str(mid): {k: v for k, v in monsters[mid].items() if k in
        {"id", "monsterName", "nameTextMapHash", "hpBase", "attackBase", "defenseBase", "propGrowCurves", "ai", "affix", "describeId"} or k.endswith("SubHurt")} for mid in sorted(used_monsters) if mid in monsters}
    revision = subprocess.check_output(["git", "-C", str(resources), "rev-parse", "HEAD"], text=True).strip()
    return {"schemaVersion": 1, "range": [2022, 2026], "resourceRevision": revision,
        "sources": [{"url": "https://github.com/invoker-bot/LunaGC-Resources/tree/" + revision,
                     "kind": "community resource snapshot"},
                    {"url": "https://github.com/Hiro420/GS_Lua/tree/f9eb0f823c15c0bf26e6a2a0406697655ac07af7", "finding": "5.6.0 contains scene Lua, no server group Lua"},
                    {"url": "https://homdgcat.wiki/gi/abyss", "finding": "unreachable at research time; no data imported"},
                    {"url": "https://gi.nanoka.cc/tower/20091", "finding": "HTTP 403 on data retrieval; no data imported"}],
        "limitations": ["No official-accuracy certification. Current monster curves are not versioned historical curves.",
            "Suite membership is not wave order. Referenced Lua owns spawning, tides, timing and positions.",
            "Unavailable periods are skipped; preview data never substitutes for wave scripts.",
            "AI, boss mechanics, blessings and 7.1 protocol require separate client verification.",
            "Calendar gaps and periods not present in the source are not fabricated."],
        "inputs": inputs, "periods": sorted(periods, key=lambda x: (x["startsAt"], x["scheduleId"])),
        "floors": floor_cache, "scenes": {str(k): v for k, v in sorted(scene_cache.items())},
        "monsterStats": stats, "monsterCurves": list(curves.values())}


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "src/main/resources/abyss/catalog.json")
    args = parser.parse_args()
    result = export(ROOT)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"periods": len(result["periods"]), "scripted": sum(p["status"] == "scripted" for p in result["periods"]), "output": str(args.output)}))


if __name__ == "__main__": main()
