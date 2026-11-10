# D02-R2 / NATIVE-ACCOUNT-LOCAL-ROUTE（2026-11-11）

## 重大问题有界计划

| 项目 | 本卡合同 |
| --- | --- |
| ID | `D02-R2/NATIVE-ACCOUNT-LOCAL-ROUTE`；承接已验33107入口及纯最终URL分类器，不重做二者。 |
| 目标 | 收敛唯一有证据的本服原生账号route/service接线及合法本地账号触发前置，交Sol xhigh必要实现/观察规格或准确首缺输入。 |
| 已有 | 固定SDK `adecdf0b…a6c25`；AccountPlat `loginUrl` producer/reader、条件service/mode链、`41E6F0` EDX10002 curl兼容consumer、两caller `34EEB7/34EF88` 窄key核心及实际安装/恢复；有限自然map/env仅按各epoch原结论复用。 |
| 准确缺口 | 当轮原生服务与模式的采用身份、批准固定host/path/method类别的自然账号URL、同次本服key供给与原SDK请求/响应后继。Java注册MDK不能补客户端MDK consumer/key/response。 |
| 排除 | 不重复SDK页面90秒；不要求官方登录、不向未知外部host送凭据；不泛域名重定向、不改版本/成功码/原校验；不跨epoch拼SDK成功；不以包配置、stored map、managed env/base当账号request。 |
| 方法 | 先复用现有SDK9MB REA/Ghidra导出、冻结nativeSources、自然11模块及准确caller，逐层记静态/离线CPU/自然事实；只读已存body。若缺必要窗，先给producer/caller/AOT或RTTI/唯一extent和上界；未经主线新合同不执行CPU/live。 |
| 依赖 | 先读AGENTS/README/Project/Harness/迁移/部署及账号专题；任务指定POSTREQUEST/TRANSPORT/LOGIN_CHAIN/ROUTE_LOADER/KEY相关专题、最新map/env证据。固定既有patch和外部来源直接复用；若发现新的重大外部复用目标，先交中文GitHub README-first/branches检索计划给主线Terra。 |
| 修改 | 仅本页、必要的`tools/client72_sdk_native_account_local_route.py`和同名test；私有输出仅`local/migration72/d02-resume-20261110b/b-native-account-route/`。共享索引、生产、patch、Proto、helper、旧pin均只读；无Git提交/分支、构建、服务、客户端或安装操作。 |
| 产物 | 固定源与已有证据索引；最小route/service/key接线合同，准确ABI/输入、first-attempt、读取账、错误与透明原返回、owned/退出恢复验收；无法闭合处明确单一首缺与解除条件。 |
| 验收 | 每条host/path/method、服务/模式、key和response分别带原producer/consumer来源；hook可行性只依唯一原body/完整指令，不以旧owned代新点；自然证据必须同epoch，认证成功与错误/保存独立。 |
| 停止/恢复 | 固定身份漂移、缺准确caller/producer、未知分支或extent歧义即停新提取；复杂合同明确或精确缺输入可交回即结束，不为填文档扩树/盲扫、不升级Astra。后继修改/owned/现场由主线审定。 |

## 当前状态

**本服账号接线尚不能批准：准确首缺是当轮实际原生service/mode，不能先假定AccountPlat。** 本卡取得两服务共用的更早入口及唯一完整body，足以交出一次无凭据的最小选择观察规格；它不把六个静态host全部批准为重定向白名单。没有新增自然账号/route/key/SDK成功验收。

## 唯一新增静态窗的必要性与上界

现有 `31B820` 完整REA伪码三次根据Config virtual`+2B0`重选service，`3433E0`位于AccountPlat ticket/autologin分支之后；仅后者未到达不能判定MDK。因此先核已证RTTI的MDK `6C2098+8`与AccountPlat `6C2CA8+8`两QWORD是否同指既有REA入口 `3413E0`（BaseLoginService）。若不相同立即停止，不追MDK其它函数。

两槽同指后，才以原exception目录的唯一exact/containing记录定完整`3413E0`范围；code上界8192B、只一个body、12B pdata，xdata/邻callee/新CPU/live均0。该入口原RCX是实际service、R8低32位是mode，位于该函数内ticket/autologin派发之前，是一次无凭据选择观察的候选切点。只读取已有login/crypto/transport冻结窗口核URL producer→POST consumer，不新增这些callee。地址均为固定SDK RVA，不能照抄为运行VA。

