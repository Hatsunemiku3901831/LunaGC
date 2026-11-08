# R2-CONFIGSYNC-FALLBACK-ENTRY：fallback的准确登录入口

## 有界计划

来源为固定profile五输入、[账号回调卡](CLIENT_72_SDK_CONFIGSYNC_ACCOUNT_CALLBACK.md)的最终10523B报告与原metadata/注册/literal解码器，以及已验[SDK transport](CLIENT_72_SDK_TRANSPORT.md)。上卡已证零参fallback tail-jump到`MiHoYo.SDK.Win.LoginManager.Login`（class5540 / method40416 / RVA`19802630`），没有把接口名当作可见页面身份。

本卡只读该一个完整准确pdata窗，核原bytes/extent、直接参数、必要action literal和准确直接/虚调用边，判断它提交的native动作及与既有transport消费者的关系。若仅转交另一个接口，停止在其准确身份与所缺输入，不读第二新函数body、不扫描所有Login、不运行CPU，不猜可见UI。

验收为私有独立audit、固定来源、双fresh逐字节一致、坏SHA负例和来源冻结，供主线第三。仅新增本专题与`local/migration72/d02-resume-20261109/r2-fallback-entry/`；不修改共享索引、旧文档/raw或运行代码，不构建、Git、DB、进程、客户端、安装或REA，不保存账号/URL正文等敏感值。不预设需要新增hook；静态结论不代验自然调用或D-02。

## 结果：这里选择登录接口，没有直接提交native action

准确`Win.LoginManager.Login(40416)`是按配置和当前账号状态选择视图、guest、token或第三方登录接口的一层分派。它没有在本窗口内直接提交已知native action；**不能把这个函数入口、或一个正常SDK页，直接标成HoYoSDK_Invoke / login_login已执行。**

最短且已有来源的无账号分支已确定：

`AccountManager.GetCurrentAccount`（`19802902 → 196e0eb0`）返回RAX为0时，`1980290a`跳到`1980294b`；其后把this放RCX、**EDX清零**，在`19802959`tail-jump到：

**MiHoYo.SDK.Win.LoginManager.ShowLoginView / class5540 / method40422 / RVA`19810dd0`，参数为`DialogType`数值0。**

参数类型不是bool。原metadata解码得到参数名`dialogType`、valuetype `MiHoYo.SDK.DialogType`（4318）；数值0来自原`xor edx,edx`。既有enum observations未覆盖4318，本卡没有为0猜一个枚举符号、页面名或native action。这是本卡的准确停止接口，未读取其函数body。

这条分派不要求先传入成功AccountModel或账号/密码；40416本身唯一声明参数仍是`Action<CallbackModel<AccountModel>>`。但真实页面是否走了该无账号分支、是否有缓存账号或静默会话、以及用户哪个可见按钮对应40416，仍未自然观察，不能把“不要求凭据参数”扩大成已验UI操作。

## 已审分支与有限负结论

固定窗口中的主要接口如下，名称和注册地址来自冻结metadata，边来自本函数原call/tail-jump；被调body均未读：

| 条件或阶段 | 原边与准确接口 | 结论边界 |
| --- | --- | --- |
| 配置中的sub_channel_id等于5 | `19802876 → 19810c50` / SteamLogin（40445） | 原渠道分派，不当作普通无账号页 |
| GetCurrentAccount返回0 | `19802959 → 19810dd0` / ShowLoginView（40422），DialogType=0 | 确定下一接口和参数，页面实现仍未知 |
| 当前返回对象上的已登录/guest分支 | 读`+88/+68`，`19802946 → 19812240` / OnGuestLogin（40484） | 偏移对应已审AccountModel字段，但本卡未恢复GetCurrentAccount的返回类型声明或证明自然对象身份 |
| 其它视图分派 | ShowAccountListView（40456 / `19810fe0`）、ShowAccountLoginView（40452 / `19811460`）、ShowQRCodeLoginView（40435 / `198120b0`） | 具体页面和action不能由方法名代验 |
| 已有账号登录模式分派 | RequestLoginByToken（40475 / `19812370`）及Facebook/Twitter/Google/Apple具名接口 | 存在自动/第三方后继，不证明发出请求、认证或当前路由 |

当前返回对象被按`+88 is_login`、`+68 is_guest`、`+8c login_type`等偏移消费，数值/类型与原AccountModel声明对应；**GetCurrentAccount返回类型声明仍未恢复**，这里只固定消费者读法，不以多个偏移相符当作实际对象已绑定。

`19802bd5`先把login_type减4，在`19802bd8`用无符号范围0..9筛选，随后按相对表跳转。该表恰在本pdata extent尾部`1980358c..198035b4`，共10项40B；目标均在本窗内已核指令边界。报告保留数值4..13及目标RVA，不猜LOGIN_TYPE符号。3972B是完整原extent，含这40B数据；不把表数据当作执行过的指令。

独立复用已有transport导入证据，四个`call [RIP]`都对应同一准确IAT **KERNEL32!GetSystemTimeAsFileTime**；本窗唯一`jmp rax`是上述有界内部switch。其它具名调用/跳转按实际metadata地址登记，没有在这里找到直接到已知ConfigSync、插件get_Instance或HoYoSDK_Invoke桥的边。

