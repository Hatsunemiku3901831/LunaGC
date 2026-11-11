# F-03d3s2 / Token producer 有界语义与reader下一必要桥（2026-11-11C）

任务：`F-03d3s2/SCENE-READER-TOKEN-PRODUCER-SEMANTICS`，父项`D02-R1`。本页消费[44B原窗](CLIENT_72_D02_SCENE_READER_TOKEN_PRODUCER_WINDOW_20261111C.md)，复用此前[220B/418B返回合同](CLIENT_72_D02_SCENE_LOADED_BUNDLE_SEMANTICS_20261111C.md)及已验reader/原文件语法，不重复原研究。

主线已通知44B集成第三通过：七件同双fresh，6门禁、13条/3引用/2RET、原caller/E8/pdata及ordinary零命中/AOT零读取均独立复核。本页复用该验收，不重新采来源；这里只关闭下述静态值流与必要性判断，不能升级自然reader/backend、Scene、resourceReady或D-02。

## 本窗确切返回什么

设入口RCX为x、入口RSP为S。完整13条中，x为零直接返回0；x非零则：

| 原PC | 观察 | 推导/未知 |
| --- | --- | --- |
| `141139040/044/047` | 栈减28h，test原RCX，非零跳`9050` | 无其它输入校验或对象字段读取 |
| `9049..904f` | 清EAX，恢复栈后RET | null输入明确返回完整U64零 |
| `9050` | R8=原RCX=x | 原64位值保持，不在本窗解引用x或拆位 |
| `9053` | RDX=`当前RSP+30h`=S+8 | 指向caller home地址；本窗未预置其内容。callee是否写此处、它是否等于返回地址均未知，不能先称out token |
| `9058` | RCX=`QWORD[145746908]` | RIP实际指向这个cell；没有读取cell内容，也没有类型/实例证明。不能凭常见编译器形状命名它 |
| `905f` | 调用`1415cb9f0`，三个实参值如上 | 返回p及对home地址的作用未知；未读该callee完整原件 |
| `9064..906b` | RAX=`QWORD[p]`，恢复栈后RET | 返回完整64位内存值。没有shift、XOR、mask、符号扩展或本地编码；不是直接读取x的固定字段 |

非零分支的准确表达式为`loadU64(F(loadU64(145746908), S+8, x))`。这里F仅代表准确CALL，不预设查找、插入、分配、注册或cookie检查。指令没有对p再作null检查；不能给未知callee补有利返回使实验继续。

定向检查`resources/`、resource-index/provider/blob/payload/index/locator原件、`astra-resources/`及文档/工具的两个准确地址，没有发现`1415cb9f0`完整raw或`145746908`内容/类型来源。此为实际检查范围，不声称本机穷尽。没有新读PE、callee或globals。

## 为什么token内部编码现在后移

已验418B消费者将本函数完整RAX存RDI，再检查其低16位和另一返回值`+10`的界限；成功时返回完整RDI。工厂将这个完整U64写reader`+20`，不是把先前RBX对象当buffer。这些事实不要求在中间转换token编码。

更直接的下游原件表明，当前需要的是**保留opaque U64并核它怎样使reader可读**：

| 已验消费者 | 实际消费+20/相应U64 | 能解锁与仍缺 |
| --- | --- | --- |
| Scene 131B前缀`14b6accb0..14b6acd33` | `14b6accc1`检查reader`+20`；非零在`14b6accd0`以同reader、EDX=1调用`14f614760`，之后从同reader重读byte[]`+10`和cursor`+1c`，再读首字节。没有拆token编码 | 这是非零backend接回真实文件语法的首个状态桥；helper可以改变reader，不能跳过或补返回 |
| Scene字典2675B原窗 | count4、key长度2/key字节数、child count4、child长度2/字节数等六类非零`+20`边调用同helper，再使用当时reader | 能把已经恢复的707组语法与自然reader状态相接；文件解析本身没有验证该桥 |
| 已验Proud base/element/array | 非零`+20`时按2/4/块长等调用同`42005.m350685`，再重读数组/游标 | 同一源窗也服务准确row消费者，不需要并开整棵provider |
| 已验Proud1438B row reader | 对缓存U64原样调用`141123260`；原U64、数组、0、B和sign-extended A交`141123190`，未拆token位 | 这两个API的参数/返回/短读仍未闭；opaque透传是值流合同，不是它们已成功 |
| ResourceRuntime/NaturalRuntime | 既有模型提供byte[]及`+1c`游标存储；原文件reference消费有界bytes | 这些是明确模型，不验证native token、`+18`初始化或refill；不能用模型通过替代自然backend合同 |

因此继续追`1415cb9f0`的通用内部、该global的当前值或token位编码，当前没有比实际reader helper更直接的D02后继价值。2299B构造器也先后移：它可能在需要复现开读对象/寿命时成为必要，但它不能代供reader的byte[]/cursor状态桥。本包不为“整条链都解释”展开这些内部。

这不表示任意U64都可用；自然token、实际对象构造、有效长度/可读范围、读API与寿命仍未验。只有以后准确消费者实际拆位、要求字段或缺少构造状态时，才按那条证据重新评估对应最小输入。

## 下一项为何独立且必要

工厂普通native分支在取得对象后先清`+10=null`、`+1c=0`，随后只给`+20`写token、`+28`写长度helper低32位。Scene前缀要读取byte[]，在`+20`非零时必先调用`14f614760(reader,1)`。如果没有该helper的状态合同，就不能证明已清null的`+10`怎样进入实际数组读取，也不能判断`+18`何时使用/重置、cursor如何推进或refill/错误怎样处理。

