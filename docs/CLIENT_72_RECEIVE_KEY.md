# 7.2同会话状态的响应收帧与key选择（F-02c）

本项把既有Token响应Handler/密码学/真实Login封帧的运行状态，继续用于真实LoginRsp接收回调、消息工厂查找、reader及原生分发。**11组离线样本证明：Token Handler安装session key并写selector=0后，接收20835读取同一session key；用dispatch key加密的20835在帧起点校验返回6，不能进入reader/队列/Handler。** 不据此声称真实线程调度、完整Handler返回、SDK、补丁信任或D-02已通过。

## 输入与范围

三份目标输入由`read_verified`重新固定，未使用7.1地址或解码参数：

| 输入（相对GAME_PATH） | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| YuanShen_Data/Managed/Metadata/global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| YuanShen_Data/Managed/Metadata/startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

工具：`tools/trace_client72_receive_key.py`；脱敏摘要：`protocol/cn-7.2/receive-key-observations.json`。摘要另固定工具、graph、已有Handler-frame及两份schema证据哈希。私钥、完整帧、原始trace和本机目录只留忽略目录；摘要中的key哈希属于固定离线测试向量，没有真实会话密钥。

三层协议没有新增命名或晋级：20835→40503/NDENMDAIAMC与PlayerLoginRsp身份沿用已验独立getter、注册和typed Handler参数，本工具重新核查。字段沿用已有目标wire，64位未知字段仍称KHJKGGNMAGJ；列表及资源外层61/1337原名不改。资源四字段具名目的地沿用[最小响应合同](CLIENT_72_RESPONSE_CONTRACT.md)。

## 实际原生路径

| 步骤 | 本项实际执行/观察 | 明确边界 |
| --- | --- | --- |
| Token Handler→Login发送 | 既有1507BE0E0→密码学/MT64→Login构造/实际size/writer→完整帧；原生15A4E0D3B写selector=0 | 响应reader先行调用；沿用本地签名信任、SDK/clock等显式夹具；发送后的整个Handler未返回 |
| 接收回调 | 1472CF280完整正常返回，实际14A446A70分配包流程→14A446CC0 | 回调由工具另行调用，保留内存；没有模拟socket或声称线程自然进入 |
| 选key与解帧 | 14A446D4C实际读取145878FC0；14A446D65读取GLOBAL+19580；整帧循环XOR、端序、长度、包头和尾标记 | 没有向receiver传key或重写selector/19580；另外19588是预先声明的不同dispatch key向量 |
| 实际消息工厂 | 151A853D0→15585B760执行UInt16→Type字典查找，20835得到40503类别，再真实reader14B89EC90 | 字典启动内容只装入已验证一行；managed图分配和具体生命周期14B8A05F0是显式模型，不称完整对象池初始化 |
| 消息读取 | 原生tag/32位/64位标量、嵌套ReadMessage、两种消息列表及目标具体reader | ASCII/blob存储、graph managed分配和已审查的泛型元数据模型保留；没有opaque body reader |
| 入队与分发 | 接收回调成功后给队列sink的实际包指针，再供1507CC240原生分支转发至1507B7BE0；资源或错误前缀执行 | lock/入队是sink；消费是第三次显式调用，同一包/消息指针；不称真实FIFO/主线程调度 |

同一个`Offline72`对象完成以上状态衔接；工具没有在收帧前重建引擎、重新写key、重新设置selector或从摘要加载key。session key与本轮独立Java MT64输出逐字节一致，SHA-256为`5d517d6c52800a32b573b136c7a469131670b869881157c2fa013f714b796df5`。所有收帧及消费期间，原key指针和4096字节保持，原生selector写入记录为空。

这里的“同会话状态”是内存连续，**不是跨socket/线程的控制流连续**。三次入口之间复位CPU寄存器是工具的明确调用约定，完整Token Handler发送后续、真实消息注册初始化和队列调度仍是解除实机前置的后续工作。

