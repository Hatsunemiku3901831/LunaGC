"""Project the pinned D-02 birth Promote and AvatarCurve evidence to Java JSON."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct

REPORT_SHA256 = "546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1"
CURVES = ("GROW_CURVE_HP_S4", "GROW_CURVE_ATTACK_S4", "GROW_CURVE_HP_S5", "GROW_CURVE_ATTACK_S5")
PROMOTES = {
    "AvatarPromote": ("avatarPromoteId", 12, 4, ("FIGHT_PROP_BASE_HP", "FIGHT_PROP_BASE_DEFENSE", "FIGHT_PROP_BASE_ATTACK", "FIGHT_PROP_ATTACK_PERCENT")),
    "WeaponPromote": ("weaponPromoteId", 11101, 3, ("FIGHT_PROP_BASE_ATTACK", "FIGHT_PROP_CRITICAL", "FIGHT_PROP_CRITICAL_HURT", "FIGHT_PROP_CHARGE_EFFICIENCY", "FIGHT_PROP_ELEMENT_MASTERY")),
}


def project(report):
    projection = report["javaProjection"]
    promotes = projection["completeBirthPromoteInputRows"]
    if len(promotes) != 2 or {r["table"] for r in promotes} != set(PROMOTES):
        raise ValueError("expected exactly two approved birth Promote rows")
    result = {}
    for row in promotes:
        table = row["table"]
        id_field, group, slots, props = PROMOTES[table]
        f = row["javaFields"]
        if (not row["completeBirthInputRow"] or row["allDeclaredGameFieldsMapped"]
                or set(f) != {id_field, "promoteLevel", "addProps", "costItems"}
                or f[id_field] != group or f["promoteLevel"] != 0 or row["key"] != group << 8
                or f["costItems"] != [{"id": 0, "count": 0}] * slots
                or f["addProps"] != [{"propType": p, "value": 0.0} for p in props]):
            raise ValueError(f"invalid {table} birth input")
        result[table] = [f]
    rows = projection["completeAvatarCurveInputRows"]
    if len(rows) != 100:
        raise ValueError("expected 100 curve levels")
    curves = []
    for level, row in enumerate(rows, 1):
        f = row["javaFields"]
        if (row["table"] != "AvatarCurve" or not row["completeDeclaredJavaInputRow"]
                or row["key"] != level or set(f) != {"level", "curveInfos"} or f["level"] != level):
            raise ValueError(f"invalid curve level {level}")
        infos = f["curveInfos"]
        if len(infos) != 4 or tuple(x.get("type") for x in infos) != CURVES:
            raise ValueError(f"missing, duplicate or unordered curve at level {level}")
        for info in infos:
            value = info.get("value")
            if (set(info) != {"type", "arith", "value"} or info["arith"] != "ARITH_MULTI"
                    or type(value) not in (float, int) or not math.isfinite(value)
                    or struct.unpack("<f", struct.pack("<f", value))[0] != value):
                raise ValueError(f"invalid curve value or arithmetic at level {level}")
        curves.append(f)
    result["AvatarCurve"] = curves
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="new independent directory")
    args = parser.parse_args()
    raw = args.report.read_bytes()
    if hashlib.sha256(raw).hexdigest() != REPORT_SHA256:
        raise ValueError("report SHA-256 mismatch")
    rows = project(json.loads(raw))
    args.output.mkdir(parents=True, exist_ok=False)
    for table, data in rows.items():
        path = args.output / f"{table}ExcelConfigData.json"
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8", newline="\n")
        print(f"{path.name} {hashlib.sha256(path.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