## 已验来源、自然事实与不能拼接的边界

本卡重新核profile五输入及父transport的自然11模块磁盘身份，13个去重文件全部匹配。SDK完整SHA-256仍为`adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`。固定源/REA/既有报告15项记录在本卡报告`nativeSources`，逐项重核；没有新建REA/Ghidra会话，没有执行Unicorn或目标CPU。

| 来源 | 已成立的范围 | 本卡采用方式 |
| --- | --- | --- |
| [登录链](CLIENT_72_SDK_LOGIN_CHAIN.md) | `31B820`按Config virtual`+2B0`选择`+368` MDK或`+370` AccountPlat，并可再次选择；AccountPlat模式1的条件链及RTTI/槽位 | 复用冻结source与`final-v2-first.json`、`rea-final.json`，不把一次早期service字段快照当最终采用 |
| [原生Crypto/route](CLIENT_72_SDK_CRYPTO_CONTRACT.md) | `107FE0`六表→`passport_env`→AccountPlatformUrl `+870`；准确`loginUrl` getter及两key caller | 六host只是固定候选；`passport_env`与SDK普通env/managed env分开 |
| [transport](CLIENT_72_SDK_TRANSPORT.md) | `41E6F0` Win64 varargs、10002复制存储、POST builder caller与真实setopt错误 | 不把setopt 0当URI验证、perform发送、TLS/HTTP或认证通过 |
| [key桥接](CLIENT_72_SDK_KEY_NATIVE_BRIDGE.md)、[loader](CLIENT_72_SDK_KEY_LOADER.md)、[实际key观察](CLIENT_72_KEY_LOADER_OBSERVATION.md) | 准确两caller窄核心→原PEM/RSA/Base64与本服私钥数学互通；真实installed代码绑定/恢复 | 不重跑；实际轮`naturalCallerObserved=false`，没有自然key供给事实 |
| [八点自然配置](CLIENT_72_SDK_CONFIG_STAGE_OBSERVER.md)、[锁内map](CLIENT_72_SDK_CONFIG_ROUTE_OBSERVER.md) | 前者同调用env store0/正常native返回；后者同锁内`comboLogin=cn-route-env-0` | 都不是AccountPlatformUrl `loginUrl`或原生账号请求，不能借给service/mode |
| [stock v2](CLIENT_72_D02_AB_STOCK_V2_20261110.md) | 另一epoch managed env0与comboLogin完整base0候选一致 | 不等同SetEnv/GetURL/40108/33107执行，也不与上轮native map拼接 |
| [响应](CLIENT_72_SDK_RESPONSE_CONTRACT.md)、[session](CLIENT_72_SDK_SESSION_ADAPTER.md)、[combo](CLIENT_72_SDK_COMBO_HTTP.md) | AccountPlat单token消费者、session真实Java HTTP→原生及combo真实保存往返的限定合同 | 保留原认证/错误/确认保存；没有自然完整SDK阶段链成功 |

旧11模块清单不包含可选`HoYoSDKNetworkFallback.dll`，这只是当时snapshot缺席，不能据此排除后续fallback transport。Java的CN MDK注册与AccountPlat的handler均存在，但不能给未知客户端服务作选择。

## 两服务共用入口：本轮唯一新增原件

两QWORD原bytes均为`e013348001000000`，SHA-256 `176be29ce1990016d636c9e57079e376542b42f2d7d721fba4e4959ef639c5e3`：

| 归属 | 原槽 | 目标 |
| --- | --- | --- |
| MDKLoginService，已证RTTI | SDK RVA`6C2098+8` | `3413E0` |
| AccountPlatLoginService，已证RTTI | SDK RVA`6C2CA8+8` | `3413E0` |

原入口已有REA Evidence `ev_6d7f10950d9744646b137e702ab759efd00c8a10c161a77721929e207140bdf9`，本卡复用其BaseLoginService伪码；原生身份同时由固定SDK两槽与唯一pdata独立核实。

