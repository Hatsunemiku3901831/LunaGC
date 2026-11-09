# 国服7.2在途工作收尾交接（2026-11-10，已暂停）

**已按用户最新明确要求，完成当前在途子项的主线验收、本地提交与收尾，现暂停，不开启新任务。D-02 / F-05 / D02-R6未完成。** 此决定覆盖此前“持续至D-02全部通过”的执行节奏；停止原因是用户要求，不是所有独立工作均被外部条件阻塞。未在暂停要求后新开factory、Hash/FindEntry、TLS实验、八点SDK观察器实现、部署或客户端采集。

接手先读AGENTS → README → Project → Harness → 本页 → [迁移现状](docs/CLIENT_72_MIGRATION.md) → [部署手册](docs/CODEX_ONE_CLICK_DEPLOY.md)，再读对应专题。Project继续是任务和完成标记入口，保留原ID、完成子项和全部未知。R1/R2/R3的“进行中”、g2的“阻塞”和R4/R5/R6的“待开始”表示业务验收状态；当前调度统一暂停，不能据这些词自动恢复工作。

## 备份、Git和交付范围

覆盖HANDOFF2前已先逐字节备份原件：`local/migration72/d02-parallel-20261109/closeout-20261109T164643Z/handoffs/HANDOFF2.before-pause-20261109T171113Z.md`，SHA `1ecb5f1fe602844ecbabbb48b943a4615675744c1b2c4ca1438c6fd8d23af8f5`；HANDOFF原件备份为`local/migration72/d02-parallel-20261109/closeout-20261109T164643Z/handoffs/HANDOFF.before-pause-20261109T171113Z.md`。HANDOFF.md仅在原bytes后追加本次简略记录，不删除或规范化旧内容。备份与append-only核验见`local/migration72/d02-parallel-20261109/closeout-20261109T164643Z/handoffs/handoff-close-proof.private.json`。

本轮从主仓库`7d1244f54e181dcc1d9b148b4c970c106946310f`接手，分支`7.2-dev`。本页写入前已验成果HEAD为`d66429d11e14830c37c7ded66024e2cebf92145a`；最后交接提交不自引用自己的hash，实际最终HEAD及索引/保护复核见`local/migration72/d02-parallel-20261109/final-pause-post-commit.private.json`。所有提交只在本地，未推送。

- patch为`566b211b3ca56c28b313dbba6eb59c7e5141c36e`，分支`codex/7.2-sdk-key-loader`，clean；父仓库gitlink已提交。
- resources为`dac2a9d43332ef51879522cf39da2831b0ca0543`；既有dirty文件`ScriptSceneData/flat.luas.scenes.full_globals.lua.json`保持SHA `db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe`。
- `AGENTS.md`是用户在本轮补充的未提交修改，当前SHA `17eaf014ed41ff6206eee98345604005de46f3def12299f6dd382ac90b24bd7c`；保留、不提交、不回退。最终工作区只应剩该文件和resources既有dirty标记，不以全仓库add/commit收走。
- 规划修改前与四份新专题交付版本已备份到`local/migration72/d02-parallel-20261109/closeout-20261109T164643Z/`，包括`before/`及`before-pause-indexes/`。旧报告涉及历史文档SHA时用这些快照追溯，不改原报告或放松pin。

## 现场、构建和数据保护

最终只读盘点为`local/migration72/d02-parallel-20261109/closeout-20261109T164643Z/final-inventory.private.json`，SHA `67022c094ce0df672bded7674a0ae40b6e3c57b1d4e38ecc8fbbe0d3c1883719`，实际时间`2026-11-09T17:10:05.597117+00:00`：五stock输入、SDK资源carrier、13个现存保护文件及`.env.local`不存在事实全部匹配；`Astrolabe_orig.dll`不存在，相关游戏/Java/Mongo/调试器/patch guard进程为0，固定TCP8088/22101/27017/27027和UDP22101均无监听。没有待恢复DLL、待处理UAC、未退出实验或待用户操作。

