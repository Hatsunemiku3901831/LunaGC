# R2-CONFIGSYNC-SHOWLOGINVIEW：无账号fallback页面分派

## 有界计划

从已验fallback的准确边进入`Win.LoginManager.ShowLoginView`（class5540 / method40422 / RVA`19810dd0`），其无账号caller传`DialogType`（4318）数值0。固定profile五输入、fallback最终19213B报告及原metadata/方法/参数/literal源；先解原enum/default声明，不把0猜成bool或页面名。

本卡最多三个新完整准确pdata窗，只沿首窗实际直接call或已绑定delegate的一条必要UI/native边推进，确认实际页面/插件/action分派入口及配置/transport关系。不按方法名搜索扩展；有完整来源和extent的下一接口在预算内立即推进，三窗耗尽或首次真正未知动态目标即停并给准确解除输入。

仅新增本专题与`local/migration72/d02-resume-20261109/r2-login-view/`私有证据。验收为独立audit、原bytes/extent/typed/enum/literal各自来源、双fresh逐字节一致、坏hash负例、source freeze及主线第三命令。不读取凭据或URL正文/值hash，不CPU、客户端、构建、DB、Git、REA或patch，不改旧源/doc/共享索引，不预设新增hook或自然实验；只有真实调用边成立才复用已有curl成果。

## 结论与边界

已把无账号fallback的**条件账号分支**推进到实际插件UI分派入口：`ShowLoginView(DEFAULT)` → `ShowAccountLoginView(null)` → `ShowAccountLoginPluginUI(null, false)` → 按UI名称取`PluginUIDispatcher.m_dicPluginUIName2ShowTask`的值，调用`ShowPluginUITask`的invoke_impl。第三窗的实际动态调用是EXE RVA`1981a3a5: call [rax+18]`。所选key的注册value、invoke_impl及其业务方法绑定尚未恢复，不能把此入口直接等同于SDK native action。

`DEFAULT=0`不是强制账号页面。`ShowLoginView`还按`local.area`、`mdk.scene`和`CheckSceneType`结果选择账号或手机验证码接口；后者body本卡未读。自然运行的这些值、所选分支和UI handler均未观察。此前用户可见“SDK登录页”与ConfigSync的`no-matching-event`仍不能区分native插件路径和fallback路径，也不能证明配置在安装前发生。

`ShowAccountLoginPluginUI`返回的插件gate即使为true，也可能因没有对应entry或delegate为空而完全未调用UI task。其caller此时仍直接返回，不再创建下列managed视图。因此**gate=true不证明task已调用，更不证明页面可见**。

## 原始声明与准确三窗

原始enum字段/default表和常量存储类型重新解码：`DialogType`（4318，底层int32）的`DEFAULT=0`、`ACCOUNT=1`、`PHONE=2`、`QRCODE=3`、`TAPTAP=4`；`SceneType`（4317，底层int32）的`S_NORMAL=0`、`S_TEMPLE=1`、`S_USER=2`、`S_ACCOUNT=3`。这些是原声明符号，不添加业务含义。

DialogType的DEFAULT字段20533由default row7798、typeIndex70、dataIndex189261给出0；其余四项依次row7799..7802/dataIndex189265..189277。SceneType四项为field20528..20531、row7794..7797、dataIndex189245..189257。default表offset为`916620`十六进制（9528864），915840B；data offset为`2791de4`（41491940）。audit同时核原type reference的存储kind与enum底层int32一致。

| 方法（class5540 / Win.LoginManager） | 原pdata范围，末端不含 | 字节 | 原窗SHA-256 |
| --- | --- | ---: | --- |
| 40422 / ShowLoginView | `19810dd0..19810fd4` | 516 | `c1eddeec9d378c86a36e66c4c463f46026edb2a63850777a1bee0c7d84aef336` |
| 40452 / ShowAccountLoginView | `19811460..198120a8` | 3144 | `7f86ec4c4728c7ed53da75cd8a2630d88d7182894a31bd765ca334934dfa0010` |
| 40451 / ShowAccountLoginPluginUI | `19819810..1981a6a2` | 3730 | `130ce26f5c5fb6ef13655b122ec98d7a364f943c4e72233fd751814379b04f8d` |

总计三个新窗7390B，均完整读取、保存原bytes并逐指令重解，覆盖整个准确pdata extent。第二窗只沿首窗账号tail-jump；第三窗只沿第二窗第一个插件UI直接call。没有读取第四个新body，也未展开手机视图、managed视图或dispatcher注册body。

## 按原指令推进的关系

