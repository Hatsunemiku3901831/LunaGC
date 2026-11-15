# Material 分类职责与 Reliquary rank/set 来源边界

`R3-ACTIVE-MATERIAL-TYPE-ROLE-SCOPE`、`R3-ACTIVE-MATERIAL-DIRECT-TYPE-DECLARATIONS`、`R3-ACTIVE-MATERIAL-TYPE34165-ROLE-REVIEW` 和 `R3-ACTIVE-RELIQUARY-RANK-SET-SOURCE-GAP` 已完成限定审阅或声明投影。其结果不提供实际 row、字段数值或完整恢复许可。

Material 原 `+78 → type62334` 的 `FOOD_QUALITY_*` 符号异证继续保留，不能接为 MaterialType。另对 `23400` 的六条直接 valuetype 声明做固定查询：六个目标自身 parent 均为 `System.Enum`，合计 145 字段（139 literal + 6 `value__`），该轮literal数值未读；本轮其中34165的94项已静态出值，其余45项不据此赋值。其符号及直接边给职责线索，不按字段顺序赋数值。

对 `field110906/+c8 → typeIndex517407 → typeDefinition34165` 的准确直接边及其 `MATERIAL_*` 原符号，已闭合与必要分类的有限 MaterialType 概念职责；不证明客户端实际 consumer、Java 数值接线、row 或初始化。正常条件中 H 是父返回后的 8B 输入加 `dd682a0b` 的 U64，H bit 48 控制字段读取；置位读取 LE32 并计算 `U32(W+0764b2bb)`、cursor 加 4，再 store `+c8`；缺 flag 时局部 0 到同一 store，不证明 `MATERIAL_NONE=0`。上述是原审阅时点；本轮已复用固定PE/global默认表入口读出94 literal（含MATERIAL_NONE=0），见[静态常量结果](CLIENT_72_D02_MATERIAL_SPECIAL_ENUM_CONSTANTS_20261116.md)。不再将literal入口列阻塞；实际Material行、同row/key/span/cursor及consumer另项。

Reliquary 的 rankLevel/setId 在既有来源中仍无具名职责绑定：派生类 9 个单值候选及父类 6 个未具名整数域均不能凭排列选名；交换两个候选的 rank/set 标签仍兼容现有观察。需允许的具名 target consumer/property，或同源实际输入与独立非零 oracle 排除该反例。没有成熟的 value/source 执行包，不泛封整个族。

私有来源均在 `local/migration72/d02-resume-20261116/`：`active-item-material-type-role-scope/spec.private.json` SHA-256 `9966ba7dbf52f34697462db738707cfb5855612ac13119352d0ad7f01945309f`；`active-item-material-type-candidate-declarations/result.private.json` SHA-256 `12fa7800c859e984908cdddb8c9ff75f17480da3177b36700dc35fd1e98dfb65`；`active-item-material-type34165-role-review/spec.private.json` SHA-256 `51131d552e8e586b8df2a466cc9b1e3a00ea225fdd497dbcfc20360a63c36b18`；`active-item-reliquary-rank-set-source-gap/spec.private.json` SHA-256 `3a331dcb398fa5c791773d9a07b1044acb22e28b8b7864c0628ca3e694f129b3`。各原件按其 manifest 或目录保留 scope/spec；声明 query 目录另有 request 和 driver。

仅限定职责、声明与缺口，不证明 target 数值/实际 consumer/初始化、Java 恢复或 ready。此前收尾暂停属于历史，用户现已解除暂停并继续必要独立项；A/B、preview、准确依赖及 A opaque 纠错保持，D-02 / F-05 / D02-R6 仍为 0/6。
