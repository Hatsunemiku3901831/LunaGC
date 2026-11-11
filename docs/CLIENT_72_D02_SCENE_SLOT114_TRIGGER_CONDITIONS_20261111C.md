# F-03d3s2 / SCENE-SLOT114-TRIGGER-CONDITIONS

2026-11-11C，父项D02-R1。**当前没有可执行的World同帧观察包，不能把三读224B标为已准备或只差ready。** 固定原件已经给出准确caller与factory寄存器流；缺的是独立合法自然触发、受支持的精确停点/frame交付、World专用返回/退出恢复合同和整轮API预算。本轮没有实现观察器，也没有申请现场。

本结论只覆盖本页列明的现成材料与工具。沿用[slot114来源审计](CLIENT_72_D02_SCENE_SLOT114_20261111.md)、[后继独立性](CLIENT_72_D02_SCENE_READER_FACTORY_SUCCESSOR_20261111B.md)、[World实际来源](CLIENT_72_SCENE_RUNTIME_CONTEXT.md)及既有GitHub/仓外成果，不重做36读缺槽证明、资源解析或新的外部大检索。

## 准确自然路径与同帧问题

自然路径的已存静态来源如下；这些CALL关系不是本轮观察到的执行：

| 来源 | 已证关系与未证前置 |
| --- | --- |
| 21761 / type34208的处理入口`1507d1620` | `1507d1700→1482879c0`读取真实SceneScriptData/type45183的stage条件；World为enum66233数值0。SceneExcel的SCENE_WORLD=1、scene ID3或SDK登录页不能代替此状态。 |
| 同handler后继 | `1507d17fd→14cd68530`；其中`14cd6868c→14828ba00`消费同21761的root/tag14、type44512。此前能力注册、列表/本地角色及加载任务等成功条件仍不能由本页填空。 |
| `14828ba00`的两个分支 | 44512的`+20/+24/+18`进入现有实体查找及能力处理。已有实体路径先`14828bab6→148d813d0`；缺实体路径才`14828bbb1→15154bf20`。不能删除/替换实体或伪造miss来制造自然触发，第二U32仍不命名为host peer。 |
| World factory `15154bf20` | 实际模板90000004及MPLevel配置来源已部分恢复；Pool、接口回调、配置返回、模板关联及自然IFix选择还须实际成立。host解析出World21名称不是这些调用已执行。 |

来源是固定`protocol/cn-7.2/scene-deep-observations.json`（SHA `bed41fc74af5ef39b951c6d57834c10a56b0577da1166957d5221f19ada4acd5`）的原CALL索引，以及固定完整factory报告`local/migration72/d02-active/main/scene-runtime-context-third.json`（SHA `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`）。既有场景deep样本把stage/managers作为显式fixture，未取得自然World stage和对应21761；本轮不重跑它们。

完整factory原body为`[15154bf20,15154c2f0)`，SHA `1d46e69c9304becd4b7efef4671bf31541fb433e6e55e4a58655f5653df08504`。已存原hex连续重组后，本轮核以下坐标：

1. `15154bfc6`原`ff90f0000000`调用实际Pool klass的slot4；`15154bfcc`原`4889c6`把返回RAX保存为RSI。到后面的slot114普通调用前，没有替换RSI的显式指令；中间各callee正常返回仍是必要前置。
2. `15154c062/c069`检查原开关并可转IFix支路。未观察自然值，不能强设0来保证普通路径。
3. `15154c06f`原`488b06`读取`[RSI]`到RAX。**到`c07e`时RAX是klass，RSI才是实体**；不能把此时RAX写成Pool返回对象。
4. `c072/c075/c078/c07b`依次准备`RCX=RSI`、`EDX=R13D`、`R8D=R14D`、`R9D=0`；`c07e`原`ff9060040000`调用`[klass+460]`。其机器级正常返回坐标是`15154c084`，不代表本轮已经正常返回。

真正同帧输入必须绑定当次目标创建身份、实际模块基址、事件/当前线程、精确RIP及factory frame，保存上述寄存器和Pool返回对象来源。只见请求class46314头或启动primary的TEB，均不能证明RSI来自该次Pool返回。若只依据固定普通原码把RSI追溯到`bfcc`，应标静态推导；不能写成实际Pool返回已经观察。若要取得独立返回观测，必须另有该次`bfcc`的合法上下文来源，并将其纳入观察点与总预算，不能偷偷增加第四项采样。

## 准确拒绝依赖边与独立性

本轮在既存CALL索引遇到`14828ba31→1405336c0`，目标与A已知allocator入口相同；原Pool资料另已记录`14050ab6e/14050ac0d→1405336c0`。**已停止这些边的返回/归属分析、主动触发、离线补成功和实现依赖。** 没有读取该callee、运行草稿、调用目标或借World验证它；也没有把同入口直接宣称为被拒的那一次“第二返回”。该次调用的具体返回/归属关系仍未知。

