# 7.2 三层映射输入

2026-11-06 F-03d1c：PlayerData用途及五字段有限接入，28三层行，Cmd/类型23 verified/5 unresolved，schema10 verified/13 candidate/5 unresolved；248字段70 verified/9 candidate/169 unresolved。完整raw不等于业务完成，ready仍拒绝（208条缺口；较205增加来自显式列出raw未知，并非静默清零）。统一review84证据/1268源码与Proto，151根/矩阵、1121描述符文件、1258类型/4810字段，两次recover一致。见[PlayerData合同](../../docs/CLIENT_72_PLAYER_DATA_CONTRACT.md)；完整P0与实机未验。

2026-11-06 F-02b/F-03当前：27三层行（含embedded Vector用途别名），Cmd/类型21 verified/6 unresolved，schema10 verified/11 candidate/6 unresolved；239字段65 verified/9 candidate/165 unresolved。字段wire和应用编码分别存，Java optional为存在性适配；不声称原target语法。研究结构0错误，ready仍205缺口，P0闭包unresolved。74证据/1267源码与Proto、150根及矩阵双跑相同；468研究/761 Java及portable构建通过后按要求暂停。当前Asta对照239/147/92、两个MD5冲突保留本地结论，旧214/130/84为F-01快照。见Project/HANDOFF2与新scene/response合同；无D-02/运行部署验收。

2026-11-06固定AstaPS审计与F-02a融合：[协议来源](../../docs/CLIENT_72_ASTAPS_AUDIT.md)/[实现候选](../../docs/CLIENT_72_ASTAPS_IMPLEMENTATION.md)及两个astaps-*-observations摘要新增，研究输入65→67。四运行schema45字段仅10个用已验业务名、35保留目标原名，生成器`tools/generate_login72_slice.py`不晋级mappings。10个上游CmdID相符不等于全闭包；214字段130同形/84缺tag，两个MD5语义冲突保留本地结论。399研究、结构校验通过/ready228；完整P0/客户端未验。按用户要求限定融合后暂停，见Project/HANDOFF2。原source/dependency/proto review已逐差异人工复核、旧守卫拒绝证据保留，未改变守卫算法。

B-02e3计时纠名新增[stage-timing-observations](stage-timing-observations.json)，并把旧摘要误称identity request的三个键修为stageValue/stageBodyExecuted。14C8AE9A0是85949/OnPlayerEnterSceneNotify阶段计时；13原生样本、311完整研究回归、两次一致，55研究输入受hash约束。三层字段与runtime不晋级，见[计时证据](../../docs/CLIENT_72_STAGE_TIMING.md)。

B-02e2新增[scene-wait-observations](scene-wait-observations.json)，见[入场通知/缓存等待](../../docs/CLIENT_72_SCENE_WAIT.md)：27748/46311/PlayerEnterSceneNotify两层身份verified，26外层业务字段全部unknown/unresolved，三float非空/列表/原生clone与明确缓存/前置边界已执行。完整研究305通过，矩阵54输入受hash约束；研究校验通过，ready仍228缺口。没有把wire夹具载体中的opaque bytes当成完整消息schema或生成生产Proto。

E-01b资源输入摘要新增[scene-resource-observations.json](scene-resource-observations.json)，复现与限定范围见[scene3审计](../../docs/CLIENT_72_SCENE_RESOURCES.md)。它只固定资源/读取依赖，不修改任何协议三层状态，resourceReady=false、runtime未验。

E-01c新增[scene-script-observations](scene-script-observations.json)、[scene-grid-script-observations](scene-grid-script-observations.json)与[scene-grid-inline-observations](scene-grid-inline-observations.json)：固定旧实现对7.2实体表，出生块及全64块非dynamic顶层LuaJ实际求值，记录当前fastRequire的407错误和现有inline路径比较、8个garbage缺ID及62个缺成员。用户允许参考7.1迁移，来源仍明确继承；配置未改、生产序列化/触发器未运行。详见[脚本迁移](../../docs/CLIENT_72_SCENE_SCRIPT_MIGRATION.md)，resourceReady/runtime及协议状态均不晋级。

2026-11-06 B-03a/C-01a：仅选人三件组已按verified三层证据确定性生成生产Proto与常量，来源/碰撞/旧号漂移均拒绝；工具`tools/generate_born72_sources.py --client $env:GAME_PATH [--check]`每次核对实际输入hash。生产三件组与空schema依据见[CLIENT_72_BORN](../../docs/CLIENT_72_BORN.md)。其余研究Proto不自动接入运行，完整协议仍不ready；本批272研究/495 Java选集通过，全部客户端runtime未验。源码/Proto矩阵现99构造/148种子/1140文件，原Notify缺根由目标证据接管后补齐；下方旧数值为历史。

`profile.json` 固定目标版本/渠道与关键输入哈希；`mappings.json` 分别维护 opcodes、messages、schemas 三张表。26个登录/选人/入场种子中，Token/Login四个请求响应、PacketHead和PingReq/PingRsp的现有身份层已verified；字段层仍逐项保留未知/候选，完整schema未verified。旧段落为各研究检查点的历史范围，以mappings及最新矩阵为准。**空字段列表不代表实际空消息，当前没有完整正式协议。** P0完整闭包未确认，closure保持unresolved。

