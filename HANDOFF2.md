# F-02b / F-03 融合验收后暂停交接

2026-11-06。用户最新范围为恢复F-02b/F-03融合、优先本地证据、对应回归/文档后暂停，暂不D-02。**本批限定融合已验收并暂停；完整P0、客户端登录/场景与D-02仍未完成。** 原HANDOFF2已先备份至`local/migration72/backups/HANDOFF2-before-f02b-f03-4b566f9-20261106.md`并逐字节/hash核验，证据`local/migration72/evidence/f02b-f03-handoff-backup-proof.json`。HANDOFF只追加，未重写历史。

## 当前提交和来源

接手4b566f9；本地提交0ebe663（计划）、def036e（最小响应/初始化raw）、a5fe305（八握手/容量）、d0cddb0（请求省略/1017域）、fbe99d5（位置40原生）、4bbf9fd（运行接入/回归/三层及源码矩阵）。最后交接为文档提交，运行JAR固定内嵌4bbf9fd；未推送。

Asta固定MeChen618/AstaPS `a49f51aa6f2787ffbc25cee46801b70dee860d23`、tree `9940bf7756ce5ff8be8a836a4485a645b490c2e1`，收尾HEAD/干净均复核。参考在忽略`local/migration72/references/AstaPS-7.2.155`，保留GPLv3/上游署名，不整仓合并或复制生成Java。三目标输入仍以`protocol/cn-7.2/profile.json`及`tools/extract_client72.py`为准；5个生成器均实际重读三文件哈希并check无漂移。本地account_type/tag15、sub_channel_id/tag10、LoginRsp两MD5语义优先，上游两个MD5冲突未消除。仅Windows portable、脚本/API，无Docker/WSL/computer-use。

## 已完成的限定融合

| ID | 修改与验收范围 |
| --- | --- |
| F-02b1 | GetPlayerTokenRsp41/PlayerLoginReq55/PlayerLoginRsp44字段及18 raw nested58，共21类198字段；3已验CmdID。只改Proto源，经Gradle/protoc生成Java。原名保留未知，nested optional只作为Java显式0/false/空串合并适配。50 native writer/34 native响应图/6错误拒绝独立验证 |
| F-02b2 | Token成功只uid/token/sign/server_rand_key；错误retcode-only。key<=0在准入前拒绝；RSA失败去掉伪签名/异或假seed/切key。实际JCA加密/解密/XOR/验签成功与%%/AQ==失败通过，UID/账号保存路径保留。LoginRsp资源61只子1/3/4/5，不发未知版本/UID/旧440/658。连续收帧key selector仍缺 |
| F-03a | Ready5046/9948、Init4905/21392、Done1017/298、Post1696/26760，10类20 raw字段；8 token/4 retcode及Notify token应用变换独立。请求parse一次并逆变换后关联当前Player token，失配在所有游戏副作用前走既有异常路径；不造retcode/时序策略。缺字段照raw0解码，Done42557/Post64180正常可过；Rsp编码raw0必须显式发。15+12 Java通过 |
| F-03b | 容量28836/22609：8 raw字段/5具名，pack enum+relic/weapon/material/furniture容量；3未知不发、不命名总weight。134 reader、12真实字典路由、enum/阈值及容量6 Java；服务器经济总量Guard不动。历史选择缓存21136/46070已非目标坐标，停用正号与onLogin发送；显式DoSet/SetPlayerBorn与确认保存保留 |
| F-03c | Notify27748只scene_id/type/token/非空pos；8类44 raw字段完整，Vector3轴另验。40 native=31+6 Default/DirectlySetPos+3独立QuickV3，两轮一致；wire12/producer8及三套入场/成员/复活81通过。保留7个public构造、LOADING/随机token；不发送未命名world/time/UID/dungeon/transaction/region，不称头像实际落地 |

三层当前27行：Cmd/类型21 verified/6 unresolved；schema10 verified/11 candidate/6 unresolved；239字段65 verified/9 candidate/165 unresolved。Vector是已具名embedded用途别名，原target类仍BGDCNKAKGHI。闭包unresolved，结构0错误，ready205缺口（接手228）。Asta当前21目标类型/239字段，147同形/92缺tag/0形状冲突、2 MD5语义冲突；实现审计仍是c9aafa6基线，不能当当前源码。

source review13源/81方法/98边（16人工82候选）/998未关联；dependency49源/294方法/411边（75人工336候选）/2854未关联，4条requireCurrent static边明确复核，旧人工边及开放边界不丢。Proto review74证据/1267源码与Proto、98候选/150根及矩阵/1120文件/1257声明/4806字段；28旧输入变化、4新增、24仅依赖退场，63旧证据hash保持。verified nested依赖使Vector入矩阵，不是编译或同名自动批准。旧review拒绝与两轮恢复证据在`local/migration72/evidence/f03-final-proto-review-check.json`和`analysis/fusion-source-review/`。

## 实际测试、构建和环境

