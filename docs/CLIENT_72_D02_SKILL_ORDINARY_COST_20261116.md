# 旧号能量 Skill 普通 cost 字段

`R3-OLD121-SKILL-ORDINARY-COST-HOST-PILOT-20261116`已完成限定验收：固定Skill10019的同源span内，原bit53 present、effective bit4 absent、尾bit11 absent；准确offset1590的一次4B读取，经原parser的`LE32 XOR 8a4ce70f`得到Float32 bits`42a00000`，实际`costElemVal=80`。逐字节XOR与UInt32、Float位及逆wire相同；数值有限、非负且能精确表示为Java int，没有截断。

独立原消费者将同一energy Skill资源+98解为Float，与当前元素能量作`current >= cost`比较。该字段职责和文件值分别有来源；候选Asta的80仅事后旁证，不作目标oracle。实际skillobject+3a条件、自然availability及maximum-energy采用仍未证明。

固定规格`debug/d02-skill-energy-search-20261116/local-analysis/ordinary-cost-host-pilot-spec.private.json`为22670B，SHA-256 `7a4a793c8b9eba546efbfad3d897be6abb50a1374c9ad8cee1e02c9383bc3bd9`；13来源pin及raw均核一致。raw141702B一次磁盘读取/hash，113B选中span和8B已有mask仅同buffer hash；新语义准确4B。结果6755B，SHA-256 `4b5650c2dcb2a22d8db6a5e44f2492edd0a1790d8e2772d4a6087f0b65327492`，manifest SHA-256 `07784bbf4d07b9eeee4ccf7d62268c6f8118b8a786ed99a278f07fb1269b2528`，合7170B。原件在`debug/d02-skill-energy-search-20261116/ordinary-cost-host-pilot/`。

入口[client72_skill_ordinary_cost.py](../tools/client72_skill_ordinary_cost.py)，命令`python -X utf8 -B tools/client72_skill_ordinary_cost.py`；已有输出目录不覆盖。`id`复用已验tableKey绑定，`idRawDecoded=false`、`fragmentOnly=true`、`resourceReady=false`。本项仅离线工具与文档，无PE/native/getter/constructor/reader/TLS/JVM/DB/客户端或部署；不代121字段、特殊池、完整Skill/Player或D-02六项。

## 121 行同源批量

`R3-OLD121-SKILL-ORDINARY-COST-HOST-BATCH-20261116`已完成：121个不同Depot energy Skill中，原bit53 present119个，源确证absent两行为11065/11145。10019复用pilot，其余118个present准确读取472B；absent按原`PXOR XMM1 → 同Float setter → Skill+98`得到逻辑Float32 +0，不以JSON缺席或Java默认补值。全部present有限非负且精确为Java int，独立byte-index、逐字节XOR、Float位与逆wire及同row终点一致。

实际cost分布为0×2、40×11、50×4、60×55、70×20、80×27、90×2。新结果合并369片段：121个energy含`{id,costElemType,costElemVal}`，另248个维持原两键。原effective bit4 absent119行证明分类逻辑0；两特殊行仅标分类token present，其值未由本批解码。两特殊行的普通cost字段0不证明特殊池为0。

批量规格`debug/d02-skill-energy-search-20261116/local-analysis/ordinary-cost-host-batch-spec.private.json`93527B，SHA-256 `de081c668c7e9517c53bbe09347d3a5494a4ee9b0efdd78a71526759154be632`。raw141702B一次磁盘/hash；14319B选中span及968B已有mask仅同buffer hash，0新mask语义。结果241982B，SHA-256 `f3a82460f93779bbc226ef8fe877c62a7e3586d0b6a0644d4ecf334f3833c0a8`，manifest SHA-256 `815ae95e3184ba2b767a52fbb75c5d64fe3ffc2576a03e82eeece6b480bd8dbb`，合242430B低于256KiB。原件`debug/d02-skill-energy-search-20261116/ordinary-cost-host-batch/`；入口[client72_skill_ordinary_cost_batch.py](../tools/client72_skill_ordinary_cost_batch.py)，命令`python -X utf8 -B tools/client72_skill_ordinary_cost_batch.py`，不覆盖已有目录。

本批关闭121行`costElemVal`字段缺口；特殊START/MAX cap、实际maximum采用、完整Skill/Player及R3仍另验。尚未用本批结果执行Java；不是运行资源部署，六项仍0/6。
