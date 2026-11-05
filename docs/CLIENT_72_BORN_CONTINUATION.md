# 国服7.2选人成功继续：B-02e1限定证据

后续B-02e2已恢复46311/27748的PlayerEnterSceneNotify身份、26外层字段及原生clone/等待，实际网络分发先经1507AD120包装；本页下方“下一项”的旧线索以[入场通知/缓存等待](CLIENT_72_SCENE_WAIT.md)为续篇。原33样本重跑摘要不变；正常入场/五类嵌套/包装有上下文和真实异步仍未完成。

2026-11-06。B-02e1闭合的是固定目标的同步继续、Action绑定和明确异步入口。**B-02e父任务、完整初始化次序与D-02实机未完成，runtimeStatus仍为not-tested。** 本批没有启动客户端或服务，没有修改运行Java、Proto、补丁、配置或资源；离线工具/测试/文档不需要部署。

入口：[选人三件组](CLIENT_72_BORN.md)、[取证工具](../tools/trace_client72_born_continue.py)、[脱敏摘要](../protocol/cn-7.2/born-continuation-observations.json)、[回归](../tools/test_client72_born_continue.py)。每次工具运行重新`read_verified`核对EXE和两份metadata的SHA-256，再`extract(..., structures=True)`恢复声明；与原B-02d完全相同的三输入。方法、类型usage、Action MethodInfo、字段偏移分别核对，不从临近指针猜类型。

## 已执行的同步路径及其限制

`SetPlayerBornDataRsp`原生reader `15374AC20` → Handler `1507D2820`。retcode=0尾跳`14CD68860`（type18753/LKMLKAHBGDN.NCJHAFCKKDH，method142815）；116、132、-1仍进入原错误分支，132另发850事件。错误路径没有本批新发送或本地继续状态写入。

1. `14CD68860`先调用`1507CB3B0(16, true)`。网络owner的`+150`为空则原生直接返回；非空时实际分配type61311、写字段并进入已核实发送入口`15120D730`。发送入口在本批是观察终点，没有执行网络队列、socket或SDK。
2. 查询`GLOBAL+9E60` UI的`+1E8`字典。结果必须与usage cell `1458F4C90`绑定的type36626/LFFDFBPNCPN命名页完全匹配，才进入`14CD68070`，并调用命名`ClosePage@15245B6B0`。无页和类型不匹配走直接继续。匹配页样本在真实ClosePage入口停止，没有伪造UI关闭成功。
3. 页面分支在helper返回后创建Action，usage row263669/cell`145A1FC28`指向`14CD62270`，该方法原生尾跳`14CD62280`。分段执行`14CD6899D`后缀确认调用`14EE38F40(timer, duration, action, true)`；duration由page+240对象的+2C浮点值提供，缺view或view+10为空时为0。0和2.5均有样本。**只验证登记参数，未执行Unity定时器或触发该Action；不把分段后缀冒称从关闭页到回调连续完成。**
4. 无匹配页时直接执行`14CD62280`。owner+48由声明确定为type68442/CCCNNCMENIL；其+18布尔选择两个目标字面量：1对应`MDAQ001_OPNew_Part2_PlayerBoy.usm`与数值300010，0对应Girl文件与300011。报告称`bornFlag18`，不把其来源擅自等同于已恢复的avatar→性别链。函数写born+1A=1，调用明确记录的UI/resource观察入口，并创建三个Action。
5. `1523F1120`检查type55558/KKDGDFHIHDB的+63。0时原生返回，不排媒体任务；1时进入`14C4F1CA0`配置转换。两个标准样本在真实转换入口停止。两个额外样本使用**明确为空的配置表**：`1517355D0`存储模型返回null，原生转换实际返回null；随后原生`1523F1350`和`1503536B0`构造type23519/DGAEHPFHIHL，并在明确托管Queue存储模型中登记。没有给配置查找填成功，也没有执行媒体播放。
6. 同步尾段创建`List<uint32>[351]`并调用`15347B8F0`，经缓存的本地数据对象更新列表和`GLOBAL+A070`对象的+3D=1。托管列表操作为显式存储模型，`14A9CE8E0`缓存命中分支原生执行，不访问真实存档。**缺born只记录错误后仍执行此尾段；媒体关闭或空配置入队后也执行此尾段。因此这些本地状态和同步返回都不能证明角色初始化、影片播放或世界入场成功。**

