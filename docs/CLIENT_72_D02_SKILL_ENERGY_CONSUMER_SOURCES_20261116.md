# Skill 能量消费者准确来源

本页区分已取得的完整静态源与尚未闭合的资源字段。两个 source-only 项已通过固定输入、fresh AOT 和独立完整 pdata 门；没有执行目标代码或取得资源值，`costElemVal/specialEnergyMin/specialEnergyMax` 均不由此晋级。

| 任务 | 原方法和完整范围 | 实际来源结果 |
| --- | --- | --- |
| `R3-SKILL-SPECIAL-ENERGY-NAMED-GETTER-SOURCE-WINDOW-20261116` | `59617.MonoBattleBtn.m494903/get_IsSpecialEnergyCanUse`；`[14ced3cb0,14ced3d7b)`，203B | body SHA-256 `909001014181b4fc39cb0ef3f4f94ddd5276548e0150492859d3bede0fdb249f`；strict52条/18引用，直接冻结site/target为0 |
| `R3-SKILL-ENERGY-AVAILABILITY-PREDICATE-SOURCE-WINDOW-20261116` | `53763.m446868/LGLNKLKMCHF`；`[15345d020,15345d0e2)`，194B | body SHA-256 `4c1143b1203530e13696c4e2fa3761b53fefd2aecbfd04cd39d48d5f9d236231`；strict53条/18引用，直接冻结site/target为0 |

当前 GAME_PATH 由原便携 resolver 重新解析，五个 stock 文件实际完整 hash 均匹配固定7.2 profile；主 PE SHA-256 `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`。两项先分别验证完整 exception 目录 SHA-256 `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d` 的 exact-begin/containing/multiplicity/overlap，再各一次读取完整 body。目录14344080B、header两读4616B（distinct4096B）、five-stock hash550661784B另账；各项 source证据为231B/222B，不把 hash或目录读写成 body读取。应用预检、Python/Capstone/OS loader 的额外 IO未全部计量。

203B getter 的限定事实：常规分支在 button `+1b8` 非空时，从其 class `+288` 取未知虚目标并 tailjmp；否则对 button `+128` 对象作类型层级门，匹配后只将对象 `+48` byte 是否非零转成返回值。既存 metadata 把前者声明为 `59617.field295829/+1b8 → class80054`，后者为基类 `75427.field374549/+128 → class75428`；门内目标 class59619 的 `field295864/+48` 为 bool。该窗没有直接读取 Skill 资源；虚目标、初始化/IFix分支及对象实际状态均未展开，不能用 getter 名称给三字段命名。

194B predicate 则直接闭合了一条资源引用：同53763 receiver `+290` 为已验 Depot；`15345d041` 读取 Depot `+6c`，经原 UInt32 getter得到 energySkill ID；同 Skill 字典 `+16c70` 查得该 row，`15345d091` 比较 row `+90` DWORD 是否非零并返回。原完整消费者 `15345e260..15345e392` 的 `15345e2ad` 调此 predicate；非零结果后续走 `15345d2c0`，零结果走 `15345d0f0`。这是 source 上的引用和分流，尚无自然 receiver、实际行值或可用性执行验收。

`88602.field440595/+90` 声明为 enum61184；固定 metadata 中该类型有 `SPECIAL_ENERGY_NONE`、`SPECIAL_ENERGY_MAVUIKA`、`SPECIAL_ENERGY_SKIRK` 三个 literal名称。因此“特殊能量分流”有类型符号与实际 energy-row 消费两条来源支持；literal数值仍未知，不按声明顺序推0/1/2，也不将 `+90` 误当 `specialEnergyMin/Max`。字段桥及精确声明只读摘录为 `debug/d02-skill-energy-search-20261116/local-analysis/energy-source-metadata-review.private.json`，SHA-256 `62fa76e39d7ba2e470278da952a83670bee21846f822056f46de2b41c9ae9552`，其来源为已验 metadata cache SHA-256 `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`。

203B 原件在 `debug/d02-skill-energy-search-20261116/special-energy-named-getter-source/`，result SHA-256 `c20667e200fdd3534f55ce686f68c652396b5283159d632514f565d81a1eadf0`，manifest SHA-256 `02416d887ae72244e0bf6a43719b301a6b79514d9e8b96830bf7fa11e66038e9`。离线工具为 `tools/migration72/skill_special_energy_getter_source.py`。194B 原件在同根 `energy-availability-predicate-source/capture/result.private.json`，SHA-256 `9a281a181a74ec254dd32d97abc2c35b3ee6abaef9217ba8bbd0b26b8dca5cac`；任务根 `energy-availability-predicate-source/manifest.private.json` SHA-256 `7d342c7d93005a65cacfea13219c1370cd95f20013717688b29584093abcc6f2`。独立执行工具为忽略目录 `energy-availability-predicate-source/run_capture.private.py`，9295B/SHA-256 `b4b36584b8bfe92b70937d9ed811cdd2ebef8423ab5bfebfa7f6846faec920fd`，不作为新增跟踪工具提交，不修改203B工具或原件。

下一项 `R3-SKILL-ENERGY-RESOURCE-BRANCH-SOURCES-20261116` 只检查上述普通/特殊两准确分支的独立完整边界和 source。父项的字段值、三项英文职责、完整 Java/旧号恢复及 D-02 六项仍未闭；A/B、preview和准确拒绝依赖保持，未启服务、数据库或客户端。本页及离线工具无需重启部署。
