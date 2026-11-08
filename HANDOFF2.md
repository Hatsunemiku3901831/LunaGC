# GS 7.2 收尾交接（2026-11-08，已暂停）

**当前已按用户最新指令暂停，不开启新任务。D-02 / F-05 / D02-R6 未完成。** 当前在途的key实际观察、Avatar接口后继与突破资源已由主线完成既定范围的第三复验、验收和分项本地提交；没有把局部研究或SDK页面当作D-02。此暂停决定覆盖先前“持续执行直到D-02”的节奏要求。恢复前不得自行新开卡、采样、客户端、生产实现、构建或部署。

接手顺序：AGENTS → README → Project → Harness → 本页 → docs/CLIENT_72_MIGRATION.md → docs/CODEX_ONE_CLICK_DEPLOY.md，再读专题。Project是唯一当前任务/验收队列；历史快照和本页旧备份只作追溯。当前技术缺口不等于全部外部阻塞，本次停止原因是用户明确要求暂停。

## 交接保护与实际现场

覆盖本页前已逐字节备份旧HANDOFF2：`local/migration72/d02-resume-20261108/handoffs/HANDOFF2.before-final-pause-20261108T032618Z.md`，SHA `70810efe28e78c9a0525bd4b92ca822e36d8ce7a942e2cabe9bd5d42bd1adc51`。另备份旧HANDOFF以核append-only：`local/migration72/d02-resume-20261108/handoffs/HANDOFF.before-final-pause-20261108T032618Z.md`。HANDOFF只在原始bytes之后追加，不归一化其历史混合换行、不删改旧内容；写入/前缀核对在本轮忽略目录`handoff-close-proof.private.json`。

本页写入前主仓库为`9f0002e8d891febf4e560796f6a99af0ce1d3bce`、分支`7.2-dev`；最终文档提交以实际Git HEAD及`final-pause-post-commit.private.json`为准，不能把这个写入前hash当未来HEAD。patch当前`e2d307227c6b5614260d63b0b73776962583dc19`，本地分支`codex/7.2-sdk-key-loader`；resources当前`dac2a9d43332ef51879522cf39da2831b0ca0543`。所有提交仅本地，未推送。

最终现场入口：`local/migration72/d02-resume-20261108/final-pause-inventory.private.json`。实际无项目游戏、Java、MongoDB、调试器、patch guard或本批研究进程；TCP8088/22101/27017/27027及UDP22101均空。无待恢复的暂停研究进程，无待用户处理的客户端/实时动作。无关MCP/其它Python进程未操作。GAME_PATH只取当前配置及`tools/game_path.ps1`，不复制历史路径、PID或地址。

原Astrolabe已恢复stock，实验新建Astrolabe_orig别名已移除。原.env/config、AGENTS、原resources差异及local/data实际9文件共13项逐hash保持；.env.local仍不存在，原mongodata/local/data均未清理。保留且不提交`resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json`既有差异（SHA `db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe`）。主仓库预期只剩resources的既有dirty标记，patch应clean；最终以post-commit盘点核对。

五stock输入在最终现场重新核对：

| 输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| AccountPlatNative.dll | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| Astrolabe.dll | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

只用Windows portable，不部署Docker/WSL或虚拟化。实际工具版本本次读取为：Java `openjdk 17.0.20.1 2026-08-18`；Mongo `db version v7.0.45`；Rust `rustc 1.101.0-nightly (19833af72 2026-10-20)`。HTTP/SDK8088、Mongo27017等硬编码边界按源码核查，环境变量不能单独证明隔离。

## 构建、部署和数据库边界

最后生产Java变更仍为R2-LOG `a9fb9df`与R2-SESSION `496e96f`。已有portable构建`build/artifact-proof.json`记录JAR46306类、新增2/变化9/删除0均与该次编译匹配、46295保持；这是该次记录，本轮未重做广泛class比较。本轮另做真实compileTestJava与test runtime classpath生成，不把它当作生产JAR重建或部署。

当前磁盘JAR重新核SHA为`8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291`，既有产物内嵌496e96f。版本名称仍由build.gradle导出7.1.0；没有改版本绕guard。R4、完整资源及SDK自然链未齐，**未部署GS服务**，健康/TCP/UDP/运行JAR没有服务对象，不记通过。Rust新入口已实际构建并仅临时安装取证，现客户端恢复stock；没有启用旧ext.dll/ccp/memguard/swap或完整性绕过。

