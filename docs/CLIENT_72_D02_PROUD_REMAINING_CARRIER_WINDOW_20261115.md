# Proud 剩余 carrier 单窗来源

`R3-PROUD-REMAINING-CARRIER-WINDOW` 已完成限定来源验收：主线接受一次固定输入采集及自身账本，未重复采集或新增测试。目标 `69135.m572227 / 14c5115e0` 为flags `81h`、instance，显式参数 `List<59500>` 与 bool；formal return、隐藏参数及native ABI未知。另两个候选 `m572215/572222` 未关闭。

17项固定pin、五stock、身份与当前AOT实读、唯一pdata、body前账本及strict连续覆盖通过；完整方法范围 `[14c5115e0,14c511cb1)` 位于同一file-backed可执行段内，一次读取1745B，SHA-256 `34a5cb4c8a445cdd8147f66846394383964c006047e618c07db782f576301838`。383条指令索引含44 call、70 branch、19 RIP。AOT 16B + pdata 12B + body 1745B = 1773B；header 4616B、exception目录14344080B与文件hash另账。

私有根 `local/migration72/d02-resume-20261115/proud-remaining-carrier-window/` 保存 `来源摘要.zh.md`、`manifest.private.json` 和 `capture/`；manifest SHA-256 `e3f38f76cc3336ab1a0e7e3bf08f09eb1a157eafe5c6efe484fc9fbf0df44b32`。已列冻结目标直接命中0仅为有限索引，不证明间接或传递独立；未分析冻结tail、callee、返回或IFix，未执行目标。

`R3-PROUD-REMAINING-CARRIER-SEMANTICS` 正分析可见whole carrier传递及条件，不展开数值codec。本来源不预称数组消费、bool业务、actual M、row/current/epoch或resourceReady；A/B、preview及准确tail依赖冻结保持，D-02 / F-05 / D02-R6 仍0/6。
