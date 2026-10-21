#!/usr/bin/env python3
"""Read-only Spiral Abyss resource audit; no server, client, or database writes.

The candidate test mirrors TowerSystem.isPlayable, not a gameplay acceptance test.
Paths in the JSON are repository-relative. Only --output writes an analysis file.
"""

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import subprocess


def git_revision(root):
    result = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "HEAD"],
        capture_output=True, text=True, check=False,
    )
    return result.stdout.strip() if result.returncode == 0 else None


def audit(root, selected=None):
    resources = root / "resources"
    sources = {}

    def read_table(filename):
        path = resources / "ExcelBinOutput" / filename
        data = path.read_bytes()
        rows = json.loads(data.decode("utf-8-sig"))
        sources[path.relative_to(root).as_posix()] = {
            "sha256": hashlib.sha256(data).hexdigest(), "rows": len(rows)
        }
        return rows

    schedules = read_table("TowerScheduleExcelConfigData.json")
    floors = {x["floorId"]: x for x in read_table("TowerFloorExcelConfigData.json")}
    levels = read_table("TowerLevelExcelConfigData.json")
    dungeons = {x["id"]: x for x in read_table("DungeonExcelConfigData.json")}
    rewards = {x["rewardId"]: x for x in read_table("RewardExcelConfigData.json")}
    read_table("TowerRewardExcelConfigData.json")
    read_table("TowerBuffExcelConfigData.json")
    groups = defaultdict(list)
    for level in levels:
        groups[level["levelGroupId"]].append(level)
    scripts = {}

    def scene_scripts(scene):
        if scene not in scripts:
            directory = resources / "Scripts" / "Scene" / str(scene)
            scripts[scene] = sorted(
                p.relative_to(root).as_posix()
                for p in directory.glob(f"scene{scene}_group*.lua") if p.is_file()
            )
        return scripts[scene]

    def details(schedule):
        return [x for x in schedule.get("schedules", schedule.get("LHOGNLPBILP", []))
                if x and x.get("floorList")]

    def schedule_floors(schedule):
        return list(schedule.get("entranceFloorId", [])) + [
            floor for detail in details(schedule) for floor in detail["floorList"]
        ]

    def problems(schedule):
        ids = schedule_floors(schedule)
        if not ids:
            return ["empty-floor-list"]
        result = []
        for fid in ids:
            floor = floors.get(fid)
            if not floor:
                result.append({"floorId": fid, "reason": "missing-floor"})
                continue
            members = groups[floor["levelGroupId"]]
            if not members:
                result.append({"floorId": fid, "reason": "missing-levels"})
            for level in members:
                dungeon = dungeons.get(level["dungeonId"])
                if not dungeon or not scene_scripts(dungeon["sceneId"]):
                    result.append({"floorId": fid, "levelId": level["levelId"],
                                   "dungeonId": level["dungeonId"],
                                   "sceneId": dungeon["sceneId"] if dungeon else None,
                                   "reason": "missing-dungeon-or-group-script"})
        return result

    rejected = {}
    playable = []
    for schedule in schedules:
        failures = problems(schedule)
        if failures:
            rejected[str(schedule["scheduleId"])] = failures
        else:
            playable.append(schedule["scheduleId"])
    playable.sort()
    chosen_id = selected if selected is not None else (playable[-1] if playable else None)
    chosen = next((s for s in schedules if s["scheduleId"] == chosen_id), None)
    if selected is not None and chosen is None:
        raise ValueError(f"Schedule {selected} does not exist")
    floor_rows = []
    if chosen:
        # TowerSystem.getScheduleFloors uses only the first nonempty detail.
        offered = chosen.get("entranceFloorId", []) + (
            details(chosen)[0]["floorList"] if details(chosen) else []
        )
        for fid in offered:
            floor = floors.get(fid)
            if not floor:
                floor_rows.append({"floorId": fid, "missing": True})
                continue
            row = {k: floor.get(k) for k in [
                "floorId", "floorIndex", "levelGroupId", "teamNum", "unlockStarCount",
                "floorLevelConfigId", "overrideMonsterLevel"]}
            row["levels"] = []
            members = sorted(groups[floor["levelGroupId"]], key=lambda x: x["levelIndex"])
            row["threeConsecutiveLevels"] = (
                [x["levelIndex"] for x in members] == [1, 2, 3]
                and [x["levelId"] for x in members] == list(range(members[0]["levelId"], members[0]["levelId"] + 3))
            ) if members else False
            for level in members:
                dungeon = dungeons.get(level["dungeonId"], {})
                scene = dungeon.get("sceneId", 0)
                reward = rewards.get(level.get("firstPassRewardId"), {})
                row["levels"].append({
                    **{k: level.get(k) for k in ["levelId", "levelIndex", "dungeonId", "firstPassRewardId"]},
                    "sceneId": scene, "passCond": dungeon.get("passCond"),
                    "serverMonsterLevel": level.get("monsterLevel", 0) + 1,
                    "starConditions": [{k: c.get(k) for k in ["towerCondType", "argumentList"]}
                                       for c in level.get("conds", [])],
                    "firstPassItems": [x for x in reward.get("rewardItemList", [])
                                       if x.get("itemId", 0) > 0 and x.get("itemCount", 0) > 0],
                    "groupScripts": scene_scripts(scene),
                })
            floor_rows.append(row)
    return {
        "schemaVersion": 1,
        "scope": "Static source-resource audit; scripted candidates are not verified gameplay. Does not read runtime config or player data.",
        "sourceCommit": git_revision(root), "resourceCommit": git_revision(resources),
        "sources": sources, "scheduleCount": len(schedules),
        "playableCandidateCount": len(playable), "playableCandidateIds": playable,
        "rejectedSchedules": rejected,
        "selectionPolicy": "explicit" if selected is not None else "newest-scripted-candidate",
        "selectedScheduleId": chosen_id,
        "selectedMonthlyLevelConfigId": chosen.get("monthlyLevelConfigId") if chosen else None,
        "selectedResourceRewardGroup": chosen.get("rewardGroup") if chosen else None,
        "floors": floor_rows,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--schedule", type=int)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = audit(args.root.resolve(), args.schedule)
    payload = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
        print(json.dumps({k: result[k] for k in ["scheduleCount", "playableCandidateCount", "selectedScheduleId"]}))
    else:
        print(payload, end="")


if __name__ == "__main__":
    main()