独立Mongo27027的账号/HTTP实验都用新runtime/db及UUID库，完成后正常admin shutdown/exit0/端口释放，关闭前仅admin/config/local。原27017未启动，未在原数据库创建/修改测试账号。本批不需要冷备/迁移原数据；不得以在线复制mongodata冒充冷备。原始日志、机器路径、token、密码、客户端字节、依赖、数据库和PIPE正文留忽略目录；PIPE正文/token/sign及其hash没有导出。

## 本轮已验收成果和本地提交

原有R0、协议raw/库存/Avatar片段、隔离脚本、Lua/Chapter及资源解包/索引/六表成果直接复用；完整旧细节见本页备份和Project第6节，不重新排队。

| 子项/本地提交 | 实际验收与边界 |
| --- | --- |
| R2-LOG / R2-SESSION，a9fb9df / 496e96f | 有效日志红绿、单token session独立适配、真实HTTP→原生、账号/密码/保存关联193项及构建已有证明；日志39与193互含，不相加。原passport数组不改，session不重新轮换token。原生session双报告6634B/7a5f3bd1…69fff6。部署待R4。 |
| E-01-bridge-energy，029fea2 | 七字段/真实三表能量消费者，主线第三10,370,640B/d990c2c4…66307e、17项；不是完整Java资源或revision。 |
| fresh-ancestor，0676a00及前置0cbf31e/0890588 | Object kind28已解、两400B类壳原生发布；旧Array/Type和已采父descriptor不再列缺。第三1,703,202B/dc738c4f…933256b、22项，止1169接口。 |
| R2 loader/sign/natural-config，305d9de / b287819 / 5768668 | 准确SDKDelegate.LoadFile包/typed字段、url:string、map/app_key片段；完整原生signer/发送前builder、HMAC独立oracle；Config根/生命周期与原持锁事件合同。主线第三分别471340B/bae776a1…bee1471（13项）、185859B/f2f8f3ad…379d434（15及20审计）、8100B/396b9ceb…28b76d2（14项）。自然Config/env/route未验。 |
| E-01-bridge-equipment，6328e0b | Avatar169/Weapon288/WeaponCurve100真实表与初始武器/两属性/成长片段；第三4,164,206B/50aaea94…eb5bc，16项及20审计。Weapon NONE/zero原项保留，Promote缺口当时没有补空。完整Java行/加载未验。 |
| R1-SAMPLE-INTERFACES，9a2c099 / 86b5318 | 新ready原始90秒44观察，8读584B零错，1169/1249 kind18头/70B metadata相符、双零compact，TLS索引实读0；主线29项。raw SHA3c08c1f7…64d040。用户正常页/退出；线程TLS和消费前时序未证。 |
| R2-COMBO-HTTP，7ae58b3 | 实际Java编译、6项真实Javalin/default认证/Mongo同步保存JUnit（0fail/error/skip）、16 Python/mock及六例准确native builder/sign→真实HTTP/Mongo→native parser/formatter全部通过。23,602B/4d8691f3…994b37，14 class/source/输入/保密独立核对。失败BSON不变/无token，成功沿既有login-token轮换；完整UI/自然SDK未验。 |
| R2-KEY-LOAD-owned，patch e2d3072 / parent9ea77cd | 独立47转发+准确key核心，release生产578048B/ad18e741…65a2139d，fixture631808B/ded080ec…adce8。主线第三两新owned各917B/72b0759e…21a8同双跑、12项及独立EAT49审核；9绑定/2材料拒绝和真实ilhook错误。 |
| R2-KEY-LOAD-OBSERVE，4eb17f1 / fdec2b6 | 主线19项、实际owned emitter与原mover证明；新ready真实90秒45观察，8读1244B零错，稳定state5、47表、far297B完整trampoline/原38Bcallback/回entry+18均相符。raw1,301,118B/1a43006d…da4535c、38项复核。用户正常页/退出，原DLL恢复、实验alias删除。自然caller/key选中/route/完整信任未验。 |
| F-03d3f4d2-fresh-interfaces，42310a2 | 第三2,114,437B/d89df39b…3e9e970同双跑、22项及23审计。三类壳400/400/208、原64B清零/接口写回；原方法表首缺保留，独立静态index投影后准确止method-cache mask。完整初始化/default/Handler/g2仍未验。 |
| E-01-PROMOTE-ENTRY，9f0002e | 第三3,297,024B/546b95ce…d70fef1同双跑，20项/26独立审计/6宿主gate回归过。WP1821/AP889/AC100完整原parser与EOF，出生用途2个Promote/100完整Curve输入、Avatar批准字段片段、完整Weapon stat6非零case通过。不是完整Avatar/Item/自然Java加载。 |

