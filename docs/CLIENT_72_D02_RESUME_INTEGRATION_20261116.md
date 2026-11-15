# D-02 恢复集成活动检查点

本轮任务 `D02-RESUME-INTEGRATION-20261116B`，接手 HEAD `99a868d2c58730dd1f39a7f3d5dbee1d62eb57b4`。用户已明确恢复D-02，持续推进必要独立研究及成熟合同集成；旧暂停与前轮条件2不是本轮停止依据。`D02-INCR-PLAN-20261116`的[中文增量计划](CLIENT_72_D02_INCREMENTAL_REUSE_PLAN_20261116.md)及两组有限公开检索已验收；B本地有界关联已完成、实际入口阻塞，A/Relic自主研究继续。R1/R2/R3必要实现出口仍阻塞，D-02 / F-05 / D02-R6仍0/6，R4/R5/R6待开始，A/B、preview与准确依赖继续冻结。当前任务与验收见[Project](../Project.md)。

## 本轮来源与自主研究出口

| 子项 | 本轮已验出口 | 当前必要缺口与下一动作 |
| --- | --- | --- |
| A1 `D02-INCR-A-MAX-20261116` | [A组有界公开检索](CLIENT_72_D02_INCREMENTAL_REUSE_A_20261116.md)已完成，未给新的目标MAX cap职责桥；代码接口401不记源码零命中 | 同target88602字段/getter到24887 enum77/MAX_SPECIAL的独立值流仍缺；Sol max自主关联进行中，不以无直接同owner排除间接消费，不读取候选值选cap |
| A2 `D02-INCR-A-RELIC-20261116` | 公开出口已完成；Jetty6.2 JSON只作参考。后继[Codex边界/source](CLIENT_72_D02_RELIC_CODEX_SOURCE_20261116.md)两项来源出口已完成：唯一21946.m168869/真实74316参数，完整2792B/632条/259refs | 同target46431/74316字段的等级/套装用途仍未闭；准确CALL`14c3fa784→1405336c0`保持A冻结、整窗semanticfalse。不依赖其返回的前置交Sol继续有界分析，不当完整rank/set或资源合同 |
| B1 `D02-INCR-B-MATERIAL-20261116` | [公开检索](CLIENT_72_D02_INCREMENTAL_REUSE_B_20261116.md)及[本地有界关联](CLIENT_72_D02_INCREMENTAL_LOCAL_B_20261116.md)已完成；首次read的O仍消费未知B，后台创建a=0不消O；post-parser覆盖+18的清理不支配首读 | 实际入口阻塞：需首refill前同R的有效B定义、backend与固定m_bytes物理起点，或独立目标revision文件语法。准确pool/getter→A返回依赖保留，不泛封Material；12混淆声明未选出有用新窗，无合格现场包 |
| B2 `D02-INCR-B-WEAPON-20261116` | 公开及本地有界关联已完成；288组三入口同reader、raw/base同row关联成立，但记录不含内部cursor/caller/return stack | 实际F入口阻塞：需同reader/同row的`P_wrapper→P_raw→P_F`与物理边界/补读关系的新允许独立来源或准确记录。旧wrapper/raw body仍属已停路径；不以[4,400)、String尾、平铺schema或11101值补差，不扩11201 |

A组报告SHA-256 `984d8d1a9f11cc1afc2ae2a09f383c64237cb13c7265a8677c6ea12078cf7228`；集成只读核对7份manifest、7个保存文本的SHA/blob/tree、固定commit/tree和6.2版本说明，均匹配。私有核对`local/migration72/d02-resume-20261116b/a-public-integration-review.private.json` SHA-256 `2ea8ad7b120eef453af5d4536793b4a72430d6d47e0f0373e78ff28acbaf7334`。没有重发查询、运行上游、测试或运行变更；有限公开出口不升级字段、资源ready、R1/R2/R3或六项实机。

B组报告SHA-256 `9497b02788d53fded16786ae89e8d0e5cc1ca28f9da6c1f5d19a88a3d8a861d2`；集成只读核7份manifest、10个保存文本SHA/blob及3.5 commit/tree/README身份均匹配，核对`local/migration72/d02-resume-20261116b/b-public-integration-review.private.json` SHA-256 `69f4e1f67ccf46e0bc923729a516d18d01c675631ac039fb884e35ba76b3cc4f`。原报告partial clone首次对象存在性查询自动补取pack的偏差保留，非安全拒绝；本次不重发查询、不运行上游/测试。旧版本语法、401及未覆盖均不转为目标合同或来源穷尽。

