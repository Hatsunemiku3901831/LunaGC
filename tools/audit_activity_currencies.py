"""Audit activity currency ownership against declarations and original exchange prices."""
import argparse
import hashlib
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]


def audit(resources):
    inputs = {}

    def source(path, name):
        raw = path.read_bytes().replace(b"\r\n", b"\n")
        inputs[name] = hashlib.sha256(raw).hexdigest()
        return raw.decode("utf-8-sig")

    def rows(name):
        return json.loads(source(resources / "ExcelBinOutput" / name, name))

    activities = rows("NewActivityExcelConfigData.json")
    items = {row["id"]: row for row in rows("MaterialExcelConfigData.json")}
    shops = rows("ActivityShopOverallExcelConfigData.json")
    goods = rows("ShopGoodsExcelConfigData.json")
    history_path = ROOT / "src/main/resources/defaults/data/ActivityHistory.json"
    history = json.loads(source(history_path, "ActivityHistory.json"))
    schedule_owners = {row["scheduleId"]: row["activityId"] for row in history if row.get("scheduleId", 0) > 0}
    enum_path = ROOT / "src/main/java/emu/grasscutter/game/shop/ShopType.java"
    types = {name: int(value) for name, value in re.findall(r"(SHOP_TYPE_[A-Z0-9_]+)\((\d+)\)", source(enum_path, "ShopType.java"))}
    beyond_path = ROOT / "src/main/java/emu/grasscutter/game/shop/BeyondCurrency.java"
    global_ids = {101, 102, 105, 106, 107, 121, 201, 202, 203, 204, 207}
    global_ids.update(int(value) for value in re.findall(r"case (\d+) ->", source(beyond_path, "BeyondCurrency.java")))
    owners = {}
    evidence = {}

    def add(coin, owner, origin, price=None):
        assert coin > 0 and owner > 0 and coin not in global_ids | {109, 110}, (coin, owner)
        assert items[coin]["itemType"] == "ITEM_VIRTUAL", coin
        assert owners.get(coin, owner) == owner, (coin, owners.get(coin), owner)
        owners[coin] = owner
        row = evidence.setdefault(coin, dict(itemId=coin, activityId=owner, sources=[], originalPriceRows=[]))
        if origin not in row["sources"]:
            row["sources"].append(origin)
        if price is not None:
            row["originalPriceRows"].append(price)

    for activity in activities:
        for coin in activity.get("activityCoinIdList", []):
            add(coin, activity["activityId"], "activityCoinIdList")
    declared = set(owners)
    shop_owners = {types[row["shopType"]]: schedule_owners[row["scheduleId"]]
                   for row in shops if row["shopType"] in types and row["scheduleId"] in schedule_owners}
    for good in goods:
        owner = shop_owners.get(good["shopType"])
        if owner is None or owner == 2001:
            continue
        for cost in good.get("costItems", []):
            coin = cost["id"]
            if cost["count"] > 0 and coin not in global_ids and items.get(coin, {}).get("itemType") == "ITEM_VIRTUAL":
                add(coin, owner, "original-shop-cost", dict(goodsId=good["goodsId"], shopType=good["shopType"], count=cost["count"]))
    return dict(schema=1, currencyCount=len(owners), declaredCurrencyCount=len(declared),
                legacyPriceCurrencyCount=len(set(owners) - declared), separateReceiptCurrencies=[109, 110],
                inputSha256=inputs, currencies=[evidence[coin] for coin in sorted(owners)])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=pathlib.Path, default=ROOT / "resources")
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    result = audit(args.resources)
    output = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        assert args.output.read_text("utf-8-sig") == output, "Activity currency audit is stale"
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
    print(json.dumps({key: result[key] for key in ["currencyCount", "declaredCurrencyCount", "legacyPriceCurrencyCount"]}))


if __name__ == "__main__":
    main()