| 原件 | 本轮值 | SHA-256 |
| --- | --- | --- |
| 完整body | RVA`[3413E0,34180F)`，1071B/265条完整指令 | `eea7fc7301072783039a2dcf7951c2a61e87dd3f935b63701b49fa2e6e32ef1a` |
| 唯一pdata | RVA`81B808`，12B=`e01334000f183400000a7300`，unwind RVA`730A00` | `53e955f337c260b588007096b6ffd86f2bedff27b0c8e6c3212247f3dddca1ec` |
| 既有URL/consumer窗 | `34ECA0..34F500`、`3433E0..343460`、`2F0940..2F118E`、`41E6F0..41E71E` | 各与冻结报告完整SHA一致；没有新增这些窗 |

14B ilhook最小搬移实际覆盖21B：`40555356574154415541564157488dac24a8fcffff`。它们是八个push和`lea rbp,[rsp-358h]`，没有CALL、分支、RIP相对操作或原return搬移；回跳为`B+3413F5`，再原样执行`sub rsp,458h`。不能沿用其它hook的297B trampoline或owned证明代验这21B。

令入口CPU RSP为S0：八push后RSP=S0−40h，LEA令RBP=S0−398h；原SUB后RSP=S0−498h，正常Win64调用下内部16B对齐。`341410`原`mov esi,r8d`保存mode低32位，`341416`保存RCX至RDI；`34168C/692`恢复R8D/RCX，`341695`才经原service virtual`+120`派发。正常出口`3417FB`加458h、反序pop八寄存器、`34180E RET`。这是void式服务调用，没有可拿作认证成功的RAX状态。

此入口覆盖两服务的共同前段，优于只看AccountPlat ticket/autologin之后的`3433E0`。实际进入`3413E0`仍不证明原`31B820`某一callsite已经到达；本卡不输出虚构caller身份。若后继确需“由某一准确LoginManager调用而来”，须另给该原callsite/普通返回证据，不能按同TID或函数范围补齐。

## 可交Sol xhigh的最小选择观察规格

这是**仅观察、尚未实现**的独立opt-in合同；先闭当轮service/mode，不读取route map、账号或任何请求。无需修改生产服务、MDK/AccountPlat逻辑、版本、校验或旧key核心。

1. 新产物只装`B+3413E0`一点；复用已验透明47转发、loaded-module binder、ilhook与assembly外壳的固定来源，另封新manifest/源/产物/导出和实际emitter。安装前绑定实际SDK path/base/SizeOfImage/disk SHA；目标窗绑定为1071B body及两8B vtable槽，3读1087B；47表/loader状态、ilhook decode/patch/protection与安装后实际trampoline另账。
2. 任何目标数据读取之前CAS占用首次任意`3413E0`入口；后续/重入/其它线程只透明通过，不等“正确服务”再抢尝试。保存的RCX为null即固定unknown；否则只同步借用读取`QWORD[RCX]`8B，精确比较当轮SDK base+`6C2098/6C2CA8`，不追对象字段、基类或全局，不读RDX/R9、参数栈、body/header、session或token。
3. R8D直接从已保存的入口寄存器取低32位，投影`0/1/3/11/other`；不把整个R8当64位enum，也不猜正式枚举名。输出只允许`base-service-entry`事实、`mdk/accountplat/unknown`、上述mode类别及固定读取失败类；不输出指针、任意caller、未知原文/长度/hash或事件计数。读取预算为首次最多1次8B；失败/短读立即seal，无重试、补页或第二次尝试。
4. callback不分配、不调用目标getter/Marshal/JSON/托管函数、不修改任何入口参数/stack/返回或服务字段。LastError包住callback并完整恢复；从进入assembly到快照之前也不能先经Rust破坏volatile状态。所有GPR、XMM0..15低128位、RFLAGS、原RSP/原return和原调用次数保留。原函数继续一次，正常返回仍归原caller；观察结果不包含`returned/HTTP/auth`成功位。
5. 新owned必须实际执行这21B的near/far搬移，用独立assembly核S0、原`[S0]`、完整GPR/XMM/RFLAGS/128B owned参数栈/LastError与无hook基线一致；用相同push/LEA/SUB与ADD/pop/RET形状的自有callee核一次原调用及高64位返回。还要覆盖两精确服务表、未知/null、mode高32位非零、首次错误后正确入口、重入及唯一读取失败/短读。自有对象不是目标SDK调用/GC或自然事件证明。
6. hook/userdata/表/module引用持有至进程期；安装失败保留原转发，不修改客户端可见性/完整性门，不在仍执行时Drop/unhook。正常退出→OS确认目标消失→按当轮journal恢复stock及核五输入/保护/alias是必要验收。xdata未取，SEH/C++/托管异常展开、YMM/ZMM上半部、x87/MXCSR等保持未知，不为此加入新机制。

