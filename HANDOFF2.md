# 国服7.2收尾交接（2026-11-09，已暂停）

**已按用户最新要求完成本批限定工作收尾，现暂停，不开启新任务。D-02 / F-05 / D02-R6未完成。** 本轮开始时用户要求继续至D-02，后来明确要求“目前正在做的任务完成收尾后先暂停并交接”；以最新暂停指令为准。停止原因是用户要求，不是所有独立工作都存在外部阻塞。

完成标记只覆盖Project列明的来源、原生片段、真实Java加载、只读采集及数据保护范围。最新实验采集/身份/复读/退出恢复通过14项审核；SDK分类超限、enum两零槽与String空usage仍未闭，不伪称业务通过或完整Avatar/D-02完成。没有在用户要求暂停后启动后继研究、String后备、自然loadAll driver、生产实现或部署。

接手顺序：AGENTS → README → Project → Harness → 本页 → docs/CLIENT_72_MIGRATION.md → docs/CODEX_ONE_CLICK_DEPLOY.md，再读对应专题。Project为当前任务/验收队列；历史暂停、继续及旧交接只供追溯。恢复须用户另行指示，不自动消费旧ready。

## 备份、Git和现场

覆盖前已逐字节备份原HANDOFF2：`local/migration72/d02-resume-20261109/handoffs/HANDOFF2.before-final-pause-20261108T232909Z.md`，SHA `ffa090124827adcb5de7151b7c97ff4cf03c66ba1d05140feaa4efe53130bcae`。HANDOFF原件也备份在`local/migration72/d02-resume-20261109/handoffs/HANDOFF.before-final-pause-20261108T232909Z.md`；本轮仅在其原始bytes后追加，不改历史混合换行或旧内容。备份及append-only证明在`local/migration72/d02-resume-20261109/handoffs/`。

本页写入前主线为`408d07d066860ea61a043383489f716d50127f91`，分支`7.2-dev`。最终文档提交不自引用自身hash，以实际Git HEAD及`local/migration72/d02-resume-20261109/final-pause-post-commit.private.json`为准。patch为`8c255548e89c54d01f71c44dee0820831c7f0b87`，分支`codex/7.2-sdk-key-loader`，clean；resources为`dac2a9d43332ef51879522cf39da2831b0ca0543`。

主仓库预期只剩resources原有dirty标记。保留、不提交其`ScriptSceneData/flat.luas.scenes.full_globals.lua.json`差异，SHA `db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe`。所有提交仅本地，未推送。不要reset、clean、更新子模块或整包替换资源。

最新真实退出证明：`local/migration72/d02-resume-20261109/r2-early-bridge/live2-post-exit.private.json`，SHA `9115095a3ffefad62b9ef9cbb1f5512610a9e361b8ed0af66b445f943af38a90`。用户明确反馈登录页正常、已正常退出、无异常；原Astrolabe恢复stock、自有Astrolabe_orig alias移除。五stock、13保护文件、配置存在性均保持；相关游戏/Java/Mongo/调试器/patch guard及固定TCP/UDP均0。没有待恢复研究进程、待安装DLL或待用户处理的现场动作。所有子代理均已结束，没有后台在途任务。

GAME_PATH只用当前配置和`tools/game_path.ps1`解析，不沿历史路径/PID/base/地址。`.env`为portable，`.env.local`不存在；原config、AGENTS、resources差异和local/data九文件组成13项保护，原mongodata/local/data未清理。固定输入见`protocol/cn-7.2/profile.json`：EXE `8b014f64…b6b5`、metadata `45dc7a44…ef2a`、startup `b3148108…5105`、AccountPlatNative `adecdf0b…6c25`、Astrolabe `87e0a005…b442`，完整SHA在profile和本轮盘点；不得只用简称核验。

## 构建、部署与数据边界

只用Windows portable；不Docker/WSL/虚拟化。便携JDK17.0.20.1+1、Mongo7.0.45和Rust nightly1.101.0为本轮已读工具身份，恢复时重核，不套历史环境。HTTP/SDK8088、GS UDP22101、Mongo27017/测试27027按源码核，不凭环境变量声称隔离。

根JAR仍为`LunaGC-7.1.0.jar`，SHA `8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291`，既有内嵌496e96f。文件名版本来自既有build.gradle，未改版本绕guard。本轮真实Gradle执行相关Java测试/compileTestJava，不把它称作生产JAR重建或部署。R4、完整Avatar/Scene、必要资源及SDK自然链未齐，**未部署或启动GS**；没有HTTP健康/运行JAR正向服务验收对象，不能记通过。

