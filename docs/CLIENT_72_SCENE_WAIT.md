# B-02e2：入场通知reader与选人缓存等待

2026-11-06，固定国服7.2三输入重新提取。工具为`trace_client72_scene_wait.py`、`client72_scene_reader.py`；本项只恢复消息身份、外层wire、部分非空嵌套及原生缓存等待，**未完成正常场景入场、生产接管或D-02**。

## 消息身份与入口链

目标getter1523856A0实际返回**27748**，声明类型**46311 / BBECFDMDFJC**。注册1507C2A03的原始12字节与1532ED560目标吻合；实际1507CC240分发27748时把原对象传给**1507AD120包装入口**，不是直接调用先前发现的1507AD6F0。0/65535对照没有该调用。

包装入口及内部Handler的独立元数据参数均为46311，LoadingManager入口14CD67260也收同一类型。目标静态literal通过真实usage落点关联到这些指令：

- index45993、cell145B23B68：OnReceivePlayerEnterSceneNotify的格式日志；包装入口先解算原始+94值，再送入该日志参数。
- index50090、cell145B2BB70：PlayerModule OnPlayerEnterSceneNotify；内部1507AD6F0入口实际调用该日志。
- index53019、cell145B316F8：LoadingManager OnPlayerEnterSceneNotify；正常分支的日志锚点，本批在其之前停止。

因此CmdID→类型与类型→PlayerEnterSceneNotify身份可分别核实，mappings仅晋级这两层；runtimeStatus仍not-tested。不能把内部Handler称为已执行完整网络入口。包装的1517113F0和1507FD7D0在**GLOBAL+25260上下文为空**的明确样本下执行原始返回路径，随后原对象进入1507AD6F0→14CD67260→原生clone。上下文存在时的分支、调度与包装尾段尚未覆盖。

## 外层wire与非空内容

保护reader1523856B0实际执行128组输入，范围为tag1..2047、wire0/2/1/5；范围本身不证明完整性，另对照全部26个实例字段，少一个或多义即拒绝。结果如下，偏移均相对对象：

| tag | 偏移 | 原始wire类型 / 内容范围 |
| --- | --- | --- |
| 1 | A0 | uint32 |
| 2 | 8C | uint32 |
| 5 | A4 | uint32 |
| 6 | 68 | uint64 |
| 7 | 7C | uint32 |
| 8 | 98 | int32存储的ILGABKBIAIE枚举 |
| 10 | 94 | uint32 |
| 11 | 80 | uint32 |
| 12 | 84 | bool |
| 13 | 20 | repeated uint32，packed/unpacked均实际验证 |
| 14 | 18 | 84343三float对象，非空reader与clone已验 |
| 15 | 50 | 同84343 |
| 46 | 74 | uint32 |
| 157 | 9C | uint32 |
| 293 | 38 | string，UTF-8/UTF-16托管转换为明确模型 |
| 395 | 60 | 66040消息，内容未追踪 |
| 739 | 58 | 67761消息，内容未追踪 |
| 763 | 30 | 同84343 |
| 941 | 90 | uint32 |
| 957 | 88 | uint32 |
| 1345 | 85 | bool |
| 1357 | 40 | 34133消息，内容未追踪 |
| 1583 | 78 | uint32 |
| 1723 | 48 | 69612消息，内容未追踪 |
| 1964 | 28 | 25866消息，内容未追踪 |
| 2008 | 70 | uint32 |

84343的原生reader154048D40绑定tag1→+18、tag2→+20、tag3→+1C，均float32。这里的三float结构和工具Position夹具名不等于已恢复正式Vector类型或轴语义。ILGABKBIAIE默认值表恢复15个EnterNone至EnterHall目标符号，EnterGoto=2；字段业务规范名仍未晋级。

后续32项正式样本中，实际执行ReadMessage/PushLimit/PopLimit、外层reader、三float reader、uint32列表读入/append/复制、构造函数、clone及CopyFrom。为区分整数算术位置，还移除uint32/uint64取值模型，实际执行147520110、1475211E0、1475202D0的varint代码；仅raw-byte/标签/跳过/float32/字符串等底层边界保留明确模型。

独立手写wire与protoc比较6组：空、全部非零标量、32/64位上界与负int32、Unicode/emoji/NUL、packed列表、三个非空三float子对象。其余覆盖重复标量最后值、packed/unpacked混合追加、重复子消息合并、错误wire跳过、最大合法未知tag携带64位值，以及7项错误/边界。varint溢出与recursion-limit到达原生错误分支；零tag等来自明确WireInput校验，不冒称客户端所有错误行为均恢复。

