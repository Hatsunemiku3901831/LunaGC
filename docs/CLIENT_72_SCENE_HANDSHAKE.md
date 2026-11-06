# F-03：八个场景握手与编码值边界

后续融合补充：[请求省略边界](CLIENT_72_SCENE_REQUEST_CONTRACT.md)独立执行8个构造/writer，确认缺字段按raw0逆变换，不能沿用响应默认；[1017域](CLIENT_72_CMD1017_DOMAIN.md)实际分清正常Game C2S与另一beyond/WPF出站流，保留全局重号。生产入口已一次解析并关联当前Player token，失配在发送/状态变更前走现有packet-handler异常路径，不猜错误码、不加未证阶段时序或重发策略。Java先红12项中4失配失败，后请求12项与握手15项全过；整批账号/经济/保存验收由本轮收尾记录。

2026-11-06。固定国服7.2三输入，完成八个Ready/Init/Done/Post请求响应的目标身份、20个raw字段/10类型，以及八个token、四个retcode和PlayerEnterSceneNotify token的13条业务绑定。工具为`tools/client72_scene_handshake.py`，机器契约为[观察摘要](../protocol/cn-7.2/scene-handshake-observations.json)的`roles`、`messages`、`semanticBindings`、`responsePresenceContract`。只做离线研究，不启动客户端、调试目标或服务；本项不修改生产源码、mappings和父任务状态，不代表F-03完整初始化或D-02完成。

所有输入由`extract_client72.HASHES`重新校验并提取，未引用Asta包号、setter名称或旧客户端地址来晋级。Asta固定参考`a49f51aa6f2787ffbc25cee46801b70dee860d23`只作为父批背景，本工具无需其运行文件，也未复制其实现或生成类。既有[scene-wait](CLIENT_72_SCENE_WAIT.md)、[scene-nested](CLIENT_72_SCENE_NESTED.md)及研究Proto的原始SHA-256固定在`REUSED_EVIDENCE`，沿用其非空uint64/列表/字符串边界，不把历史原生样本称为本轮重跑。

## 三层来源和完整raw载体

| 业务角色 | 目标类型 / CmdID | 目标入口 | raw字段 tag / 对象偏移 |
| --- | --- | --- | --- |
| EnterSceneReadyReq | 74391 DEDHOOCBHCL / 5046 | 应用构造15121E560；writer14734BA00 | uint32 MCCMGLDOECH 3/+18 |
| EnterSceneReadyRsp | 17278 MMLHNEJIFHG / 9948 | Handler1507D4D70；reader147964650 | uint32 MCCMGLDOECH 2/+18；int32 LDLIEKOAFBP 12/+1C |
| SceneInitFinishReq | 20380 MLIHBBENFFA / 4905 | 应用构造1512242B0；writer1518DE8A0 | uint32 MCCMGLDOECH 11/+18 |
| SceneInitFinishRsp | 36349 NBCLHOLLHLK / 21392 | Handler1507C5B10；reader14E7A8B40 | uint32 MCCMGLDOECH 1/+18；int32 LDLIEKOAFBP 7/+1C |
| EnterSceneDoneReq | 63999 HDPGCNCMACO / 1017 | 应用构造15121E380；writer14A75C040 | uint32 MCCMGLDOECH 12/+18 |
| EnterSceneDoneRsp | 74698 FIMHLLEPCBH / 298 | Handler1507D50D0；reader148198570 | uint32 MCCMGLDOECH 4/+24；int32 LDLIEKOAFBP 6/+20；82602 MFLHLEMFIGI 12/+18 |
| PostEnterSceneReq | 74076 AHBJNPDFDMJ / 1696 | 应用构造151226220；writer14C963FC0 | double MHCBMOHFBCK 1/+18；uint32 MCCMGLDOECH 6/+20 |
| PostEnterSceneRsp | 51326 ODANIDKHMMG / 26760 | 分派wrapper1507D1CF0→Handler1507EEE50；reader149671480 | uint32 MCCMGLDOECH 2/+1C；int32 LDLIEKOAFBP 6/+18 |

四个响应分别核对其getter槽22、注册1507C2D0F/1507C2A8A/1507C2D1E/1507C2A6C、独立方法参数及自己的目标日志usage。实际1507CC240分派四个CmdID，0/65535对照无匹配；26760确实先到wrapper1507D1CF0，不能写成直接到内部Handler。wrapper参数独立指向51326，1507D1DA2的直接调用目标为1507EEE50；未执行完整wrapper及其事件广播。