装载完成不证明早于第一次service调用。主线后继需明确原正常login动作的producer/时序，确保观察器已安装后才让该有来源动作自然执行；本卡没有找到保证命中的零凭据页面动作。不得把“再等SDK页90秒”当实验计划；无事件只能保留未观察，不能判MDK或AccountPlat，也不延时/扩读/伪调用。该合同只允许后继准备与owned，尚不是现成现场许可。

## 本服接线的准确条件与当前首缺

**当前批准的自然host/path/method类别为0项。** 静态六候选如下，全部是HTTPS、无query/fragment，path均`/account/ma-cn-passport/app/loginByPassword`；不得把六项直接放进生产重定向表：

| passport code | 固定host |
| --- | --- |
| 0 | `passport-api.mihoyo.com` |
| 1 | `pre-passport-api.mihoyo.com` |
| 2 | `ue-account.mihoyo.com` |
| 3 | `devapi-takumi.mihoyo.com` |
| 4 | `sandbox-sdk.mihoyo.com` |
| 5 | `ptsapi-takumi.mihoyo.com` |

AccountPlat模式1的条件链已知：`3433E0→32F460→34EA20`设置账号UI，真实submit到`35C190→34ECA0`。`34ED61`以Config vtable`+90→3773C0`读取`loginUrl`，R9=0，返回点`34ED67`；该reader走独立`+870` map及原锁/复制，不读combo的`+860`。随后两次原`3950C0`加密调用的返回点严格为`34EEB7/34EF88`，再构造原account/password JSON并交RequestManager `3A57D0`；URL/body/header/callback经原异步任务复制。

最下游窄consumer仍为SDK `41E6F0`：RCX为64位easy，EDX为32位option，R8为option决定的vararg；只有EDX10002时R8才是NUL字节串。POST builder在`2F0A37/3A/3F`转发URL/10002/easy，`2F0A42 CALL41E6F0`、原return`2F0A47`；同body随后以option47、值1选择POST并在`2F0B54 CALL41E6E0`。这些是静态method/consumer来源，10002本身没有HTTP method参数。

将来若当轮证明AccountPlat/mode1与一个固定URL类别，具体接线才可约束为：只在已绑定`41E6F0`、原caller`B+2F0A47`、EDX10002与**那个完整已批准URL的NUL原bytes**同时符合时，把R8供给为主线已审部署的本地endpoint；只改该URL输入，原body/header/timeout/retry/callback、easy、其它option、原函数次数/顺序/EAX返回/LastError与全部校验保持。替换buffer安装前构造并持至进程期；原setopt复制或失败必须由真实原返回决定。不能prefix匹配、截query、规范化host、遍历未知字符串或改原返回。

当前`${approvedLocalHttpBase}`尚未由本卡取得运行归属。源码`HttpServer`实际把`HTTP_INFO.bindAddress/bindPort`交Jetty，当前portable/config候选为loopback HTTP；端口、HTTP/TLS、listener/PID/运行JAR及原native perform接受须由R4/R5新epoch核实。只将`.env`或客户端scheme改为本地并不闭合接线，更不能给原TLS/签名校验填成功。

route modifier的重试/后续调用策略也必须另定：一次观测预算不能使之后同一登录的原URL重新流向外部。未知URL按透明合同仍保留原请求，因此**在route未知时输入本地测试账号同样不合法**；不以本地账号“反正不是官方账号”作为泄露到未知host的理由。没有这个前置就停于无凭据选择观察，不启动账号提交。

若实际分类是MDK，立即停止沿AccountPlat的route/key/response接线。MDK的真实请求consumer、key来源/格式/算法及响应→AccountModel链须独立证明；已注册`/hk4e_cn/mdk/shield/api/login`并不能允许借用`34EEB7/34EF88`窄key。若是AccountPlat其它mode，也不假定密码路径；Phone/QR/ticket/autologin等按实际必要producer另立精确缺输入。

## 合法本地账号触发与验收顺序

