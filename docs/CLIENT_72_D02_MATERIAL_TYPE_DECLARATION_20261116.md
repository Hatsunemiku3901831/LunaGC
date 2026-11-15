# Material 局部 parser 与 type62334 声明

`R3-ACTIVE-MATERIAL-MINIMUM-SEMANTICS` 和 `R3-ACTIVE-MATERIAL-TYPE62334-DECLARATION` 已完成限定验收。**撤回把 `23400.field110880/+78` 接为 MaterialType 的候选依据**：固定 target 声明符号与该业务职责存在冲突，不能据此接线。实际 MaterialType 仍缺准确字段及具名消费来源；本结论不泛封整个 Material 或其他独立工作。

已验 Material parser 中，首个条件分支前有 `14a88ca31 → 150b6a9b0`，CALL 时 RCX/RDX 保留入口值，仅证明局部寄存器转交。对 `+78`，未置符号 flag 时 `14a88cbaf` store 局部 EDX=0；置位时读取当前 cursor 的 4B、XOR `25a08999`、cursor 加 4，再于 `14a88cc15` store。完整 listing 中未见该 receiver 的旧 `+78` 直接 load；不扩展到 callee、别名或实际行初态。MaterialType 的具名职责尚未建立。

固定 metadata 对目标 `62334` 和控制 `23400.field110880` 直接投影一次：目标记录自身 parent 明确为 `System.Enum` / typeDefinition 204，未额外 lookup parent。目标完整五字段、无重复或截断，method 0：四个 literal `FOOD_QUALITY_NONE`、`FOOD_QUALITY_STRANGE`、`FOOD_QUALITY_ORDINARY`、`FOOD_QUALITY_DELICIOUS`，以及 instance `value__`。常量数值 unknown，不能按声明顺序赋值；这些是 target-symbols-only，不自动等于已验 Java FoodQuality 数值或实际行值。

结果的 `targetSymbols` 数组只筛 `MATERIAL_` 前缀而为空，**不意味着原声明无具名锚或全混淆**；完整 `FOOD_QUALITY_*` 符号保留在 `fields` 和 summary。控制项 `field110880` 为 instance `+78`，`typeIndex517402 → valuetype/typeDefinition62334`，与既有 v2 的 fields/7 全项一致。最初最小语义 spec 曾误将 517402 写为 field index，后在派工前修正为上述 field → typeIndex → definition 关系，原记录保留。

私有 `local/migration72/d02-resume-20261116/active-item-material-minimum-semantics/` 的最终 `spec.private.json` SHA-256 `80cee7eb4ad7e165d972cb9a7fc549448f285cb30356ef91380eaf256ac3387f`、`scope.zh.md` SHA-256 `ad56534627c38792490624cf55b623c6005e938268989345251c88b198597c83`。`local/migration72/d02-resume-20261116/active-item-material-type62334-declaration/result.private.json` 为 7789B，SHA-256 `48a489f3ce644e4b274b34db9b08a13e6cec59ee574482a000d654eeb577bed4`；同目录 request、driver、summary 及结果保留固定范围和应用 IO 账。

metadata 一次完整应用层读取/hash 117201003B，DataOnly 解包禁止 globals；仅直接访问两个指定 record，未扫描全部 89748、继承树、consumer 或新类型。预检、重复 helper/spec 读取另账；应用层次数不冒作底层 loader 或 OS 调用计量。原 Material KeyError 与 4096B cap 失败原件不改。

共同父 `id/itemType` 最小语义由 Sol max 统一审定中，不重复取源；MaterialType 暂无新执行包，合法输入、独立 oracle 与新值仍缺。本批无 PE、目标/native、row/getter、JVM、DB、客户端或测试；不批准 Java alias、资源 ready 或恢复。A/B、preview、准确 tail 及 A opaque 纠错保持，D-02 / F-05 / D02-R6 仍 0/6。
