# 入场必要资源 parser 限定来源规划

`R3-OLD121-SKILL-ENTRY-TO-PROJECTION-SCOPE` 与 `R3-ACTIVE-MATERIAL-RELIQUARY-PARSER-SOURCE-SCOPE` 已完成限定范围审定；两项首轮分别在包装错误和长度预算门停止，Skill 的普通包装修正 RUN2 已完成限定 source 验收，Material 新完整范围规划已完成、RUN2 待开始；Reliquary 单窗已由主线独立派工并进行中，尚无成功结果。这些是静态来源问题，不批准 typed row 执行或资源值、alias、初始化、Java、恢复与 ready。

## Skill 与实际能量字段

已有 121 Depot 正引用的 369 个不同 Skill 中，仅 2 个与旧四个获批 `id/costElemType` 片段相交；新 id/element 缺 367，其中 energy 引用缺 120 个片段。`Depot.onLoad` 查 energy Skill 及其元素，`AvatarStorage.postLoad → recalcStats` 后 Avatar 还实际读取 `costElemVal` 和 `specialEnergyMin/Max`。这三个字段的 target 职责、字段、值及 oracle 尚未闭合：包括旧片段 `10068` 在内，**121 个 energy 行的这三字段均未闭**，不能因旧片段省略而补零。已验入口 `c3` 不证明 typed 初态、host zero、自然 GC 或完整构造，来源未齐前不批准 typed row 执行。

`R3-OLD121-SKILL-PARSER-SOURCE-WINDOW` 固定 `88602.m732420 / 149b56fe0`，flags `3c3`、instance、一显参、slot 5；唯一参数 `550628` 的 type reference `86325 → class 42005 / OFMKHIMCGKE`、`byRef=false`，不是同号 definition。formal return 和 hidden ABI 未知。历史 8309B、end `149b59055` 及 body SHA-256 `2f8dbdd35f208383213cc4843fe009845825a9f33e4231f553b3b0eb60a3602d` 仅作一致性对照；旧索引有范围/hash 不等于保存了完整逐条 listing。

新窗须 fresh AOT 16B、完整 exception 目录独立核对后唯一 exact-begin/containing/overlap pdata 12B、一次完整 8309B body 和 strict ≤4096 指令，并保存 durable listing；证据合计 8337B，header、完整目录和 hash/metadata 另计。任一门失败即停，不按下一地址、猜长度或 RET 截取；不执行目标。固定 `local/migration72/d02-resume-20261116/old121-skill-entry-projection-scope/spec.private.json` 最终 SHA-256 `8d2adfc37afb85d9cb52d9b0032dae3a763a83bf0c2f8f58011a8f41a012684c`；旧 `4bb84…` 仅因错误指定 Terra max 而修正为 Terra xhigh，范围未变。

Skill 首轮在 identity 阶段的私有 runner 抽取 `decode(code,entry=ENTRY...)` 时缺少 env 中的 `ENTRY`，以 `NameError` 停止；固定 35 pin / 506814395B hash、exact identity、alias 和一参 ref 已过，stock/PE/header/AOT/exception/body 均为 0。原目录 `local/migration72/d02-resume-20261116/old121-skill-parser-source-window/` 保留；failure SHA-256 `ffaef74f717ab742248eef35871943e9708737ab4bb92706f6455bba5eb193b2`，manifest SHA-256 `fd23720172defab9be25dd8cb4704f9066019baee701fb2846abd64086ff7c5e`。主线审核包装与 helper 后明确授权 `R3-OLD121-SKILL-PARSER-SOURCE-WINDOW-RUN2`，在新 `old121-skill-parser-source-window-run2/` 仅补 `ENTRY=int(selected.entry,16)` 和输出 prefix/task ID/失败关联；decode 调用仍显式传 entry，8309B、8337B evidence、4096 strict、pdata 及其他门不变。这是普通包装修正，不是新安全拒绝。

RUN2 已完成限定 source 验收：fresh AOT、唯一完整 pdata、完整 exception hash 与 8309B body hash 和历史一致；strict 1985 条指令、539 引用（144 CALL / 328 branch / 67 RIP）。直接准确冻结 site/target 索引为空只覆盖本窗直接引用，不证明传递 A-free。来源证据 8337B；header 两读 4616B、distinct 4096B，五 stock hash 550661784B（PE 450927000B），PE stock + header + AOT + 目录 + body 共 465284021B；pdata 12B 从目录复制，不重复记物理读。额外预检、GetFileHash 和 request 应用 IO 见 `capture/io-addendum.private.json`；底层 loader/OS calls 未计量，不称全部物理 IO 已计。目标 CPU/进程读取、row 值及 ready 均无。

