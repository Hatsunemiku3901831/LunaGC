# 7.2 Token 成功分支与会话研究

输入为 extract_client72.HASHES 固定的三份目标文件。本页只记实际执行范围；不是客户端登录或补丁验收。

## A-04d5a 解密后 seed、整数存储与会话密钥

新增 client72_session_runtime、trace_client72_session 和独立 Java 对照入口 Client72SeedReference。研究从 Token Handler 内 **15A4E064F** 开始，明确输入为前段解密得到的 byte[] 与本机保存的 uint64 client seed；停止在 **15A4E0D4B**，即账号状态写入之前。这不是从 Handler 入口贯通的成功样本。

实际执行的目标行为：

1. 检查输入长度至少8；159B54250 读前8字节，15A4E06C4 交换字节序。长度9的样本表明这里不要求恰好8字节。
2. 14CB58170 解码静态14587D740的16字节整数存储，原生循环按字节 XOR；独立表达式为 `int.from_bytes(decrypted[:8], 'big') ^ client_seed`。14CB58BD0再次编码结果，真实解码检查得到相同64位数。
3. MPPHJAKKLFI 的原生 cctor 14B770850 写 N=312/M=156；初始化14B76FEB0、提取14B770520及两个状态更新函数执行。以seed初始化，首个输出初始化第二实例，丢弃第二实例首个输出，再将512个uint64按大端写成4096字节密钥。
4. 结果存入 GLOBAL+19580 的 byte[]，145878FC0置0。此标记的具体业务名暂不命名；不能仅凭置0断言完整网络状态切换已验收。

托管边界只模拟指定类的对象/数组分配、线程本地System.Random对象及Next值、整数存储用16×256置换表。整数编解码与MT64运算均执行目标代码；两套互逆置换表（恒等/197乘41移位）与不同随机值验证输出不依赖夹具表。数组类、长度、元素宽度不匹配即拒绝。

实际验证：**12组4096字节原生结果与仓库既有Java MersenneTwister64逐字节相等**，覆盖零、高位、64位全1、大小端可区分值和尾随字节。另**6组长度0/1/7输入**到达原生异常路径，密钥未安装、标记未切换。91项工具/映射回归通过；研究校验通过，ready仍拒绝。trace与摘要二次生成逐字节一致。

Java对照只编译MersenneTwister64及离线runner到忽略目录，不加载服务端、数据库或已有存档。工具报告固定Java输入源码SHA-256，不改运行代码或服务端协议。

## 尚未闭合的前段

- Handler入口1507BE0E0成功前缀读取响应+B8；147CF3070在普通ByteString存储路径实际返回+18的数组。此前默认null数组的探索失败属于合成夹具限制。
- 14D581600检查运行时列表是否含5714。两个分支目标literal为`[Shell] Old WarmupSwitch`/`[Shell] New WarmupSwitch`；后者进一步调用15A3CE060（0xCA）。已执行至内部读取140583A00，后续未恢复；没有将此调用模拟为成功。
- 响应+98/+88现已由A-04d5i正式工具从wire接入Base64、原生私钥验签/解密、托管SHA-256、响应验签及密钥安装；成功明确使用本地签名信任输入。完整Handler/真实SDK初始化与补丁仍待验；上面的解密后片段本身不能验证前段。
- 响应+68写会话+98，1507C2211读取同一会话成员传给151213730；后者写请求+D8。该链已由下一节的独立wire与分段原生执行验证；未据此宣称完整Handler执行。
- Login构造保护续段已越过151213AC6；148A22550跳1421C1F40（目标原生ret），后续需合成1458971C0类初始化状态并追踪下一真实调用。未知helper继续失败，不默认返回成功。

## A-04d5d client seed来源与保存

Token字符串构造15121DB40首先调用152F1B4D0。目标System.Random类型280的槽8为NextDouble；原生代码调用此槽后乘1422404F0的double常量`2^64`，执行uint64转换，再经14CB58BD0编码，将同一16字节同时返回并存到14587D740。该存储正是A-04d5a后续XOR的输入。

SessionRuntime72新增generate_client_seed：仅提供受控NextDouble结果，缩放/转换/整数编码/全局保存均执行原生代码，并用独立表达式和真实decoder核对。两套置换表各5组输入（0、2^-53、0.5、0.123456789、1的前一个double）共10组通过；0.5得到8000000000000000，最高输入得到FFFFFFFFFFFFF800。工具拒绝NaN/无穷/负数/1及非数值；没有把这组受控随机值当成客户端真实随机性测试。

此前12组4096字节Java对照与6组短输入拒绝同时重新通过，产物重复一致；工具回归106项通过。证据仍为session-seed-trace/observations/determinism，新增clientSeedSourceFixtures；日志client-seed-source-tests.log。

早期15121DB40保护路径进入Shell操作0x64后停于栈探测/SDK状态前置；现已由下文A-04d5g的正式工具推进到完整原生返回。SDK真实初始化仍未闭合，不能以夹具标志代替实机结果。

此前ArgumentNullException(inArray)已确认来自夹具遗漏145B74508运行时空byte[]常量。补上该常量和String.Empty后，未初始化分支正常返回空字符串；不是客户端缺陷。类型usage的kind1/registration+48探索仍只作索引，不能套用literal的registration+58缓存。A-04d5g明确列出运行时常量夹具，不声称已执行整个metadata/SDK初始化。

## B-02c2 会话token保存和Login参数

trace_client72_token_flow从独立响应wire `32 <length> <ASCII>` 开始，经真实TokenRsp reader将string写至CLDBKDNJHFG+68（tag6）。随后分别执行以下边界，实际存储的指针贯穿这些片段：

| 边界 | 实际观察 |
| --- | --- |
| 15A4E0D74→15A4E0D9A | 15A4E0D93将响应+68存入HOMIEDIPBBJ+98；缺少会话对象到达原生null错误路径 |
| 1507C21EB→151213730 | 从同一会话+98读出token作为RDX；RCX为GLOBAL+A0D0发送器；R8保留管理器+1D8的uint32，具体含义未知 |
| 151213730→1512137F2 | 原生构造前缀将RDX存至KOHBOMDDFCB+D8；stage值6被捕获；分配和stage辅助操作明确模型化 |
| 完整请求writer152EEB470 | 保存的token内容生成独立预期 `0A <length> <ASCII>`，即tag1；空值省略 |

4组链路样本通过：两组非空、重复tag6取最后值、空token；参数覆盖0、1、80000000、FFFFFFFF。另1组缺会话原生拒绝通过。两字段以`token`进入三层表并标注有界来源；本检查点未由字段确认推导整包或发送已完成，请求身份由下节另验。

校验器增加session-token-observations三份输入hash完整性、固定store/caller/类型/CmdID/字段集合与wire/映射一致性检查，拒绝缺失/重复/额外字段和替换来源。95项工具回归通过。完整RSA/UID处理、调用者timer/reset、首个token存储后的构造、实际网络发送均在此实验之外。

## A-04d5b 两个请求身份与CmdID