请求由目标命名日志、独立self-class usage、原生分配/参数写入、同一对象的完整writer闭合。Ready日志literal34850还直接标明`token:{0}`。**1017的全局重号仍保留**：另有22065/NCJGOJMOPHB getter14AC565C0；本项确认场景请求确为63999/1017，不把两类型合并，也不声称另一类型的网络域已解决。

Done必要嵌套全部实际非空读取，未造空消息或用bytes掩盖未知内容：

| 类型 | raw字段 |
| --- | --- |
| 82602 NFIGDMDEDBC | DFDHCAIKMAI：84343 message tag1/+18；ADMHCDNJLAI：uint32 tag3/+24；LLHEBIHGDAE：uint32 tag11/+20 |
| 84343 BGDCNKAKGHI | CJMJBINEBAG：float tag1/+18；ALOKJBKPLGA：float tag2/+20；MGICHKNKDKO：float tag3/+1C |

三float只按offset/bits核对，未把它们命名为坐标轴；82602各字段与Done外层嵌套业务名称也未知。Post double实际从静态cell145873360复制，保留64位bit-pattern，尚无`client_time`或`client_loading_cost`等业务命名依据。[研究Proto](../protocol/cn-7.2/scene-handshake-research.proto)保留全部raw名称；不是生产就绪声明。

## token、retcode与presence契约

以下运算全部按32位模运算；retcode最终按int32解释。表达式把应用值编码成实际wire整数，不能用普通getter名称省略这一层。

| 消息 | token 应用值→wire | retcode 应用值→wire |
| --- | --- | --- |
| ReadyReq | `(token XOR E8C3) + 4C01` | — |
| InitReq | `(token XOR 5673) + F8CD` | — |
| DoneReq | `(token XOR 2034) - 8609` | — |
| PostReq | `(token XOR 4902) - B3B6` | — |
| ReadyRsp | `(token + E034) XOR 21BE` | `(code XOR 83BE) + 1077` |
| InitRsp | `(token - 4765) XOR C7CB` | `(code - A488) XOR 7530` |
| DoneRsp | `(token XOR BF71) - 860B` | `(code XOR 6D20) - BEAC` |
| PostRsp | `(token XOR E67B) + 4885` | `(code - F7C) XOR 73B7` |

独立token命名链：四个响应的实际reader对象进入自己的Handler，Ready/Init进一步进入真实公共错误前缀1507C5E10，最终都进入原生14CD5F7B0。使用明确的空字典输入，真正执行1578B66F0的missing-key路径，把解码值送到literal52998/cell145B31650的`GetLoadingTask is Null token:`诊断；没有把查找替换成成功。Post另有typed消费者15255BFF0按同一算术读取+1C，目标literal60411明确标记Post成功token，作为静态独立对照；其条件查询/完整成功体未执行。

Init请求的Level入口14828C930把同一参数先交给请求入口，再经14CD68CF0进入上述命名Task诊断。该请求入口在这项producer探针中仅捕获参数，其完整应用构造/writer另有16项实际执行。Done的受限原生块14828C751–14828C789向Init和Done传递相同保存参数。Post从真实DoneRsp reader对象出发，在显式Loading块14CD6719E进入实际148289D60→148289E50，保存token到真实coroutine类型78239/+30；随后在单独的150725574块读该原字段交给Post请求。coroutine self-class1458ECBC8来自lazy usage group17B5E、row240698、source657896，不能只查global group。**两个块的live-in、前置分支和调度间隙明确保留；未运行或模拟调度器。**

通知token新增绑定：PlayerEnterSceneNotify的tag10/+94，经真实reader→14CD615A0→15121E560→发送入口15120D730，满足`token = (raw + 5889) XOR E011`。原通知对象未替换，Ready对象也是实际构造所持有的同一指针。其余25个通知字段仍不晋级。

目标构造器的默认存储并非raw零；以下各缺字段默认都解码为逻辑零：

| 响应 | 缺token的raw默认 | 显式raw0 token解码 | 缺retcode的raw默认 | 显式raw0 retcode解码 |
| --- | --- | --- | --- | --- |
| Ready | C18A | FFFF418A | 9435 | -37833 |
| Init | FFFF7F50 | 10F30 | FFFF2E48 | 72120 |
| Done | 3966 | 397A | FFFFAE74 | 54156 |
| Post | 12F00 | FFFF5100 | FFFF8333 | 33587 |

