# 国服7.2详细交接：已恢复，独立封/收帧验收完成

更新：2026-11-05。用户本轮明确要求继续按Project逐项推进，直到D-02完成并通过实机验收。目标工具状态为active，**没有暂停指令**；旧暂停交接只属历史。此文为持续工作检查点，不是请求再次授权。D-02尚未完成，不能标记complete或用离线研究替代客户端验收。

## 当前状态与提交

- `ae23ee7`：A-04d5r独立原生封包14A447E00完整返回，15原生样本与210研究回归通过。
- `b698f75`：A-04d5s独立原生收帧14A446CC0，18原生样本与216研究回归通过。
- 上项`dc41c99`连续Handler→Login→发送封包入口仍成立。新工具目前独立执行，**尚未与上项Handler/真实payload或seed安装串联**。
- 研究校验退出0，ready退出1，213项缺口未消除；四登录消息36个业务字段不变。响应tag1/3/658和Token请求tag965仍unknown。
- 本批只改离线研究工具、测试、脱敏摘要及文档，没有运行Java/主Proto/补丁/配置/资源改动，没有部署或客户端实测。
- Java完整测试集原有217项失败仍待处理；216是本批Python研究回归数量，不等于Java回归通过。

## 已完成内容

### A-04d5r：独立封包

工具`tools/trace_client72_frame.py`，摘要`protocol/cn-7.2/frame-observations.json`，6项测试`tools/test_client72_frame.py`。

原生PacketHead CalculateSize/WriteTo、标量varint、端序转换、MemoryStream.SetLength/Write/GetBuffer、帧标记/长度、整帧循环XOR、writer清理与返回实际执行。独立字节表达式确认格式：4567、u16be command、u16be headerLength、u32be bodyLength、header、body、89AB。selector145878FC0=0选GLOBAL+19580，=1选+19588；selected key为空/缺失时保留明文，不回退另一槽。两槽数组是显式输入，不代表真实初始密钥来源已确认。

15样本覆盖零、7/8/9字节体、命令32767/32768/65535、uint32/uint64上界、65536字节body、两槽4096字节循环、缺key/空key、缺message/stream/header、只读stream。11成功逐字节相等；缺message/stream原生false，缺header停14A448572错误入口，只读stream停159B46B50抛错helper。密钥不变、XOR次数与整帧长度一致、writer清理已核对。

模型限定：payload size/writer是明确bytes输入；对象池及Array.Copy存储为模型，stream容量预置，未测扩容/非零origin。原生封包函数完整返回不等于整个Token Handler返回。

### A-04d5s：独立收帧

工具`tools/trace_client72_frame_reader.py`，摘要`protocol/cn-7.2/frame-reader-observations.json`，6项测试`tools/test_client72_frame_reader.py`。

每组先执行BitConverter类型173的.cctor159B548E0，真实写14586B610=1，再从14A446CC0入口执行原地XOR、端序、PacketHead原生reader及CodedInputStream的tag/uint32/uint64/边界helper。factory151A853D0明确返回虚构消息，body reader只消费实际传入窗口，不执行业务schema。基类67861槽11→1495C9D60原生空ret用于夹具；1495AA480实际增加生命周期计数。

18样本包括命令高位、64位上界、重复singular最后值、65536字节体、两密钥循环、明确明文参数路径、错key、坏标记、短帧/截断/非法header及尾随字节。返回：1正常、4 null数组、5短于12字节、6前导错误/错key、2长度不足、7尾标记错误；header截断varint/零tag分别停1509CB360/1509CB150异常构造入口。正常命令/包头/body窗口/消费长度/生命周期/归池均验证。

注意原生次序：body reader先于尾标记校验，坏尾标记样本已消费body但未归还reader；尾随三字节仍返回1，packetLength排除尾随字节，XOR却覆盖传入全长。这只是目标行为记录，不自动改变服务端策略，不推断业务reader对坏帧是否有副作用。

未覆盖：真实factory、负载schema、压缩/替换delegate分支、实际key来源、socket/Handler/实机、异常抛出后的处理。

## 实际测试与复现