独立早期观察DLL已经release/locked/offline构建并做两fresh各18 owned case，随后仅临时装到固定客户端取证，现已恢复。没有启用旧整DLL、额外WinHTTP hook、强制验签、未知输入放行或SDK key核心。纯工具/测试/文档修改无需GS重启。账号/数据库、原运行配置和生产资源未因这些片段测试改变。

日志、机器路径、凭据、依赖、客户端二进制、raw、数据库和临时安装备份均在忽略目录。临时配置字符串只在有界回调内复制，不保存自然正文、密钥、URL或其hash/长度。

## 本轮已验收及提交

本轮起点`d31de75`。完整逐项状态在Project，详细日志/命令在Harness及专题；不要按下表测试数量重复加总完成率。

| 任务/成果 | 实际范围、验收及本地提交 |
| --- | --- |
| E-01/R3-UPSTREAM-D02、R2-PATCH-DELTA | 本轮开始核两远端仍为固定参考提交，35原文件、13本地/18源码及manifest核对；无新资源/patch差异被采用。`b9280d9`，见[定向审计](docs/CLIENT_72_D02_UPSTREAM_REVIEW.md)。 |
| R3装备/成长/affix真实Java片段 | 原11101两属性、100曲线、零阶突破及有序[0,0]通过真实loader/ItemData/GameItem。武器Java4项、affix Java3项；已有NONE/0与上游省略分列。`4f6b72f`、`f67bcfd`、`2202fb2`。 |
| R3出生Avatar HP/grow及critical | 原生HP/grow18检查/99pin、Java4项及关联隔离项复用；critical原installer/typed20/22实际8case174调用40140hooks，主线14检查/12审核/111pin，Java3项过。`e25d4ce`、`a77d13f`、`525f31f`、`435577a`；+78及自然完整行仍未知。 |
| R3 Weapon→Gadget | 原Item+2c指向50011101，7case124调用12052hooks，15检查/12审核/98pin；真实Java2项过，GameItem持同数据对象。`3fd1ff8`、`35850c2`，见[Gadget桥](docs/CLIENT_72_D02_WEAPON_GADGET_BRIDGE.md)。 |
| R3 Gadget配置key/Java | 原+58键至原查询/hash，7case21调用3425hooks，18检查/15审核/113源路径/13原范围，`8f616cb`。后续独立核typed实体配置域和Java职责，仅批{id50011101,jsonName:Weapon_Sword_Metal}，真实Java2项0失败/错误/跳过，21秒，`d6e9766`。旧false报告不改，配置内容/actual hit不升级。见[key Java](docs/CLIENT_72_D02_GADGET_LOOKUP_KEY_JAVA_LOAD.md)。 |
| R1方法缓存/16B/32B后继 | 原自然cache141/3048、9原lookup及factory/8方法64B、16B发布/32B类接口行连续原前缀已验。第三分别20/17/23检查并同双fresh；`e24da93`、`0cb9a8e`、`241161b`、`8606a83`。原六前缀保留，完整cold未验。 |
| R1新ordinary/warm输入与原回放 | 原版90秒44监测，235/5873（R1 231/5869+SDK4/4），14审核；用户只确认退出，没有单独页面确认。原warm getter真实ret、四MethodInfo/ASLR函数指针由原store写入，8调用8794步，第三1839053B/22检查/16审核/37pin同双fresh；`1d94f27`、`759d7d4`。cold准确缺普通root+10294×8，未借inflated同号。 |
| R3 String合同、R1 warm collector/adapter | String静态21向量/10审核/17pin/一新383B窗八复用，普通1243与generic VAR0继承分立。纯collector主线第三212636B同双fresh，37检查/12审核/52pin/九复用窗，人工243/9032，85unique统一末尾复读；新adapter13检查及真实隔离文件gate过。`fc92620`。 |
| R2托管配置/UI与早期observer | callback/fallback/ShowLoginView/typed注册入口静态限定已验，实际UI producer仍未闭。早期EXE单入口源22断言/10 Python，实际DLL577536B SHA20186081…2854a9、51 EAT/72B DATA，两fresh18 owned与完整284B搬移/恢复通过。patch`8c25554`及父`e173839`；新Windows adapter16检查/12审核和真实文件gate，`ea6ae7a`。 |
| 当前合并实测 | UAC后90秒45监测，SDK9/1450+R1 256/8104=265/9554，84unique复读保持；14项纯重放/身份/保护验收过，用户正常页/退出无异常。SDK准确caller到达但event5超限；enum两个槽0、String usage0，业务仍partial。`408d07d`，见下节。 |
| 自然loadAll入口审计 | 只读31源/四候选、两JAR24类族共192条class及10依赖、LuaJ能力等10主线审核过；不需要先启动服务/DB。未确定首诊输入/脚本范围，未建driver或执行loadAll。`6edea26`，见[审计](docs/CLIENT_72_D02_NATURAL_LOAD_FIRST_AUDIT.md)。 |

