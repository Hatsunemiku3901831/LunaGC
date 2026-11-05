# 国服7.2详细接续记录

更新：2026-11-05。用户已明确恢复A-04d/B-02并要求一直推进到D-02完成验收，随后强调持续维护Project.md、按任务列表逐项验收标记。**当前没有暂停指令，接续无需重新申请恢复；D-02未完成，不得宣称目标达成。**

覆盖前已把原HANDOFF2.md复制到忽略目录`local/migration72/handoffs/HANDOFF2.before-dispatch-prefix-c9f6870.md`，核对复制前后SHA-256一致。HANDOFF.md只追加。旧暂停记录属于历史。

## 1. 接手顺序与工作区

遵循根AGENTS.md；先读README、Project、Harness、CLIENT_72_MIGRATION、CODEX_ONE_CLICK_DEPLOY，账号/认证另读三个ACCOUNT_AND_SAVE专题。Project管理任务及验收；本记录不能替代任务表。新证据详见`docs/CLIENT_72_SESSION.md`末尾A-04d5l/5m/5n1。

分支`7.2-dev`。本批研究提交46d422c、5dcf882、c9f6870；任务表维护提交74ac0f0、54dfac1。全部仅本地，未推送。

两项既有差异仍原样保留、未提交：根AGENTS.md；resources内`ScriptSceneData/flat.luas.scenes.full_globals.lua.json`。patch HEAD仍f2874103ab70afc56260ee12d1eaca96247933b2，resources HEAD仍dac2a9d43332ef51879522cf39da2831b0ca0543。不要提交他人差异或子模块引用。

本机配置已核对GAME_PATH、LUNAGC_RUNTIME=portable；工具每次从指定GAME_PATH重新核对三份固定SHA-256。未启动客户端、未改运行Java/Proto/补丁/配置/资源、未操作数据库，也未重启服务。本批是离线研究，现有运行服务不是7.2可连接构建。后续若改运行内容，必须重新核对配置/进程/固定端口归属并完成构建、便携部署、日志/健康/TCP/UDP/PID/JAR哈希核验。

## 2. 当前任务状态

| 项目 | 实际验收 | 剩余边界 |
| --- | --- | --- |
| A-04d5k/B-02c8基线c7556a0 | SDK凭据五字段到会话/真实seed/Token writer | 不等于完整认证调度 |
| A-04d5l，46d422c | 原生认证入口连续到14F192325，六个可选session字段、缺省清零、Int32边界/溢出、两个exact-one标志 | UI/调度未执行；无新协议语义 |
| A-04d5m，5dcf882 | 候选30677 tag14→两处post-parse片段→session+B0→Token+70/tag965，14组含空值与缺对象边界 | 当时源类型关联candidate；第二协程仍未绑定；业务未知 |
| A-04d5n1，c9f6870 | 151210540 JSON→原生Base64/RSA/SHA/验签→实际30677分配/reader→151211BA1的post-parse原指针，6组 | 成功明确本地签名信任；后处理/第二协程/业务/实机未完成 |
| A-04d5n/B-02c9 | 进行中，前缀类型关联已推进 | 后处理至B0连续路径、第二协程、tag965业务来源 |
| A-04d/B-02 | 进行中 | P0三层映射/剩余字段/实际流程未闭合 |
| B-03/C-01 | 待开始 | 前置映射未ready，不接猜测包号 |
| D-01/E-01 | 进行中 | 实际补丁信任/加载、资源revision/Lua/场景等未齐 |
| D-02 | 待开始，无7.2客户端验收 | 新旧测试号、选人、场景握手、心跳、退出重登全部未实测 |

四个登录身份/CmdID仍为TokenReq82491/24604、TokenRsp37481/27808、LoginReq76410/23172、LoginRsp40503/20835。业务字段仍35，不把B0原始链或认证可选session字段当作新协议语义。

## 3. 本批测试和证据

- 认证可选字段5组原生样本，两次trace/摘要逐字节一致；初始聚焦27项通过。
- B0链两路径各4值+3缺对象，共14组原生样本；两次trace/摘要一致，聚焦30项通过。
- 分区解析前缀6组：本地非空/空/高数字文本成功；坏签名、原始信任下不匹配签名、缺content拒绝。真实key验签与独立RSA一致；SHA与hashlib一致，原生解密byte[]与独立wire相同。两次最终trace/摘要一致，聚焦33项通过。
- 最新完整研究工具/映射/资源审计回归169项通过；不是169个真实游戏场景，也不是Java完整集。既有Java217失败未据此消除。
- 三层研究校验退出0；--ready实际退出1，未知字段继续拒绝发布。git diff检查通过。
- 测试开发中发现摘要输入accepted=true/value=null时抛异常，已修正并回归；未删减该拒绝测试。

忽略证据位于`local/migration72/`：

