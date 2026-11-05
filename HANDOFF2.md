# 国服7.2详细交接：B-03a/C-01a限定实现完成，取证前停止

更新：2026-11-06（Australia/Sydney）。本次用户指令恢复选人三件组实现，覆盖旧B-02d研究停止点；要求先建卡、实现/回归/构建，遇需新增客户端取证的关键阻塞，做完其余独立项后整理Astra最小项并停止。**限定实现及可独立回归/构建均已完成，已停止新增开发/取证。** 没有派发Astra、创建新chat/子代理、启动服务/客户端或操作调试器。父任务未因局部通过晋级。

## 当前状态与本地提交

| 提交 | 实际内容 |
| --- | --- |
| d2cfb14 | 先建B-03a/C-01a子卡，更新当前授权/验收与停止边界 |
| c6bf035 | B-03a确定性生成器、三份生产Proto/三条包号、发送类、15项独立生产wire测试及规格 |
| 87cda4c | 为扩展Guard中实际复现的/resources路径失败先建V-01d卡 |
| 9fe3bb2 | V-01d只修FurnitureStartFlowTest默认资源路径，保留原配方/扣费断言 |
| 8bd338f | C-01a显式选人、错误/幂等/确认保存、19项真实Mongo业务回归、原登录准入保护、源码审计/矩阵及文档 |

均只本地提交，未推送。代码起点bed2e3c，分支7.2-dev。最后便携JAR内嵌代码提交8bd338f，后续收尾提交只改文档/交接，不需重建。B-03a/C-01a/V-01d限定项已完成；父B-03/C-01仍进行中，A-03/B-02完整闭包、D-01/E-01与D-02实机未完成。B-02e最小研究卡待开始，未执行。

主入口：[CLIENT_72_BORN](docs/CLIENT_72_BORN.md)、[Project](Project.md)、[Harness](Harness.md)。源码/依赖摘要及矩阵在protocol/cn-7.2，原生B-02d证据仍为固定目标限定研究，runtimeStatus全部not-tested。

## 已实现内容

1. `tools/generate_born72_sources.py --client $env:GAME_PATH [--check]`每次核对实际EXE/两metadata SHA-256，以及profile、三层状态/证据、静态CmdID、born原生摘要、字段/别名/冲突。仅生成三份生产Proto及PacketOpcodes三条值，不猜候选、不回退旧号。三Proto/PacketOpcodes固定LF，Windows检出后check不产生换行伪差异。Java只由Gradle/protoc生成。
2. 目标通知5008/PMNMDAIKALO为空schema；请求8541/JNJOMANHPCD为nick_name:string tag4、avatar_id:uint32 tag11；响应414/EODFLMDBPOG为retcode:int32 tag15。移除未引用的旧DDINJLABBFI=5008，未将它当作目标别名。其余遗留包表/Proto未被整体接管，不构成可连接7.2表。
3. 新号进入PICKING_CHARACTER并发送目标通知，等待显式选择。删除NewAccountIntro配置、配置包号覆盖、enabled绕过、默认旅行者及15秒兜底；旧本机JSON同名键不再参与运行。旧号完成态冷读后不重新选人。
4. 请求保持原AccountLoginAdmission。先校验一次旅行者ID、非空白且无控制字符昵称及实际资源；完整客户端昵称validator/长度/过滤规则未恢复，不能把这些基础服务策略说成复刻客户端。重复创建回116，不改名、不加角色/武器/队伍、不重复onLogin/欢迎。ACTIVE重复请求经路由到达幂等拒绝。
5. 除明确重复116外，基本输入/资源拒绝、解析或保存失败回-1通用非零分支，不借用未验证命名枚举。132只作目标独立wire/错误分支验证，不将所有内部错误强映射为屏蔽词。非零响应真实编码tag15，不能用空BasePacket代替。成功响应retcode0与通知都为空payload，但opcode不同。
6. 创建沿原Avatar/初始武器/队伍业务流程；用正式Item Guard、Avatar同步确认及Player Guard确认最终数据后才继续onLogin。保存失败发送非零、关闭会话，不在半成品内存上重试。实际故障注入证明Avatar/武器可先保存而Player未确认；冷读角色数与mainCharacterId完成态不一致时拒绝登录，保留数据核对，不清库或自动修复。

没有新持久字段/集合，没有重写账号、经济或Guard算法。多集合不是事务；失败后不承诺回滚或欢迎邮件在异常重启后恰好一次。写后丢确认可能已持久化，重登须按真实冷读状态判断。

## 实际测试与复现