本轮没有重新研究已验账号/保存、raw投影、八握手、SDK响应/真实HTTP、原生密钥桥接、透明加载、能量/装备/突破、Lua网格/Chapter/303及InstanceRoot成果；这些继续直接复用，不自动关闭父项。

## 最新实机原始入口（全部忽略）

共同根为`local/migration72/d02-resume-20261109/`：

- `r2-early-bridge/live2.private.json`：SDK45次monitor、前后身份及绑定；SHA `d7f1f483ae293069dbd4611837377c4b076674d7fe04cf42c9a1cc1f79488771`。
- `r2-early-bridge/live2.private.json.reads.private.json`：同次9读1450B固定code/pointer/自有DATA，SHA `ad4fe1971aea1a3db7fd384b65d2596cd94560b69480cf9a127ccbf7dcc79c0b`。
- `r1-warm-usages/live/warm-live2.private.json`：256读8104B、全部source/同轮cache/header和最终84 spans，SHA `cfea3e67d6795e791c541e2c140db8b3f19cc70d6c571f47e7ce84c3eb5a4204`。
- `r2-early-bridge/live2-main-review.private.json`：14独立检查/SDK纯重放及R1完整时间序列重投影，SHA `12dd94c7b1457a2a15c4041de45c91931afde773eb24e680e313e326ac0ffb1f`。
- `r2-early-bridge/live2-post-exit.private.json`：用户反馈/5+13保护、stock恢复/无alias/进程端口0，SHA见前文。
- `r2-early-bridge/live-plan2.private.json`及run/restore/launch-early-live2脚本：固定scope/14source pins、模块环境和admin guard。输出/ready已消费，恢复时不能重放这些文件名。
- `r3-java-gadget-key/main-proof.private.json`：真实Java2项及XML/输入SHA，证明SHA `a7e4653d41e66e8f7ae355692fe6c14662a8e9ecdefd78adda34ea9b08edb581`。
- `r3-natural-load-audit/main-review.private.json`：只读入口审计10项，SHA `da6b883b644fd493697a23e4dd2ad0c6c11ca664c8a94f4eac372bdc721ae012`。

## 当前未完成、准确首缺和停止边界

1. **完整Avatar/R1（F-03d3f4d2、g2）**：当前List/HashSet<uint32>/Default/enum类及旧warm getter/identity七候选取得。enum carrier15590的owner/同一canonical成立，但真实rgctx+10/+18均0，停getter-slot-mismatch，没读或造ctor。须先按原代码/实际同轮数据区分未初始化或其它语义，不能手设slot/flag、host返回Default或借旧owner。typed managed allocator、完整构造/装备/技能/池、自然caller/Handler和正式g2仍未验。旧cold分支另缺普通10294槽，不能拿inflated10294替代；两条路线不混成已闭。
2. **String/Gadget配置（R3及Scene）**：primary B+596c848实际0，停usage-null；没有Default/Equals输入，没有运行两个32-probe后备。jsonName lookup-key已独立通过Java，不应再把字段职责绑到完整配置actual hit。配置资产u64注册key、17591 owner、25270真实配置bytes/解析/ability仍未闭。Scene/Team/Entity自然注册、stage、Pool/分配、MPLevel和组件生命周期继续未知；不能用空配置或空实体代验。
3. **SDK自然链（R2）**：新单EXE入口、47转发、20B patch/完整284B trampoline、callback/其余代码窗及72B DATA相符，any_entry_seen/expected_caller_seen均1。event5 over-limit按预设停止，没有成功分类；合并错误码不能判明name还是payload，更不能推断正文大小/内容/缺NUL。原Marshal、SDK export/writer return/路由/校验/自然本服认证未验。不要仅放大限额或再跑相同实验，先审具体ABI/边界假设及安全事件；不扩大hook/强制成功。
4. **自然资源/保存（E-01/E-01c/E-01d1）**：必要字段片段可复用，但完整Avatar/Item、选中revision和实际loadAll/引用/日志闭包、正向QuestManager恢复仍未验。直接loadAll无需服务/DB，但LuaJ标准globals具有实际文件/进程/Java能力；本次首诊准确输入/脚本范围未选定。这个实验输入边界不新增AGENTS逐项审批规则，不把全部Lua/161表列P0。未启动新driver，不能手设loadedAll或把catch后自然true当完整兼容。
5. **R4/R5/R6**：正式sender/必要合同未汇合，隔离便携GS未部署，新旧号六项最终实机均未验。SDK登录页不是本服登录；没有显式选人/命名保存、完整场景/实际进世界、预定连续10分钟心跳或两类账号正常退出重登持久一致的完成证据。

## 外部来源与已审结果