删除A第二返回/Default同epoch及B组合loader动作、产物后，本轮的固定来源核对、factory静态坐标和工具能力审计仍能独立完成。**删除后尚没有独立可执行的主动World frame取得方案**，因此不能派一个以“自然触发”为名继续穿越该边的包。B没有为本页提供可复用的受审World观察出口；不修复、编译、装载或借用其组合loader。

这与普通缺口分别记录：即使不考虑拒绝边界，当前也缺自然World/44512缺实体执行来源及合格同帧观察合同；即使将来有合格frame，也不能由它批准A/B的返回或归属研究。自然热Equals或Proud的7读50B不参与World，本页没有扩该现场范围。

## 现有工具能做什么、为何不能直接接

| 既有入口 | 已有能力及本题边界 |
| --- | --- |
| `tools/sample_client72_entry_context.py` | 从新原始monitor身份读取限定内存根，现成冻结样本36读2232B且非原子。源码用ReadProcessMemory，无GetThreadContext/SuspendThread或精确RIP停点来源；不能在轮询时把任意RSI当factory当前RSI。 |
| `tools/observe_client72_tls_inputs.py` / Pool collector | 持有同次普通CreateProcessW原primary handle，核TID/创建时间/TEB及限定TLS输入；不是自然World调用线程。API上界、含final的Pool预算和P+1990状态只回答自身合同，不提供15154c07e的寄存器或调用事件。不能从旧报告加读或复原frame。 |
| `tools/observe_client72_startup.py` / `client72_single_hardware.py` | 已有受控fixture入口/返回、寄存器/DR/自有点恢复能力；CLI仅software、none、global-dispatch、global-dispatch-hardware四种模式。HW固定GlobalDispatch入口1512250e0及已审返回点；没有World模式或任意地址参数。其当前源码SHA `d7ca80bbd896628e6e852fef58bc585eb2a1a2e137dabee06e51563f01681d8b`恰等下列真实失败run的sourceSHA，并非已有另一版World实现。 |
| 可见REA工具元数据 | `observe_native_calls`是Mach-O/LLDB owned启动；持久GDB/Rizin要求Linux，不能用于本项目Windows portable。Frida接口接受caller新脚本/attach或spawn，不是现成World collector；使用它需要新instrumentation、来源和生命周期设计，不能把工具名当受审同帧能力。本轮只查工具描述，未开启会话或调用运行工具。 |

原HTTP观察器有两个与本题直接冲突的具体行为：

- 它在**函数入口**读`[RSP]`为返回地址，并用入口RSP+8关联返回。`15154c07e`是factory内部CALL前，此时尚未压入`c084`；设pre-call RSP=s，正常CALL把c084压至s-8，RET回c084时RSP恢复s。不能套入口RSP+8，也不能把此处`[RSP]`当slot114返回点。原工具的参数解析、命名/返回语义同样按已审HTTP/string入口，不能改地址后沿用。
- 原HW模式每次完整snapshot已经读取stack256B、当前code64B、TEB ClientId16B，共3读336B；还调用寄存器双API、表达式、点表、身份和set/clear前后核验。224B连这一个snapshot都不包含。具体总量取决于新观察点及生命周期，现有代码没有World整轮API/事件/read上界。

因此结论是“现有已验入口没有本题可直接执行合同”，不是“Windows无法观察寄存器”。本轮不改观察地址、实现hook、debugger或collector，也不换工具重试旧失败。

## 真实观察失败与恢复缺口

[单HW前置](CLIENT_72_SINGLE_HARDWARE_OBSERVER.md)证明自有fixture的9停点和4次set/clear，但目标游戏的实际结果由[退出审计](CLIENT_72_D02_HW_EXIT.md)保留：

| 固定原件（相对`local/migration72/d02-active/`） | 关键事实 |
| --- | --- |
| `main/global-dispatch-resume1.json`，423277B，SHA `4ba34a38adbd960a033cfb1754ec1455daff804cbed8f376c0cb6f291cd4bbf4` | 同一目标专用前置续跑；约38.875秒后OS/PUB/log退出80000004，未取得自然入口/返回。 |
| `main/hw-exit-main-audit.json`，4504B，SHA `f09f1fb8aff165730cafaedeefaf437fc3aa47321f1746bdd3c38987c5fb6e35` | 54个停止、一次HW安装、0高层HW命中、无最终异常原事件／故障TID-context，rootCause=unknown。 |
| `main/wer-after-hw.private.json`及`main/wer-copy/Report.wer` | 既有审核将同目标fault RVA关联到112250e0；不是frame、参数、调用栈或根因。没有minidump。原题故障不能由换World地址视为已解决。 |

