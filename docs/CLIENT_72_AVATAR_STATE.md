# 7.2 AvatarInfo tag4 生命状态消费

任务 `F-03d3f4a / D02-R1`。新增离线工具、定向测试和脱敏证据；不修改旧 Proto、raw adapter、生产 sender、opcode、mappings、ready 或共享 runtime。本卡提出字段语义供主线独立审核，`runtimeStatus=not-tested`、`targetProtocolReady=false`，不代表客户端实机入场验收。

## 目标、来源与结论范围

目标仅为 `48178 / DFENDOGMEAD / tag4 / +0xc8` → typed AvatarInfo 应用 `15184e100` 的 `15184e1e4/1fd` → `151849670`。初始候选是一般状态或生命周期状态；没有借旧 `life_state` 名称给字段配对。

每个正式进程使用当前 `tools/game_path.ps1` 结果，按 `extract_client72.HASHES` 重读固定 EXE/global/startup，SHA-256 分别为：

- `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`
- `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`
- `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`

另固定 e8 raw、f1 Avatar 合同、f2a Hero 消费、f2b SceneEntry identity、f3 Team 的既有摘要；具体五个文件/hash 均在工具 `EVIDENCE_SHA` 与新摘要 `inputContracts` 内。复用既有 reader、Avatar 构造/GUID索引/身份片段；本卡不重新批准其它字段。

现在有四条目标自身来源支持“Avatar 生命状态应用输入”：

1. 实际应用块读取 tag4/+0xc8，直接调用 `151849670`。独立参数表恢复证明该函数首参为 enum57376 `LOEACNBBAAB`，目标枚举符号为 `LIFE_NONE=0 / LIFE_ALIVE=1 / LIFE_DEAD=2 / LIFE_REVIVE=3`。
2. `151840630` setter 的参数和 Avatar model+0x2cc `BOCCNJLBFCJ` 均为同一个 enum57376。
3. setter 原生写 +0x2cc，构造事件371；独立 enum75464 的原始具名符号为 `AvatarLifeStateChanged`。其 payload 保留完整64位 Avatar GUID和状态。
4. 有实体时，目标代码构造 enum74697 的 `EvtKill=75` / `EvtRevive=82`。这不是仅从分支值1/2/3恢复名称。

wire 仍是 `uint32`，没有改成受限 Proto enum，也没有宣称恢复混淆字段原始英文拼写。IFix启用时的实际动态方法内容未知。

## 实际行为

| wire 最终值 | 原生受控存储结果 | 无可用实体时的事件 |
| --- | --- | --- |
| 省略或0 `LIFE_NONE` | 保留先前 +0x2cc | `RefreshAvatarData=16`，payload 65535 |
| 1 `LIFE_ALIVE` | 写1 | `AvatarLifeStateChanged=371` 后发16 |
| 2 `LIFE_DEAD` | 写2 | 371后发16 |
| 3 `LIFE_REVIVE` | 写1 | 371后发16 |
| 7 / uint32最大值 | 保留先前 +0x2cc | 16 |

重复 wire 字段先由真实 reader 按最后值覆盖。最后值为0不会清零既有模型状态。同值再次应用仍执行 setter、写入并发371；该行为和 f3 current-Team setter 的“同值不发事件”不同。未知值未被本片段拒绝，不因此成为业务允许值。

独立输入包含 >2^53、bit63、uint64最大值和0 GUID；事件 payload 的完整 qword 来自真实模型身份写入。GUID0的状态事件行为不改变 f3 记录的“GUID0不登记 Avatar 索引”边界。

## 非空实体及准确停止位置

原生 `1517ab3a0` 先按 model+0x218 的 entity ID 查 scene+0x150 字典，再按 scene+0xc0 列表中 entity+0x348 逐项找备用实体。字典位置查询是声明过的 managed 存储模型，查找分支和备用列表扫描为实际机器码。实体对象的 +0x80、+0x426、+0x4fa 是显式受控字段，不按混淆 bool 名字额外恢复业务。

- LIFE_ALIVE且实体+0x4fa已置位：执行状态更新并返回应用块，发371/16。
- LIFE_ALIVE且+0x4fa未置位，或 LIFE_REVIVE且+0x4fa未置位：实际虚槽98进入 `28174::148d74b00`，真实写 +0x4fa=1；在下一虚调用指令 `148d74b38`（vtable+0x4b8）前停止。没有用成功 stub 越过该效果。
- LIFE_REVIVE且+0x4fa已置位：实际槽98直接返回，调用者再次写 ALIVE1 / 发371；`151852150` 在受控 model dictionary+0x80=null 分支返回，随后实际构造 `EvtRevive82`，到 `14e4e1000` 投递入口前停止。
- LIFE_DEAD且实体+0x426未置位：只完成状态更新和刷新；+0x426置位时真实构造 `EvtKill75`，同样停在 `14e4e1000` 投递前。
- 无scene、scene有而实体缺项、entity+0x80无效均独立验证；备用列表非空命中也实际执行。

对象池的具体绑定独立重提：usage740636/cell1459ae430 → MethodSpec32634 → type21751；usage754018/cell145a31670 → MethodSpec78152 → type15410；两者都是 method645855 的闭合实例，实际 AOT入口 `1547a9320`。对象池分配是本卡声明的**新建清洁 managed 对象模型**，不声称真实池租借/清理已经执行。事件编号、entity ID、flags和其它相应字段由客户端实际指令填写。父类58582的 +0x14 声明为 enum74697；事件对象与符号的连接没有靠相同布局猜测。

## 片段、错误与模型

