# D02-R2：SDK账号加密、key与route合同

## 独立攻坚计划（2026-11-08）

目标：承接已冻结transport/login-chain卡，准确恢复AccountPlat CryptoManager对account/password的加密入口、key producer/来源/格式/编码/算法/生命周期，判断既有EXE托管SDK_RSA hook是否覆盖该原生链，分离SDK RSA与dispatch key4/正文和key签名。若固定原生literal和producer足以证明route，同时交最小自然观察规格。

证据：原profile五输入和父卡11模块磁盘hash；父卡source/raw/REA导出；新自然monitor `plain/run-elevated3.json` SHA-256 `303db4bc6daa85d3fda6d73f52cbe339021ba84cfaa45152bb4bf92a1746c58a`，只读scope `main/cold-arrays-tool/live-elevated1.json` SHA-256 `63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0`。主线确认三SDK根非null、engine/manager vtable和46字节setopt入口匹配，用户在登录页未输入凭据；这不证明自然加密或HTTP调用。

未知：CryptoManager准确槽位/代码、key来源与所有权、native和managed链的交点、RSA padding/编码及错误返回；`loginUrl`实际producer和值；自然动作/加密/请求仍未知。不能以类名、nearby字符串、旧patch注释或初始化根存在推断覆盖。

候选：component aggregate中的CryptoManager、AccountPlatAccountLoginJobber `34ECA0`的真实调用参数、配置getter与初始化writer、内嵌密码库入口；先复用固定REA Evidence，必要时仅对9MB SDK做有界静态分析，不再对450MB EXE整库分析。

最小实验：沿真实call/vtable/RTTI与指针writer闭链；对可独立执行的原生段使用合成非零/64位/错误和边界输入，算法输出只在内存与独立oracle比较，raw只留固定身份、长度、布尔结果和显式模型事件。绝不记录真实或合成账号URL/query/body内容、密文/明文及其hash；key只保留公开身份或公钥数值相等布尔，不输出私钥。

修改范围仅`tools/client72_sdk_crypto_contract.py`、`tools/test_client72_sdk_crypto_contract.py`、本专题及忽略目录`local/migration72/d02-active/sdk-crypto-contract-astra/`。父transport/login-chain冻结文件不改；禁止live、attach、hook、inject、断点、新DLL、网络、服务、DB、Gradle、构建/安装和Git。生产原始正文日志问题由主线处理。

失败出口：来源hash变化、未解析间接边、真实算法/生命周期输入不足时保留精确停止点与首缺输入；不让模型先给成功，不跳校验，不用旧ABI补空。停止于证据闭合或准确最小缺口，继续独立离线路径时另列边界。

验收：source/全部输入hash、目标原始机器码与call/return、明确模型清单、独立原生样本/错误与边界断言、两个新进程确定性和独立第三命令；给主线可审生产规格或最小剩余输入。研究通过不等于本服SDK或D-02验收。

## 已闭合的接入阻塞

**固定7.2 AccountPlat账号提交使用原生CryptoManager内嵌的1024位公钥。它与当前服务端四份私钥对应的公钥以及旧patch的SDK XML公钥均不相等。** 本卡实际执行key getter、SPKI/PEM解析、PKCS#1 v1.5 type2填充、原生大整数RSA及Base64完整返回；独立整数RSA核结果相等。旧`passport_1024.der`注释中的历史DLL地址或“匹配国服”不能当作当前7.2事实。

原生加密链在AccountPlatNative内完成，不经过EXE的`SDKUtil.RSAEncrypt`。本卡对旧patch特征在固定EXE重扫唯一命中`15997C290`（RVA`1997C290`），与既有metadata/ABI声明一致；它的两个托管string参数和本卡原生PEM/byte-pointer接口不同。**仅在旧managed hook换XML key不会覆盖本卡已执行的原生AccountPlat链。** 不由此泛化“该EXE入口从不自然执行”，MDK/其它业务仍可能走不同路径；本次自然选择AccountPlat及密码提交均未观测。