- `evidence/auth-tail-{trace,trace-repeat,determinism}.json`
- `evidence/dispatch-token-{trace,trace-repeat,determinism,mapping-check,mapping-ready}.json`
- `evidence/dispatch-prefix-{trace,trace-repeat,determinism,mapping-check,mapping-ready}.json`
- `logs/auth-tail-tests.log`、`dispatch-token-tests.log`、`auth-tail-dispatch-regression.log`
- `logs/dispatch-prefix-focused-tests.log`、`dispatch-prefix-regression.log`

已提交摘要为`protocol/cn-7.2/{auth-tail,dispatch-token,dispatch-prefix}-observations.json`。私钥、客户端、完整trace、反汇编、环境配置和日志不提交。

## 4. 关键数据流

认证可选字段：allow_community→session+120；allow_ugc→+124；allow_pay→+118；country/string→+E0；age_group→+184；lrsag→+16C。除country外是int32；community/ugc另设全局标志仅当值恰好1。缺字段实际清零；此段不写B0。

B0原始链：151212949..151212983（store15121297C）和15122952D..151229567（store151229560）都从`[rbx]+20`读取字符串，GLOBAL+9E80的manager+180定位session并保存+B0。Token构造取+B0到请求+70/tag965。非空保留指针；空值会话保留而请求采用默认空对象，writer省略tag965。缺session跳过写入；缺manager/source到原生错误调用前停止。

实际分区前缀：151210540接受JSON字符串，目标读取content/sign；30677 Clone153A597F0的self-class cell1458C3CC0及CopyFrom153A594A0由原始指令检查。验证通过后真实分配该class；147EA0940从byte[]初始化流并调用slot19/153A598A0。原始tag14写入DGHGFAELIDO+20，parsed对象保存在`[rbp+A8]`指向的槽供后处理使用。源30677的这个入口关联现有执行证据，另一协程尚未执行。

成功夹具用仓库4_Pub.der独立加密明文，SigningKey独立签署明文。操作101的目标私钥/签名信任不改；操作103仅提供一致的本地服务器公钥、公钥签名及根公钥输入，真实验签决定不替换。无原始服务器有效签名、实际补丁/网络通过的声明。

## 5. 下一步按Project任务执行

1. A-04d5n/B-02c9：从151211BA1继续到B0 store。当前成功wire只有tag14，缺RegionInfo等字段；不能宣称整体分区成功。需要原生解析RegionInfo/配置并覆盖合法与错误输入，或分段明确记录未执行边界。
2. 验证15122952D所在第二协程的实际解析源类型，不把第一入口证据自动扩展过去。
3. 寻找tag14/tag965独立业务来源。目标全局literal查询未发现connect_gate_ticket名称；旧Proto同tag名称只能作为线索，仍保持unknown_field_965/unresolved。
4. 可独立推进认证14F192325之后UI/调度、Handler后段和14A447E00封包体；按依赖补齐B-02再做B-03/C-01。
5. D-01真实SDK/HTTP路由/补丁信任及加载、E-01资源revision/Lua/场景核验仍是D-02前置。不能用离线通过、编译或健康代替新旧号实机序列。

复现：

```powershell
# 从已核对的本机配置设置GAME_PATH，Python不会自动读.env
python tools/trace_client72_dispatch_prefix.py --client $env:GAME_PATH --output local/migration72/evidence/dispatch-prefix-trace.json --summary-output protocol/cn-7.2/dispatch-prefix-observations.json
python -m unittest tools.test_client72_dispatch_prefix tools.test_client72_dispatch_token tools.test_client72_auth_tail tools.test_client72_auth_sources tools.test_client72_token_complete tools.test_validate_protocol72 -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

最后一项预期拒绝。其余两新工具复现命令在CLIENT_72_SESSION/Harness。依赖默认`local/migration72/python`，固定输入以profile.json/HASHES为准。

## 6. 弯路与限制

- 不线性解释保护段中的数据字节。对保护入口使用真实执行；原生实际读到的type cell才有意义。
- 前缀探索的class=0不是缺byte[]：实际缺145887560的SHA256Managed及1458869F0的uint类型，复用ResponseCrypto72已有夹具后消除；不要给任意class分配对象来绕过。
- 15A4D7F00处返回值未证明是signed32错误码；最终工具记录完整nativeReturnRAX，不截断指针、不推断返回语义。
- 认证/分区JSON容器和ASCII wire底层为显式模型，JSON parser全部强转、Unicode、嵌套/IFix均不自动覆盖。
- 工具成功不是整个分区成功。当前6组成功输入没有RegionInfo字段，终点151211BA1必须保留。
- `analysis/current-structures.json`很大，不直接rg打印单行；用Python按type/address过滤。探索脚本`probe_dispatch_prefix.py`/`probe_dispatch_signed.py`已被正式前缀工具接管，不能引用旧失败范围代替当前证据。
- 本批无必须等待外部才可做的整体硬阻塞，未完成来自真实研究/集成缺口。不要错误标记D-02完成或以“用户未授权恢复”作为阻塞。