选择观察/route类别及其代码绑定先于凭据。原AccountPlat两caller窄key核心及正式loader直接复用，但自然供给仍需同次来源事实；dispatch key/root与SDK账号RSA分别验，不能互代。后续本服endpoint经真实原native transport/本地HTTP往返及错误返回核实后，才可在独立测试DB/运行数据里使用主线确认的本地测试账号或合法本服已有会话；不使用用户官方账号，不操作原存档。

成功需依次见真实本地handler认证、原错误仍拒绝、原`342B10/338990`单token消费、必要`32F640→3448D0/345910`阶段及最终成功AccountModel。session verify/exchange与combo仅在真实必要路径出现时分别接线；不能为了取得33107先伪ret0/对象、远程调用40108或把HTTP200当成功。只有随后原40103成功→40108→33107才是[已审PostRequest](CLIENT_72_SDK_POSTREQUEST_ENTRY_20261111.md)的合法触发来源。R1/R3/R4/R5及D-02六项仍独立未完成。

## 实际验证、复现与交回

新工具只做固定文件读取。两fresh提取分别写`static-first/`与`static-repeat/`；每次先核15固定来源、profile五文件与自然11模块磁盘，再核两槽、已有REA身份和唯一exception extent，然后取唯一body。没有目标CPU/客户端/网络/构建/安装/服务/DB或Git提交/分支操作；只改本文、离线工具与同名test，无需便携重启。Project/Harness和提交由主线维护。

`python -B -m unittest tools.test_client72_sdk_native_account_local_route -v`的5项通过：独立原bytes/pdata/两槽、完整指令拼接及21B普通搬移、原service/mode到+120、自然事实不晋级，以及重叠extent/8193B/目录未对齐拒绝。它们不是hook/owned/原CPU或客户端验收。

两fresh的7份静态原件逐字节相同；各`static-audit.private.json`为9934B / SHA-256 `a68dbaa1538dbc552f14fa801c68c7f5d025c8ece83d8d54d90545de12387bee`。确定性证明1471B / SHA `0c7ed24c62f88a61688b66c265ae97f4dde281f82fbf1b1687296db3f48af8ef`。新工具11075B / SHA `d524afa925ebfd64b0c73d37506edd773bacef6edcd36f35dfa5c2fc153e8acc`；test4312B / SHA `a0a3067772e306d70821c0d1e4ca1bee6313e72826858269c2365f3b3ae280bc`。主线独立第三尚不由这两fresh代验。

首个fresh resolver使用本机Windows PowerShell5.1时被本机脚本execution policy拒绝；失败stdout/stderr保留。随后用本机已存在pwsh的新进程执行原`tools/game_path.ps1`退出0，未改脚本、系统policy或配置。一次Windows wildcard查找和一份minified REA初看造成大预览，后续改为按JSON字段读取；未改原件或重复分析。

主线第三复现使用新目录，保持旧双跑/失败原件：

```powershell
$sdkAccountClient = & tools/game_path.ps1
python -B tools/client72_sdk_native_account_local_route.py --client $sdkAccountClient `
  --output local/migration72/d02-resume-20261110b/b-native-account-route/static-main-third
$env:SDK72_NATIVE_ACCOUNT_ROUTE_REPORT = 'local/migration72/d02-resume-20261110b/b-native-account-route/static-main-third'
python -B -m unittest tools.test_client72_sdk_native_account_local_route -v
Remove-Item Env:SDK72_NATIVE_ACCOUNT_ROUTE_REPORT
```

复现只读相同固定输入，不需新REA。主线若要实施，下一可执行项为上述共用service入口的独立观察器/owned及时序证明；本卡到此释放max。准确业务首缺仍是自然service/mode，之后才选择一个route/key/response合同，没有可要求用户当前输入凭据的合法动作。

主线新进程重新解析GAME_PATH后执行 `static-main-third/`，7份原件与两个fresh逐byte相同，报告仍9934B/SHA `a68dbaa1538dbc552f14fa801c68c7f5d025c8ece83d8d54d90545de12387bee`；5定向测试通过。最初在当前shell调用脚本后误检查遗留LASTEXITCODE，改为实际独立pwsh退出码；没有目标变化。仅验静态共用入口合同，自然service/mode/loginUrl及route批准0项保持。
