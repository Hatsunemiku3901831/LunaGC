# Proud 参数元素 consumer 单窗来源

`R3-PROUD-PARAM-ELEMENT-CONSUMER-WINDOW` 已完成限定来源验收：主线接受一次完整静态采集及自身账本；未重复采集或新增测试。声明为 `33507.m281263 / 1488a5af0`，flags `86h`、instance、7个显式参数；formal return、隐藏参数与 native ABI 仍未知。

固定输入、五 stock/profile、caller和来源 pin 通过；ordinary AOT 当前实读与唯一 pdata、同一文件支持的可执行段、正文前账本均通过。完整 `[1488a5af0,1488a5c70)` 为384B，SHA-256 `06def7c5f8b3a7aa84cea8d1e3ecbb0f427f4407498e8cedd4676d31092223ae`；strict解码连续覆盖98条，call 7、branch 12、RIP 3，列明冻结目标直接边0不证明间接无关。listing `capture/body.asm.private.json` 为15132B，SHA-256 `37f2e911c395bc7798ccc30c79a6572c4392abecd4cef369a07fbc68fbab5cee`。

证据读取 AOT 16B + pdata 12B + body 384B = 412B；header 4616B、exception目录14344080B及文件hash另账。调用exit 0、stderr空；未读取callee、global/RIP内容、table、seed或xdata，未访问目标进程或执行目标。来源与账本保存在 `local/migration72/d02-resume-20261115/proud-param-element-consumer-window/` 的 `source-only.zh.md`、`self-check.private.json` 与 `capture/`。

本体目前可见读取owner的 `+18` 集合；owner `+20` 只是原typed候选理由，不能据此声称本方法消费 `IList<54724>`。`R3-PROUD-PARAM-ELEMENT-CONSUMER-SEMANTICS` 进行中，值流及该列表与carrier数组的绑定尚未验；actual M、row/current/epoch及resourceReady均未知。A/B与preview冻结不变，D-02 / F-05 / D02-R6 客户端验收仍0/6。