固定route也闭合到producer和native reader：`107FE0`原生构造6张环境表，`passport_env`选择其中一张，`loginUrl`读到准确的`/account/ma-cn-passport/app/loginByPassword`。配置对象持有这些URL，和父卡请求结束后释放的task/easy不同。它是可供下一合同讨论的配置来源，不是当次请求已发出的证明。

## 固定身份和证据来源

工具每轮核profile五文件、父transport自然11模块磁盘表（13个唯一文件）、父source/test/raw及本卡REA/Java epoch。唯一输入清单为`local/migration72/d02-active/sdk-crypto-contract-astra/input-manifest-v2.json`，SHA-256 `7c574fcd6638b70434a249a587708ad69b035662b357359f09bc617dc3d5a958`；其中完整来源hash保持本机，不包含私钥字节、任何明密文或其hash。旧`input-manifest.json`是改动前epoch，保留不覆盖。

SDK SHA仍为`adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`，EXE仍为`8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`。父login raw `final-v2-first.json` SHA `3d864c07b944ed63af4f69e07f5b4e9abafb8f2e30de47b716bd0f06f6c7fd96`、父source `53da43c970a179699d80a1eab570bff47b9b619d0424c83597766b96d670cad2`和test `424a8cc7204147e11933a869f582461572154e960cb27a9868fe5b3b0871853d`均固定。父专题允许主线追加审核，本卡不把它的旧doc hash设成证据门槛。

新自然raw仅复用其明确结论：source `2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae`，`sdkResult=roots-sampled-not-a-request`，`sdkModuleBinding=file-entry-matches`，engine/NetworkManager vtable匹配。没有从自然raw取账号、URL、请求正文或加密输入，没有读live；三根和setopt入口匹配不证明CryptoManager调用或配置表自然选择。

应用`reverse-engineer-anything`技能，复用父SDK快照并有界查询同一固定SDK，实际取得目标函数反编译；无EXE整库分析。会话已导出并close：`rea-final.json` 3137981 B/100 records，SHA-256 `3c14ee95de5234b04ceb559b13e724ef78819cad1042fde6f08d19673cbcf91c`；snapshot 4642947 B/90 records，均含前卡承接。最终报告逐条保留REA ID/参数/限制，反编译没有当源码或自然执行。

| 关键函数 | 具体REA Evidence |
| --- | --- |
| key getter `3951C0` | `ev_0893cc12a6a37ac1509ca8c1f740c092da13bdc23278677caeb3a66bd612da06` |
| string encrypt `3950C0` | `ev_286a71a1a4e90f9a967598cd873b245cfd42439b31e0d4037a23b6fb19d75786` |
| byte encrypt `395D00` | `ev_03e7ab54d7ccc9711f6f1727a7e4dc24d7f7f642c5282db95ca802543716da03` |
| native RSA `4C9DD0` / padding `4ED800` | `ev_4e08537923365c66bd8408f34d3db7ab121f909fb72a7da5cbd38411c963c761` / `ev_65a0436af0bcb1118a917b701b5486090ae3bcd6f1983dd1268921a706d9fa44` |
| route table ctor `107FE0` | `ev_8eb29df67c2182417b506d0fef96b281be6a18b25b889662b0e3f89e1a0cb1ba` |
| config chooser `3646A0` / selector `37E270` | `ev_add2ff8b1cef8f63ce9f6381186d07bfb14840b838433e0f93d79f96475d2757` / `ev_7dc82e4977aaf532c97c7bba296d9067685612741ff6e05c073ef1f92759eb65` |
| getter `3773C0` / map reader `37E3E0` | `ev_4078f1c22c4abd997cebf7d96e856cd0544089d8fdd15bfb43bfe58a341df8c1` / `ev_2886e561f88dba52eb5581258346521892828bc371f3688c6537a415b168a788` |