追踪此前模型化的stage操作14C8AE9A0，元数据方法711360的参数538572/type675783指向枚举85949/NEKEJMEIJBF。从目标field-default表恢复其16个符号，**不按声明顺序赋值**；原生常量读取也核对了6/7/8/9及两端。

| 请求业务身份 | 原生阶段标记 | 实际分配类型 / getter CmdID | 已确认响应阶段 |
| --- | --- | --- | --- |
| GetPlayerTokenReq | OnPlayerTokenReq=8，151220009→14C8AE9A0 | NJAALDCMHEP，1458C5500；1500C4A50返回24604 | OnPlayerTokenRsp=9；已由目标日志标记确认CLDBKDNJHFG/27808 |
| PlayerLoginReq | OnLoginReq=6，1512137B4→14C8AE9A0 | KOHBOMDDFCB，1458C3DF0；152EF20F0返回23172 | OnLoginRsp=7；已由目标日志标记确认NDENMDAIAMC/20835 |

trace_client72_request_identity执行两个阶段→分配检查点、两个成功响应到阶段操作的前缀、两个请求原生getter，共6组。请求self-class单元同时核对其声明类型Clone中的RIP相对load及同类型CopyFrom调用；不是只把合成类指针命名为预期类型。

两个请求消息身份和CmdID现可标静态verified，四个登录请求/响应身份均闭合。**完整字段语义、完整构造/发送、密码学前段和实机仍未闭合，B-02整体仍不ready。** 本批不依赖完整构造得到身份，也不声称完成完整构造。Token前缀起点15121FFF5明确在SDK预处理之后；Login从151213730开始到分配调用；阶段业务操作本身仍是捕获模型。

枚举总计8类型102符号；4条literal/22条原生常量验证通过。100项工具回归通过，identity trace/摘要二次生成逐字节一致。新增校验器检查精确阶段参数、常量、类型/Clone/CopyFrom/getter地址、响应身份和三层记录，保留runtimeStatus=not-tested。

## A-04d5c / B-02c3 Login构造至发送入口

新增trace_client72_login_constructor，从151213730执行两段保护续段，最终到151214D20入口；核对RCX仍为原发送器、RDX为原生路径分配并填充的请求对象。不是发送队列执行或实际发包验收。

限定环境：目标运行时开关列表包含3628，执行原生旧Shell分支；CloudGame使用已初始化的非云缓存；可选环境功能列表为空；其余明确初始化为默认值。旧Shell操作3、0、1、2分别提供不同不透明ASCII字节，**不**将新Shell/IFix调用默认成功。ByteString构造/赋值、三个SDK包装、CloudGame检查和可选列表检查执行真实代码。所有执行触达的literal从目标usage/source解码后分配；没有用空字符串替代未知literal。

| 稳定字段 | 目标来源 | 偏移 / tag / wire |
| --- | --- | --- |
| uapc | MiHoYoSDK.InvokeReturn(`info_get_uapc`) | +18 / 1533 / string |
| channel_id | MiHoYoSDK.InvokeReturn(`info_get_channel_id`) → Number.TryParseUInt32 | +114 / 12 / uint32 |
| sub_channel_id | MiHoYoSDK.InvokeReturn(`info_get_sub_channel_id`) → Number.TryParseUInt32 | +158 / 364 / uint32 |

3组输入实际到达发送入口并交由完整原生writer：123/456、FFFFFFFF/0、溢出数字/无效文本。后两类解析失败走原生失败分支，保持字段0；uint32解析本身为明确限定十进制夹具的库模型。请求实际存储快照按已声明类型交给writer，嵌套设备消息也执行子writer；不是源环境值直接构造预期Proto。

另外，旧Shell操作0/1/2按Encoding.GetString后拼接流入+A8/tag624/string，操作3流入+38/tag1105/bytes，均以独立tag与字节断言验证；**这两个字段的业务语义保持unresolved**。屏幕原生getter、Pbase来源、String.Concat/ASCII编码、托管对象/默认值、数字文化对象与stage操作为列明模型，未宣称系统环境和外部SDK实测。

累计25个字段有明确业务来源。105项工具回归通过，三组构造trace/摘要二次生成逐字节相同。共享login-constructor-observations.json；本机evidence/login-constructor-{trace,determinism}.json与logs/login-constructor-tests.log。全局B-02、密码学前段、Token完整构造、C-01/D-01/E-01/D-02仍未完成。

## 补充复现

```powershell
# GAME_PATH / JAVA_HOME 先按 Harness 核对本机portable配置
python tools/trace_client72_session.py --client $env:GAME_PATH --java-home $env:JAVA_HOME --output local/migration72/evidence/session-seed-trace.json --summary-output protocol/cn-7.2/session-seed-observations.json
python -m unittest tools.test_client72_session tools.test_client72_login_semantics tools.test_client72_metadata_values tools.test_validate_protocol72 -v
python tools/trace_client72_token_flow.py --client $env:GAME_PATH --output local/migration72/evidence/session-token-trace.json --summary-output protocol/cn-7.2/session-token-observations.json
python tools/extract_client72_enums.py --client $env:GAME_PATH --output protocol/cn-7.2/enum-observations.json
python tools/check_client72_metadata_values.py --client $env:GAME_PATH --output local/migration72/evidence/metadata-values-native-stage.json
python tools/trace_client72_request_identity.py --client $env:GAME_PATH --output local/migration72/evidence/request-identity-trace.json --summary-output protocol/cn-7.2/request-identity-observations.json
python tools/trace_client72_login_constructor.py --client $env:GAME_PATH --output local/migration72/evidence/login-constructor-trace.json --summary-output protocol/cn-7.2/login-constructor-observations.json
```

共享摘要：protocol/cn-7.2/session-seed-observations.json。完整trace、重复生成报告：evidence/session-seed-trace.json、session-seed-determinism.json；回归：logs/session-all-tests.log。原始探索留analysis/。本批只改离线工具/测试/研究记录，不需要服务端重启；新旧号、选人、入场、重登均未实机验收。

token链共享摘要为session-token-observations.json；evidence/session-token-trace.json、session-token-determinism.json及logs/session-token-tests.log保存本批复现。seed检查点本地提交0f324eb。

token链本地提交0c12f2c。请求身份新增request-identity-observations.json；evidence/request-identity-trace.json、request-identity-determinism.json，回归logs/request-identity-tests.log。后续执行线索：151213BE2→SDK `info_get_uapc`、CloudGame、PbaseMD5、设备子对象→151213FF7保护续段→151214D20发送队列；Token调用同一发送入口151220673。静态调用点不替代完整受控发送样本。

请求身份检查点本地提交70f3e5b。上述Login构造链已按限定环境执行完成，下一项转15121FF70的Token构造、151214D20发送队列及Token成功路径的密码学前段；不要求重跑此前已经一致的writer/对象图样本。

## A-04d5e / B-02c4 Token构造外段与SDK来源

`trace_client72_token_constructor`从15121FF70执行至151214D20，**此工具仍将15121DB40的结果作为不透明输入**。报告固定`completion=constructor-with-opaque-seed-boundary`及`nativeExecuted=false`，不能将本项称为完整Token构造成功。下文A-04d5g已另行执行helper密码学内部；两项合并执行和SDK真实初始化仍待验证。

