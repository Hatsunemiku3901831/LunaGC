# R2-ROUTE-CONFIG-SELECT / B-REQUEST-ADOPTION（2026-11-10）

## 本轮有界研究计划

本轮按主线最新恢复授权继续 D-02；旧交接的暂停仅是历史状态。本页责任线只写本页、必要的 `tools/research/d02_route_adoption_20261110_*.py`，原件独立写入 `local/migration72/d02-resume-20261110b/b-route/`。共享索引、生产源码、Proto、patch、旧研究材料和旧 pin 只读；构建、现场、部署、状态及 Git 提交归主线。

| 项目 | 本轮合同 |
| --- | --- |
| ID / 目标 | `R2-ROUTE-CONFIG-SELECT / B-REQUEST-ADOPTION`：收敛 `LoadLocalConfig/SetEnv/GetURL` 必要选择语义，以及实际请求消费者、触发、最终 URL 分类、调用身份和返回边界。 |
| 已有 | 八点同配置 writer/parser/env store/native 正常返回；原锁内 stored `comboLogin` 为 CN env0 类；stock v2 当前 managed env0/base0 非原子候选；40108 读取 managed `URLConfig.comboLogin` 并加单个 `?` 后交 33107。上述分别保留 epoch 和验收范围。 |
| 首缺 | `LoadLocalConfig(33350)` 完整 env/cp_app_id/第二布尔参数/返回选择分支；没有同一新 epoch 的 SetEnv/GetURL 分支和 40108→PostRequest 原实参采用证据；零凭据自然触发未知。 |
| 方法 | 先核本机冻结 code/report/signature/metadata 及旧外部检索；只为首缺申请固定 SHA 的唯一 exception body。静态边、当前存值、真实采用、发送和认证分别记账。先证明窗口必要性，再提取，不取邻函数凑边界，不运行目标或补 host 成功。 |
| 依赖 | 固定 profile/原 AOT+exception 身份、已审调用链与 managed String/字段来源；新的现场实现必须由主线审定普通 Win64、生命周期、LastError、首次尝试/预算、代码绑定、异常边界及恢复。 |
| 范围 | 只离线 PE/既有 cache 和来源审核。0 客户端/调试器/API/DLL 安装、0 目标 CPU、0 JVM/DB/服务、0 Git 提交或切分支；不再运行已回答的 SDK 页面实验，不扩旧预算、不要求官方账号。 |
| 产物 | 本页中的必要选择调用链、准确消费者/触发和最小实现或观察规格；新输入/审核原件、完整 SHA 清单和可复现命令；给 Terra max 的中文 GitHub 定向增量检索计划。 |
| 验收 | 原 bytes/完整边界/AOT/caller/签名分别有来源；已知/推断/未知分列；最终 URL 只固定类别，单个 `?` 语义明确；已验事实不跨 epoch 拼业务成功；给出 key/信任→本服认证→会话的下一必要出口。 |
| 停止 / 恢复 | 缓存足够给出可实施规格即交 Sol xhigh/Terra并释放 max；唯一窗口后若首缺已闭也立即返回。不为缺现场扩链。缺精确 AOT/exception、私密凭据/真实自然触发或未批动态合同时，保留准确缺输入与解除条件，交主线安排，不猜测启动。 |

## Sol max 中文 GitHub 定向检索补充计划（交 Terra max 执行）

此节是本新缺口的计划，不宣称已经完成检索。已有固定来源和仓库外 clone 优先，不重爬无关资源/协议。只查可复用的 managed 配置选择、CN SDK route 采用/补丁接线成果，不能用上游字符串或 patch 行为代验本客户端。

