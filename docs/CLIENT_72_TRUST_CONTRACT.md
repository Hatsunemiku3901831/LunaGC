# F-04b：保留原生验签的本地信任输入合同

2026-11-06。本项把既有密码学夹具收束为两个明确原生调用点上的输入供给合同；没有修改或安装patch，没有启动游戏、服务、数据库或调试器。只证明固定三输入下的离线原生执行，不构成运行DLL或D-02验收。

## 输入与来源

`tools/trace_client72_trust_contract.py`每次重新读取并验证`extract_client72.HASHES`的EXE/global/startup三文件，再提取目标结构。两条当前patch特征均重扫唯一命中，并核对实际call目标；未沿用旧ABI。沿用的明确模型包括CRT/Windows分配、单线程TLS/时间/熵、托管对象与常量、SDK初始化flag、受限JSON和wire存储；RSA、PKCS#1、SHA-256、key验签、响应验签和拒绝分支均实际执行。

| 输入 | 原生来源与用途 | 长度及身份 |
| --- | --- | --- |
| 服务端公钥 | 操作100加密客户端seed；操作103验证响应签名，两路径原始DER指纹相同 | raw268、完整DER270；SHA-256 `7427ee11ef690cfd43a001758eabc3604042b62f78a31cb21f4afe6d715d629e` |
| 客户端私钥 | 操作101解密Token/分区响应；从真实响应reader/保护段恢复到输入边界，仅在内存中使用 | raw1190、完整DER1192；公钥数值独立等于`src/main/resources/keys/game_keys/4_Pub.der`；指纹见摘要 |
| 本地服务端公钥 | 从仓库`SigningKey.der`解析公钥；既有patch blob另逐字节比较 | 完整DER270，`patch/server_public_key.bin`恰为其`DER[2:]`268字节 |
| 本地root及两类key签名 | 本次离线夹具明确使用同一仓库SigningKey的公钥作root；分别预签完整客户端私钥DER、完整本地服务端公钥DER | root只供公钥PEM；两签名各256字节。运行供给对象只持有公钥、签名和身份指纹，不持有签名私钥或客户端私钥 |

三条路径均实际到达`140571EB0`，返回点为`15A4D7F00`。其Windows x64输入：ECX是操作100/101/103，RDX/R8D是数据指针/长度，R9是操作输出参数；入口栈`+30`是raw key指针，`+38`是key签名指针，`+40`是签名长度。seed数据8字节，响应解密输入256字节，验签输入SHA-256摘要32字节。不得把这组签名与响应正文`sign`混为一类。

## 钩子位置与允许变更

| 点 | 实际指令与已执行ABI | 本合同的供给白名单 |
| --- | --- | --- |
| `140572059` | call `141FDF9C0`，实际为清零重建DER缓冲区。RCX=R14=新缓冲区；EDX=0；R8=R15=完整DER长度+1；RBX=完整DER长度；R13=原raw key；`[R12]`=operation | 仅已知公钥指纹且长度相等时，把R13指向预先持有的本地raw公钥。私钥不替换。R8不是key长度本身，268不能用于私钥 |
| `140574A95` | call `14078BAB0`，验签前。RCX=RDI=完整DER；EDX=DER长度；R8=签名；R9D=256；`[RSP+20]`=root PEM（callee入口对应`RSP+28`） | 仅已识别来源且完整DER指纹符合预制包时，更换R8签名指针及第五栈参数root指针。长度、完整DER、返回值均不改 |
| `140574A9A` | 验签返回后的`add rsp,30`，随后`test al,al`与原拒绝跳转 | 只观察AL。不挂旧`after_key_sign_check`，不写RAX或flags |

公钥重建分配271字节，私钥1193字节。原生代码继续写`30 82`并复制raw内容；供给时不改分配尺寸、不覆盖原始key数据。调用前后逐个比较18个GPR/RIP/flags，CHECK另比较前48字节栈，真实变化必须精确等于上表；返回点所有寄存器/flags保持。两个完整调用/决策窗口的32字节也逐样本与输入映像比较相同。

未知key的策略是保持原输入，不给它本地签名或root。本次在API输入边界修改公钥一字节，使其真正无法匹配已知指纹；后续两个供给点不作替换，原生拒绝。此合同没有“收到什么就现场签什么”的行为。签名在实例启动前预计算，运行供给无需签名私钥。

## 实际执行与独立验证

正式14样本：4成功、10拒绝。

| 路径 | 成功 | 拒绝 |
| --- | --- | --- |
| 完整seed helper | 相同非零64位seed、两组熵；完整返回344字符Base64，两组密文不同 | 坏公钥key签名、错误root、未知key；原生返回-7，空字符串，不执行加密 |
| Token响应reader→解密→SHA→验签→安装key | 高位64位seed与客户端seed异或，4096字节会话key逐字节等于独立Java MT64 | 坏私钥key签名、坏公钥key签名、错误root、合法签名的另一把公钥、坏正文签名、错误密文；均不安装key |
| 分区JSON→解密→验签→实际reader | 独立tag14 wire解析出`fixture-A`，后处理输入仍是同一解析对象 | 坏正文签名，不调用reader、不供后处理对象 |

