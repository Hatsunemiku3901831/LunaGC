# Proud carrier 572215 单窗来源

`R3-PROUD-CARRIER-572215-WINDOW` 已完成限定来源验收：主线接受一次固定输入采集及自身账本，未重复采集或新增测试。目标 `69135.m572215 / 14c50f790` 为flags `81h`、instance、一个 `List<59500>` 显参；formal return、隐藏参数及native ABI未知。

固定pins、声明/signature、五stock、当前AOT实读、唯一pdata与正文前账本通过。完整 `[14c50f790,14c50f9d6)` 位于同一file-backed可执行段内，一次读取582B，SHA-256 `d8ea4a70ff41a1f45416bdc4c2b6be1a013eab278d521e1287e3a841e75bc7fa`；strict连续覆盖152条，索引19 call、31 branch、4 RIP。ASM 25344B SHA-256 `ac9693d44e1bb470628311240f359fb59293e2b6948969564daf2f87891506bc`。

AOT 16B + pdata 12B + body 582B = 610B；header 4616B、exception目录14344080B及文件hash另账。私有根 `local/migration72/d02-resume-20261115/proud-carrier-572215-window/` 保存 `来源摘要.zh.md`、21件 `manifest.private.json` 与 `capture/`；manifest SHA-256 `53aae490357290f8ca6413b72d628888e89cca1dee3c0812fd8531df7d9d26dd`。已列冻结目标直接索引0不证明间接或传递独立；未读取冻结tail、callee、global/RIP内容，未执行目标。

`R3-PROUD-CARRIER-572215-SEMANTICS` 正审字段、whole List和条件，不展开清理callee树；此来源不预称数组消费、业务或实际M/row/current/epoch/ready。572222仍未关闭；A/B、preview及准确tail冻结不变，D-02 / F-05 / D02-R6 仍0/6。