## CryptoManager、ABI与所有权

`1538E0` component初始化在`153929`读全局`7E89B0`，null时分配8B；`153941/153948`写vtable`6D1CB8`，`15394B`发布全局，`153957`写aggregate`+38`。准确RTTI为MiHoYoSDKCryptoManager。对象只有这8B接口头，本卡未发现按实例存储的动态key字段。

以下均为固定SDK RVA；Win64栈偏移以函数**入口CPU RSP**为准，不能直接套到某hook库保存的Registers.rsp。

| 入口 | 参数、执行及返回 |
| --- | --- |
| vtable`6D1CB8+10→3951C0` | RCX=this，RDX=caller提供的32B std::string输出，R8D=mode；返回RAX=同一输出对象。mode低32位等于1时复制RVA`6D19D0`的444B SPKI PEM/RSA2048，其余复制`6D1B90`的268B SPKI PEM/RSA1024，e均65537 |
| vtable`+18→3950C0` | RCX=this，RDX=32B std::string输出，R8=字节输入指针，R9=长度存储地址，`[RSP+28]`=NUL结尾PEM指针。输出先置空，经virtual`+20→395D00`取得临时vector，再由`3B6A00`原生Base64编码并移动至输出；返回RAX=输出对象，**不是HRESULT/成功bool** |
| vtable`+20→395D00` | RCX=this，RDX=输入bytes，R8=长度地址，R9=PEM，`[RSP+28]`=3 QWORD vector(begin/end/cap)。`395DF5`从长度地址读**DWORD**进ECX，调用`4B9D10`；调用方虽用QWORD长度，实际RSA接口取低32位。不能擅自统一成64位长度 |
| `4B9D10→4C9DD0` | RSA对象method pointer`+8`指向默认表`7634D0`，slot`+8→4C9DD0`；ECX=长度32，RDX=input，R8=output，R9=RSA对象，第五参数padding=1。`4ED800`执行type2检查/填充，随后native BIGNUM/Montgomery路径到`4D67C0`；返回128或256字节，失败-1 |

账号caller `34ECA0`两次取mode0：key getter调用`34EE78`/`34EF4A`，encrypt调用`34EEB4`/`34EF85`，准确返回点分别`34EEB7`/`34EF88`。第5参数从getter的std::string取C-string；account/password原输入先按首NUL计算长度并复制，再传length地址。getter副本各在加密完成后析构（`34EF05`/`34EFD6`）；不能在函数返回后追这个key临时string或其data指针。

395D00用`4B9980`建立PEM memory BIO，`4BB500`新RSA对象，`4BA050`先按SPKI public PEM解析；失败时重建BIO并走`4BA010`备用RSA公钥解析。失败保持输出为空。native解析及key检查均执行；没有把公钥格式、padding或RSA换成Python模型。成功时临时RSA/BIO/vector在返回前释放，最终std::string由调用方持有并析构。本卡只验证了输出/临时片段所有权，不宣称完整OpenSSL进程退出清理。

原始string接口直接接收bytes，无UTF8→UTF16或managed Marshal调用。本卡UTF8及嵌入NUL样本验证按显式长度加密原bytes；业务caller在进入接口前用C-string长度，因此真实密码中的首NUL之后不会进入此调用。空bytes在crypto层可加密，外层`34ECA0`的空账号/密码拒绝仍是另一层业务检查。

## 独立key比较与密码学验证

key getter的四组原生样本mode `0/1/2/100000001`分别得到1024/2048/1024/2048位key，输出/析构均原生执行。独立DER/SPKI解析在内存取(n,e)，与`private_key.der`、`auth_private-key.der`、`SigningKey.der`、`passport_1024.der`的公钥数值及`patch/sdk_public_key.xml`逐个比较，全部false。私钥仅在内存解析，不输出其字节、参数或衍生明密文/hash。

