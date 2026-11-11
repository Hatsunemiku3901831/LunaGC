# F-03d3s2 / CONFIGMETA-ACTUAL-LOCATOR

本卡在获准 segment 的 **header 长度门禁停止**，没有解码 payload 或定位 serialized 对象。两次新鲜离线执行得到相同原件：header 指示的 logicalBlobBytes 为 **284563**，大于指定段的 **212203**；codec 原值同时为 **9**，不满足本包仅允许 tag3 的条件。已有索引值 A/B 不能据此认定为完整 blob 边界，实际关系待进一步研究。

## 固定来源到 segment

执行依据为私有规格 `scene/configmeta-locator-spec.zh.md`，10929 B / SHA-256 `908a7c9ab8de67853b95c1b35fb37cdb859212b37eb63213ccbd508ef1811881`。每次先核规格、所列来源、九个工具、profile 与五个 stock 的完整大小/SHA；冻结 locator 报告中的八个工具 SHA 也逐项匹配，第九个 provider 单独按规格固定。snapshot 为历史已导出的同 profile 输入，不代表此次自然调用。

`145b51ee0` 来源 literal 为 `Data/Common/MetaConfig`；使用固定 index 类型映射 `.ScriptableObject`，reference 计算得到完整 U64 `69d7a7642027d5f7`。两个固定原 index 的 asset 区间按原 count/end 扫描后只有一个匹配：31049740 的 `[19917,19931)`，14 B SHA `355f82df27c40f1988c634e0e58343fc61a4b6b729dc7c30252c3f17f8b7ecf1`，唯一变体 `(0,33095)`。reference packing 的上半为 `642027d5`；此路径与打包是独立计算和静态索引关系，不证明自然 LoadConfig 或 selector 执行。

唯一定位行原六元组为 `(2455671,33095,308700,212203,0,6)`。核对 `pkg_version` 第 341 行及完整 block 的大小、SHA、MD5 后，保存固定 `[308700,520903)` segment，212203 B / SHA `2033c3659731308aa208d4e3ac9ee52e0b9df1396a5d0be32a3b5f5971859bb0`。A/B 的含义未超出上述来源证明。

## Header 与准确停止

原前12 B为 `426c62037500000005000000`，header 长度117。使用冻结 Snapshot 中 `B+5eed350` 指向的256 B表（SHA `789e5887f53e09404fcf8b4bf38b36565e6dc05a532460ca37ebe17f1f612297`）和已审离线 BlobProbe，按原签名检查、准备与变换调用处理准确 header；canary、IV 及无模型调用门禁通过。解码后117 B SHA `207d6cdc9f26656a1a9ff05ad4b2087102fd826697045f1f40fffa38d4a5a741`。六个原 scalar getter 给出：

| Getter | 值 |
| --- | ---: |
| logicalBlobBytes | 284563 |
| dataOffset | 145 |
| compressionTag | 9 |
| blockBytes | 262144 |
| blockCount | 2 |
| contentCount | 1 |

独立原 header 结构校验以 `Blob or tail length is outside the actual file` 终止；原 header 的 tail 字段是72852。因为长度不相容，尚未通过完整 header 区间与 content 验证；表中 scalar 是已审核 getter 结果，不能升级成已经验证的完整 blob 内容表。codec 9 另不满足已批 tag3 门禁。未用 tag3/LZ4 冒充 codec9，也未从 block 提取所指定 segment 以外的额外字节。

因此无 payload、serialized member、entry→对象或 MonoScript PPtr 结果；不解析182字段，也不把运行时 `+50` 当 serialized offset。下一步需由 Scene 语义负责人审查 A/B 与原 header 的实际关系，以及 codec9 所需独立证据；任何额外范围或 codec 执行均不由本卡自动开启。

## 验证与复现

两次相同最终工具 fresh 位于 `local/migration72/d02-resume-20261111b/scene-configmeta-locator/final1/` 和 `final2/`，五份原件逐字节相同。`header-raw.private.json` 为3474 B / SHA `6eed04b362954ac93b0fd989cbfb1c0e47737eb3fee9df418947084330084196`；`failure.private.json` 为6949 B / SHA `f19ebc044f7a5c5bf97de4a09683569fec2849670ae0e669bfa10ccdc6653cb5`。两个独立原件审计各1234 B / SHA `525d9e4a775f1e11bcf9d75f5520c4fae90d46ff35d9f0efce4689566f4e2aac`，核双原件、原字段、getter 与准确失败；审计本身不重新执行 PE。

独立工具 [scene_mplevel_actual_locator.py](../tools/migration72/scene_mplevel_actual_locator.py) SHA `2729e0322fb77687270095ce1b3dc4d69f374efc2f85a0af122a1f458766fc70`；[测试](../tools/migration72/test_scene_mplevel_actual_locator.py) 共7项通过，覆盖来源、literal/后缀、U64、变体与定位歧义、长度/codec/预算拒绝及实际 header 的失败。member、entry、PPtr 未到达也未实现，故没有把相应假测试算作完成。

```powershell
python -B -m unittest tools.migration72.test_scene_mplevel_actual_locator -v
python -B tools/migration72/scene_mplevel_actual_locator.py --output <新的私有运行名>
python -B local/migration72/d02-resume-20261111b/scene-configmeta-locator/audit.private.py --output local/migration72/d02-resume-20261111b/scene-configmeta-locator/audit-main-third.private.json
```

提取命令按预期以非零退出并保存 `failure.private.json`；主线第三已按原门禁重现，五份新原件与 `final1/` 相同，7项测试与独立审计通过。审计命令本身只检查 final1/final2。开发首轮误抄 index SHA，source gate 立即拒绝；核原规格后纠正，保留 `header-pilot/` 失败。第二次 pilot 已发现长度门禁，但当时未保存变换后原 header，因此补保存并重新复核；原失败保留。没有遇到安全审查拒绝。

本卡未启动目标客户端或 live 进程，未操作 JVM、数据库、端口、共享构建或部署；离线 Probe 执行的是限定 header 原指令，不等于自然资源读取。资源 ready、实际 MPLevel 分组及 D-02 均未验。
