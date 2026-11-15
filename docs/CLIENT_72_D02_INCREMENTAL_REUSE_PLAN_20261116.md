# D-02 准确字段与文件入口增量检索计划

任务 `D02-INCR-PLAN-20261116`。规划输入为实际接手 HEAD `99a868d2c58730dd1f39a7f3d5dbee1d62eb57b4`（`7.2-dev`）及当时工作树；本计划由 Sol max 编写，供两组 Terra max 并行检索，Sol max 分析新语义，成熟合同再交实现。用户已恢复并行研发，历史暂停/条件2不阻止本轮独立工作；既有安全拒绝及准确依赖不解除。

本文件只规划增量，未执行新的公开查询、源码采集、解析或测试。既有报告的 Fork 数及公开状态均为其采样时点，不冒充本轮实时结果。共享状态、Git和运行操作由主线指定的唯一集成者管理。

## 已有覆盖与固定输入

先读 [Project 3.2](../Project.md#32-批次路线与退出条件)、[活动前沿](CLIENT_72_D02_RESUME_INTEGRATION_20261116.md)、[详细交接](../HANDOFF2.md)。本轮不重研369 Skill id/element、121 cost/START、Depot绑定、Avatar七键、Talent/phase6、97枚举、Material准确载荷、Float/Pair、启动修复及cold-cache；旧报告中的367/120缺口以这些后继成果为准。

| 只读来源 | 本次实际核对的 SHA-256 / 可复用范围 |
| --- | --- |
| [定向公开检索](CLIENT_72_D02_TARGETED_REUSE_SEARCH_20261116.md) | `534f6ef62b47a181e001093dde18eb3e0312133db4ed9fd6dc790e8056087853`；Asta/Anime候选、已审分支/讨论、Go JSON消费及未覆盖账 |
| [Material行增量检索](CLIENT_72_D02_MATERIAL_ROW_REUSE_SEARCH_20261116.md) | `78af0a1bb44f8151dba00f76e52331009b08e9c5b757440d8a04c3a2a18a35ee`；YSRes父仓及2个Fork、DataParser两gitlink、5.2/2.2参考与未审Fork |
| [energy职责来源](CLIENT_72_D02_SKILL_ENERGY_BRANCH_FIELDS_20261116.md) | `b4bb22d60c5c08967f7f2a362f34fdad9c59385e76f4cead172829d0f4889073`；88602的cost/START、24887的MAX_SPECIAL属性、名称查询及525B注册分支排除 |
| [Material表头/槽初始化](CLIENT_72_D02_MATERIAL_HEADER_SOURCE_20261116.md) | `e604a18e26158ddbdc8da73a12af1d064160c36299491467e51031be42a54140`；569B源、独立EDI倒计数和239B同cursor槽清零；有效backend仍缺 |
| [Relic 27候选typed核查](CLIENT_72_D02_RELIC_TYPED_CANDIDATES_20261116.md) | `8fd867a21fa2668d163233e3094ef9eced228babc1741fda97d6d41d45a1a7ba`；10owner无准确46431/74316字段边、26 UI同名字段、74316的18声明排除结果 |
| [String尾游标/Weapon独立性](CLIENT_72_D02_SHARED_STRING_CURSOR_SOURCE_20261116.md) | `bd9e1d4e36749b9a61fadbb8287c54f3ca68489fc15cb315e73a10b82acd779c`；仅normal-tail字节推进与wrapper跨度，未闭真实父入口 |

两组均以[固定国服输入](CLIENT_72_EXTRACTION.md#输入与安装一致性)为版本基线，读取现成脱敏摘要和必要源即可；不为检索重新读取五stock。候选固定仓库+branch+commit/tree+README blob，命中后固定文件blob与SHA-256、许可、版本声明及同源关系。进入目标分析时逐项固定已有原件及其hash，不能只固定本计划。

## 两组派工合同：四个出口分别验收

| 子项 | 固定输入与准确问题 | 最小输出 / 验收 |
| --- | --- | --- |
| A1 `D02-INCR-A-MAX-20261116` | 上述energy来源；已验121四键结果 `9d4b69d00a438084cd9a10b6b4eacda5618eaeebdab735a3bfe38cd3c4e755cb`；找同target88602某字段→MAX_SPECIAL职责，或有revision/字段身份的schema | 一条具名字段/consumer/schema桥，说明对象/属性身份及可排除START的证据；只形成线索则列缺边。候选11065/11145的200/100不能倒推cap，START=100/50或另119行START=0均不证明cap值/缺席 |
| A2 `D02-INCR-A-RELIC-20261116` | 上述typed结论及原件 `6c709c56a80d7502434cf69ca1f9a6e5332a266a54bf3abb689317dc451adc59`；目标46431和74316.field368811/+20；分别排除rankLevel/setId交换 | 准确字段→rank等级查表或set套装用途，或同源真实输入+独立非零role oracle；两个角色可分别验收。不以Go JSON、UI名字、数值大小、参数形状或27候选重复命中选字段 |
| B1 `D02-INCR-B-MATERIAL-20261116` | 真实m_bytes 1,560,105B，SHA `088f2b43c860c2892c1f6e9a0349547dcd94b021e5d56983349f2cfd81e7ee89`；17822.m135074/60257.m500132、父88968.m735168；找实际filecursor、count/首row | 独立完整文件语法，或同reader backend/+18→首refill→实际header位置的准确桥；清零+1c不等fileoffset0。先闭一个首行前缀，不预设首flag，不读取条件P=0的4B |
| B2 `D02-INCR-B-WEAPON-20261116` | raw128,936B，SHA `cca19efbe032e5416d78c28fcb69c2ea2d9707187197aa4ed90d869c3a1ff918`；whole报告 `1e9817cbea6da6db5ee564d5c3f3a64436fa3319e6d9b8ccf5aef0014cf85a86`；11101既验六键与239 cohort；找87097 wrapper→20600/父88968真实入口 | 同reader/同row的准确父F入口cursor和完整物理边界，或独立schema；再分别闭presence/转换/数组单位。wrapper[4,400)及400后的11201不作F入口，11201不在必要cohort，不另造任务 |

两组建议各独占一份新报告 `CLIENT_72_D02_INCREMENTAL_REUSE_A_20261116.md` / `CLIENT_72_D02_INCREMENTAL_REUSE_B_20261116.md`，原始响应分别置忽略目录 `local/migration72/d02-incremental-a-20261116/` / `d02-incremental-b-20261116/`；实际文件归属由主线派工固定。同名已有则返回，不覆盖。每份报告分列本组两个子项的“有可用新桥 / 仅参考 / 有界无命中 / 缺准确输入”，任何一项可先交回，不等另一项。检索组不改代码/资源/共享文档/Git索引，不运行上游代码。

## 候选增量：先README，再相关分支/源码

| 优先级 / 组 | 公开候选和未覆盖范围 | README筛选与后续范围 |
| --- | --- | --- |
| 1 / B，A仅复用其命中 | [SagiriYuuki/YSRes](https://github.com/SagiriYuuki/YSRes)既有API报13个Fork，仅Hiro420、ztsvlr已审；先枚举实际Fork列表并排除这两个已审项 | 逐Fork先描述/README，再枚举公开分支；分支README明确binary/Excel/schema/版本升级或与已审gitlink不同才读目标文件。旧main `726dfe5…` 与两个相同README不重复分析；未审名单须由本轮API取得，不能用13直接计算覆盖完成 |
| 1 / B，A可取目标schema线索 | [paimooon/DataParser-paimooon](https://github.com/paimooon/DataParser-paimooon)既有API报6个Fork，尚未逐审；默认main之外情况须实际列出 | README的Kaitai/Excel binary、Material/Weapon/版本声明为筛选依据；随后看相关分支README、gitlink/tree差异及必要KSY。`19f393…`/`1045cf…`、Material及aux_types旧blob已审，不重读；寻找新增table wrapper、继承父行或版本差异 |
| 2 / A | [AstaPS-Resource](https://github.com/MeChen618/AstaPS-Resource)7个默认README未命中者的非默认分支元数据未审：`1F1FC`、`cjhlk`、`ANameless2`、`2406450951`、`nyakochao`、`fengfeng65`、`Stellarysian` | 默认README沿既有缓存，不重抓；仅新增/版本或schema相关分支先读其README，README说明能关联energy/Relic才查差异。已审main、mechen runtime同树、tangyanliang1 develop/update-quest同energy行、#21/#11不重查 |
| 3 / A | 精确代码查询发现的新schema/consumer仓库；AstaPS及LunaGC_7.2.0仅审未覆盖且README确称相关的新分支 | 7.2标签不足以证明国服目标；先版本/profile/数据谱系，再核字段职责。固定Go `excel-hk4e@1c5ea225…`无Fork和旧Set()/Level()已审，不能计新增 |
| 3 / B | GitLab/Gitee/Forgejo仅查上述候选README/分支给出的公开同源仓、独立schema或版本增量 | mavuika-rs `67833c…`唯一master、Fork/Issue/PR零记录及5.2方法已审，不重复；非目标7.2 profile、7.1 Global和2.2/3.4仅参考，不生成目标行 |

仓外既有clone优先只读使用；README通过且确有未审源码才在仓外clone，不递归取无关子模块/二进制。不同Fork同tree/blob记一条来源，字段相同不算独立oracle。

## 精确查询和记录方式

先在入选仓库/分支的固定tree内查询；GitHub代码搜索可用时用下表 `repo:OWNER/REPO` 精确式，不用旧仓库搜索的零命中替代源码查询。代码搜索不可用则记录原因，使用公开tree+必要文本文件的只读检索，不声称代码零结果。

| 子项 | 新代码查询词（按目标分别运行） |
| --- | --- |
| A1 | `repo:OWNER/REPO "specialEnergyCostMax"`；`repo:OWNER/REPO "FIGHT_PROP_MAX_SPECIAL_ENERGY"`；`repo:OWNER/REPO "special_energy_cost_max"`；`repo:OWNER/REPO "max_fighting_spirit"`；命中后联查`AvatarSkill`/`START_SPECIAL_ENERGY`，判断职责而非只取值 |
| A2 | `repo:OWNER/REPO "ReliquaryExcelConfig"`；`repo:OWNER/REPO "rank_level"`；`repo:OWNER/REPO "set_id"`；`repo:OWNER/REPO "ReliquaryLevelExcelConfig"`；`repo:OWNER/REPO "ReliquarySetExcelConfig"`；需要字段到等级/套装实际查找边 |
| B1 | `repo:OWNER/REPO "material_excel_config"`；`repo:OWNER/REPO "MaterialExcelConfigData"`；联查`seq`/`bitfield`/`count`/`from_binary`/`deserialize`，只取尚未审的table framing与父入口 |
| B2 | `repo:OWNER/REPO "weapon_excel_config"`；`repo:OWNER/REPO "WeaponExcelConfigData"`；`repo:OWNER/REPO "item_excel_config"`；联查wrapper、base/item继承、reader position/offset及数组单位 |

若候选族无新增，只补仓库/README精确词 `"specialEnergyCostMax" "schema"`、`"ReliquaryLevelExcelConfig" "binary"`、`"WeaponExcelConfigData" "binary"`、`"material_excel_config" "table"`，附 `is:public fork:true in:name,description,readme`；不重跑两旧报告的广泛关键词。相关托管平台只用这些准确词和明确候选名；既有两个Codeberg restricted地址不换入口重试，404与未搜索分列。

每次查询记UTC、平台、完整词、排序/页码、total/incomplete、状态及原页hash。Fork/分支列表按实际分页完成；代码结果按可见覆盖记录。结果过宽先加目标schema/consumer条件，不扩长尾；权限/超时/未覆盖不写零命中。旧报告原始索引直接复用：`local/migration72/d02-targeted-search-20261116/`与`d02-material-row-search-20261116/`。

## 无直接复用时的最小自主研究

有界公开检索无桥，不等于外部阻塞。Sol max先消费两组新差异，再做以下**新关联**，只读现有metadata、完整原窗及同源报告；不重跑已验动作，不先造工具/值/资源。共同metadata为 `analysis/current-structures.json` SHA `d5d7f671a622439b2f9a3ed0018ce551aa5c573cd9ca68bed3b006712b8a98aa`、signatures SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`，按所用原件另固定hash。

| 子项 | 下一个有判别能力的问题 / 证据入口 |
| --- | --- |
| A1 | 以88602资源及24887属性的实际类型/字段引用筛选既存完整消费窗，核是否存在同值流→enum77/MAX_SPECIAL、且与78/START分离；区别于已完成名称查询和已排除525B注册窗。若仅有候选字段位式，不读候选值选cap；输出唯一可核consumer身份或缺的那一条赋值/消费边 |
| A2 | 将46431派生/继承字段候选与**同类型实际资源查找**关联，分别找等级表key与套装表key用途；复用27 UI排除，不试读74316的18混淆body。可区分交换的证据是准确字段进入不同表查找/具名属性，或独立非零role控制；仅metadata类型关系不升级字段角色 |
| B1 | 在已固定loader/factory/refill完整来源之间，以同reader身份核backend/+18的到达定义和首次refill传递，而非重证+1c清零；只有出现独立于A返回/构造的新写入或文件语法桥才可闭实际header。没有该边不读4B，不猜filebegin |
| B2 | 将现成whole、observer的同reader/同row身份与现存完整父源逐边核对，区分wrapper entry与真实20600/88968 entry；只有已有可核完整来源或新增独立schema支持cursor等式才成立。旧hash/RIP refs不补缺失body，不按11101六值扫描定位，不重新取得已停的Weapon构造路径 |

若需要新静态源，先形成**单一准确请求**：固定PE/hash、唯一metadata方法slot/真实签名、来源如何选中该方法、完整可追溯边界、只解决哪一条边及字节预算；主线判定移除被拒动作及其产物后仍能独立完成，再由指定执行者处理。只有未知方法族/普通缺样本时不采窗、不升级Astra；没有成熟自然问题不要求现场。

## 边界与本轮结束条件

- A第二返回/完整构造、B组合loader及准确依赖，preview与既有专项限制保持；不执行native/parser/getter/ctor/TLS、主动造Default/cache、computer-use、同SDK页重复采样或common E8扩扫。Material两A CALL、shared String准确A返回及Weapon已知旧ctor/parser冻结边继续停；不把整族潜在host路线泛称依A。
- `P_after=wrap32(P_at_tail+N)`不升为入口净推进；Material槽清零不升为有效文件起点；Weapon wrapper跨度不升为raw父F入口。5.2/2.2 schema和旧Default策略不能填目标值、presence或单位。
- 新hash/边界冲突或安全拒绝时立即停相关动作和依赖，保留原提示/停止点；拆分后只做删除被拒动作及产物仍成立的独立子项，不换模型/工具重试，不自动派Daybreak。
- 本轮在四个子项各交一份新证据链或准确的覆盖/未覆盖/最小缺边后结束；一项成熟即可转最小样本合同，不等整组。不循环相同查询；无新桥不生成walker/批量行、不改ready/required或父项状态。
- 当前可立即派工的是两组只读检索，及无复用时上述有界本地新关联；目标字段实现、完整Player恢复、R4–R6仍分别受必要合同约束。计划与公开命中不等资源ready或实机验收；D-02/F-05/D02-R6仍0/6。