GAME_PATH从当前环境/.env与`tools/game_path.ps1`重新解析，没有套旧路径；机器绝对路径、PID、heap/base、命令、凭据和raw只留忽略目录。`.env`为`LUNAGC_RUNTIME=portable`，config保持原version14；原mongodata、local/data保留，不清库或迁移原库。13项保护包含AGENTS、.env、config、resources既有差异及9个运行数据文件；不把这一固定文件集的核验说成全盘逐字节审计。

固定五输入以`protocol/cn-7.2/profile.json`完整SHA为准：EXE `8b014f64…b6b5`、metadata `45dc7a44…ef2a`、startup `b3148108…5105`、AccountPlatNative `adecdf0b…6c25`、Astrolabe `87e0a005…b442`。SDK carrier为固定24223855B/SHA `8e33ae960729065d273928d029b03b72e72cdfbee823fc1f94e83d8ab8c59cda`。恢复时重新核完整SHA，不用orig别名绕门禁。

根`LunaGC-7.1.0.jar`仍是496e96f既有portable构建，SHA `8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291`。遗留文件名不是7.1运行许可，不改版本绕guard。当前Java定向测试实际编译了test及必要classes，但没有重建生产JAR。R4必要合同未齐，未部署/启动GS，没有正向HTTP健康、运行JAR/PID/TCP/UDP的服务验收对象。仅离线工具、测试和文档收尾无需重启；今后运行材料变化仍按AGENTS正常构建与portable部署核验。

本轮已执行的SDK/stock采集均已正常退出并恢复。最新SDK前缀采集用户明确反馈“页面正常，已正常退出”，restore helper实际exit0，最终盘点再次验证stock。原ready已经消费，没有待答实时问题；新实验必须另取新实时配合，不能复用旧PID或地址。

## 本轮已验成果和本地提交

这些都是Project第6节列明的限定子项。下表不是D-02完成率，片段通过不晋级父项；不重跑历史研究或机械累计测试数。