- 首轮聚焦66项Java全部通过（18业务+33原登录准入+15wire）；补充半成品冷读后业务为19项。
- 最终21套**495项Java，失败0/错误0/跳过0**。覆盖19业务、15独立wire、原33项登录准入，以及Account创建/UID预留、Account/Player/Item/Activity版本保护/恢复、写队列/屏障/栅栏和原221项家具/加工/手册经济保存选集。完整Java集没有重跑，历史217失败不据此扣减。
- `BornDataFlowMongoTest`通过真实隔离Mongo和正式Player/Item Guard验证两旅行者×两任务模式、显式选择、重复幂等、错误不登录、凭据轮换、成功冷读、旧号不重选、保存前失败/写后丢确认及半成品冷读。
- **onLogin和欢迎邮件仅是明确继续入口观察点**，不是完整初始化或实际邮件落库测试；网络为假KCP tunnel，没有服务端socket/客户端连接。不能用这19项宣称实机新旧号登录/世界入场通过。
- 15项生产wire测试以B-02d原生/protoc十六进制向量为独立来源，包含uint32最大值、Unicode/NUL、正负int32、重复/未知/错误wire、非法UTF-8、空通知/成功响应不同完整帧。三件组无声明64位/嵌套/list，未知字段只验证跳过，复杂消息旧研究回归保留。
- 首次扩展467项中1项失败，为FurnitureStartFlowTest默认/resources路径。保留失败快照，V-01d仅调整到LUNAGC_TEST_RESOURCES优先、其次LUNAGC_RESOURCES_DIR、缺省当前resources；1396合法配方和非法/扣费/容量断言不删不跳过。修后该31项及关联110项通过，再跑最终495全过。FurnitureTalentPlanTest等其他V-01范围未扩展。
- **272项研究工具回归通过**：tools/test_client72*.py加test_extract_client72/test_validate_protocol72/test_audit_resource72/test_inventory_protocol/test_review_protocol72_surface/test_generate_born72_sources。研究校验退出0；ready仍退出1，204缺口。
- 首次生成/再次check的四产物hash一致；6生成+6原born工具测试通过。研究矩阵两次完整生成相同。旧schema从d2cfb14取出后实际用protoc解码目标向量，证明旧avatar tag2/retcode tag6读不到目标业务字段；没有切换工作区源码来复现。

本机证据统一在忽略的local/migration72/：

| 类型 | 位置 |
| --- | --- |
| 聚焦/修后/最终Java XML | born-focused-test-results/、born-changed-test-results/、born-guard-final-test-results/ |
| 首次失败快照 | born-guard-before-test-results/、logs/born-guard-regression.log |
| 最终495及研究272 | evidence/born-guard-final-summary.json、logs/born-guard-final.log、logs/born-research-regression.log |
| 生成与旧schema复现 | evidence/born-generation-{first,check}.json、born-legacy-parser-reproduction.json；logs/born-generation-tests.log |
| 源码/矩阵 | evidence/born-{source-surface-review,login-source,login-dependencies,matrix-repeat}.json；logs/born-source-regression.log |
| 映射闸门 | evidence/born-implementation-mapping-{check,ready}.json |
| 构建与环境 | evidence/born-build-check.json、born-implementation-{environment,final-environment}.json；logs/born-portable-build.log |

研究/源码复现仍须按Harness从当前.env解析GAME_PATH和便携JDK/protoc，不复制旧机器路径或PID。Mongo测试先显式设置独立LUNAGC_TEST_MONGO_URI和需要的其他测试变量，不指向正式库；本轮只用一台独立测试实例，不能宣称验证两台独立实例。

## 源码审计与文档状态

为生产改动重新人工复核已删除的fallback边、选人player.onLogin选择器及对应hash，未放松漂移门禁：

- A-03a当前12源码/9根/81方法、95边（12人工、83候选）、1000未关联；62直接构造类型/63总类型。
- A-03b当前48源码/294方法、408边（71人工、337候选）、2856未关联；10开放锚点保持open。
- A-03c矩阵当前99构造、148引用/种子、1140描述符文件、1254声明/4786字段；1287源码/Proto与47研究输入固定hash。目标空通知正式接管后missingProtoRoots为空；不是缺文件就造空message。仍不声称最小/完整P0。
- 三层矩阵及188字段状态不变：L1/L2各10V/138U；L3为3V/7C/138U，字段41V/9C/138U。5组静态重号/传输域未知/runtime未验保留；空schema已独立确认的Notify不再错误标成无字段证据。
- 动态来源因移除两处配置包号减为15处/8组/40固定来源；当前3272常量、323 Handler/227启用正值/0静态冲突、626发送位置、2804 Proto。全域注册覆盖、SendPacketEvent最终包、FanOut、replay、UnionCmd/枚举等仍归父C-01，不因静态0冲突通过运行验收。

已同步Project、Harness、README、CLIENT_72_BORN、账号系统/数据模型/运维、源码索引/管理器依赖/Proto矩阵/协议盘点、protocol README及复核摘要。旧数值/停止段落按历史保留，当前入口明确标注新状态。

## 构建、环境与未部署原因

