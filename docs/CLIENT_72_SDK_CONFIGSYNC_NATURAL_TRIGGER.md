# R2-CONFIGSYNC-NATURAL-TRIGGER：自然触发与安装时序

## 有界静态审计计划

承接已验ConfigSync独立proxy及本轮90秒实机：安装/完整代码绑定成立，事件为`no-matching-event`。该结果不能区分托管生产者未触发、安装前发生或入口caller/name过滤不同；不直接重复启动，不增加hook或计数器。

先重核固定profile五输入、本轮owned/live来源与既有配置选择/dispatcher证据；优先复用`r2-config-select`已有窗口及元数据，定位`ComboManager.LocalConfigSync`（EXE RVA `19806d70`）的直接调用、委托注册及虚调用职责，明确进入SDK登录页是否足以推定该方法应执行。只沿已证方法/槽/注册边读取最多六个新的准确直接函数窗口，不全EXE盲扫，不将无静态xref解释为不存在间接调用。

随后静态核proxy首Astrolabe转发→worker→EXE验证→等待SDK→安装顺序，与生产者准确触发边对照。交付各输入/window extent、代码来源、静态调用关系、自然未知和能够区别上述三种解释的最小后继合同。新的实验或实现须由主线另审，不在本卡安装、构建、启动、读取目标或复用旧ready。

仅新增本专题与忽略目录`local/migration72/d02-resume-20261109/r2-config-trigger/`；不改共享Project/Harness、冻结代码、配置、资源或协议，不调用Git、构建、DB、端口、客户端或REA共享会话。首个无源调用/必要输入不足或六窗预算耗尽时记录准确解除条件。

## 已确定的触发链及页面边界

固定五输入与本轮owned/live原件均重新命中既有SHA。静态调用链为：

1. `MiHoYo.SDK.Win.ComboManager.Login`四参重载（method40102，RVA `197fc950`）在`197fcec6`直接调用`CheckAccountPlatformAndLogin`（40106，`197fe310`）。本次明确看到的直接调用位于检查`ConfigModel.Instance.client.sub_channel_id == 5`后的路径；另一分支先调用`HoYoChannel.CheckChannel(7)`并可走其他登录路径，不能把这一条直接边扩成所有登录入口的必经边。
2. `CheckAccountPlatformAndLogin`先检查配置根/manager存在，读取`ConfigModel.Instance.local.area`并与准确literal **os**比较；相等则返回false，非os还须`AccountPlatformPlugin.IsPluginRegistered`对应的byte为真，才在`197fe43b`直接调用`ComboManager.ConfigSyncWithNative`（40116，`198035c0`）。这些条件是原代码，不是以国服资源area代替自然area已观察。
3. 复用原窗，`ConfigSyncWithNative`先检查本卡记为`G_once`的全局byte；为0才在`198035f1`直接调用`LocalConfigSync`（40117，`19806d70`），随后调用`ComboConfigSync`，返回后在`1980360f`置该byte为1；后续仍可调用Box配置同步。没有给`G_once`套用未证明的字段名，也没有证明整个EXE只有这一处writer。
4. `LocalConfigSync`正常路径创建准确`local_config`后，经`AccountPlatformPlugin.get_Instance`，在`19807970`取对象class虚槽`+f8`，`19807989`tail-jump。固定metadata中base plugin与implementation的ConfigSync slot均为5，Invoke slot均为4；与已证class表`+f8/+f0`一致。准确implementation普通路径复用旧窗`6e363ee → 6d80910`，最终在`6d809c9`调用解析的native export，正常返回地址仍为`6d809cf`。
5. 同一`CheckAccountPlatformAndLogin`的native插件分支在`197fed58`调用对象虚槽`+f0`，其name经准确literal usage解为 **login_account_plat**。它位于上述配置同步之后；但sub_channel_id为5时又可转Steam登录，BiliBili/WeGame检查也可返回false，不能将整个函数等同于“打开一个SDK页”。后两次`RegisterMessage`的name分别是`account_list_save_account`、`account_list_delete_account`，它们不是LocalConfigSync回调注册。

因此，**若自然执行的是上述已注册、非os、G_once为0且进入login_account_plat虚调用的普通路径，LocalConfigSync在它之前被直接调用；用户只看到“SDK登录页正常”并不能证明走了该路径。** 当前记录没有页面action名、当时area/注册位/G_once或托管调用事实，不能据页面或已加载SDK倒推生产者应发生，更不能据no-event判断SDK未通过此接口。