同时只在内存把本窗RIP引用的literal与已验SDK原注册表24个action名比较，匹配为0；仅保留必要的`os`分区literal来源，其他literal的值、长度和hash均不记录。**这只是本3972B窗及这24个已证action集合的负结论**，不排除被调接口、字段、其它编码或间接实现最终调用native SDK。

所以“fallback已经直接提交了login_login”不成立；“fallback完全不使用native SDK”同样没有证据。缺口已收敛为ShowLoginView(40422)的准确实现/页面路由，而非继续扫描所有Login、重开ConfigSync计数或要求用户认证。

## 输入、extent与实际验收

固定profile五输入重新命中原SHA；父callback最终10523B / SHA`6afa48db51d2db15a17de12355b74773cd62ccad8c4625b156f04278a6b29177`、SDK登录链第三和SDK transport第三均重新核对。冻结metadata、方法/参数/literal解码器和Capstone来源分别记录，报告不包含账号、URL或其正文摘要。

| 本卡唯一新窗 | 实际extent |
| --- | --- |
| Win.LoginManager.Login / 40416 | `[19802630,198035b4)`，3972B（其中尾部40B相对表） |
| 原bytes SHA-256 | `18bc207fa6cf4320a33c9259bcba2797900c562360a9003ce424a8e6f6f095d3` |

私有`audit_fallback.private.py`重新核原pdata/end、完整原extent与线性解码、原方法指针、闭合callback参数、ShowLoginView的DialogType声明、null/guest/视图分派、switch数据、限定action集合及四个IAT来源，共32项静态断言。

两次fresh Python的`final1.private.json`、`final2.private.json`均为 **19213B / SHA`f024e2ab8e7ee7fbb5c8542ad287002b2ad221350baeceea33ce4b336b4537c2`**，逐字节一致；将唯一窗预期SHA改坏的内存负例实际拒绝。helper SHA **`0eb0df8bd05e79bd23be7dcfd5538d6ff4da33b55c8f5a3909abff4e79d34523`**。主线可用本机GAME_PATH和新的本卡private输出重放helper第三次；不需要CPU、构建或目标进程。

首次audit包含一个被来源门拒绝的错误假设：把EDX=0解释为bool。原metadata重解在`show-view-bool-parameter`处失败，未产生通过报告；现改为DialogType(4318)与原数值0，原窗不变，失败说明保存在private。不得把第一次口头的“false”写回共享结论。

本卡只新增本专题/private证据，未读取第二个新函数body，未修改probe、旧代码或共享索引；没有Git、构建、DB、进程、客户端或安装操作，不能把这些静态断言当作真实页面、SDK调用或R2/D-02验收。

## 最小后继与transport复用边界

如果下一步需要查清fallback究竟选哪个页面/native action，准确源码输入就是 **ShowLoginView / method40422 / RVA19810dd0 / DialogType=0**：固定EXE和metadata在本机已有，只缺该被调body的有界读取及它给出的UI/action直接边。可在新的单窗卡中核其完整pdata、必要DialogType取值来源及直接callee/虚槽；本卡不读第二窗，不默认扩大成整个SDK或所有视图。当前没有足够证据指定用户“关闭再打开页”来复触发配置，也不需要用真实账号提交换取源码阶段信息。

已有自然transport候选仍是[已验SDK入口](CLIENT_72_SDK_TRANSPORT.md)：AccountPlatNative **RVA41e6f0、完整46B原窗、EDX=10002**，HoYoNetworkEngine设置URL的准确caller为`2f0a42`、正常返回`2f0a47`；对应SDK整体SHA仍是固定`adecdf0b…5a6c25`。已有原生ABI/option分派/复制生命周期成果可直接复用，无需重做WinHTTP搜索或猜持久easy handle。

但**本卡尚未建立这个fallback视图与该SDK transport之间的调用边**。不能因为入口成熟，就默认用它采下一次同一页面，或把未来零事件当作MDK未发请求。只有后继ShowLoginView/action边表明该路径实际进入既验SDK，才决定是否使用这个消费者；若原边指向另一个已知transport，则按其实际来源处理，不替换成SDK curl假设。

任何未来自然观察只可在准确入口的原参数生命周期内做有界自有副本及固定类别/unknown分类，不记录URL正文、长度、hash、账号、token或body。自然route、请求发出/接受、完整认证与本服前置仍需分别验收；本卡的明确下一动作是解除这个单一UI接口，不新增hook或重启客户端。

## 主线第三

主线实际第三运行冻结helper，32项断言退出0；`main-third.private.json`为19,213B/SHA `f024e2ab8e7ee7fbb5c8542ad287002b2ad221350baeceea33ce4b336b4537c2`，与正式双fresh逐字节相同。另6项独立审核复核五输入、helper/pins、原PE exception及3972B原bytes/40B表、报告和全部未验状态，记录于`main-review.private.json`。只完成单窗静态分派，不晋级任何自然页面/action/transport或R2父项；继续原40422的有界直接边。