每次key验签均与Python整数RSA恢复的完整PKCS#1 v1.5块（padding、DigestInfo、SHA-256）比较。seed加密还独立核对明文、非零padding及RSA密文；Token正文验签与独立整数RSA比较，原生SHA与hashlib比较。合法签名的错误服务端公钥样本中，两次key验签均通过，但正文验签失败，原生拒绝；这不是强迫所有错误都在同一门失败。

首次运行在坏公钥key签名样本的测试期望处失败：正文在数学上仍是正确签名，但key信任门已拒绝。修正独立预期为“key验签通过并且正文签名通过”，保留`bodySignatureIntegerOracle`与最终`bodySignatureAccepted`两个事实；没有改原生结果或把该错误样本改为成功。首次失败与中间结果留在忽略日志。

共享摘要只含长度、地址/操作和哈希，不包含DER、PEM、签名字节、目标私钥或机器路径。

最终两轮各14原生样本/23次key验签均通过，完整trace91,522字节与摘要38,549字节分别逐字节相同。摘要SHA-256为`0aea753929317d66b27da075c4e4f317652983d2ce50a92950614bea08a6753b`；trace及工具hash见忽略`trust-contract-determinism.json`。6项新增合同回归及seed/RSA夹具/响应/分区关联测试合计18项，失败/错误/跳过均0；不是完整研究集或实机测试。主线负责把本摘要纳入统一review及Project/Harness索引。

## 复现与产物

先按本机配置设置`GAME_PATH`与便携JDK17；Python不会自动读取`.env`。

```powershell
python tools/trace_client72_trust_contract.py --client $env:GAME_PATH --java-home $env:JAVA_HOME --output local/migration72/evidence/trust-contract-trace.json --summary-output protocol/cn-7.2/trust-contract-observations.json
python -m unittest tools.test_client72_trust_contract tools.test_client72_seed_crypto tools.test_client72_rsa_fixture tools.test_client72_response_crypto tools.test_client72_dispatch_prefix -v
```

第二次把两个输出改为忽略目录下`trust-contract-repeat.json`、`trust-contract-summary-repeat.json`，再逐字节/hash比较。最终证据为`local/migration72/evidence/trust-contract-{trace,repeat,summary-repeat,determinism}.json`，日志为`local/migration72/logs/trust-contract-*.log`。

## 尚未解除的运行前置

当前`patch/src/modules/security.rs`依然是原实现，验签返回后置RAX=1不可部署。本项没有变更patch子模块引用、运行Java/Proto/配置/资源，故没有重启或便携部署要求；也没有安装或操作客户端。

下一项可执行工作是把上面的已验输入合同接到正式补丁：先决定本机受信root与生成/加载位置，在忽略产物中预制仅含root公钥、服务端公钥、两类签名、三输入哈希及来源指纹的bundle；目标私钥不得输出。随后以固定二进制测试实际hook/trampoline和完整回调ABI，去掉返回值强制成功，并验证实际DLL加载与拒绝路径。离线供给类和现有SigningKey作root只是本次明确夹具，不等于已完成生产信任配置。

可直接复用的生成步骤：`discover_private_key`只在内存返回目标DER，校验SHA-256及key4公钥数值，随后用本机root签名者对这个完整DER做RSA PKCS#1 v1.5 SHA-256签名；另一份签名覆盖完整本地服务端公钥DER，不能覆盖268字节raw或DER的hash字符串。构建输入是固定三文件、明确的root签名私钥与服务端SigningKey；构建输出仅保存root公钥、服务端raw公钥、两份256字节签名、相应DER指纹/长度及三输入哈希。无需在DLL包含root签名私钥、服务器私钥或目标私钥。

实际回调分类应使用该次调用的`[R12]`与完整DER指纹：101匹配目标私钥指纹；100/103在前一个供给点匹配原生公钥后改为bundle的R13指针，后一个点可再核对这个指针及本地完整DER。离线记录中的`current`是单实例观测状态，不是正式补丁必须照搬的跨回调全局变量。文中RSP是CPU执行到call前的真实RSP，**尚未证明ilhook保存到`Registers.rsp`的值与之相同**；下一项必须先读当前依赖实际实现并验证，不能直接把`reg.rsp+0x20`当已验ABI。

SDK初始化、HTTP/字符串工厂、完整Handler/分区、socket和D-02仍独立未验。不能只移除旧RAX写入或只替换268字节blob就声称本地信任已供齐；客户端私钥原有签名在换root后也必须同时得到对应的有效本地签名。
