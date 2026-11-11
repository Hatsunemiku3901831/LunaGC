# F-03d3s2 / Read core首region与正常续段（2026-11-11C）

任务：`F-03d3s2/SCENE-READ-CORE-SEMANTICS`，父项`D02-R1/R3`。消费[204B候选原窗](CLIENT_72_D02_SCENE_READ_CORE_WINDOW_20261111C.md)，不重做208B参数/令牌合同或refill状态投影。派工时204B集成第三待主线，本页不代替来源验收。

**这是完整pdata region，不是完整逻辑read。** 该region没有RET、目的buffer字节store或读取循环；三条条件边跳至`141138f85`，正常路径还从末指令结束处直接落入未保存的`141138d3c`。因此目前full/partial/EOF/error、返回值均未闭。下一项优先这个正常续段，不能只追f85或把pdata结束当函数返回。

## 固定源与本轮检查

原件根`local/migration72/d02-resume-20261111c/scene-read-core-window/{fresh1,fresh2}/`，七件逐byte一致。body为`[141138c70,141138d3c)`204B，SHA `ba5cd6de5a0f86c0d054b57c769670a3c97b8b11c6fffeefcd80601ecb5eafa0`；52条连续原hex、12引用、0RET、0外部无条件tail。原pure audit SHA `8dde8673283021802b70ed03a103767186631b7d0aa5d2345388c739cd73edbd`，Terra6门禁和来源账见原窗专题。本轮只核原件/完整hash、连续重建、三条外branch目标、末指令/正常fallthrough和五stock同源，不重复采集/测试。

来源检查：`local/migration72/d02-resume-20261111c/scene-read-core-semantics/semantic-source-check.private.json`，**6035B / SHA `9df651960e798d58c4ade554c72c3aec03292dc2c662fd940ff26a204939ec4f`**。0新PE/callee/global内容/CPU/live。定向检查旧resource/index/provider/blob/payload/index/locator、scene-runtime、astra原件及工具/文档，没有找到d3c/f85完整raw；结论限于所查范围。

## region内已证参数和控制流

入口四实参为This、dst、O、K。`8c88..8c91`将K/O/dst/This分别保存至RBP/R13/R12/RDI，EBX清零。当前调用链的K此前已过正数门；本region没有再次检查K或写入dst。

| 原PC / 条件 | 观察 | 仍未知 |
| --- | --- | --- |
| `8c83/8c94/8c96` | 读I32`This+40`，signed≤0直接到`8cff`；正数进入三CALL前缀 | 不将该字段命名compressionType/状态标志 |
| `8ca4` | 以栈临时地址和RIP地址`143b14ad8`调用`140d12e90`；返回值首QWORD为零时，后续实参改用返回地址+8 | 返回对象/内容语义未审，不能凭形状称字符串构造 |
| `8cdd` | 调用`1416fa740`，其中R8为RIP地址`143ad8635`、R9D=`3fc`，其它明确栈参数为1/0/0/0/byte0 | 不猜日志/异常/成功；若正常返回，代码继续共同路径 |
| `8ce2..8cfa` | 栈临时QWORD非零且另一个QWORD大于0时，调用`141a2e5a0`；否则直接共同路径 | 只记原调用与条件，不为它提供有利结果 |
| `8cff/8d07` | 保存原RSI及原R15后才进行下述门 | 这是同一活动frame内的继续，不是另一调用入口 |
| `8d0f/8d12` | dst=0时跳出region至f85 | f85是否返回0/报错/其它行为未知 |
| `8d18/8d1c` | unsigned O≥QWORD`[This+30]`时同样跳f85 | 仅证明无符号界限门；不把字段直接称资源总长或此边称EOF |
| `8d22..8d34` | 清RDX，RAX=O，对QWORD`[This+28]`作无符号DIV；保存quotient到RSI、K到R15，quotient bit63为1时跳f85 | 没有软件除数零分支，也不在本region执行目标/猜其取值。除数和商的业务含义未闭 |
| `8d3a` | `mov ecx,DWORD[This]`，指令结束为`141138d3c` | 无跳转/RET，正常路径必继续落入未保存区域；下一指令未知 |

三处f85边共享已分配的frame，但不共享同样的中间寄存器：dst零、O越界时RSI/R15仍为先前值；商符号边发生在它们已被改为quotient/K之后。不能拼一个有利的f85返回model。范围内对dst只有保存和判零，没有实际byte写；也没有K递减、读数累加或向后循环。

