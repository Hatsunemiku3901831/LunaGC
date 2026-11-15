# Proud 已绑定数组尾跳目标单窗来源

`R3-PROUD-BOUND-ARRAY-TAIL-WINDOW` 已完成限定来源验收：主线接受一次完整采集及自身账本，未重复采集或新增测试。ordinary metadata 身份 `33507.m281259 / 1488a5750` 的零显参门通过；参数型签名缓存没有该条目，formal return、隐藏参数及native ABI未知，未降低来源门。

唯一pdata与同一file-backed执行段门通过，完整 `[1488a5750,1488a590f)` 为447B，SHA-256 `ad638036765f36f23b51dea87d249fb2b4139f24839ac48d1f337a6e16876d93`；strict连续解码121条，索引14 call、22 branch、6 RIP。AOT 16B + pdata 12B + body 447B = 475B；header 4616B、exception目录14344080B和文件hash另账。私有根 `local/migration72/d02-resume-20261115/proud-bound-array-tail-window/` 保存 `来源摘要.zh.md`、`manifest.private.json` 与 `capture/`；22件manifest SHA-256 `869174de6ef4453ea40c6769a035ef6b46cdda3a1f3fb25c78cf5645b3066582`，ASM SHA-256 `3ac7814313a6934bd97f22b2753f95832d59c15ed097f4e60e4dc125c6999dbe`。

主线有界语义审阅只到 `1488a5772 e849dfc8f7 → 1405336c0` 的准确A冻结CALL：此前34B未读 `T+20`，含CALL共39B/11条。停止该调用返回、构造及后继语义；不使用后段解释数组消费，不以IFix替代。完整raw已存档只证明来源，不升级后继语义。这是已存在的拒绝依赖，不是新的安全拒绝。

`R3-PROUD-BOUND-ARRAY-TAIL-CONSUMER` 因此阻塞；[ctor pre-tail条件引用复制](CLIENT_72_D02_PROUD_ARRAY_CARRIER_CTOR_BINDING_20261115.md)仍有效，但不证明完整构造或最终对象状态。实际M、数组、业务桥、row/current/epoch及resourceReady未知。剩余69135普通候选的独立来源规划另项进行中，不泛封Proud；A/B、preview冻结继续有效，D-02 / F-05 / D02-R6 仍0/6。
