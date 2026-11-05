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
- 响应+98/+88进入Base64、原生密钥处理和摘要/校验区域；尚未通过完整独立密文与错误签名夹具。上面的解密后片段不能验证前段解密或验签。
- 响应+68写会话+98，1507C2211读取同一会话成员传给151213730；后者写请求+D8。该链已由下一节的独立wire与分段原生执行验证；未据此宣称完整Handler执行。
- Login构造保护续段已越过151213AC6；148A22550跳1421C1F40（目标原生ret），后续需合成1458971C0类初始化状态并追踪下一真实调用。未知helper继续失败，不默认返回成功。

## A-04d5d client seed来源与保存

Token字符串构造15121DB40首先调用152F1B4D0。目标System.Random类型280的槽8为NextDouble；原生代码调用此槽后乘1422404F0的double常量`2^64`，执行uint64转换，再经14CB58BD0编码，将同一16字节同时返回并存到14587D740。该存储正是A-04d5a后续XOR的输入。

SessionRuntime72新增generate_client_seed：仅提供受控NextDouble结果，缩放/转换/整数编码/全局保存均执行原生代码，并用独立表达式和真实decoder核对。两套置换表各5组输入（0、2^-53、0.5、0.123456789、1的前一个double）共10组通过；0.5得到8000000000000000，最高输入得到FFFFFFFFFFFFF800。工具拒绝NaN/无穷/负数/1及非数值；没有把这组受控随机值当成客户端真实随机性测试。

此前12组4096字节Java对照与6组短输入拒绝同时重新通过，产物重复一致；工具回归106项通过。证据仍为session-seed-trace/observations/determinism，新增clientSeedSourceFixtures；日志client-seed-source-tests.log。

15121DB40后续保护路径经已核对的原生ret thunk和堆分配后进入Shell操作0x64；当前停于其栈探测/SDK内部状态前置，尚未恢复加密字符串。该helper没有被模型化为默认成功。原始探索probe_token_seed.py及token-seed-path.json在忽略的analysis/，不能据此把Token完整构造、密文或签名标记通过。

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