RUN2 私有根 `local/migration72/d02-resume-20261116/old121-skill-parser-source-window-run2/`：`capture/result.private.json` SHA-256 `1d8f87ead14d030d01e2efcbe71b1cf9951dbe35443c4c1beb73e5a837262f9c`、`capture/body.asm.private.json` SHA-256 `e05cbc6e1a98af8c21ec442887852a7df2d5fbba86eacfbee2d5d88fc82eaa16`、`capture/reference-index.private.json` SHA-256 `dd10dd1a4e31b97efa788437f8c37b58b570762b7bc216b5ad90ad06c1fe4092`、`manifest.private.json` SHA-256 `cd5bd1e5b1644613bc8094f6491b8c284cd9e03a28a828a4ffb8fde0ba9e981a`。`R3-OLD121-SKILL-PARSER-MINIMUM-SEMANTICS` 进行中，尚无语义结论。

## Material 与 Reliquary

Java 冷读最小字段分别为 Material `{id,itemType,materialType}`，Reliquary `{id,itemType,equipType,rankLevel,setId}`；后者 postLoad 重算还存在其他未闭缺口。已审 source-only 规格如下，均 flags `2c3`、slot 4、instance、一显参；两者参数分别为 `155009`、`327791`，均是 type reference `86325 → class 42005 / OFMKHIMCGKE`、`byRef=false`，不是同号 definition。formal return / hidden ABI 未知。此前误名 `KIPCGJDGMGP` 已在派工前撤回，不作为已验类型名。

| 任务 | 准确 parser | 当前状态 |
| --- | --- | --- |
| `R3-ACTIVE-MATERIAL-RESOURCE-PARSER-SOURCE` | `23400.m180606 / 14a88ca00` | 阻塞：原完整边界 7128B 超原 4096B cap |
| `R3-ACTIVE-RELIQUARY-RESOURCE-PARSER-SOURCE` | `46431.m385148 / 1529b1930` | 进行中，按原 4096B 规格独立派工 |

限定旧来源没有两者完整逐条 raw parser listing。原规格各窗独立预算：body ≤4096B、strict ≤1024 指令，fresh AOT 16B + pdata 12B，证据 ≤4124B；header ≤8192B / 2 reads、完整 exception 14344080B、hash 与 metadata 另账，不读 callee/global/xdata。各族须主线按独立规格分别派工，失败不自动授权重试或扩源。

Material 首轮经完整 pdata 门确定 `14a88ca00..14a88e5d8` 为 7128B，超原 cap，按 `ValueError: body extent` 停止；body 0B / 0 read，pdata evidence 0（预算门未接纳），未解码。identity、一参 ref、fresh AOT 与 exception hash 通过；header 4616B、AOT 16B、exception 14344080B、stock 550661784B。原 `local/migration72/d02-resume-20261116/active-item-material-parser-window/capture/failure.private.json` SHA-256 `1f5a7c9140e1902b3c278b66b02ca91239e3bd50419decfd06c9a2e7775dce4a` 保留。这是**完整长度超原 cap**，不是缺边界；不泛封 Material 或其他工作。此前一次 driver 前 schema 查看把字符串键当整数导致 `KeyError 86325`，未读 PE；识别正确字符串键后继续，不是采集重试，额外 signatures 两次、metadata 一次和 derived 一次实读另账。

`R3-ACTIVE-MATERIAL-PARSER-EXACT-EXTENT-SCOPE` 已完成限定规划：仅依据确认的完整 7128B 边界与原 row/raw 12B 修订新单窗，evidence 为 7156B，strict 指令上限 7128（每指令至少 1B 的硬上界）；须重新核 current fresh AOT、完整 exception hash 与 pdata，使用新 prefix `active-item-material-parser-window-run2`。新 source RUN2 **待开始**，原 cap 失败不追认。固定 `local/migration72/d02-resume-20261116/active-item-material-parser-extent-scope/spec.private.json` SHA-256 `9c72aca5edd80e9582bdc99215c1b4e6c2709b93e958e1445ed48f3add1190c1`，同目录 scope SHA-256 `1dccf2e68bd1140c1ae7f2a28fdaf8b2ad794e6ded0dcf31c3688628788684ce`。

父 `88968.m735168 / 150b6a9b0` 仅是后置候选，须已验 raw listing 的准确 CALL 和必要字段来源成立后再由主线审定。旧报告有 2018B 范围/hash，但现有 3246B 文本 listing 止于 `150b6ab3a`，不是完整 source。固定 `local/migration72/d02-resume-20261116/active-item-parser-source-scope/scope.zh.md` SHA-256 `26eda8692b0e1b5ca097d1f2991f4ac1c9fc35bae79f377e6aa1e4974e899746`，同目录 `spec.private.json` SHA-256 `29cd7839e7530067519925103c4018c32d37676935300c8d16bb52376fc103d7`。

规划阶段无新 PE/window、目标 CPU、JVM、测试、DB、网络或 Git；后续 Skill RUN2 的有限来源及 Material 停止点和分账如上。两个普通首错不是新安全拒绝，不提升父项；A/B、preview 及准确冻结边保持，D-02 / F-05 / D02-R6 仍为 0/6。