`D02-INCR-A2-CODEX-BOUNDARY-20261116` / `D02-INCR-A2-CODEX-SOURCE-20261116`的result SHA分别`8d17e8d2379690f8f25e3583ca619332dc65f5c1c8bf6402ee7bef60e11fa841` / `103c75c3894aebbfad1c9528de599884e379087b32070fbf0399512b89e16a9f`，完整body SHA`3fd04ec7192e2347533827703f673cf834ef5b13963b7b452c791de9953df284`，集成只读核对匹配。边界阶段body0，后继固定PE同句柄一次2792B；无新header/AOT/目录/callee/global/xdata或目标执行。首次ast NameError在PE/hash读取前停止，run2只补绑定；REA Windows不支持后的固定helper回退及原失败均保留，详见来源专题。仅闭准确来源，不升级语义、rank/set、父项或六项实机。

`D02-INCR-B-LOCAL-RESEARCH-20261116`的[报告](CLIENT_72_D02_INCREMENTAL_LOCAL_B_20261116.md)SHA `db9a44443d7054527bb06be183e353af4dd903653799d1b9a4928860cb60c2d3`已验收有界结论；8既存body4182B/1070条重拼一致，另3个旧wrapper raw共1100B只核hash。集成只读核6份manifest/关键结果身份全匹配，回执`local/migration72/d02-resume-20261116b/b-local-integration-identities.private.json` SHA`44277961873457c2e6a3f283558bf4eab3b5714fecf5cf9e995ef613829309f9`。原typeReferences字典误作list的首失败与修正run2均保留；未重跑研究脚本/原源/测试。B1/B2没有成熟header、F入口或新必要字段规格，按报告最小输入表继续，不要求用户重复启动；A/Relic仍执行，未作最终收口或交接。

## 2026-11-16B 当前接手与规划验收

实际HEAD及空索引已核，patch/resources的gitlink与各自HEAD分别保持`50f9bf82b3dbc0d4857321417dccf40f78c13ce4` / `dac2a9d43332ef51879522cf39da2831b0ca0543`。既存用户AGENTS、patch三类差异、resources单文件差异及两份未跟踪E8草稿均保留不提交；11个受保护文件与前轮指纹一致。最小完成回执只核当前输入与保护差异，旧11项已验产物回执直接复用，不报告新的产物hash验收或测试。

当前`.env`为portable、HTTP8088/game22101，`.env.local`不存在，根config v14/HYBRID、两Mongo URI端口27017；资源/data目录存在。`GAME_PATH`的config声明7.2.0/channel1/sub_channel1，五固定输入均逐文件SHA-256匹配profile。相关Java/Mongo/客户端/调试器进程0，TCP8088/22101/27017/27027与UDP22101监听0，`local/running.json`存在但没有运行JAR/健康验收对象。源码仍为InstanceRoot Mongo27027、根Mongo27017与patch HTTP8088，不据环境变量宣称全端口隔离。

私有最小接手回执`local/migration72/d02-resume-20261116b/preflight-minimal.private.json` SHA-256 `897b0c5c61135715b8daa121fad1c105a32f3ea913192b319ba22b17ec49432e`，独立进程端口回执`process-ports.private.json` SHA-256 `946f51b0b0ea4a0240869fa9549c09c237e4261f300e68a9d6505cd664b00d01`。初始盘点包装器停止及后续准确范围在[Harness](../Harness.md)留索引；无客户端、computer-use、JVM/数据库、构建、Proto生成或部署。

计划冻结版SHA-256 `50abc5bdd26c6c8c9c80ec00e60938ba0240834e02ac41e13f805dfa510cce69`，四个出口分别为`D02-INCR-A-MAX-20261116`、`D02-INCR-A-RELIC-20261116`、`D02-INCR-B-MATERIAL-20261116`与`D02-INCR-B-WEAPON-20261116`，两组Terra max已经启动。计划覆盖README/相关Fork/分支/精确代码查询，以及公开无桥后可独立的既存来源新关联；目前没有未入计划但已成熟的必要实施合同。新来源先证明同目标与独立性，一个最小真实样本成立后再扩必要集合及受影响加载/保存，不能用计划或命中替代字段验收。本文下方为前轮`D02-RESUME-INTEGRATION-20261116`（接手`420002e`、收口`c0e3f16`）实证与历史方向，前轮条件2仅保留其当时来源范围。

## 接手核对与已验复用

本轮只读核对：`.env` 为portable、HTTP 8088/game 22101，`GAME_PATH`目录、exe与config存在；`.env.local`不存在。根配置v14/HYBRID，两服务回环地址、两Mongo URI均27017，资源及data目录存在。匹配Java/Mongo/客户端/调试器进程0，TCP 8088/22101/27017/27027与UDP 22101监听0。`local/running.json`存在，但无实际运行进程，不能据此声称健康或运行JAR已验。源码 `tools/local_windows.ps1:57–59` 明确HTTP/game默认8088/22101、InstanceRoot Mongo固定27027；patch HTTP/WinHTTP仍固定8088，未声称全端口隔离。

