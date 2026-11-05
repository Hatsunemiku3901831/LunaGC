# B-02e6：入场非空嵌套的限定wire闭包

2026-11-06。本项继续[入场通知reader](CLIENT_72_SCENE_WAIT.md)，固定同一国服7.2三输入，恢复此前仅空载体的五个外层嵌套及必要子类型。**只确认原始声明/字段/wire，不晋级业务名、服务端生成或D-02实机。** 生产Proto未改。

## 固定声明与字段

`trace_client72_scene_nested.py`重新提取类型、方法和metadata usage，source与destination分别解码；`client72_scene_nested.py`只新增限定存储适配器，不改既有reader/normal/query/list工具。6个类型共15实例字段，构造器、writer、reader均核对owner、地址和参数数；完整代码范围/SHA-256记录在摘要。

| 类型 | 字段存储→tag | reader / writer |
| --- | --- | --- |
| 66040 NAECHMKLBEI | bool +18→3 | 153F9FA70 / 153F9F920 |
| 36137 HIGIKLLJCHJ | uint32 +18→2；bool +1C→3；uint64 +20→1 | 14EF01160 / 14EF00CF0 |
| 67761 HKDMELCIJGB | uint32 list +18→10 | 14BF3A8B0 / 14BF3A9A0 |
| 25866 ABJMLEPLFJG | uint32 lists +18→1、+20→7、+28→6 | 14BCF8CF0 / 14BCF8B20 |
| 34133 HJLMHFHHJKM | string +18→13、+28→6；36137 +20→1；bool +30→15 | 149B5ECE0 / 149B5F0F0 |
| 69612 PCCNENMODDD | uint32 list +18→13；25866 +20→1；uint32 +28→11 | 14D0E9630 / 14D0E9BC0 |

六个type-info cell分别有原始usage行、source index与解析类型。36137使用row187787，其他重复usage指向同一source/cell，不据此猜调用可达性。三段真实cctor实际产生uint32 codec的82、58、50、10、106 tag参数及对应GLOBAL存储；不是按旧Proto编codec。

## 实际执行及独立验证

13个原生writer样本包括六类型的非零/零以及36137的uint32/uint64最大值；全部与独立protoc 3.18.1编码逐字节相同。[研究Proto](../protocol/cn-7.2/scene-nested-research.proto)只有这些已观测字段，不把未恢复内容声明为空message。

24个reader样本包括上述13组、外层26字段及8个非空子对象、独立手写bool false/2归一、错误wire跳过、独立双最大值、packed/unpacked/混合重复列表、重复子消息与显式零合并，以及一个明确输入模型边界。非空字符串含中文、emoji和NUL；内层uint64为FEDCBA9800000020及FFFFFFFFFFFFFFFF，外层为FEDCBA9876543210。所有对象结果由独立输入期望断言，不用writer输出反推期望；三个旧float对象按原始offset/bits核对，不命名坐标业务。

重复外层34133时，原生reader继续消费已有子对象：uint32显式零、空string和false覆盖旧值，未出现的uint64/string保留。25866与69612的列表/嵌套均为非空，重复顺序和uint32高位保留。错误wire的66040字段实际被跳过而保持false，没有误称成拒绝。

3条拒绝路径明确分开：负子消息长度和递归上限实际进入原始错误helper（诊断helper仅抛错）；物理截断输入在显式raw-byte输入模型中拒绝，不能冒称原生缓冲补充/EOF已恢复。

## 构造与缓冲模型的边界

六个无参构造器都在独立消息夹具上执行原指令，默认结果保存在摘要。父保护reader的子对象分配在当前元数据模型下没有观察到完整构造调度；初次直接零分配使67761的list为空并触达原始null helper，失败日志已保留。后续明确在分配边界提供**由分别执行的真实构造器得到的默认对象存储**：空string、独立空列表和已观测标量。没有把未知构造分发模拟成原生执行；父构造调度和真实managed ABI仍未恢复。

149B5ECE0的lazy metadata开关作为已初始化输入，IFix保持关闭。真实ReadMessage、长度/递归控制、六类reader、uint32 varint/列表体及字段写入执行原指令；managed分配、字符串转换、数组及writer的字符串/子消息字节/packed发射仍是明确模型。writer的字段选择/tag路径执行原指令，独立protoc另验编码。

`length=7FFFFFFF`但物理没有子内容的样本在当前stream/EOF模型下返回一个默认子对象，已单列`INTMAX-empty-child-fixture-accepted`；不把它算成安全拒绝，也不据此认定真实客户端接受畸形包。物理buffer refill/EOF、保护父函数构造调度、IFix和完整SDK/网络/入场均在范围外。原来的native构造分发与越界拒绝预期失败日志保留，没有放松成统一“成功”。

## 复现与证据

```powershell
$sceneClient = & ./tools/game_path.ps1
$sceneProtoc = (Get-ChildItem local/gradle/caches/modules-2/files-2.1/com.google.protobuf/protoc/3.18.1 -Recurse -File -Filter '*.exe' | Select-Object -First 1).FullName
python tools/trace_client72_scene_nested.py --client $sceneClient --output local/migration72/evidence/scene-nested-final-trace.json --summary-output protocol/cn-7.2/scene-nested-observations.json --proto-output protocol/cn-7.2/scene-nested-research.proto --protoc $sceneProtoc
python -m unittest tools.test_client72_scene_nested -v
```

8项回归读取真实共享证据并验证：固定wire向量、输入hash/未知语义、覆盖缺失、字段/tag篡改、native reader/独立构造遗漏、内外64位截断、native与模型拒绝区分及已接受边界披露。两次完整trace、摘要和Proto逐字节相同；trace SHA-256 04bf9078…7016，摘要ae91950a…3098，Proto316fa5fb…1e35。原始证据均在忽略local/migration72，入口scene-nested-determinism.json与同前缀日志。

共享[观察摘要](../protocol/cn-7.2/scene-nested-observations.json)为第63份研究输入；人工复核时全部源码/Proto、三层矩阵、候选/描述符及开放边界保持，recover两次一致。只有研究工具/测试/Proto载体/文档变化，无需重建或部署；当前运行构建另按E-01c4核查。B-02e及D-02父任务未完成。

完整整合回归：366项研究测试全过，研究结构0错误、ready仍拒绝228缺口。13writer/24reader/3拒绝共40个限定执行结果与Proto均重复相同；不能将它们统称40个完整native成功或实机样本。