继承Login所列的非云/旧Shell环境模型；额外限定SDK+c8为false，执行152758600的真实判断而不进入可选SDK预处理。实际旧Shell分支只调用操作5，ASCII GetString后的原始数据进入请求+50/tag1300；该字段业务含义仍未知。

目标SDK包装152758AC0实际解码并使用`info_get_sub_channel_id`（literal13490），TryParseUInt32结果写入TokenReq+B4，原生writer输出**tag10/uint32/sub_channel_id**。4组输入456、FFFFFFFF、溢出、无效文本通过；失败走原生默认0路径。此字段进入映射，累计26个有界业务字段来源。

六个会话来源只记录原始偏移：+78→请求+18/tag9、+F8→+40/tag2、+B0→+70/tag965、+138→+B0/tag3、+13C→+A4/tag15、+15F→+C4/tag11。验证非空/空字符串、非零/uint32上界和bool真假，不按旧schema或邻近字段猜业务名称。另验证opaque helper→+90/tag623、常量4→+98/tag1184、三个未命名全局→tag83/1206/561。这里也不将常量4命名为key_id。构造原生路径将145878FC0置1，与解密后置0的观察对应，其业务名仍未晋级。

四组原生构造到发送入口、完整writer及独立tag/type/value断言通过；133项研究/映射/盘点测试通过（前113项+盘点8项+复核7项+本项5项）。trace与摘要重复一致，普通映射校验通过、ready退出1。校验器拒绝源key/tag/type漂移、额外SDK语义声明及把不透明密码学边界改称执行成功。未修改运行源码/主Proto/配置/补丁/资源，无重启；D-02仍未验收。

```powershell
python tools/trace_client72_token_constructor.py --client $env:GAME_PATH --output local/migration72/evidence/token-constructor-trace.json --summary-output protocol/cn-7.2/token-constructor-observations.json
python -m unittest tools.test_client72_token_constructor tools.test_client72_login_constructor tools.test_validate_protocol72 -v
```

证据：`evidence/token-constructor-{trace,determinism}.json`、`logs/token-constructor-tests.log`；共享摘要`token-constructor-observations.json`。下步接发送入口的原生路由/队列，或恢复会话字段的上游SDK来源；15121DB40的Shell 0x64与响应解密/验签继续独立处理。

## A-04d5f 两请求到封包入口的原生路由

`trace_client72_send_flow`将上面的两种构造结果接入151214D20，执行至14A447E00入口。**明确选择AKFCDHJBCHD传输实现及其连接状态作为离线夹具；未执行封包函数体、加密、socket，也未证明实际客户端选择此传输。** Token构造继续保留15121DB40 opaque边界。

实际执行链为：151214D20将原请求指针存入JKGIAOABCNP信封+10→149935970按GBHBGOHOOAK接口槽4分派→AKFCDHJBCHD具体槽8/1472CCF80→1472CD100构造FBNMCCIAMBP封包对象，调用请求的原生命令getter，将word写入+22，并将同一请求指针写入+10→14A447E00入口。接口、桥接器、实现的参数分别从目标方法655015/569162/250987解码，均为typeRef165362/JKGIAOABCNP13973；不是只根据同名方法布置虚表。

合成运行类按实际槽声明布置，接口offset=4（接口槽4→具体槽8）；显式提供对象池分配、空HashSet、MemoryStream存储、时钟和信封sequence。HashSet空表判断、MemoryStream.SetLength、消息getter、封包字段赋值和时钟计算均执行原生代码。1458715C0=true选择命令getter模式，14586D06A=false选择普通发送路径；这些启动开关的真实运行状态仍需D-01验证。

每请求4组共8组通过：普通参数、sequence=FFFFFFFF且sent_ms=200000044（十六进制，超过32位）、传输禁用、发送器缺少传输包装。前两组分别实际取得24604/23172，整个请求对象字节未被修改；请求及包头再执行完整writer，token/opaque数据和独立包头wire断言一致。sequence来自显式信封输入，未宣称该路径负责递增。禁用分支分配信封但不分配封包对象；缺包装分支在分配信封前正常返回。

138项研究/盘点测试通过，trace/摘要重复一致；研究映射校验通过、ready仍拒绝。`validate_send_flow`约束请求/getter/参数类型和停止边界，不允许将本项改称真实封包或实机成功。下一项继续封包函数体/另一传输与实际SDK前置，仍无运行源码变更或部署。

```powershell
python tools/trace_client72_send_flow.py --client $env:GAME_PATH --output local/migration72/evidence/send-flow-trace.json --summary-output protocol/cn-7.2/send-flow-observations.json
python -m unittest tools.test_client72_send_flow tools.test_client72_token_constructor tools.test_validate_protocol72 -v
```

本机证据`evidence/send-flow-{trace,determinism}.json`、`logs/send-flow-tests.log`；共享摘要`send-flow-observations.json`。Token外段检查点本地提交423330e。

## A-04d5g 完整seed helper的原生验签、加密与Base64

`trace_client72_seed_crypto`从15121DB40执行到正常返回，原生代码生成/保存seed、构造密钥参数、验证公钥签名、进行RSA加密、复制到byte[]、生成Base64字符串。没有替换密码学helper或修改验签返回。SDK初始化标志145DCAEA8、Random.NextDouble、整数置换表、OS/TLS/堆与运行时常量是明确夹具；不代表客户端已正确初始化或可连接服务器。

原生Shell操作100的参数块为64字节：+0/+8输入指针/长度，+10/+18输出指针/长度，+20/+28公钥指针/长度，+30/+38签名指针/长度。原始公钥缓冲272字节，实际268字节前补`30 82`成为270字节PKCS#1 DER；256字节附属块是该公钥的签名，不能误称seed密文。14078BAB0接收DER、签名与原生构造的根公钥PEM，140574A9A处观察真实返回。原生成功和Python整数RSA/完整PKCS#1 v1.5 SHA-256 DigestInfo比较一致，篡改任一签名或公钥字节均失败。

公钥DER SHA-256为`7427ee11ef690cfd43a001758eabc3604042b62f78a31cb21f4afe6d715d629e`，签名为`520da33620e8c69eff6b6b3d40fd699a4642583e3e33cd1b53aa19150633cf4d`；原始材料只留忽略目录。1406595F0接收8字节大端seed，140659AC0观察原生PKCS#1 v1.5编码块：`00 02`、245个非零填充字节、`00`和seed。用独立Python整数幂运算得到的256字节密文与原生结果全部相同，原生Convert输出344字符并与Python Base64双向核对。

8组真实执行覆盖：未初始化；seed为0、800、8000000000000000、FFFFFFFFFFFFF800（十六进制）；相同seed改变熵输入；篡改签名；篡改公钥。5个有效输入成功，未初始化返回-2，两个验签失败返回-7，失败均返回空字符串且没有进入加密/请求熵。不同熵生成不同密文。native Path hash、所有夹具和共享摘要重复一致。3个独立oracle测试检查完整签名填充、消息变化、非零加密填充、64位seed、密文变化、截断与非法DER；连同既有研究/映射/盘点测试共146项通过。

