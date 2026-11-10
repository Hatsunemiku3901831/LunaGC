# 国服 7.2 任务规划与验收

任务证据基线：2026-11-11；本批起始HEAD `a38aef7`；本批起点见[恢复执行批次B](docs/CLIENT_72_D02_RESUME_20261110B.md)。仅面向国服 7.2，Windows portable。

## 当前阶段

**已按用户最新指令恢复持续并行执行，上一轮“收尾后暂停”调度限制解除；D-02 / F-05 / D02-R6仍未完成。** 八点SDK同配置阶段、锁内map、stock v2候选及出生默认装备局部恢复直接复用。当前关键路径为首pop之后的完整Avatar分配/Default、准确SDK请求采用与认证、Scene生命周期，以及选定旧号和必要资源/完整恢复；成熟后进入正式发送、R4汇合、R5 portable隔离部署及R6六项实机。当前没有已准备完毕的用户操作，旧ready全部已消费。技术门禁、未知及缺省语义保持，恢复盘点、包边界与实机插入条件见[本批计划](docs/CLIENT_72_D02_RESUME_20261110B.md)；HANDOFF2的暂停为上一轮历史。

本文件维护目标、任务设计、依赖、发布顺序、任务状态、完成标记和验收结论。方法/复现索引放 [Harness](Harness.md)，详细证据与失败分析放对应专题，原始日志/进程/路径/凭据留忽略目录；不再向这里追加逐日进展、测试流水、JAR/PID 或暂停历史。旧版全文见[只读历史快照](Project.archive-2026-11-08.md)，旧快照不作为当前执行指令。

## 1. 目标、范围与完成规则

- D-02：独立测试环境中新旧号登录、新号显式选人/命名、完整初始化/场景握手、角色实际进入世界、连续心跳、两类账号退出重登及归属/保存正确。
- D-02只迁移入场必要依赖。完整战斗、商城、深渊、全地图及7.2新增内容归E-02/E-03/E-04；被入场真实调用的同类资源或通知仍属P0，不因功能名而删掉。
- 状态仅用：待开始 / 进行中 / 阻塞 / 已完成。子项完成只覆盖列明范围，不能代替父项或实机通过。已完成项保留ID与证据入口，后续只增加明确的新缺口，不重做整项。
- **任务状态和完成标记持续在本文件维护。** 新任务缺省为“待开始”，没有验收结论不能标为“已完成”；完成后及时更新对应状态、已验范围和证据链接，不能只在Harness、专题或历史快照登记。已标出的缺口、缺省/省略语义及未知/未验标记保留，只有新证据支持时才更新。
- CmdID→类型、类型→语义、字段→wire分别验收；未知保持未知。固定输入、数据保护、生成Java及本地提交等执行规范见 [AGENTS](AGENTS.md)。不改ready/required、不绕原生校验、不用旧号或空对象代替缺证据。
- 不按研究卡、字段或测试数量计算完成率。统一矩阵与原生片段用于证明部署前合同，完整运行顺序和客户端效果必须由最终实测反核。

<a id="d02-route"></a>

## 2. D-02阶段与依赖

| 阶段 | 状态 | 已有基础与剩余目标 | 阶段验收出口 |
| --- | --- | --- | --- |
| D02-R0 入场依赖台账 | 已完成 | 已登记源码候选、管理器/事件/脚本开放边界；运行闭包继续由R4/R6更新 | 新旧号路径及必需依赖有来源，未知未被标成可省略；[台账](docs/CLIENT_72_D02_ENTRY_DEPENDENCIES.md) |
| D02-R1 角色与场景初始化 | 进行中 | raw适配器、类/方法后继、warm输入及有限Java投影可复用；enum/String、完整Avatar构造、g2和Scene/Team/Entity未闭 | 同源角色/库存/资源→真实Java发送→目标消费者及必要状态/顺序闭合，未知不参与发送 |
| D02-R2 SDK与连接前置 | 进行中 | 日志/响应修复、真实HTTP往返、key桥接/装载及准确caller到达已验限定范围；SDK分类、自然Init/env/route/认证与完整校验未闭 | 正确认证/错误拒绝、会话交换、dispatch/密钥和必需补丁合同成立；实际装载/本服连接在R5/R6复核 |
| D02-R3 最小资源与保存恢复 | 进行中 | 真实资源/曲线/突破、出生HP/critical/装备/Gadget查找键、自然首诊、303局部正向Manager恢复及出生默认装备局部保存/冷绑定已验；完整行、配置/ability、revision及入场全部必要加载/恢复未闭 | 必需资源/引用/脚本实际加载有效；保存→新Player→真实manager恢复成立，不能手设loadedAll |
| D02-R4 P0部署前汇合 | 待开始 | 依赖R1/R2/R3的必要合同；C-01b网络边界与既有回归直接复用 | 必经请求/响应/通知、嵌套、聚合、顺序、资源与最终发包一致；生成、相关回归和构建通过，未验runtime继续保留 |
| D02-R5 隔离部署 | 待开始 | 依赖R4；InstanceRoot入口已验，真实实例未部署 | 独立配置/data/DB/资源，日志/健康/TCP/UDP/PID/运行JAR SHA及实际DLL/信任一致；[部署手册](docs/CODEX_ONE_CLICK_DEPLOY.md) |
| D02-R6 客户端验收（D-02 / F-05） | 待开始 | 依赖R5；原SDK登录页不等于本服登录 | 第5节每项通过并记录范围；失败需修复、回归和正常重部署后复验 |

依赖关系：`R0 → {R1, R2, R3} → R4 → R5 → R6`。三条前置线共享数据合同，但不能用环形等待搁置独立实现：已完成SDK修复直接复用；输入明确的loadAll首诊不等待完整Avatar，资源字段仍与实际消费者相互核实。部署前证明合同，部署后验证真实连接，不能要求先实机成功才允许部署。

## 3. 并行执行路线与派工

本节保留本批恢复执行时的任务ID、范围、模型、依赖和文件归属，已完成前置统一在第6节维护，不重新排队。当前恢复执行；3.2及3.4按实际已完成缺口推进，3.3列本批唯一写入归属。批次号和A/B/C仅为调度标签。

<a id="d02-parallel"></a>

### 3.1 模型与责任

| 角色 | 指定模型 / 推理档位 | 责任与交付 |
| --- | --- | --- |
| 主线 / 日常开发 | GPT-6.1 Sol xhigh（`gpt-6.1-sol` / `xhigh`） | 规划、明确实现、集成、定向回归、现场、验收与提交；先确认输入和文件归属，再发布任务 |
| 难点分析子代理 | GPT-6.1 Sol max（`gpt-6.1-sol` / `max`） | 证据冲突、协议/ABI、调用链、零槽/空usage/分类失败及已复现但原因未明的卡点；交明确结论、独立证据、最小实现规格或准确缺输入 |
| 有界执行子代理 | GPT-6.1 Terra（`gpt-6.1-terra`，档位按任务指定） | 规格完整的清单/数据对照、受控转换、driver、测试准备、机械修改和文档整理；未知语义返回主线，不自行猜测 |
| 最后升级，非日常排班 | GPT-6 Astra max（`gpt-6-astra` / `max`） | 仅主线和Sol max实质分析仍不能解决、且影响D-02关键路径时使用；须先说明已排除假设、升级理由、准确问题和返回条件。缺样本、缺现场或未读既有证据不构成升级理由 |

起步采用**主线＋A/B两个Sol max子代理＋C一个Terra子代理**，以实际工具并发上限为准。C的输入/规格由主线Sol xhigh先定；若仍需设计，由主线处理或将C改派Sol xhigh，不直接让Terra探索未知。任务完成或阻塞就释放槽位；复杂问题消解后转Sol xhigh/Terra实现，不让max长期承担机械工作。

显式传入工具支持的模型与档位；模型不可用应如实返回主线调整，不能静默升级Astra或冒称已使用指定模型。子代理不得自行扩大派遣和修改范围。

### 3.2 批次路线与退出条件

| 批次 | 既有任务 / 本批动作 | 并行安排 | 退出条件与推荐模型 |
| --- | --- | --- | --- |
| 0 恢复盘点 | 复用R0及全部已验前置，重核HEAD/差异、输入、配置、现场；固定本批各分支来源与文件清单 | 主线准备后发布A/B/C，不重做历史全量研究 | 主线Sol xhigh；各任务输入、责任文件、可复现证据入口与验收出口明确 |
| 1 复用已验成果推进后继 | A：stock v2 oldFlags8必经GC helper及完整构造依赖；B：选择语义→准确请求采用/触发；C：所选旧号实际存档依赖及明确资源/driver；Scene优先承接释放max槽 | A/B Sol max分析、C Terra xhigh有界实现，见3.3；明确实现转Sol xhigh，主线串行构建/JVM | 解除实际依赖或交准确最小缺输入；准确请求消费者/边界及实现规格；旧号必要引用/完整恢复准备。八点、锁内map、stock v2、Promote/Curve、出生装备局部恢复和303不重开 |
| 2 定向实机，按需插入 | 只补批次1证明必须取得的上下文、自然选择或SDK事件；已完成采样器复用 | 主线独占客户端/安装；同身份、同阶段、预算兼容才合并采集，否则分次串行 | 自有验证、源门禁及恢复方案先通过，再新实时ready；取得准确输入或保留有效失败。Sol max设计难点，Sol xhigh执行 |
| 3 三线闭合 | R1：完整构造→g2规格；R2：自然Init/env→route→key/信任→认证；R3：完整必要行→Gadget配置/ability→实际加载，Scene后继按共同依赖推进 | 三线不同文件可并行；String共同合同由A维护，C消费冻结版本；资源/Scene缺口按必经路径收敛 | 有证据即交Sol xhigh最小实现；新语义交Sol max；Terra做明确输入转换/回归准备。完整消费者、顺序和必需数据分别成立 |
| 4 接入与正向恢复 | F-03d3g2、F-03d3s2正式发送；E-01/E-01c/E-01d1真实必要加载及保存→新Player→Manager恢复 | 独立sender源码可并行；共享Proto/生成器/矩阵、构建与DB串行集成 | Sol xhigh主责；真实Java输出与目标消费者一致、独立库正向恢复及拒绝回归成立。父项前置不满足则只完成相应子出口 |
| 5 R4汇合 | A-03/B-02/B-03、C-01/F-02/F-03、V-01及F-04必要材料；最终收发/嵌套/枚举/顺序/资源汇合、定向回归与构建 | 审核可分工；主线统一生成、构建、准入和提交 | Sol xhigh；R1/R2/R3必要合同与实现齐备，现有C-01b等成果直接复用，运行时未验边界保留 |
| 6 R5→R6 | 隔离portable部署→新旧号六项实机验收 | 主线顺序执行；失败回到责任分支修复、回归、重部署复验 | Sol xhigh；第5节全部实测通过才完成D-02/F-05/D02-R6。有复现的疑难问题交Sol max，Astra仍按3.1升级条件 |

批次是依赖顺序，**不是全员同步屏障**：A/B/C任一分支已有后继条件即可前移；某个分支需用户配合时继续其他独立工作。首诊无需等待完整Avatar或全部资源研究；首诊结果不等于resourceReady。正向Manager恢复必须等实际必要资源条件成立。批次2可在需要时穿插，不强制每批重启客户端。

### 3.3 当前批次派工与文件归属

| 分支 / 既有任务 | 本批具体动作及前置 | 产物与验收出口 | 责任文件与模型 |
| --- | --- | --- | --- |
| A：F-03d3f4d2；必要String共同合同 | 复用stock v2完整head/HashSet；oldFlags8 helper静态及真入口首pop副本已验；完整20分配/12根及子构造已列清，当前推进第二record/block/Default准确输入；自然线程、GC所有权与完整构造仍未验。旧cold普通10294、enum、fresh分立，已验getter/owned字符比较复用 | 有界准确原窗、GC/所有权与副本写域合同或准确首缺、完整构造依赖和最小实现规格；自然allocator线程单独验，不补槽/TLS/对象/未知页或host成功 | Sol max；独有GC专题、`tools/client72_d02_gc_helper_20261110.py`及test、私有`a-gc/`。清楚后交Sol xhigh/Terra |
| B：R2-ROUTE-CONFIG-SELECT、R2-CONFIGSYNC-EARLY-BRIDGE | 复用八点原返回、锁内map和stock v2 managed env/base；LoadLocalConfig必要选择静态子出口/最终URL纯分类器已验；下一核33107入口准确body/pdata、原caller和hook生命周期，以及合法本服触发 | 最终URL分类、调用身份和返回边界形成可实现观察/接入规格；map已写/env0不当调用，不跨epoch拼业务，不重复页面/扩预算，UI注册仅必要时继续；EH/unwind未知保持 | Sol max分析，Sol xhigh实现；独有ROUTE_ADOPTION专题/离线脚本和私有`b-route/`，patch/现场归主线 |
| C：E-01/E-01c/E-01d1；R3-OLD-SAVE-DEPENDENCIES | 已验出生装备/303/Metal/Promote/Curve/Skill/Depot/talents复用；所选旧号固定快照/真实关联已验；实际121角色level100/phase6、12254物品，按实际持久行收敛必要资源与完整恢复，区分已批资源与额外引用/未知。没有明确旧号不猜账号 | 固定SHA离线BSON/EXTENDED JSON只读提取、UID/缺字段/正引用测试及脱敏清单；没有快照准确列取得条件。后续按不重叠资源族/测试拆多个Terra；proud/ability/parser新语义交Sol max | 主线Sol xhigh定规格，Terra xhigh执行；独有old_save_dependencies工具/test/专题及私有`c-old-save/`。共享JVM/DB仅主线 |
| Scene：F-03d3s2 | 已优先完成148d6ebf0模板关联静态出口；首未闭actual slot114、MPLevel原分组输入及Pool/Scene/组件创建替换拆除与通知，沿准确producer继续；包装器仅转发、回收接口不是ctor | 新窗先证明必要性/有界计划；准确producer/生命周期与最小实现合同，明确发送交Sol xhigh，不据偏移/同值猜alias | Sol max语义；Sol xhigh发送。新包发布前主线冻结独立文件与输入；父项进行中，已验模板子出口不重发 |