11个既验产物的实际SHA-256与专题一致：121 Avatar base/growth及critical投影、两个Java completion、121 Depot三键、Talent规范报告、phase6、Float断言集成、启动闭环及cold现场/审阅回执。仅核文件完整性，未重跑测试/native/JVM或把最终critical driver当作已native重跑。Float/Pair、启动修复与唯一cold结果继续复用；完整资源及同旧号恢复未因核hash晋级。

私有盘点为 `local/migration72/d02-resume-20261116/integration-resume-20261116/preflight.private.json`，SHA-256 `ac5e0f2b38775c98da5597edabbf2ad4f0c1a4f557d1816d39f5f05e69c0396e`。独立审阅 `debug/d02-resume-independent-20261116/summary.md` 的10个文件hash直接复用，其结论只覆盖既有本地来源。没有客户端启动、computer-use、数据库/实例写入、构建、Proto生成或部署；R1/R2/R3必要合同未齐。

保护范围：用户`AGENTS.md`；patch的`Cargo.toml`及未验`examples/sdk72_service_trigger_proxy.rs`/同名目录；resources的`ScriptSceneData/flat.luas.scenes.full_globals.lua.json`；两份未跟踪E8草稿。11个文件指纹留私有盘点。patch与resources gitlink仍分别为`50f9bf82b3dbc0d4857321417dccf40f78c13ce4`和`dac2a9d43332ef51879522cf39da2831b0ca0543`；不暂存这些差异。

## 必要项与当前动作

| 必要出口 | 准确缺输入 | 已有覆盖 / 本轮动作 |
| --- | --- | --- |
| R1完整Avatar与g2正式发送 | f4d2完整20分配/12根、自然String tuple/caller/trigger与TLS/GC所有权、enum/cold普通10294及同源Store/SavedVitals仍未闭 | A第二allocator返回/构造及其准确依赖冻结；其他R1来源不泛封。121 Avatar七键与限定loader直接复用，g2待完整必要合同，不以本轮Skill字段代验 |
| Skill/energy → Depot实际加载与旧号属性恢复 | 121行costElemVal和specialEnergyMax/START均已闭；specialEnergyMin/MAX cap目标字段职责桥及实际maximum采用仍缺准确来源，影响Avatar.setCurrentEnergy/recalcStats | [367本轮+2旧片段](CLIENT_72_D02_SKILL_ELEMENT_HOST_BATCH_20261116.md)和[真实369Skill→121Depot Java](CLIENT_72_D02_OLD121_SKILL_DEPOT_JAVA_20261116.md)已通过，120新正向energy绑定解除；不等raw-id/reader，不建Player或假cost。[121普通cost字段](CLIENT_72_D02_SKILL_ORDINARY_COST_20261116.md)已实际闭合，[START121](CLIENT_72_D02_SKILL_SPECIAL_START_20261116.md)亦已闭，[真实cost/START-aware Java](CLIENT_72_D02_OLD121_SKILL_ENERGY_JAVA_20261116.md)已通过，MAX cap另验；ctor缺边界单窗不加作已闭出口门 |
| Weapon/Material/Relic必要行 | Weapon其他238种/1636行的独立跨行host cursor/framing、key/span及六字段presence/转换/数组边界合同；Material有效backend+18起点/首refill与实际row语法/keyspan/cursor（94 literal与准确payload已闭）；Relic rank/set具名来源或同源输入+非零oracle排除标签互换 | 11101既验片段保留；[Material literal](CLIENT_72_D02_MATERIAL_SPECIAL_ENUM_CONSTANTS_20261116.md)与[准确payload](CLIENT_72_D02_MATERIAL_EXACT_PAYLOAD_20261116.md)已闭，Material最小row语法另研究，Relic缺准确具名consumer不泛扫18方法；Weapon固定raw、11101 span/六字段职责可复用，冻结仅已知旧native ctor/parser A构造返回路径；潜在纯host合同尚未准入，不泛封整族 |
| Proud实际资源/当前采用 | 目标字段到共享Pair的准确桥、实际row/M、非空cache/key、同epoch及独立值 | 148B共享消费正例、Float条件合同已验；不重复SDK页采样，不创建Default/cache；普通新来源尚未查尽 |
| Scene/Team/Entity必要合同 | M1 producer→同次+5d60；M2同receiver +f0/返回R；M3首次消费前R+18及writer顺序；M4同R release/reacquire/reset | 238B工厂、writer/getter来源复用；本地暂无新输入。World slot114及getter/Recreate准确A返回依赖冻结，普通M1–M4来源问题保留 |
| SDK自然连接与认证 | 新的固定允许来源闭合Init/env→route采用→认证/完整校验及会话交换 | map/HTTP/key局部结果复用；B组合loader及其依赖冻结，未认定全部SDK都依赖B；本批限定公开检索已完成但未给目标自然链；未覆盖范围只按下一轮具体缺口增量处理 |
| R4→R5→R6 | 上述必要合同、最终发送/原消费者顺序、同旧号全121角色和实际物品装备postLoad/recalc/save | 无成熟运行包，暂不部署。成熟最小合同回传后由唯一集成者实施、相关验证、本地提交；R4充分后再portable隔离部署与六项实机 |