14组完整原生encrypt：1B、117B成功；118B被真实padding门拒绝；空输入、内嵌NUL、7B UTF8成功；长度存储`100000001`实际只用低32位1；malformed/truncated/null key均执行两个原生PEM解析器并返回空；2048位key的245B成功/246B拒绝；最后两组仅在离线夹具给第5参数传既有`passport_1024.der`对应的SPKI公钥，1B和117B均被同一原生算法接受，独立私钥整数解密回原合成输入true。

成功样本在内存读取原生padding结果，独立核`00 02`、至少8个非零padding字节、分隔NUL及原始bytes相等；再以Python整数`pow(m,e,n)`与原生cipher逐字节比较，并与原生Base64输出的独立decode比较。全部成功只记录boolean及长度。1024位结果128B/172字符，2048位256B/344字符。118/246错误真实`4ED800→0`、`4C9DD0→-1`；manager仍返回原输出对象但其长度0，不能把非null RAX当加密成功。

这些本地key样本证明**供给公钥输入即可让既有server私钥数学上解密，原生算法和拒绝逻辑无需改写**。它们不批准实际hook或已完成Java/HTTP账号互通；Java认证程序及数据库未在本卡执行。

## route producer、稳定配置与自然边界

`107FE0`实际构造global`7DBF70`：外层环境code树，每个node key DWORD在`+20`，内层map对象从`+28`开始。内层node的std::string key/value为`+20/+40`。本卡执行初始化并读取准确`loginUrl`项，而非将静态近名当关联。

| code | 固定host | 固定path |
| --- | --- | --- |
| 0 | `passport-api.mihoyo.com` | `/account/ma-cn-passport/app/loginByPassword` |
| 1 | `pre-passport-api.mihoyo.com` | 同上 |
| 2 | `ue-account.mihoyo.com` | 同上 |
| 3 | `devapi-takumi.mihoyo.com` | 同上 |
| 4 | `sandbox-sdk.mihoyo.com` | 同上 |
| 5 | `ptsapi-takumi.mihoyo.com` | 同上 |

六个固定输入均HTTPS、无query/fragment；这不是截取真实URL得到的日志。`3751E0`初始化ConfigManager`+870`为AccountPlatformUrl shared对象（shared control allocation70B，object=allocation+10，object内map头指针`+0`、count`+8`、mutex`+10`）；与MiHoYoSDKRequestUrl的`+860`严格区分。

`3646A0`读取配置JSON的`passport_env`整数/数字，缺失或空JSON默认0；`378850`也从SDK初始化参数中的`passport_env`选表。它们经`37E270`查global`7DBF70`并把对应map复制到AccountPlatformUrl；未知code保持旧map，本卡99样本原生验证。getter `3773C0`：RCX=config，RDX=返回std::string，R8=键C-string，R9=可选override code字符串；非空override先解析并选表，随后`37E3E0`查当前map。`34ED61`实际传R9=0和`loginUrl`，沿用此前选择。`37E3E0`查不到或空value时返回固定`https://127.0.0.1`，这是错误fallback，不是本服已接线。

六个选择/reader均实际原生执行，另核99保留旧值、显式override `0`、未知key fallback和空配置默认0。自然`passport_env`及返回URL仍unknown。配置map比请求task长寿，但会被选表函数加锁清空/复制，外部RPM读没有取得同一mutex，不能称并发安全/原子snapshot。当前raw仅采aggregate头16B，没采其`+40`config或`+870`，不能倒推已初始化或选中0。

若主线未来选择只读配置观察，准确首级链是SDK全局`7ACC80`→aggregate`+40`→ConfigManager（vtable`6C6CD8`）→`+870` AccountPlatformUrl。只读节点内容须另审边界、更新窗口和失效处理；本卡没有实现/批准walker，也不要求再启动采集。比遍历map更小的自然事件是`3773C0`来自caller返回点`34ED67`的`loginUrl` getter完整返回，在调用期只分类固定host/path，不保存原文或hash；仍缺无校验影响的观察装载方式。

