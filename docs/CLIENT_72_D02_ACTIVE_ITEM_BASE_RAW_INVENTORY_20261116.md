# 活动物品父字段条件语义与 raw 来源盘点

`R3-ACTIVE-ITEM-BASE-MINIMUM-SEMANTICS` 和 `R3-ACTIVE-ITEM-RAW-SOURCE-INVENTORY` 已完成限定验收。父 parser 的两个字段有条件来源，但实际 row、合法输入与独立值 oracle 仍未闭；限定文件名盘点不能证明整个机器没有原件。

父入口读取 4B 为 F，G=`F XOR b1faea00`。`itemType` 的 `88968.field442110/+38`：F bit 0 置位时读当前 LE32 并计算 `U32(W+5b016aed)`、cursor 加 4，缺 flag 时局部 ESI 为 0 并跳至该字段 store。`id` 的 `field442106/+28`：G bit 14 置位时读 LE32 并计算 `U32(W+1758ec57)`、cursor 加 4；缺 flag 仍调用已验 UInt32 setter(0) 后覆盖，存储结果为 S(0)，不等同物理 DWORD 0。两个目标 offset 在完整父 listing 中无直接旧值 load；这些条件不证明任意 host zero、reader 有效范围或自然构造。此前前序可变输入仍影响 cursor，不能清 flag 假造输入。

`R3-ACTIVE-ITEM-RAW-SOURCE-INVENTORY` 在三个指定根检查 7666 项（3237 / 4403 / 26），精确 `Material.native-m_bytes*` / `Reliquary.native-m_bytes*` 均 0 命中；第二根有四处达到 depth 8 后停止，不能外推全盘缺失。7 个固定 manifest 入口匹配；额外两个 locator JSON 各三次实读是 one-off 补充 ledger，不是原 driver 自动重现。限定范围中未建立两族 raw 身份、keyspan 和入口 cursor，不造 span/值；缺口为可追溯的同版本输入及其 source/keyspan/cursor 绑定。

固定私有 `local/migration72/d02-resume-20261116/active-item-base-minimum-semantics/spec.private.json` SHA-256 `41d40b400b4b6d67f7aa118caba54601bb4283e7c412198bdb531460a0086b7e`；`local/migration72/d02-resume-20261116/active-item-raw-source-inventory/result.private.json` SHA-256 `94ec53645a4f1940fbb31a8882a74a027a5abd361f158591c79316ca0f5fd9fc`，同目录 request、`inventory.private.py` 和 summary 留范围及原始账。补充读取不改原 driver 或旧报告。

无新资源值、原 parser 执行或恢复验收。按用户指示收尾后暂停，不派新业务后继；A/B、preview 和准确依赖冻结不变，D-02 / F-05 / D02-R6 仍为 0/6。
