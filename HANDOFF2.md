# 国服7.2详细交接：B-02d选人三件组闭合，立即停止研究

更新：2026-11-06（Australia/Sydney）。用户要求只攻DoSetPlayerBornDataNotify、SetPlayerBornDataReq/Rsp，先建子卡，三层证据闭合且Sol可按明确规格实现回归即停。**停止条件已达到，研究已停止。** 本交接不是继续B-03/C-01/D-02的授权；没有派发新chat或子代理，也没有执行Sol后续实现。

## 当前状态与提交

- `3fc7e80`：先建Project B-02d验收卡、明确范围和停止条件。
- `36eb486`：B-02d限定研究验收，工具/6项测试/研究Proto/三层映射/矩阵及文档，39原生样本、266研究回归通过。均仅本地提交，未推送。
- 阅读入口：[CLIENT_72_BORN](docs/CLIENT_72_BORN.md)，包含固定输入、逐层证据、模型边界、独立十六进制向量、给Sol的具体实现回归规格及复现命令。任务卡在Project，索引在Harness，完整矩阵在protocol/cn-7.2/login-proto-gap-matrix.json。
- 没有修改src/main Java/Proto、生产配置、版本准入、补丁或资源；无需构建部署。当前无7.2可连接构建或实机选人/登录/入场验收。

## 已闭合的三件组

| 消息 | 7.2 CmdID / 类型 | 字段及关键证据 |
| --- | --- | --- |
| DoSetPlayerBornDataNotify | 5008 / PMNMDAIKALO / index69319 | 目标自身无字段，无slot13/17/19覆盖；父67861的零size/空writer/跳过未知字段reader实际执行。getter1528AEBE0、注册1507C2B20、分派到Handler1507D25D0及参数类型交叉一致；Handler门控到选人页面 |
| SetPlayerBornDataReq | 8541 / JNJOMANHPCD / index29956 | nick_name:string tag4/+18；avatar_id:uint32 tag11/+20。自身clone类槽1458C5458→构造151223B80→真实writer14CC44560/size14CC44380；选择回调到命名页字段和输入文本来源分别追踪 |
| SetPlayerBornDataRsp | 414 / EODFLMDBPOG / index34385 | retcode:int32 tag15/+18；reader15374AC20/key0x78与size15374AB50。注册1507C2B2F、getter15374AA50、分派1507D2820。0走成功入口，非0走错误入口，132另发NicknameWordIllegal事件850 |

三层研究状态verified，runtime均not-tested。通知无字段不是根据旧Proto缺失推断；原缺失DoSetPlayerBornDataNotify.proto继续列在遗留依赖图。研究Proto在protocol/cn-7.2/born-research.proto，不是生产Proto。响应类型没有自己的writer，不能用继承空writer发送错误；生成服务器响应必须真实编码tag15。

目标输入3份SHA-256均由工具每次核验，见专题及born-observations.inputSHA256；不依赖旧路径、旧号或缓存元数据。原始反汇编和探索结果只在忽略local/migration72/analysis。当前没有动态取证需求，因此没有连接调试器或启动游戏；用户的“必要时仅脚本API、禁computer-use/绕过校验”仍是后续边界。

## 实际测试及范围

- 工具正式执行39组，二次完整trace和摘要逐字节一致；三getter及通知继承size/writer另实际执行。
- 6新增聚焦回归通过。研究完整选集为tools/test_client72*.py及test_extract_client72/test_validate_protocol72/test_audit_resource72/test_inventory_protocol/test_review_protocol72_surface，最终**266项、失败0/错误0/跳过0**，包含原非零/64位/嵌套/list/map/error回归。
- 请求：0/空、10000005/10000007、127/128、uint32高位和最大、中文/补充字符/NUL及130字节文本；实际构造→writer→独立wire/protoc、size一致。null实际到异常分配入口，未强制成功。极值/长名只是表示层测试，不代表业务允许。
- 响应：0/1/116/132/-1/int32最小与最大；protoc→原生reader/size→Handler分支验证。116来自目标RetRepeatSetPlayerBornData枚举（客户端按错误处理），132与事件850业务名有目标枚举交叉。
- 重复retcode最后胜出，错误wire类型与未知64位/嵌套/重复字段跳过。三件组无声明64位、嵌套、列表/map/oneof，声明字段测试不适用，不能借未知字段注入宣称这些是新协议字段。
- 4类错误输入×2 reader共8组由显式WireInput拒绝，protoc独立拒绝相同输入。消息reader执行原生，但primitive读取/异常分类来自模型，不冒称目标CodedInputStream真实错误验收。
- 研究映射校验退出0；ready退出1，**204项缺口**。矩阵148行：L1/L2各10V/138U；L3为3V/7C/138U；188字段为41V/9C/138U。只新增三件组与3字段，其他unknown不变，完整P0和传输域仍未验证。
- A-03c矩阵经固定源码/JDK/protoc重新生成，回归再次逐项/全产物复现，47份研究证据输入纳入复核。遗留97构造/148引用种子/1139依赖文件计数不变，不是目标最小必需集。
- 本批没有重跑Java经济/保存回归或完整Java集，没有动数据库；前批221项通过保留历史，完整集既有217失败不能推算为已修复。