| 任务与提交 | 实际验收范围和入口 |
| --- | --- |
| 恢复盘点 `49d1373` | 核实际HEAD/配置/五输入/原差异，按用户恢复A/B/C；[执行历史](docs/CLIENT_72_D02_RESUME_20261109.md)。 |
| A warm/enum/String合同 `0adca68` | 最新warm原消费者10case/158步，三fresh一致、20检查/主线17审核；保留零槽lazy分支及真实allocator/definition首缺；String共同合同冻结，不补槽或host成功。[warm后继](docs/CLIENT_72_AVATAR_WARM_CONTINUATION.md)。 |
| A普通allocator `640f8b7` | 连续完整Avatar入口到普通类缓存root首缺，三fresh一致、26测试/主线13审核；没有GC/TLS/allocator成功模型。[普通查询](docs/CLIENT_72_AVATAR_ALLOCATOR_SUCCESSOR.md)。 |
| String采集器与lazy诊断 `78017ea`、`92a9eba` | 独立collector/adapter及主线门禁；新stock自然采得Dictionary泛型owner、FindEntry0/getter非0，v2按真实门禁partial；正常页/退出及保护通过。[原诊断](docs/CLIENT_72_STRING_CONSUMER_COLLECTOR.md)。 |
| A联合输入 `a8b517b`、`7f9b36e` | 工具16测试/134审核，人工三fresh及当前stock gate；新实时stock90秒44监测，69读2704B、四分支同epoch图，34unique终读保持，15主线纯重放及正常页/退出通过。[实际输入](docs/CLIENT_72_AVATAR_STRING_SUCCESSOR_INPUTS.md)。 |
| A fresh消费者 `f6169b0` | 两正式报告同SHA `73e55971…a6754`；真实GetDefault25条返回同轮Default，完整Avatar/allocator/普通lookup连续105条后止TLS index4B；13只读测试/13审核，累计4call/262观察/260完成/2保留失败。[准确后继](docs/CLIENT_72_FRESH_AVATAR_STRING_SUCCESSOR.md)。 |
| String原入口/字符 `afec0f0`、`cfd9c53` | 10339无pdata，获批64B只执行可达48B；旧8call/66条有界前缀保留。新177B Helper2908/53原指令，两fresh各14case，累计28call/1302完成/0失败，预期AL全符；13只读测试及主线15审核，未再跑CPU。[字符路径](docs/CLIENT_72_STRING_EQUALS_CONTENT.md)。 |
| B超限归因/分流 `4038d6f`、`ada0eec`、`d8e4787`；patch `5c2c0a5` | 旧event5只来自payload预算，不能分辨具体出口；独立11/12产物实际构建与两fresh各23 owned，真实新采终态11，16主线审核与stock恢复通过。旧5未知保持，不扩大限额。[split](docs/CLIENT_72_SDK_EARLY_SPLIT_OBSERVER.md)。 |
| B language/前缀 `466f7a5`、`abb8329`；patch `566b211` | 来源与695字段模型、前缀正反通过；独立DLL/fixture实际release/locked/offline，两fresh各54 ABI，51EAT/72B DATA/完整284B、六源/86pin及主线14审核；adapter33检查/真实隔离文件门禁。[产物](docs/CLIENT_72_SDK_CONFIG_PREFIX_OBSERVER.md)。 |
| B自然prefix13 `18ec70c` | 新ready90秒45监测，9读1450B，准确caller必要前缀cn-env-0/两个reference等值，完整代码绑定/16主线纯重放、正常页退出stock恢复通过。完整payload/NUL/Marshal/Init/route仍false。[实际自然13](docs/CLIENT_72_SDK_CONFIG_PREFIX_OBSERVER.md#新epoch自然13正常退出与stock恢复)。 |
| B后继规格 `d66429d` | writer5422B/1223指令及21静态投影，八点方案与12项整轮预算纯模型、主线8审核通过。只验规格，八点产物未实施/构建/安装/实机。[来源与预算修正](docs/CLIENT_72_SDK_PREFIX_NATURAL_SUCCESSOR.md)。 |
| C自然首诊 `5aa2e70` | 新JVM真实loadAll一次，固定三文件/独立R/S/D，Chapter436/Quest63、10WARN/326ERROR，返回且自然loadedAll=true；temp已声明变化，14主线审核。原runner退出1保留，非全局兼容。[首诊](docs/CLIENT_72_D02_NATURAL_LOAD_FIRST_RUN.md)。 |
| C303局部保存 `1f3cbfb` | 同一新JVM自然加载后，真实Morphia/save→新Player→QuestManager恢复全部63子项；未就绪0写/BSON相同、正向精确两写及raw BSON差异经24审核，独立Mongo正常shutdown。非账号登录/场景/完整保存验收。[303正向恢复](docs/CLIENT_72_D02_QUEST_303_NATURAL_REBIND.md)。 |
| R3候选/Java `90d1c2d` | 固定两完整候选原文/27pin/三fresh，字段损失与未知保持；仓库外所选AstaPS源无checkout克隆。真实loader三JUnit通过，34config/3ability/hash，主线13审核与数据保护通过。[候选与加载](docs/CLIENT_72_D02_GADGET_ABILITY_CANDIDATE.md)。 |
| R3静态语义 `a8f96cc` | 初始19pin、196497字典parser10pin、196498值工厂分派8pin；完整1343B/167B原窗及静态表达式已审，主线10/8审核与最终10项复核。LKLP/defense/common、实际reader/class仍未知。[语义](docs/CLIENT_72_GADGET_ABILITY_SEMANTICS.md)。 |
| R3必要性/Metal `1f9e003` | 31源必要性矩阵→Sol xhigh实现→主线真实JDK17单类Gradle23秒/2JUnit通过；真实EntityWeapon/Ability初始化及root/owned参数局部投影，LC02缺定义null槽对照。executor前后全0、4092 class-init无DB/服务/ScriptLoader/PluginManager，15审核。[实际生命周期](docs/CLIENT_72_D02_GADGET_NECESSITY.md)。 |

旧R2日志/单token响应、真实HTTP/Mongo、key同核原生桥/实际装载、raw适配器、Lua/Chapter/装备/曲线/突破等限定成果直接复用；本轮未重新验完整账号或经济系统，也不声称历史全量全绿。

## 最新原始证据与主线证明

以下共同根为`local/migration72/d02-parallel-20261109/`，均留忽略目录；专题给完整复现和失败入口。

| 证据 | SHA-256 / 作用 |
| --- | --- |
| `combined-successor-inputs/live/avatar-string-stock-20261109T150554Z-b9263392.private.json` | `1a2e7509f6149f995cc0cc5c4ddc8f0c3f8acd297afcfc27c1eb9d5caee9c63e`，69读2704B原图 |
| `combined-successor-inputs/main-live-acceptance.private.json` | `d2b0a9a3389f7f0b8d0947efd6b72cfb6e54bf33731369e61ba3614f8daccd22`，15项全时序纯重放与保护 |
| `fresh-successor-replay/main-approved-string-common-input.private.json` | `07494634d3d46ee26807e0f96672b3378bd50fa7451786801c988a86b0bea08e`，只冻结同epoch图/getter返回；不扩大Hash/FindEntry权限 |
| `string-equals-content/{first,repeat}.private.json` | 两个608803B，均`215969b45954049249bd08fccca28c6ac2bfe051b7004b16b2d285b4f56ee640` |
| `string-equals-content/main-review.private.json` | `214c0e0b3a9b9a2b3e39c0e3c53d8a141e8ad0461119595c9881a1126de99475`，15项主线审核；`main-tests.private.log`为13只读检查 |
| `b-sdk-prefix/helper-preparation/live1.private.json` | `8be0a8a53eb173a375473645ad955d0fdd5ce5b0d63fa3f8c91dcfb73e3cdef2`；reads sidecar `2ed9de53be71624df8844d93ac6e01f63d8ebad38ad146374b8ac4927738691b` |
| `b-sdk-prefix/helper-preparation/main-live-acceptance.private.json` | `934ba6137f724ffa35d20628ea7e8fd40cc65ed6a621df36892c9cd01844171e`，16项实际自然13审核；main-after/restore journal保留正常退出与恢复 |
| `b-sdk-prefix-successor/writer-window-analysis/attempt-budget-correction.private.json` | `2f45829e048b15a3a2b2107b94a8bcd9245037c123446bace39bf63914982f0d`，12纯模型 |
| `b-sdk-prefix-successor/main-final-spec-review.private.json` | `954ca9aca9e5e4ff697a816af77d670e3716e450187b2b58cd8b6312a46c6f96`，8项只读规格审核，非实现 |
| `r3-weapon-lifecycle/run-20261109T163039Z-3e808450/main-review.private.json` | `d284fa99632be3bd0dd37ac154eaccfd9b79f162999f07ab5a1f3344addbd2d5`；同run保留init/manifest/Gradle/XML/class-init/前后现场 |
| `closeout-20261109T164643Z/static-main-review.private.json` | `21fa138b4f1cde17bff5967c36c30761a512c1e91792e0a67af4c684c7518a1a`，三阶段静态源及必要性31pin，旧文档映射到备份 |
| `c-natural-load/main-loadall-review.private.json` | `c5587e0b1d64157ed0052d4d8c3d7223ad31676cf6287289ee4ca8e7d949b0e7`；实际run及source-at-first-run见首诊专题 |

外部复用检索按新增AGENTS完成Sol max中文计划及Terra max有界检索，**第二轮coverage仍partial且墙钟超过20分钟预算**，原manifest/result/ledger保留，不记完整成功或C1/C2全部审计。所选AstaPS来源commit `6cfecfb55f889f8e3b7156adb12809608db4f74b`、tree `3cf83488a94b47a018934e5f91c1bf7e93357aae`；仓库外depth1/blob:none/no-checkout克隆只含.git，pack1803463B，没有运行上游代码、LFS或子模块。`upstream-reuse/main-reference-clone.private.json` SHA `00765c78ff00dc2d5b2c063dd18ba4ca8887e8b16a700e5a503da95e5494a29b`记录实际路径。未整包替换资源，runtime参考license/ABI和未覆盖分支仍未知；不重爬已有成果或无收益patch。

## 未完成项、准确缺口和解除条件

| 原任务 | 当前准确边界；恢复后才可推进 |
| --- | --- |
| F-03d3f4d2 / D02-R1 | 新fresh路径已真实消费普通root/1255及比较，当前首缺是原allocator读本轮模块TLS index4B；TEB/GC/OS分配语义和返回均未闭，不能补TLS或host成功。enum两零槽的旧lazy诊断保留；普通10294属于另一路旧cold输入，不借inflated同号，也不把它冒称新fresh当前首缺。完整Avatar构造/Handler、装备/技能/池与g2仍未验。 |
| E-01/R3 String | 旧primary usage0不再意味着所有输入都空：本轮同epochDefault/实际getter/Equals MI取得且getter返回已验。新字符路径只用明确长度/UTF16/显式NUL的owned字段；自然allocator终止存储、Hash/FindEntry/自然dictionary caller/键仍未验。恢复后先选一个有必要性的消费者合同，不扩共同wrapper、页填零或fake String class。 |
| R2-CONFIGSYNC-EARLY-BRIDGE / ROUTE-CONFIG-SELECT | 自然prefix13仅证明必要前缀cn-env-0及两reference等值，原payload完整/NUL/Marshal、SDK parser/writer正常返回、实际env store、自然Init/route/key/认证仍未验。八点观察仅有冻结规格；恢复后若选择实施，先单独构建/两fresh真实owned/外collector source gate及恢复方案，再新实时ready。原unwind/xdata及CALL异常栈透明性未知，不能声称production透明。 |
| R2-PLUGIN-UI-REGISTRATION / CONFIGSYNC-OBSERVE | 原静态UI入口和旧观察装载不证明producer/自然选择；只有证实关键路径需要再推进，不重复同身份同预算实验。 |
| E-01/R3-ITEM-ENTRY | 当前Java Metal局部构造与初始化可用不证明目标完整配置。LKLP四叶、defense/common及其target字段职责、原asset/reader/selector/实际parser类仍缺；动态klass+100不能写成固定196496。514570 factory尚无批准的D-02必要性出口，暂停后未提取。Java未用字段不能默认可省略，248叶不全列P0。 |
| E-01 / E-01c / E-01d1 / D02-R3 | 三文件首诊与303局部63子项真实恢复已完成，禁止回到“未执行loadAll”重开队列。完整Avatar/Item必要行、Gadget能力内容、Scene/引用/revision及实际入场必要加载仍未闭。只有实际必要条件成立后扩大正向保存/Manager覆盖，不由loadedAll=true推resourceReady或所有恢复成功。 |
| F-03d3s2 / F-03d3g2 | Scene/Team/Entity自然注册/stage、池分配、MPLevel、组件创建/替换/拆除及发送顺序未闭；g2仍依赖完整f4d2/R3及同源Store/角色/SavedVitals。局部weapon root投影不是完整入场sender。 |
| D02-R4 / R5 / R6 | 尚未完成合同汇合、正式sender与相应回归/构建；隔离portable实例未部署。先满足前置，再正常构建部署、核日志/健康/TCP/UDP/PID/运行JAR SHA，最后新旧号全部实机；不能用编译/当前登录页代验。 |

## 原始失败与避坑

- SDK旧5、split11和新13分别按各自epoch保留，不能回填旧报告的分类结果；不直接抬高语言或payload限额。新SDK规格已修正“筛caller之前无限读取”的漏洞：第一次任意P0先占once，P2同理；错caller拒绝seal，不留机会重试。参数16读37B、代码绑定17读6409B、外部成功30读10415B/拒绝1读256B分别记账，不把caller读取藏到账外。
- JmpBack八点的parser/app_key/url/epilogue仅为待实施的阶段事实，P5/P6只是分支到达，P7不是RET；不得把它们升级为完整JSON业务、writer返回或route成功。未取得xdata，不造unwind/异常成功模型。
- 10339无pdata的原取窗按门禁在写code前停止，诊断原件保留。后续64B是另获批前缀，仅48B/19条可达；末2B不完整不能补齐执行。Helper2908实际完整177B/53条，不沿派工中的49条误数。String尾dword跨入显式NUL只证明owned字段，自然allocator终止存储未知。
- loadAll首诊真实Java返回0，runner原退出1来自已声明temp目录的hsperfdata判定；保留失败，不为改退出码重跑。10WARN/326ERROR和17814 QuestExcel拒绝描述三文件输入，不能说原resources坏了或全161表皆P0。
- Windows门禁必须真正调用准确argv/schema和owned布局。此前缺PYTHONPATH/capstone、GBK/JSON bytes、包装器字段及PS对象展示等原失败已保留；修正wrapper后走真实门禁，不把模拟结果写成实际启动。`tools/game_path.ps1`用独立powershell.exe并立即核其LASTEXITCODE，避免在进程内误读上次命令的退出码。
- 早期曾有“允许UAC但客户端没有出现”的反馈，该轮不能记客户端已启动或SDK业务失败；后续新封存/预检及新ready的实际采集分别留证。最终收尾的Windows PowerShell只读盘点又出现异常长时间无输出，主线核准确命令及PID后只停止这个自建辅助进程，保留exit1和`inventory-helper-stall.private.json`，不记验收通过。后续Python流式文件哈希及独立有界CIM/native netstat查询实际通过，最终proof已列前文；没有停止客户端、DB或未知进程，原stall点未知，不另启研究。
- Native两正式组、失败、CPU/read预算和source副本不可重置或删除。主线收尾仅只读审核，未追加native调用。旧合同/报告冻住；共享文档更新后使用历史快照解释其旧SHA，不改原报告或放宽hash。
- Java forked Test worker必须显式得到四个输入属性、独立CWD/version14六根和logback；不要在仓库根直接测试、触发原config/DB/脚本或调用equipItem填依赖。当前两例没启动DB/完整服务，不把它们当完整玩家/场景实例。只改Proto源，生成Java仍归主线Gradle/protoc。

## 调度和恢复后的动作

本轮实际A/B难点分析使用显式Sol max，有界C及资料对照按规格交Terra，明确工具/Java实现交Sol xhigh；未调用Astra。完成的真实子任务已经返回。工具列表仍可能显示历史`/root/c_loadall_driver`为`pending_init`：该旧派遣已多次取消/interrupt并发送“不得工具/JVM/写文件，初始化则直接结束”的消息，状态未随工具更新；没有由它执行的新工作，不把这个遗留槽位写成活动C任务或声称所有工具条目已消失。其旧首诊结果已经由主线完成，不能恢复后自动执行第二遍。

恢复的第一解除条件是**用户新的明确恢复指令**。此后重新核最新HEAD/差异归属、子模块、配置/GAME_PATH、固定完整SHA及进程端口，按Project3.4挑依赖已满足的未闭出口；不另建重复任务队列。可独立推进的候选为：String单一必要消费者/Hash/FindEntry的准确合同、R1 TLS/分配语义、R2八点规格的独立实现与owned验证、R3必需字段/引用和加载。这里是交接候选，当前均未启动。任何新任务仍先明确ID/目标/现有证据/准确缺口/依赖/范围/产物/验收/停止条件，并按最新AGENTS处理有价值的GitHub复用。

真实现场归主线串行；stock身份回放与临时DLL安装互斥。新实验先准备自有验证、输入门禁、预算及恢复，再一次说明用户动作/预期/反馈并等待新确认。无新ready不能启动。历史“页面正常”只证明对应采集轮，不证明用户现在在线或D-02成功。

## D-02最终六项状态

以下全部未完成，不关闭D-02 / F-05 / D02-R6：

1. 旧测试号真实登录本服、账号及角色归属正确：未验。
2. 新号显式选人、命名并确认保存：未验。
3. 完整初始化与场景握手正确消费：未验。
4. 角色实际进入世界：未验。
5. 连续10分钟心跳正常、无异常断会话：未验。
6. 新旧两类账号分别正常退出重登，角色、装备、归属和受控进度持久一致：未验。

当前暂停后不自动继续任何上述步骤。已完成研究/局部Java/采集成果保留并可复用，不代表最终客户端验收。