## 样本与边界

按下列顺序在同一实例运行，错误样本后仍有成功帧：

| 样本 | 独立输入/结果 |
| --- | --- |
| nonzero-64-error | 手写hex `28 8180808080808010 40 ffffffffffffffffff01`；未知uint64=9007199254740993、retcode=-1；真实分发到错误诊断前缀 |
| resource-consumer | 61四字段及1337/7；同一子对象指针到RESOURCE_MD5、RESOURCE_VERSION_SIZE、RES_VERSION_SUFFIX、NEXT_SCRIPT_VERSION；RESOURCE_VERSION=42 |
| message-list | 手写hex覆盖两消息列表，各两元素，fixed32负值/300及非零标量；实际append/helper与reader |
| key-wrap-string | tag437的4200字节ASCII，帧跨4096字节key周期；完整对象与输入相等 |
| empty-response | 空body仍正常工厂/reader/分发；不擅自补服务器未知字段 |
| dispatch-key-rejected | 以另一key加密，返回6；未进入factory、body或队列 |
| bad-start / truncated-frame | 分别返回6/2；未进入factory、body或队列 |
| bad-end-after-body | body已经原生读取，尾标记失败返回7；没有reader归还/入队/Handler消费 |
| invalid-body-tag | 原生错误入口1509CB150；无入队/Handler消费；不吞异常继续同一帧 |
| session-after-errors | 所有错误之后，仍用原session key成功接收并消费资源字段；没有回退到dispatch key |

6组正常入队/分发，5组错误拒绝。每帧包头均独立编码sequence=`0xffffffff`、sentMs=`0x200000044`；XOR迭代数等于实际输入帧长。对象/输入哈希、原生路径哈希、实际调用和模型名单写入摘要，守卫拒绝把callback/factory/lookup/body或tag/scalar改成模型。

## 复现与验收

先按Harness从已核对的portable配置设置`GAME_PATH`、`JAVA_HOME`，Python不会自动读`.env`：

```powershell
python tools/trace_client72_receive_key.py --client $env:GAME_PATH --java-home $env:JAVA_HOME --output local/migration72/evidence/receive-key-trace.json --summary-output protocol/cn-7.2/receive-key-observations.json
python tools/trace_client72_receive_key.py --client $env:GAME_PATH --java-home $env:JAVA_HOME --output local/migration72/evidence/receive-key-trace-repeat.json --summary-output local/migration72/evidence/receive-key-summary-repeat.json
python -m unittest tools.test_client72_receive_key tools.test_client72_frame_reader tools.test_client72_handler_login_frame tools.test_client72_response_crypto tools.test_client72_response_contract tools.test_client72_graph -v
```

2026-11-06实际完成：新增6项守卫先红测（缺工具导入失败），随后与5个关联模块共32项通过，失败/错误/跳过均0；11样本完整trace及摘要两轮逐字节相同，摘要SHA-256为`8e4397dacee24ba5db0f6e568daa8a5f6a1ab825184a90ed74741b67c5cb8313`。双跑核验见本机`local/migration72/evidence/receive-key-determinism.json`。首次探索继承了发送工具的packet allocation模型，模型按接收调用参数主动拒绝；移除后实际接收分配流程通过。首正式运行只因手写预期tag437原名录错失败，按已固定wire摘要校正；未调整原生输出或跳校验。失败日志保留在忽略`local/migration72/analysis/receive-key-*.log`。

仅离线工具、测试、摘要和文档改变，没有运行代码、Proto、配置、补丁或资源修改；无需重启，未部署、未操作客户端/调试器/数据库。F-02父项及D-02保持未完成。

下一项可以独立做保留验签的信任材料供给与补丁ABI核查；当前旧补丁的强制验签成功不构成可部署合同。协议侧下一步是完整Handler发送后续和真实接收调度/初始化来源；不能把本项显式调用间没有写selector推广为所有重连、IFix或SDK路径永远不改它。
