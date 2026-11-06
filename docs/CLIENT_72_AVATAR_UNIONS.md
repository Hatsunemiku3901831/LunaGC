# F-03d3e2：Object string/u32、float与上层列表

限定85344/80101/62912/77771/70808/51162/34045七类。36个metadata实例存储槽经独立原生扫描确认36wire；不把Object判别槽另算普通字段。51050只允许空envelope，非空明确拒绝。`fullComplexRawClosure=false`、`fullAvatarRawClosure=false`，身份/业务语义`unresolved`。不修改公共runtime、生产Proto/业务/包号，不运行服务、数据库、客户端或Gradle。

每次完整运行重新核验`extract_client72.HASHES`的三份固定CN7.2输入、提取metadata/有界usage cells/AOT；哈希表与[AvatarInfo核心](CLIENT_72_AVATAR_CORE.md)相同。复用[e1外层](CLIENT_72_AVATAR_COMPLEX_OUTER.md)和已验Item上下文，库存摘要仍严格核SHA-256 `ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18`。所有英文业务名保持未批准。

## Object与float的独立来源

85344/EMMGCCPNPII实际reader `0x151e512d0`：tag1/wire2调用`0x147500e70`读string，写Object+18及判别+20=1；tag2/wire0调用`0x147520110`读u32，依据真实usage cell `0x145886170`的primitive uint32类型调用`0x140525c40` boxing，写同Object+18、判别+20=2。摘要保存该cell的独立metadata source/destination解码记录。string本身是引用，只有u32走boxing；没有把Object统一当message或把判别槽当独立wire。

实际reader分支对空string与u32=0仍设置判别，独立样本保留active empty/zero；string→u32→empty-string切换最终只保留tag1分支。Unicode/emoji/NUL保留，UTF8→托管UTF16和boxing为明确原语/分配模型。

四个float存储字段在80101、77771、51162的实际reader中由原生写入；float原语把4字节送入XMM0，snapshot按float32读取，同时独立保留raw bits。80101的实际写点为`0x14ee7dc28`（+18）及`0x14ee7dc58`（+1c）。负-1.25、-0.0分别保留`0xbfa00000`与`0x80000000`；最大有限值与最小subnormal保留`0x7f7fffff`、`0x00000001`。这证明raw reader/存储，不批准业务用途或writer默认值策略；NaN/Inf不在本批样本。

## 精确36wire

偏移为十六进制。string/message/list wire2，float wire5，其余wire0。enum只采用metadata `value__`的int32 raw值，不批准枚举业务含义。

| 类型 | tag / clientField / offset | raw |
| --- | --- | --- |
| 85344/EMMGCCPNPII | 1 NOEEBKJAFAG_string/+18；2 NOEEBKJAFAG_uint32/+18 | 共用Object，判别+20=1/2 |
| 80101/GJMHGPIPLLI | 1 EBDPHJGGAFO/+1c；2 MDDPGNIHKAF/+18 | float×2 |
| 62912/EELPBKPPNAM | 1 KNLALOGEGAI/+1c；2 HBNNOCOEFEE/+18；3 IJKEMAINHOD/+24；5 DCLPPKIHMBC/+28；6 OKDKGHLCKDL/+20 | bool；u32；u32；int32；enum48370 int32 |
| 77771/JGLOBICLMAI | 1 DBCJNBDFEOL/+18；3 GCOLEBCPBNP/+20 | →85344；float |
| 70808/OAOGHPJHFGN | 1 COECLMLNMMA/+18；2 PKNHHAFBGFB/+28；3 KNHMDLBJFMJ/+20；4 ONFKOHNNLDI/+30 | →85344两份；message-list→77771；u32 |
| 51162/EGHBAPJMNNP | 1 IHHOCELBHNN/+58；2 JAAKMAJMAFP/+4c；3 FDCIMEECAIM/+18；4 DNCDDOAIGAO/+30；5 ONFKOHNNLDI/+5c；6 IJKEMAINHOD/+50；7 NPJOJHAIMEE/+44；8 DIFHAHEMPCN/+20；9 MMOEOHFIHCN/+40；10 PEMOKNBLGBN/+38；11 DHJBELHAMOE/+28；12 PGNIPDHPCFD/+54；14 CMLFPFPAFJH/+39；15 CLOIMKOPMMP/+48；16 OKDKGHLCKDL/+3c | int32；u32；→85344；→85344；u32；u32；float；→62912；u32；bool；→80101；u32；bool；u32；enum48370 int32 |
| 34045/PLMODKAFNAD | 1 FAGKAADHKDI/+40；2 AOGEMELMKBO/+20；3 HCPGHHDMGKM/+28；4 PCHCMFOCHDO/+38；5 PPDFGNJDAAE/+30；6 COADAPGCGKK/+18 | bool；list→77771；list→70808；list→51162；list→51050仅空；list→77771 |

重新解码8条选定AOT记录：25060/5883→77771、27561→70808、27562→51162、27563→51050，四种具体FNNADLCNGFO均为`0x158dc41f0`。记录含ctor静态绑定，不称全部method执行。两个原生`.cctor`（34045 `0x147ccfe90`、70808 `0x1533de570`）实际执行到返回并组装各field codec；复用已有34045 class，保持先前message/class绑定检查。

## 实际验证

896次7类/tag1..32/wire0/1/2/5扫描逐一匹配全部metadata字段及Object两分支。27个独立图含空/零/切换、Unicode/NUL、boxed-u32上界、float负零/上下极小边界、int32最小值、负enum、51162全部15字段非空、34045全部6外字段、两个同形77771列表用string A/-1.25与u32 7/3.5区分、列表追加及两个同形85344字段独立值。

完整AvatarInfo组合仍保留全部37字段、双Item列表各9个非空Item、已有小树及float/message/string maps。其复杂列表保留实际空53824、e1外层（34045为空对象）和e2完整非空上层三种元素；实际进入全部7个新reader，51050仍只保留空标记。没有拿这个标记批准完整复杂图。

9项拒绝明确分开：1个51050非空范围拒绝；4个输入模型拒绝（非法tag、u32 varint溢出、非法UTF8、float截断）；4个真实ReadMessage错误入口（negative-length、truncated-message及两层recursion-limit），到入口即停、未执行throw。managed allocation/class/generic contexts、UTF8/boxing、primitive float/XMM0和固定容量64集合为明确模型；具体reader、ReadMessage/limits、list append及继承的map/clone/copy helper执行原生代码。不声称IFix/live ABI、handler、实机或部署验收。

8项新增、101项关联研究回归通过；两个全新进程各自核三hash、提取、扫描/独立图，完整证据及摘要逐字节一致。共享Project/Harness/矩阵由主线维护。

```powershell
python tools/trace_client72_avatar_unions.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-unions-native.json --observations local/migration72/evidence/avatar-unions-summary.json
python -m unittest tools.test_client72_avatar_unions -v
```

下一e3已限定11类43存储下界的标量和primitive-list叶子，需独立核float-list AOT、native body及packed/unpacked/负float/零/边界。int32-list保留到e4；51050的tag100/双Object和完整34类组合保留到e8。