1. 先读取原 `b-natural-successor/github-route-map-increment.zh.md`、其 result/ledger，以及本机既有 GitHub 研究 manifest；核固定 `hk4e-patch-universal` main `2b589125874b0b96f8694bfad31e34811eba8573` 的来源和仓库外 clone。原轮只答 native map，不自动覆盖 managed `LoadLocalConfig` 新缺口。
2. 精确检索词组按顺序：`"ConfigManager" "LoadLocalConfig" "cp_app_id"`、`"URLConfig" "RequestComboLogin"`、`"comboLogin" "PostRequest" "hk4e_cn"`、`"Config/server_pc.json" "SetEnv"`。GitHub code search受限时，记录状态，改用已知仓库 tree/blob 或网页代码搜索，不能将403/空页记作零命中。
3. 优先复核已有相关仓库的全部可见分支名/commit/tree，并只拉命中分支的新文件：固定 hk4e 补丁来源及其有明确关联的 fork；新命中必须记录 default branch、命中 branch、commit SHA、blob SHA、license、精确文件/行、真实作用是 native map 还是 managed caller。不要只看 main，也不把分支列表未完成写成全网覆盖。
4. 若源码明确实现当前所需 `LoadLocalConfig` 选择/managed `URLConfig.Instance` 或准确 combo route 采用，先做小文件差异与适配分析；只有有实际复用价值才按固定 commit clone到仓库外已有研究根，保留 clone路径于私有 manifest。不得把客户端二进制、源码镜像或 clone 放入本仓库。
5. 有界预算：先复用已有 ledger；最多4条精确查询、每条前20个可见命中、最多3个明确相关仓库、每仓库最多30个可见分支元数据及10个命中文件。每个请求记时间、URL/status、返回 SHA/限制、后续选择；一轮无增量或平台限流即返回 `partial/no-new-evidence`，不扩大预算。
6. 输出到本卡私有根的独立 `github-increment/`：`plan.zh.md`、request ledger、去凭据结果、固定来源 manifest和“可直接复用/仅背景/不适用/未知”表。只把脱敏结论追加本页；无结果仍保留限制与解除条件，不重复 clone已固定来源。

## 研究结果与边界

**本轮新增静态结论：`LoadLocalConfig` 每次加载并选择资源行；第二参数原声明是 `readOnly`，它只控制末尾是否回写 `ConfigModel.local`。`true` 仍执行 game key、passport app id 和模块配置的正常更新路径。** managed 最终请求的消费者仍是40108→33107；当前没有自然采用或认证新证据。

已直接核三份主线冻结接受原件，不重跑其页面实验：八点 proof SHA `9ca7be02728749d5e8ac10c5cd099318b720e79348680585aab2b32c1eb76f33`、锁内map proof `ff65adf58a0dfea4ece85fa6a468748b89f4812aaecfd1bd2fd14b01e592b09f`、stock v2 proof `d6ad7d00f2656c017e74fbf828683fe8d2be98c54800db87e28a3558009338fb`。stock v2实际A62读1388B+B26读432B=88读1820B，当前managed env0/base0一致候选；其 `managedRouteAdoptionProven=false` 和三轮不同epoch保持。stored map、当前字段、请求参数不能合成一轮成功。

### 唯一新函数窗与必要性

原 `r2-config-select/explore-windows` 的四窗不含33350，`r2-route/main-third` 只有其 `[197761C6,19776225)` 与 `[19776669,1977672E)` 两个片段，不能回答完整选择与readOnly。本轮据此只取得一个原exception body，未取下游函数或xdata：

| 项目 | 实际固定结果 |
| --- | --- |
| owner / method / 原声明 | 4408 `MiHoYo.SDK.ConfigManager` /33350 `LoadLocalConfig(env, readOnly)`；签名参数42153/42154，第二参typeRef445378；不是从行为猜出来的force/cache名字 |
| AOT | 原 `QWORD[1422F98E0+70]` 指向 `1428A54D0`；method33350原slot指向 `159776120`；两个原QWORD留原件 |
| body | EXE RVA `[19776120,19776AD1)`，2481B/504条完整指令，SHA `b590f54fa3b514a070afe8342d68667618bb794d78ed552fa930a05e903ec01f`；完整拼接等于原bytes，两段旧局部SHA相同 |
| exception身份 | 原pdata RVA `1B22915C`，12B SHA `b2609fdd932f751a4c918cd2594643cfe3ef9c0da7f19719edae45332eb6943d`；唯一exact且唯一containing记录；unwind RVA `5400468`，xdata读取0B |
| 原caller | `SetEnv:197770DF`、`GetURL:199AE7A8`、`LocalConfigSync:1980775C` 的各5B原E8均独立相对解码到本entry |
| 输入 | 当前重新解析GAME_PATH，只读核profile五stock大小/完整SHA全部匹配；不沿用旧机器路径/PID/heap，不运行目标代码 |