这使单个helper原窗具有明确出口：确认五个reader字段、请求数、实际read/refill调用和返回检查，或者给第一个准确缺输入。静态取得这份合同不需要先构造自然reader、改其字段或让未知I/O成功。

reader对象来自工厂`[klass+f0]`的准确目标/重置/生命周期仍未知；当前没有绑定到A拒绝allocator的具体reader CALL/data边。World的`14828ba31→1405336c0`只约束该World边，不能泛封本reader。反之，新helper若真正出现准确A依赖，就保存并停止对应边；不能借这个专题接续拒绝动作。

## 固定来源与唯一单窗规格

本轮实际核44B双fresh七件逐byte一致、完整hash、13条连续hex重建、3引用/2RET、ordinary0/AOT0账；核旧Scene131B双fresh原bytes、完整hash、42条连续hex、13B setup/E8及准确metadata/signature。没有重复6tests或第三采集。

| 固定源 | 大小 / SHA-256 |
| --- | --- |
| token producer body`[141139040,14113906c)` | 44B / `67c5fed7ca83adc556a1eac06b479a740fb9ff38c428ff1e04d012f834933621` |
| token pure audit | `47a99cd8ca3ba43c9e3772289485f2d58c2ab6bd9abadce4cbf37971925d4154` |
| Scene131B原caller`[14b6accb0,14b6acd33)` | 131B / `c576218f5e911876ad28cce91c7e55df4dcc7ac468b7adbbcd56757fbf230cbe` |
| Scene parser语义主线第三 | 27312B / `698cf34f6544786f6343dbdbb63ecd3a354d05e76a2dee97228ce051681d5632` |
| Scene字典语义主线第三 | 74815B / `3162a55e75712cc55873cfecec31adfb9fd6640175bba1f52d2fb5f8ed2b5f19` |
| metadata / signatures | 原完整pin为`df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` / `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` |

来源检查：`local/migration72/d02-resume-20261111c/scene-token-semantics/semantic-source-check.private.json`，**7558B / SHA `3c795f26db536089bd8690b8d48c90d926c3d68a8beea163808634ba92472241`**。reader helper完整raw在所查既存原件中未找到，已有材料仅声明、准确CALL与保留的未知边；不重取旧parser。

唯一任务：`F-03d3s2/SCENE-READER-REFILL-ONE-WINDOW`。request为`local/migration72/d02-resume-20261111c/scene-token-semantics/reader-refill-one-window-request.private.json`，**16756B / SHA `ed0569bc4236b9bbd8c520104a0d444731571ba18cc09a502f6e68aabf161ada`**。

| 项目 | 精确机械范围 |
| --- | --- |
| 入口声明 | owner42005 `OFMKHIMCGKE`、m350685 `MBCIMMBMGHJ`、`14f614760`、flags86、非static、slotFFFF；唯一显式参数307541、type70、非byref int32。返回声明仍不在旧signature覆盖 |
| 原caller | 复用完整131B/42条和固定来源；新PE仅核`14b6accc8..14b6accd5`的13B `4889f9ba01000000e88b7af603`，原`ccd0` E8必须指同entry。RCX=同reader、EDX=1，不重读整个caller |
| AOT | cell`1422f9950`应指`1428a54d0`，P+350685*8=`142b523b8`应指entry。新核两QWORD16B，不沿用native token producer的AOT不适用规则 |
| pdata/body | 固定exception目录14344080B和SHA；exact/containing各唯一且同一原记录，先保存12B pdata与pre-body。只取真实完整正extent≤8192B，连续decode≤2048条；end当前未知，不按邻方法猜，不预读邻域 |
| 产物/验收 | 完整body/raw/asm/SHA、CALL/branch/RIP/RET/tail和来源账；固定五stock、metadata、caller、AOT、pdata及入口一次校验；来源漂移、错误E8/64位rel32、歧义extent、节/短读/解码门禁，双fresh、纯artifact audit、主线第三 |
| 窗后唯一问题 | 原helper怎样读写`+10/+18/+1c/+20/+28`及请求数？token是否仅opaque转发？真实read/refill的目的地/count/offset及返回/短读如何消费？正常返回是否能接实际数组读取？首缺记录后即回主线 |
| 排除/停止 | 0新callee/global/xdata/constructor/native/live；不取registry或单独长度/read API。源码/预算失败保留诊断，不虚报外部blocked；准确拒绝边或新安全拒绝立即保存原证据并停依赖，不改工具/模型/代理或间接重试 |

## 本包交付

44B已经足以保持opaque U64的准确值流，没有必要新增token编码投影/成功driver。后继只补直接服务Scene/Proud实际reader的一个原窗，不展开所有native/helper。完整reader有效状态、实际token与对象、长度/refill/寿命、World/slot114及Proud现场均未由本包验收。

本轮仅新增本页及独占忽略目录的检查/request；旧报告/工具、Project/Harness/HANDOFF、运行代码/Proto/config/patch/resources均只读。新PE/callee/global采集、目标CPU/live、Git/index、构建、JVM、进程、端口、客户端、DB及部署均0；无需重启。没有新安全拒绝，既有A/B拒绝继续有效。主线负责后继派工、共享状态和本地提交；本有界语义项交回即释放槽位。
