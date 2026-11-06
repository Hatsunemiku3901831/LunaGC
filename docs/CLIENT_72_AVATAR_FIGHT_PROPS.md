# 7.2 AvatarInfo FightProp 初始化有限合同

任务 `F-03d3f4b / D02-R1`。仅新增独立离线工具、测试与证据，提出字段/选定ID的语义供主线审核；不修改旧 Proto、独立raw adapter、生产 sender、opcode、mappings、ready 或共享 runtime。`runtimeStatus=not-tested`、`targetProtocolReady=false`，完整战斗、实体效果和入场最小集合均未验。

## 目标与固定来源

目标为 `48178 tag7 ADJINCFKMML / +0x28` 的 `map<uint32,float>`，经应用块 `15184e3bf` 到模型18163虚槽10清理、虚槽4应用，核实初始 HP/MaxHP与选定基础/当前攻防值。本卡没有按旧 `FightProperty` 相同编号搬表。

每个正式进程通过当前 `tools/game_path.ps1` 取得路径，重新按 `extract_client72.HASHES` 核对固定 EXE/global/startup：

- EXE：`8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`
- global：`45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`
- startup：`b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`

固定 e8 raw、f1、f2a、f2b、f3、f4a 摘要。文件/hash 全部列于工具 `EVIDENCE_SHA` 与摘要 `inputContracts`。g1独立raw Proto仍保持原样；本卡是独立作者wire→客户端消费，不另宣称Java producer或生产输出验收。

## 语义证据链

实际 `15184e3bf` 取得 map，`15184e3d8` 调虚槽10=`1518517c0`，继而 `15184e3ed` 调虚槽4=`151852cf0`。后者的目标参数声明确为17961实例5971 `<uint32,float>`，按实际节点+0x2c/+0x30逐项调用 `152f7e830`。

写入后进入18163虚槽8=`15184cca0`。独立目标参数表证明这个callback、HP回退callback `151845780`、getter `152f80950` 的属性参数均为 **enum32419 `MAMOCEFGLOL`**。这是 map key 与目标 FightProp 枚举域的直接绑定，不只是“某字典能写入这个整数”。

进一步运行两个具名消费者：

- 完整 `72302 / LuaAvatarPropWatcherHandler.CheckAvatarProp / 152585120` 从目标具名字段 `FightPropType1/+0x5c`、`FightPropType2/+0x50`、`PropValue/+0x58`、`CompareState/+0x48`读取条件，经实际 Avatar-ID getter `14e9b1040` 和 enum-typed getter `152f80950`比较绝对值或比例。每个测试属性有相等/不等独立断言。
- `151852660` 实际读取1010/2000，计算并夹取比例，生命状态+0x2cc为2时返回0。精确 UI 调用块 `14cb6e3fe` 将其结果交到具名 `70990 / MonoAvatarIcon.set_hpRatio / 14f44f530` 入口前，真实验证 receiver 和 xmm1 值。仅执行这一小块，不声称整个UI刷新已运行。

目标enum原始符号、逐个非零写入/getter和具名watcher实际验证的7个ID如下。它们的业务数值来源、计算公式与入场必要性仍另审。

| ID | 固定目标原始符号 | 独立标准值 |
| --- | --- | --- |
| 1 | FIGHT_PROP_BASE_HP | 400 |
| 4 | FIGHT_PROP_BASE_ATTACK | 61 |
| 7 | FIGHT_PROP_BASE_DEFENSE | 20.5 |
| 1010 | FIGHT_PROP_CUR_HP | 123.5 |
| 2000 | FIGHT_PROP_MAX_HP | 500 |
| 2001 | FIGHT_PROP_CUR_ATTACK | 91 |
| 2002 | FIGHT_PROP_CUR_DEFENSE | 40.5 |

对HP/MaxHP还执行实际 callback：每个对应key触发 `AvatarHPChanged=374`，输入包含1010时尾部发 `AvatarHPChangedEnd=375`，包含2000时发 `AvatarHPDebtRefresh=376`。376不是“MaxHPChanged”，不能据错名扩大语义。枚举中其它ID没有因被列出而得到合同；0、4242、uint32最大值仅作为raw未知key测试。

## float 编码与清理

模型89508的 +0x10 是 `Dictionary<32758,30710>`，分别为编码int32 key和编码float value。30710的真实编码/解码入口为 `152e90fe0 / 152e90a60`；它不是 f1 正常属性使用的44490 double。使用已有明确的可逆置换表与确定性Random fixture，执行真实整数和float编码，不绕过一致性比较。

具体AOT重新提取并核对：raw节点枚举 `11026/5972 MoveNext=15720a600`；encoded字典 `1222/53617 FindEntry=158f3f170`、`TryInsert=158f3f4c0`。MoveNext实际执行；FindEntry/TryInsert是明确managed字典存储模型，原生编码、setter/getter/callback均不替换为成功模型。

每组先用真实setter写入三条旧值，再运行实际slot10清理。原生 `152f7f190` 控制 Array.Clear 范围、count/free-list/version更新、pool归还及 model+0x10=null；Array.Clear的字节清零操作和pool边界为明确模型。随后实际应用新map。观察到：

- **省略map也清除先前FightProp**，不保留旧值。与 f4a 省略生命状态保留旧值的语义不同。
- 部分map不继承被省略的旧key；只有当前HP而没有MaxHP时，getter对MaxHP读0，所测比例为0。
- 重复map key由reader保留最后value，consumer只收到每个最终key一次；显式0值仍保留key，HP事件按key存在触发。
- 相同map再次应用仍重新清理/写入/发相应事件。