原件为本卡私有根 `load-local-config-window/`。extraction SHA `3fa7ba960074951a63151286ff1d1fb8667bd894159a633e5b399f56c759d07f`；新脚本为 `tools/research/d02_route_adoption_20261110_extract.py`。所列PE读取与hash是磁盘证据，不是target CPU或动态调用。函数尾部另有冷路径；缺xdata，不能据它给异常捕获、展开或透明性背书。

### 必要选择与正常返回的准确链

| 原位置（EXE RVA） | 已证明的选择/状态关系 |
| --- | --- |
| `1977613E/41/43` | 保存R8d readOnly、EDX env；env按原int32使用，不由native DWORD0倒推。 |
| `19776161..6185` | `ConfigModel.Instance(+C18) → client(+20) → gameKey(+20)` 留R14。独立字段是ClientConfig field19495 `gameKey:string`；不是ConfigIni或任意app id。ConfigModel/client非空前置仍在。 |
| `197761C6..6220` | 从SDKDelegate.LoadFile静态槽+1530取delegate，传已验编译期`Config/server_pc.json`；缺delegate、null/空字符串走正常null出口；非空调用原JSONNode.Parse32952。没有“先返旧local缓存”的分支。 |
| `19776238..6366` | 取数组后，index从0递增。env转换成JSONNumber的double，和`row["env"]`交原JSONNode.op_Equality32945；相等后，若client.gameKey为null/空则选该行，否则把该gameKey包成JSONString，与`row["cp_app_id"]`交同一原比较器。首个符合条件的index进入选择；未执行比较器，不能额外宣布所有JSON数字/字符串表示等价。 |
| `197763AC/64E4/6504` | 命中保存实际index；无匹配时默认index=0。命中且index!=count的分支在`197764F8`写已知configLoaded byte=1后继续。 |
| `197763C5..64DF` | 无匹配时还有一个反射式原谓词P。若P真且env为位掩码`1018AC`的成员`{2,3,5,7,11,12,20}`，原`all_set_env("2")`路径后写configLoaded=1并正常返回null。本轮不为P猜正式方法名或异常含义。八个已审CN资源env`{0,1,4,6,8,9,22,19}`均不在此集合，故CN本缺口不需继续取P的helper窗。 |
| `19776519..65E7` | 其余无匹配路径写configLoaded=0，原SendLoginFlowClientError32745取错误码-1005；随后仍继续使用index0。不能把configLoaded=false等同必然null返回，也不能把后继非null等同配置无错误。空数组/坏JSON的callee边界没有执行或放宽。 |
| `197765E7..66D8` | 选定行→原StringBuilder/字符串→原`15482C600`对象转换call，结果存RBX；同index的`row["url"]`交ServerRegionManager.LoadConfigUrl32182。本轮没有给该generic转换call发明方法/实例名，也未执行完整parser。 |
| `197766E6..6729` | RBX+38（LocalConfig field19764 `cp_app_id`）交SetGameKey33346；RBX+A0（field19781 `passport_app_id`）交NetworkManager.SetPassportAppId33092；再以原env调用GetModulesConfig33349。**此处+38不是app_key；LocalConfig.app_key独立在+28。** |
| `19776737..6824` | GetModulesConfig非null时将列明的模块字段复制到RBX，并写ConfigManager.Instance.hoyopass等状态；readOnly的判断尚未发生。这里只确认原copy与字段关系，不把完整模块语义或这轮自然值补齐。 |
| `1977682A..6863` | 检查保存的readOnly低byte。非0直接到共享正常返回；0才把RBX写入`ConfigModel.Instance+30`（field19428 `local:LocalConfig`），然后返回。 |
| `19776398..63AB` | 共享正常出口将RBX交RAX并恢复栈/RET；可以是null，也可以是上述结果对象。正常返回不是完整Init、HTTP或业务成功码。 |

