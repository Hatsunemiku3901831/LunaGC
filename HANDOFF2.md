# 国服7.2详细接续记录

更新：2026-11-05。用户要求持续推进至D-02完成并验收；目标active，无暂停指令。D-02未完成，不能把离线工具验收当作实机通过。

## 当前状态与完成项

本批接手0c08812，已阅读AGENTS、README、Project、Harness、迁移/部署手册、两份交接及账号/保存专题。当前本地研究提交`c34ab42`完成A-04d5n2限定验收：同次JSON/原生RSA/SHA/验签→30677父reader→67961嵌套ReadMessage/limits/reader→151204AC0配置入口。8原生样本，173研究回归；trace/摘要二次逐字节一致。研究校验退出0、ready退出1。所有协议业务命名仍35字段，tag14/tag965未知。

工作区既有AGENTS修改、resources内ScriptSceneData/flat.luas.scenes.full_globals.lua.json保留且未提交。patch f2874103ab70afc56260ee12d1eaca96247933b2，resources dac2a9d43332ef51879522cf39da2831b0ca0543，均未变。已核对本机.env使用portable和目标GAME_PATH，.env.local不存在；未启动/操作客户端或数据库，未修改运行代码/Proto/配置/补丁/资源，不需要部署重启。未来启动须重新核对配置、进程及固定端口归属。

## 工具和实际证据

- `tools/trace_client72_dispatch_post.py`、`tools/test_client72_dispatch_post.py`、共享`protocol/cn-7.2/dispatch-post-observations.json`。
- 本机`local/migration72/evidence/dispatch-post-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json`；logs/dispatch-post-{run,repeat,regression}.log。
- 8组：非零、空Region、UInt32最大、重复Region合并、缺Region、负int32、嵌套长度截断、嵌套varint截断。合并2次reader/1次分配；长度截断为原生异常分支，varint截断是显式wire-helper边界，二者不混称原生拒绝。
- 配置实参rdx/r8来自响应tag12/tag11，原生ToByteArray后字节相同；mode=1/receiver不变。只验入口，不代表任意bytes是有效配置JSON。session+B0全样本保持未变。
- Region67961 Clone读取1458DEE48、CopyFrom149BB5A00及reader slot19/149BB4EB0已核查。只覆盖选定字段，未宣称完整Region schema。

复现命令见CLIENT_72_SESSION末尾A-04d5n2与Harness。工具每次校验三份固定输入哈希。README/Project/Harness/会话研究已更新。前批n1及更早历史详见本次备份与Project，不要重复覆盖其范围。

## 未完成、阻塞和下一步

A-04d5n/B-02c9及A-04d/B-02仍进行中。没有必须等待外部才可做的整体硬阻塞：继续151204AC0配置函数内部，返回151211DD5，再贯通后处理至B0；第二协程和tag965业务来源另验。B-03/C-01尚不具备完整映射前置；D-01补丁实际加载/信任、E-01资源revision/场景/Lua仍未齐。D-02新旧测试号、选人、初始化、场景握手、心跳、退出重登全部未实测。

最新探索：忽略脚本`analysis/probe_dispatch_config.py`、`analysis/dispatch-config-probe.log`。以{}配置/空key/模式1从151204AC0原生执行，目标literal34216日志为`Genshin Start Log: NetworkManager SetServerDispatchConfig isGlobal=`。原生代码对输入执行可选XOR，然后Encoding.GetString→JSON Parse→146D3F6E0配置字段消费。已允许146D3F6E0真实执行；未把该业务方法模拟成功。

目前可执行下一步：探索最后到1404C2BE0，返回地址151207A94（实际调用151207A8F），在secureOptionalResourcePolicyPlugins缺省路径遇到尚未设置的容器。查1512057D6以后的引用/相应GLOBAL槽，恢复明确初始化布局，继续真实执行，不把所有空指针替换成任意对象。Encoding缓存GLOBAL+24B8目前显式用既有Encoding模型，GLOBAL+6248是空集合存储夹具；它们只是离线初始化输入，不证明真实SDK初始化已完成。现有{}探索不列为正式验收。

## 弯路与避坑

1. n1成功样本只有tag14，缺Region时真实后处理走151211BDD错误处理入口；不能直接拼接成功地址到B0。
2. Region为空class并非目标客户端缺陷；原生实际读取1458DEE48，先用Clone/CopyFrom/声明核对类型再模型化分配。
3. ByteString字段还需允许原生147CF2D60赋值包装；不应绕过wrapper直接写字段。
4. 配置函数入口前缀通过不代表配置被消费。原始cfg/key bytes用于参数传递断言，后续必须改为有效JSON/密钥夹具并验证真实解码。
5. 不把任意返回RAX截成错误码，不跳未知调用，不用线性解释保护填充段替代原生执行。
6. 继续保持错误UI/HTTP/IFix/实际运行时ABI和客户端验收独立；仅离线工具改动无需重启服务。

## 交接备份

本次覆盖前备份原HANDOFF2至`local/migration72/handoffs/HANDOFF2.before-dispatch-post-c34ab42.md`，已核对SHA-256相同。HANDOFF.md只追加。研发未暂停，下一轮接续不需再次授权；全部本地提交，未推送。
