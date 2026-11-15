# 旧号能量 Skill 的特殊 START 需求

`R3-OLD121-SKILL-SPECIAL-START-HOST-PILOT-20261116`已完成限定验收。固定11065和11145两个同源span，经已验原mask、P1..P8定宽及原bit30 present定位到offset52408和58229；各读取4B，用原parser的`(LE32 + 70326507) mod 2^32`重新解释为Float32。实际START分别为100.0和50.0，保留Float位；未截断、四舍五入或按候选强制数值。

独立原消费者将同energy Skill资源+74的阈值与当前特殊能量比较；FightProp分派分别证明CUR_SPECIAL_ENERGY、START_SPECIAL_ENERGY及MAX_SPECIAL_ENERGY的不同职责。当前Java的`specialEnergyMax`对应START需求；`specialEnergyMin`对应MAX cap，本次没有填写。Asta100/50只是分列旁证，不能从其推上限200/100。

14来源pin、同row/key/Depot引用、source/mask hash、byte-index位与UInt64位一致；P1..P8位置和首String length前边界成立。逐字节ADD carry与UInt32模加、Float位重编码及逆wire全部一致。仅新语义8B；raw141702B一次磁盘/hash，253B选中span和16B已有mask仅同buffer hash。无新增分类token或其他字段语义读取。

规格`debug/d02-skill-energy-search-20261116/local-analysis/special-start-host-pilot-spec.private.json`27354B，SHA-256 `c67b46bc5b31ab3eac256ad24280a3840ac8c0ac2e172b1f7ff7daa92e0b3f4d`。结果9854B，SHA-256 `b3b8921b81c709971a87f915ce23d8e6947e9159414cc8e63ba3746466ca00df`；manifest SHA-256 `fe7191efb01818fcf55e73589e8d24d0ef752a2ce6d74a9e70d7408e16fe83d3`，合10269B。原件`debug/d02-skill-energy-search-20261116/special-start-host-pilot/`；入口[client72_skill_special_start.py](../tools/client72_skill_special_start.py)，命令`python -X utf8 -B tools/client72_skill_special_start.py`，已有输出目录不覆盖。

只闭合这两个START字段，其他119行的该字段分支另核；完整特殊池、MAX cap、实际availability/maximum采用、完整Skill/Player仍未闭。`fragmentOnly=true`、`idRawDecoded=false`、`resourceReady=false`，无PE/native/getter/constructor/reader/TLS/JVM/DB/客户端或部署，六项仍0/6。

## 121 行零新 raw 合片

`R3-OLD121-SKILL-SPECIAL-START-HOST-BATCH-20261116`已完成。固定121 energy集合中，原bit30仅11065/11145 present，直接复用两个pilot结果；其他119行均absent。原两条路径分别为`5713c PXOR → 57140/57146/5714c`和`57380 PXOR → 57384/5738a`，共同到`573cf`，经原Float setter及`573eb` store+74明确提供逻辑Float32 +0。不是物理wrapper清零、JSON补值或候选缺席推断。

8个既有source输入和spec核hash、121同key/source/Depot集合、逐行byte-index bit30及双pilot Float位全部一致。0新raw/PE/metadata读取，0新mask或payload语义；369合片的原字段保留，仅121 energy增加`specialEnergyMax`及presence/Floatbits证据，形成121四键与248原两键。START实际为0×119、50×1、100×1；`specialEnergyMin`始终不输出。

规格`debug/d02-skill-energy-search-20261116/local-analysis/special-start-host-batch-spec.private.json`72466B，SHA-256 `c7f530ccee6df14ed0fbb6629b1751bc630fb3f3ec3d5b1b38258b8bf5591a06`。结果286804B，SHA-256 `9d4b69d00a438084cd9a10b6b4eacda5618eaeebdab735a3bfe38cd3c4e755cb`；manifest SHA-256 `f8ab88198989fd645e42aeca5efe045309450e311228f49ed364e6681cbbfd4a`，合287241B低于512KiB。原件`debug/d02-skill-energy-search-20261116/special-start-host-batch/`；入口[client72_skill_special_start_batch.py](../tools/client72_skill_special_start_batch.py)，命令`python -X utf8 -B tools/client72_skill_special_start_batch.py`，不覆盖已有输出。

本项关闭121 START字段的来源与值缺口。后继[真实ResourceLoader](CLIENT_72_D02_OLD121_SKILL_ENERGY_JAVA_20261116.md)现已消费已验cost/START并通过原121 Depot绑定；本批host本身未执行Java。MAX cap、实际maximum采用、完整Skill/Player及六项仍未完成。