只新增三个准确编译键`env/cp_app_id/url`的literal来源，分别固定usage row770003/770007/767929、literal index7630/7634/3179；cipher总15B，各≤16B。来源审核先核117201003B metadata cache完整SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`，拒绝全部执行型pickle opcode后才读纯数据，选出26项准确type usage和相关声明。它不把编译期type cell当自然class，也不读取自然gameKey/cp_app_id/app_key。

`source-binding/audit.private.json` 23项实际通过，SHA `e2de6bc0d436cb0d5149dae5fd86d8be4aacf411c0777f24f0beaa5fe9a6c878`；compiled keys SHA `d9e38bc0607f3d609598694064e13a178337f8ad4db768ea7d7dbdf678645c53`。脚本为 `tools/research/d02_route_adoption_20261110_sources.py`，新增code窗0/target CPU0/live0。

### SetEnv、GetURL和请求采用分别验收

已冻结SetEnv先在`197770A0`写ConfigModel.env，再用该env/readOnly=false调用33350；返回非null才在`19777103`调GetURL。同一个SetEnv成功路径因此还会触发GetURL内部的第二次`LoadLocalConfig(env,false)`，不能把第一次返回对象直接认作第二次对象或同一次资源快照。

GetURL的原成功分支要求返回对象非null且其`+B0/url`非null，调URL ctor32801后写静态`+B80`；另一分支返回此前`+B80`。ctor正文/完整复制及本轮动态分支仍未执行，旧非null值不能代验刷新。本轮只把上述选择与readOnly静态首缺收窄，不回填旧proof。

普通成功账号回调40103在ret0、AccountModel非空并走已审uid/token分支时，从`197FFE91`调用40108。它可由合法既有会话/票据触发；没有已证的零凭据页面动作保证该条件。40108从managed`+B80/+E0`取comboLogin，原String.Concat3018加编译期单个`?`，在`19801312`把RDX交33107 PostRequest，正常返回边界`19801317`。body、callback、headers来自原入参，timeout5.0/retry3保持原callsite。SDK native map不在此路径内。

## 立即交 Sol xhigh/Terra 的最小实施出口

现在可实施的是**独立pure最终URL分类器及来源验证器**；无需新页面实验、改env或route。精确输入为8个已审CN完整base各自加**一个**问号后的UTF-16LE，长度集合65..70 units。只输出`managed-final-request-cn-env-k`或unknown，不prefix匹配、不剥query、不规范化、不接受第二个问号或任何额外参数。未知内容、长度、hash和body/token/headers均不输出。人工数据覆盖全部8类、base无问号、额外问号/query、内嵌NUL/未知UTF16和边界64/65/70/71；这只验pure分类，不构造目标托管对象或执行native。

**准确自然采用观察切点应放33107入口，并核原返回地址`B+19801317`及RDX原最终URL。** 原CALL留在`19801312`时，这才能把“最终值候选”收窄为该原消费者实际进入。当前已知40108完整328B足以冻结上游caller和实参；本轮尚无33107的原exception body/最小覆盖输入，不实现或安装新hook。

这一步选择入口还有具体原因：从40108的`19801312`起最小14B完整指令覆盖会包含原PostRequest CALL及部分epilogue（实际至少15B），搬移CALL会改变callee所见返回地址；而更早点只看到Concat结果候选，不能单独声称33107已进入。不要把旧“CALL前语义切点”规格直接当作已验ilhook位置或返回观察。

给下一工位的准确输入/停止合同如下：

- 只为33107入口必要覆盖，先按owner4362/method33107 AOT `15992C890` 与唯一原exception目录定界；准确输入缺这一body/pdata及普通Win64/managed String生命周期、LastError、首次尝试、代码绑定和恢复的实施审定。新xdata若未取得仍标未知，不承诺异常展开透明。
- 准确身份用一次新epoch的EXE/SDK输入、实际base/module与固定source；原33107入口的`[RSP]`必须等`B+19801317`。只选首次任意入口尝试，先占尝试再读caller，错caller封存不等后续命中。若确实需要上游40103身份，则另以40108入口`[RSP]==B+197FFE96`和同TID/同原栈范围绑定，不能仅按同线程合并。无必要不加上游点。
- String类型参照复用已审fresh `QWORD[B+5886040]`，对象首QWORD须精确相同，再按已审`+10`长度/`+14`字符读取；参照null/不符、null对象、未知长度/bytes一律有效拒绝/unknown，不改对象。现场只输出固定类别/事实位，绝不保存未知URL、body/账号/token/签名/headers。
- 单个入口的候选最小数据账可定为caller8B、String类型cell8B、对象class8B、长度4B、字符最多140B，即最多5次/168B；这是待实现审定的新最小候选账，**不是沿用或扩展旧observer/RPM许可**。失败无重试、不补页、不追body。身份与代码绑定另账；原调用、参数、返回与请求策略不改。
- 若只证明入口到达，可称该原PostRequest消费最终URL；不能称网络发送、异步完成或认证成功。`19801317`只代表PostRequest同步返回，仍不是HTTP完成或业务成功；当前没有它的自然返回观测。缺合法自然trigger时保留未到达，不用官方凭据、伪ret0/空账号或重复90秒碰运气逼事件。

LoadLocalConfig选择观察是独立缺口：若主线确实需要证明自然选择，应同一新调用记录env/readOnly/准确caller和选择分支/正常返回，不以stock当前值补齐；只记录原数字和有限事实，不读gameKey/完整资源正文或账户内容。该观察不必与请求观察同轮强合，也不把两次LoadLocalConfig调用合成一帧。当前先交可实施pure分类/验证与33107准确缺输入，max到此释放。

## key/信任、本服认证与会话的必要后继

route观察只有固定类别。若结果仍是CN官方host，后继需由主线审定本服route接线和目标key/信任；本卡不改资源/env或绕验签使分类命中。复用[准确key装载](CLIENT_72_SDK_KEY_LOADER.md)、[原生key桥](CLIENT_72_SDK_KEY_NATIVE_BRIDGE.md)、[密码学合同](CLIENT_72_SDK_CRYPTO_CONTRACT.md)的已验范围，另核实际caller选到的公钥与本服私钥契合、实际装载/信任路径，不从app_key或当前map推定RSA/信任已经匹配。

随后必须取得本服HTTP handler的实际请求/拒绝与响应、客户端准确回调结果，以及[会话适配器](CLIENT_72_SDK_SESSION_ADAPTER.md)的实际session verify→账号/会话交换→dispatch/key使用证据。已有内存/真实Java路由合同直接复用，但自然认证、会话、入场和D-02六项仍另验；不记录凭据或把一次200/同步return当完整本服登录。

## 实际执行、复现与交付

在仓库根目录用当前 `tools/game_path.ps1` 新解析GAME_PATH后：

```powershell
python -B tools/research/d02_route_adoption_20261110_extract.py --client $env:GAME_PATH --output <本卡新的独立目录>
python -B tools/research/d02_route_adoption_20261110_sources.py --client $env:GAME_PATH --window <本轮已提取目录> --output <本卡新的独立目录>
```

输出目录拒绝覆盖。实际invocation/exit/stdout/stderr留本卡私有根；extraction退出0，source绑定退出0。早期外层PowerShell误用in-process resolver后的旧`LASTEXITCODE`，在执行提取前停止；失败记录保留，改用新子PowerShell进程的实际exit0后才运行一次提取。两次JSON查看遇到旧schema list/string假设，以及一次rg直接Windows wildcard失败，只影响查看，未改原件、未补成功。

最终纯离线复核31项通过，记录为私有根 `delivery.private.json`：两工具AST、2481B/504条完整连续原bytes、三caller、10处关键选择opcode、readOnly/local回写、cp_app_id与app_key区别、三旧接受proof完整SHA及首pin改坏的内存负例均核；负例在重开PE前被准确拒绝，原输入不改。该31项是本工位原件/来源复核，不称主线独立第三或新客户端验收。

新增客户端验收0，没有安装/构建/运行代码、JVM/DB/GS或Git提交；只改文档与离线工具，无需重启。主线统一独立审核、共享文档状态、后续实施/实机和本地提交。下一可执行项是pure分类器/源门禁，以及为33107入口实施所需的准确原body/pdata；不是重做已回答SDK页面。

主线2026-11-11复核原body/caller及选择边界，并用当前GAME_PATH重跑source绑定23项，三个literal/26type usage保持；没有新增窗口或CPU/live。纯最终分类器已独立验收，33107入口原body/pdata与自然合法触发继续为准确后继。