- 最终研究76模块/468项，fail/error/skip全0，43.906秒；`evidence/f02b-f03-final-research-regression-summary.json`。
- 最终独立Mongo43套/761项Java，fail/error/skip全0；`evidence/f02b-f03-final-java-regression-summary.json`及`f02b-f03-final-java-test-results/`。包含账号/经济/保存/Lua、显式选人、协议及入场/成员/复活；不是全Java集，全Java历史217失败未在此全量重跑。
- 三次使用新独立Mongo目录/27027，均正常shutdown，之前仅admin/config/local；原27017/原数据未动。中间38套660通过，最后新增Notify20与世界三套81后为43套761，不能将二次结果重复相加。
- 五生成检查：`generate_login72_slice.py`、`generate_born72_sources.py`、`generate_login72_contract.py`、`generate_scene72_sources.py`、`generate_enter_scene72_sources.py`，均`--client $env:GAME_PATH --check`无漂移；调用入口见Harness。
- 实际`tools/local_windows.ps1 -Mode build`24秒，JAR `570d64f4f1680ab023c7eda7f2e930ebf5eba79791030e8499100ca8f2799bf5`、内嵌4bbf9fd；92变化/114新增class与编译输出一致，109账号/Guard/Lua类字节保持。Player及其行号相关类变化对应移除旧选择缓存发送；3旧LoginRsp map holder被raw字段替换。旧JAR a8c2d893…53a8f8已校验备份`local/migration72/builds/LunaGC-before-f02b-f03-a8c2d893.jar`。
- `-Mode status`退出1表示无服务器，HTTP8088/UDP22101/测试27027无监听，无服务PID/健康/运行JAR对象。**未部署、未安装补丁、未D-02**，原因完整P0/SDK/信任/补丁/资源前置不齐。解除条件是这些前置逐项验证后再部署独立运行数据，核对日志/健康/TCP/UDP/PID/运行JAR SHA；不靠改版本、跳校验放行。
- .env portable、.env.local无、GAME_PATH实际目录留本机配置；config SHA `0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45`及fastRequire=true未改。patch f2874103ab70afc56260ee12d1eaca96247933b2、resources dac2a9d43332ef51879522cf39da2831b0ca0543；AGENTS/resources原差异hash保持且未提交。
- 收尾仅原Mongo与两个既有x64dbg进程，**原YuanShen目标当前未见，退出原因unknown**。本轮未用调试API/客户端操作，不能拿旧PID/旧暂停状态无条件run/load。具体进程/本机路径只在忽略`evidence/f02b-f03-final-runtime-check.json`，此处不复制机器标识。

## 复现、弯路和限制

响应旧schema4/4失败、merge4中3失败、初始化15中7失败、握手14中10失败、请求12中4失配漏关联、Notify producer7/7及raw12/12失败均保留。nested默认proto3 merge会丢第二消息显式0/false/空，optional只是Java适配，不能称原target语法。请求writer省略raw0，不能套响应reader的constructor默认logical0；否则会误拒正常42557/64180。1017全局两条仍保留，只有正常Game C2S与另一beyond/WPF流出站已分清，不能叫22065 Game S2C/旧MonsterChess。

首660 Java有6测试前提问题：既有账号测试无加密前提2项、RSA全send=1断言3项、旧21608断言1项；修正夹具保存/恢复配置、仅TokenRsp单回复及目标包号断言后全过，未绕生产入口。Player新号构造本来还先发其他初始化包，不能把Token单回复说成全流程只有1包。Notify新BG类型暴露6个旧getter编译点，三业务测试改为实际三轴和四tag合同；宿主/等级改验服务器业务状态，不虚构未知wire字段。

研究首468有1个Vector语义行未入Proto矩阵的KeyError，修复为verified字段的嵌套依赖索引，并实际双跑/全468再过；未放宽未知字段/源哈希。一次本地review辅助脚本误用已刷新报告作差异起点导致assert，后固定从4b566f9只读还原基线，精确允许清单和原63摘要核对通过，失败日志保留。位置命名最初只查RIP-load漏掉CMP类引用；正常mode0/3与QuickV3独立具名路径分验，不能模拟未知接口槽返回成功或把模式7冒充正常分支。

完整SDK初始化/来源信任、完整Token/Login Handler和连续接收key selector、初始化全序列、场景后续协程/落地都未验。部分native managed helper和UI/事件调度有明确模型边界，见各专题；不把静态命中、当前构建、raw图或健康当客户端成功。

## 用户恢复后的可执行顺序

1. 先读AGENTS、README、Project、Harness、迁移/部署及本交接；复核checkout/config/GAME_PATH三哈希、子模块、参考HEAD/clean和端口/进程。目标当前缺席，先核对调试器会话，勿直接重复load/run；本批没有授权推进D-02。
2. F-03父项：优先复用本地P0来源及已有raw图，补PlayerData/PlayerStore/Avatar等最小初始化消费者；Notify未知world/time/owner UID/dungeon/transaction/region逐具名源验证，不重做8类44字段reader。`CLIENT_72_ENTER_SCENE_CONTRACT.md`保留manager+100、static time、PlayerModule+1D8等明确下一跳，算术相同不证明业务名。
3. F-02父项：恢复连续实际收帧选择key来源及SDK/完整Handler证据，保留账号准入/UID/显式选人/确认保存/经济Guard/Lua。上游0/旧号/20000+/推断都仅候选，本地15/10和MD5冲突证据优先。
4. F-04/P0前置满足后才做Windows portable独立部署和实际日志/健康/TCP/UDP/PID/运行JAR验证；随后用户恢复实机范围时逐验新号显式选人、旧测试号登录、场景握手、心跳、退出重登。当前D-02全部未验。每批本地分项提交、不推送；交接先备份核验H2后覆盖，H1只追加。

文档已维护Project/README/Harness、迁移/部署、Asta融合/审计、源码/Proto矩阵、账号三篇及登录/响应/初始化/场景专题。原始二进制、日志、依赖、数据库和本机路径仍留忽略目录。
