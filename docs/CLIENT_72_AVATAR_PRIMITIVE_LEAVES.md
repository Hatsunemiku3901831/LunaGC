# F-03d3e3：标量与primitive-list叶子

限定21518/38218/48088/51614/67498/68918及12876/18849/31113/65248/66229共11类。43个metadata存储槽经原生扫描独立确认43wire，无nested消息或Object依赖。本卡只恢复这些独立叶子；51050仍拒绝非空，`fullComplexRawClosure=false`、`fullAvatarRawClosure=false`，身份/语义`unresolved`。int32-list留给e4，不修改公共runtime、生产Proto/业务/包号，不运行服务、数据库、客户端或Gradle。

每个完整进程重新核验`extract_client72.HASHES`的三份CN7.2输入、提取metadata/有界usage cells/AOT；固定hash见[核心](CLIENT_72_AVATAR_CORE.md)。沿用[e2](CLIENT_72_AVATAR_UNIONS.md)上下文，库存摘要仍严格核`ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18`。不按旧版本、Asta或字段相似名批准业务。

## float-list独立原生路径

新选定13条AOT记录覆盖35584/11<u32>、35584/78<float>的ctor、两个Add overload、FNNADLCNGFO、set_Item及cctor，还有45077/78<float> helper cctor。float reader是`0x1571dc270`，scalar Add是`0x1571dc010`，另一个Add overload`0x1571dbbd0`只是静态记录，未称全部method执行。

实际float reader从codec+20取delegate，执行float原语后把XMM0送入XMM1调用scalar Add；原生`0x1571dc0c9`以`array+32+index*4`写入4字节。模型只提供已分配的四字节数组和primitive float/XMM0，不手工向列表塞最终结果。摘要保存float helper、float list及float[]三个usage cell（`0x14592a188`、`0x14589da10`、`0x145886440`）的实际metadata类型来源。

没有手置新的packed支持flag。独立AOT指出45077/78 cctor `0x159229a20`，其实际前缀box default float后，于`0x159229a4d`根据返回非null写static+4。工具实际执行此前缀并观察flag=1，在`0x159229a51`反射段前停止；boxing仍为明确模型，不冒称完整cctor、Type反射ABI或反射后default初始化执行。List类的float值类型/null-policy metadata与容量64 backing array仍为明确托管fixture，完整反射cctor未执行。

五个消息`.cctor`（12876、18849、31113、65248、66229）实际执行到返回。12876为u32 factory参数18、float factory参数26，即tag2/wire2与tag3/wire2；float unpacked的wire5由实际reader分支和非空向量另验。空packed也记录native helper的container→owner/字段偏移和原始key，没有拿无输出当支持证据。

## 精确43wire

偏移为十六进制。scalar wire0；u32-list wire0/2；float-list wire2/5。

| 类型 | tag / clientField / offset | raw |
| --- | --- | --- |
| 12876/AKLPGNHEFFB | 1 NOEEBKJAFAG/+2c；2 MDAMHOLGONG/+18；3 HMODGFNMPML/+20；4 LPJMAOFKBPI/+28 | int32；u32 list；float list；u32 |
| 18849/IDNNFOFBALH | 1 ABBODGKNDEP/+18；2 KGMKPEBKEFF/+20 | u32 list×2 |
| 21518/BGFMINLLJOO | 1 OGJOJBLIGCB/+20；2 JDMBGGLHOMJ/+1c；3 HDDPFKNMJJG/+18 | u32×3 |
| 31113/LCMEOJCJAPN | 1 PJAJDHBBMGB/+28；2 PFBKEGPBKAN/+20；3 CCPOLEPGAEB/+18 | u32；u32 list×2 |
| 38218/HHKMGMFIGAH | 1 HGGPCEMHOBI/+18；2 ILJLGHBLKMP/+24；3 PBIBHHJEOPE/+1c；4 GOKKMOKPCEE/+20 | int32；int32；u32；int32 |
| 48088/GPIHKEAFMFG | 1 CJMJBINEBAG/+18；2 ALOKJBKPLGA/+1c | int32×2 |
| 51614/IFENHCMFOCH | 1 NOEEBKJAFAG/+50；2 ANKEOLKBECN/+30；3 JCGPKLKCINE/+18；4 OOKGFNCMOMD/+48；5 AEPFCKJJOMN/+24；6 CMACDPNAIAF/+28；7 GEODCFEFNLP/+1c；8 NEBNDBIGKIA/+4c；9 HJPIIFMALJK/+38；10 ECEHDMGMPBB/+44；11 ILJLGHBLKMP/+2c；12 PLGFFEKCPHF/+54；13 AANCEPCPHKJ/+3c；14 FDBBEEIKJDK/+34；15 NJAGFJINHMA/+40；16 OCNIJJOEGJM/+20 | 1/2/12..16 u32；9 bool；其余int32 |
| 65248/CNKBIBNDOHA | 1 HGFBPFCEACB/+18 | u32 list |
| 66229/OAPCFPAKOLP | 1 KMBPJIKEDGD/+24；2 KBDNIGMHCBM/+20；3 PPJFLLGKNAH/+28；4 FEKDEEENNBB/+18 | int32×2；u32；u32 list |
| 67498/GHGAIHJNKLG | 1 AFKJIKCEOKA/+1c；2 GNHFNECPAHD/+18 | u32×2 |
| 68918/PBBDOCEDNIE | 1 CJMJBINEBAG/+18；2 ALOKJBKPLGA/+1c | int32×2 |

## 独立样本、错误与限制

1,408次11类/tag1..32/wire0/1/2/5扫描匹配全部metadata字段。33个独立图包括每类全部字段同时非零、不同同形字段用不同值、全部7个u32 list的packed/unpacked混用、u32上界/0、int32最小/最大/负值、重复清零/bool规范化及空图。51614全部16字段独立值避免同形标量误配；48088和68918同名双字段使用不同向量、不同原生reader，未合并类型。

float列表覆盖packed与unpacked混用追加、空packed不清旧值、错误varint wire跳过、正负0、负1.25/-2.5、最大finite和最小subnormal，保存raw bits区分0与-0。实际native Add写点、每次4字节store及数组偏移有独立记录。64元素向量执行64次原生Add，写偏移0..252，最后-63.0；第65元素实际到达未建模的native allocator `0x140514ee0`，分类为`fixture-model-boundary`，这是本工具容量边界，不是客户端列表上限或原生拒绝合同。

7个拒绝分为1个51050非空范围拒绝、5个WireInput/float模型拒绝（非法tag、signed varint溢出、u32 packed截断、float packed/unpacked截断）及上述1个工具扩容边界。不把这些输入模型错误称客户端原生异常；本卡没有新增已执行throw路径。managed allocation/class/generic/null-policy、boxing、primitive input/delegates与固定容量64数组为明确模型；具体11个reader、float helper前缀、float/u32 list reader与append实际执行。没有IFix/live ABI、业务Handler、实机或部署通过声明。

8项新增、109项关联研究回归通过；两次全新进程各核三hash、提取、扫描和独立图，完整证据及摘要逐字节一致。共享Project/Harness/矩阵由主线维护。

```powershell
python tools/trace_client72_avatar_primitive_leaves.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-primitive-leaves-native.json --observations local/migration72/evidence/avatar-primitive-leaves-summary.json
python -m unittest tools.test_client72_avatar_primitive_leaves -v
```

下一e4限定19829/21103/89623三类40存储下界，复用68918，需另核int32-list具体AOT、helper/layout和packed/unpacked负值，不能把u32/float列表模型套到signed list。51050双Object/tag100及整体复杂图仍留e8。
