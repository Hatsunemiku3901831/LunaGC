# R2-CONFIGSYNC-ACCOUNT-CALLBACK：账号回调与配置同步阶段

## 有界静态计划

承接已验[自然触发条件](CLIENT_72_SDK_CONFIGSYNC_NATURAL_TRIGGER.md)和本轮准确hook的`no-matching-event`，只解决普通非sub_channel_id=5路径的可见SDK页面、账号回调与ConfigSync的先后。复用六个旧窗及area/注册/once/虚槽来源，不重新扩展登录或注册扫描，不改hook或增加计数。

先重核固定profile五输入、旧源码、冻结触发报告与本轮live原件；准确读取method40103 `OnMDKOrAuthTicketLogin`（RVA`197ff180`）和method40105一参`Login`（`19801e80`）的完整pdata窗。若其中出现具体直接call或委托MethodInfo边，最多再读该边的一个准确callback，总计不超过三个新窗。只按原指令、参数/字段类型和必要literal核实阶段，不因方法名称推断登录成功或必须认证，不运行CPU片段。

交付必须回答：是否已有可由用户无凭据触发的有来源动作与原分支；若没有，则准确列出仍缺的页面/action/账号成功来源以及本服route前置，给最小离线或transport消费者后继，不直接重复启动。本卡仅新增本专题与`local/migration72/d02-resume-20261109/r2-config-callback/`私有证据，不修改共享索引或旧代码，不调用Git、构建、DB、端口、客户端、安装或REA共享会话；不以静态审计代验R2/D-02。

## 结论：配置分支在认证之前，页面仍有两条来源

**不能把无ConfigSync事件归因于“用户还没输入凭据”。** 一参`Win.ComboManager.Login`只接收`Action<string> callback`；普通非sub_channel_id=5、未被BiliBili/WeGame分支接管的路径，在`198023fb`先调用已验`CheckAccountPlatformAndLogin`，参数为一个零参失败委托及空extraArgs，没有AccountModel、账号或密码输入。

| 原分支 | 静态顺序 | 当前页面不能替代的事实 |
| --- | --- | --- |
| native插件适用 | Login1→Check；若非os、已注册等原条件满足，进入ConfigSyncWithNative；G_once=0时Local+Combo同步；随后原插件Invoke `login_account_plat` | 同步发生在这次登录动作/返回回调之前，输入凭据不是配置同步前置；自然area/注册/G_once及实际虚目标未采 |
| Check=false的失败fallback | `19802410`取准确零参委托，`19802427`tail-jump；委托可进入Win.LoginManager.Login或有条件的CXHandheld登录接口 | false可能源于原入口条件，也可能来自其他原渠道分支；不能只按false断言从未同步，更不能把两类SDK页视为同一入口 |
| MDK/票据账号结果处理 | OnMDKOrAuthTicketLogin先判断CallbackModel.ret；错误直接转失败，成功AccountModel的具体分支准备uid/token等后到RequestComboLogin | 正常combo请求需要成功账号结果；成功可能来自已有会话/票据，不等于必须手工输入密码；本卡没有观察真实结果 |

因此，当前正常页可能属于native插件流程，也可能属于fallback登录流程。native适用路径上的配置不应等到账号回调才做；fallback窗口本身没有ConfigSync调用。**仅凭当前“SDK登录页”与no-matching-event，仍不能确定本次走了哪一条，也不能证明安装前已发生。** 本卡没有在名字之间补一条不存在证据的页面→回调边。

## 一参Login的准确fallback

`Login1`（method40105，RVA`19801e80`）在`198022bd`比较`ConfigModel.Instance.client.sub_channel_id`与5；非5转`198023b9`。在那里创建失败delegate，`198023c8`装入的MethodInfo cell为`B+5975418`，由metadata usage row13062独立绑定method40184、`<>c__DisplayClass21_0.<Login>b__15`、RVA`19a58c10`。它不是根据匿名方法名称猜出的目标。

`198023fb`调用Check后，`19802400`测试返回AL：true直接返回；false才读取delegate的原function/MethodInfo/target，在`19802427`tail-jump。该委托声明参数数目为0，不以已有账号对象作为它的调用参数。

唯一第三窗就是这个准确fallback（584B）。它先取得/创建本closure的`<>9__16`回调，普通分支在`19a58df7`tail-jump到**MiHoYo.SDK.Win.LoginManager.Login**（class5540 / method40416 / RVA`19802630`）。目标参数已直接从原metadata解为`Action<CallbackModel<AccountModel>>`；`<>9__16`对应method40185 / RVA`19a58e60`，其参数同样是`CallbackModel<AccountModel>`。本卡只固定注册与类型，不读取或推断40185的body。