以上是分项范围，不相加为一次“全量测试”。所有“已完成”均保留ID、来源、模型/受控片段/自然状态/实机效果的区别；子项通过不自动关闭父项。本批沿用已配置Sol max处理资源/Avatar、Sol xhigh处理HTTP/observer，主线检查源码、原证明并独立第三；未调用Astra。历史目录含astra不代表本次换模型。

## 未完成、准确首缺和禁止绕行

1. **R1完整Avatar/g2：**未投影case仍在14051e387读1234+98方法表首8B时停止。独立受控投影仅array64B及8个MethodInfo+18的4B index10287..10294，没提供其它MethodInfo字节/自然指针。之后原1404eeb80首缺module+5bc7290的mask8B，随后+5bc72b0 blocks8B、+5bc72b8 probeLimit8B。`r1-fresh-interfaces/next-runtime-contract.private.json`实际3843B/c7b6b894…541e83f3，**只是3根24B最小解除点，samplingReady=false**；需先闭合真实hit/miss/上下文和完整预算，不能把模型classInstance/hash当live bucket或只补空cache。两新窗口/2组case/各调用cap2M，旧六前缀path/regs保持。完整cold/default写回、Avatar Handler、同源保存字段及正式g2未闭。

2. **Scene/Team/Entity：**Pool slot4/mode/type46314已取，原前缀到TLS索引后继；索引0是实际loader值，不证明分配线程GS:[58]/TLS数组或allocator。能力注册根/typed头非空不证明完整key/string/bucket/entry、默认名、stage/MPLevel已加载。stage与Scene3表枚举不能混用；还需组件创建/替换/拆除、owner和通知顺序。SceneTeam/EntityAppear及世界后续各自三层未闭，不能只验八握手、删通知或用LOADED代替角色落地。

3. **R2自然SDK/route/trust：**准确包/LoadFile producer与map/app_key/sign/Java接收可复用，不再找普通Config/server_pc文件或重开7AD200旧阻塞。自然选env、实际managed→native Init、运行route/会话、真实key caller/替换和完整校验仍未验。完整378850旧探针止CRT locale encoded callback（490af1→591860→591820），未加atoi/locale成功桩；现只批准writer片段。Config+860是shared ownership且writer会释放旧节点，双读头不证明无writer；原持锁37e430区间仅保护URL map，不保护env/app_key。新key实际installed不等于自然认证；旧整DLL未验且禁止顺手启用。

4. **R3完整资源/保存：**已取得真实资源字节、完整解包/index/六表；不再重排这些已完成定位。Promote当前只批准出生必要用途group/phase/costItems/addProps，两条0阶原列表不能伪造[]：武器5个zero props/3个(0,0)材料槽，Avatar4个zero props/4槽。其余coin/scoin/unlockMaxLevel/requiredPlayerLevel职责未闭。AvatarCurve100×4全部MULTI、完整声明字段齐；Avatar仅id/avatarPromoteId/attackBase/defenseBase片段批准。+64目前只证MAX_HP/CUR_HP，不是hpBase；+98/+a4的critical/criticalHurt及growth caller未批准，完整Item/ability/gadget/ID引用仍需核。Java onLoad过滤是源码模型，不是JVM自然加载。active selectedRevision、自然NativeConfig必要输入、loadAll/事件/Lua闭包与保存→新Player→真实Manager正向恢复未验；保留loadedAll=false拒绝/BSON不变负例，禁止手设ready。

5. **R4/R5/R6：**完整P0的CmdID→type、type→语义、field→wire、嵌套/聚合/枚举/时序/最终sender未汇合。Proto只改源并经Gradle/protoc生成；未知保持未知，不能把旧legacy正包号当目标。独立InstanceRoot已验入口可复用，但GS独立配置/资源/DB/运行JAR部署未做。D-02六项（旧号本服登录、新号显式选人命名、完整初始化、实际落地、约定时长心跳、两类退出重登保存）**全部没有最终实机通过证据**。