当前停条件需分项判断：已知冻结的是上述准确A/B边及其返回/构造/组合现场依赖；Proud自然非空cache/key与Scene M1–M4独立输入缺失，当前没有有用的新同页采样。普通来源缺口包括特殊MAX cap字段职责桥与Relic rank/set具名职责，有限查询未给准确新入口不等于所有本机或外部来源不存在。Material准确payload/literal、[569B来源与独立首A前标量、239B同cursor槽清零](CLIENT_72_D02_MATERIAL_HEADER_SOURCE_20261116.md)均已验，实际file位置仍缺有效backend+18起点/首refill或独立文件语法来源；不读取条件P=0的header4B。[Relic27候选typed/祖先/真实参数](CLIENT_72_D02_RELIC_TYPED_CANDIDATES_20261116.md)有限核查已完成，无准确资源角色入口。[shared String helper来源/normal-tail独立N字节推进](CLIENT_72_D02_SHARED_STRING_CURSOR_SOURCE_20261116.md)现已闭，准确A返回/构造仍停；早先Skill id窗取消不是拒绝。Weapon最后[单字段host依赖判断](CLIENT_72_D02_SHARED_STRING_CURSOR_SOURCE_20261116.md)已完成：wrapper span非raw父F入口、11201非必要cohort，缺独立wrapper→raw父cursor/语法桥，P_tail不升P_entry；没有成熟新必需字段合同。Material+18/filebegin不因此闭合。主线据各准确来源缺口及冻结依赖审定条件2等待，未把有限查询解释为全网/全机不存在。

第5节六项缺口仍分别为：旧号自然本服登录/归属；新号显式选人命名/确认保存；完整初始化与握手；角色可见落地；连续10分钟心跳；两类账号正常退出重登及受控进度保存。没有以SDK页面、局部Java、hash或服务健康代验。

## 准确停止边界与文档处理

A第二allocator返回/构造、热Default/sameepoch、`151841361`及准确tail/caller依赖，B首Cargo类型错误修复及组合loader/owned/装载/自然现场依赖、preview专项冻结均保持。A原平台error已找回；1528字符opaque事件关联不恢复执行正文，原明文验收已撤回，仍缺可读正文与固定技术输入。原提示、停止点和最小候选见[队列](CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md)与[纠错](CLIENT_72_D02_A_PROMPT_PROVENANCE_CORRECTION_20261116.md)，不重试或自动派Daybreak，不扩大成整族/所有独立工作禁止。

Project/Harness/HANDOFF/HANDOFF2已先备份至本批忽略目录`integration-resume-20261116/before/`，四件字节/hash匹配；原HANDOFF2 SHA-256 `cabe165b17c6375a2d9ba3e745588b557bdaf88632aa63a1e7d385d436ea9c2f`。接手阶段先纠正当前授权和过时首发排期；随后独立实施及相关验收已按上表追加，历史事实、完成标记、拒绝/未知保留，HANDOFF只追加。接手核对及后继独立实现/相关Java/文档提交均已完成；本轮按条件2等待准确输入，后续成熟包从对应最小缺口恢复。仅文档/离线工具/测试变更，无需重启。

## 前轮方向微调（历史）：只排下一轮，不新增技术验收

以`c0e3f16`为复核基线，两次Java completion与JUnit原件已只读核对，未重跑。369 id/element、121cost/START及energy绑定不再排开发；特殊MAX cap职责、Material/Weapon独立入口、Relic角色映射为资源首缺，完整Avatar/SDK/Scene/Proud仍有各自必要缺口。

既有公开报告明确未做全局代码搜索，YSRes/DataParser相关Fork和非默认分支也未穷尽。因此将UP-72-N1/N2的下一轮增量列待开始：Sol max用已有报告制定一份中文有界计划，Terra max分两组核具体字段职责和文件入口的相关候选，README/版本命中才读源码；固定候选先复用，不重爬旧范围。只有新来源满足独立性和目标连接时交Sol/Terra实现；没有命中则保留实际覆盖与准确缺输入，不能从旧版schema、候选相等或默认值补目标语义。

两组可并行，R1/R2/Scene/Proud仅新输入到达时前移；共享Git/构建/JVM/DB串行。当前没有新现场包，无需用户启动客户端；后续明确实验准备好再取实时ready。原A/B及准确依赖不解除，也不自动派Daybreak。本文新增部分只是规划，上一轮条件2及全部原始证据保持其历史范围。