另有`HoYoChannel.CheckChannel(7)`和原虚调用检查为真时的分支：`19a58d3f`tail-jump到**MiHoYo.SDK.PC.OS.CXHandheld.LoginManagerBase.AsynHandheldLogin**（method36086 / `1972f610`），不是AuthenticationManager。其`<>9__17`回调由准确usage绑定method40186 / `19a58e90`，原参数为`int32 ret, string msg, AccountModel account`；回调body未读，不能把它与普通Win登录流程合并。

第三窗没有直接call/jmp到Check、ConfigSyncWithNative或LocalConfigSync。后续`Win.LoginManager.Login(40416)`的body是当前精确停止接口；它是否直接开启所见页面、选择哪个native action、是否自动采用已有账号及如何发回40185，都没有在本卡内证明。原文件及方法映射均已存在，不是缺安装包或不知道地址；本卡不越过三窗预算继续第四窗。

## 账号回调实际检查与请求阶段

`OnMDKOrAuthTicketLogin`（40103，`197ff180`）参数从固定metadata及原注册表重解为`CallbackModel<AccountModel> response`、`Action<string> callback`，与旧signature投影相同。原函数在`197ff215`比较response `+10`的int与0：非0的路径复制错误信息、清成功data，在`197ff249`转`OnComboLoginCallback`；为0才在`197ff24e`读取response `+20` AccountModel并检查非空，继续按`AccountModel.login_type`分派。

CallbackModel泛型字段的旧metadata offset标记是runtime，本卡没有对其编码offset掩码求布局。这里的`+10/+20`来自具有准确闭合参数类型的原消费指令；字段声明分别是唯一int32 `ret`、string `msg`、泛型data，不能把它们升格成任意CallbackModel实例的通用重建许可。

已审普通账号分支从AccountModel `+10`取uid，`197ff817`选择`+58` token；两字段在原AccountModel声明与实例布局中分别为string。之后用准确literal `uid`、`token`等组装原请求数据，在`197ffe91`调用`RequestComboLogin`（40108 / `19801220`）。其他login_type/payload、guest或票据分支分别存在，不把这个正常字段片段推广成所有账户模式。

这三个新窗均没有对40116/40117的直接call/jmp，因而本卡未找到“账号成功后再调用LocalConfigSync”的直接边。错误、取消或空账号结果不等于正常combo提交，不能靠制造ret0/空AccountModel让它继续；也不能由callback名称断言一定需要密码输入。自然账号认证、票据、回调、combo发送及本服接受继续未验。

## 来源、extent与实际验证

五profile输入、冻结trigger报告和live原件重新命中既有SHA；继承原六窗、area/注册/G_once/虚槽合同，只读它们的已审来源，没有重新扩展扫描。新代码窗均由准确注册地址及原PE exception directory限定完整begin/end，end互斥：

| 窗口 | RVA范围 | bytes / SHA-256 |
| --- | --- | --- |
| OnMDKOrAuthTicketLogin | `197ff180..19800520` | 5024 / `b775785d7cd5a739fdf4d90a4c6bb2ec1adf75cf3bf3d3b19e54d8d627e9c203` |
| Login1 | `19801e80..19802630` | 1968 / `1df0205dd4e84c05bcd654705cf54577471351e7995a514d1bc5c2c94421faf1` |
| 准确fallback40184 | `19a58c10..19a58e58` | 584 / `dbe29ef4644ba1f898268c535c82be876c5ba0c6bf07dbc6eab4849d0e24f645` |

私有helper `audit_callback.private.py`固定提取器、literal/参数解码器、Capstone、原observer源及父proof；重核原bytes/pdata/完整解码、所用类型投影、原method pointer、直接原metadata参数及其闭合TypeResolver结果、具体delegate usage、普通/失败分支、账号字段和有限负结论，共61项静态断言。没有跑CPU、SDK、客户端或网络。

`final3.private.json`与`final4.private.json`为两次fresh Python进程结果，均 **10523B / SHA `6afa48db51d2db15a17de12355b74773cd62ccad8c4625b156f04278a6b29177`**，逐字节一致；改坏首窗预期hash的内存负例实际拒绝。helper SHA **`bbc3d7452393403d2175fdb8c09fd06051f20235a324568ef46527e503623cba`**。早期final1/2为53项epoch，末尾增加普通/handheld callback的原参数解码后才形成上述61项最终epoch，原件保留。

