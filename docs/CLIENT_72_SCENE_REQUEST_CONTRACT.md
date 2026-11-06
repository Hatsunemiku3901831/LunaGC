# F-03请求token省略边界与入口关联

固定国服7.2三输入；只执行离线原生应用构造与完整writer，不启动客户端、服务或调试目标。新工具[client72_scene_request_contract.py](../tools/client72_scene_request_contract.py)复用冻结的场景握手声明、`Writer72`及运行时模型；未改旧场景五文件。机器摘要为[scene-request-contract-observations.json](../protocol/cn-7.2/scene-request-contract-observations.json)。不把本项发送方向证据说成执行了服务端原生reader或完整场景生命周期。

## 实际原生边界

下表每组都先运行目标默认构造器，再执行目标应用构造直到`15120D730`，检查同一请求对象和实际raw字段，最后执行完整目标writer。不是模型直接写字段或根据同schema往返推断。除既有integer/byte发射helper、Post double发射helper及托管分配/日志边界外，字段省略判断与保护writer本体由原生指令执行。

| 请求 / writer | 应用token | 对象raw值 | 实际完整wire |
| --- | --- | --- | --- |
| Ready / 14734BA00 | 4294925116 | 0 | 空 |
| Ready相邻 | 4294925117 | 4294967295 | `18ffffffff0f` |
| Init / 1518DE8A0 | 4294922560 | 0 | 空 |
| Init相邻 | 4294922561 | 4294967295 | `58ffffffff0f` |
| Done / 14A75C040 | 42557 | 0 | 空 |
| Done相邻 | 42558 | 1 | `6001` |
| Post / 14C963FC0 | 64180 | 0 | `090100000000004043` |
| Post相邻 | 64181 | 1 | `0901000000000040433001` |

Post的9字节部分是未知double的已验位型`4340000000000001`，不命名其业务含义。四个writer都省略raw0 token；Post raw0时仍发送double，说明不是把整个发送替换成空操作。

因此请求缺字段与显式raw0必须走同一个逆变换。**不能把响应reader的“缺字段保留native构造默认并解码logical0”套到请求接收入口。** 客户端请求对象默认构造存储确实是encoded logical0，但完整writer省略的条件是raw0；构造默认与输出省略语义是两层证据。Done42557、Post64180都落在本仓库当前随机scene token 1000..99999范围内，将请求缺字段强制改成logical0会误拒正常加载。

| 请求 | wire→logical（32位模运算） | 缺字段/raw0的logical |
| --- | --- | --- |
| Ready/tag3 | `(raw - 0x4C01) XOR 0xE8C3` | 4294925116 / FFFF5B3C |
| Init/tag11 | `(raw - 0xF8CD) XOR 0x5673` | 4294922560 / FFFF5140 |
| Done/tag12 | `(raw + 0x8609) XOR 0x2034` | 42557 / 0000A63D |
| Post/tag6 | `(raw + 0xB3B6) XOR 0x4902` | 64180 / 0000FAB4 |

Java `int`按位运算与溢出正好表达此合同；比较按相同32位bit pattern，不用大小关系猜测有效token。请求`getMCCMGLDOECH()`缺字段返回raw0即可解码，不要按`hasMCCMGLDOECH()`将其重写成logical0或自动拒绝；optional仍保留原始presence供wire适配和诊断。四个响应缺字段/显式0的原生合同保持原样，不能受本请求规则覆盖。

## 最小入口关联及证据边界

可做的正常路径是：在每个handler入口parse一次，按对应逆变换解出本次请求token，和当前`Player.enterSceneToken`比较，相同才执行既有流程。Player token由`PacketPlayerEnterSceneNotify`三个实际构造路径设置，同时把状态设成LOADING；请求不能反过来覆盖这个token或创建场景身份。当前transfer路径先把player放入目的Scene，再构造通知生成新token，因此旧场景请求有具体失配条件。

检查必须在全部发送和游戏副作用之前：Ready的Peer/Rsp；Init的批量初始化、widget/天气/队伍/水印、INIT和scene初始化回调；Done的LOADED/spawn/实体/NPC/任务组/建筑/技能等；Post的任务事件/地下城启动、unfreeze与开场流程。只在最后响应时检查或只把本次token放进Rsp，不能防止旧Done错误地加载当前新场景。

匹配时本次请求的logical token与当前Player token相同，四个现有响应mask产生相同已验wire，正常流程可保持。目标响应消费者确实把解码token用于LoadingTask查找；不能对失配旧token执行当前场景副作用后，随意回复当前新token成功。根批选用“失配抛异常，经现有packet-handler错误路径处理、不发猜测retcode”的本地入口策略，与现有证据没有矛盾；它是本仓库关联策略，不是已恢复的官方服务端错误响应或重试策略。

目前没有证据要求新增严格LOADING→INIT→LOADED单向门禁、拒绝同token重发、维护旧token表、自动生成重试、发明错误码或把Post double作为时钟。已有客户端Ready响应后存在受控Init/Done重发分支；同token重复与异步调度不能凭常识一并拒绝。入口一次匹配也不等于整个复杂场景流程获得原子事务，不能据此宣称所有并发或真实调度已覆盖；本项不新增锁或重构游戏流程。

建议回归直接执行四个实际handler：匹配token进入正常流程，失配token在第一副作用前拒绝且无回复/状态变化；缺字段和显式raw0分别在Done42557/Post64180匹配时通过关联，邻值42558/64181使用固定非零wire；重复token字段按解析后的最后值关联；同token正常重发保持现有业务行为；通过新通知产生B token后，把旧A请求投递到实际handler，证明不触及B场景。涉及完整Init/Done游戏行为的夹具仍需根批既有资源/账号/保存回归，不能用side-effect sink声称完成了真实加载。

## 复现

```powershell
python tools/client72_scene_request_contract.py --client $env:GAME_PATH --output local/migration72/evidence/scene-request-contract-trace.json --summary-output protocol/cn-7.2/scene-request-contract-observations.json
python -m unittest tools.test_client72_scene_request_contract -v
```

完整trace与两轮对照留忽略目录；正式摘要只含固定目标来源、合成token、wire及原生路径摘要。实际部署、资源/SDK/补丁、PlayerEnterSceneNotify其余字段与完整初始化顺序仍须独立满足，D-02实机未验。
