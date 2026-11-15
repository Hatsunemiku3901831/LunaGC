# 旧号能量 Skill 普通 cost 字段

`R3-OLD121-SKILL-ORDINARY-COST-HOST-PILOT-20261116`已完成限定验收：固定Skill10019的同源span内，原bit53 present、effective bit4 absent、尾bit11 absent；准确offset1590的一次4B读取，经原parser的`LE32 XOR 8a4ce70f`得到Float32 bits`42a00000`，实际`costElemVal=80`。逐字节XOR与UInt32、Float位及逆wire相同；数值有限、非负且能精确表示为Java int，没有截断。

独立原消费者将同一energy Skill资源+98解为Float，与当前元素能量作`current >= cost`比较。该字段职责和文件值分别有来源；候选Asta的80仅事后旁证，不作目标oracle。实际skillobject+3a条件、自然availability及maximum-energy采用仍未证明。

固定规格`debug/d02-skill-energy-search-20261116/local-analysis/ordinary-cost-host-pilot-spec.private.json`为22670B，SHA-256 `7a4a793c8b9eba546efbfad3d897be6abb50a1374c9ad8cee1e02c9383bc3bd9`；13来源pin及raw均核一致。raw141702B一次磁盘读取/hash，113B选中span和8B已有mask仅同buffer hash；新语义准确4B。结果6755B，SHA-256 `4b5650c2dcb2a22d8db6a5e44f2492edd0a1790d8e2772d4a6087f0b65327492`，manifest SHA-256 `07784bbf4d07b9eeee4ccf7d62268c6f8118b8a786ed99a278f07fb1269b2528`，合7170B。原件在`debug/d02-skill-energy-search-20261116/ordinary-cost-host-pilot/`。

入口[client72_skill_ordinary_cost.py](../tools/client72_skill_ordinary_cost.py)，命令`python -X utf8 -B tools/client72_skill_ordinary_cost.py`；已有输出目录不覆盖。`id`复用已验tableKey绑定，`idRawDecoded=false`、`fragmentOnly=true`、`resourceReady=false`。本项仅离线工具与文档，无PE/native/getter/constructor/reader/TLS/JVM/DB/客户端或部署；不代121字段、特殊池、完整Skill/Player或D-02六项。