另有两项有界负结论：新审`ComboManager.Init`窗口内没有直接call/jmp到40116/40117；固定metadata从已知usage记录起点到global group结束，也没有这两个方法或40106的kind3 MethodInfo usage。三者均为非virtual方法；40116/40117的metadata flags分别`86/83`、slot均FFFF，**不能把它们写成C# static方法**。IL2CPP全方法指针表中有注册地址只证明代码存在，不等于事件/委托已注册。本次没有全程序xref或任意间接调用覆盖，以上负结论不排除其他直接调用、wrapper/反射或未知回调路径。

## 注册确切来源

沿既有LoadFile注册来源扩展`DelegateRegister.Register`（method70395，RVA `6e36690`），其普通分支从已证静态storage取implementation singleton，不存在时以准确TypeInfo分配`MiHoYo.SDK.AccountPlatformPluginImpl`（class9797），调用base构造后保存。分配TypeInfo cell由独立metadata usage row700544绑定，base plugin TypeInfo由row699734绑定，不以方法名称或编码offset掩码猜类型。

`Register`在`6e37729`把该插件指针非空结果写到EXE `B+586dbb0`，并在`6e37737`把同一指针存入static storage的`+7d30`。新审`AccountPlatformPlugin.IsPluginRegistered`（32203，`196e55e0`）的两条返回路径都读同一byte，且该byte恰是`CheckAccountPlatformAndLogin`在`197fe3f0`检查的gate。`get_Instance`读取同一storage槽；若槽为空，它可自行分配base plugin，故单纯“getter非空”也不能当作准确implementation已注册。

`Register`另有条件分支，当前没有自然执行这些分支的数据；本卡没有闭合谁在本次进程调用Register、何时完成及是否后续替换singleton。这里只固定普通分支的原始写入关系，不宣称自然注册或虚调用目标已经观测。

`G_once`精确地址为EXE `B+586ba00`；其读/写均由原40116窗口直接RIP-relative计算。它目前只有“此函数跳过/执行Local+Combo”的代码角色，没有批准跨线程读取该位就能推断之前SDK writer或原生export已经执行。

## 六个新窗与复核

全部新代码来自准确注册地址和PE exception directory的完整begin/end，结束地址互斥；合计6窗19,179B，达到本卡预算后没有继续读第七个新代码窗。只做静态反汇编与metadata读取，没有执行这些客户端函数。

| 新窗 | RVA范围 | bytes |
| --- | --- | ---: |
| ComboManager.Init | `197fb660..197fc7ab` | 4427 |
| CheckAccountPlatformAndLogin | `197fe310..197ff0fc` | 3564 |
| Login四参 | `197fc950..197fdd35` | 5093 |
| AccountPlatformPlugin.get_Instance | `196e5380..196e54d4` | 340 |
| DelegateRegister.Register | `6e36690..6e37cda` | 5706 |
| IsPluginRegistered | `196e55e0..196e5611` | 49 |

私有`audit_trigger.private.py`重新核五输入、冻结reader/observer/loader/table源、六个pdata extent及完整解码、五个旧同步窗、三条直接call、两个byte关系、插件根读写、选中字段/flags/virtual slot、精确literal/TypeInfo usage和原live结果，共47项静态断言。`final1.private.json`、`final2.private.json`均为8744B/SHA `f99de6aa2b80367507cc4e17d7bf6b1fdfec44851c436a3c52f96d4e31ea7eb9`，逐字节一致；改坏首窗预期SHA的内存负例实际拒绝。没有把这些断言当作原函数/ABI或自然客户端重跑。

复现只运行本卡忽略目录helper，以本机已核GAME_PATH及新的本卡private输出为参数。helper SHA `ad5851fc8f96458be9b0627862a25637cd55962975506165fbaeeae6762b3fc8`；完整源/window pin与具体extent在原报告。最初helper根目录层数与一个指令起点抄录错误均在生成final前拒绝并修正；原字节没有变化。大型minified metadata应按JSON结构投影，按行截取不能限制其输出。

## 本轮装载记录实际支持什么

只解析既有live原件，没有第二轮目标读取。proxy与stock alias最早在elapsed **2.015秒**的样本中出现，前一条为0.015秒；SDK最早在 **22.031秒**出现，前一条20.031秒未见。原件只有90秒末尾一次state/code采集，不能从这些模块样本推导首次转发、worker真正运行、EXE验证结束或hook生效的时间。

