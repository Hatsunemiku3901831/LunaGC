# B-02e7：69612子对象的原生部分消费

2026-11-06。接续[嵌套reader](CLIENT_72_SCENE_NESTED.md)与[列表消费](CLIENT_72_SCENE_LIST.md)，新增独立scene-data工具，在同一离线CPU上延长正常场景路径。**本项只确认实际执行到的部分消费与下一入口参数；没有执行完整消费者返回、25866后续消费、完整入场或D-02。** 未命名未知字段的业务含义。

## 原始声明、取参与代码

每次CLI先校验`extract_client72.HASHES`中的三个固定客户端文件，再重新提取metadata/声明。实际SHA-256为：

| 输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | 8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5 |
| global-metadata.dat | 45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a |
| startup-metadata.dat | b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105 |

type61485的method510062/MILNAHNKJMA，入口`153A16AA0`，声明的两个参数精确为type69612/PCCNENMODDD与bool。原正常分支`14CD67467`的12字节`488b56484531c0e82df6ca06`实际是从原消息+48取对象、传false并调用该入口；返回地址为`14CD67473`。工具同时验证字节与相对调用目标，未用旧方法名猜语义。

`153A16AA0`在对象为null或原始+28为零时早退；其余路径读取原始+18列表，将owner、原始uint32 +28、列表指针及bool传给`153A16B30`。后者和`153A3CD60`的三个声明参数均为uint32、type35584<uint32>及bool。owner+1F8字段AJNGOCGNOCN精确声明为`Dictionary<uint32,List<uint32>>`；与此前owner+60的外层缓存独立。

完整三个函数均已反汇编读取，代码范围和SHA-256随报告输出：

| 函数 | 字节数 | SHA-256 |
| --- | --- | --- |
| 153A16AA0 | 144 | 7c7e9fad35fe5c920ffd674503b4c22f21cf49437185b6b2dfc2fbe70a66823b |
| 153A16B30 | 1760 | 0968c36a87566fbd2becf1bdcf3eca9cb85f32957416a5b276f525962e6aa45e |
| 153A3CD60 | 3312 | ac57f010cec22fabf9666f984563711e5a49801e52db3473e124a58e024c446c |

## 实际执行的范围

支持的正值样本给原生query提供明确的缺key存储，query实际返回零。这里的“query零”与69612原始+28为零的提前返回不同。原生代码进入`153A3CD60`，查owner+1F8的已有缓存List，清size、增加version，再由原生`15881F800`执行InsertRange。缓存初始为`[99,88]`、capacity8、version9；实际结果独立检查逻辑列表、整个原始数组、size/version11、List及数组身份和字典中的key/value。

空输入只清size，旧数组中的99/88保留；非空输入按原顺序复制，含重复、显式零、80000000及FFFFFFFF。原始+28覆盖17、80000001、FFFFFFFF，并按原值传递，没有观测到这段对它做算术变换。bool false/true在三个原生入口均实测保留；其后依赖bool的其他系统路线未执行，不能据此声称两种业务效果已恢复。

随后原调用在`153A3D933`进入`14AF3D130`。工具在执行第一条指令前停止，记录实际List指针、内容及原始+28；不会给该函数伪造返回。主线另从固定声明核对该函数为type26814的static方法，参数是`List<uint32>`和uint32，并非只有单个数值的包装函数；本工具没有扩展其body。

`153A16AA0`原本在第一个helper返回后还会读取GLOBAL+10C50，并将原始+28与原消息+20/type25866传给`14FCDCAB0`。正值样本目前停在更早的`14AF3D130`，因此25866虽然由真实reader产生了非空对象，却**没有执行该下游消费者**；标志`child25866ConsumerExecuted=false`按此范围保留。

## 同reader、构造与接口模型

`SceneDataReader72.run_scene`只把调用委托给`SceneNestedReader72.run_message(SCENE,wire)`，使用原CPU及原对象，返回通用Graph视图。三个连续场景样本延长既有fixture的停止点，实际执行原scene reader、Stage10、任务创建、query、外层非零列表消费者，再从原消息+48进入本项消费者。未在中途切换Python对象或另造一个返回对象；逐样本检查原+48指针与完整消息内容保持。