## 正常落入d3c的准确frame

以core入口RSP=S计，五次push和`sub rsp,a0h`之后当前RSP=`S−c8h`。原RSI保存在`[当前RSP+e0h]=[S+18h]`，原R15保存在`[当前RSP+90h]=[S−38h]`；其它原非易失寄存器按前缀push保存。入口dst还在`[S+10h]`有原store。

在dst非零、unsigned O低于`[This+30]`、DIV完成且quotient bit63为0的普通路径：

- RDI=This，R12=dst，R13=O，RBP=K，RBX=0。
- RSI=RAX=quotient，R15=K，RDX=remainder；O、K仍完整64位。
- RCX=zeroExtend32(DWORD[This])；最后MOV不改变标志，RFLAGS仍来自`TEST quotient,quotient`，SF=0、ZF由quotient是否零决定。

这只为静态续段解释提供来源，不能将d3c当可独立调用的callee、重建一个新栈或运行夹具。This字段、真实对象来源/寿命、arrayLenHelper、reader初始base等未知不被这些寄存器关系补齐。

## 唯一必要源规格：正常续段的containing region

request：`local/migration72/d02-resume-20261111c/scene-read-core-semantics/normal-continuation-region-request.private.json`，**17425B / SHA `7c91fa80af6ac8beecdff1f6e531470b861a51f42182ae71e30c802ffed08a89`**。主线接收204B集成第三后才可派采集，本代理未执行。

| 项目 | 必须按真实结果处理 |
| --- | --- |
| 控制源 | 固定204B/52条及末`141138d3a:8b0f`结束于d3c；新PE仅核`141138d22..d3c`26B `33d2498bc548f777284c8bfd488bf04885c00f884b0200008b0f`。不是E8/E9，不造method entry/AOT |
| region选择 | 固定exception目录14344080B及SHA；对PC`141138d3c`要求**unique containing**：Begin≤PC<End。真实Begin可不同，exact命中单独记录、可为空，不作为入口门 |
| body前诊断 | 先保存完整exact/containing原记录、PC覆盖、实际Begin/End/size、与旧`[c70,d3c)`交集、完整region预算和distinct-new范围/字节数、节及file-backed检查、12B pdata/pre-body |
| 交叠/预算 | 若与旧204B有交叠，本包0body返回真实诊断，由主线按实际范围另判；不伪报缺exact、不重读旧段或猜拼。仅真正新增且无交叠的唯一完整region≤8192B/2048条才读；end仅用原record，不读邻域 |
| 解码/产物 | 从实际Begin连续覆盖完整region，d3c须为指令边界。保存CALL/branch/RIP/RET，并明确外部条件边、无条件边及region末仍有的正常fallthrough；不能把“完整region”写成“逻辑read已完整” |
| 验收 | 固定source/profile/五stock/context、unique containing、exact可空、交叠、预算/节/短读/连续decode门禁；双fresh、纯artifact audit和主线第三。新增框架/xdata聚合为0 |
| 停止 | no unique containing/交叠/超预算等保留原诊断；只选一个region。f85若未被此实际region覆盖仍未知，不另采其region；首个必要CALL/data/control边保存即回主线 |

d3c优先于三个前缀callee或单独f85，是因为它直接承接普通读取路径，当前还没有任何字节/循环/返回合同。若所选真实region恰好也覆盖f85，可按原分支状态分别解释；没有覆盖就保持未知，不把“文件内可定位”虚报外部blocked，也不机械展开全部native库。

## 本包边界

只新增本页与独占忽略目录的来源检查/request；另按主线明确授权，对[208B语义专题](CLIENT_72_D02_SCENE_NATIVE_READ_SEMANTICS_20261111C.md)作两段最小文案修正：本地idx/tag用低32位，但完整T在未审`141139030`前未改写，故不能推整个API高32无关。未改raw/spec/算法，未重跑来源链。

其它tools/专题、Project/Harness/HANDOFF/config/Proto/patch/resources/Git只读；0runtime/model/CPU、构建/Java/DB/端口/client/调试器/部署，无需重启。没有新安全拒绝；准确A/B拒绝CALL/data边保存并停对应依赖，新拒绝原提示保留立即停，不换工具/模型/代理或间接重试。自然reader/Scene/Proud/resourceReady均未晋级，主线负责后继派工和本地提交。本有界项交回即释放槽位。
