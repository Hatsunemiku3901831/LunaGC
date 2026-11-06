# F-03d3e1：53824外层、string/u32 map及两叶子

只恢复53824/JHPMKLGOMKF外层及64132/GNDDOIAANMO、86812/HKBBMGICMPP两叶子。三类先核对18个metadata实例存储槽，再由原生扫描独立确认18wire。34045非空明确拒绝，`fullComplexRawClosure=false`、`fullAvatarRawClosure=false`，身份及业务语义均`unresolved`。不修改公共runtime、生产Proto/业务/包号，不运行客户端、服务、数据库或Gradle。

每次完整进程逐一核验`extract_client72.HASHES`的三份CN7.2固定输入，再提取metadata/有界usage cells/AOT；哈希表见[AvatarInfo核心](CLIENT_72_AVATAR_CORE.md)。沿用[双Item图](CLIENT_72_AVATAR_ITEMS.md)的已验上下文，库存摘要仍严格核SHA-256 `ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18`。本卡不以Asta或旧版本语义批准英文名。

## string→u32的独立原生证据

从三输入重新解码7条选定AOT记录：

| owner / instance | 参数 | method / address |
| --- | --- | --- |
| 17961/3472 | string,u32 | .ctor `0x154d57f30`；FNNADLCNGFO `0x154d585c0` |
| 17961/3473 | object,u32 | .ctor同上；FNNADLCNGFO同上；set_Item `0x154d5a490` |
| 17964/3473 | object,u32 | .ctor `0x155f98970`；MOMMACBEMEK `0x155f989e0` |

具体string/u32与shared object/u32的reader共同体由AOT独立核对。原生reader用R9的read RGCTX：entry class/ctor在context+10/+18，setter info在+140；实际adapter读取string codec的tag/delegate +30/+28，以及u32 codec的+34/+18。原生adapter、ctor、setter均记录实际进入，不沿用旧string/int32的函数地址或layout。

setter原生代码查map+24的dictionary，entries位于dictionary+48；linked位于map+16。新节点的key/value在+16/+24，value按u32读取，+28为padding。重复key时实际执行`0x154d5a5dd`写key指针、`0x154d5a5e1`写4字节value、`0x154d5a5e5`清padding。这个布局不同于旧signed map的node+24/+32；新工具只复用class/context/delegate三个底层分配方法，没有复用旧map body或模板构造逻辑。

`64132 .cctor 0x14bebc440`实际执行到返回：string codec factory参数10、u32 factory参数16，map tag1/wire2，static+1f290。`53824 .cctor 0x148490870`实际执行：u32/u32 map tag8/wire2，static+2a8f8；86812 message-list tag12/wire2，static+2a8f0。factory、托管metadata/class contexts与dictionary存储是明确模型，原生initializer负责传参、组合与写static。

## 精确18wire

偏移为十六进制。scalar wire0，message/map/list wire2。

| 类型 | tag | clientField / offset | raw |
| --- | --- | --- | --- |
| 53824 | 1 | LLHEBIHGDAE/+5c | u32 |
| 53824 | 2 | FAAMIEFKMPB/+38 | u32 |
| 53824 | 3 | PJAGIOHBKLD/+54 | u32 |
| 53824 | 4 | FBLKPHOCBIK/+40 | u64 |
| 53824 | 5 | NJAGFJINHMA/+50 | u32 |
| 53824 | 6 | KFDCBKBPPGL/+58 | u32 |
| 53824 | 7 | KDKAEHEEMMJ/+28 | →34045，仅空envelope |
| 53824 | 8 | JMGKMMFNABP/+30 | map<u32,u32>，非空已验 |
| 53824 | 9 | GDLHHFGKOGO/+18 | →64132，非空已验 |
| 53824 | 10 | MNCFEDPDEAG/+48 | bool |
| 53824 | 11 | MKFOJONFDMA/+4c | u32 |
| 53824 | 12 | LOLPFGGFGLH/+20 | message-list→86812，非空已验 |
| 64132 | 1 | JMPEPGDDKPD/+18 | map<string,u32>，非空已验 |
| 64132 | 2 | BGNAFEDFIHF/+20 | u32 |
| 64132 | 3 | HBBLAFDHAOL/+24 | bool |
| 86812 | 1 | BAHECKLMKGJ/+1c | u32 |
| 86812 | 2 | HDKEBNDMFLF/+20 | u32 |
| 86812 | 3 | ELPEMEBJKNO/+18 | u32 |

## 实际样本与边界

384次3类/tag1..32/wire0/1/2/5扫描逐一匹配metadata全部字段。22个独立图覆盖全部12外字段同时输入、两叶子全部6字段非空、两个不同值的86812列表元素、string key A→300、u32上界4294967295（保持无符号）、相同key重复清零、key-only/value-only/空entry、entry内部重复字段及未知字段、空key、中文/emoji/NUL key `中🌙\0`、bool规范化及清零、u64=2^53+1和上界、嵌套map合并。

完整AvatarInfo组合仍有全部37外字段、双Item列表各9个非空Item、此前小树和float/message maps；tag37列表同时保留一个真正空53824对象和一个本卡完整非空外层。首次实现重分配53824 class，已有AvatarInfo codec仍持有原class，实际触发`Message list codec/type mismatch`；改为复用已建立的class后同一native检查和非空组合通过，没有修改或跳过检查。

9项拒绝分清来源：1个34045非空范围拒绝；4个明确输入模型拒绝（非法tag、u64溢出、非法UTF8、字符串截断）；4个真实ReadMessage错误入口（negative-length、truncated-message、map及子对象recursion-limit），到入口即停、未执行throw。UTF8→托管UTF16、string equality、managed allocation/class/generic contexts、primitive input/delegates、固定容量64 dictionary/linked存储仍是模型；实际reader/ReadMessage/limits、string map adapter/ctor/setter、list append及继承的clone/copy helper执行原生代码。没有IFix/live ABI、handler、业务语义、实机或部署通过声明。

8项新增、93项关联研究回归通过；两个全新进程各自核三hash、提取、扫描和独立样本，完整证据及摘要逐字节一致。共享Project/Harness/矩阵由主线维护。

```powershell
python tools/trace_client72_avatar_complex_outer.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-complex-outer-native.json --observations local/migration72/evidence/avatar-complex-outer-summary.json
python -m unittest tools.test_client72_avatar_complex_outer -v
```

下一e2已限定85344/80101/62912/77771/70808/51162/34045七类36存储下界，须独立执行boxed string/u32、float与全部非空依赖，51050继续拒绝。34类238存储只是53824原静态计划合计，不当作238个已验wire，也不因本卡通过批准完整复杂图。
