# F-03d3b：AvatarInfo外层与四叶子raw下界

本卡只恢复48178外层37个wire字段、66100/27246/13438/40807四个叶子12字段及已验78350的4字段，共6类53wire。53824/16352/27196/66308四个复杂子图只允许空envelope；非空在各自reader入口明确拒绝。`fullAvatarRawClosure=false`、业务身份和字段语义`unresolved`，不修改生产Proto、包号、sender或公共GraphReader/MapRuntime；不启动客户端、服务、数据库或Gradle。

输入逐次核对[迁移研究](CLIENT_72_MIGRATION.md)约定的三份固定SHA-256：

| 输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | 8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5 |
| global-metadata.dat | 45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a |
| startup-metadata.dat | b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105 |

候选根20143→30509属于[F-03d3a](CLIENT_72_AVATAR_OUTER.md)范围；本卡不重新批准AvatarDataNotify英文身份，也不按Asta字段顺序命名。前序摘要`avatar-outer-observations.json` SHA-256为`18ffa869ee1ebb7d73e028727aa1b424d0f1ce641530d73c7984696cf38093b8`；78350前序`player-data-wire-observations.json`为`455a353974185c885622759247a19b94c905c037545232b29e3cede189bb8cf6`。本轮重新解码metadata、全部有界usage class cells和选定29条AOT记录，并实际重跑78350原生reader。

## 有效复现与专用上下文

`66210/classInstance9505<uint32,78350>`与`12396<uint32,66100>`均独立绑定到原生reader `0x156d35a40`；shared1541同地址，adapter为`0x157b22e00`。原先公共runtime的`by_entry`对同一地址只保留最后一条配置。

先将两条真实配置装入原有实现，执行48178原生reader及wire `1a06080712020801`：tag3实际传入78350的codec，而`by_entry`最后保留tag10/66100，实际触发`0x156d35a40: Wrong map codec`。这不是手工抛出的预期异常或伪造red。随后仅在新工具内按真实R8 codec选择对应read RGCTX，adapter按实际对象class取上下文；set_Item仍执行原生共享body和其实际传入的上下文。相同wire得到`IKIFIBAEDGD[7].NOEEBKJAFAG=1`。

独立交错向量依次输入tag3、10、3、10且key均为7，分别最终保留78350负float分支与66100的第三字段；旧value整对象被替换，不跨map串值、不合入旧list。公共runtime文件未改。

float map的具体AOT为`17961/5971` reader `0x1551db160`、setter `0x1551dc230`，adapter `17964/5971`为`0x1551d29e0`。该codec的value tag/delegate在`+2c/+20`，不是u32 codec的`+34/+18`；原生adapter调用float delegate后读XMM0，原生map reader经XMM2传float给setter。map对象`+24`是dictionary、`+16`是linked；node key/value是`+44/+48`，不同于u32/u32节点`+40/+44`。初次把float codec按u32布局建模时非零样本得到0，按实际原生adapter纠正后负float和位模式样本才通过。

独立重复key向量由-1.25覆盖为-0.0，原生`0x1551dc2c4`实际写node+48的4字节，最终bits `0x80000000`；还验证上界u32 key、3.5、-2.5、key-only/value-only/空entry和entry内部重复字段。托管dictionary查找/插入、linked节点分配依旧是显式固定容量64的模型。

## 53个raw字段的精确范围

48178/ONGOHFDHHNI的标量17项如下；偏移均为十六进制，实际wire皆为0。

| tag | clientField / offset | raw |
| --- | --- | --- |
| 1 | NLNJDCDLHHE/+e0 | u32 |
| 2 | FBLKPHOCBIK/+d8 | u64 |
| 4 | DFENDOGMEAD/+c8 | u32 |
| 11 | BLBHOJMCOJN/+c4 | u32 |
| 13 | ACNMNNPAHPA/+c0 | u32 |
| 16 | DADPEGADCAC/+d0 | enum35709，metadata value__为int32，仅raw有符号值 |
| 18 | JPDLHLGNJIH/+ec | bool |
| 19 | MMLPICELLIF/+f0 | u32 |
| 21 | HCHEIDINIMO/+d4 | u32 |
| 23 | APNACEAKHPN/+f8 | u32 |
| 25 | MHAIENEPEIP/+e8 | u32 |
| 27 | GBBBBHEGCDL/+cc | u32 |
| 29 | EACODLJLDIL/+bc | u32 |
| 30 | IBPJHIBEFNP/+fc | u32 |
| 31 | BPGODHNDNNJ/+f4 | u32 |
| 32 | MKFOJONFDMA/+b8 | u32 |
| 34 | JIDLIPEAKLI/+e4 | u32 |

其余20项：