1. 复用[前卡](CLIENT_72_SDK_CONFIGSYNC_FALLBACK_ENTRY.md)已证的条件边：若`AccountManager.GetCurrentAccount`返回RAX=0，则`Win.LoginManager.Login`在`19802959`tail-jump到40422，EDX=0。本卡的raw参数声明证实它是`DialogType.DEFAULT`。自然返回值仍未知；GetCurrentAccount的返回类型声明没有在这里补猜。
2. 40422读`ConfigModel.local(+30).area(+10)`。与原literal `os`相等时到账号分支`19810ef8`。非os读`ConfigModel.mdk(+40).scene(+58)`：1或2也到账号；否则DialogType=2到手机，=1到账号；其它值（包括DEFAULT=0）调用`CheckSceneType`（`19810eee→1977b210`），返回3到账号，否则到手机。相等判断的内容比较callee由原registration绑定为`System.String.EqualsHelper`，method2908/RVA19aac320。
3. 账号分支`19810efb`清EDX，`19810f03→19811460`；手机分支`19810f0b`清EDX，`19810f13→19814350`。两种callee的raw参数均为`AccountModel model`，故这里传null，不是bool。手机callee为method40424/ShowPhoneMessageView，只核声明与注册地址，未读body。
4. 40452先在`19811480→19819810`调用40451，model沿RDX传入，R8D清零；raw声明为`(AccountModel model, bool showBackButton)`，故是`(null, false)`。AL为true直接到公共返回；false才继续相应managed UI接口：`19811525→199a18c90`（LoginByAccountOversea.Show），或`CheckSceneType==3`时`19811837→1991f0f0`（LoginByAccountSpecial.Show），否则`19811bf7→198bf340`（LoginByAccount.Show）。这些是准确直接接口，不是已观察的可见UI。
5. 40451先按同一area选择UI key，保存到closure+30，再`19819921→PluginUIManager.GetInstance`（39117/RVA199a7520），`19819936→IsUsingPluginUI`（39121/RVA199a7aa0，raw参数`string strUIName`）。返回EAX保存到R15D；AL=false跳到`1981a3a8`返回该值，跳过后续UI分派。
6. gate为true的路径准备action callback字典及自定义JSON字符串，再`1981a2a5→PluginUIDispatcher.GetInstance`（39044/RVA197f5e40）；读该对象+18的`m_dicPluginUIName2ShowTask`。`1981a368→1787cdc0`的返回值被当作字典entry index消费：负值直接返回；否则读entries数组+30、按24B步长取entry value，value为空同样返回。没有用方法名把这个共享泛型callee补成已绑定FindEntry声明，也没有读它的body。
7. value非空时，`1981a397/39b`分别从delegate的+28/+40取`method`与`method_code`，RDX=R13（action callback字典）、R8=RBX（自定义字符串），在`1981a3a5`调用value的`invoke_impl`槽+18。最终`1981a3a8 mov eax,r15d`原样返回gate。这里不把`method_code`猜成m_target：原Delegate布局的m_target在+20，本调用未直接读取它。

`PluginUIDispatcher`为class5353；field25231的原类型是`Dictionary<string,ShowPluginUITask>`，偏移24。`ShowPluginUITask`为class5355，父类为MulticastDelegate（365），再继承Delegate（347）；上述四个delegate字段名/偏移均按该原始布局核实。其Invoke方法39060/RVA199e0420的raw闭合参数是：

```text
Dictionary<string, Action<string, Action<string>>> dicElement2Action
string strCustomData
```

并非`Dictionary<string,string>`。本卡只解声明和调用端寄存器，不实例化/调用这些委托、不序列化运行数据。窗内另有`19819981 call rdi`的泛型缓存间接调用和`1981a313 call [rax+178]`的对象虚调用；未展开这些helper，也未把它们改称SDK动作。完整原指令保留在private原窗中。

## UI key与来源分层

| area条件 | 原cell / literal index | 原UI key | 使用点 |
| --- | --- | --- | --- |
| 非os | `145ad1038` / 3651 | `Login.AccountLogin` | `198198eb` |
| os | `145ace080` / 2124 | `Login.OverseaAccountLogin` | `198198ff` |

以上值只由这两个准确RIP cell解码。literal index和destination slot分别解码，未因数值相同而假定二者恒等。第一次仅查global group时遗漏Oversea cell，保留该尝试；完整复核只扫描既有metadata usage记录中指向这两cell的项，不扫EXE body或解码其它字符串。CN key有usage row768034；Oversea key有11项usage row（3405、6804、7041、15901、16066、24626、31765、31768、31772、31857、31870），都绑定同一index2124/slot2124/cell。多处初始化不等于多个不同key。

声明/类型来源为冻结完整structures和原参数metadata的重新解码；方法地址另核原code-registration指针；enum值来自原default表和存储类型；UI key及`os`来自准确literal usage；实际分支、参数数值和动态call来自三个原PE窗。当前自然area、scene、gate、字典entry/value/target以及UI可见性均无运行观察，不能从静态key反推。

## 配置与transport关系、准确停止点

本卡沿的是前卡已证的Check返回false后fallback边；[账号回调卡](CLIENT_72_SDK_CONFIGSYNC_ACCOUNT_CALLBACK.md)已说明false可能发生于不同原条件，不能只按false断言从未同步。三个新窗没有到既知ConfigSync/LocalConfigSync/SDK Invoke桥地址的直接call/jump；两个UI selector也不属于此前已验24项SDK native action名。这只排除了相应已知直接边，不排除UI task或其它间接callee进一步使用SDK。