本机证据：evidence/born-trace.json、born-trace-repeat.json、born-summary-repeat.json、born-determinism.json、born-mapping-check.json、born-mapping-ready.json、born-final-environment.json；日志logs/born-focused.log、born-repeat.log、born-regression.log。共享摘要born-observations.json、mappings.json、login-proto-gap-matrix/review已提交。

## 原生执行与未完成边界

Offline72执行固定EXE机器指令，不是Windows客户端实机。显式模型包括托管分配/初始化存储、UTF-8/字符串size、wire primitive读取、UI基类/渲染/error/event/success sink和platform=0；未替换validator为true，未修改客户端或绕过校验。无IFix实例为离线环境输入，不是实机热修复兼容结论。

提交前缀运行到真实昵称validator15245A920入口即停止；不执行也不跳过它。成功分支到请求构造的机器码边独立复核，构造另从函数入口使用显式参数执行；不是端到端点击链。默认角色值由目标构造恢复，但SetupView有配置行3覆盖逻辑。通知非默认平台分支、真实UI/SDK/网络调度、请求队列与SDK尾段、成功继续完整函数、实际角色创建与存档仍未验。

这些边界不妨碍Sol按三条明确wire规格实施回归，但不能转称B-02全部/P0/实机验收完成。A-03完整闭包、其他B-02字段、tag965、补丁/资源revision/Lua/场景、D-02等仍是原缺口，本轮没有扩展。

## 弯路与避坑

1. 全局literal没有直接命中三个名称，不能据此否认协议存在。最终沿MonoCharacterSelectScene→选择回调→MonoInLevelNamePage→请求与响应事件定位。
2. string+uint32形状筛出9个候选，仅作索引；最后由目标调用与native数据流锁定29956，不靠旧tag/旧号命名。
3. 请求clone内联CopyFrom，不能套先前Token固定clone调用位置；初次套用旧函数的形状守卫失败，已改为此目标自身声明/类槽校验。没有绕过输入hash或替换客户端指令。
4. 空通知必须有目标声明与继承执行证据；不存在旧Proto不构成空schema。retcode=0成功响应也为零payload，但与通知包号/语义不同；失败响应绝不能沿用BasePacket空消息。
5. 旧avatar_id tag2与旧retcode tag6不可复用。nick_name tag4虽与旧值相同，也已独立从原生writer/protoc核实。
6. 把WireInput的严格错误分类说成客户端实际异常、把长名wire可编码说成通过昵称校验、把UI sink说成完整页面运行，都会超出证据。
7. 最初读取metadata时仅基础表缺offset；正式工具重新提取structures，明确实例字段与父类，不从顺序推tag。

## 下一项可执行工作（由用户交Sol后实施，本轮不启动）

按CLIENT_72_BORN“给Sol的实现与回归规格”接手：固定上述CmdID/Proto字段和独立向量；区分合法wire与合法业务输入；针对新号选人/重复创建幂等/错误不进onLogin/成功持久化与旧号不重选实施业务回归，保留原账号经济保存Guard断言。正式生成/运行集成涉及B-03/C-01，需用户另行给Sol对应范围，本轮禁止且未做。

核心向量：Req(10000005,Aether)=22064165746865725885ade204；Req(uint32.max,x)=22017858ffffffff0f；Rsp132=788401；Rsp116=7874；Rsp-1=78ffffffffffffffffff01。通知/成功Rsp都为空payload但opcode不同。研究Proto/工具可直接作为生成类的独立对照。

## 环境、差异与文档

本机GAME_PATH从当前.env解析并核验三个输入hash；LUNAGC_RUNTIME=portable，.env.local不存在；当前.env/config摘要及进程/端口归属留本机born-final-environment.json，不复制路径/PID到共享文档。收口只读检查Java与YuanShen不在运行，HTTP8088/UDP22101/测试27027无监听，Mongo27017属本工作区。另有两个既有x64dbg进程（一个工作区关联、一个无关联），均未连接/恢复/停止；不推测其调试目标状态。

既有mongodata/、local/data/和测试库目录均未操作。主仓库AGENTS.md原有11行编码/交接规范修改与resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json差异保留未提交；patch无修改。子模块父引用patch f2874103ab70afc56260ee12d1eaca96247933b2、resources dac2a9d43332ef51879522cf39da2831b0ca0543不变。没有推送。

已同步Project、Harness、README、矩阵说明、账号系统/运维；账号数据模型无变化。交接只追加HANDOFF.md，覆盖本文前先完成备份核验。

## 交接备份

旧HANDOFF2已先备份到`local/migration72/handoffs/HANDOFF2.before-born-36eb486.md`，逐字节相等，SHA-256 `7ba3b0d7d41c0423b489e878eeb0dec65ecde648b41fbd5dd5812e4f5bb664c9`。报告`local/migration72/evidence/handoff-born-backup-check.json`。该备份保留A-03c/V-01b/c与更早动态研究入口，不删旧历史；HANDOFF.md持续追加。