69612使用手写wire：+18列表tag13、+28标量tag11、+20子对象tag1。25866的三组列表分别使用tag1/7/6，输入为`[11,80000000,11]`、`[0,FFFFFFFF]`、`[23,23,80000001]`；同时有空子对象。重复列表另用packed/unpacked混合输入。输入不经原生writer生成；单测另列16个显式输入期望和固定hex向量，核对真实reader结果、原生输出存储及未知入口实参。

六个嵌套类型首先严格按`66040,36137,67761,25866,34133,69612`顺序分别执行真实无参构造器与空reader。父分配边界只提供这些已观察的默认存储。保护父函数的完整构造调度、实际managed ABI及物理buffer refill/EOF仍未恢复；Graph适配不改变这一限制。

query字典、owner+1F8已有缓存、初始化metadata/class/interface与数组为显式输入存储。连续场景中外层query缓存仅含key17，+44真实枚举count明确设为1，本项另用23/高位key或原始零，保持查询输入一致。字典查找、三个消费者、List clear/InsertRange、类型关系与接口搜索均执行原指令。

managed ICollection.Count/CopyTo仍是边界模型：它们只读取原reader对象的实际列表/数组及调用参数，将实际字节写入目标数组；不按expected列表生成输出或直接返回消费者成功。辅助模型允许同一CPU上的外层列表与69612内层列表，分别记录`dataList`来源。数组分配沿用既有明确模型，没有改共用工具。

## 结果与复现

16个限定结果为：7个下一helper入口、4个早退、2个原始null入口、3个IFix入口。两个null样本分别缺owner+1F8字典及缓存条目为null，实际callsite为`153A3D65B`、`153A3D9B6`。三个IFix分别在`153A16B1F`、`153A16D0C`、`153A3D651`转入原始patch入口。上述错误/IFix均在入口停止且不执行body；缓存尚未改变。不能把16项统称16次完整消费成功。

10项新增纯validator测试不依赖新增共享摘要，与旧list/query/normal/reader/born/continue/stage/nested合计81项通过，检查独立向量、真实reader/构造缺失、内容与高位篡改、bool/指针、原数组尾部/字典/版本、原始错误位置和不得越过未知body等。首次写入的两个literal hex测试期望长度有误，已按独立varint长度更正；两份失败日志`scene-data-unit-{first,second}.log`保留，原生输入与实现未因这两条测试失败改变。

正式16样本实际执行两遍，完整trace及摘要逐字节一致，见`local/migration72/evidence/scene-data-determinism.json`。trace SHA-256为`17b599097d7ee166785a716eb389c8f79b5eb5c359e406a13ac5ed4053552068`，draft摘要为`f64f6d77945596ac6a79e82dfddbed5506e9cca362a59623c2e75b0c90ce09c1`。首轮较少断言的报告另保留`scene-data-first-{trace,summary}.json`，以正式报告为准。

原list24、query19、normal14、scene-wait32、born39以及nested的13 writer/24 reader/3拒绝均实际重跑；六份摘要与既有共享文件逐字节相同，nested研究Proto也一致。回归索引为`local/migration72/evidence/scene-data-regression.json`，日志均使用scene-data前缀。没有把截断输入模型的拒绝改称原生EOF已恢复。

```powershell
$sceneDataClient = & ./tools/game_path.ps1
python tools/trace_client72_scene_data.py --client $sceneDataClient --output local/migration72/evidence/scene-data-final-trace.json --summary-output local/migration72/evidence/scene-data-summary-draft.json
python tools/trace_client72_scene_data.py --client $sceneDataClient --output local/migration72/evidence/scene-data-repeat-trace.json --summary-output local/migration72/evidence/scene-data-repeat-summary.json
python -m unittest tools.test_client72_scene_data tools.test_client72_scene_list tools.test_client72_scene_query tools.test_client72_scene_normal tools.test_client72_scene_wait tools.test_client72_born tools.test_client72_born_continue tools.test_client72_stage_timing tools.test_client72_scene_nested -v
```

本项仅新增离线工具、测试和专题；原始数据/日志/草稿留在忽略目录。未改生产Java、Proto、配置、资源、共用工具或三层状态，没有启动服务/数据库/客户端，无部署和新增实机验收。按最新用户要求，完成本项提交后暂停；后续需先恢复`14AF3D130`及其依赖、25866消费与完整场景后缀，不能据本项解除D-02前置。