复现仅执行本卡private helper，以本机已核GAME_PATH与新的本卡输出文件为参数。完整断言、typed source及code extent在报告，不能把静态双跑记成原函数返回、实际页面或D-02验收。

## 可执行后继：先辨认原分支，不用认证换取配置事件

当前已经证明：进入普通native适用的Login1配置分支不需要先取得账号成功；因此**不要求用户登录官方账号来触发ConfigSync**。但还没有把可见按钮/关闭重开动作绑定到Login1或40416，不能承诺“关闭再打开页”一定重新执行配置，也不清G_once或远程调用这些函数。

最小源码解除项是已有精确停止接口 **Win.LoginManager.Login / method40416 / RVA19802630**：后继如需彻底确定fallback页面action，只读该一个完整pdata窗及已有action/literal来源，不扫描其他Login或完整迁移SDK。本卡不继续第四窗。这个输入用于确定页面的路由来源，不需要任何自然账号或密码材料。

若主线选择下一次自然辨认实验，问题应改为“同一正常页下是否到过SDK ConfigSync入口、是否被caller过滤，以及当前插件注册/once状态”，不是重复旧采集器等待相同事件。具体最小合同如下：

- 仍只有原SDK `13d5b0` hook；在过滤前增加两个只置true的事实位`any_entry_seen`、`expected_caller_seen`，不计次数，不读取未知caller的name或payload。准确caller/name后的原有复制、分类和65536B上限保持。新的DATA schema、实际EAT、owned未知caller/错误name零payload读、完整ABI/LastError与恢复另行审核并实际构建。
- 只读采集器可在原已核EXE身份下额外读取两个固定scalar byte：`B+586dbb0`注册位、`B+586ba00`一次性位。增加恰好两读两字节，不追托管配置堆、用户对象或指针；仅投影固定bool/非法值状态。它们来自不同读取时刻，不称原子快照，更不把末尾值反推启动时值或证明过去发生过事件。
- 新ready后一次自然启动至同一SDK页面，不输入凭据、不提交账号，也不新增未经源码绑定的UI操作。新事实位用于区分安装后无入口、未知caller、准确caller但非目标name与目标分类；两个scalar用于判断本次末尾注册/once条件。`no entry`、注册为真或once为1均不能单独证明安装前已发生，仍按明确区间/事实报告。
- 若该轮指向native不适用或仍没有入口，停止这个配置probe分支，不靠继续重启、手设flag或认证提交逼事件。只有新的真实调用必要性证明早期生产者观察不可替代，才回到父卡的独立前置probe审查。

需要进一步自然transport时，已有准确消费者直接可复用：[SDK transport](CLIENT_72_SDK_TRANSPORT.md)的 **AccountPlatNative RVA`41e6f0`、46B原窗、EDX=10002**，其中HoYoNetworkEngine设置URL的已证callsite为`2f0a42`、正常返回`2f0a47`。后继只应在经来源/ABI/实际装载审查的入口生命周期内，将有界自有URL副本分类为既证固定host/path类别或unknown，不记录URL、长度、hash、请求正文或凭据，不追已释放task/easy handle。命中仍只证明URL被交给该消费者，不证明perform、请求成功或登录；自然时序、caller与URL均需实测，当前卡没有采样或实现该hook。

若目标是继续正常combo请求，则所缺是原链上的成功AccountModel/票据与本服自然route前置，不能以普通页面、空账号或owned HTTP成功代替。本服route/信任/必要补丁与R4/R5未闭时，不要求用户做可能走外部路由的认证；先按上述准确接口或消费者解决分支/选址来源。当前任务的可交付是这一有界阶段合同与明确后继，R2、R4/R5及D-02均未由本卡关闭。

## 主线第三验收

主线已实际新进程运行本卡冻结helper，61项静态断言退出0；`main-third.private.json`为10,523B/SHA `6afa48db51d2db15a17de12355b74773cd62ccad8c4625b156f04278a6b29177`，与final3/final4逐字节相同。另独立7项审核重新核五固定输入、helper与6个pins、三完整PE exception extent/原bytes、报告相等/断言及所有未验边界，见`main-review.private.json`。

本项只关闭三窗的静态阶段合同，没有自然callback/CPU、账号提交或新增hook。下一任务先核已知40416的单个准确body，确认fallback路由；其它已验SDK修复/桥接/透明加载成果直接复用，不重复旧未命中实验。