代码提交8bd338f后，`tools/local_windows.ps1 -Mode build`退出0，便携JDK17、Gradle/protoc及JAR构建成功。产物仍名LunaGC-7.1.0.jar，版本准入未修改，不能称为7.2可连接构建。SHA-256：`3a05ca88115f93a46bbb0ca334f54241f560a291fbb5b05fa2d42bb0f87aa522`。JAR内10个改动/生成类与build/classes逐字节匹配，BuildConfig包含8bd338f；旧NewAccountIntro配置类与scheduleFallback/createDefaultTraveler方法不存在。旧JAR已保存在忽略目录。

**没有部署或启动服务/实机。** 完整成功继续/真实必需首包和P0闭包、D-01实际SDK/本地信任/补丁加载、E-01资源revision/Lua/场景均未齐。最终无Java/游戏进程，HTTP TCP8088、游戏UDP22101、测试TCP27027无监听；健康及运行JAR hash没有运行对象，不标为通过。原工作区Mongo27017保留；测试实例启动前核对空端口、新独立dbpath，结束时核对该dbpath及只剩admin/config/local后通过管理命令正常关闭，测试数据目录保留。

.env的LUNAGC_RUNTIME=portable，.env.local不存在；GAME_PATH/配置/子模块/进程与端口已重新核对，敏感路径/配置只在本机报告。mongodata/、local/data/没有修改或清理；没有操作任何客户端、补丁、资源或既有调试器。

主仓库AGENTS.md原有11行编码/交接规范修改、resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json原差异均保留未提交；不覆盖、不归入本批。子模块引用patch f2874103ab70afc56260ee12d1eaca96247933b2、resources dac2a9d43332ef51879522cf39da2831b0ca0543不变，patch无修改。全部提交仅本地。

## 当前阻塞与Astra最小研究项

B-02e待开始，用户指定遇关键取证缺口后停止，本批未执行/派发：

1. 固定CLIENT_72_BORN三输入hash。由已核实SetPlayerBornDataRsp Handler的0分支进入14CD68860，恢复完整成功继续函数的状态变化、真实发送和调用依赖。先做可离线验证的部分；模型边界、IFix/SDK前置保持显式。
2. 新号与已有隔离测试号的受控目标证据，核对PlayerLoginRsp、DoSetPlayerBornDataNotify、成功Rsp和**第一批必需**初始化/场景消息的次序与门控。不能把本服务当前发送顺序当目标规格，也不能把源码全部通知直接认作客户端必需集。
3. 只对实际必需的新消息闭合CmdID→类型、类型→语义、字段→wire，带独立非零/错误/边界样本。若仍缺真实SDK/补丁/收帧调度，记录最早阻塞入口与解除条件即停，不扩大所有玩法，不猜号/空schema/枚举。

现有495项只能验证服务器业务/保存不变量，不能决定上述客户端顺序和完整P0；ready204缺口正是不能启动本构建的证据。完整昵称validator亦尚未执行，基础服务昵称政策不构成完整兼容结论。Astra应在前置允许时按脚本API/固定目标取证，历史禁止computer-use、绕过校验或沿用旧暂停地址仍有效；最后游戏目标已退出，调试器状态不得据旧记录推断。

## 弯路与避坑

- 旧空BasePacket把所有响应伪成成功；retcode0为空不代表非零也为空。负int32须10字节varint，不能用sint32/zigzag。
- 旧tag2/avatar、tag6/retcode及旧26105请求不再是本三件组默认值；5008旧未引用常量也不是目标别名。目标空通知必须追溯声明/继承原生执行证据。
- 生成成功、同schema往返、完整Java编译、健康或静态0冲突均不能替代独立wire和客户端验收。本轮JAR根本没有运行，不制造运行hash/健康结果。
- 保存为多个集合，故障后先冷读，不能声称错误自动回滚或重发欢迎恰好一次；不在失败对象上重试，不清理半成品数据来绿测试。
- 首次Guard失败实际是测试默认/resources路径；通过新资源路径重跑原断言，不能跳过实际1396配方或删冷读验证。完整V-01仍未验收。
- 源码hash变了需更新人工调用边/父hash再重生成，不能仅改统计常量冒称复核。本批删除fallback边、更新选择器、复核两处配置消失并保留所有开放锚点；候选不晋级。

## 交接备份

覆盖本文之前，旧HANDOFF2已逐字节备份至`local/migration72/handoffs/HANDOFF2.before-born-implementation-8bd338f.md`，SHA-256 `8254eae519d5610454c670e00c4b6d49e1f1c0f2836ec95d12002769c1dfc086`，核验byteEqual=true；报告`local/migration72/evidence/handoff-born-implementation-backup-check.json`。旧B-02d及更早A-03c/V-01交接链保留于该备份和更早备份中。HANDOFF.md本轮只追加，不删除历史。