protoc使用工具内的**wire夹具载体**，三个已验子对象给出真实float字段，另外五类未知内容仅作opaque bytes表示长度包络，未造空message，也未作为完整目标schema提交到生产。mappings的26字段全部为unknown_field_N/unresolved，schema为candidate。

## 构造默认、消费算术与缓存

不能仅凭消费代码推断reader也有同一变换。实际原生varint和reader把显式输入值原样写入所查标量偏移；构造函数152387890却把+94设为8788、+7C设为FFFF121E等非零默认。显式wire零会覆盖这些默认，与字段未出现不同。后续消费端实测：

```text
sender+D0 = sender+120启用时 ((raw94 + 5889) mod 2^32) XOR E011，否则0
network+1D0 = ((raw7C XOR 2D03) + C0E3) mod 2^32
```

这只确定两处应用前的算术和原始值来源；不能据此把字段猜成旧scene_id/enter_scene_token，不能向服务端直接填旧业务值，也没有核实所有整数的消费规则。26字段均保持业务未知。

13组内部/包装样本保留真实前置判断：feature键1B1B、消息+84/枚举2、当前状态38、ready40、id28、配置查询返回值等作为明确输入；1507B32D0本身原生执行。配置105到达1507B3490恢复入口即止，不伪造其返回或称整个Handler已返回；null消息在前置重置后返回。查询/日志/托管格式化为模型，不用成功谓词替换门。

当born+19为1，14CD67260调用原生152386C00→构造/152386CB0 CopyFrom，再把新对象写入born+10：

- born+1A为0：缓存后等待，标志保持[1,0]、UI未转场，内部Handler只跑到缓存调用返回1507AD859。
- born+1A为1：清[19,1A]，UI+33C写3，再以缓存clone进入正常分支；在14C8AE9A0(mode10)身份请求入口停止，未执行身份请求/完整场景后段。
- born不存在或未激活：直接到同一身份请求边界，不冒称缓存。

专用原生入口观察点记录Handler/CACHE/CLONE收到的仍是原reader对象指针。独立比较全部已验内容；列表与三float子对象地址独立，并实际修改源列表/子对象后确认clone不受影响。字符串作为托管不可变对象允许共享，未假造深拷贝要求。

## 复现、回归及尚缺内容

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
$sceneProtoc = (Get-ChildItem local/gradle/caches/modules-2/files-2.1/com.google.protobuf/protoc/3.18.1 -Recurse -Filter '*.exe' | Select-Object -First 1).FullName
python tools/trace_client72_scene_wait.py --client $env:GAME_PATH --protoc $sceneProtoc --output local/migration72/evidence/scene-wait-trace.json --summary-output protocol/cn-7.2/scene-wait-observations.json
python -m unittest tools.test_client72_scene_wait tools.test_client72_born tools.test_client72_born_continue tools.test_validate_protocol72 -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

三输入哈希、声明/方法/usage/注册与代码入口均固定；正式32样本和128发现输入两次trace/摘要逐字节相同。11新增及原born/校验器选集共44项通过。首次接入校验把profile中SDK/Astrolabe也误当本工具声称的输入，44项中1失败；已改为精确核对binary/metadata/startup三份，保留实际错误hash拒绝测试和失败日志，并未扩大研究范围或跳过检查。

完整研究选集305项通过；原born39和继续33样本实际重新执行，两份摘要与原提交完全相同。研究结构校验0错误、ready仍拒绝228缺口。人工复核依赖矩阵仅该消息行与mappings/新增摘要变化，147行及1287源码/Proto、99候选/1140描述符、开放边界不变；53→54研究输入，recover两次相同。证据scene-wait-login-proto-{review-check,repeat}.json及scene-wait-research-regression.log。

本机原始证据均在忽略local/migration72：evidence/scene-wait-{trace,trace-repeat,summary-repeat,trace-determinism,summary-determinism}.json与logs/scene-wait-{final,repeat,mapping-focused-tests,mapping-focused-repeat}.log。共享[观察摘要](../protocol/cn-7.2/scene-wait-observations.json)含三层来源、原始字段绑定、模型/边界和计数，不含客户端二进制、实机凭据或抓包。

下一项追踪包装上下文存在的路径、14C8AE9A0之后的实际场景数据消费，以及其余五类非空嵌套和必要业务字段/编码。媒体定时器实际调度、完整P0消息次序、SDK/补丁和实机均未完成；父B-02e/B-02、C-01/D-01/E-01及D-02不晋级。仅离线工具/映射证据/测试/文档变更，没有部署或重启运行代码。