IFix开启的独立样本在真实wrapper `14D8DE310`停止，既不替换wrapper返回值，也不把固定EXE里的无IFix分支视为实际客户端运行状态。

## 新发送的三层证据

这条消息只增加限定研究摘要，不修改mappings、生产包号或Proto；原188字段及41V/9C/138U矩阵不因本批改变。

| 层 | 本批事实 | 尚未确认 |
| --- | --- | --- |
| CmdID→类型 | usage row704573/type cache cell1458A50E0→61311/ALJAMAECHIP；原生分配后进入15120D730；getter150976530实际返回20353 | 实际传输域/完整帧、网络到达、是否必需及服务器行为 |
| 类型→语义 | 选人Rsp0继续入口以16和true调用1507CB3B0；+150为空时不发送 | 正式消息名、字段业务名；不猜成某个旧协议消息 |
| 字段→wire | +1C/DBCJNBDFEOL为uint32 tag11；+18/BNJKCHONGGE为uint32 tag14。writer150976540、size1509767B0实际执行，独立手写varint与protoc对照 | reader/接收业务不在本批；该消息无声明64位/嵌套/list，不能借其他消息填入 |

目标调用的wire为`58107001`；uint32最大双字段为`58ffffffff0f70ffffffff0f`。5组覆盖零、目标16/1、127/128、uint32最大值和高位边界。它们是实际原生writer输出，不是同schema往返自证。protoc只在临时研究目录生成两个匿名字段的独立规格，未写生产Proto。

## 回调与最早未闭合入口

三个Action通过独立method usage恢复，实际闭包保存owner原指针与影片字符串原指针，原生媒体任务构造仍保存**原来的Action对象**：

| usage cell / row | 目标 | 媒体任务字段 | 本批执行范围 |
| --- | --- | --- | --- |
| 145A1FD38 / 263792 | 14B68DCB0，type18759/<>c | +58 | 绑定和登记；该UI回调未触发 |
| 145A1FD40 / 263793 | 153D70F80，type18766/MDMLBJBPHKF | +50 | 手动离线调用真实函数，清born+19/+1A，写UI+33C=3，转14CD67260 |
| 145A1FD48 / 263794 | 153D71010，同一闭包类型 | +38 | 同上，独立验证；不猜两个回调各自是结束、跳过或失败 |

两回调都从born+10取出type46311/BBECFDMDFJC对象，传给`14CD67260`。空闭包owner/空born进入原生拒绝helper `1404C2BE0`。原生场景函数在正常继续分支先到已核实请求入口`14C8AE9A0(mode=10)`。缺scene对象的独立样本，仅把该请求入口和日志作为观察sink，随后在真实null拒绝入口停止；未用空对象或全零schema替代缺失消息。

**最早真实阻塞是异步调度与接收对象来源：**页面关闭后的Unity定时器、媒体队列何时触发哪一个回调，以及期间哪些网络消息先写入born+10和其他初始化状态，均无实机/真实收帧证据。现有样本只证明数据依赖，不能推断PlayerLoginRsp、BornRsp与首批通知的实际总顺序。实际SDK/IFix和资源前置另由D-01/E-01处理，本工具不能解除它们。

### B-02e2可直接执行的线索

固定目标type46311的getter为`1523856A0`，本批原生返回27748；reader slot19=`1523856B0`，size=`152386290`，clone=`152386C00`，CopyFrom=`152386CB0`，ctor=`152387890`，cctor=`1523876A0`。本批只确认声明和getter，**正式语义与wire仍unknown**；完整26实例字段和1静态codec声明保存在摘要`declarations.sceneObject`，包含uint64 +68、泛型uint32集合+20和多个嵌套对象，下一项不能仅测试零值。