## 失败记录和避坑

- 最早Session日志所谓red1是缺throws编译失败/旧XML，只有sdk-red2四分支4/4是真红；35/39/193等不同阶段有交叠，不求总和。SDK PIPE必须内存通道，禁止terminal/tee/正文或凭据hash落盘。
- 原接口合同原件为6614B/c84…，写前LF声明误作6465B/bac…；149处CRLF而字段相同。保留原件，新建主线LF投影6465B/bac…并单独pin；不能忽略换行放宽gate。见`sampling-contract-newline-audit.private.json`。
- 原透明观察0x410缺SYNCHRONIZE导致Wait WinError5，另有\\?\路径字符串差异；两次失败均发生0 RPM前。改0x101010与samefile，不能改目标绕失败。新key observer已沿正确合同。
- 新owned emitter构建main1缺full rmeta，main2只rmeta缺rlib，main3漏同release LTO导致LNK1107；main4同名双格式--extern+原profile才成功。Rust源和库未变，不能把编译输入失败说成native编码差异。实际owned Hooker当时near281；far33B是原mover合成地址。随后真实客户端另证far297，层次分开，详见key观察专题。
- 新key EAT实际49：stock47、state DATA@48、冻结核心既有sdk72_key_input_inner@49。仅这一准确内部符号经主线显式接受，不得把未知导出统统放行；不为统计复制callback或加未验wrapper。userdata不读、R8正文不读。
- 原资源probe01 whole2M触限保留；WP1821行的连续原调用分段由主线批准累计6M，AP/AC各2M。正式累计含readout/index为WP2,910,551/AP1,508,548/AC111,853，无重置/隐藏getter开销，段间寄存器/stack/heap/global/cursor连续校验。
- 资源probe03先为实机窗口按准确ownership/creation短暂hold，stock恢复后同creation resume；后来定位每primitive call追加重复allowed区间导致数十亿宿主扫描，主线终止这个确属旧开发预览（非graceful、非native失败/通过）。当前formal源只sorted(set)去重同一tuple集合，6个真实host gate断言证明覆盖/拒绝不变，双跑和第三均成功。不要再启动、resume或等待旧硬编码PID脚本；无遗留暂停任务。
- 主线审核helper的offset类型误写及一次shell引号准备失败仅属私有工具，原失败/源保留；只修审核表示/文件脚本，没有改原生producer/断言/原报告。最终现场盘点改为文件脚本和准确guard后缀/排除自身，避免inline命令字符串自匹配，不操作无关Python进程。
- 没有稳定输入就不扩大推理/模型或盲重复SW/HW/ETW；需要新现场先补合同/预算，再取得新user-ready。历史ready、指针、PID和ASLR不能复用。

## 证据入口与恢复后候选步骤（当前不执行）

本轮raw统一前缀`local/migration72/d02-resume-20261108/`：

- `r1-fresh-interfaces/{main-third.json,main-review.private.json,tests-main-third.log,manifest.private.json,next-runtime-contract.private.json}`。
- `r3-promote/{main-third.json,main-third.independent.json,main-review.private.json,tests-main-third.log,allow-list-check-main-third.private.json,delivery-manifest.private.json,probe03-host-cancellation.private.json}`。
- `key-loader/{main-review.private.json,verify-owned-main-third/proof.private.json}`及`observation/{live-key1.private.json,live-key1-main-review.private.json,live-key1-restored.private.json,live-key1-post-environment.private.json,owned-emitter-build-main4.private.json}`。
- `r2-combo-http/{native-http-main1.private.json,main-review.private.json,native-main1-runtime-proof.private.json}`、`r2-combo-http-green-main1/`。
- `r3-equipment/main-third-review.private.json`、`r1-interface-sampling/main-live-review.private.json`、`build/artifact-proof.json`和最终现场/交接proof。

恢复授权后先重新核HEAD/dirty/子模块/配置/GAME_PATH/五输入/进程端口，再从Project未完成包选关键路径：方法cache准确输入、SDK自然Config/route/caller或剩余必要资源字段可独立计划；批准用途输入达到实现条件时交明确Java规格，不扩全玩法。前置齐才R4→R5隔离部署→R6新旧号实测。需要用户实时动作，先准备构建/采集/观察方案，再一次说明具体操作并等新确认；用户不在时推进独立工作。现在不派代理、不启动这些候选动作。