逐位验证正零、负零、最小subnormal、最大有限float、负小数、正负无穷、quiet NaN payload。raw节点、native setter输入及native getter各自核对32位bit pattern；NaN在具名watcher的相等比较为false。非finite仅验证此受控编码/消费边界，不批准这些值作为游戏业务输入。JSON snapshot中的非finite用float32 bits对象表示，避免非标准JSON。

## 边界与错误

完整执行的是精确应用块 `15184e3bf` → `15184e3f3`，后续 `152f7ec20` 和其余Avatar handler停止。场景是明确的空entity索引，实际scene lookup走缺项路径；没有假combat组件或成功效果stub。次级Avatar-ID字典是指向实际已生成模型的受控存储，用来运行具名watcher；本卡不批准该次级索引的注册流程。

原生GUID模型身份、所有事件中的完整GUID复用f1；所测0、>2^53、bit63和uint64最大值未截断。HP比例普通样本 `123.5/500` 的float bits为 `3e7ced91`；DEAD状态时UI比例为0，而独立watcher的原始比例仍为 `3e7ced91`。

5个错误边界：负map长度到真实reader错误helper；截断float、非法tag区分输入模型拒绝；损坏30710编码实际进入 `152e90d75` 一致性失败分支，停在读取未知上报服务之前；人为使decoded map指针null时，实际先清空旧map，再在setter前到null helper入口。有效wire的省略map是非null空容器，不与人为null存储混为一谈。

首次损坏编码测试试图抵达更后的上报函数，但上报服务缺失，实际碰到 `1404c2be4` 未建模调用；原始 `logs/avatar-fight-props-formal-probe1.log` 保留。最终将停止位置收紧到已实际到达的一致性失败分支，未创建假服务、未把错误改成成功。

## 验收与复现

23组独立wire/环境、24次初始化应用、5个错误边界。12项新增定向及 f1/f2a/f3/f4a/e8 关联共58项通过。两次全新 Python 进程各自重读三hash、重提取参数/枚举/具体AOT，在离线 Unicorn 执行目标机器码；完整轨迹与脱敏摘要逐字节相同。

| 产物 | SHA-256 | 字节数 |
| --- | --- | --- |
| `local/migration72/evidence/avatar-fight-props-native{,2}.json` | `af225811efc26b2ed225d548ac6fd643cec2388d674899d3762b0228862f2793` | 16,527,812 |
| `local/migration72/evidence/avatar-fight-props-summary{1,2}.json` | `66113992295fb759d80c2caa392c7ac8c2e6f78dc9961e6860427f23564d940e` | 941,517 |

比对记录 `local/migration72/evidence/avatar-fight-props-determinism.json`；正式运行日志 `logs/avatar-fight-props-native{1,2}.log`、测试日志 `logs/avatar-fight-props-tests.log` 均位于 `local/migration72/`。共享摘要发布服从主线输入集审查窗口；窗口期间输出在 `local/migration72/evidence/avatar-fight-props-summary1.json`，不改变共享review集合。发布后 `protocol/cn-7.2/avatar-fight-props-observations.json` 使用同一摘要字节。

主线已逐项审核工具、测试、模型边界和限定语义，并独立执行58项及第三个全新进程原生回放；完整/摘要与上表双跑逐字节相同，六份source pin保持。证明为 `local/migration72/evidence/avatar-fight-props-main-review.json`，独立输出 `evidence/avatar-fight-props-main-{native,summary}.json`，日志 `logs/avatar-fight-props-main-{tests,native}.log`。此项仅关闭有限消费合同，不晋级入场必要性、生产发送、完整handler或D-02。

```powershell
$fightGamePath = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_fight_props.py --client $fightGamePath --output local/migration72/evidence/avatar-fight-props-native.json --observations protocol/cn-7.2/avatar-fight-props-observations.json
python tools/client72_avatar_fight_props.py --client $fightGamePath --output local/migration72/evidence/avatar-fight-props-native2.json --observations local/migration72/evidence/avatar-fight-props-summary2.json
python -m unittest tools.test_client72_avatar_fight_props tools.test_client72_avatar_state tools.test_client72_avatar_team tools.test_client72_avatar_control tools.test_client72_avatar_contract tools.test_client72_avatar_complete_raw
```

原始metadata、具体AOT、反汇编、完整轨迹和失败日志仅保存在忽略目录 `local/migration72/{analysis,evidence,logs}/avatar-fight-props-*`；部分复用探索器的完整反汇编为 `analysis/avatar-team-native-0x1518517c0-*.txt`、`avatar-team-native-owner18163.txt` 等。没有控制客户端、调试器、服务或数据库，没有Gradle、部署或Git操作。

## 最小 producer 缺口

本卡支持待审核的 tag7 FightProp map关系和上述7个target符号/ID的限定消费。真实入场是否必须发送、应该发送哪些key、数值由保存值/资源/计算何者产生仍未建立。不能把“map可以省略”理解成“业务可以省略”：所测原生块会清旧值，alive HP显示依赖当前与最大HP的配对值。

下一隔离producer可以在主线审核后验证真实业务数据对应的HP/MaxHP与life_state一致性，同时保持f1装备GUID和Store一致；不从这张卡扩成完整战斗属性表。后续最短独立取证仍为 skillDepot候选 raw tag11/+0xc4→model encoded+0x2b8→f3 Team资源链，以及完整Avatar handler后续152f7ec20/模型初始化必需性。未批准sender、完整战斗、SceneInfo/AbilitySync闭包或D02Ready。
