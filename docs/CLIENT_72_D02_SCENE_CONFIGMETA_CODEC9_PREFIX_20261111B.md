# F-03d3s2 / CONFIGMETA-CODEC9-PREFIX-ONLY

两个固定压缩块的原前缀变换已通过两次 fresh 和独立原件审核。输出仍是 **transformed compressed bytes**；本卡没有解压 payload、运行 codec9/Oodle 或解析 serialized 对象。

| 块 | 原始压缩字节 | 变换后 SHA-256 | 前128 B变化字节 |
| --- | ---: | --- | ---: |
| 0 | 212058 | `ba5b8ff2a824679258176fb16accf438f03ed1e0c85b9228a14a00cdc1269887` | 128 |
| 1 | 72360 | `8a739cdf59ce2102df450a40a518bbe4f3558c4a2a61124d036e58bfad681515` | 128 |

## 固定输入与执行范围

请求 `scene-header-conflict/codec9-prefix-only-request.private.json` 为10031 B / SHA `42364f5b71128ae356940f6165d5bcb3b77718e1955266dcde3b3ffb9b5c5abf`。每次核请求中固定 blob、真实解码 header、主线第三审核、历史 snapshot、已有报告及六份工具源码的完整大小/SHA，并重新解析 GAME_PATH、核五个 stock。使用原 `parse_header` 复验完整284563 B、tag9、两个精确压缩区间与 content 长度；不将声明的解压长度分配为解码输出。

独有 driver 用原 BlobProbe 和 PayloadProbe 的原 `setup`/`transform` 方法，未调用 PayloadProbe 初始化、codec 注册或构造。仅增加已验 `1415aa8c0..1415aa911` 的81 B原前缀入口，核原 pdata extent 和 SHA `93ee6859b3c44f6dc8a7846ed5b4552f2edc3e99d49450b73e485566931ac18f`。保存 `pre-execute.private.json` 后才调用两个固定切片。archive/header 是依据实际固定 header 和原 IV 建立的离线夹具，table 来自同 profile 的既有 snapshot，不能代表自然调用。

两块均返回实际 AL=1、无 modeledCalls/memoryModels、正常 END。输入两侧16 B guard 完好，原输入的128 B以后逐字节不变；header/archive、原 IV 和256 B table 前后相同。额外写入 hook 在执行时监视 guard、尾部、header 和 table：每块观察到212次前缀写事件、零受保护区域写事件。第一块28537步、第二块28719步；每块记录482条实际直接调用边和站点，本次没有观察到间接调用。严格指令白名单阻止未允许的目标，轨迹没有 codec/state 入口。guard 和写入结论属于此离线夹具及已记录执行范围。

## 原件、审核与复现

两次最终运行位于 `local/migration72/d02-resume-20261111b/scene-configmeta-codec9-prefix/audited1/` 和 `audited2/`，四份原件逐字节一致。完整报告142802 B / SHA `394c835c2c9e249800797a8f64212a26621b8917be80b3385680b2e04f787325`，每块实际变换后前64 B仅保存在私有报告。独立审计直接重算两块原始区间、完整输出和尾部、写入/guard记录及逐条调用目标；两次审计均1438 B / SHA `cc844d15df04bd6b80d016e8f28ff142109f8e56f08d3e46f4bae5fb38a2bf9c`。

[独有工具](../tools/migration72/scene_mplevel_actual_codec9_prefix.py) SHA `008c1b388cea1c33a9b51828e6d6a475b2d7a09e79ef108e4ffe04711edda36e`；[测试](../tools/migration72/test_scene_mplevel_actual_codec9_prefix.py) 5项通过，覆盖固定 blob/header/source/table 漂移、错误 index、调用指令分类和受保护写入边界。工具只对实际两块调用 prefix，没有额外 native codec 错误探针。

```powershell
python -B -m unittest tools.migration72.test_scene_mplevel_actual_codec9_prefix -v
python -B tools/migration72/scene_mplevel_actual_codec9_prefix.py --output <新的私有运行名>
python -B local/migration72/d02-resume-20261111b/scene-configmeta-codec9-prefix/audit-final.private.py --output local/migration72/d02-resume-20261111b/scene-configmeta-codec9-prefix/audit-main-third.private.json
```

主线已在 `main-third/` 重做并逐字节核对四份原件与 audited1，5项门禁及独立审计通过；审计脚本本身只核 audited1/audited2。开发过程先完成两块初读，然后补充逐条 direct/indirect 调用边和受保护写入 hook，最终两份均按相同工具重跑；早期原件保留，不代最终验收。

没有执行完整 block reader、codec 或 state、外部解码器、客户端、JVM、DB、端口和部署。自然 loader、serialized entry→对象、MPLevel 内容、Scene 和 D-02 均未验；后续需要的解码证据与范围需另定。
