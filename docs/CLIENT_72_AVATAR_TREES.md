# F-03d3c：Avatar两棵小树的非空raw闭包

限定27196→76885→71429/18373与66308→77263，新增6类。固定三输入逐次按`extract_client72.HASHES`核验，与[外层](CLIENT_72_AVATAR_OUTER.md)、[AvatarInfo核心](CLIENT_72_AVATAR_CORE.md)完全一致；不采用旧版本号、Asta语义名或生产Proto作为wire依据。原6类18个metadata存储槽只是下界，本轮独立扫描和非空原生图后才确认这六类共18个wire字段。

真实metadata实例field及各reader/`.cctor`有界全部usage class/generic引用审计得到恰好6个消息类型、18存储槽、16条原生usage引用；没有Object槽或额外消息依赖。选定8条AOT记录直接从固定输入解码：message-list的25060/36273→71429、12616→18373、23297→77263均绑定`0x158dc41f0`，u32-list的35584/11绑定`0x158db8e10`。AOT记录含ctor等静态绑定，不声称全部method实际执行。

`fullSelectedTreeRawClosure=true`只指这两棵限定树。`fullAvatarRawClosure=false`、身份/业务语义`unresolved`，16352及53824继续仅允许空envelope并明确拒绝非空；不扩大这两棵复杂子图，不修改生产Proto、业务、包号或公共runtime，不运行客户端、服务、数据库或Gradle。

## 精确raw字段

偏移为十六进制。标量wire0，message与message-list wire2；四个u32-list同时支持wire0/2，实际非空packed/unpacked已验。

| 类型 | tag | clientField / offset | raw |
| --- | --- | --- | --- |
| 27196/LIIKPPLEEOJ | 1 | EPCMGLPMKLC/+28 | u32 |
| 27196 | 2 | GONJACBHGEP/+18 | →76885 |
| 27196 | 3 | CBIONIIBDIN/+20 | →76885 |
| 76885/IGPCEDMMFLE | 1 | AIEEBDFPNPJ/+20 | message-list→18373 |
| 76885 | 2 | CNGEAFGELMF/+18 | message-list→71429 |
| 71429/ELCCHLNCAKG | 1 | KIIGMHLMLMA/+18 | u32 |
| 71429 | 2 | DODCEPDNAIM/+1c | u32 |
| 18373/KAHJPHODOML | 1 | MHAIENEPEIP/+18 | u32 |
| 18373 | 2 | OACOOBKGAKO/+1c | u32 |
| 66308/AEKPCHHKBPF | 1 | KLNPMEAELPD/+38 | u32 |
| 66308 | 2 | PHCBOKNHIHH/+3c | u32 |
| 66308 | 3 | MLGIMHKCCHG/+28 | u32 list |
| 66308 | 4 | DCOFEJBIBGN/+18 | u32 list |
| 66308 | 5 | AAAMKIEOGNJ/+30 | u32 list |
| 66308 | 6 | JEONOFFKPNB/+20 | message-list→77263 |
| 77263/OJPJBJECJJF | 1 | BMMLCJLAODF/+20 | u32 |
| 77263 | 2 | GIFDBBABDOD/+24 | u32 |
| 77263 | 3 | NCKEHOOOKGN/+18 | u32 list |

实际reader：27196 `0x14c1e7070`、76885 `0x14769eb80`、71429 `0x14ec1b160`、18373 `0x149c85fc0`、66308 `0x14b107ae0`、77263 `0x147bc1090`。三个`.cctor`实际执行到返回：66308 `0x14b107d50`传u32 codec参数26/34/42，76885 `0x14769ecb0`组装两个具体message codec，77263 `0x147bc12f0`传参数26。factory是明确原语模型，原生initializer负责传参、组合codec和写static。

## 非空原生图和边界

768次6类/tag1..32/wire0/1/2/5扫描逐字段与metadata核对。空packed分支保留实际native helper的container→owner/字段偏移和原始key，再以非空样本确认。没有按同形字段在metadata中的顺序猜配tag。

27196独立wire `080b120c1204080b10160a040821102c1a0c1204083710420a04084d1058`使tag2/+18的两种叶子分别得到11/22、33/44，tag3/+20分别得到55/66、77/88，标量为11。同一个76885对象字段重复出现时，原生reader复用已分配对象并向各list追加；最后追加空envelope不会清空已有列表。两条singular message的显式空envelope则分别保留两个独立空对象。

66308的三个u32 list分别得到`[3,0,4294967295]`、`[4,0,4294967295]`、`[5,0,4294967295]`，子77263含独立两个非零u32和`[0,128,4294967295]`。两层message-list均验证多个独立非空元素，另有空/非空/空连续元素证明对象与backing array不串用。四个u32 list还验证packed/unpacked混用与追加。首轮独立样本有一处packed长度手写成2而实际payload为3，实际输入模型拒绝；纠正样本长度后全轮重跑，不放宽校验。

20个独立图含两树各自全部字段非空、同形字段不同非零值、嵌套合并/list追加、零/上界/重复字段/错误wire，以及两种AvatarInfo组合。完整37外字段组合复用上一卡独立固定wire后追加两树非空内容，实际进入全部6个新reader；保留原有高u64、负float、map、列表及另外两个复杂子图的空标记。没有用空壳冒充这六类的完整非空图。

10项拒绝分来源：2个16352/53824非空范围拒绝；4个原语输入模型拒绝（非法tag、varint溢出、packed截断、叶子varint截断）；4个真实ReadMessage错误入口（negative-length、truncated-message、首层及次层recursion-limit），到达即停，未执行throw。managed allocation/class/generic context、primitive input/delegates、固定容量64 backing array/map dictionary仍为明确模型；具体reader、ReadMessage/limits、list append和继承的map adapter/setter执行原生代码。没有IFix/live ABI、命名Handler、业务用途、实机或部署验收。

新增8项与77项关联研究回归通过；两个全新进程各自核三hash、提取、扫描和独立样本，完整证据及摘要逐字节一致。共享Project/Harness/矩阵由主线维护。

```powershell
python tools/trace_client72_avatar_trees.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-trees-native.json --observations local/migration72/evidence/avatar-trees-summary.json
python -m unittest tools.test_client72_avatar_trees -v
```

下一步应由主线明确拆卡恢复16352或53824；本卡不自动展开。16352原静态16类45存储包含大量已验库存Item子闭包，53824原静态34类238存储仍复杂；数字有重叠且只为静态下界，不能当已恢复wire数或完整Avatar可发送合同。
