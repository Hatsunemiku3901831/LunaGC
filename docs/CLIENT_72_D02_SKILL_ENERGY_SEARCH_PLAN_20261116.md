# D-02 Skill / energy 定向复用检索计划

任务 `D02-SKILL-ENERGY-SEARCH-20261116`；规划由 GPT-6.1 Sol max 完成，公开检索交 GPT-6.1 Terra max。仅解除 D02-R3 必要依赖；本计划不是资源转换或执行许可。用户本次已恢复 D-02，旧文档中的暂停记录不覆盖本次授权，所有准确拒绝和未验边界继续保留。

## 要解决的准确缺口

| 出口 | 现有基础 | 本次应找的新增证据 |
| --- | --- | --- |
| 121 保存当前 Depot → Skill | 已验三键片段；369 个不同正引用中 2 个沿用旧片段 | 367 个新 Skill 的真实 `id/costElemType`；保留原 row/source 与元素域，不用候选 JSON 给默认值 |
| Depot 正向 energy 绑定 | 121 个正 energy 引用，只有 10068 旧片段 | 120 个新 energy Skill 的准确元素值及引用；`onLoad` 不得落入 None fallback |
| 同旧号 `AvatarStorage.postLoad → recalcStats` | Java 实读 energy Skill | 包括 10068 在内的 121 行 `costElemVal/specialEnergyMin/specialEnergyMax` 的 target 字段职责、值及独立非零 oracle；三个字段仍全部未闭 |
| Skill 合法逐行输入 | 已验 8309B parser、UInt32/Float/Int32 条件合同与 flags/String1 长度头 | 与固定目标版本一致的具名 consumer、独立表/解析实现、typed 初态或 reader/TLS 来源；ctor `m732415/149b59940` 仅缺同 PE 准确完整边界，不重试旧门、不猜长度 |

直接复用 121 Avatar 成长/critical 七键、两个限定真实 Java loader、Depot 三键、Talent/phase6、Float/Pair、启动修复和唯一 cold-cache 结果。不得重做已验窗口长度、371 行 flag / String1 长度盘点；保存 Skill 389 键与 Depot 369 需求分别记账。

## 先复用的公开与本机范围

先读 [UP-72 账本](CLIENT_72_UPSTREAM_LEDGER_20261114.md)、[67 分支](CLIENT_72_UPSTREAM_BRANCHES_20261114.md)、[固定文件](CLIENT_72_UPSTREAM_SOURCES_20261114.md)及 [D-02 定向审计](CLIENT_72_D02_UPSTREAM_REVIEW.md)。UP-72 仍在进行；本次不接长尾 N3，不重新跑其已完成仓库/README 搜索。

1. 优先审本机 `AstaPS-Resource` 固定缓存及已有 tree：`main@6cfecfb55f889f8e3b7156adb12809608db4f74b` / tree `3cf83488a94b47a018934e5f91c1bf7e93357aae`。`mechen/resources-7.2-runtime@9f1ae21ea6b4610fca4fde8484ae4316cb3860b1` 与 main 同树，PR #21 已合并，不能计独立来源；`update-quest-excel` 仅已知 Quest 增量。既有审计未为本次 121 energy 三字段建立 target 合同。
2. 在既有 12 个资源 Fork 清单中，先筛 README 的 Skill/energy/7.2/revision 声明，再枚举入选 Fork 的全部分支，核固定 commit/tree 与 Skill/Depot 文件差异；同树去重。查该资源库全部状态 PR/Issue 中的字段名、技能成本、特殊能量、7.2 资源和解析讨论。旧清单不等于所有 Fork 均已逐分支深审。
3. 对已验 `AstaPS@a49f51aa6f2787ffbc25cee46801b70dee860d23`、`LunaGC_7.2.0@acc7a0b48f44f3d51666c91f1125e8e1ee923ca4` 及其已列分支，只定向核 `AvatarSkillData`、Depot、Avatar 重算及独立解析/数据说明；先查现有缓存，已取文件不重下。其协议冲突不允许整包替换。
4. 既有 patch 对比为 `hk4e-patch-universal@2b589125874b0b96f8694bfad31e34811eba8573`；WinHTTP 差异和 A/B 冻结不能补 Skill 值。只复用审计结论，不爬 patch 寻找绕行或改补丁。

## 新检索顺序与关键词

所有发现先看仓库名称/描述/README，再看相关分支、Fork、全部状态 PR/Issue，最后读取明确相关文件；固定 URL、commit/tree/blob、SHA-256、UTC、页码、total/incomplete 与未覆盖原因。GitHub 仓库查询用 `is:public fork:true in:name,description,readme`；同查询同时相关性和更新时间排序，超过 1000 或 incomplete 时拆不重叠分片，不能宣称全网穷尽。

| 顺序 | 查询及对象 | 预期复用形式 |
| --- | --- | --- |
| 1 | `AstaPS-Resource AvatarSkill`、`AvatarSkillExcelConfigData 7.2`、`AvatarSkillDepot energySkill`、`costElemVal specialEnergy` | 固定候选表、字段改名说明、与当前 121 energy 交集；缺省/显式 0 分开 |
| 2 | `Genshin ExcelBin parser 7.2`、`hk4e resource parser skill`、`原神 7.2 技能 能量 资源解析` | 可读实现/格式文档及版本输入声明；不执行上游工具 |
| 3 | `specialEnergyCostMax specialEnergyCostStart`、`specialEnergyMin specialEnergyMax`、`Mavuika Fighting Spirit AvatarSkill` | 具名消费者或独立非零样本；旧版仅帮助识别职责，不作为目标值 |
| 4 | 同关键词筛已列 GitLab/Gitee/Forgejo 资源或解析项目，以及 README 直接关联的公开 issue/论坛 | 新版本/源码线索；二手描述只作发现，不当 oracle |

