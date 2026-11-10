# D02-R2 / NATURAL-SERVICE-TRIGGER（2026-11-11）

## 有界中文计划

| 项目 | 合同 |
| --- | --- |
| ID / 目标 | `D02-R2/NATURAL-SERVICE-TRIGGER`；定位无需账号/密码、由原producer/caller支持且位于新观察器安装之后的自然service动作/时序，或交回单一准确缺输入。 |
| 已有 | 两服务共用`3413E0`、RCX vtable/R8D分类及主线第三已验；独立service owned两fresh208实际case已验。入口/body/8B分类不重做。 |
| 准确缺口 | 谁在真实正常客户端动作中调用service virtual`+8`；R8 mode生产；该动作与Astrolabe/loaded-module观察器安装的严格先后。菜单名称、已有map/env或SDK页本身均不足。 |
| 排除 | 不重复随机页面90秒，不据LoadLocalConfig/map/env称service调用；不输入凭据、不官方登录、不伪调用/远程调用/成功stub，不扩大JSON/curl树或绕校验。 |
| 方法 | 复用既有SDK9MB REA/Ghidra、managed签名、原callsite、`31B820`选择链及loader源；先从准确native caller反向定位原action/managed producer，再与现有冷转发/worker安装顺序对照。旧UI注册缺口先读原证据，只有证明必要才继续。 |
| 依赖 | 固定profile/SDK与前卡冻结nativeSources、既有自然阶段事实；service独立owned和当前loader源只读。已固定GitHub/clone/partial直接复用，不开新大搜索或重复REA会话。 |
| 原窗规则 | 若既有证据不足，先将准确caller/AOT/RTTI、唯一extent、必要性和字节上界报告主线；未获新合同不取新窗。新CPU/live均0。 |
| 修改 / 产物 | 仅本页、必要`tools/client72_sdk_natural_service_trigger.py`及同名test；私有`local/migration72/d02-resume-20261110b/b-service-trigger/`。交准确trigger、无凭据/请求分界、安装先后与最小实现或缺输入；源及新证据双fresh/完整SHA。 |
| 验收 | 原类型/参数/caller/分支各有来源；静态可能与保证分开，实际自然service/mode/loginUrl仍unknown；不拿owned或加载完成代自然事件，不跨epoch。 |
| 停止 / 恢复 | 合法时序/最小实现或单一缺输入明确即交回释放max；来源漂移、间接目标未闭或时序无保证即停该边，不为填文档扩树。主线统一loader实现、门禁、新ready现场和Git。 |

## 当前状态

**已收敛可交实施的同步安装规格：在原`login_account_plat`已在途的生产点`197FED3C`安装service hook，恢复原指令后才放行原`197FED58`虚调用。** 无需猜一个页面按钮、等待异步worker或重开UI注册树。新loader尚未实现/安装；实际自然producer、service/mode/loginUrl仍unknown，未发生本卡客户端验收。

## 主线批准的三个条件式原窗

旧UI注册卡已停在key/task producer无源处，本卡不重复该负检查。改沿既有`CheckAccountPlatformAndLogin`的准确`197FED58 virtual+F0`、普通注册implementation及native action向下定位原Invoke桥；虚槽本身不代替动态目标已选。

主线已批准以下条件式新读取，总code上界14336B；每次先重核profile五完整SHA、固定签名源和原AOT pointer/slot，再核唯一exact/containing pdata，之后才读body。全部连续decode且原bytes可拼回，xdata/邻callee/CPU/live/新REA均0。

| 顺序 | 准确owner/method/AOT与已知输入 | 必要性、上界与停止条件 |
| --- | --- | --- |
| 1 | type9797 `AccountPlatformPluginImpl.Invoke` / method70135 / preferred VA`146E36310`；参数funcName、args、callback，承接原插件虚调用候选 | 只读唯一完整body≤2048B，核普通非IFix原直接边；不直接call/jmp到下项即停，不凭slot4跳过。 |
| 2 | type9798 `AccountPlatNative.Invoke` / method70155 / VA`146D81680`；同名三参数 | 仅上一原body直接边确到本AOT目标才读；唯一body≤8192B。核序列化/回调包装后的原native桥直接边，不直接到下项即停，不扩其它callee。 |
| 3 | type9798 `AccountPlatNative.HoYoSDK_Invoke` / method70166 / VA`146D81A00`；strFuncName、strArgs、callback、nCallbackIndex | 仅上一原直接边确到本目标才读；唯一body≤4096B。只定SDK export解析、原调用/正常返回与同步安装候选；未知间接目标/IFix/EH保持，不扩export/curl/UI树。 |