已定位的缓存写入在`14CD672B4`：born+19非0时调用该消息的真实clone，把返回值写born+10；born+1A为0则返回等待，为1则清两个flag、写UI+33C=3，并循环进入后续场景处理。这段本批保留为固定目标反汇编线索，未执行完整clone/复杂场景对象。

直接call扫描发现`14CD67260`的另一个调用来源：网络owner56238的方法467802/LDLBKNPDDNK，入口`1507AD6F0`，调用点`1507AD854`；另两处为本批回调`153D70FC1`、`153D71051`。定点反汇编确认输入rdx先保存在r14；`1507AD7B8`调用`1507B32D0(this,msg)`，AL非零则返回，这是该候选网络Handler进入场景函数之前最早未解业务门控。其后才将原r14传`14CD67260`；回栈尾段还调用`14FCDCAB0(msg+28)`、`153A41650(msg+58)`、`1523F15F0`及事件109，不能只验证中间调用就称完整Handler返回。扫描/反汇编是入口线索，不替代接收注册、参数绑定或调度证明。下一项应先核对该Handler参数、27748注册/分派与reader，再做独立非零/uint64/嵌套/list/error/boundary样本，并恢复born+48创建及+18/+19的设置来源。不能把服务器当前发送次序复制成目标规格。

`14CD67260`正常分支的最早外部请求依赖是`14C8AE9A0(10)`；随后需要实际scene对象，第一次标量访问为+94，经`(+0x5889)^0xE011`后进入`14CD5F7B0`。后续消费+18嵌套坐标、+7C、+20、+48、+98枚举及+A0等；这些转换常量和偏移仅作定位，尚不能给字段命名或生成wire。

## 实际验证与复现

33组固定目标原生样本：5 wire、11 reader→Handler→继续分支、4定时器后缀、6回调/空依赖边界、4回调→场景入口/缺scene拒绝、2空媒体配置→真实任务构造及Action排队、1 IFix分流。新9项研究回归检查独立wire、错误不继续、空上下文不发送、空回调不成功、定时器/IFix/场景边界和媒体Action保留；没有写“返回true”来代替业务成功。

新9项与原born6项共15项聚焦回归通过；原born39组重新执行通过，摘要与原提交逐字节一致。本批33组trace与摘要二次运行也均逐字节一致。首次完整研究回归281项：280通过、1 error，错误为`test_client72_login_proto.test_real_matrix_reproduces_and_keeps_all_layers_unchanged`的`Dependency input set changed; review again`。新增本摘要进入研究输入集合，公共依赖review/矩阵需由主任务按原门禁复核后重生成；没有跳过、删除测试或放松校验，最终公共矩阵状态以Project/Harness为准。

```powershell
$env:GAME_PATH = & .\tools\game_path.ps1
$bornContinueProtoc = (Get-ChildItem local/gradle/caches/modules-2/files-2.1/com.google.protobuf/protoc/3.18.1 -Recurse -Filter '*.exe' | Select-Object -First 1).FullName
python tools/trace_client72_born_continue.py --client $env:GAME_PATH --protoc $bornContinueProtoc --output local/migration72/evidence/born-continuation-trace.json --summary-output protocol/cn-7.2/born-continuation-observations.json
python -m unittest tools.test_client72_born_continue tools.test_client72_born -v
```

本机完整trace、第二次trace/summary、确定性比对、旧born重跑和测试日志保留`local/migration72/`忽略目录。共享摘要只包含输入hash、目标地址/声明、脱敏向量、模型边界及执行路径hash；不包含客户端二进制、原始抓包、真实凭据、机器路径或运行PID。

主任务复核收口：公共门禁按原规则接纳本摘要和独立场景/启动摘要，仅新增三份输入（47→50），原源码/Proto和三层矩阵内容均不变；两次矩阵生成相同，包含新增资源7项后的完整研究选集为288项全部通过。首次281项中1项漂移拒绝保留于原日志。D-01实际客户端启动由主任务另做，不能混入本工具的33组离线或runtime状态。
