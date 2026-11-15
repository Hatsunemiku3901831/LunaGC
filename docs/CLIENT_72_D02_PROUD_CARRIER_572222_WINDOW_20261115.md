# Proud carrier 572222 单窗来源与准确停止点

`R3-PROUD-CARRIER-572222-WINDOW` 已完成限定来源验收：主线接受一次固定输入采集及自身账本，未重复采集或新增测试。目标 `69135.m572222 / 14c510820` 为flags `81h`、instance、一个 `List<59500>` 显参；formal return、隐藏参数及native ABI未知。

身份、五stock、当前AOT实读、唯一pdata、正文前账本与strict连续覆盖通过。完整 `[14c510820,14c510aee)` 位于同一file-backed可执行段，一次读取718B，SHA-256 `0a7a555ad683d453412df1e4b076fc99f1f5289a6861654ab81cd59a8ec21914`；194条指令索引含23 call、33 branch、6 RIP。AOT 16B + pdata 12B + body 718B = 746B；header 4616B、exception目录14344080B及文件hash另账。原调用exit 0、stderr空。

私有根 `local/migration72/d02-resume-20261115/proud-carrier-572222-window/` 保存 `来源摘要.zh.md`、`manifest.private.json` 和 `capture/`；manifest SHA-256 `e4356c56c3d52a1eac9194f44ef12bd9592d22899d3e680d69259074acc2f1f1`。

主线只审首次非空row入口路径至 `14c5108da e8e12d02f4 → 1405336c0` 的准确A冻结CALL，停止该返回、构造及其后继；不解释post-CALL或地址更早的回边中依赖返回的store。索引另有 `14c510947 → 1488a5750`，该目标已知包含 `1488a5772` 冻结CALL，不读取其冻结tail产物或以IFix替代。这些是既有拒绝的准确依赖，非新安全拒绝。

`R3-PROUD-CARRIER-572222-CONSUMER` 因准确依赖阻塞；完整718B raw不证明整个body的数组消费为阴性，也不证明实际M/row/current/epoch、业务或resourceReady。该候选只是原572215/572222/572227三项中的一项，不排除全部carrier来源；A/B、preview及tail冻结保持，D-02 / F-05 / D02-R6 仍0/6。