运行真实 reader/root 构造/身份片段后，仅执行 `15184e1e4` → `15184e202` 的精确应用调用块，显式 live-in 为 `rdi=模型`、`rbx=reader产出的AvatarInfo`。它没有跨过其它未执行的 Avatar handler 段，也不等于完整 handler。

managed分配、scene字典位置、事件对象池和event bus交付为明确模型；状态函数、setter、实体查找、备用扫描、真实虚槽98、枚举/事件字段构造均执行目标机器码。后续虚槽效果、事件订阅者、非空 model dictionary+0x80 的处理与客户端生命周期仍未知。

IFix三组分别把状态函数、setter、实体槽98的guard置位，实际执行对应跳转后停在 `151849abc` / `151840710` / `148d74b5e`，在读取未知patch对象之前停止；不建立假动态方法成功上下文。

5个错误边界为截断状态varint、varint溢出、非法tag、负Avatar长度、scene字典null。前三项归于显式 primitive input 模型拒绝；负长度确实到 native reader error helper；scene字典null到真实 null helper入口，发生在状态写入/发事件前。

## 验收与复现

26组独立wire/受控环境、28次实际应用、5错误边界，含零/非零/1/2/3/未知/最大值、字段省略/重复覆盖、同值重复、缺实体、不同原生效果分支与IFix。11项新增定向及 f1/f2a/f3/e8 关联共46项测试通过。

主线审核了参数/枚举、具体对象池AOT、原生关键入口与模型/止步范围，并独立复跑46项及第三个全新进程的完整原生回放；完整报告和摘要与下表双跑逐字节相同。限定字段语义已受审，生产最小集合与完整效果仍未批准；冻结摘要中的提请审核标记保持，不以重写摘要追加批准状态。主线证明在`local/migration72/evidence/avatar-state-main-review.json`，第三回放为`avatar-state-main-{native,summary}.json`，测试/原生日志同前缀。

两次全新 Python 进程各自重读三hash、重提取声明/具体AOT，在离线 Unicorn 中执行目标机器码，完整轨迹和脱敏摘要逐字节相同：

| 产物 | SHA-256 | 字节数 |
| --- | --- | --- |
| `local/migration72/evidence/avatar-state-native{,2}.json` | `cebfefdb19078ef47a08c160c32a9e8a0dbd37ba19171956b9068065941fe748` | 1,186,161 |
| `protocol/cn-7.2/avatar-state-observations.json` / `local/migration72/evidence/avatar-state-summary2.json` | `6cbc46b1d447883a0d4c3ede00b43af56ccf3950a37de85db50840396f1b4cc9` | 130,388 |

比对记录 `local/migration72/evidence/avatar-state-determinism.json`；正式日志 `local/migration72/logs/avatar-state-native1-final.log`、`avatar-state-native2.log`、`avatar-state-tests.log`。源校验和也记录在摘要中。

```powershell
$stateGamePath = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_state.py --client $stateGamePath --output local/migration72/evidence/avatar-state-native.json --observations protocol/cn-7.2/avatar-state-observations.json
python tools/client72_avatar_state.py --client $stateGamePath --output local/migration72/evidence/avatar-state-native2.json --observations local/migration72/evidence/avatar-state-summary2.json
python -m unittest tools.test_client72_avatar_state tools.test_client72_avatar_team tools.test_client72_avatar_control tools.test_client72_avatar_contract tools.test_client72_avatar_complete_raw
```

原始执行/metadata/AOT/反汇编和失败日志仅保存在 `local/migration72/{analysis,evidence,logs}/avatar-state-*` 或复用探索器产生的 `analysis/avatar-team-native-0x151849670-*.txt`、`avatar-team-native-0x148d74b00.txt`。声明探索曾有重复`cell`键和未加载generic方法实例的错误，已改为按真实实例表读取；新增指令断言把pool call后的地址抄成调用点，在 `avatar-state-native1.log` 明确拒绝，修正为 `15184973c`。未改共享runtime去满足错误预期。

主线单独尝试 REA/Ghidra 对151849670的只读冷分析，provider timeout，没有可用函数证据；本卡的结论来自独立hash固定提取和原生离线执行，不把该次REA尝试列为成功证据，也未并发打开其工程。

## 最小 producer 与下一入口

本卡可以供主线审核 tag4 的生命状态应用语义，但**尚不能证明入场必须发送该字段**。wire允许省略，当前受控片段会保留先前状态；真实模型默认状态、完整初始化顺序以及其它字段/效果的前置关系仍未验。审核后，隔离producer可针对有明确业务来源的存活1/死亡2验证一致性；不能以0/未知值作为安全默认，或把REVIVE3当作普通持久状态直接发送。

下一最短证据入口：

- FightProp候选 raw tag7/+0x28：`15184e3bf`取map，model18163虚槽10=`1518517c0`，槽4=`151852cf0`（父类89508对应槽4=`152f7e6b0`）。需要独立参数/具名枚举、非零float写入和生命状态/当前HP消费关系；当前只定位，没有恢复业务合同。
- skillDepot候选 raw tag11 `BLBHOJMCOJN`/+0xc4：`15184e18e`读取，`15184e1ad`编码存入model+0x2b8；f3 `151a9ec40`从该存储走GLOBAL+0x212b8和+0x16c70资源链。最短下一步是核实实际资源类型、具名literal与关联字段，再批准候选名称。

以上定位不批准整个战斗图，也不改变主线 SceneInfo/AbilitySync 的闭包与必要性边界。仍缺完整 handler、实际最小入场字段集合和客户端验收；不部署此离线工具/测试变更。