运行时模型包括：CRT/Unity/GC分配、跟踪释放、memcpy/memset；单线程临界区/TLS、模块句柄和onexit登记；固定进程/线程/时间；显式BCryptGenRandom字节。该API以NULL handle、flag=2请求系统随机源，夹具按[Microsoft接口定义](https://learn.microsoft.com/en-us/windows/win32/api/bcrypt/nf-bcrypt-bcryptgenrandom)写缓冲并返回STATUS_SUCCESS，未测试真实随机性。原生RNG混合、RSA、SHA-256和填充代码继续执行。Marshal.Copy的klass+B8元素宽度与托管byte[]、String.Empty/空数组、标准Convert字母表及字符串分配亦明确提供；没有宣称完整CLR初始化执行。

此前Token外段已将helper返回值保存至请求+90并由writer写tag623/string；本项完成helper本身，两者尚为分开执行的证据。该字段可描述为“Base64 RSA加密的客户端seed来源”，但本批未自动套用旧字段业务名，也未晋级其他会话字段。SDK真实初始化入口、响应seed解密/验签、完整Token构造合并、封包/真实网络仍待验证；D-02未验收。旧补丁强制验签成功的钩子没有启用，也不能用作本项证据。

```powershell
python tools/trace_client72_seed_crypto.py --client $env:GAME_PATH --output local/migration72/evidence/seed-crypto-trace.json --summary-output protocol/cn-7.2/seed-crypto-observations.json
python -m unittest tools.test_client72_seed_crypto -v
```

产物：`evidence/seed-crypto-{trace,determinism}.json`、`logs/seed-crypto-{run,tests}.log`、共享`seed-crypto-observations.json`。本批只改离线工具/测试/研究摘要，无运行代码、主Proto、配置、补丁或资源变更，无需重启。下一项将完整helper接入Token构造，再追踪响应密码学前段和SDK初始化。

## A-04d5h / B-02c5 完整Token构造与client_rand_key

`trace_client72_token_complete`现在从15121FF70开始，在同一原生执行中调用完整15121DB40，观察返回地址151220057及其原始返回指针，确认该指针直接保存到Token请求+90，再到151214D20发送入口。此工具移除了opaque seed结果模型；其他SDK、Shell操作5、OS、托管和初始化夹具继续明确保留。后续原生发送链把同一请求交给14A447E00，实际getter为24604；封包函数体仍未执行。

writer输出JFCLDBKLKDF/tag623/string与原生Base64结果一致。其业务语义为“RSA/PKCS#1加密的8字节大端客户端seed，经Base64传输”，与现有HandlerGetPlayerTokenReq的`clientRandKey`消费契约（Base64解码→RSA/PKCS1解密→ByteBuffer大端getLong）对应。因此字段映射命名为`client_rand_key`，这是业务契约名称匹配，并非声称客户端混淆名已解密。三层表累计27个字段有来源；literal4/tag1184尚不命名key_id，其他会话字段也不随之晋级。

5组贯通：seed为0、8000000000000000、FFFFFFFFFFFFF800，未初始化，篡改签名。前三组原生验签/独立RSA一致、tag623长度344；后两组helper分别返回-2/-7后生成空字符串，外层仍到发送/封包入口，tag623按proto默认空值不输出。这是实际客户端代码行为；不能用发送成功替代seed成功或服务器接受。全部保持24604、原请求指针和SDK tag10=456。补充3项映射测试拒绝错误命令、wire tag、opaque冒充原生、实机冒称、遗漏失败及把失败称为不发送；149项回归通过，独立seed工具重构后8组产物未变，完整Token工具重复一致。

```powershell
python tools/trace_client72_token_complete.py --client $env:GAME_PATH --output local/migration72/evidence/token-complete-trace.json --summary-output protocol/cn-7.2/token-complete-observations.json
python -m unittest tools.test_client72_token_complete tools.test_client72_seed_crypto tools.test_validate_protocol72 -v
```

证据`evidence/token-complete-{trace,determinism}.json`、`logs/token-complete-{run,tests}.log`，共享`token-complete-observations.json`。旧token-constructor/send-flow表保留其当时opaque实验范围，新表记录消除此边界的实际执行。研究校验通过、ready仍拒绝；无运行文件变更或重启。A-04d5g本地提交823762e。下一项为响应seed解密/验签、真实SDK初始化或封包函数体，D-02仍未验收。

## 响应密码学接续探索（未正式验收）

`analysis/probe_response_crypto.py`从15A4DE707进入响应前段，显式提供响应+98/tag477和+88/tag208的Base64字符串。前者送Shell操作101，参数含1192字节内置私钥及256字节私钥签名；后者送操作103，参与响应数据签名验证。内置私钥签名通过真实原生校验，也通过独立RSA/SHA-256计算；其公钥部分只与仓库game_keys/4_Pub.der匹配。独立公钥运算生成的密文由原生解密为预期8字节非零输入FEDCBA9876543210，返回0。

随后SHA256Managed的Initialize/HashCore/HashFinal、Array.Clear及HashAlgorithm.Dispose执行，摘要与hashlib一致。为此提供类型/虚表、byte/uint数组布局、目标metadata中唯一匹配的SHA常量表、数组Clone/类型检查及GC.SuppressFinalize登记边界；没有替换摘要运算。操作103构造的公钥及其签名，与Token请求seed加密所用的272/256字节缓冲完全相同；真实公钥签名验证通过，无效全零响应签名返回-7，结果0，控制流停在15A4E0645错误日志调用前，未进入15A4E064F密钥安装后段。

完整指令观察下解密执行45,744,307条，已得到正确明文。随后为探索速度仅省略140580000..140780000密码学库区域的逐指令Python回调，CPU指令仍执行，结果一致；正式工具应补上明确的块级轨迹，不能把省略后的计数当总指令数。私钥/原始密钥材料不提交，只存忽略目录；摘要`evidence/response-crypto-exploration.json`。

当前仓库SigningKey与目标公钥不匹配，不能构造“目标原始信任配置下的有效服务器签名”或将旧补丁强制返回成功作为证据。下一项先正式化已成功的解密/摘要/拒绝路径，再研究本地服务端一致的密钥、签名与信任配置；任何替换都须实际通过验证算法。SDK warmup/真实初始化、完整Handler和实机仍未知。此探索未晋级响应字段/整体任务，149项测试结果仍仅对应上文已提交的工具。

## A-04d5i / B-02c6 响应wire、真实验签与会话密钥

正式工具`trace_client72_response_crypto`以独立tag477和208的wire输入真实TokenRsp reader，接入15A4DE707至15A4E0D4B。原生Base64、RSA私钥验签/解密、SHA256Managed、响应签名验证、seed XOR和两阶段MT64均实际执行；成功后得到4096字节密钥并清除145878FC0。托管字符串reader提供真实UTF-16存储，字段保存/分派仍由目标reader执行。两个字段业务契约据此确定为+98/CGDONMBOALA/tag477 `server_rand_key`及+88/GKBJLBAKMCJ/tag208 `sign`，三层映射累计29字段。

有效响应使用**明确的本地签名信任输入**：从本仓库SigningKey读取离线夹具，为原生内置解密私钥DER及本地服务器公钥DER分别签名，在各操作输入处提供签名/公钥，并为原生公钥验签器提供对应根公钥PEM。原生验签返回值与结果字段均未覆盖，两次密钥验签及响应数据验签全部执行，并与独立RSA验证一致。原始目标私钥不替换，其公钥部分独立匹配仓库4_Pub.der；原始请求加密/响应验签公钥仍交叉核对为同一DER。该夹具证明密码学/字段数据流及一致信任输入下的行为，不证明原始服务器签名、真实SDK或补丁已加载。

8组覆盖：本地零seed、高64位seed、9字节尾随输入；原始信任下无效响应签名；本地无效响应签名、内置私钥签名错误、无效密文；合法签名但7字节短seed。前3组完整结果与现有Java MT64逐字节一致；原生SHA-256均与hashlib一致。签名/密文错误到15A4E0645拒绝分支，短seed在159CA4EA0拒绝；均不安装密钥、不清标志，保存的client seed也不变。短seed用例说明验签成功仍不能替代后续长度验收。

密码学库140580000..140780000改用原生基本块轨迹，其他区域保持逐指令轨迹；两者单独记录数量与SHA-256，绝不把块数或区域指令数当总指令数。指令/时间预算和系统指令拦截仍覆盖整个执行，RSA计算没有被模型替换。新增模型仅为SHA类/虚槽/数组存储、目标metadata常量数据、byte[] Clone/类型判断和GC.SuppressFinalize边界；原生摘要与Array.Clear/Dispose继续执行。8组全套trace/摘要二次运行逐字节一致；155项工具/映射/盘点回归通过，普通校验通过、ready仍退出1。

```powershell
python tools/trace_client72_response_crypto.py --client $env:GAME_PATH --java-home $env:JAVA_HOME --output local/migration72/evidence/response-crypto-trace.json --summary-output protocol/cn-7.2/response-crypto-observations.json
python -m unittest tools.test_client72_response_crypto tools.test_client72_rsa_fixture tools.test_validate_protocol72 -v
```

默认签名夹具文件为仓库`src/main/resources/keys/SigningKey.der`，仅记录输入哈希；私钥及目标原始材料不写入共享产物。证据为`evidence/response-crypto-{trace,determinism}.json`、`logs/response-crypto-{run,tests}.log`和共享`response-crypto-observations.json`。仅离线工具/测试/研究文档变更，没有运行代码/Proto/补丁/资源变更，无需重启。下一步追踪SDK warmup/完整Handler前缀及实际本地信任配置，D-02仍未验收。

## A-04d5j / B-02c7 Handler入口至UID/token保存

`trace_client72_handler_core`从真实1507BE0E0入口执行至15A4E0D9A，连续覆盖stage9、ByteString解包、warmup分支、上述原生密码学和seed安装、UID setter及会话token保存。此处不再从解密边界或token store中段开始；仍停在后续robot-check/version/UI动作之前，不能称整个Handler完成。4组均使用明确本地签名信任输入和非云环境，密钥与Java对照一致。

独立输入tag9/uint32→响应PJAJDHBBMGB+ D4→15217AA90→会话+168，随后目标cctor153D333E0把literal57480 `__LastUid__`保存到GLOBAL+2B708，153D34300经NumberFormatter.NumberToString和PlayerPrefs写入。NumberFormatter方法3829的三个参数独立解析为string、uint32、IFormatProvider；PlayerPrefs命名wrapper实际跳到141454050/140973BC0。默认十进制格式和PlayerPrefs内存记录为明确模型，未写宿主注册表/客户端存档。0、1、80000000、FFFFFFFF（十六进制）均保持uint32意义；因此映射`uid → tag9`，累计30字段。响应token原指针同时保存到session+98，空/非空均验证。

目标运行flag-list是否含5714决定Old/New WarmupSwitch。旧分支仅记录原生日志后继续；新分支实际调用操作202，参数块16字节。空byte[]返回-2，当前8字节fixture返回-1，Handler不检查该返回值而继续密码学与会话保存。这里既没有替换warmup调用，也没有证明新warmup成功或SDK真实初始化；tag838字节的业务含义仍未知。日志/stage只做捕获，empty cache的原生Clear路径及非云判断继续执行。

4组原生样本、157项回归通过，trace/摘要重复逐字节一致；为复用拆出的密钥发现/执行入口也重跑原有8组response工具，产物与caa7bea相同。研究校验通过，ready仍退出1。新映射守卫拒绝UID/账号ID互换、错误tag/有符号类型/保存位置，以及把-2/-1改称warmup成功或整个Handler已执行。

```powershell
python tools/trace_client72_handler_core.py --client $env:GAME_PATH --java-home $env:JAVA_HOME --output local/migration72/evidence/handler-core-trace.json --summary-output protocol/cn-7.2/handler-core-observations.json
python -m unittest tools.test_client72_handler_core tools.test_client72_response_crypto tools.test_validate_protocol72 -v
```

证据`evidence/handler-core-{trace,determinism}.json`、`logs/handler-core-{run,tests}.log`及共享`handler-core-observations.json`。后续可继续15A4E0D9A后的动作，或从SDK ComboUserModel/LoginResultModel恢复TokenReq其余会话字段；不得用相似字段名直接配对。D-02、真实SDK/补丁/PlayerPrefs和后续Login调用者前缀仍未实机验收，无运行文件变更或重启。

## A-04d5k / B-02c8 SDK认证来源到Token wire

`trace_client72_auth_sources`执行14F1917D0至14F191E18的原生回调。目标日志自身标记为`LoginMainPageContext OnSDKLoginCB`。JSON解析/容器/字符串值、SDK返回字符串是明确模型；SimpleJSON.JSONNode的get_AsInt/get_AsUInt/get_AsBool原生getter、必需字段检查、会话写入继续执行。缺键类型从实际usage row701114、kind1缓存和类型引用解码为JSONLazyCreator9000，不能混用SDK另一套JSON库的类。

| 业务字段 | 原始来源 | 会话偏移 | Token对象偏移 / tag / 类型 |
| --- | --- | --- | --- |
| account_uid | data.open_id | +78 | +18 / 9 / string |
| account_token | data.combo_token | +F8 | +40 / 2 / string |
| channel_id | SDK info_get_channel_id，UInt32解析 | +138 | +B0 / 3 / uint32 |
| account_type | 可选data.account_type | +13C | +A4 / 15 / uint32 |
| is_guest | 可选data.guest | +15F | +C4 / 11 / bool |

成功来源片段之后，在同一会话存储上执行真实seed helper和完整Token构造，原生getter仍为24604，完整writer输出与独立wire解析逐字段比较。来源片段与构造片段分段执行，未宣称回调尾段/UI到发送调度已经连通。account_uid的高数字文本`9007199254740993`保持字符串，不转成玩家UID或浮点数。guest先被原生代码清零；缺少account_type时保留原值，本组夹具初始值为0。

9组覆盖非零、uint32最大、可选字段缺失、account_type溢出、错误ret、缺open_id、空token、无效渠道和渠道溢出。前4组进入构造，account_type溢出按原生getter回落为0；后5组到明确拒绝/返回边界且凭据未被改写。未执行完整JSON解析/类型强转、实际SDK认证或网络。会话+B0→请求tag965等其他来源仍未知，不能因相似字段名晋级。

160项研究工具/映射/资源审计回归通过，9组真实目标代码样本及共享摘要重复逐字节一致。为复用拆出的`construct_native_token`另重跑原5组完整Token，trace和摘要与原产物完全相同。研究校验通过，ready仍拒绝；五字段进入映射，累计35字段。

```powershell
python tools/trace_client72_auth_sources.py --client $env:GAME_PATH --output local/migration72/evidence/auth-sources-trace.json --summary-output protocol/cn-7.2/auth-sources-observations.json
python -m unittest tools.test_client72_auth_sources tools.test_client72_token_complete tools.test_validate_protocol72 -v
```

证据`evidence/auth-sources-{trace,determinism}.json`、`logs/auth-sources-{focused-tests,tests}.log`，共享`auth-sources-observations.json`。仅离线工具、测试和文档变更，无运行部署。上一Handler核心提交b0ca919；本项收口后按用户要求暂停，交接见仓库根HANDOFF.md/HANDOFF2.md。恢复时继续回调尾段、会话+B0来源或封包体；D-02仍未完成。

## A-04d5l 认证可选字段段落（已恢复推进）

2026-11-05用户已恢复研发，旧暂停记录仅为历史。`trace_client72_auth_tail`从14F1917D0连续执行至14F192325，复用已声明JSON/SDK模型；两个setter和JSON字符串转换1471685A0实际执行。六个来源：data.allow_community→session+120、allow_ugc→+124、allow_pay→+118、country→+E0、age_group→+184、lrsag→+16C。除country/string外均为int32，不因allow名称视为bool。community/ugc额外全局标志仅在值恰好1时为真。

5组为非零、零、signed边界、全缺省、Int32溢出；会话先填非零以证实缺字段清零，country缺省为空指针。两次trace及摘要逐字节一致，27项聚焦回归通过。上述段落不改session+B0；**不新增协议语义**，累计仍35字段。UI/调度/实际SDK及D-02未验；工具不触碰运行服务、数据库或客户端进程，无重启要求。

```powershell
python tools/trace_client72_auth_tail.py --client $env:GAME_PATH --output local/migration72/evidence/auth-tail-trace.json --summary-output protocol/cn-7.2/auth-tail-observations.json
python -m unittest tools.test_client72_auth_tail tools.test_client72_auth_sources tools.test_client72_token_complete tools.test_validate_protocol72 -v
```

证据`evidence/auth-tail-{trace,determinism}.json`、`logs/auth-tail-tests.log`。接续UI边界14F192325；B0的另一来源探索已定位151212949/15122952D段落，源对象+20→session+B0，不能把这些原始偏移直接命名为旧协议字段。

## A-04d5m 分区候选源到session+B0与Token tag965

`trace_client72_dispatch_token`固定三份目标哈希，执行30677/IBOFGKFAEPI的真实reader153A598A0，独立wire字节`72 <length> <ASCII>`写入DGHGFAELIDO+20；随后分段运行下表两个实际写入片段，再从同一session执行真实seed helper/Token构造、getter24604及writer。**30677与实际协程上游解析结果的类型关联仍为candidate；tag965业务语义仍unknown。** 夹具明确把30677对象供给post-parse片段，不能把这一供给动作当作已经证明的运行时调用关系。

| 入口 | 正常/缺session终点 | 缺manager终点 | 缺source终点 | 写入 |
| --- | --- | --- | --- | --- |
| 151212949 | 151212983 | 151212E11 | 151212E7B | 15121297C |
| 15122952D | 151229567 | 1512299DF | 151229A49 | 151229560 |

两处均从`[rbx]+20`读字符串，经GLOBAL+9E80的manager+180定位session，写+B0；Token构造再到请求+70/tag965。独立Token key字节`AA 3C`，测试不从观察表生成期望key。每路径4个源值（非零、空、高数字文本9007199254740993、另一不同字符串）及3个缺对象边界，共14组。非空源在session/request保持原指针，空源在session保留而请求使用默认空字符串对象，writer省略tag965；缺session正常跳过写入，缺manager/source到原生错误调用前停止，均不改原session、不进入构造。

两次trace/共享摘要逐字节一致；30项聚焦回归、166项研究工具/映射/资源回归通过。摘要守卫拒绝将candidate/unknown改verified、把整个dispatch/实机标成功，亦拒绝把mappings中的unknown_field_965晋级。研究校验退出0、ready退出1；累计业务字段仍35。无运行代码/Proto/配置/补丁/资源修改，不需重启，不部署猜测协议。

```powershell
python tools/trace_client72_dispatch_token.py --client $env:GAME_PATH --output local/migration72/evidence/dispatch-token-trace.json --summary-output protocol/cn-7.2/dispatch-token-observations.json
python -m unittest tools.test_client72_dispatch_token tools.test_client72_auth_tail tools.test_client72_auth_sources tools.test_client72_token_complete tools.test_validate_protocol72 -v
```

证据`evidence/dispatch-token-{trace,determinism,mapping-check,mapping-ready}.json`、`logs/dispatch-token-tests.log`与`logs/auth-tail-dispatch-regression.log`。新工具明确限定ASCII/string，不能扩展宣称全部HTTP响应、嵌套或网络数据覆盖。

### 下一段探索与避坑

- 固定目标literal34429/34433/34435为RegionDispatch相关日志；另一入口151210540接受string及Action<IENBACJFBGI>，原生实际读取JSON content/sign。忽略脚本`analysis/probe_dispatch_prefix.py`已运行到受控Base64/原生SDK后段，尚未完成合法响应解密/反序列化；不是正式验收。
- 单凭字段布局符合、RegionDispatch日志、或旧QueryCurrRegionHttpRsp的tag14名称不足以晋级业务语义。需贯通真实解析调用的泛型/类型及有效响应输入。保留`analysis/b0-writes.json`扫描线索，1644个字节形状命中不是1644个真实session写入；按目标反汇编/来源逐项排除。
- 前缀探索目前在15A4E2F49前的140514EE0请求未知元素class=0，属于夹具运行时元数据尚未恢复，不证明目标客户端缺陷。不得用通用任意数组分配/强制成功来消除此边界。`probe_dispatch_reader.py`的3/4/5嵌套扫描未完成也未列为通过。
- 认证完整尾段仍从14F192325开始；当前A-04d5l只清除了可选字段段落缺口，UI/调度/实际SDK仍待验。D-02还需B-02/B-03/C-01/D-01/E-01完整前置及新旧账号实机序列。

## A-04d5n1 分区JSON到实际响应reader

`trace_client72_dispatch_prefix`从151210540真实入口执行至151211BA1。JSON容器/SDK环境为已声明夹具；原生检查content/sign、Base64、操作101私钥验签/解密、SHA256Managed、操作103公钥验签/数据签名验证，随后实际分配30677/IBOFGKFAEPI并调用147EA0940 byte[] merge及其slot19 reader153A598A0。解析对象原指针保存到`[rbp+A8]`指向的槽中，供后续原生分区处理读取。这里不再手工将30677放入post-parse槽；A-04d5m保留其当时两片段的candidate范围，不能将新结果扩展到未执行的第二协程。

目标Clone153A597F0实际读取1458C3CC0，并调用同类型CopyFrom153A594A0；工具检查这两条原始指令及声明slot19，防止按相似布局替换源类型。byte[] merge本体原生执行，分配的流存储/slot19元数据和ASCII wire底层helper为明确模型；不宣称完整托管运行时ABI或所有嵌套字段。

成功输入的`content`是独立RSA/PKCS1编码的tag14 wire；使用仓库4_Pub.der公钥，原生私钥及其原始签名信任保持不变。`sign`由仓库SigningKey独立签署明文。**只在操作103的验证器输入处提供一致的本地服务器公钥、该公钥签名及根公钥**，真实验签算法/结果/分支不替换。两次密钥验签均与独立RSA oracle比较；原生SHA256与hashlib一致，解密后byte[]与独立明文逐字节一致，实际reader的+20字段与tag14字符串一致。该结果提供D-01信任配置研究输入，不代表实际补丁已安装或原始服务器签名通过。

六组：非空、空字符串、高数字文本9007199254740993三个本地信任成功；篡改数据签名、原始信任下不匹配签名、缺content三个拒绝。前两种拒绝实际到`[RegionDispatch] DecryptRegionDispatch Decrypt VerifyHash Fail`；缺content到`... inputJson Content is Null`。全部拒绝未调用响应reader。输入没有RegionInfo等其余字段，所以成功只表示到post-parse边界，不代表完整分区流程可以成功。

两次trace/摘要逐字节一致；33聚焦、169研究回归通过，研究校验退出0、ready退出1。对未知的SDK返回寄存器仅记录完整RAX，不将其猜为有符号错误码。tag14/tag965业务语义仍未知，登录语义35字段不变。

```powershell
python tools/trace_client72_dispatch_prefix.py --client $env:GAME_PATH --output local/migration72/evidence/dispatch-prefix-trace.json --summary-output protocol/cn-7.2/dispatch-prefix-observations.json
python -m unittest tools.test_client72_dispatch_prefix tools.test_client72_dispatch_token tools.test_client72_auth_tail tools.test_client72_auth_sources tools.test_client72_token_complete tools.test_validate_protocol72 -v
```

证据`evidence/dispatch-prefix-{trace,determinism,mapping-check,mapping-ready}.json`，`logs/dispatch-prefix-{focused-tests,regression}.log`；共享摘要不含私钥/机器路径/实际账号。上节class=0缺口已定位为SHA256Managed/uint数组运行时状态遗漏并由既有ResponseCrypto72模型解决，不是客户端缺陷。

下一项A-04d5n/B-02c9：从151211BA1恢复RegionInfo/配置消费至B0的连续路径，验证第二协程的源类型，再寻找业务名称的独立来源。认证UI/调度14F192325之后、封包体和D-01实际加载仍未验；这些缺口不能用本项通过代替。仅离线工具/测试/研究文档变化，无运行代码/Proto/配置/补丁/资源变更，无需重启；D-02尚未完成。


## A-04d5n2 分区嵌套读取到配置处理入口

`trace_client72_dispatch_post`在同次执行中从151210540经过已验证JSON/原生密码学，继续越过151211BA1，到151204AC0配置函数入口；中间不重置CPU、不替换成功分支。新增Region类型67961/IENBACJFBGI由真实父reader分配，Clone149BB5950读取1458DEE48和调用149BB5A00的原始指令、slot19/149BB4EB0分别核对。托管分配/接口表是明确模型；ReadMessage1474D7E60、PushLimit/PopLimit及具体reader原生执行。只覆盖选定字段，不声称完整Region闭包。

独立固定wire包含响应tag3/message→+48；嵌套tag2/uint32→+13C、tag13/string→+30；响应tag12/bytes→+18和tag11/bytes→+40经原生ByteString.ToByteArray147CF3070传入配置函数rdx/r8，r9d=1，receiver不变。三层语义没有晋级；tag14/tag965仍未知。配置入口到B0尚未贯通。

8组样本：非零、空Region、UInt32最大、重复Region合并、缺Region、负int32、嵌套长度截断、嵌套varint截断。重复字段两次执行同一reader，只分配一个对象并保留两次输入值；缺Region/负返回值在151211BDD进入错误处理分支（没有执行整个错误UI，不能称已完成错误恢复）。长度截断实际进入原生truncated-message；varint截断由显式bounded-wire-helper拒绝，两者区分记录。所有样本session+B0未改变。

成功仍使用n1明确的本地签名信任输入，真实验签与RSA oracle一致；未安装补丁、没有HTTP/SDK/客户端验收。8组原生样本及173项研究回归通过，trace/摘要两次逐字节一致；研究校验退出0、ready退出1。错误样本允许解析中产生部分对象，不把这些对象当作成功或提交给后处理。

```powershell
python tools/trace_client72_dispatch_post.py --client $env:GAME_PATH --output local/migration72/evidence/dispatch-post-trace.json --summary-output protocol/cn-7.2/dispatch-post-observations.json
python -m unittest tools.test_client72_dispatch_post tools.test_client72_dispatch_prefix tools.test_client72_dispatch_token tools.test_validate_protocol72 -v
```

证据`evidence/dispatch-post-{trace,trace-repeat,determinism,mapping-check,mapping-ready}.json`、`logs/dispatch-post-{run,repeat,regression}.log`。只改离线研究工具/测试/摘要/文档，不需部署或重启。下一步执行151204AC0内部的配置解码/解析，继而回到151211DD5；需使用有效配置样本，不能把本批任意bytes参数样本当作有效配置JSON。


## A-04d5n3 两段配置消费、原生事件与Region全局写入

`trace_client72_dispatch_config`在同次执行中从151210540经原生密码学/reader、两次151204AC0配置消费，到1512124B9；未把配置函数或事件派发替换成成功回调。该终点在下一SDK前置检查之前，尚未执行15275F080和B0写入。第一/第二调用的mode分别1/2，字节来源分别响应tag12/tag13，key/tag11明确为空；**非空key解码/XOR未覆盖**。

显式初始化输入包括Encoding.utf8Encoding缓存、GlobalVars.SafeOptionalResourceCategories与SecureOptionalResourcePolicyPlugins的固定容量List<int>、类初始化标志、无监听器的事件manager。元数据声明核对这三处命名静态字段。JSON对象/数组和mode装箱仍为模型；原生JSON scalar getter、原生List清空/插入、配置两函数146D3F6E0/147517860、事件分发1532D6720→1532ECA40→空字典查找1565EE8D0真实执行。无注册监听器仅是夹具，不代表实际SDK/UI回调已运行；可选GLOBAL+E0F0保持缺省，目标分支真实跳过。

固定wire独立使用tag12/tag13配置字节、tag3的Region、tag14高数字文本；Region tag13/string→+30→GLOBAL+6240及+6078，tag14/uint32→+140→14586CFB4，最终均与独立值一致。配置可选键来自目标literal，包括downloadThreadNum、showexception及两个列表键；这些配置键不用于推断tag965的业务语义。

6组：空配置、第一段非零设置/列表、第二段Int32最大/负列表、Int32最小与溢出、第一段坏JSON、第二段坏JSON。容器预填123，实际缺键后清空；列表包含1、300、-1、2147483647；Int32溢出由真实scalar getter配合已声明解析helper返回0。两次原生配置返回分别在151211DD5/151211E2C观察；事件ID919携带对应mode。返回指令只设置DIL低字节再复制EDI，**不把完整EAX当整数1**；报告保留nativeReturnRAX并断言returnAL=1。最初全EAX断言失败已据原始指令修正，字段/list断言未删减。

坏JSON由明确的JSON容器helper拒绝，不称原生JSON解析异常；第一段失败无完成事件，第二段失败仅第一段事件，均不进入Region后处理、不改session+B0。6原生样本及177研究回归通过，两次trace/摘要逐字节一致。研究校验0，ready1，业务映射仍35字段；D-02实机仍未验。

```powershell
python tools/trace_client72_dispatch_config.py --client $env:GAME_PATH --output local/migration72/evidence/dispatch-config-trace.json --summary-output protocol/cn-7.2/dispatch-config-observations.json
python -m unittest tools.test_client72_dispatch_config tools.test_client72_dispatch_post tools.test_client72_dispatch_prefix tools.test_validate_protocol72 -v
```

证据`evidence/dispatch-config-{trace,trace-repeat,determinism,mapping-check,mapping-ready}.json`和`logs/dispatch-config-{run,repeat,regression}.log`。仅离线工具/研究摘要/测试/文档变更，无运行构建部署或重启。

下一步：1512124B9读取GLOBAL+F2E0并要求非空，然后执行15275F080（SDK类型14237的方法）；不能跳过该调用接B0。探索中直接继续遇到缺singleton的原生NullReference分支，停在151212D8E，应恢复声明和初始化。15275F080会构造MiHoYo.SDK.JSONObject/JSONString/JSONBool、StringBuilder，调用SDK入口；其完整行为未验。候选type cell查询保留在忽略的analysis/probe_sdk_type_cells.py，固定目标usage行699017/699018/700517/700519/698608分别关联JSONObject4354/JSONNode4346/JSONString4356/JSONBool4358/StringBuilder452；不能将这些静态绑定当实际SDK成功。


## A-04d5n4 第一分区入口连续到B0及Token原始wire

`trace_client72_dispatch_session`从151210540在同次CPU执行中完成JSON/原生密码学、30677 reader及Region、两段配置、15275F080 SDK更新包装、提示分支，再到151212983。原生15121297C确实把解析对象+20原指针写入session+B0；成功样本随后复用同一密码学运行时执行真实Token seed helper/构造/getter24604/writer，独立key字节AA 3C证明同值进入tag965。**只闭合第一入口原始关联，tag14/tag965业务名称仍未知，第二协程和dispatch后续尾段未验。**

SDK模型`client72_sdk_update`区分MiHoYo.SDK JSON与既有SimpleJSON；固定usage行核对JSONObject4354、JSONString4356、JSONBool4358、StringBuilder452、语言枚举77368。JSONObject.Add/WriteToStringBuilder、StringBuilder.ctor/ToString的声明参数和slot均核对，容器/字符串桥接/池/字典存储为显式模型。SDK包装15275F080、ConfigChannel匹配、偏好查询的空backend路径、1527571F0/15275F990、语言传递、字典原生枚举与清空、原生导出包装实际执行。

第一份SDK输出包含app_id/app_version/area/compile_type/use_device_name；第二份含CPU/GPU/Language。app/version/CPU/GPU/channel/language均为已声明离线输入，未读取机器设备或真实账号。原生SDK导出表14572BC18明确未初始化，140AC4160/140AC4180实际按目标代码返回，**不代表真实SDK接收或初始化成功**。GLOBAL+F2E0仅为该非IFix路径所测的presence夹具，不宣称执行singleton初始化。

提示分支没有通过加feature flag跳过。`client72_dispatch_notice`按usage698601恢复Object[]（并非String[]）及String/System.Object元数据，原生assignability、线程ID比较、1471623F0→147161FA0真实执行；目标literal为`SDKServer - OnConnectRegionDispatch: GameBiz==Null`，code5000。声明OS线程1/主线程1、日志sink未初始化，原生按缺sink返回；不代表真实界面回调/完整GameBiz初始化。不得把该离线状态组合称作可启动的真实客户端环境。

6组为高数字文本9007199254740993、空字符串、不同源值/语言、缺session、缺singleton、坏配置JSON。前三组连续store并到Token；空源在会话保留原指针，Token选择默认空对象且省略tag965。缺session真实跳过store，缺singleton在151212D8E的原生错误调用前停止，坏JSON由已声明helper拒绝，均不产生Token。SDK区域覆盖cn/os，语言覆盖None/Sc/It；字典非空枚举后实际清空再归还。

`construct_native_token`新增可选已有seed运行时参数，避免重复映射同一OS内存。复用时保留内存/会话数据，增加RSA明文和padding两个精确观测点；原response性能模型按block追踪该区间，所以缺这两个点时最初oracle采不到plain，不能误判算法失败。真实验签/加密结果不替换。原Token5组重跑trace/摘要与修改前逐字节一致。空源另按原生返回对象读取UTF-16值后注册观察，不将默认字符串强写为期望值。

6原生样本、182研究回归通过，两次trace/摘要逐字节一致；研究校验0、ready1，业务35字段不变。原有账号/经济/存档运行代码未改；仅离线工具/测试/摘要/文档修改，无构建部署/重启。

```powershell
python tools/trace_client72_dispatch_session.py --client $env:GAME_PATH --output local/migration72/evidence/dispatch-session-trace.json --summary-output protocol/cn-7.2/dispatch-session-observations.json
python -m unittest tools.test_client72_dispatch_session tools.test_client72_dispatch_config tools.test_client72_token_complete tools.test_validate_protocol72 -v
```

证据`evidence/dispatch-session-{trace,trace-repeat,determinism,mapping-check,mapping-ready}.json`、`logs/dispatch-session-{run,repeat,regression}.log`；原Token回归`evidence/token-complete-after-dispatch-session{-summary,-check}.json`。下一项第二协程：元数据类型23192/NCIDAOJKFEI，MoveNext151226CD0，实例+50为状态，+20为DHABIHPMOAH接收者，+18为23181闭包，+38为Action<IENBACJFBGI>。尚未执行其实际解密/解析来源，不把第一入口证据直接移过去；tag965独立业务来源亦待恢复。