## 显式模型与失败记录

原始PE映射在Unicorn，未启动目标进程、创建OS线程或发送请求。模型只覆盖已遇到的存储、单线程OS状态与合成熵：

- 父Fixture的C++/CRT allocate/free；Windows HeapReAlloc按同一所有权账本复制/释放。输入key/output/string/tree/vector均仍由目标代码进行逻辑操作，不替换container或密码算法。
- TlsAlloc/Get/Set、critical section、Get/SetLastError、固定合成PID/TID、GetModuleHandleExW的当前已映射SDK身份；CRT `atexit 48274C`只记录退出回调。没有真实loader pinning、并发和进程退出清理。
- BCryptGenRandom提供可复现合成熵，GetSystemTimeAsFileTime/QueryPerformanceCounter提供合成时间；后续OpenSSL DRBG、非零padding循环及RSA仍执行原生。不是自然随机源测试，不能把合成熵移入生产。
- 不模型PEM解析、key格式/尺寸验证、padding、RSA、Base64或其返回。每个native sample单独列model boundary和调用数；成功/错误均从原生返回路径取得。

探索`probe1..9`逐项保留未绑定的TLS、CRT atexit/heap、module、LastError、随机/时间边界。首次直接执行未初始化CRT onexit表时遇无所有权HeapSize，随后把模型限定到已独立反编译确认的atexit注册入口，未把未知pointer伪造为合法heap。后续逐个补明确Windows状态，未在crypto入口填成功；`research-complete1`及matrix为探索版本，最终冻结结果另列。OpenSSL进程global/TLS分配留到夹具销毁，输出副本逐项验证释放；不报“所有native heap归零”。

## 给主线/Sol的窄生产规格及准确首缺

只给review合同，**本卡没有实现、编译或安装DLL**。保留原始五文件、原生RSA/padding/返回与dispatch验签；当前旧整patch包含未验的ccp/memguard/NEUTRALIZE/swap/异常处理器，仍不可整装。

1. **SDK key输入**：候选点为`3950C0`入口，先核固定SDK/完整指令、CryptoManager准确vtable，以及caller返回RVA严格为`34EEB7`或`34EF88`；当前key须匹配本卡固定mode0 SPKI公钥身份。仅将入口`[RSP+28]`的PEM指针供给为预制、进程期持有的本地1024位SPKI PEM，其公钥数值必须匹配本服明确选定的私钥。保留RCX/RDX/R8/R9、length storage、输出对象、其余stack/GPR/XMM/flags、LastError与原生调用/返回；未知caller/key保持原输入。不要全局替换key getter，以免影响其它使用者。不能将model成功码或private key带入DLL。
2. **route输入**：静态候选是上述六host/path之一，实际接入必须先有自然固定分类；可以承接父卡`41E6F0` option10002、POST builder caller返回`2F0A47`合同，在已证host/精确path时供给本服URL并保持其它参数和所有权。没有批准扩大到所有URL、SDK全部路径或借WinHTTP包办原生curl。实际loopback host/port由主线受审部署/config/监听归属提供，不只改环境变量。query/fragment及所有原账号/headers/body不输出、不hash；未知路径保持原输入。
3. **独立门**：SDK请求加密与dispatch key4解密、server SigningKey正文签名、两处key/root供给不是同一条门；本卡local RSA互通不替代既有[F-04b/c信任](CLIENT_72_TRUST_PATCH.md)、[HTTP版本准入](CLIENT_72_HTTP_ROUTE_CONTRACT.md)或[Marshal](CLIENT_72_MARSHAL_STORAGE.md)。不得跳签名、改返回值或仅改版本取得“通过”。