缓存`local/migration72/upstream-review-20261108/`仍是逐文件/tree/commit审计材料，不是Git checkout。本轮开始已核远端HEAD为AstaPS-Resource `6cfecfb55f889f8e3b7156adb12809608db4f74b`和hk4e-patch-universal `2b589125874b0b96f8694bfad31e34811eba8573`；不声称它们永久是远端最新。恢复后若需要新增增量，先固定新提交，只审对应缺口，不混数据epoch。

相同出生行/曲线/突破直接复用，不能算新增资源；Weapon省略propType/initValue与明确NONE/0影响本地结果，不擅自等同。Quest接受条件/Lua/默认值、Scene差异未获语义许可。新Gadget候选key/ability本地已有，上游aggregate只看固定tree，不下载正文不能替代目标配置来源。未整包替换、未加猜测alias、未运行未审上游安装/同步/部署脚本。

WinHTTP域名限制/句柄关闭候选缺当前curl/Winsock自然路径覆盖和本地有效缺陷，暂缓；不因上游称登录重定向就安装hook。已有SDK key/透明加载、响应修复、ABI成果不重复做；强制验签成功不采用。

## 走过的弯路与复现注意

- 后台Start-Process继承的PowerShell模块环境可能找不到Get-FileHash；直接前台gate通过不代表后台环境通过。只读后台诊断实际复现后，新helper显式Utility和PSModulePath，后台CheckOnly复验。曾怀疑父进程寿命，但owned子进程正常完成，未据此改DLL。
- 固定EXE manifest为requireAdministrator；原CreateProcessW不弹UAC。启动token未提升会在PID取得前失败。当前helper安装前检查admin，UAC launcher先请求提升；不能把CreateProcess失败算作一次90秒实测。前两个失败均0 target读，临时安装已各自恢复。
- Cargo自动example是bin，不能直接覆盖cdylib；仅新增本example的crate-type声明，DLL显式cdylib、owned fixture显式bin。旧lock/依赖和入口不变。真实两fresh18例及284B实际emitter已过，不只核摘要/后缀。
- Marshal原helper以+14十六进制处UTF16/NUL扫描，不读取+10长度；旧SDK+OS转换proof不能冒充EXE managed Marshal已执行。当前event5也不能补出这项。
- 旧class80 Object模板有明确controlled来源，当前自然header不拿来填owned冷类；保持旧56B SourceMemory限制，新208B overlay独立核。实际ASLR函数指针原样存储，尚未发生该间接dispatch，不能大范围重定位“修好”。prologue中途[RSP]叫stackTop，不是caller return证据。
- String类型kind14的data/attrs不被原hash/equality消费，不把data当definition293；ordinary1243与generic父VAR0分别核，继承Equals的MI owner要实际读，伴随槽是derived+110+vtableCount*8，不是固定+118。slot函数不要求等于MI+8，两者原值保留、代码绑定另验。
- 定向窗口已有缓存就复用；hash leaf没有pdata时只可用独立下一个registered method边界，不能猜函数尾。旧/新AOT共享类型与concrete caller context分开。minified JSON必须结构化投影，别rg整行泄出巨量raw。
- private旧失败和确定性报告不覆盖；日志UTF-8 BOM名称是utf-8-sig。JSON数字存在/同ID/同数量不证明语义；省略、显式零、float32、顺序和list结果继续分别记。

## 恢复后的候选顺序（现不执行）

先按AGENTS重新读当前HEAD/差异/子模块、配置/GAME_PATH、五输入/13保护、进程和端口，不沿本页旧PID/base/结果。复用现有证据与本批local提交，不重做整轮历史回归。

优先离线审最新enum两零槽、String空usage和SDK over-limit的准确原语义，给最小解除条件；需要新实机时先准备具体范围/预算、源门禁、UAC/备份恢复、自有验证，再一次请求新实时ready。旧ready全部消费，当前没有待用户操作。

资源继续已批最小片段→真实消费者和必要引用；Gadget配置/ability、Scene生命周期与自然loadAll/正向保存按真实缺口逐项推进，不改成全量迁移。输入足够后立即最小实现并真实Java/保存/任务回归，不长期停在研究卡。Sol max处理冲突/ABI，明确实现交Sol/Terra；Root独占Git/构建/DB/端口/客户端/安装。Astra只有用户指定的最后升级条件满足才用，本轮未使用。

只有R1/R2/R3必要出口齐备才R4正式发送汇合→R5独立便携部署→R6真实新旧号六项实测。运行代码/Proto/资源/配置/patch变化后按AGENTS构建、部署及日志/健康/端口/PID/运行JAR核；前置不足记录未部署原因。Proto只改源经Gradle/protoc生成。所有提交本地，不自动推送。
