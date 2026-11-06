# CN7.2 Avatar候选外层与依赖下界

2026-11-06，F-03d3a。**已验3类型14个raw wire字段；AvatarInfo非空内容明确拒绝，完整Avatar初始化闭包未完成。** 身份和字段业务语义均保持unresolved，不能直接迁生产或认为P0/D-02通过。

工具[trace_client72_avatar_outer.py](../tools/trace_client72_avatar_outer.py)每次实际读取并核对`extract_client72.HASHES`三个客户端输入，再重新提取metadata、静态Cmd和选定AOT记录。三哈希与[固定profile](../protocol/cn-7.2/profile.json)一致；target静态getter `0x152de0cb0`唯一绑定20143→30509/ILEPMBEJKIA。AvatarDataNotify仅为固定Asta来源的候选用途名，不沿用旧27799，不用其字段名验证业务。

正式摘要为[avatar-outer-observations.json](../protocol/cn-7.2/avatar-outer-observations.json)，SHA-256 `18ffa869ee1ebb7d73e028727aa1b424d0f1ce641530d73c7984696cf38093b8`。两轮完整报告与摘要逐字节一致，本机证明`local/migration72/evidence/avatar-outer-determinism.json`。

## 静态依赖规模与Object边界

先前51类型331存储只按已知field边递归。本轮把从首usage到global组末尾的有界全部记录分别解码source/destination，再沿实例field、reader/.cctor实际RIP class引用及generic参数展开，得到65类型368个metadata实例存储字段。仅root reader是保护跳板`0x152de03c0→0x15a4e4753`；其余64个reader可直接审计。该65集合是静态依赖审计，不是65个已恢复wire schema或完整客户端动态闭包。

Object owner为65333/28471/44169（库存raw已验）、78350（PropValue raw已验），以及新51050/OHPEJIDHEOP、85344/EMMGCCPNPII。51050含两个Object及float list等复合内容；85344为Object+判别的boxed union。摘要保留这六类reader/.cctor的全部已解码usage类型引用，未把未运行的分支、Object对应tag/判别或业务用途升级为verified。

37个metadata容器实例均找到静态FNNADLCNGFO绑定，选定AOT共85条记录；本卡只执行root/Team/Rename相关路径。其余绑定标记`static-binding-found-not-all-executed`。root实际u32 list使用35584/classInstance11→`0x158db8e10`，u64 list使用instance58→`0x1571ddd60`，两个message-list的6060/8554实例都绑定`0x158dc41f0`。Team map的66210/instance19346为`<uint32,type26081>`，与shared1541的原生读体`0x156d35a40`独立核对。

## 已恢复的14个raw字段

root保护reader实际执行到返回；不是静态tag命中。四个同形u32 list分别用独立非零值区分，不能按metadata顺序或Asta用途名配对。

| 类型 | tag / wire | 目标字段与偏移 | raw内容 |
| --- | --- | --- | --- |
| 30509 | 3/0 | LFMIIOCDKNK/+58 | uint64 |
| 30509 | 4/0,2 | BJKPIFCAKKA/+40 | uint32 list |
| 30509 | 5/2 | CNCCIILAJNJ/+28 | map<uint32,26081>，非空内容已验 |
| 30509 | 6/2 | JGECGMPNGBD/+48 | message-list→48178；仅空envelope绑定，非空拒绝 |
| 30509 | 9/0,2 | IAACAIILIFD/+30 | uint64 list |
| 30509 | 11/2 | AGCIPPKKCAC/+50 | message-list→14786，非空已验 |
| 30509 | 12/0 | OCIKDBJPOAJ/+60 | uint32 |
| 30509 | 13/0,2 | GDFOCENMMIA/+38 | uint32 list |
| 30509 | 14/0,2 | LCDMGOFLDKF/+18 | uint32 list |
| 30509 | 15/0,2 | HENJGDHPLIN/+20 | uint32 list |
| 26081/CFCDAEBPLOG | 2/2 | MGIDEJKCAKD/+20 | string |
| 26081/CFCDAEBPLOG | 12/0,2 | PIDCNPCLBCE/+18 | uint64 list |
| 14786/PPJCCBIDNNH | 5/0 | NLNJDCDLHHE/+20 | uint32 |
| 14786/PPJCCBIDNNH | 9/2 | PBDIJJHDMOF/+18 | string |