本批[详细计划](docs/CLIENT_72_D02_RESUME_20261110B.md)固定输入/准确问题/只读依赖、可写文件、独立目录、禁止项、验收/停止恢复。实际4槽：主线＋A/B两个Sol max＋C Terra；任务交回立即释放，成熟包改派多个Terra。主线派遣，子代理禁止扩招；不能以责任线数量冒称并发。
每次派工先固定：**任务ID、输入版本/来源、准确问题、允许修改的文件、只读依赖、独立输出目录、禁止操作、产物、验收、停止/返回条件**。所有者以本批派工为准，一个文件同一时刻只有一个写入者。子代理返回“已完成/有界结论/阻塞”及证据；主线审核实际差异、必要独立样本和回归，再更新本页并提交。阻塞时写准确解除条件，不改父项为完成。

### 3.4 未完成工作包与模型分配

此表是原未完成任务的状态与验收账本；本轮已恢复，按依赖和3.3实际缺口发布任务。各行已有完成子项直接复用，R2-LOG/SESSION等不再列为优先待办。

| 批次 / 工作包 | 状态与依赖 | 交付与验收 | 推荐模型 | 证据与边界 |
| --- | --- | --- | --- | --- |
| 1A→按需2→3 / F-03d3f4d2 完整Avatar构造/后缀 | 进行中；String原getter/owned字符已验，必要FindEntry新输入边界及TLS静态后继已明确；此前fresh原回放首缺TLS index；新受控primary同epoch候选已取得，未新执行CPU，实际head oldFlags8的141fdf9c0及首pop副本已验，第二record/block/Default未闭 | 自然String字典/查询/MI/调用帧及trigger未知，不重复SDK页碰运气；当前推进首pop后准确items/第二HashSet/Default与其余构造未闭依赖。TLS/TEB/GC、enum/cold普通10294及完整构造/g2不补值或host成功 | Sol max执行疑难后继；Sol xhigh/Terra接明确实现 | [新自然输入边界](docs/CLIENT_72_D02_A_SUCCESSOR_20261110.md)、[字符比较验收](docs/CLIENT_72_STRING_EQUALS_CONTENT.md)；完整父项不晋级 |
| 1B→按需2→3 / R2-CONFIGSYNC-EARLY-BRIDGE | 进行中；自然13及八点实际同配置writer/parser/env写入/原生返回与正常退出恢复已验，完整业务及自然Init/route未闭 | 首次任意P0/P2与三预算保持，实际外采30读10415B/512；原状态前后相同，8点代码/真实trampoline绑定。后继自然Init/env→route按证据最小推进；unwind/完整payload及业务未闭 | Sol max处理新语义；Sol xhigh现场/恢复 | [八点实际验证](docs/CLIENT_72_SDK_CONFIG_STAGE_OBSERVER.md)、[实施审核](docs/CLIENT_72_D02_B_SDK_AUDIT_20261110.md) |
| 1B→按需2→3 / R2-ROUTE-CONFIG-SELECT | 进行中；准确托管loader存在，当前env/base=0的同epochstock候选已验；自然选择/Init/采用未验 | 固定托管env选择、managed→native必要输入和安全事件；原始材料可得先离线，合同足够再实机 | Sol max语义；Sol xhigh接入 | [执行计划](docs/CLIENT_72_D02_RESUME_20261109.md) |
| 1B→按需2→3 / R2-CONFIG-ROUTE-WRITE（归R2-ROUTE-CONFIG-SELECT） | 已完成；36源147pin/独立产物准入与新ready90秒/16项原件审核和恢复通过，仅关闭锁内map观察 | 首同owner M0占尝试后验同writer帧/caller，锁内只查comboLogin并输出固定类别；新M0账69/4440、绑定15/6408、外采25/9262原件已验；非空comboLogin为固定cn-route-env-0。托管/native reader采用与认证另验 | Sol xhigh实现与主线现场；新语义再Sol max | [单一五点合同、原码及边界](docs/CLIENT_72_SDK_STAGE_NATURAL_SUCCESSOR_20261110.md) |
| 1B/3，必要性确认后 / R2-PLUGIN-UI-REGISTRATION | 进行中；137B入口静态子项已验，producer未闭 | 固定RegisterTask入参、field25231字典set_Item上下文、真实producer和所选task；只在自然路径必要时继续，不以静态入口通过代验entry写入、实际转发或视图行为 | Sol max | [来源、已验子项及准确解除输入](docs/CLIENT_72_SDK_PLUGIN_UI_REGISTRATION.md) |
| 2/3，复用early结论 / R2-CONFIGSYNC-OBSERVE | 进行中；实机装载已验，目标事件未捕获 | 复用装载/恢复和early结论核触发/安装时序；确需不同观察时先列新问题，取得目标事件及producer/consumer身份后验收。保持原转发/校验/恢复，不重复同实验 | Sol xhigh执行；Sol max处理未闭语义 | [实际结果与未知](docs/CLIENT_72_SDK_CONFIGSYNC_OBSERVER.md#新ready实际90秒未命中及恢复) |
| 1C首诊→3加载→4恢复 / E-01 / E-01c / E-01d1 自然加载与正向保存 | 进行中；三文件自然首诊及303局部真实保存→新Player→Manager重绑已完成，完整必要资源/日志/场景事件与入场恢复仍未闭 | 局部63项及未就绪BSON不变直接复用；继续实际必需map/引用和完整加载，不由自然loadedAll=true推断resourceReady或全部恢复通过 | Sol xhigh定边界/集成；Terra做明确driver/清单 | [局部正向恢复](docs/CLIENT_72_D02_QUEST_303_NATURAL_REBIND.md)、[实际首诊](docs/CLIENT_72_D02_NATURAL_LOAD_FIRST_RUN.md)、[最小资源](docs/CLIENT_72_D02_MINIMUM_RESOURCES.md) |
| 1C/3→4 / E-01 真实资源→Java必要字段 | 进行中；能量/装备/曲线/Promote/七字段绑定及talents职责、投影和五项真实Java已验；两出生Java构造返回仍不批准proud规范化/ability、完整资源与加载/revision | 同源表/行→目标parser/消费者→Java字段逐项对应；先角色、depot、技能、默认装备、Scene及必需Quest，拒绝无证alias/省略；已实测Java接受缺引用，不能拿返回代验完整性 | Sol max处理新语义；Sol xhigh实现；Terra做已批投影 | [字段分层](docs/CLIENT_72_D02_AVATAR_ITEM_GAPS_20261110.md)、[七字段真实绑定](docs/CLIENT_72_D02_C_BRIDGE_JAVA_20261110.md)、[突破真实加载](docs/CLIENT_72_D02_C_RESOURCE_LOAD_20261110.md) |
| 1A共同输入→3→4 / E-01/R3-ITEM-ENTRY | 进行中；gadgetId/key、完整候选/Java加载、有限parser语义、D-02必要性及Metal局部真实生命周期已验；String同epoch图/getter/owned字符路径已验，目标完整内容未闭 | 自然Hash/FindEntry/配置命中及目标字段仍未知；不猜defense/common/LKLP alias、不许可空ability。当前A审必要String消费者；完整能力、加载及保存另验，factory须先证明D-02必要性，sender依原合同 | Sol max维护String/配置语义；Sol xhigh现场/加载；Terra明确实现 | [真实候选/Java加载](docs/CLIENT_72_D02_GADGET_ABILITY_CANDIDATE.md)、[必要性与生命周期验收](docs/CLIENT_72_D02_GADGET_NECESSITY.md)、[目标未知](docs/CLIENT_72_GADGET_ABILITY_SEMANTICS.md) |
| 3→4 / R2-ROUTE SDK配置、会话与自然连接（D-01 / F-02 / F-04） | 进行中；包/loader、map/app_key、原生builder/sign与Java真实HTTP/保存往返已验；自然Init/env及运行route未闭 | 固定来源追配置→选址→签名→会话交换；自然实验一次回答明确问题；必要输入可离线取得时先离线；与R2-SESSION/KEY汇合 | Sol max闭合新语义；Sol xhigh接入 | [配置loader](docs/CLIENT_72_SDK_ROUTE_LOADER.md)、[真实接收](docs/CLIENT_72_SDK_COMBO_HTTP.md)、[请求链](docs/CLIENT_72_SDK_LOGIN_CHAIN.md)；原登录页/root非空不证明请求成功 |
| 3→4 / R2-KEY 窄key核心接原生Crypto（D-01 / F-04） | 进行中；同核native桥接、实际零hook/窄key装载及精确代码绑定已验；自然caller/route和完整校验未验 | 同一核心输出供真实SDK Crypto，覆盖身份、caller、寄存器/栈/返回保持及错误拒绝；自然key/认证与完整校验仍须实际验证 | Sol xhigh复用实现；Sol max处理自然caller/校验难点 | 不启用旧整DLL；[ABI](docs/CLIENT_72_SDK_KEY_INPUT_ABI.md)、[原生桥接](docs/CLIENT_72_SDK_KEY_NATIVE_BRIDGE.md)、[实际key观察](docs/CLIENT_72_KEY_LOADER_OBSERVATION.md) |
| 2/3→4 / R2-KEY-LOAD 独立入口启用key核心 | 进行中；产物/owned与实际安装/代码绑定/正常页恢复已验；自然caller与完整校验未验 | 独立产物仅接准确3950c0和同一callback；实际代码绑定不代验自然key被选中、认证或完整信任路径 | 主线Sol xhigh实机；Sol max处理已复现难点 | [产物与验收](docs/CLIENT_72_SDK_KEY_LOADER.md)、[实际观察](docs/CLIENT_72_KEY_LOADER_OBSERVATION.md) |
| 3→4 / F-03d3s2 场景/Team/Entity闭合与发送 | 进行中；Pool实际函数/模式/目标头及独立TLS分支门静态合同已验；合并只读工具及World row365B转发原窗/主线第三已验；146e055f0为AutoAllocRecycle调度，非实体ctor。自然分配/注册/stage/MPLevel与组件生命周期未闭，真实row后继148d6ebf0已验模板关联，actual slot114仍未闭 | 用准确producer和同源实体完成能力/附属对象/创建替换拆除与通知顺序；逐个批准必需三层和Java来源后接sender | Sol max语义/生命周期；Sol xhigh发送实现 | [Scene上下文](docs/CLIENT_72_SCENE_RUNTIME_CONTEXT.md)、[场景token/属性](docs/CLIENT_72_SCENE_ENTRY_CLOSURE.md)；World stage与资源枚举分开 |
| 4，f4d2/R3出口充分后 / F-03d3g2 正式Avatar发送 | 阻塞；依赖f4d2、R3及同源Store/角色/SavedVitals | 固定字段/tag/类型/枚举/值来源和省略语义，确定性生成；真实Java输出交目标消费者；非零/64位/非空/错误及账号保存回归 | Sol xhigh；Terra做明确生成/向量准备 | g1和g2-prep已完成；只改Proto源，不改旧共享定义求方便；[投影](docs/CLIENT_72_AVATAR_JAVA_PROJECTION.md) |
| 随合同更新→5 / A-03 / B-02 / B-03 证据与生成 | 进行中；复用已完成B-01，随新增合同维护 | 精确审核新增/变化来源、保留漂移拒绝、重复生成一致；已验证片段才接入，旧号/未知不兜底。索引更新不是新的语义发现；[矩阵](docs/CLIENT_72_LOGIN_PROTO_MATRIX.md) | Sol xhigh审核/集成；Terra做明确索引/漂移检查 | [既有规范](AGENTS.md) |
| 4→5 / C-01 / F-02 / F-03 网络与初始化整合 | 进行中；全量出口归R4 | 复用C-01b帧/注册修复，补必要Union/枚举/最终发包与序列。检查onLogin、SceneInitFinish、EnterSceneDone后的队伍/世界/实体，不能只验八握手 | Sol xhigh；复杂协议冲突交Sol max | [既有规范](AGENTS.md) |
| 随变更→5 / V-01 回归失败归因 | 进行中；按本次改动和R4范围选集 | 新运行先明确基线/夹具/生产失败；保护账号、经济、保存、Lua。完整全量未重跑就不宣称全绿；不机械用历史失败总数安排任务 | Sol xhigh归因修复；Terra做明确夹具/结果整理 | [既有规范](AGENTS.md) |
| 准备可提前→5→6 / F-04 部署材料及隔离核验 | 进行中；复用ENV-01基线，实际部署待R4 | 真实SDK/信任/资源合同充分后形成7.2产物；正常构建和停服，按InstanceRoot归属核对，不仅改版本或环境变量放行；[隔离](docs/CLIENT_72_PORTABLE_ISOLATION.md) | 主线Sol xhigh；Terra做清单/离线核对 | [既有规范](AGENTS.md) |

R2-CONFIGSYNC-OBSERVE与EARLY-BRIDGE共享自然配置目标：先消费已有early结果，只有不同问题/时机需要时才安排旧观察路线。E-01首诊是现有自然加载父项的诊断步骤，父项“阻塞”表示完整加载/正向恢复前置未齐，不禁止输入明确的首诊。新发现须先归到现有任务并证明D-02必要性，不因一个失败再复制一套父任务。

### 3.5 并行与互斥规则

| 对象 | 可同步执行 | 必须由主线串行或先冻结的操作 |
| --- | --- | --- |
| 独立源码/工具/证据 | A/B/C各有文件清单与输出目录；只读冻结来源可以共享 | 不同时写同一文件、输出或已固定报告；公共helper/协议矩阵/源pin变更先由主线协调，通知受影响分支后再必要复验 |
| 当前客户端五输入 | 只读历史快照、无需检查现场身份的分析可在实机时继续 | 依赖当前stock磁盘身份的回放与临时DLL安装互斥；恢复后重核身份，不能把orig别名替代当前输入或放宽hash |
| 客户端/调试器/安装 | 合同兼容的R1/R2/String采样可由主线同次执行 | 单一现场负责人；新实时ready，固定scope/预算与备份恢复；原版和临时DLL的不同输入epoch不混用，不启动冲突目标 |
| 构建/生成/资源加载 | 独立文件的实现与测试准备可并行 | 主线统一Git、Gradle/Cargo、protoc及共享生成输出；实际JVM加载/集成测试不占用其他分支的静态map或输出；子代理不自行运行共享构建 |
| DB/端口/部署 | 隔离配置和验收脚本可提前准备 | 主线独占Mongo/固定端口/服务/运行目录；按源码核端口，保护原数据，正常停旧实例后部署；不能只改环境变量宣称隔离 |
| Project/Harness/提交 | 子代理提交各自专题/证据及建议状态 | 主线统一更新共享索引、验收结论、任务标记和本地Git提交；不自动推送，不提交他人修改 |

## 4. 任务发布与范围控制

1. **按3.2依赖及3.4准确缺口持续调度。** 本轮A/B/C的已验子项列在第6节，包括自然首诊及303局部恢复，不重新发布旧研究或重复首诊。本轮恢复授权覆盖上轮暂停，按准确缺口持续发布、验收并汇合未闭出口；R2-LOG/SESSION及既有来源/采样成果直接复用。
2. **规格成熟就实现。** Sol max给出明确来源/字段/时序和验收后，转Sol xhigh/Terra做最小变更；重大问题或长任务先规划。主线审核后执行必要测试/独立复现、本地提交、更新状态，再启动后继；无新变化或未决风险不重跑历史全量。
3. **实机只补准确缺输入。** 不直接放大SDK预算、重复同参数观察或用零槽补值；需要用户操作时先备妥实验再一次说明操作/预期/反馈并等待新ready，同时继续独立工作。采集通过不代替消费者或业务通过。
4. **必要性先于扩展。** 缺表/日志须结合真实入场调用筛选；不把全部Lua/资源表/玩法列P0，也不将未知默认判作可省略。完整Avatar/Scene、自然SDK、真实资源与保存各有独立出口，不以一项片段通过互相代验。
5. **外部复用按缺口启动。** E-01/R3-UPSTREAM-D02和R2-PATCH-DELTA的审计已完成；AstaPS候选用于对照、已验同一行直接复用。只有新提交或当前缺口有明确可用差异时审增量。WinHTTP先证自然路径覆盖及本地缺陷；不整包替换、不启强制验签、无证alias或未知输入放行。
6. **保留完成标记与未知。** 已完成子项仍在本文维护；已验范围之外的缺口继续留父项。日志/命令/逐次失败留Harness/专题，本文仅维护任务设计、责任、依赖、状态和验收结论。恢复后只有D-02全部通过、用户明确暂停或所有独立工作均有准确外部阻塞时交接，按AGENTS先备份HANDOFF2再写详细交接，HANDOFF只追加。

<a id="d02-offline-sol-terra"></a>

原“Sol/Terra离线前置路线”已收口，成果在第6节；旧锚点保留。后续从第3节按依赖派工，当前未闭语义优先Sol max，日常实现Sol xhigh，有界执行Terra；Astra不列常规批次。旧路线、暂停或继续历史不覆盖本页当前阶段及用户最新指令。

## 5. 验收标准

每个任务完成前，须具备**明确范围、可追溯来源、验收结论和可复现证据入口**。协议/运行变更另须独立正反样本、对应实现/生成及受影响回归；不适用项说明理由。静态、离线原生、模型和实机范围分列；必要时双跑核一致。运行变更按AGENTS完成构建/部署核验；缺前置则任务只在局部范围完成，部署及父项保持未验。

实机验收使用受控脚本/API与用户确认，只操作本项目目标，不使用computer-use；原始数据留忽略目录。

| D-02验收项 | 当前状态 | 通过条件 |
| --- | --- | --- |
| 旧测试号本服登录 | 待开始 | 真实SDK/认证→本服会话，账号与角色归属正确；原SDK页不能代入 |
| 新号显式选人/命名 | 待开始 | 新账号进入正常选人流程，合法创建确认保存；错误/重复请求不重复创建或发放 |
| 初始化与场景握手 | 待开始 | 必需角色/库存/队伍/世界/实体状态被客户端正确消费，整个加载序列完成 |
| 实际进入世界 | 待开始 | 可见角色落地与对应服务/协议证据一致；LOADED或健康不代替 |
| 连续心跳与会话保持 | 待开始 | 按既定方案连续10分钟回包、无异常断会话，记录完整观察结果 |
| 两类账号退出重登/保存 | 待开始 | 各自正常退出再登录，角色、装备、归属及本次受控进度持久一致 |

全部通过才关闭D-02/F-05/D02-R6。新旧号可先旧后新共用基础链，但两类账号均须重登；失败修复后正常重建部署复验。验收证据放专题/报告，本文只更新结论和链接。

## 6. 已完成任务与成果目录

下列均为**已完成的限定验收**，逐行保留“已完成”状态，合并展示不删除旧ID；父项未完成状态不被覆盖。后续完成的任务继续在本文件登记，只有验收范围一致的项才可合并。详细样本、提交、测试数量、失败和实现限制在链接专题及[原任务全文](Project.archive-2026-11-08.md#任务卡)。有后继不重开已验子项；报告里的历史待办若与当前工作包冲突，以当前依赖和最新专题证据为准。

### 6.1 基础、登录与会话

| 任务ID | 状态 | 已验成果 / 保留边界 | 证据 |
| --- | --- | --- | --- |
| DOC-01、A-01、ENV-01 | 已完成 | 接手规范、固定客户端和隔离研发基线；不代表7.2可连接。 | [提取/输入](docs/CLIENT_72_EXTRACTION.md)、[研发基线](docs/CLIENT_72_RESEARCH_VALIDATION.md) |
| A-02、A-02a、A-02b | 已完成 | 服务端静态协议盘点及动态来源守卫；开放插件/事件边界保留。 | [表面盘点](docs/CLIENT_72_PROTOCOL_INVENTORY.md) |
| A-03a、A-03b、A-03c、D02-R0 | 已完成 | 方法索引、管理器依赖、Proto候选矩阵与入场台账；只完成A-03/B-02/C-01的台账层，运行闭包未验。 | [源码图](docs/CLIENT_72_LOGIN_SOURCE_GRAPH.md)、[依赖](docs/CLIENT_72_LOGIN_DEPENDENCIES.md)、[矩阵](docs/CLIENT_72_LOGIN_PROTO_MATRIX.md) |
| A-04a、A-04b、A-04c、A-04d1 | 已完成 | 元数据、泛型/父类/布局、静态包号与签名恢复；同值域和运行时未知保留。 | [提取](docs/CLIENT_72_EXTRACTION.md)、[网络](docs/CLIENT_72_NETWORK.md)、[签名](docs/CLIENT_72_SIGNATURES.md) |
| B-01 | 已完成 | 三层模型、输入约束及研究/ready校验；未知可记录但不自动晋级。 | [模型](protocol/cn-7.2/README.md) |
| B-02a、B-02b、A-04d2 | 已完成 | 包头/心跳raw及Token保护writer限定恢复。 | [网络](docs/CLIENT_72_NETWORK.md)、[保护入口](docs/CLIENT_72_PROTECTED.md) |
| A-04d3、A-04d3a、A-04d3b、A-04d3c、A-04d3c1、A-04d3c2、A-04d3c3 | 已完成 | Login完整raw、响应叶子/非空图、map AOT和合并行为；业务/运行时合同另验。 | [Login wire](docs/CLIENT_72_LOGIN_WIRE.md)、[响应图](docs/CLIENT_72_GRAPH_READERS.md)、[AOT](docs/CLIENT_72_MAP_AOT.md) |
| A-04d4a、A-04d4b、B-02c1、A-04d5b | 已完成 | 登录标记/枚举、请求响应身份及首批字段来源。 | [登录语义](docs/CLIENT_72_LOGIN_SEMANTICS.md)、[会话](docs/CLIENT_72_SESSION.md) |
| A-04d5a、B-02c2、A-04d5c、B-02c3、A-04d5d、A-04d5e、B-02c4、A-04d5f | 已完成 | seed、token保存、SDK来源与请求构造/发送入口限定链。 | [会话](docs/CLIENT_72_SESSION.md) |
| A-04d5g、A-04d5h、B-02c5、A-04d5i、B-02c6、A-04d5j、B-02c7、A-04d5k、B-02c8、A-04d5l、A-04d5m | 已完成 | 原生RSA/验签、密钥安装、UID/token、认证来源和分区候选链；实际SDK调度另验。 | [密码学/会话](docs/CLIENT_72_SESSION.md) |
| A-04d5n1、A-04d5n2、A-04d5n3、A-04d5n4、A-04d5n5、A-04d5o、B-02c10、A-04d5p、A-04d5q | 已完成 | 分区解析/配置/恢复链、key选择、Handler到Login；未知业务不因路径通过而命名。 | [分区/Handler](docs/CLIENT_72_SESSION.md) |
| A-04d5r、A-04d5s、A-04d5t、A-04d5u、A-04d5v、A-04d5w | 已完成 | 原生封/收帧、Ec2b、Token/Login完整帧限定链与错误边界；自然socket时序未验。 | [网络](docs/CLIENT_72_NETWORK.md)、[会话](docs/CLIENT_72_SESSION.md) |
| F-01、F-02a、F-02b、F-02b1、F-02b2、F-02c | 已完成 | 固定参考差异、登录/响应raw与最小发送、连续收帧key合同；完整登录未验。 | [融合](docs/CLIENT_72_ASTAPS_FUSION.md)、[收帧](docs/CLIENT_72_RECEIVE_KEY.md) |
| B-02d、B-03a、C-01a | 已完成 | 选人三件组研究、生成和业务/保存接入；新号显式选择，不代入实机。 | [选人](docs/CLIENT_72_BORN.md) |
| C-01b | 已完成 | 真实帧入口、最终发送域检查及注册冲突最小修复；业务映射/完整C-01另验。 | [网络边界](docs/CLIENT_72_NETWORK_BOUNDARIES.md) |

### 6.2 初始化、场景与Avatar

| 任务ID / 成果 | 状态 | 已验成果 / 保留边界 | 证据 |
| --- | --- | --- | --- |
| F-03d3f4d2-SECOND-ALLOCATOR-SPEC | 已完成 | 主线29pin独立第三20429B同双fresh；实际generic AOT/caller/唯一pdata及201B/53条核原ac214。旧265是静态显示错误，旧请求/pin保留。零参Default三QWORD及第二pop两条有界后继明确，但仅proposal，不改本轮AB98/2304，不批准新采集/写/CPU或完整对象语义。 | [长度勘误、源合同、后继及停止点](docs/CLIENT_72_D02_SECOND_ALLOCATOR_SPEC_20261110.md) |
| F-03d3f4d2/R1-METHOD-CACHE | 已完成 | 原lookup/hash/深比较与hit/miss边界、完整有界collector及主线第三已验；新ready原版90秒实际候选图取得、正常页/退出及保护文件保持。原生采样图回放/自然caller/完整构造/default/Handler/g2未代验。 | [合同与实机输入](docs/CLIENT_72_AVATAR_METHOD_CACHE.md) |
| F-03d3f4d2/R1-METHOD-CACHE-REPLAY | 已完成 | 同轮采样图的原9查询及连续原factory/8方法/64B写回通过，三新进程同字节、20检查/12独立审核；旧六前缀保留，止准确16B请求。自然caller/完整cold/default/Handler/g2仍未验。 | [源绑定回放](docs/CLIENT_72_AVATAR_METHOD_CACHE_REPLAY.md) |
| F-03d3f4d2/R1-HIERARCHY-STORAGE | 已完成 | 准确16B/两clear/已采Object8B复制、原store/xchg发布通过；三新进程同字节，17检查及10独立审核；4新增模型/45读345B。下一32B/完整cold/default/Handler/g2未代验。 | [有界原生存储](docs/CLIENT_72_AVATAR_HIERARCHY_STORAGE.md) |
| B-02e1、B-02e2、B-02e3、B-02e4、B-02e5、B-02e6、B-02e7 | 已完成 | 选人成功继续、缓存等待、计时/场景查询、列表和非空子图消费片段；完整继续/落地未验。 | [继续](docs/CLIENT_72_BORN_CONTINUATION.md)、[场景消费](docs/CLIENT_72_SCENE_DATA.md)、[嵌套](docs/CLIENT_72_SCENE_NESTED.md) |
| F-03a、F-03b、F-03c | 已完成 | 八握手、容量和进入场景通知最小发送合同；完整初始化/实体另验。 | [握手](docs/CLIENT_72_SCENE_HANDSHAKE.md)、[容量](docs/CLIENT_72_STORE_WEIGHT_CONTRACT.md)、[入场通知](docs/CLIENT_72_ENTER_SCENE_CONTRACT.md) |
| F-03d1a、F-03d1b1、F-03d1c | 已完成 | PlayerData raw、用途与有限生产接入；其余属性及完整初始化不晋级。 | [PlayerData](docs/CLIENT_72_PLAYER_DATA_CONTRACT.md) |
| F-03d2a、F-03d2a1、F-03d2a2、F-03d2a3、F-03d2b1、F-03d2b2、F-03d2c1、F-03d2c2 | 已完成 | 库存完整raw、Material/Equip有限语义、独立适配器及最小生产发送；精炼转换/其他分支保留。 | [库存生产](docs/CLIENT_72_INVENTORY_PRODUCTION.md)、[装备](docs/CLIENT_72_EQUIP_CONTRACT.md) |
| F-03d3a、F-03d3b、F-03d3c、F-03d3d、F-03d3e1、F-03d3e2、F-03d3e3、F-03d3e4、F-03d3e5、F-03d3e6、F-03d3e7、F-03d3e8、F-03d3g1 | 已完成 | 完整Avatar raw、子树/容器及独立Java适配器；不等于必要字段/正式sender。 | [完整raw](docs/CLIENT_72_AVATAR_COMPLETE_RAW.md)、[适配器](docs/CLIENT_72_AVATAR_WIRE_ADAPTER.md) |
| F-03d3f1、F-03d3f2a、F-03d3f2b、F-03d3f3 | 已完成 | 身份/三属性/装备GUID、Hero与本地角色来源、Team限定消费。 | [Avatar合同](docs/CLIENT_72_AVATAR_CONTRACT.md)、[控制](docs/CLIENT_72_AVATAR_CONTROL.md)、[入场身份](docs/CLIENT_72_SCENE_ENTRY_IDENTITY.md)、[队伍](docs/CLIENT_72_AVATAR_TEAM.md) |
| F-03d3f4a、F-03d3f4b、F-03d3f4c、F-03d3f4d1、F-03d3g2-prep | 已完成 | 生命态、FightProp、depot、构造/省略片段及真实getter隔离投影；完整构造和g2未验。 | [生命态](docs/CLIENT_72_AVATAR_STATE.md)、[属性](docs/CLIENT_72_AVATAR_FIGHT_PROPS.md)、[depot](docs/CLIENT_72_AVATAR_SKILL_DEPOT.md)、[构造片段](docs/CLIENT_72_AVATAR_INITIALIZER.md)、[投影](docs/CLIENT_72_AVATAR_JAVA_PROJECTION.md) |
| F-03d3s1、F-03d3s2a | 已完成 | SceneInfo有限非空raw及GUID/实体/附属模型关联；完整能力/生命周期未验。 | [Scene raw](docs/CLIENT_72_SCENE_INFO_RAW.md)、[身份关联](docs/CLIENT_72_SCENE_INFO_CONTRACT.md) |
| Avatar深度子合同（归F-03d3f4d2） | 已完成 | rgctx/List/Sorted、槽查询/回退、tag29/38、Ornament来源、warm/cold与接口片段已验；父descriptor/完整Handler仍未验。 | [深度合同](docs/CLIENT_72_AVATAR_DEEP_RESEARCH.md)、[warm构造](docs/CLIENT_72_AVATAR_LIVE_CONTRACT.md)、[cold合同](docs/CLIENT_72_AVATAR_COLD_CONTRACT.md)、[cold后继](docs/CLIENT_72_AVATAR_COLD_COMPLETION.md)、[接口](docs/CLIENT_72_AVATAR_COLD_INTERFACES.md) |
| Avatar真实表与HandlerClosure限定成果（归D02-R1/R3） | 已完成 | 六真实表、同缓存消费、Int32配置与泛型factory有限后继；NativeConfig旧typed解释已撤回，不批准自然模式/默认或g2。 | [六表/勘误](docs/CLIENT_72_AVATAR_NATURAL_TABLES.md)、[HandlerClosure](docs/CLIENT_72_AVATAR_HANDLER_CLOSURE.md) |
| F-03d3f4d2-parent-continuation | 已完成 | fresh静态父descriptor重定位模型、准确vector增长/释放及单线程已知锁后继；实际止于class1234未采读，未写cachedclass/default，完整cold/Handler未完成。 | [父类后继](docs/CLIENT_72_AVATAR_PARENT_CONTINUATION.md) |
| F-03d3f4d2-fresh-ancestor | 已完成 | 精确Object kind28/canonical模型、fresh1234重定位及原1239/1234类壳发布；真实首缺cache1169。Nullable opaque模型与未绑定对照分列，完整类初始化/default/Handler未完成。 | [fresh后继](docs/CLIENT_72_AVATAR_FRESH_ANCESTOR.md) |
| R1-SAMPLE-INTERFACES-prep | 已完成 | 1169/1249接口和Pool TLS索引的最小采样器、固定来源及预算已验；实际新user-ready采样未执行。旧Array/Type/371等不重采，null/unknown与上层未完成保持。 | [接口采样](docs/CLIENT_72_INTERFACE_CONTEXT_SNAPSHOT.md) |
| Scene深度/运行上下文限定成果（归D02-R1/R4） | 已完成 | retain/copy、hash注册/查询、token与80154消费、真实Scene/Gadget索引及工厂前缀；PoolCluster/stage/实体后继未验。 | [深度合同](docs/CLIENT_72_SCENE_DEEP_RESEARCH.md)、[入场后继](docs/CLIENT_72_SCENE_ENTRY_CLOSURE.md)、[运行上下文](docs/CLIENT_72_SCENE_RUNTIME_CONTEXT.md) |
| F-03d3s2-WORLD-ROW-SUCCESSOR | 已完成 | 20pin/41静态断言，主线第三42834B同双fresh；146e055f0仅AutoAllocRecycle slot0→slot2。唯一365B/79条row consumer只向148d6ebf0转交，未直接读+50/+58或写entity字段；MPLevel键职责沿已验来源，注册/能力组仍未知，不解锁新增Java alias或sender。 | [准确排除、原件与最小后继](docs/CLIENT_72_D02_SCENE_REQUIRED_SUCCESSOR_20261110.md) |
| F-03d3s2-pool-prefix | 已完成 | fresh实际Pool绑定→真实非零Allocate前缀，准确首缺loader TLS索引；独立零模式控制已验，未提供TLS/allocator成功模型，分配/生命周期仍未闭。 | [Pool后继](docs/CLIENT_72_SCENE_POOL_CONTINUATION.md) |
| F-03d3f4d2/R1-CLASS80-STORAGE | 已完成 | 原32B发布、两接口行、inflation/class返回及首vtable消费已验；停普通缓存输入，完整cold/default未验。 | [实际结果/预算/准确后继](docs/CLIENT_72_AVATAR_CLASS80_STORAGE.md) |
| F-03d3f4d2/R1-RUNTIME-INPUT-COLLECTOR | 已完成 | 纯collector来源、预算、owned正反及全部unique末尾复读已验；无target/native调用，不代替自然输入。 | [实际接口与来源](docs/CLIENT_72_AVATAR_RUNTIME_INPUTS.md) |
| F-03d3f4d2/R1-RUNTIME-INPUT-ADAPTER | 已完成 | 新原版运行的ordinary/warm候选、身份、统一复读及退出保护已验；自然caller与消费者闭合另验。 | [实际输入与未验边界](docs/CLIENT_72_AVATAR_RUNTIME_INPUTS.md#新ready原版实测) |
| F-03d3f4d2/R1-RUNTIME-INPUT-REPLAY | 已完成 | 同轮warm原返回、四ordinary MethodInfo及ASLR函数指针原store已验；cold停普通10294槽，完整构造/default原写未验。 | [实际消费者与精确后继](docs/CLIENT_72_AVATAR_RUNTIME_REPLAY.md) |
| F-03d3f4d2/R1-WARM-USAGES | 已完成 | main/optionalString分立的纯collector、来源、读预算与统一末尾复读owned已验；自然输入由adapter另验，完整构造未验。 | [真实来源/预算/主线验收](docs/CLIENT_72_AVATAR_WARM_USAGES.md) |
| F-03d3f4d2/R1-WARM-USAGES-ADAPTER | 已完成 | 有界自然采集、身份/复读及退出恢复已验；7/8候选取得，enum两个零槽及String空usage真实保留，完整图/消费者/父项未完成。 | [实际结果与准确未闭项](docs/CLIENT_72_AVATAR_WARM_USAGES.md#本轮合并实测与收尾) |
| F-03d3f4d2（最新warm原消费者后继）；E-01/R3-ITEM-ENTRY（String共同合同v2） | 已完成 | 三fresh10case/158步同字节，20检查及主线17审核；uint32构造/getter/List片段和零槽lazy分支有界执行，allocator/锁/definition pointer分别止准确首缺。String真实Dictionary carrier合同已由主线冻结为独立54读2704B；只批准collector实现，未采自然图、未完成比较/配置/父项。 | [原执行、未知和合同](docs/CLIENT_72_AVATAR_WARM_CONTINUATION.md) |
| F-03d3f4d2（typed allocator普通查询后继步骤） | 已完成 | 原完整Avatar入口连续执行allocator及旧已验纯native查询，61条完成/62次前观察，准确止于未采普通类缓存root；三fresh同字节，26测试及主线13项审核通过。未取得分配返回、普通1255结果或GC/TLS/OS成功，不代验完整构造。 | [原窗、执行、真实首缺及String后继](docs/CLIENT_72_AVATAR_ALLOCATOR_SUCCESSOR.md) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（同epoch联合后继采集工具步骤） | 已完成 | 独立collector/adapter、三fresh人工图、16测试及134项审核通过；最坏76读/3264B、统一final与18父pin保持，主线实际stock/source门禁通过。只批准新epoch采集准备，未采自然String/普通root或验原生消费，父项不晋级。 | [合同、工具及实际门禁范围](docs/CLIENT_72_AVATAR_STRING_SUCCESSOR_INPUTS.md) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（联合stock自然输入采集步骤） | 已完成 | 新实时确认下90秒/44监测，69读2704B取得四分支同epoch候选图；15项完整纯重放、身份/末尾复读及正常页/退出保护通过。FindEntry仍零，factory/比较/完整构造/配置能力未验；只交冻结输入给后继，不晋级父项。 | [实际输入、去重预算及未验边界](docs/CLIENT_72_AVATAR_STRING_SUCCESSOR_INPUTS.md#新实时确认的联合stock采集) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（fresh原getter/普通查询比较后继步骤） | 已完成 | 两fresh同字节，真实GetDefault25条返回同轮Default；完整Avatar连续105完成后止缺TLS4B。13测试/13主线审核及限定String共同边界冻结通过；无host返回/跨epoch数据，Equals体/allocator返回/完整父项未验。 | [实际原执行、准确首缺及冻结范围](docs/CLIENT_72_FRESH_AVATAR_STRING_SUCCESSOR.md) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（String Equals实际入口前缀步骤） | 已完成 | 无pdata事实保留，独立获批64B中只执行可达48B；两fresh8call/66完成、13测试及12主线审核通过。null/null、同owned指针、长度不同实际AL=1/1/0；等长不同地址停Helper2908尾跳前，未比较字符/自然键，不完成完整比较器/FindEntry或父项。 | [准确源、owned范围与尾ABI](docs/CLIENT_72_STRING_EQUALS_SUCCESSOR.md) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（String原字符比较步骤） | 已完成 | 两fresh各14case从实际10339经原尾跳/177B Helper2908返回，累计28call/1302完成且预期AL全符；13只读测试及主线15审核通过。显式owned NUL尾读逐字节保留；自然allocator终止存储、Hash/FindEntry、自然键、完整Comparer/构造及父项未验。 | [原字符路径、预算与主线验收](docs/CLIENT_72_STRING_EQUALS_CONTENT.md) |
| F-03d3f4d2；E-01/R3-ITEM-ENTRY（必要String自然后继边界） | 已完成 | 三个原窗/28关键opcode/10pin及TLS420B静态复核、6测试与主线第三同字节；明确Gadget到asset-key的实际consumer及首NUL hash规则，旧5次hash只复用。自然字典/查询/MI/帧/trigger缺失，26/816图仅proposal，未新CPU/PE/live或验完整查询。 | [准确源、必要性、输入与停止条件](docs/CLIENT_72_D02_A_SUCCESSOR_20261110.md) |
| F-03d3f4d2（allocator TLS直接callee单窗步骤） | 已完成 | 重新核五stock后取得唯一312B/74指令PE exception完整窗；双静态报告与主线第三同字节，明确size32/bin2、真实freelist/写域及三个未审外部边。15/82读取与捕获副本写域仅proposal，未新CPU/live或验allocator返回/完整Avatar。 | [真实原窗、输入、写入与停止合同](docs/CLIENT_72_D02_AVATAR_TLS_SUCCESSOR_20261110.md) |
| F-03d3f4d2（owned线程ABI步骤） | 已完成 | 固定phnt分支/提交及SDK来源，独立cl构建、两fresh恰六次class0、9测试和原48B独立审核通过；仅当前机器原生x64自有main/worker的TEB/身份/GS58关系成立。不是游戏线程/TLS/池或回放许可，未晋级完整Avatar。 | [实际构建、原件、失败及后继](docs/CLIENT_72_D02_OWNED_TEB_ABI_20261110.md) |
| F-03d3f4d2（同epoch只读TLS/R1采集器实现步骤） | 已完成 | 七冻结来源/原audit27–35关系、21人工测试及主线独立第三图/失败守卫通过；保留本次正常launch原primary，API另账、13/340→统一26/680。仅工具实现，未调用真实目标API或采自然TLS，primary非自然allocator线程证明、pool写域/replay仍未批。 | [实现、准确预算及主线审核](docs/CLIENT_72_D02_TLS_INPUT_COLLECTOR_20261110.md) |
| F-03d3s2-pool；F-03d3f4d2（Pool TLS条件分支合同步骤） | 已完成 | 16冻结pin/46关键反汇编/20call site、双静态20007B与主线第三同字节；明确Pool+1990与Avatar+19d0不同、P+20为U64界限、条件读取及首外call/首写/未采global前停止。原窗没有保存机器码hex，不声称重核raw；18/369→36/738仅未来合并规格，未验自然caller或分配。 | [准确分支、预算及执行边界](docs/CLIENT_72_D02_POOL_TLS_CONTRACT_20261110.md) |
| F-03d3s2-pool；F-03d3f4d2（合并条件只读工具步骤） | 已完成 | 34人工测试、原21测试字节保持及默认完整/错误报告同原版，12来源门禁和主线独立第三图/primary人工门禁通过。两个固定profile：默认13/340→26/680，合并18/369→36/738，单208；统一final、API数不变。0真实API/native/live，未验自然allocator、block/写域或pop。 | [实现与明确边界](docs/CLIENT_72_D02_TLS_POOL_COLLECTOR_20261110.md) |
| F-03d3f4d2（完整head输入及单pop副本合同步骤） | 已完成 | 27pin/44关键bytes、409B必经callee与20直接site/12roots，双23139B及主线第三同字节；真实L32..208完整span、六精确写域仅proposal，未获CPU/写/live许可。单block不是完整构造，后继items/第二类另记。 | [真实长度、原指令与精确边界](docs/CLIENT_72_D02_AVATAR_CAPTURE_ALLOCATION_SPEC_20261110.md) |
| F-03d3f4d2（完整head只读工具实现步骤） | 已完成 | 52人工测试、原34源码保持、两旧profile完整输出同before、主线L87/L208第三图/四tool来源与primary人工门禁通过；第三固定profile最大28/622→56/1244，API上限不变。0真实API/native/live，未实采block、执行pop或批准写。 | [实现及主线独立审核](docs/CLIENT_72_D02_AVATAR_BLOCK_COLLECTOR_20261110.md) |
| F-03d3f4d2（items与第二类输入合同步骤） | 已完成 | 20pin/metadata/call字节双10281B与主线第三同字节；B+5b74628仅原值供H+10，producer/数组身份未知；第二B+5897cf0绑定HashSet2147/instance11 UInt32，旧请求56/bin4非新值。增量8/304→16/608为未批proposal，不补普通槽或盲跟N。 | [准确来源与下一有限输入](docs/CLIENT_72_D02_AVATAR_CTOR_NEXT_INPUTS_20261110.md) |
| F-03d3f4d2（items与HashSet同epoch只读工具步骤） | 已完成 | 第四固定profile、68人工测试/主线S65/L87及共享L208第三与API人工门禁通过；原三profile九场景完整报告/请求同before，旧test只适配一处registry名称。最大36/926→72/1852，统一final、五tool新epoch；0真实API/native/live，不验第二pop/数组身份/完整Ctor。 | [实现、预算与主线审核](docs/CLIENT_72_D02_AVATAR_CTOR_COLLECTOR_20261110.md) |
| E-01（Depot talents职责/投影/真实Java步骤） | 已完成 | 同typed Depot+28→解码匹配→EnhanceDetail Talent=8/current发布、唯一5901B/16pin及原default常量主线第三通过；八投影正反/第三同bytes、五实际Java/14独立审核通过。两出生真实构造返回，单删talents均NPE529；不批准proud规范化、空能力、完整资源/原生Avatar/g2/保存。 | [直接职责](docs/CLIENT_72_D02_DEPOT_TALENTS_SUCCESSOR_20261110.md)、[真实Java与scope](docs/CLIENT_72_D02_DEPOT_TALENTS_JAVA_20261110.md) |
| E-01（两出生Avatar已批准片段合并步骤） | 已完成 | 五完整报告同canonical行、32片段值/float32位、两独立与主线第三1211B输出/20940B来源manifest逐字节一致，七正反测试通过；仅十个已批字段，completeAvatar/resourceReady仍false。0新Java/CPU/live，不覆盖talents、完整构造/保存。 | [同源规则、实际审核及边界](docs/CLIENT_72_D02_BIRTH_AVATAR_JOIN_20261110.md) |
| F-03d3f4d2/A-GC-OLDFLAGS8 | 已完成 | 本epoch L32/oldFlags8必经904B helper的准确15条SSE分支闭合：两次同址H+10h/16清理、无该分支GC回调/外部call。18pin与主线第三同字节；完整20分配/12根及子构造依赖列全，其他长度/GC/自然线程未验。 | [静态合同与完整构造依赖](docs/CLIENT_72_D02_GC_HELPER_20261110.md) |
| F-03d3f4d2/FIRST-POP-COPY | 已完成 | 原1405336c0入口经同epoch普通查询/TLS/pop/helper/post原ret到owned sentinel，200条原指令、九写七span44B；8测试及主线第三36425B同SHA通过。无自然ctor frame，线程/GC所有权、第二分配/Default及完整Avatar/g2仍未验。 | [原CPU副本、拒绝与边界](docs/CLIENT_72_D02_FIRST_POP_COPY_20261110B.md) |
| F-03d3s2/SCENE-ROW-LIFECYCLE（模板关联子出口） | 已完成 | 唯一585B/120条下游将原row存entity.field138373/+140；不消费row+50/+58。16pin/30静态断言主线第三同SHA通过。条件容器调用非具名Remove，首未闭actual slot114、组件/拆除/通知及MPLevel完整配置不批准。 | [原窗、准确排除与后继](docs/CLIENT_72_D02_SCENE_ROW_LIFECYCLE_20261110B.md) |


### 6.3 SDK、补丁、观察与运维前置

| 任务ID / 成果 | 状态 | 已验成果 / 保留边界 | 证据 |
| --- | --- | --- | --- |
| R2-LOG | 已完成 | SessionKeyAuthenticator敏感日志有效红绿、认证/会话采用/保存保持、隔离回归与portable构建；不变更认证策略，真实部署/SDK另验。 | [日志](docs/CLIENT_72_AUTH_LOGGING.md) |
| R2-SESSION | 已完成 | session verify独立单token响应、真实HTTP成功/错误/64位/嵌套、Java→原生parser/formatter正反和账号/密码/保存回归/构建；自然SDK及D-02未验。 | [适配与验收](docs/CLIENT_72_SDK_SESSION_ADAPTER.md) |
| D-01a、D-01b1、D-01b2、D-01b3a | 已完成 | 命名入口参数、启动RNG、全局/UTF8、TLS/Marshal限定合同；完整SDK和自然函数返回另验。 | [入口ABI](docs/CLIENT_72_PATCH_ABI.md)、[动态](docs/CLIENT_72_DYNAMIC_DEBUG.md)、[Marshal](docs/CLIENT_72_MARSHAL_STORAGE.md) |
| D-01b4、D-01b5a、D-01b5b、D-01b5e、D-01b5f、D-01b5g-preflight | 已完成 | 观察通道、自有SW/HW、实际退出/对照与观察前置均按限定范围验收；根因或本服成功未被推定。 | [通道](docs/CLIENT_72_DEBUG_CHANNEL.md)、[启动观察](docs/CLIENT_72_STARTUP_OBSERVER.md)、[软件点](docs/CLIENT_72_SOFTWARE_BREAKPOINT_CHANNEL.md)、[单HW](docs/CLIENT_72_SINGLE_HARDWARE_OBSERVER.md) |
| D-01b5c、D-01b5d、D-01b5h1 | 已完成 | 当前区/RegionList的身份和Java→原生消费、真实HTTP签名版本拒绝；7.2成功准入未验。 | [当前区](docs/CLIENT_72_HTTP_DISPATCH.md)、[列表](docs/CLIENT_72_REGION_LIST.md)、[HTTP路由](docs/CLIENT_72_HTTP_ROUTE_CONTRACT.md) |
| F-04a、F-04b、F-04c、F-04c2 | 已完成 | 运行前置核对、本地信任输入/保留验签补丁及便携路径供给；未批准整DLL加载。 | [前置](docs/CLIENT_72_RUNTIME_PREREQUISITES.md)、[信任](docs/CLIENT_72_TRUST_CONTRACT.md)、[补丁](docs/CLIENT_72_TRUST_PATCH.md)、[路径](docs/CLIENT_72_TRUST_LAUNCHER.md) |
| 原始启动与只读初始化成果（归D-01b / D02-R1/R2） | 已完成 | 原SDK登录页、初始化/cold数组与资源槽取证；首次错误解释已撤回，不代入本服登录。 | [原始启动](docs/CLIENT_72_UNINSTRUMENTED_STARTUP.md)、[初始化](docs/CLIENT_72_RUNTIME_INITIALIZATION_SNAPSHOT.md)、[cold数组](docs/CLIENT_72_COLD_ARRAYS_SNAPSHOT.md)、[资源槽](docs/CLIENT_72_LIVE_RESOURCE_CONTRACT.md) |
| SDK观察候选与HW分界成果（归D02-R2） | 已完成 | 标准API自有夹具/ETW过滤/WinHTTP无事件结果及HW退出边界已记录；不证明SDK观察覆盖，不默认重试。 | [观察夹具](docs/CLIENT_72_SDK_OBSERVER_DESIGN.md)、[ETW](docs/CLIENT_72_SDK_ETW_OBSERVER.md)、[WinHTTP](docs/CLIENT_72_WINHTTP_ETW_FIXTURE.md)、[HW分界](docs/CLIENT_72_D02_HW_EXIT.md) |
| SDK transport/login/Crypto/response限定成果（归D02-R2） | 已完成 | 准确请求所有权、原生key/算法/route、session单token规格；成功本服连接未验；DTO独立修复见R2-SESSION。 | [transport](docs/CLIENT_72_SDK_TRANSPORT.md)、[登录链](docs/CLIENT_72_SDK_LOGIN_CHAIN.md)、[Crypto](docs/CLIENT_72_SDK_CRYPTO_CONTRACT.md)、[response](docs/CLIENT_72_SDK_RESPONSE_CONTRACT.md) |
| SDK key核心及采样器准备（归D02-R1/R2/R4） | 已完成 | 未启用核心自有ABI、有限根/头采样工具已验；父descriptor已由后继补取，新增原生桥接见下项，实际加载仍未验。 | [key ABI](docs/CLIENT_72_SDK_KEY_INPUT_ABI.md)、[采样器](docs/CLIENT_72_ENTRY_CONTEXT_SNAPSHOT.md) |
| R2-KEY-native-bridge | 已完成 | 同一Rust核心实际供给准确caller→原PEM/RSA/Base64，保持入口状态/原返回及拒绝；限定离线桥接，不证明loaded、自然输入、hook wrapper或整DLL。 | [原生桥接](docs/CLIENT_72_SDK_KEY_NATIVE_BRIDGE.md) |
| R2-ROUTE-loader | 已完成 | 准确SDKDelegate.LoadFile→包内TextAsset、实际配置与url:string/map/app_key片段；主combo builder直接读初始化map。完整Init、自然环境选择、签名与本服请求未验。 | [配置loader](docs/CLIENT_72_SDK_ROUTE_LOADER.md) |
| R2-ROUTE-SIGN | 已完成 | 原生HMAC-SHA256字节合同/准确caller与外层序列化；固定Java源码接收条件已审。完整builder/网络、自然配置/请求和运行HTTP接受未验，不新增无依据验签策略。 | [combo签名](docs/CLIENT_72_SDK_COMBO_SIGNATURE.md) |
| R2-NATURAL-CONFIG-contract | 已完成 | 准确Config root/env/map writer/reader与owned不可变collector；原持锁URL事件及key参数事件已定位。自然Init完成/teardown区间未闭，禁止外部异步深读，未取得自然env/key/route。 | [配置生命周期合同](docs/CLIENT_72_SDK_NATURAL_CONFIG.md) |
| F04-LOADER-CLEAN-owned | 已完成 | 独立零hook入口实际编译，自有Win32夹具核47导出、透明ABI/原调用和4类真实异常拒绝；未加载实际客户端DLL，完整性/实际时序与自然SDK另验。 | [透明加载](docs/CLIENT_72_CLEAN_LOADER.md) |
| F04-LOADER-CLEAN-live | 已完成 | 实际透明proxy/stock加载、47目标表与SDK Crypto/两caller原窗一致；登录页正常、正常退出后恢复stock。只核零hook加载/绑定，未启用key/trust/route或认证。 | [真实加载](docs/CLIENT_72_CLEAN_LOADER.md#真实零hook加载观察器修正与恢复) |
| 认证日志与InstanceRoot前置（归D02-R2/R5） | 已完成 | 三个认证类日志修复和数据隔离回归、独立运行目录入口已验；DefaultAuthenticator遗留日志已按R2-LOG修复，真实部署未验。 | [日志](docs/CLIENT_72_AUTH_LOGGING.md)、[隔离入口](docs/CLIENT_72_PORTABLE_ISOLATION.md) |
| R2-CONFIGSYNC-EVENT | 已完成 | 固定producer、原ctor/OS转换、dispatcher及name负例至local_config writer入口已验；自然caller/writer返回未验。 | [原始证据与模型](docs/CLIENT_72_SDK_CONFIGSYNC_EVENT.md) |
| R2-CONFIGSYNC-OBSERVE-owned | 已完成 | 独立DLL构建与owned ABI、实际搬移/恢复及输入守卫已验；自然目标事件另验。 | [实际构建与限定验收](docs/CLIENT_72_SDK_CONFIGSYNC_OBSERVER.md#主线实际构建与自有验证) |
| R2-CONFIGSYNC-NATURAL-TRIGGER | 已完成 | 注册、once、插件槽、literal及条件gate静态来源已验；自然选择、执行时序及本服route未验。 | [准确边界/后继](docs/CLIENT_72_SDK_CONFIGSYNC_NATURAL_TRIGGER.md) |
| R2-CONFIGSYNC-ACCOUNT-CALLBACK | 已完成 | 两个账号回调与单例fallback的准确静态先后关系已验；条件路径不等于自然执行，完整callback/route未验。 | [准确范围及下一接口](docs/CLIENT_72_SDK_CONFIGSYNC_ACCOUNT_CALLBACK.md) |
| R2-CONFIGSYNC-FALLBACK-ENTRY | 已完成 | fallback至40416具名视图入口的静态分派已验；自然业务与完整注册来源未验。 | [原始边与下一输入](docs/CLIENT_72_SDK_CONFIGSYNC_FALLBACK_ENTRY.md) |
| R2-CONFIGSYNC-SHOWLOGINVIEW | 已完成 | ShowLoginView至准确插件UI委托分派的类型/调用边已验；实际注册producer由未完成父项继续。 | [准确类型/来源/首缺](docs/CLIENT_72_SDK_CONFIGSYNC_LOGIN_VIEW.md) |
| R2-CONFIGSYNC-SCALAR-OBSERVE | 已完成 | 新epoch有来源的四次1B自然gate输入已取并核保护；仅标量事实，不代验条件后继。 | [来源/实测/未知](docs/CLIENT_72_SDK_CONFIGSYNC_SCALAR_OBSERVATION.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE-owned | 已完成 | 新单EXE桥的来源、独立构建、导出、自有ABI及完整搬移/恢复已验；不代验自然Marshal/SDK返回或分类成功。 | [实际构建、ABI与边界](docs/CLIENT_72_SDK_CONFIGSYNC_EARLY_BRIDGE.md) |
| R2-CONFIGSYNC-EARLY-LIVE-ADAPTER | 已完成 | 新只读adapter的owned正反和真实文件门禁已验；自然事件结果由EARLY-BRIDGE父项维护。 | [实际范围与现场未知](docs/CLIENT_72_SDK_CONFIGSYNC_EARLY_BRIDGE.md#新现场adapter的owned与真实文件门禁) |
| R2-CONFIGSYNC-EARLY-BRIDGE（离线超限归因步骤） | 已完成 | live/accepted/源/构建/实际artifact同epoch及caller/两managed String/Marshal表示已核；旧5只来自payload的两个限额出口，18个owned Python模型/13检查及主线第三同双fresh。自然具体出口、原Marshal执行、分类/Init/route仍未验。 | [归因、边界和独立split规格](docs/CLIENT_72_SDK_EARLY_CLASSIFICATION_AUDIT.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE（独立11/12产物及现场准备步骤） | 已完成 | 最小分流保持旧源和限额；新DLL/EXE实际构建、两fresh各23个真实owned、30项检查、accepted绑定及真实隔离文件门禁通过；主线新CheckOnly与独立审核通过。未安装/启动本轮客户端，实时ready和自然终态仍待取得。 | [实际产物、预检及未验边界](docs/CLIENT_72_SDK_EARLY_SPLIT_OBSERVER.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE（split自然预算出口区分步骤） | 已完成 | 新实时确认下准确caller终态11，45监测/9同源读取、完整代码绑定及纯重放经16项主线审核；用户正常页/正常退出，stock和保护恢复保持。只区分本epoch预算出口，不验JSON分类/Marshal/Init/route或父项；旧5仍未知具体出口。 | [实际终态、恢复及未验范围](docs/CLIENT_72_SDK_EARLY_SPLIT_OBSERVER.md#新实时确认的实际终态11与恢复) |
| R2-CONFIGSYNC-EARLY-BRIDGE（language输入与前缀后继规格步骤） | 已完成 | 两个准确完整原窗、695字段模型及24项来源投影、13项前缀正反、12测试和主线三fresh/13审核通过；language结构足以使自有合法候选超预算。仅离线来源/规格，不确认自然language位置、完整serializer、NUL/Marshal/Init/route。 | [来源、实施合同及新实验边界](docs/CLIENT_72_SDK_SCAN_LIMIT_FOLLOWUP.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE（独立前缀产物与文件门禁步骤） | 已完成 | 原读取限额内借用已有副本，独立DLL/fixture实际release构建、两fresh各54例ABI、51EAT/72B DATA/完整284B、六源/86pin及主线14审核通过；新adapter四SHA绑定、33检查和真实隔离布局文件门禁通过。未安装GAME_PATH或取得新自然13/11，不代验完整payload/Marshal/Init/route。 | [实际构建、ABI及门禁](docs/CLIENT_72_SDK_CONFIG_PREFIX_OBSERVER.md#主线实际构建abi及文件门禁验收) |
| R2-CONFIGSYNC-EARLY-BRIDGE（自然必要前缀13观察步骤） | 已完成 | 新ready下90秒/45monitor、9读1450B、完整原代码绑定及16项主线纯重放通过；自然cn-env-0/两reference等值已取，用户正常页/退出及stock/保护恢复通过。仅前缀，不代验完整payload/NUL、原Marshal/SDK writer/Init/route/认证。 | [实际事件、恢复及准确未验范围](docs/CLIENT_72_SDK_CONFIG_PREFIX_OBSERVER.md#新epoch自然13正常退出与stock恢复) |
| R2-CONFIGSYNC-EARLY-BRIDGE（writer窗口与整轮后继观察规格步骤） | 已完成 | 原5422B完整writer、21项静态投影与12项预算纯模型、主线8项审核通过；首次任意P0/P2唯一尝试，参数16读/37B、绑定17读/6409B、外采成功30读/10415B和拒绝1读/256B分别固定。仅规格；八点观察器未实现/构建/实机，unwind、自然Marshal/writer/Init/route及父项未验。 | [来源、修正、主线验收及未实施边界](docs/CLIENT_72_SDK_PREFIX_NATURAL_SUCCESSOR.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE、R2-ROUTE-CONFIG-SELECT（八点实施审核步骤） | 已完成 | 26项冻结材料审核与主线第三同字节，明确P0实际14B→C3、新loader窗口分块、首次尝试、writer-open、真实mask及三账本；旧原文/报告保持。已交Sol xhigh实施，实际产物/owned/collector/自然执行、unwind和业务均待验。 | [实施合同、覆盖纠正与出口](docs/CLIENT_72_D02_B_SDK_AUDIT_20261110.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE、R2-ROUTE-CONFIG-SELECT（八点观察器离线产物步骤） | 已完成 | 33源/105pin、独立DLL/fixture构建、规定两fresh owned及主线第三、16个near/far实际emitter、17测试、51导出/256B DATA及真实隔离文件门禁通过；新helper实际CheckOnly匹配保护/输入且进程端口空。自然现场尚待新ready，unwind/完整Marshal/writer/Init/route及业务不由owned通过代验。 | [产物SHA、accepted、复现与现场出口](docs/CLIENT_72_SDK_CONFIG_STAGE_OBSERVER.md) |
| R2-CONFIGSYNC-EARLY-BRIDGE、R2-ROUTE-CONFIG-SELECT（八点自然阶段观察步骤） | 已完成 | 新ready的90秒/45sample、外采30读10415B/512及15项原字节审核通过，完整代码/8trampoline/转发绑定、256B状态前后相同；同配置parser/env写入与原生返回成立，用户正常页/退出，stock/保护恢复。P5/P6仅分支，完整payload、Init/route/认证、unwind及D-02不晋级。 | [实际自然证据及边界](docs/CLIENT_72_SDK_CONFIG_STAGE_OBSERVER.md#本轮自然阶段观察与恢复) |
| R2-CONFIG-ROUTE-WRITE（阶段语义与五点原锁后继规格步骤） | 已完成 | 本轮原DATA独立语义/原320B窗口+pdata及38项冻结投影、主线第三通过；明确stored DWORD0不等于managed env选择，P5/P6仅分支。首次M0/同writer帧/P7关闭、只读map分类/新三预算及ABI反例已冻结。定向公开heads唯一main同旧commit，有限范围无新增量；未实现/构建/实机新observer，不关闭route业务。 | [自然语义和明确实施合同](docs/CLIENT_72_SDK_STAGE_NATURAL_SUCCESSOR_20261110.md) |
| R2-CONFIG-ROUTE-WRITE（五点自然锁内map观察步骤） | 已完成 | 新实时ready90秒/45样本，25读9262B和16项原件审核通过；DATA首末同256B、facts1023、同writer帧/代码/返回绑定，comboLogin非空=固定cn-route-env-0；用户正常页/退出、stock/保护恢复。非managed env/实际route采用/auth，不关闭R2或D-02。 | [实际证据/恢复与准确分界](docs/CLIENT_72_SDK_CONFIG_ROUTE_OBSERVER.md#本轮自然锁内map观察与恢复) |
| R2-ROUTE-CONFIG-SELECT（托管必要消费者静态步骤） | 已完成 | 新核原AOT/真实328B和caller、单字节? literal；89项冻结审计/主线第三20425B一致。40108读托管URLConfig.comboLogin+?→PostRequest33107，不读SDK native map；当前state/Init/env/真正请求采用及GC/ABI/EH仍未验。 | [准确调用、来源与后继](docs/CLIENT_72_SDK_ROUTE_CONSUMER_SUCCESSOR_20261110.md) |
| R2-ROUTE-MANAGED-STOCK-SPEC（归R2-ROUTE-CONFIG-SELECT） | 已完成 | 21pin/68项来源审计与主线独立第三13884B一致；真实typed/String布局和13项固定读图、unknown统一脱敏、A/B独立final与98/2304合计上界已冻结。仅stock当前值输入规格，不证明SetEnv/GetURL/请求采用、GC所有权或认证。 | [已审规格、隐私及预算](docs/CLIENT_72_SDK_MANAGED_ROUTE_STOCK_SPEC_20261110.md) |
| AB-STOCK-CURRENT-INPUTS（归F-03d3f4d2/R2-ROUTE-CONFIG-SELECT） | 已完成 | 有界B reader/adapter、新协调器和门禁离线实现；两fresh及主线各111人工项通过，第三98读2050B，source/晚失败脱敏和一次cleanup独立审核通过。旧A18源/四profile字节保持；总cap98/2304，尚无实际CheckOnly/自然输入/目标CPU或副本写权限。 | [实现、主审及现场前置](docs/CLIENT_72_SDK_MANAGED_ROUTE_STOCK_COLLECTOR_20261110.md) |
| AB-STOCK-PREPARATION（归F-03d3f4d2/R2-ROUTE-CONFIG-SELECT） | 已完成 | 主线63源并集seal、实际CheckOnly及8项独立审核通过，五stock/载体/13保护/portable14/缺席与进程固定监听空；该准备不代验现场；之后本轮实际0RPM失败见299后继。新轮必须新实时确认，A/B各自身份/final/敏感seal保持。 | [实现与门禁入口](docs/CLIENT_72_SDK_MANAGED_ROUTE_STOCK_COLLECTOR_20261110.md) |
| AB-STOCK-STARTUP-299（归F-03d3f4d2/R2-ROUTE-CONFIG-SELECT） | 已完成 | 首次模块聚合错误299/空items、A/B0RPM、自有句柄各关闭一次及用户退出后stock/保护/进程监听复核均已核；184项静态/主线第三通过。仅关闭失败归因边界及最窄v2规格，Create/First子阶段、唯一根因和页面表现未知；独立v2及后续实际采集另项已验，本失败不代验自然输入，不能重用本轮ready。 | [原件、API证据与v2严格边界](docs/CLIENT_72_D02_STOCK_STARTUP_299_20261110.md) |
| AB-STOCK-STARTUP-299-V2（离线实现及实际准备步骤） | 已完成 | 最小新flow仅pre-first299空结果及窗口末None拒绝，原helpers/A4/B2字节保持；两fresh及主线各136人工项、四PS Parser及主线第三98读2078B通过。83源seal/真实CheckOnly与8项独立门禁核对通过，尚不代验自然输入或业务。 | [新实现、审核与门禁](docs/CLIENT_72_D02_AB_STOCK_V2_20261110.md) |
| AB-STOCK-V2-CAPTURE（归F-03d3f4d2/R2-ROUTE-CONFIG-SELECT） | 已完成 | 新ready45次/90秒，实际A62/1388+B26/432=88读1820B；9项原件重构/ABI/身份/末核/83源快照审核通过，用户无异常退出且stock/保护/进程监听核验通过。32B head/HashSet身份与托管当前env/base0仅为候选；未执行native或证明GC/自然caller/route采用/认证。 | [同一v2合同](docs/CLIENT_72_D02_AB_STOCK_V2_20261110.md) |
| R2-CONFIG-ROUTE-WRITE（五点独立产物准入步骤） | 已完成 | 36传递源/147冻结pin、独立DLL/EXE构建、两fresh owned及第三、10 near/far实际emitter/真实mini帧、24 Python、51导出/256B DATA及真实隔离文件门禁通过；新helper实际CheckOnly通过。新实时ready/自然map仍待，完整writer/真实SDK锁/route采用/auth/EH不由owned代验。 | [实际产物/accepted/准备与边界](docs/CLIENT_72_SDK_CONFIG_ROUTE_OBSERVER.md) |
| F04-LOADER-CLEAN 独立透明入口 | 已完成 | 零hook独立透明入口、实际stock/SDK绑定、正常登录页/退出恢复已验；key/trust/route自然链和完整校验另验。 | [计划/产物/真实范围](docs/CLIENT_72_CLEAN_LOADER.md) |
| R2-KEY-LOAD-OBSERVE 固定只读观察 | 已完成 | 窄key入口实际安装、精确代码/转发/回调绑定、正常页及退出恢复已验；未证明自然key调用、route或完整校验。 | [实际结果、来源与边界](docs/CLIENT_72_KEY_LOADER_OBSERVATION.md) |
| R2-ROUTE-CONFIG-SELECT/B-REQUEST-ADOPTION（静态选择子出口） | 已完成 | 唯一LoadLocalConfig2481B/504条、三caller、readOnly仅跳local回写与cp_app_id匹配client.gameKey、两次加载和返回边界已核。来源23/最终31断言及主线23复核通过；自然选择/实际采用未验。 | [精确选择与请求入口规格](docs/CLIENT_72_D02_ROUTE_ADOPTION_20261110.md) |
| R2-ROUTE-CONFIG-SELECT/FINAL-URL-CLASSIFIER | 已完成 | 八个完整base各加单?的纯UTF16LE分类器及七直接源门禁，18测试/独立8正例1090扰动/7漂移主线复核通过；只输出固定类/unknown。未观察33107/HTTP或认证，不解锁hook。 | [实现、来源与边界](docs/CLIENT_72_SDK_FINAL_ROUTE_CLASSIFIER_20261110B.md) |


### 6.4 资源、脚本、保存与回归

| 任务ID / 成果 | 状态 | 已验成果 / 保留边界 | 证据 |
| --- | --- | --- | --- |
| E-01/R3-ITEM-AFFIX-FRAGMENT | 已完成 | 11101真实有序[0,0]经原数组AOT/解码至具名EquipAffix getter；独立11501非零/零对照通过。三新进程同字节、17检查/11审核/91pin；完整Item/gadget/正引用未验。 | [真实数组与来源](docs/CLIENT_72_D02_ITEM_ENTRY_RESOURCE.md) |
| E-01/R3-JAVA-ITEM-AFFIX-FRAGMENT | 已完成 | 真实ResourceLoader/onLoad→Item map→GameItem身份/count/level/affixes及受控对照3项通过；源[0,0]/NONE保持，map恢复、loadedAll不变。完整Item/实体/保存未验。 | [Java真实加载与构造](docs/CLIENT_72_D02_ITEM_AFFIX_JAVA_LOAD.md) |
| E-01/R3-JAVA-AVATAR-FRAGMENT | 已完成 | 100Curve/两出生HP/grow片段真实ResourceLoader加载，18个native float32结果一致；4项及32隔离回归通过，缺binding/curve真实拒绝，loadedAll保持。本项仅HP/grow，critical后继另已验；完整行/能力/自然加载与保存未验。 | [真实Java加载](docs/CLIENT_72_D02_AVATAR_JAVA_LOAD.md) |
| E-01/R3-AVATAR-ENTRY | 已完成 | Avatar.hpBase/grow binding通过真实选中行→原缓存查询片段→完整helper闭合；主线第三与双跑同原字节、18项和99源pin已核。本项不覆盖critical，后继许可另列；完整Avatar/Item/能力/自然加载/revision未验。 | [属性与成长](docs/CLIENT_72_D02_AVATAR_ENTRY_RESOURCE.md) |
| E-01/R3-UPSTREAM-D02、R2-PATCH-DELTA | 已完成 | 两固定远端/35个原文件及必要行、消费者、许可和差异分类已审；未有可直接解除首缺的合入增量。未知省略/Quest/Scene不造alias，WinHTTP缺覆盖暂缓；不代验父项。 | [定向审计](docs/CLIENT_72_D02_UPSTREAM_REVIEW.md) |
| E-01/R3-JAVA-WEAPON-FRAGMENT | 已完成 | 准确原生派生11101两属性、100曲线、零阶原列表经真实 Gson/onLoad，4项Java通过；NONE/0保留与上游缺省过滤分列。完整Item/loadAll/revision/保存未验。 | [Java片段加载](docs/CLIENT_72_D02_WEAPON_JAVA_LOAD.md) |
| E-01a、E-01b | 已完成 | 固定候选七表、scene3依赖及引用审计；不代表整套资源已安装/可用。 | [候选](docs/CLIENT_72_RESOURCE_CANDIDATE.md)、[scene3](docs/CLIENT_72_SCENE_RESOURCES.md) |
| E-01c2、E-01c3、E-01c4、E-01c5、E-01c6 | 已完成 | Lua序列化修复、API来源、owner UID、生产加载审计及嵌套require恢复；local作用域/必要接口/完整加载仍开放。 | [序列化](docs/CLIENT_72_SCENE_SERIALIZATION_FIXES.md)、[API](docs/CLIENT_72_SCENE_SCRIPT_API.md)、[生产加载](docs/CLIENT_72_PRODUCTION_SCENE_LOAD.md)、[require](docs/CLIENT_72_REQUIRE_BINDINGS_FIX.md) |
| E-01d1a、E-01d1b、E-01d1c、E-01d1d、E-01d1e、E-01d1f、E-01d1g、E-01d1h、E-01d1i、E-01d1j、E-01d1k、E-01d1l | 已完成 | 受控角色/depot、出生网格、能力、实际Lua事件、日常保存和初始实体/自动回调；只验对应suite/路径，不代替所有异步后代或revision。 | [最小资源各子合同](docs/CLIENT_72_D02_MINIMUM_RESOURCES.md) |
| E-01d1m、E-01d1n1、E-01d1n2、E-01d1n3 | 已完成 | Quest台账、目标303拒绝/Fallback、启动/冷读和未就绪门禁、Chapter/通知及反射加载清单；正向manager恢复未验。 | [任务台账](docs/CLIENT_72_D02_QUEST_ENTRY.md)、[恢复](docs/CLIENT_72_D02_QUEST_303_RECOVERY.md)、[保存](docs/CLIENT_72_D02_QUEST_303_START.md)、[Chapter](docs/CLIENT_72_D02_CHAPTER_LOAD.md) |
| E-01/E-01c/E-01d1（三文件自然loadAll首诊步骤） | 已完成 | 一个新JVM真实调用并自然返回；实际路径、全部拒绝、Chapter436/Quest63、10WARN/326ERROR、temp唯一变化及原数据保持经14项主线审核。只验固定部分输入，不验resourceReady、正向Manager或完整资源兼容。 | [实际首诊与复现](docs/CLIENT_72_D02_NATURAL_LOAD_FIRST_RUN.md) |
| E-01/E-01c/E-01d1（自然303局部保存与正向Manager恢复步骤） | 已完成 | 同一新JVM自然加载后，真实Morphia/save/新Player/QuestManager将63项全部绑定真实定义，状态/进度/归属保持；未就绪0写且BSON相同，正向队列精确两写、原始类型/字节差异经独立复核，Mongo正常停库。仅局部303，不验全局resourceReady、入场事件/场景或客户端重登。 | [实际执行、BSON边界及24项审核](docs/CLIENT_72_D02_QUEST_303_NATURAL_REBIND.md) |
| 资源深度parser/条件子合同（归E-01/R3） | 已完成 | depot/条件parser、LHFO嵌套及GameTime/LuaNotify分界，Ornament标量/条件数组；未知JSON映射和接受规则未补造。 | [深度资源](docs/CLIENT_72_RESOURCE_DEEP_RESEARCH.md)、[Ornament](docs/CLIENT_72_AVATAR_ORNAMENT_CONTRACT.md)、[条件数组](docs/CLIENT_72_AVATAR_ORNAMENT_CONDITIONS.md) |
| 实际资源来源/容器/索引/locator链（归E-01/R3） | 已完成 | 候选/真实来源、provider、索引reader、blob头、完整payload/index与精确表字节取得；不再等待同一已解决解包问题。 | [来源](docs/CLIENT_72_D02_RESOURCE_SOURCES.md)、[provider](docs/CLIENT_72_RESOURCE_PROVIDER.md)、[索引入口](docs/CLIENT_72_RESOURCE_INDEX.md)、[native reader](docs/CLIENT_72_RESOURCE_INDEX_NATIVE.md)、[blob](docs/CLIENT_72_RESOURCE_BLOB.md)、[payload](docs/CLIENT_72_RESOURCE_PAYLOAD.md)、[完整索引](docs/CLIENT_72_RESOURCE_PAYLOAD_INDEX.md)、[locator](docs/CLIENT_72_RESOURCE_LOCATOR.md) |
| E-01-bridge-energy（归R3） | 已完成 | Avatar/Depot/Skill七字段通过同缓存原生角色索引/能量槽/元素消费者闭合，正引用/缺省/枚举和片段转换已验；完整资源行/加载/revision/保存恢复仍未验。 | [字段桥梁](docs/CLIENT_72_D02_RESOURCE_BRIDGE.md) |
| E-01-bridge-equipment（归R3） | 已完成 | 真实三表完整解析、初始武器/两项属性/同源曲线及突破ID消费者闭合；非零/64位/float32及缺引用/未知枚举拒绝已验。仍为字段片段，完整Java行/突破加成/加载/revision未验。 | [装备字段桥梁](docs/CLIENT_72_D02_EQUIPMENT_RESOURCE.md) |
| R1-SAMPLE-INTERFACES（归R1/Scene） | 已完成 | 新ready原始启动后准确取得1169/1249头及metadata、双零compact与loader TLS索引；预算、五输入及正常退出已验。线程TLS、消费时序及完整构造未验。 | [输入合同与实测](docs/CLIENT_72_INTERFACE_CONTEXT_SNAPSHOT.md) |
| R2-COMBO-HTTP（归R2-ROUTE） | 已完成 | 准确原生builder/sign字节→真实Java HTTP/default认证/Mongo同步保存→原生parser/formatter六正反例及关联测试已验；64位归属和失败BSON保持。全UI/callback/自然SDK/route未验。 | [真实接收与主线证明](docs/CLIENT_72_SDK_COMBO_HTTP.md) |
| R2-KEY-LOAD-owned（归R2-KEY/F-04） | 已完成 | 独立47转发/key入口、准确SDK三窗/材料守卫、release构建/精确EAT49及主线owned正反例通过；冻结core/clean/依赖对应同一来源。本子项只验owned，实际安装见OBS；自然caller/完整校验/route未验。 | [构建及主线第三](docs/CLIENT_72_SDK_KEY_LOADER.md) |
| F-03d3f4d2-fresh-interfaces（归R1） | 已完成 | 原前缀保持、第三类壳/接口写回及方法64B存储已验；两case/不可变index投影和准确method-cache首缺有来源。完整初始化/default/Handler未完成，最小roots合同不等于采样ready。 | [主线第三与边界](docs/CLIENT_72_AVATAR_FRESH_INTERFACES.md) |
| E-01-PROMOTE-ENTRY（归R3） | 已完成 | 三真实表完整解析及出生用途2个Promote/100个AvatarCurve输入、Avatar批准字段片段、完整Weapon属性6例/非零突破已验；宿主白名单同集合修复验证。完整Avatar/Item、加载/revision/保存恢复未验。 | [主线第三与准确用途](docs/CLIENT_72_D02_PROMOTE_RESOURCE.md) |
| E-01/E-01c/E-01d1（出生Promote真实Java加载步骤） | 已完成 | 固定第三报告→两个出生Promote真实ResourceLoader/onLoad与复合键通过，保留原零槽后由onLoad过滤；同上下文复验100Curve。四Python/两JUnit及保护/class-init检查通过；缺曲线由投影入口拒绝，loader不自行保证。非完整Avatar/Item/资源就绪、manager保存或D-02验收。 | [实际加载、拒绝及限制](docs/CLIENT_72_D02_C_RESOURCE_LOAD_20261110.md) |
| E-01/R3-AVATAR-ITEM-ENTRY（实际必读字段分层与桥接规格步骤） | 已完成 | 排重既有目标/Java成果，按出生/旧号恢复/SceneTeam分层必要字段；已批七字段真实绑定后继交Terra及主线执行。该步最初的talents+28缺口已由后续原typed职责和真实Java子项闭合；proud的group/level及提升门槛仍未知，不用零值或Java默认批准；不是完整资源验收。 | [准确字段分层、可执行后继与未知](docs/CLIENT_72_D02_AVATAR_ITEM_GAPS_20261110.md) |
| E-01-DEPOT-PROUD-BOUNDED（归E-01/R3-ITEM-ENTRY） | 已完成 | 20pin/三完整原窗与主线第三20977B同字节；Depot+18/81127+14→ProudSkillInherent→typed pair80067查询/output U32成立。另一个304B候选没有threshold消费；pair业务身份、81127+10与Avatar promote比较和Java算式等价未闭，不批准proud投影或完整资源。 | [有界结论、排除项及准确缺输入](docs/CLIENT_72_D02_DEPOT_PROUD_SUCCESSOR_20261110.md) |
| E-01d1/R3-AVATAR-LOCAL-SAVE | 已完成 | 真实公开Crypto/Account/GameSession/Player入口、两出生AvatarStorage保存→全新Player冷绑定、缺定义/不同UID三项JDK17/Mongo通过；主线11审核、12原BSON和既有更新/无重复创建核实，owned Mongo正常关闭保留库。仅未装备AvatarStorage，不代验正向Inventory、完整资源/Player、场景或重登。 | [实际运行、恢复与范围](docs/CLIENT_72_D02_AVATAR_LOCAL_SAVE_JAVA_20261110.md) |
| E-01d1/R3-EQUIPPED-LOCAL-RESTORE | 已完成 | 两出生各自自然默认装备、受控Player历史/保存及各两次新Player的AvatarStorage→Inventory/postLoad冷绑定，缺11101的真实NPE/BSON保留及同源恢复后原Item重绑，三JUnit和13项原件审核通过。27份BSON核原ID/owner/HP与装备关系、五次Item Guard推进，无重复发放/冷Player写；owned Mongo正常关闭保留库。仅无World局部恢复，不代验完整资源/Player、Entity/场景或客户端重登。 | [实际运行、两次准备纠正与边界](docs/CLIENT_72_D02_EQUIPPED_LOCAL_RESTORE_20261110.md) |
| E-01d1/R3-OLD-SAVE-DEPENDENCIES（离线driver子出口） | 已完成 | 5项Python及主线54输入/九组BSON与Extended JSON投影、独立引用/CLI/hash拒绝通过；inactive Item不产生装备依赖。用户已选定旧号，真实一致快照、额外资源和完整恢复仍未验，不代关R3。 | [源码合同、实际审核与范围](docs/CLIENT_72_D02_OLD_SAVE_DEPENDENCIES_20261110.md) |
| E-01/R3-AVATAR-ITEM-ENTRY（七字段真实Java绑定步骤） | 已完成 | 固定三报告/9片段→真实Skill/Depot/Curve/Avatar loader，四JUnit通过；出生同对象绑定与704→10068/Wind、缺引用/错误顺序的真实null/None行为均已实测。map/fetters/loadedAll及保护保持；704普通引用未齐，完整Avatar/能力/revision/保存/g2未验。 | [实际四项、隔离与未验范围](docs/CLIENT_72_D02_C_BRIDGE_JAVA_20261110.md) |
| E-01/R3-ITEM-ENTRY（出生武器真实库存保存步骤） | 已完成 | 同源六键Item11101经真实loader/GameItem普通保存队列/Guard→Mongo→新Player空Avatar/Inventory恢复，三JUnit及12项原件审核通过。两实例及cold Guard重存revision2、缺定义原BSON保持并恢复、UID隔离及冷读零写成立；仅未装备武器，不验完整Avatar/Player/场景/客户端。 | [实际运行、隔离恢复及范围](docs/CLIENT_72_D02_ITEM_SAVE_JAVA_20261110.md) |
| Java资源目的合同（归E-01/R3） | 已完成 | 加载类/字段/准确消费者及缺Weapon失败位置已审；不是target→Java转换许可。 | [Java目的](docs/CLIENT_72_D02_JAVA_RESOURCE_CONTRACT.md) |
| V-01a、V-01b、V-01c、V-01d | 已完成 | 能力便携路径、家具保存/加工手册夹具及家具资源路径修复；完整V-01保持未完成。 | [账号保存验证](docs/ACCOUNT_AND_SAVE_OPERATIONS.md)、[离线收口](docs/CLIENT_72_SOL_TERRA_OFFLINE.md) |
| E-01/R3-WEAPON-GADGET-BRIDGE | 已完成 | 出生武器gadgetId经原具名查询绑定50011101，交换/高位/缺缓存/空对象边界已验；完整factory、配置和生命周期未验。 | [实际来源/片段/边界](docs/CLIENT_72_D02_WEAPON_GADGET_BRIDGE.md) |
| E-01/R3-JAVA-GADGET-FRAGMENT | 已完成 | 同源片段经真实ResourceLoader/onLoad与GameItem绑定，缺键零值对照、map恢复和loadedAll保持已验；未加载Gadget配置。 | [实际加载与后继](docs/CLIENT_72_D02_WEAPON_GADGET_JAVA_LOAD.md) |
| E-01/R3-ITEM-ENTRY（Gadget/ability完整候选原文审计步骤） | 已完成 | 两份完整固定原文、27pin、commit/tree/blob、7精确Java字段/全部未知叶、三fresh同字节及12检查通过；选定来源已仓库外无checkout克隆。名称闭包不代验目标内容、未知对象可省略或revision。 | [准确来源、字段损失及主线审核](docs/CLIENT_72_D02_GADGET_ABILITY_CANDIDATE.md) |
| E-01/R3-ITEM-ENTRY（局部Gadget配置与ability真实Java加载步骤） | 已完成 | 独立配置/CWD下真实两loader及实际map的三JUnit通过、loadedAll不变；字段损失/未知对象与缺键负向保留，主线13审核及数据/进程/端口保护通过。未执行EntityWeapon/ability生命周期、loadAll、目标客户端或保存，不升父项。 | [实际Java加载、XML及未验范围](docs/CLIENT_72_D02_GADGET_ABILITY_CANDIDATE.md#主线第三审核外部克隆及实际java加载) |
| E-01/R3-ITEM-ENTRY（Gadget/ability未知对象与parser静态收敛步骤） | 已完成 | 原名称注册、196497字典parser及196498选择/工厂/动态分派的准确来源与有限静态表达式已审；双/三fresh、分阶段19/10/8个pin和主线定向审核保持。reader/原asset/实际类与字段语义未闭，不批准LKLP/defense/common alias；514570后继未批准或提取，不升父项。 | [静态结果、主线审核及停止边界](docs/CLIENT_72_GADGET_ABILITY_SEMANTICS.md) |
| E-01/R3-ITEM-ENTRY（D-02必要性与Metal局部Java生命周期步骤） | 已完成 | 31源必要性矩阵及两项真实loader→EntityWeapon→Ability初始化/root局部生命周期通过，Gradle/XML与15项主线审核通过；baseline sync0/owned参数sync1、null定义槽负向、executor0及保护/进程端口核验保持。不代验目标字段/空ability许可、普通入场完整sender、保存、客户端或父项。 | [实际测试、完整证据与未验边界](docs/CLIENT_72_D02_GADGET_NECESSITY.md#主线实际java执行与验收) |
| E-01/R3-GADGET-CONFIG-KEY | 已完成 | 真实+58键至原查询/hash前缀及交换/+50对照/空边界已验；本项止String Default前。jsonName职责另由后继批准，配置实际命中仍未验。 | [真实路由、模型和最小后继](docs/CLIENT_72_D02_GADGET_CONFIG_KEY.md) |
| E-01/R3-GADGET-JSONNAME-LOOKUP-KEY | 已完成 | typed实体配置域与Java key消费者对应，仅批准出生{id,jsonName}；真实loader/getJsonName、缺省null、map恢复/loadedAll保持已验。旧false报告冻结，配置内容/命中/ability未验。 | [新许可/实际加载/边界](docs/CLIENT_72_D02_GADGET_LOOKUP_KEY_JAVA_LOAD.md) |
| E-01/R3-CRITICAL-SEMANTICS | 已完成 | 共享基础字段经原installer及typed属性20/22确认为critical/criticalHurt基础贡献；+78、自然Avatar/modifier及完整行未验。 | [原静态范围](docs/CLIENT_72_D02_AVATAR_CRITICAL_RESOURCE.md)、[实际许可](docs/CLIENT_72_D02_AVATAR_CRITICAL_INSTALLER.md) |
| E-01/R3-CRITICAL-INSTALLER | 已完成 | 两出生真实行、独立输入、交换/零/缺接收者通过原installer/dispatcher/getter；自然构造和完整父项未验。 | [实际范围/模型/原生许可](docs/CLIENT_72_D02_AVATAR_CRITICAL_INSTALLER.md) |
| E-01/R3-JAVA-CRITICAL-FRAGMENT | 已完成 | 同源critical/criticalHurt与HP/grow片段经真实loader/getter保留float32，交换/缺键对照及状态恢复已验；完整行/能力/保存未验。 | [实际Java加载及未验边界](docs/CLIENT_72_D02_AVATAR_CRITICAL_JAVA_LOAD.md) |
| E-01/R3-STRING-COMPARER-INPUT | 已完成 | String类型/继承/伴随槽、ABI与最小采集来源合同已验；未取得自然Default/Equals，两个后备未执行。 | [实际合同、继承/伴随槽与未验边界](docs/CLIENT_72_GADGET_STRING_COMPARER_INPUT.md) |
| E-01/R3-ITEM-ENTRY；F-03d3f4d2（String独立采集器实现步骤） | 已完成 | 冻结Dictionary carrier→实际Default/Equals的独立54读2704B collector及stock epoch adapter通过17测试、三同字节owned和16主线审核/当前来源门禁。只验人工输入及工具，未采自然图、未执行比较器/配置/ability或完成父项。 | [实现、主线证据及自然输入缺口](docs/CLIENT_72_STRING_CONSUMER_COLLECTOR.md) |
| E-01/R3-ITEM-ENTRY；F-03d3f4d2（String自然carrier诊断步骤） | 已完成 | 新实时stock采集取得真实Dictionary泛型owner、FindEntry零/getter非零、37项definition来源与精确末尾复读；15项主线纯重放审核及正常退出保护通过。v2按门禁partial，getter对象/完整String图/消费者仍未验，不晋级父项。 | [实际输入、停止及未知](docs/CLIENT_72_STRING_CONSUMER_COLLECTOR.md#新stock实测的lazy诊断与非零getter) |
| E-01/R3-NATURAL-LOAD-FIRST-AUDIT | 已完成 | 真实loadAll直接入口、路径/副作用、Lua执行域和独立JVM首诊规格已审；未选定首诊输入、未建driver或运行loadAll，不新增逐项审批/全资源P0要求。 | [准确结论与主线收尾](docs/CLIENT_72_D02_NATURAL_LOAD_FIRST_AUDIT.md) |
| E-01d1/R3-OLD-ACCOUNT-SNAPSHOT | 已完成 | 用户所选旧号在原物理证据副本的第二工作副本恢复并只读导出；唯一账号/Player及owner/claim关联通过，142源文件在当时前后保持，owned27027正常关闭。实际121角色/12254物品/471装备，原库/配置未写；仅快照，不验业务完整恢复或客户端。 | [复制、恢复、实际关系与边界](docs/CLIENT_72_D02_OLD_ACCOUNT_SNAPSHOT_20261110B.md) |


## 7. 旧任务归并与后续范围

旧ID保留用于检索/证据关联，不再维护第二套重复执行队列。合并是任务管理调整，不是技术验收通过。

| 旧父项/未闭分支 | 统一去向与保留条件 |
| --- | --- |
| A-03、B-02、B-03、C-01 | R4的依赖/三层/生成/最终收发；其子项成果见第6节，完整运行闭包仍待R6 |
| A-04、A-04d、A-04d4 | 作为R1/R2实际缺输入的支撑研究，不再独立追求“恢复全部客户端”；A-04d4仅旧分组名称，具体已验项仍保留 |
| B-02c、A-04d5n、B-02c9、F-02 | R2登录/分区及R4必要字段闭合；tag965等未知保持，先证明是否必需，不按旧研究顺序盲扩 |
| B-02e、F-03、F-03d1、F-03d1b、F-03d2、F-03d2b、F-03d3、F-03d3f2 | R1及R4的完整初始化/场景；PlayerData/Store已验切片保留，新增字段按实际必要性发卡 |
| D-01、D-01b、D-01b3、D-01b5、D-01b5g、F-04 | R2及R5；观察记录完成不等于SDK通过，成熟自有fixture不反复扩展，新自然实验须精确新问题 |
| E-01、E-01c、E-01d1 | R3；全资源/全Lua扫清不作为D-02默认前置，但必要缺口不省略 |
| F-05、D-02 | 同一最终验收归D02-R6，避免重复计数 |
| V-01 | 先做受影响回归与R4必需失败；完整全量结论独立保留，不以历史数字替代当前结果 |

| 后续任务 | 状态 | 启动依赖与验收 |
| --- | --- | --- |
| E-02 基础玩法/持久化扩验 | 待开始 | D-02后逐项移动、切队、传送、战斗/背包/角色及服务重启冷读，保留经济不变量 |
| E-03 既有玩法 | 待开始 | E-02后按任务/副本/深渊/商城/抽卡/邮件/家园专题闭合请求、资源、保存和客户端效果 |
| E-04 7.2新增内容 | 待开始 | E-01/E-02基础具备，来源可追溯、ID引用和新增处理逻辑逐内容实测 |

维护时同步更新当前工作包与已完成成果目录中的状态、完成标记、验收结论及证据链接；不要粘贴命令输出、长hash、PID、逐次失败、百分比估计或重复暂停指令。新增任务必须说明父项、阻塞出口和验收标准；已完成成果只能补充边界或新增后继，不能因重排删除。合并任务仍须保留各子项完成范围与未闭缺口，不以整组“已完成”覆盖尚未通过的子项。