新窗没有读出、调用或模拟所选`ShowPluginUITask`的真实注册value。**第一处UI动作目标缺口是field25231中两个准确key对应的delegate生产者/target/MethodInfo及其来源绑定**。因此本卡不能提交`login_login`等具体native action，也不能把既有AccountPlatNative RVA41e6f0/option10002/HoYoNetworkEngine return2f0a47接到此页面。既有[transport成果](CLIENT_72_SDK_TRANSPORT.md)继续保留，尚无复用该消费者的真实页面调用边。

可执行的后继是一个新的有界静态注册卡：本机已有固定输入，先核准确声明接口`PluginUIDispatcher.OnRegisterTask`（class5353 / method39047 / RVA197f6360；原pdata给出的未读范围`197f6360..197f63e9`，137B），raw参数为`(string strUIName, ShowPluginUITask task)`。**当前只证明该接口声明存在，不凭方法名认定它已写入字典或已注册两个key。** 后继需先证明它是否写field25231，再只按实际准确调用/注册来源绑定这两个key的delegate。可接受的解除材料是固定原件中可复现的注册指令/委托记录（key、invoke_impl与业务method绑定、所属type及字段来源）；若只得到一般注册器而没有准确producer，应继续保持动态target未知，不以全Login搜索或SDK curl假设填补。

此下一卡不需要账号凭据。当前也没有证据指定用户“再次打开相同页面”会到此task、重置原once条件或触发ConfigSync；不提出新的hook、计数器或客户端重复启动。必须等注册目标接到准确SDK/transport边后，再决定是否有合适的无凭据自然动作合同。

## 独立复现与冻结

固定五输入SHA保持[observer](CLIENT_72_SDK_CONFIGSYNC_OBSERVER.md)和profile既有五项；audit先核profile本身SHA`a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`并逐文件重算。旧fallback最终报告为19213B / SHA`f024e2ab8e7ee7fbb5c8542ad287002b2ad221350baeceea33ce4b336b4537c2`；本轮live1仍固定1271265B / SHA`b036ba21735b780a342083938b83b6bd558d895834e288c0421b26bd37d51df3`，仅作为既有no-event来源，不重新采样。

原structures为241228521B / SHA`fd82e0a55baa69301e87c422465e3797b0f1279778e6b82d938c28446f1b56c5`；原signatures为78649549B / SHA`28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`。原enum decoder SHA`ee00b4a92ec81a97e3e54a56e8a38f0d0f556e69d7ec5b7203497e307b18cc93`；method/parameter/literal/PE/capstone工具各自source pin均写在private audit并实际重核。

私有`audit_login_view.private.py`为18582B / SHA`a5bcafdd8edbefce9d94f3e8c8da296894e262a6a95ae4239196c063bdf6fc4b`。两个独立fresh解释器的`final3.private.json`与`final4.private.json`均25629B / SHA`067ecd76d85de25a84c7318ff5871e3b52f747cf32962a9d4185bbf1616c8417`，62项断言逐项通过、逐字节相同。另对三个窗分别替换一个错误expected SHA，均在对应`exact-pdata-bytes-<method>`处拒绝，不产生成功报告。旧61项报告为补核Delegate原布局前的epoch，最终仅认上述62项source/report。原bytes、全部原指令、metadata投影、初次不完整literal组尝试、最终报告、测试及source freeze均限private目录。

主线第三命令（本机GAME_PATH须为已固定原件；输出须fresh）：

```powershell
python .\local\migration72\d02-resume-20261109\r2-login-view\audit_login_view.private.py $env:GAME_PATH .\local\migration72\d02-resume-20261109\r2-login-view\main-third.private.json
```

主线需独立核原PE三extent/bytes、报告与source SHA、enum默认表/typed委托和UI key使用点，再决定提交。本卡尚未记录主线第三；不改Project/Harness/旧doc/raw、运行代码、patch或部署状态，不作Git提交。静态结论不代验R2/D-02、真实UI handler、自然ConfigSync或transport。

## 主线第三验收

主线实际新进程运行冻结audit，62项断言退出0；`main-third.private.json`为25,629B/SHA `067ecd76d85de25a84c7318ff5871e3b52f747cf32962a9d4185bbf1616c8417`，与正式双fresh逐字节相同。独立6项审核重新核五固定输入、source/pins、三个原PE extent/7390B原字节、报告/断言和全部动态目标/运行未验边界，记录`main-review.private.json`。已审实际typed调用及原enum/委托字段来源，不将invoke_impl直接称业务method。

本项仅完成准确UI分派静态范围；继续上述已知注册接口，不据本卡安装hook、要求账号或重复页面实验。自然SDK配置的早期覆盖另按已证桥入口审查，两个工作包不互相代验。