现成正常恢复合同只针对其已证明自有点和匹配事件；PUB breakpoint不独立携带原内核事件TID。现有工具以选中调试上下文、TEB和双寄存器API作限定核对，不应描述为已具World事件线程的原始context交付。历史问题还包括不完整高层事件/模块库存，不能仅用“有API连接”保证自然World frame。

World的新方案若以后成立，必须先规定普通返回、EH/未知异常、线程/事件不符、无命中超时和目标正常退出时怎样停止、保留现场、清理已证明自有观察状态，并核原bytes/GPR/flags/DR及已有进程归属；不得沿用旧PID/点表或自动认领未知点。当前这些World专用规则、受支持原事件来源及预算均未实现/验收，故本页不列执行命令或新ready请求。

## 真正的最小解除条件及可独立后继

| 缺口 | 最小新增输入／受支持条件 | 当前可做范围 |
| --- | --- | --- |
| 合法自然trigger | 独立授权环境中，同一次真实World stage→21761/tag14 typed44512→实际查找缺实体→14828bbb1的来源；不得通过改stage、补包/空对象或清实体制造分支，也不得用A/B被拒动作取得它。 | 来源已知；当前没有可批准的主动触发包，不是只待用户ready。 |
| 精确frame | 独立合法来源给出的当次模块/目标创建身份、线程、15154c07e上下文、RSI/RAX/RCX/R13/R14/RSP及其factory/Pool返回来源；或能提供这些且已独立验证的受支持观察实现。 | 三读输入消费仍可复用；现场观察实现未成熟，不交Terra。已存在且独立取得的完整frame原件可另包只读验源，不自动接受来自拒绝动作的产物。 |
| 实际slot绑定 | 同frame的`[RSI]/8`、实际klass首208B及`klass+460/8`，共3读224B；模块归属、null、不同class/target保留原结果。 | 不读metadata候选1483df040；取得唯一目标后才另定原CALL/AOT/pdata完整body预算。当前没有唯一目标，也没有本题需新取的合格body。 |
| 整轮观察合同 | 在支持的frame机制上固定总API次数、read字节、event数、时长、安装/转发/返回/EH/退出与恢复上界；224B只保留为核心数据子预算。 | 不能借现有HTTP snapshot或Pool16读98B称总量已闭；没有源码实现规格，不能机械派工。 |
| 静态替代 | 独立来源同时证明实际Pool返回对象的klass选择及其slot114初始化原bytes；仅类名、slot声明或requested class头不够。 | 本轮指定旧原件没有这套绑定；不扫描PE/新global、不猜writer/CALL/pdata。若以后有原件，先审这一个绑定合同，仍不等自然factory已执行。 |

仍可独立推进的Scene静态工作是已有reader/backend来源包，以及真实World分组/配置上游：`14aa9a14c..15c`注册传的是完整分组key及另一类型值的结构，先从已存typed/generic上下文确认类型/后缀，再定位必要配置。已恢复完整World key和21名称可只读复用；不按MPLevel名称猜hash/后缀，不重新解析已验payload，也不把配置存在当slot114调用成功。这些方向各按其自身源pin/准确首缺调度，本包没有重开它们；若其后出现A/B准确边，停止该依赖。

需新source的是实际返回类/slot或其初始化绑定的准确原件，以及上述资源类型/后缀若现成上下文仍不足的准确源；需外部/受支持执行条件的是自然World trigger与精确frame交付。用户现场不能代替缺失的技术方案，因而当前没有待用户操作或“请ready”的动作。本页未新增Daybreak派工或许可。

## 本轮实际交付

新私有根`local/migration72/d02-resume-20261111c/scene-slot114-trigger/`只含本轮来源审计和输出。`audit-trigger-conditions.private.py`只读11个固定输入，不导入观察器/采样器/runtime；本轮exit0，核完整factory重组、11个坐标、既存caller索引、准确共用allocator停止边、现有工具模式/入口返回假设及旧失败来源一致性。它不重复36读覆盖审计、不执行旧native/fixture或模拟自然触发。

`review.private.json`为6548B，SHA `b89e43451a91f53d4b62e3dd158cc822feeeed7763cc3220c2131378327f7427`；结果为`not-an-executable-World-observation-package`，`readyForTerraImplementation=false`。可复现：`python local/migration72/d02-resume-20261111c/scene-slot114-trigger/audit-trigger-conditions.private.py`。

PE/global/新callee读取、live API、进程/端口核查、调试器、目标CPU、构建/JVM/DB/部署、Git均0。只写本新专题与独立私有目录；Project/Harness/HANDOFF及旧源码/资源只读。没有新增安全拒绝，既有拒绝边保持停止。Worldframe、slot114目标、自然生命周期、Scene/resourceReady/D-02均未晋级。