两工具分别重复运行，trace与summary逐字节一致。最新216研究回归全部通过；研究校验0/ready1。所有路径相对checkout，客户端由本机GAME_PATH解析，不复制旧硬编码路径。

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
python tools/trace_client72_frame.py --client $env:GAME_PATH --output local/migration72/evidence/frame-trace.json --summary-output protocol/cn-7.2/frame-observations.json
python tools/trace_client72_frame_reader.py --client $env:GAME_PATH --output local/migration72/evidence/frame-reader-trace.json --summary-output protocol/cn-7.2/frame-reader-observations.json
python -m unittest tools.test_client72_frame tools.test_client72_frame_reader
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

全回归选集仍为tools/test_client72*.py，加test_extract_client72、test_validate_protocol72、test_audit_resource72、test_inventory_protocol、test_review_protocol72_surface。

证据位于忽略目录local/migration72：

- evidence/frame-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json
- evidence/frame-reader-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json
- logs/frame-{run,repeat,regression}.log、logs/frame-reader-{run,repeat,regression}.log
- frame trace SHA256 f27b889094eb941378d799144a9392100c9cab3ef01fc1f6fb785f434c39f963，summary 2077972810f6ba3a1e16cc928d2522b2b773b14d43af95f1a617ad7e50c38b27。
- reader trace SHA256 f25c8c5e86423f7ef3614511917958844b077fd1bdf6350105f07feefd5b209b，summary c78f8b9adfba078d35da68370fc3473676e5b9cbb632a987e2e38b23e1c647ff。

## 弯路与避免误判

1. 未执行BitConverter.cctor时，offset6非对齐32位读取按另一端序解释，探索返回长度不足。已用目标.cctor实际执行修复夹具，不能声称是游戏客户端bug。
2. 独立测试最初把header长度手算为11；明确hex向量实际12字节。仅纠正测试预期，原生读取值/长度和字节都未改变。
3. 原native header reader会改变输入stream position/limit，勿套用WireInput后声称原生helper完整执行。本工具直接运行scalar/tag/limit helpers。
4. factory/body是模型，不能因完整帧函数ret就把真实消息映射晋级，或宣称Handler/SDK启动完成。
5. 旧`analysis/probe_frame_reader.py`只作探索（现已跑完整opaque路径）；正式证据以tools工具和重复产物为准。

## 未完成、阻塞及下一步

没有已确认的全局外部硬阻塞，目标继续active。

1. 优先串联真实Token/Login请求writer/CalculateSize与封包、响应实际reader与收帧，以及已有seed安装到实际key槽。Frame72目前自行配置storage/manager，直接并入现有Handler会覆盖全局对象，须拆出可复用存储设置并用连续指针/原生trace证明，不能重设寄存器伪装连续执行。
2. 或继续A-03/P0闭包：新旧号、选人、初始化、场景握手、嵌套/枚举业务关联。当前四登录消息不等于全部P0。
3. B-03正式生成、C-01双向接入依赖仍未齐；D-01实际补丁ABI/信任/SDK路由/加载、E-01资源revision/Lua/场景待验。
4. D-02需完成新号选人、旧测试号登录、初始化与场景握手、稳定心跳、退出重登实测；不能启动猜测协议。
5. V-01 Java217项失败继续逐项归因/修复，保留账号、经济和保存不变量。

## 本机状态与文档

已核对.env的GAME_PATH/portable/端口、config资源/隔离数据与库名；.env.local不存在。本轮Java及客户端未运行，8088/TCP和22101/UDP无监听，只有本工作区便携MongoDB监听27017，数据仍在原mongodata。未访问/清理数据库、未启动服务或客户端。以后启动仍须重新核对路径、PID、端口及运行JAR hash，不能复用历史部署结果。

分支7.2-dev，无新工作树；patch仍f2874103ab70afc56260ee12d1eaca96247933b2，resources仍dac2a9d43332ef51879522cf39da2831b0ca0543。AGENTS.md已有编码规范修改及resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json差异保留，未纳入提交。全部提交仅本地，未推送。

Project、Harness、README、CLIENT_72_NETWORK已维护本批实际范围；账号/存档行为及字段未改变。本次覆盖前备份原HANDOFF2到local/migration72/handoffs/HANDOFF2.before-frame-b698f75.md并核对SHA256；报告evidence/handoff-frame-backup-check.json。HANDOFF.md只追加本次简要指引。