源码顺序仍是首次Astrolabe转发→bind/load准确orig→启动worker→验证EXE和引用→等待并验证SDK→安装唯一SDK hook。alias已出现与SDK约二十秒后才被采到，使“450MB EXE hash必定让hook漏掉配置”没有证据；它也不能证明worker在SDK出现前已完成准备。90秒installed状态不回溯证明早期覆盖，保留这个具体缺口。

## 可执行后继及观察合同

**下一项优先离线核两个已知准确入口，暂不重复启动或加hook：** Win.ComboManager.`OnMDKOrAuthTicketLogin`（method40103，RVA`197ff180`）与`Login`一参重载（40105，`19801e80`）。它们是现有metadata已经给出的有限后继，原文件在本机可得，没有缺二进制或方法映射；本卡六窗预算已满，未读取其代码。新的有界卡应只问：普通非sub_channel_id=5的可见登录页是否先于这些账号回调，以及何处调用40106/40116。先检查这两个完整pdata窗和有来源的直接call/委托MethodInfo，最多再沿一个明确callback窗，不能按名称认定必须登录后才同步。若显示页面位于回调之前，旧页阶段未触发成为可检验的路径解释；若页必经同步，才进入观察器差异诊断。

诊断时的最小第一层实现可继续只有原SDK export hook：在既有过滤之前增加**两个只置true的事实位**`any_entry_seen`、`expected_caller_seen`，不加计数器、指针、name正文或payload记录；只有原准确caller仍可逐字节核name，payload读取条件和65536B上限保持。既有event足以表示准确name阶段，不必再加第三个计数。owned须补未知caller/错误name命中这两个事实位但零payload读、LastError/完整ABI/代码恢复和新DATA布局/EAT审计。它能区分安装后“接口没有到达”与“caller/name过滤未命中”，**单靠它仍不能证明安装前已经发生**。

要区分安装前生产与安装后未触发，前置条件是先有一个被原调用链证明的、可由用户执行且不需要凭据的触发动作，能安排在已核installed后：同一新进程先核稳定installed，再记录已注册位/G_once的固定scalar状态，满足该动作的原分支前提后执行一次。成功/失败均按该区间解释；若G_once已为1或动作/入口没有来源，则停止，不清flag、不强制调用目标、不以“重新打开页”猜触发。仅以末尾G_once为1也不能反推过去发生过SDK事件。

若自然调用只能在启动早期发生、确实没有上述可控动作，才需要另审一个**安装在等待SDK之前的准确托管生产者入口probe**（优先`LocalConfigSync`入口，而非未验的中途call-site ABI），与原SDK hook的两个事实位共同记录“生产者入口已到达时SDK观察器尚未准备/正在安装/已经生效”。采用阶段枚举和一次事实位，不记录时间序列、次数或参数。该补充点只能证明生产者进入相对安装阶段，不能把它冒充原生export已执行；早于该probe安装的区间、JSON构造/插件分派未到达以及安装转换区间必须显式保持未知。需另完成exact entry/ABI/双hook顺序/失败与恢复审查及owned夹具，本卡没有实现或授权其真实安装。

这样后继有明确停止出口：先用有限源码确认页面属于哪条路径；再用同一hook的事实位判过滤；只有早期不可控生产是已证必要性时，才提出额外生产者点。任一自然观察都另需主线来源/产物/恢复审核和新的user-ready；当前卡只增加文档/private证据，没有Git、构建、部署或客户端操作，D-02未由此推进为已验。

## 主线第三审核

主线已实际运行冻结helper，`main-third.private.json`为8744B、同SHA，与两fresh逐字节一致；47项静态断言全部通过。另从冻结117201003B metadata缓存核四个gate字段声明（local/client引用、area、sub_channel_id）与原始提取一致，见 `main-independent-review.private.json`。没有第二轮目标内存读取或客户端启动，没有把当前页面改记为目标路径已触发。

本次限定条件/注册/来源审计完成，自然配置父项仍未闭。后继[普通页面与账号回调](CLIENT_72_SDK_CONFIGSYNC_ACCOUNT_CALLBACK.md)只审上述两个准确入口及至多一个有源callback，完成后再决定具体实现；不先安装第二hook或重启同一实验。