任一AOT/caller不符、pdata歧义/超界、不完整decode立即写诊断停止，保留原件。静态先后只形成后继最小loader规格，没有真实安装不宣称本轮保证命中。

## 新三窗及普通Invoke链

当前checkout的`tools/game_path.ps1`由新pwsh子进程解析退出0；两fresh提取各重新核五profile完整SHA及三个AOT原槽。原pointer cell仍为`1422F98E0+70→1428A54D0`。SDK仍为`adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`，EXE仍为`8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；其余三项完整SHA在私有报告逐项记录。没有新REA会话或目标CPU。

| method / 完整原范围，末端不含 | 字节 / 原body SHA-256 | 原pdata RVA |
| --- | --- | --- |
| 70135 / `6E36310..6E363AB` | 155 / `0f7aa71b63ff733cd37d1711fa502d77baee5cd5c976569cb003dcb40660347e` | `1A6D02A4` |
| 70155 / `6D81680..6D819B7` | 823 / `44dacd31e7c7c89506e1e21031390070162896c1f3489ef4011ac67e04721008` | `1A6CAC94` |
| 70166 / `6D81A00..6D81ACB` | 203 / `76cd5ef82796d0f2a2b4f25b9dd396df1237f5f6fab4b4e983bab46eb9ff3c99` | `1A6CACAC` |

每窗均先唯一exact/containing pdata，再取body，完整连续decode并按原指令bytes拼回；合计1181B，未接近14336B上界。三个unwind RVA分别为`3CCEC0C/3CBF900/3CBF9C8`，只记录引用，xdata未取。

原普通分支的准确顺序是：

1. 旧`CheckAccountPlatformAndLogin`在`197FED3C`从编译期cell`145AD8638`取name；usage row769979 / literal7427准确值为`login_account_plat`。`197FED58`以RCX=R14插件对象、RDX=name、R8=RDI原args、R9=RAX原callback，原stack+20=0，调用`[plugin.class+F0]`。普通注册implementation type9797的Invoke为slot4，但**实际本轮虚目标仍未观察**。
2. 原70135非IFix分支在`6E36356`以E9尾跳`146D81680`，原三个参数保留；这个尾跳没有新的return。IFix分支调用另一原接口，本卡不展开、不假定它也到相同SDK链。
3. 原70155在有callback时生成/保存callback index，并在`6D8176B CALL146D81A00`，原return=`6D81770`；无外层callback的分支也有原内部native callback，index为-1，在`6D817D9`调用同桥，原return=`6D817DE`。两个原call均实际rel32解码到70166，未以同名方法补边。
4. **70135/70155的args是string。** 固定typeReference451887为string，callback457577为`Action<string>`；70166参数为string、string、`HoYoSDKNativeCallback`、int32。本卡没有发现这两个普通body内的args字典/JSON序列化，不将参数名args写成Dictionary。
5. 70166先解析/缓存native export于EXE RVA`57B4D40`，随后原两个字符串转换及callback转换。在`6D81A93..A9C`转发RCX/RDX/R8/R9D，原`6D81A9F CALL[cell]`、return=`6D81AA5`；后续原两释放call和正常RET保持。
6. 新body明确生产的module名为17 UTF16LE units的`AccountPlatNative`、export名为14 ASCII bytes的`HoYoSDK_Invoke`。只读这两个静态名及终止符共36+15=51B，均与原length opcode/完整编码匹配。父transport冻结EAT将该export绑定到SDK RVA`13D0C0`。这些静态名字不能代替当轮loaded-module的path/base/SizeOfImage/disk/code绑定。

SDK部分直接复用[登录链](CLIENT_72_SDK_LOGIN_CHAIN.md)：`13D0C0→133020`原注册动作分派，`login_account_plat→3CC220`；后者在`3CC4CF`调用LoginManager virtual`+60`，准确vtable`6C15E0+60→31B820`。它受原SDK state/callback、组件及配置条件约束，不保证任一export调用都会进入service。

谁调用共同BaseLoginService已经有源：既有`31B820`伪码在最后一次选择后读取当前service（LoginManager+360），经该service virtual`+8`调用；MDK与AccountPlat两槽已验同指`3413E0`。R8 mode在`31B820`中从默认1及配置/已有UserInfo相关分支计算，再作为低32位参数交原service；不是由`login_account_plat`这个名字强制成AccountPlat/mode1。原Config virtual`+2B0`仍可选MDK。该模式生产语义来源为冻结REA `ev_3ae3b0e67aa8733e5a200025564f189e4eb8746fd39c609de7689431c72772b1`，本卡没有另取31B820新body，也未虚构最后间接callsite的RVA。

## 精确trigger与安装先后的解决方式

**唯一推荐trigger是原正常流程已经进入`197FED3C`这一在途动作切点。** 原source决定name，callback不必读name/args。后继正常启动若实际经过该点，可在同一个原调用期间同步装service观察器；只有installer成功并保留hook后才恢复原执行，因而随后这个原`197FED58`动作在安装之后执行。这是可实施的普通控制流顺序，不是本卡已观察的自然执行。

既有普通Login1只收callback，其Check入口不以新账号/密码为参数；[旧账号回调卡](CLIENT_72_SDK_CONFIGSYNC_ACCOUNT_CALLBACK.md)与[自然trigger卡](CLIENT_72_SDK_CONFIGSYNC_NATURAL_TRIGGER.md)已证：非os/已注册的普通native分支在`197FE43B`先同步ConfigSync，后来才到本动作。`G_once`可令LocalConfigSync跳过，因此只在旧ConfigSync-P0装service仍是条件方案。精确在途动作点不依赖再次发生local_config。

现有key loader是首Astrolabe冷转发后启动worker，worker另等loaded SDK；原函数可在worker结束前继续，源码没有到自然登录动作的先后保证。已验八点stage入口则在首冷转发的`INITIALIZE.call_once`内同步准备EXE probe，P0内同步bind已加载SDK并装后继；该**同步两阶段结构**可复用。新service owned本身没有DLL入口/47转发/loaded-module resolver，不能当现成loader安装。

不推荐把通用70166入口当本任务trigger：它承载多种SDK动作，冷入口还可能尚未解析/加载SDK。新三窗用于证明原桥与返回身份，真正安装门使用已有准确name producer；不为过滤所有Invoke而读取任意strings或增加尝试。原初始EXE probe之前的调用覆盖仍未知，首次Astrolabe出现或末尾installed不能回溯证明早期覆盖。

若本轮正常流程未到本点，或实际选择了fallback/UI路径，本卡没有额外按钮动作可供保证触发；只记录`producer-not-observed`，不清once、不关闭重开页碰运气、不远程调用本方法，也不转去猜UI注册。主线准备新loader、门禁和新ready后才可验证该producer假设。

## 交Sol xhigh的最小两阶段loader / owned规格

旧来源与产物不变，新建独立opt-in产物，不整装旧DLL、不组合八点观测/route/key。完整恢复沿既验正常退出方案，主线统一实现、构建与现场。

| 阶段 | 精确实现合同 |
| --- | --- |
| 初始加载 | DllMain只记录自身，原47转发/stock绑定不变。首次正常冷转发完成原库绑定后，同步准备EXE producer hook再放行原callee；不用等SDK的后台worker。核实际EXE path/base/SizeOfImage/disk SHA和准确原窗。 |
| EXE源绑定 | 为本文完整普通桥声明，建议绑定旧Check完整3564B及新三窗1181B，共4745B；沿既有512B chunk binder为11读（7+1+2+1），47表/状态与ilhook自身decode/patch另账。完整代码不代IFix运行条件。 |
| 唯一新增producer hook | EXE RVA`197FED3C`，现有ilhook14B政策完整搬移19B；不包含原`197FED58`CALL。首次任意该点进入先CAS占尝试；后续/重入直接透明通过。 |
| 可选路径的实际目标核对 | 为把当前普通实现与原虚调用关联，首次最多读`QWORD[R14]`和该class的`+F0`两次16B，必须精确等当轮EXE base+`6E36310`；失败/短读/未知立即seal，不解引用args/callback、不重试、不改原调用。完整64位值只用于内存比较，不落DATA。 |
| 同步SDK绑定/安装 | 在该callback内只找已加载的准确SDK并取得自有引用，核path/base/SizeOfImage/disk SHA；若缺失/不符立即失败并透明继续原流程，不等待、LoadLibrary SDK或伪成功。复用已验service原body1071B与两8B槽的3读1087B validator；安装唯一`SDK+3413E0`并先持有core/hook/module至进程寿命，再发布`service-installed-before-pending-action`固定事实。 |
| 放行原动作 | callback恢复全部状态，执行原19B，再从`197FED4F`继续原三MOV和原虚CALL。成功/失败均不改原name、args、callback、stack、返回或校验，不补ret0。 |
| service观察 | 原共用入口核心不变：首次任意service entry先CAS，最多一次8B `[RCX]`与R8D低32分类，其他入口透明。它不带原LoginManager caller身份；不能把任意线程的首事件自动归因于本次producer。无需新增线程归属/过滤机制来填这个未知。 |
| 公开状态 / 恢复 | 只固定阶段/失败类、producer目标是否符合、service安装和共用入口原固定类别；无任意指针/name/args/账号/token/hash或计数。失败也保留已安装hook及引用到进程结束。正常退出→OS目标消失→恢复stock/alias与保护核对；不live卸载或覆盖DLL。 |

19B原bytes为`488b15f5982dec498b1e48c744242000000000`，SHA-256 `244138602bac3d7a6ddc8f36854f44837c9bd6583cd95dee2fca61398ed3adcc`；只有RIP相对name load、原`RBX=[R14]`及原`[RSP+20]=0`，没有CALL/分支/flags修改。唯一resume=`B+197FED4F`；原虚CALL bytes=`ff93f0000000`，return=`B+197FED5E`。RIP搬移必须仍加载原literal cell，不把该值缓存成新参数。

这是mid-function切点：旧Check原八push及SUB98h后令此处S=`入口RSP−D8h`、RBP=S+80h，普通Win64下S为16B对齐；不能套用函数入口RSP mod16=8。`[S+20]`是原call的第五参数位置，原9B写入发生在callback返回之后。

新owned需真实near/far搬移这个19B，独立assembly在原MOV前/后核RIP literal来源、R14对象/class、RBX、RDX、RAX原callback和RDI原args、GPR16/XMM0..15低128/RFLAGS、S/RBP、128B owned参数栈及LastError；原虚call保持一次及原return，完整形状的自有callee返回高64哨兵。另组合已验service 21B核心，证明同步installer实际先安装owned service hook、再由原owned producer call自然进入；不以假的installer返回成功或仅调用callback代替真实安装。

失败夹具包括producer首次未知目标后正确不重试、两次读取各自失败/短读、SDK不存在/path/disk/window错、真实ilhook失败及成功后失败保留hook、自有首service未知/重入/其它线程。必须重新审两个实际emitter及完整绑定/持有/退出恢复；不能借stage旧trampoline或service单点owned称组合已验。目标GC、IFix、SEH/C++/托管异常展开与YMM/ZMM上半部等仍unknown，本卡不加新机制。

## 无凭据与请求的准确分界

到上述managed Login/Check和在途native动作不需要新输入账号密码；它仍可能因已有会话、ticket或保存的UserInfo选择自动分支，因此不承诺“零输入即零网络”。`3413E0`位于service virtual`+120`之前，观察本身不读任何认证值。AccountPlat的`335830`有ticket/autologin分支，SDK state或配置条件也可能使service根本不进入；这些原分支保留。

已证**手工账号密码提交**的边界是AccountPlat模式1的UI submit经`35C190`校验到`34ECA0`，随后两次原Crypto调用、account/password JSON及RequestManager；它不是打开登录动作本身。无输入自然观察不要求用户点击提交，不借旧会话或官方账号去逼事件。若后继涉及已有票据/会话或任何认证请求，必须先按[本服route合同](CLIENT_72_SDK_NATIVE_ACCOUNT_LOCAL_ROUTE_20261111.md)核所选consumer、精确本服route/key和合法本服身份；未知host凭据仍禁止。MDK实际被选时独立证明其consumer/key/response，不套AccountPlat。

## 实际验证、失败保留与交回

两fresh提取的22个文件逐字节相同；各`extraction.private.json`为7859B / SHA `ed5d4cf15c414ceaf80c8996ea1a661d72a35215add63ec4a27f808def938e88`。冻结私有审核器各37项断言，两fresh报告各12741B / SHA `a91a9192e472faaae956f0b57cfda82ec2813675a508cc5c8d3397253ace64dc`，逐字节相同。确定性证明SHA `20f85e40c577bb9a77eef26cbdfc3a6b79f06ab88b34ed4999baa7d78fe5a4e5`。

`python -B -m unittest tools.test_client72_sdk_natural_service_trigger -v`的7项通过：完整原bytes/连续decode/pdata、三条原直接边与tail无伪return、19B不覆盖原CALL、真实参数类型、UTF16/ASCII及native cell、间接/错误边与extent/上界拒绝、静态不晋级自然。工具9112B / SHA `6edd53a57103276d317ef72bd509c4eb1f61e0afd353e45793ea36de2f9d74ce`；test5455B / SHA `870dfa442a92d044b4f01ee9c2866f4b02a4d49e4945d577e182f2544edd7d36`；私有auditor8670B / SHA `cc89494ee3b21d558fbcbc1c1134bf016256dec01cf7024af2d0715582133755`。

初次名称审核把module数据当ASCII18B，实际UTF16前缀触发拒绝；原失败与当时源码`audit_contract.first-red.private.py`保留。主线批准后仅按原17-unit producer将此数据校正为36B UTF16LE含NUL，export仍15B ASCII，共51B，未加新callee/字符串或覆盖旧失败。一次查看未找到预期operation造成StopIteration，仅影响旧REA预览；后续按已有record种类投影，未重开分析。

主线第三使用新的私有目录，先由当前原resolver解析GAME_PATH：

```powershell
$serviceTriggerClient = & tools/game_path.ps1
python -B tools/client72_sdk_natural_service_trigger.py --client $serviceTriggerClient `
  --output local/migration72/d02-resume-20261110b/b-service-trigger/windows-main-third
python -B local/migration72/d02-resume-20261110b/b-service-trigger/audit_contract.private.py `
  $serviceTriggerClient local/migration72/d02-resume-20261110b/b-service-trigger/windows-main-third `
  local/migration72/d02-resume-20261110b/b-service-trigger/contract-main-third.private.json
$env:SDK72_SERVICE_TRIGGER_WINDOWS = 'local/migration72/d02-resume-20261110b/b-service-trigger/windows-main-third'
$env:SDK72_SERVICE_TRIGGER_CONTRACT = 'local/migration72/d02-resume-20261110b/b-service-trigger/contract-main-third.private.json'
python -B -m unittest tools.test_client72_sdk_natural_service_trigger -v
Remove-Item Env:SDK72_SERVICE_TRIGGER_WINDOWS, Env:SDK72_SERVICE_TRIGGER_CONTRACT
```

本卡修改仅本文、新离线工具和test；私有原件全部在`b-service-trigger/`。没有新客户端、API、目标CPU、DLL、构建、JVM/DB、网络、服务、安装或Git提交/分支；无需便携重启。下一可执行项是主线审定上述两阶段loader并交Sol xhigh实现/owned、独立门禁和新ready验证。max到此交回，不延伸UI注册、worker或curl树；实际service/mode/loginUrl与D-02仍未验。

主线第三已实际完成：22个嵌套提取原件与两fresh逐byte相同，37断言/7测试通过，review SHA `c79bdc20fd5ca18d897dcc738847885bc6c7ed03286009eecf7afe4d7c8ea52f`。初审错误把嵌套目录当平面导致count断言失败，仅修复审计枚举，原件不变。该验收只归已有静态合同。后续组合loader首编译及平台安全门禁已按用户指示登记[Daybreak队列](CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md)，不再重试、不安装；上述下一实施动作须待未来Daybreak包解除。自然service/请求/本服认证仍未验。