| tag | clientField / offset | raw范围 |
| --- | --- | --- |
| 3 | IKIFIBAEDGD/+70 | map<u32,78350>，非空已验 |
| 5 | AODNDDBPMFB/+18 | u64 list，wire0/2 |
| 6 | KLODODOMJPF/+20 | u32 list，wire0/2 |
| 7 | ADJINCFKMML/+28 | map<u32,float>，非空已验 |
| 9 | JCKNHCDJFCE/+98 | →16352，仅空envelope |
| 10 | HLAIJJEMIHE/+48 | map<u32,66100>，非空已验 |
| 12 | PKPEFNIHPLA/+b0 | →66308，仅空envelope |
| 14 | HEHFONFNHEH/+68 | u32 list，wire0/2 |
| 15 | PJGIPBJMBOP/+90 | map<u32,u32>，非空已验 |
| 17 | JOJLFAOOLMH/+38 | map<u32,u32>，非空已验 |
| 20 | CBEKIIICDOM/+a8 | u32 list，wire0/2 |
| 22 | DLDNCCJCHOB/+58 | message-list→13438，非空已验 |
| 24 | IEDIGDHMEEI/+40 | u32 list，wire0/2 |
| 26 | BNHMCGLAMLE/+50 | →27246，非空已验 |
| 28 | CMGAIFKIOJP/+78 | →40807，非空已验 |
| 33 | CKDDHADKCBK/+a0 | →27196，仅空envelope |
| 35 | EIJBACPBACO/+80 | u32 list，wire0/2 |
| 36 | LKJJFCJNENG/+30 | u32 list，wire0/2 |
| 37 | AMLLHJJIMNI/+88 | message-list→53824，仅空envelope |
| 38 | EIBCGPEOJLB/+60 | u32 list，wire0/2 |

map/message统一wire2。七个同形u32 list由实际对象地址/字段偏移和独立不同非零值区分；扫描扩大到tag1..64，不能只查1..32。空packed分支保留实际native list helper对象参数、owner、偏移及原始key，不拿无输出当支持证据。

| 叶子 | tag / clientField / offset | raw |
| --- | --- | --- |
| 66100/DMGDPCGIFDM | 1 GMKNIJBNFLK/+20；2 ABAAMEKEIFJ/+18；3 AICLAOPOLPO/+24 | u32；u32 list wire0/2；u32 |
| 27246/CKCLPDDJMII | 1 OHPFCLOBJEH/+30；2 MKNHMFHLHLK/+28；3 PONFILNCCAA/+20；4 LJMOEFOGCMP/+18；5 JJAMEEKFHEK/+38 | 五个u64 |
| 13438/JJPIHCCEBOE | 1 JFKPLJIAFJL/+1c；2 PKDBMPGJLIJ/+18 | 两个u32 |
| 40807/LODODHKBJHK | 1 LPNCBONKHNN/+18；2 BHKAKIPABLM/+20 | u32；u64 |
| 78350/CODAEKFNEEE | 1 NOEEBKJAFAG/+24；2/3 BNJKCHONGGE boxed/+18、判别/+20；4 AEJAFPNBBHO/+28 | u32；int64/float oneof；独立int64 |

## 实际验证、限制及复现

1,536次6类/tag1..64/wire0/1/2/5原生扫描逐一与全部metadata存储字段核对，其中78350一个Object槽展开为两个wire分支。两个`.cctor`（48178与66100）实际执行到返回；primitive codec factory为明确模型，原生initializer负责传参、组装和写static。29条选定AOT是本轮直接解码的绑定记录，不代表29个method全部运行。

24个独立图样本含全部37外字段同时输入、四叶子全部12字段非空、78350四个wire分支、两个message map交错覆盖、两个u32 map重复key清零、两条非空message-list元素，以及empty/显式0/错误wire/有符号边界。u64列表packed/unpacked混用保留`[0,9007199254740993,18446744073709551615]`；五u64叶子用不同值覆盖2^53+1、2^63和上界，防止相同类型字段错配。

11个拒绝按来源分开：4个复杂子图非空范围拒绝；4个WireInput/float原语模型拒绝（非法tag、u64溢出、packed截断、float截断）；3个真实ReadMessage错误入口（negative-length、truncated-message、recursion-limit）到达即停，未执行throw，不声称完整异常合同。managed allocation/class/generic contexts、primitive input/delegates、固定容量64集合存储为模型；具体reader、ReadMessage/limits、map adapter/setter、list append执行原生代码。没有IFix/live ABI、命名Handler、业务语义、实机或部署验收。

9项新增测试及69项关联研究回归通过；两次全新三输入核验、提取、原生扫描/独立样本运行的完整证据及摘要逐字节一致。共享Project/Harness/矩阵由主线维护。

```powershell
python tools/trace_client72_avatar_core.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-core-native.json --observations local/migration72/evidence/avatar-core-summary.json
python -m unittest tools.test_client72_avatar_core -v
```

后续应先拆小卡恢复27196（静态4类9存储）与66308（2类9存储），再处理16352（16类45存储，大部分库存已验可复用）和53824（34类238存储复杂图）。这些静态数字有重叠，不能相加或当wire数。完整Avatar组合图及独立身份/字段用途合同仍需后续验证；本工具不静默展开四个拒绝子图。