最小natural首缺是**准确AccountPlat账号加密入口命中与固定route分类**：caller身份、key属于已知mode0这一布尔、输入/输出长度、原生结果状态；不需要采账号、密码、密文或其hash。若主线先只观察route，则以精确`3773C0`返回/`41E6F0`事件分类，root和固定表存在不能替代。合法装载路径、内存校验影响、真实ABI/生命周期仍须独立实现/自有fixture与实机验证；不存在这些输入时本卡不会批准hook。

后继还需准确SDK响应consumer/错误/会话交换合同、真实server账号试验及7.2准入；已有Java函数注册并不证明回复满足本SDK。当前Java旧epoch与主线新日志epoch做了保留字面量的token比较：只移除comment及明确logger/printStackTrace调用后3文件逐token相同，key选择/padding/fallback/业务未变。新epoch三个SHA为`0c839b9d…be732e`、`6b31741d…d81488`、`e559eb97…55fae5`，完整新旧来源和审计留`java-input-epoch0/1`；旧rawBody/明文日志问题由主线修复，本卡不据历史记录问题要求重复修改。

## 实际验证、冻结及第三复现

两次全新Python进程完整执行上述4组key getter、14组encrypt、6环境表初始化及选择/错误reader。14组encrypt每轮共6896139条记录的原始SDK指令；原始route initializer另202615条。两份`final-first.json`/`final-repeat.json`各**310124 B，逐字节相同**，SHA-256均 **`c91ebc70a0afee16cb9379286fab86a10deea2a1ec8cef10a0c49fc9a7d17313`**。15项检查通过，无失败/错误/跳过；日志`final-first.log/final-repeat.log/tests-final.log`。记录中只有固定公开host/path、长度、源文件身份、模型及布尔结果，没有真实或合成明密文及其hash。

冻结source `tools/client72_sdk_crypto_contract.py` SHA-256 **`9b8c8f10f88260bbd3dfc2c62b76632c3fad9a0e0d9d7de2fb74e04f22b66395`**；test **`422d21c187e897d195fd3c754802e16fc8e4da8916845104fabdca70c83683ee`**。最终`hashes-and-determinism.json`保留显式bytes equality、source/test/doc、输入清单、REA、日志和第三命令的身份；所有旧probe和research原样保留，不混称最终冻结源。

独立第三命令（新输出不可已存在）：

```powershell
$sdkRoot = & tools/game_path.ps1
python tools/client72_sdk_crypto_contract.py --client $sdkRoot `
  --output local/migration72/d02-active/sdk-crypto-contract-astra/third.json
$env:SDK72_CRYPTO_REPORT = 'local/migration72/d02-active/sdk-crypto-contract-astra/third.json'
python -m unittest tools.test_client72_sdk_crypto_contract -v
Remove-Item Env:SDK72_CRYPTO_REPORT
Get-FileHash -Algorithm SHA256 tools/client72_sdk_crypto_contract.py, tools/test_client72_sdk_crypto_contract.py, `
  local/migration72/d02-active/sdk-crypto-contract-astra/third.json
```

需既有本机依赖、受控key文件和REA输入，不需重新开Ghidra；所有key材料仅内存使用，脚本拒绝覆盖或写出checkout的local目录。仅本卡新工具/test/专题及忽略输出改动；无live、服务/DB、网络、生产/patch/config/资源、构建安装或Git提交。主线统一审核/提交与Project/Harness更新。本卡完成的是限定Crypto/route研究，**本服SDK及D-02客户端验收增量仍为0**。

主线已审真实代码、CRT/OS/合成熵模型及原始证明，第三`main-third.json`为同一310,124字节/SHA，与双跑逐字节一致，15项检查通过；证明 `main/scene-and-sdk-crypto-main-proof.json`、日志`main/sdk-crypto-main-tests.log`。接受窄输入合同供后继实现自有ABI夹具，不授予目标hook/装载许可；natural入口/route、loader与校验影响、SDK响应/会话及7.2准入继续未知。
