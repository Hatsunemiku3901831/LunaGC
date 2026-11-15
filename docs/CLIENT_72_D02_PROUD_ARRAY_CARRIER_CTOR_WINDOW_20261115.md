# Proud 数组 carrier 构造方法单窗来源

`R3-PROUD-ARRAY-CARRIER-CTOR-WINDOW` 已完成限定来源验收：主线接受一次固定输入采集及自身账本，未重复采集或新增测试。声明为 `33507.m281253 .ctor / 1488a5960`，flags `1886h`、instance、两个显式参数60298和59500；formal return、隐藏参数与native ABI未知。

固定身份与五stock/source pins、当前AOT实读、唯一pdata、body前账本及strict连续覆盖通过。该方法完整范围 `[1488a5960,1488a59dd)` 位于同一 file-backed 可执行段内，125B正文 SHA-256 `c2695934342a2f257c4cd8a56fee26f4d107d26c0d6f997366ed7c38654075b3`，解码38条；调用exit 0。证据 AOT 16B + pdata 12B + body 125B = 153B；header 4616B、exception目录14344080B与文件hash另账。原件位于 `local/migration72/d02-resume-20261115/proud-array-carrier-ctor-window/`，含 `来源摘要.zh.md`、`manifest.private.json` 和 `capture/`。

已列冻结目标的直接索引命中0不证明传递独立：`1488a597d` 的间接 `call [rax+110]` 目标未知，`1488a59d2` tail至 `1488a5750` 的合同未知。未取callee或global内容、未执行ctor或创建M。

`R3-PROUD-ARRAY-CARRIER-CTOR-BINDING` 正在审阅此窗中输入到 `+20` 引用的条件关系，不能以source通过预称绑定或完整构造。实际M、三字段业务、row/current/epoch及resourceReady未知；A/B、preview冻结不变，D-02 / F-05 / D02-R6 仍0/6。