两个实际`.cctor`执行到返回，factory是明确primitive-codec fixture，原生initializer负责传参、组合codec和static store。Team initializer的u64 factory参数为98，即tag12/wire2；不能按旧Team GUID字段位置恢复。空packed输入也记录实际native list helper的对象参数→字段偏移和原始key，再由非空独立向量确认值；没有用“空列表无输出”推断wire2已验。

## 实际样本与限制

384次有界tag1..32/wire0/1/2/5扫描及19次独立图覆盖root两个标量、四个u32 list、一个u64 list、非空Team map、非空Rename list、两个独立叶子和root全部10外字段同时输入。Tag6只使用显式`empty-envelope-only`占位，非空`32020801`在48178原生reader入口前明确拒绝。

独立wire `188180808080808010600b`得到root uint64=9007199254740993/u32=11；`48004a128180808080808010ffffffffffffffffff01`通过实际packed/unpacked u64 list append得到`[0,9007199254740993,18446744073709551615]`。四个u32 list输入`22030180016a0302800172030380017a03048001`分别得到tag4/13/14/15的首元素1/2/3/4，均保留第二元素128。

Team map第一value含string T和非零u64 list，第二个相同key仅含string B；实际原生map setter整体替换为B，旧GUID list不合入新value。中文/emoji/NUL样本`中🌙\0`在两个叶子的实际reader赋值路径中保留；UTF8解码和managed UTF16对象/长度是显式字符串原语模型，没有执行客户端原生UTF8 decoder或UI。

5个拒绝为1个非空AvatarInfo范围拒绝与4个WireInput/UTF8模型拒绝（非法tag、GUID溢出、packed截断、非法UTF8），不称客户端原生异常合同。托管分配、class/generic context、primitive input/delegates、字符串、固定容量64的map dictionary lookup/insertion及linked节点分配为fixture；具体root保护reader、叶子reader、map adapter/setter、list append与ReadMessage/limits执行原生代码。没有IFix/live ABI、完整Handler、实机或部署通过声明。

新增8项与包含它们的62项关联研究回归通过。初试时同形列表按预期用途配对失败，实际对象偏移显示tag4/+40、tag14/+18、tag15/+20；按原生非零图纠正raw绑定后再验，未把预期用途当语义证明。共享GraphReader/MapRuntime、生产Proto/包号/业务builder和d1b专题未修改。

```powershell
python tools/trace_client72_avatar_outer.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-outer-native.json --observations local/migration72/evidence/avatar-outer-summary.json
python -m unittest tools.test_client72_avatar_outer -v
```

## 可拆分的后续精确范围

下表类型/存储数量是静态子树，彼此有重叠，不能相加或称wire字段数。

| 根 | 类型/存储 | 下一步依赖 |
| --- | --- | --- |
| 48178/ONGOHFDHHNI | 62/354；本体37 | 外层、map<u32,float>、两个以上u32/message map及复杂nested；整个非空未验 |
| 66100/DMGDPCGIFDM | 1/3 | u32 list+两个u32，可独立 |
| 27246/CKCLPDDJMII | 1/5 | 五个u64，可独立 |
| 13438/JJPIHCCEBOE | 1/2 | 两个u32，可独立 |
| 40807/LODODHKBJHK | 1/2 | u32+u64，可独立 |
| 27196/LIIKPPLEEOJ | 4/9 | 两个嵌套76885及小子树 |
| 66308/AEKPCHHKBPF | 2/9 | 多u32 list、message-list及77263叶子 |
| 16352/BALMANFHCBI | 16/45 | 16352/71717两新类，加已验库存中排除StoreNotify根68537后的Item子闭包14类 |
| 53824/JHPMKLGOMKF | 34/238 | 复杂子树；可继续分解 |
| 51050/OHPEJIDHEOP | 24/184 | 双Object、float list、多种子对象与oneof |
| 85344/EMMGCCPNPII | 1/2 | boxed union，分支raw/类型未运行 |

AvatarInfo里多个u32/message map共享同一read body，却使用不同codec/child；后续必须提供专用按codec选择的multi-map上下文，不能让公共MapRuntime的单by_entry配置误配。float map/list也必须按实际AOT和wire恢复，不能当uint32。最后仍需独立完整组合图和业务身份/目的地合同；本卡不自动迁生产。