Codeberg 两个既有 restricted URL 保持原停止记录，不换入口重试。其他平台的不可访问记录不写作“没有项目”。本次若未找到与 Skill/energy 直接相关的 README/文件，停止该候选，不向完整战斗/全资源或无关协议扩展。

Skill/energy 无可用增量后，按主线明确追加的单项执行以下分片；先核独立审计回传的本机已查范围，只补尚未覆盖的新来源，不把未搜索写作外阻塞。

本机独立审计 `debug/d02-resume-independent-20261116/summary.md` 已确认：UP-72 的泛词、N1 五消息及 N2-303 均未覆盖下列五个准确问题；这是尚未定向检索，不是“外部来源不存在”。先消费固定候选独有增量，再补明确查询。

| 按需分片 | 既有账本/准确缺口 | 定向查询与出口 |
| --- | --- | --- |
| Material | [分类边界](CLIENT_72_D02_MATERIAL_RELIC_ROLE_BOUNDARIES_20261116.md)、[raw/父字段](CLIENT_72_D02_ACTIVE_ITEM_BASE_RAW_INVENTORY_20261116.md)；六直接 Enum / 145 声明已查，`type34165/+c8` 仅支持有限分类职责，literal/row/consumer 未闭 | `MaterialExcelConfigData materialType 7.2`、`MATERIAL_* Genshin resource parser`；只找固定 literal 原值、具名 consumer 或独立非零 oracle，不重复零命中 raw stem 盘点 |
| Reliquary | 同[分类边界](CLIENT_72_D02_MATERIAL_RELIC_ROLE_BOUNDARIES_20261116.md)；rank/set 九派生+六继承候选存在标签互换反例 | `ReliquaryExcelConfigData rankLevel setId 7.2`；找能排除互换的允许具名 target 来源或同源输入+非零 oracle |
| Proud | [Float consumer](CLIENT_72_D02_PROUD_FLOAT_CONSUMER_SOURCES_20261115.md)、[Pair 查询](CLIENT_72_D02_PROUD_PAIR_CONSUMER_QUERY_20261115.md)、[共享 Pair](CLIENT_72_D02_PROUD_ENUM32419_TRIAGE_20261115.md)；条件 Float/Pair 可复用，目标字段绑定/同 epoch 仍未知 | `ProudSkillExcelConfigData paramList addProps 7.2`、`ProudSkill Float Pair parser`；找准确字段/row 的独立桥，不重采同 SDK 页、造 Default 或借 preview |
| Scene | [允许来源差距](CLIENT_72_D02_SCENE_ALLOWED_SOURCE_GAP_20261115.md)；M1 producer→同次`+5d60`采用，M2 同receiver `+f0`目标/返回R，M3 首次消费前R`+18`及writer顺序，M4 同R release/reacquire/reset | `SceneInitFinish 7.2 scene entity team`、`scene reader pool release reset`；只补与这四条准确边相连的新来源，World slot114 的 A 返回依赖保持冻结 |
| SDK | [自然阶段](CLIENT_72_SDK_STAGE_NATURAL_SUCCESSOR_20261110.md)、[配置观察](CLIENT_72_SDK_CONFIG_STAGE_OBSERVER.md)；已验锁内map不证明托管 route 采用/认证，B组合loader保持冻结 | `hk4e SDK Init environment route config authentication 7.2`、`Genshin comboLogin route env init`；找新的固定真实 Init/env→route采用→认证/完整校验来源，不重复同SDK页采样，不以patch差异代自然采用 |

追加分片须各有任务 ID、固定输入、已查清单、候选和停止点，不能以完成 Skill 搜索自动宣称其他项已查尽。

## 交付、判定与停止

文件归属：执行者新增独立检索结果文档；不改 Project/Harness/HANDOFF、共享 Git 索引、运行源码或子模块。clone 若有必要放仓外，先复用已有外置研究根；原始 API 响应、文件/日志及固定输入清单放忽略 `debug/d02-skill-energy-search-20261116/`，公开文档只留相对路径、来源和脱敏摘要。

可采纳：候选按 ID 唯一对齐、版本/来源可追踪；字段职责有允许的具名 target 来源；值由独立输入或非零 oracle 支持；元素名字和数值均准确匹配，整数、float32 bits、64 位和 presence 不混同。若只满足部分条件，只交候选/冲突和准确解除条件，不能晋级三层映射、完整行或 ready。

允许消费者仅为 source-only 比较与下一份有界规格。任何新 native / parser / getter / JVM / 数据库 / 客户端执行，均须主线先按固定输入和准确依赖审定；不使用 A/B、common return、preview、准确 tail 或被冻结输出作为依赖，不用 host zero/空 Skill/None/假 cost 过门。遇 hash/版本漂移、缺准确边界、语义冲突或未知传递边，停止该最小项并保留其他独立工作。若出现新安全拒绝，保留原提示、失败和停止点，立即停止准确动作及其依赖，不换模型或工具重试，也不自动派 Daybreak。

验收产物为“367 新 Skill / 120 新 energy 绑定 / 121 三字段”分开的可复用表、固定输入 manifest、候选冲突、已查与未查账本及最小后继合同；局部通过不提升父项，D-02 客户端六项仍须 R4/R5 后实测。