原生reader实际验证缺字段保持构造值、重复字段最后生效、显式raw0覆盖；28项retcode验证包含正负边界、缺字段与raw0的真实success/error分支。服务端须区分缺失与显式0。生产采用`optional`或其他presence表示是Java适配决定，**不表示恢复了目标.proto中的optional声明**。本研究Proto仅作raw wire载体；显式零测试使用独立手写wire，不依赖proto3默认零编码。请求侧样本也证明逻辑token0仍被构造成非零raw值并实际发出，不能按业务零直接使用空payload。

回调条件仅按目标代码边界记录：Ready逻辑retcode0进入1507D4F41，其后设置owner+1EC；owner+1B3控制Init重发，参数来自+1F0和+24A，owner+24B控制Done重发，参数来自+1D4。Init逻辑retcode0进入返回边界1507C5D7C。Done逻辑0进入1507D51F2，后续可消费可选82602并交LoadingManager；Post逻辑0进入1507EEED9，后续依赖GLOBAL+B330及其+60上下文，再调用Level后段。**本项success样本止于这些前缀，不把上述后段或完整客户端callback称为已执行**；错误样本到命名诊断/Task缺键诊断即止，不模拟恢复成功。

## 实际执行、重复性与限制

116个有限结果：16请求构造/writer/独立protoc；22响应reader（含Done非空嵌套及重复显式零合并）；28 retcode消费前缀；24响应token命名链；12请求producer链；4通知→Ready连续token链；6分派含两个负对照；4明确拒绝。非空Done样本覆盖两个uint32边界及三float位型，Post double覆盖64位模式`C0C81CA000000000`和`4340000000000001`。本项没有新造uint64/list字段，既有通知和嵌套的uint64/list证据按固定摘要hash复用并受测试保护。

未知调用和对象范围仍直接失败。raw-byte/tag/skip、托管分配/反射、字符串格式化、writer整数/双精度发射为明确模型；构造器、保护reader/writer字段选择、uint32原生读取、ReadMessage/限长、消费算术、字典lookup与所列调用路径执行目标指令。零tag/物理截断由有界输入模型拒绝；负子消息长度和递归上限进入目标原生错误helper，二者不得混称。IFix关闭，完整managed ABI、物理buffer refill/EOF、socket收发和游戏调度未验。

10项新增回归通过：固定手写wire、编码默认/显式零、非空nested合并、64位模式、固定既有uint64/list证据、输入hash与tag篡改拒绝、语义越界晋级拒绝、未执行调度器标记及native/model错误来源。连同scene-wait/nested/normal共38项通过，失败/错误/跳过均0。两轮完整116有限结果的trace、摘要、Proto逐字节相同，SHA-256依次为`570c3da28e2a721a6f09b546c6cf403e5b700582fdaf19d3cee86b54cd209829`、`b2ff1ac423fc3f1685715a261452359265a7dd0bfdb2821b6af3c55866ad8847`、`a626162680503d46dd48ffd6c986125a422a8bd456b3248560eb9bda4cdda605`。原始失败日志保留：早期请求golden误对非Post包前置double、独立wire字段顺序、夹具heap floor覆盖initialized class、调用栈返回地址使诊断参数偏移差8字节、scene-wait hash抄写一位、lazy coroutine usage漏查及Windows文本换行hash；均修复研究工具/预期边界，没有改客户端或放宽成功条件。

```powershell
$sceneClient = & ./tools/game_path.ps1
$sceneProtoc = (Get-ChildItem local/gradle/caches/modules-2/files-2.1/com.google.protobuf/protoc/3.18.1 -Recurse -File -Filter '*.exe' | Select-Object -First 1).FullName
python tools/client72_scene_handshake.py --client $sceneClient --protoc $sceneProtoc --output local/migration72/evidence/scene-handshake-trace.json --summary-output protocol/cn-7.2/scene-handshake-observations.json --proto-output protocol/cn-7.2/scene-handshake-research.proto
python -m unittest tools.test_client72_scene_handshake -v
```

重复结果见忽略目录`local/migration72/evidence/scene-handshake-determinism.json`；完整trace、重复摘要/Proto与失败/成功日志均留local。本项交付不本地提交，由父批统一审查归属后提交；不改共享mappings/review/Project/Harness/生产Java/Proto。下一独立项是将已验八包按presence/算术契约生成接入并回归；完整PlayerEnterSceneNotify实际builder字段与必需初始化序列仍待单独证据，不因为八包通过解除F-03或D-02阻塞。