每条记录独立保留 status（unresolved/candidate/verified）、evidence、ambiguities。消息表另有方向、别名、runtimeStatus；字段表记录 semanticField/tag/type/cardinality/presence/packed/oneof、nestedType、enumValues或map键值。已验证字段必须写明presence和packed；枚举/嵌套/容器不能只保留一个wire type。

新增证据需给唯一ID、层次opcode/message/field/closure、profile输入键、相同SHA-256和相对证据位置/VA。不同层不能互借证据，静态候选不自动升为verified；runtimeStatus由实机结果维护。字段/消息拆分合并、删除或新增应先在ambiguities/证据说明中记录，确认后修订规范名，不能猜测旧字段的对应关系。

```powershell
python -m unittest tools.test_validate_protocol72 -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

第一条为合成数据结构测试；普通校验只验证研究记录自洽，当前应通过。`--ready`要求所有层及P0闭包已核实，当前**必须失败**。它检查范围、别名/唯一包号、字段引用/容器和证据归属，不替代人工判断证据真实性，也不是Java/protoc生成器或客户端验收。

`wire-observations.json`和`research.proto`由extract_client72_wire.py从三个已审查的目标读写函数生成，共17个字段；原始字段名字避免伪造业务含义。校验器同时比对原始command/wire表，候选也不能与目标号/tag/类型冲突。`check_client72_wire_proto.py --protoc ...`编译离线Proto并比较手写独立wire断言；合成数据不是客户端抓包或登录验收。研究Proto不进入服务端Gradle源集，完整业务生成器仍待开发。

`token-wire-observations.json`/`protected-token.proto`为TokenReq保护writer的28字段离线观察；`emulated-observation`与未保护函数的`static-confirmed`区分，helper和零全局模型限制见[保护入口研究](../../docs/CLIENT_72_PROTECTED.md)。mappings中这些字段保留unknown业务名，校验器核对它们与观察表的tag/类型一致；三组模拟输出与protoc比较通过仍不能解除服务端ready闸门。

`commands.json` 为固定哈希上恢复的4,012条静态CmdID→混淆类型绑定，已有两个发送入口和封/解包证据；4,007个不同值，5组重号保留歧义。它与业务映射分离，不能直接替换PacketOpcodes；业务名、网络域、运行时IFix覆盖仍需核实。可通过 `extract_client72_commands.py` 确定性再生成。证据见 [网络研究](../../docs/CLIENT_72_NETWORK.md)。未知处没有7.1正包号回退。接手见 [提取记录](../../docs/CLIENT_72_EXTRACTION.md) 和 [Project](../../Project.md)。

A-04d3新增login-wire-observations.json/login-research.proto：55外层字段、12消息83字段；response-wire-observations.json：两个响应85外层字段，contentStatus=not-traced表示内容尚未恢复。mappings.json只接入三类外层的unknown_field候选；clientNestedType是原始类型名，不是稳定业务schema。普通校验同时核对新表，ready仍失败。响应没有生成完整研究Proto，禁止以空message替代未知内容后宣称支持。

response-leaf-wire-observations.json/response-leaves.proto补充7个独立叶子30字段，含由原生读写证实的sfixed32；叶子有schema不意味着父容器内容已通过，response-wire-observations的not-traced状态继续保留。

A-04d3c：response-graph.proto是18消息137字段的完整响应研究闭包；response-graph-observations用21条contentBindings补充非空内容证据。map-aot-observations来自目标MethodSpec/函数注册；校验器检查新表与原始字段一致性。native-graph-observed不等于业务verified或实机通过，mappings中的unknown_field保留unresolved，ready继续失败。详见docs/CLIENT_72_GRAPH_READERS.md。

登录业务证据：login-literal-observations.json为4条目标静态日志标记与usage落点；enum-observations.json为7枚举86符号及4个协议字段/oneof关联；login-semantic-observations.json为两个响应身份及20字段来源。详细模型边界见docs/CLIENT_72_LOGIN_SEMANTICS.md。只有有对应来源的字段晋级；两请求配对、其余字段及P0闭包仍未确认，--ready继续失败。

2026-11-06 A-03c限定离线验收完成，见[候选消息/Proto与三层缺口矩阵](../../docs/CLIENT_72_LOGIN_PROTO_MATRIX.md)：97包构造、148引用/种子、1139文件描述符依赖，缺DoSetPlayerBornDataNotify.proto如实保留。原三层状态及185字段不变；6新增/260研究回归、重复矩阵通过，研究校验0/ready1（213缺口）。V-01b/c经济/保存221项通过；完整Java集未重跑。按用户要求到此停止并交接Astra，下一研究为B-02选人三件组的独立7.2三层证据；A-03整体仍待完整闭包/实机。未改运行文件、未部署或调试。
