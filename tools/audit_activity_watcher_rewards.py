"""Read original watcher reward tables without editing resources or reward quantities."""
import argparse
import collections
import hashlib
import json
from pathlib import Path

from audit_activity_currencies import audit as currency_audit

ROOT = Path(__file__).resolve().parents[1]
ITEM_FILES = ["MaterialExcelConfigData.json", "WeaponExcelConfigData.json",
              "ReliquaryExcelConfigData.json", "HomeWorldFurnitureExcelConfigData.json",
              "TpsWeaponExcelConfigData.json"]


def audit(resources):
    currencies = currency_audit(resources)
    inputs = dict(currencies["inputSha256"])

    def load(name):
        content = (resources / "ExcelBinOutput" / name).read_bytes().replace(b"\r\n", b"\n")
        inputs[name] = hashlib.sha256(content).hexdigest()
        return json.loads(content.decode("utf-8-sig"))

    activities = load("NewActivityExcelConfigData.json")
    watchers = {row["id"]: row for row in load("NewActivityWatcherConfigData.json")}
    rewards = {row["rewardId"]: row for row in load("RewardExcelConfigData.json")}
    items = {row["id"]: row for name in ITEM_FILES for row in load(name)}
    owners = {row["itemId"]: row["activityId"] for row in currencies["currencies"]}
    type_rows = collections.Counter()
    issues, missing, special = [], [], set()
    totals = collections.Counter()
    for activity in activities:
        for watcher_id in activity.get("watcherId", []):
            watcher = watchers.get(watcher_id)
            identity = dict(activityId=activity["activityId"], watcherId=watcher_id)
            if watcher is None:
                missing.append(dict(**identity, reason="missing-watcher"))
                continue
            if watcher.get("isDisuse") or not watcher.get("rewardID"):
                continue
            identity["rewardId"] = watcher["rewardID"]
            reward = rewards.get(watcher["rewardID"])
            if reward is None:
                missing.append(dict(**identity, reason="missing-reward"))
                continue
            totals["watcherRewards"] += 1
            totals["asterCustomWatcherRewards" if activity["activityId"] == 2001 else "genericWatcherRewards"] += 1
            lines = [(r.get("id", r.get("itemId", 0)), r.get("count", r.get("itemCount", 0)))
                     for r in reward.get("rewardItemList", [])]
            # RewardData.onLoad removes unused placeholder slots by ID.
            lines = [(item, count) for item, count in lines if item > 0]
            scalar = {k: v for k, v in reward.items() if k not in ("rewardId", "rewardItemList") and v}
            if scalar:
                totals["watchersWithAdditionalFields"] += 1
            for item_id, count in lines:
                item = items.get(item_id)
                totals["itemRows"] += 1
                if count <= 0 or item is None:
                    issues.append(dict(**identity, itemId=item_id, quantity=count, reason="invalid-item-or-quantity"))
                    continue
                item_type = item.get("itemType", "ITEM_NONE")
                type_rows[item_type] += 1
                if item.get("useOnGain"):
                    special.add(item_id)
                if item_type == "ITEM_VIRTUAL" and item_id not in (201, 202) and owners.get(item_id) != activity["activityId"]:
                    issues.append(dict(**identity, itemId=item_id, quantity=count, reason="unbound-virtual-reward"))
    return dict(schema=1, totals=dict(totals), itemTypes=dict(sorted(type_rows.items())),
                uniqueUseOnGainItemIds=sorted(special), unresolved=issues, missingDefinitions=missing,
                inputSha256=dict(sorted(inputs.items())), originalQuantitiesUnchanged=True,
                clientGameplayVerified=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=Path, default=ROOT / "resources")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = audit(args.resources)
    output = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        assert args.output.read_text("utf-8-sig") == output, "Activity watcher audit is stale"
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
    print(json.dumps(dict(**result["totals"], unresolved=len(result["unresolved"]),
                          missingDefinitions=len(result["missingDefinitions"]))))


if __name__ == "__main__":
    main()
