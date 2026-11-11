# F-03d3s2 / LoadedBundle 参数、返回token与首缺（2026-11-11C）

任务：`F-03d3s2/SCENE-READER-LOADED-BUNDLE-SEMANTICS`，父项`D02-R1`。本页分析[220B候选完整原窗](CLIENT_72_D02_SCENE_READER_LOADED_BUNDLE_WINDOW_20261111C.md)，复用已验[238B工厂](CLIENT_72_D02_SCENE_READER_FACTORY_SEMANTICS_20261111C.md)及[430B值流](CLIENT_72_D02_SCENE_READER_BACKEND_SEMANTICS_20261111C.md)，不重做它们或真实payload/分组。**派工时220B集成第三待主线，本页不代替该验收，不升级自然reader或父项。**

## 结论

两个int32零在本callee的消费已有原指令依据：第二值`b=0`触发`141122120(assetBundle,assetPathHash)`，取其低32位减去第一值a，再以有符号正数门决定是否继续。compressionType数值0会把传入pageSize改成该局部正length参数，故不能无条件沿用factory传入的1MiB；a经有符号扩展作为64位参数交后继。

**返回值须纠正为opaque U64 token：旧`141122e00`最终返回RDI，它来自`141139040`的完整RAX，并非`141137eb0`返回后保存在RBX的对象值。** 返回前还检查token低16位及另一返回值的`+10`界限；这不证明完整token编码、所有权、有效buffer、可读范围或refill。下一原件只取直接token生产者`141139040`；2299B构造器、界限表来源及`141123260`分别保留缺口，不并发扩窗。

## 固定原件与本轮检查

| 来源 | 实际大小 / SHA-256 | 本轮使用 |
| --- | --- | --- |
| `local/migration72/d02-resume-20261111c/scene-loaded-bundle-window/{fresh1,fresh2}/body.bin` | 各220B / `9f1480af9cb4063240eb6445a62813f945f1ac73bfc0730e8edec0dbb4690e9e` | 完整`[146d43ca0,146d43d7c)`、70条原hex |
| 同两目录全部9件 | 逐byte相同；asm SHA `0291bca14a6058ec4c27ee2033dc548525667a6781770c5e86181d836240fc98`；extraction SHA `b0d913ad0c9dd5cd42be8a44155be8994c5fcbb2ceff4099955a208dd54b725b` | 核连续原hex重建、15引用、一RET、无外tail、五stock及范围账 |
| `local/migration72/d02-active/resources/index/native-final-1.json` | 312462B / `a57966dabbc34b177d6a03bd76dfc91e02ba6bd9fe606e9974e66c41bb9dba13` | 直接复用两helper完整带hex原件，按连续地址重建并核全部code SHA，未新采PE |
| `local/migration72/d02-active/resource-index-astra/native-final-1.json` | 55885B / `6a24ef69ded74323049144849764ce4e727f99c5090e8e3d508e8f167402915c` | 五输入同源；仅复用构造器已记录extent/hash，不冒称其raw已保存 |
| `metadata.private.pkl` / `client72-signatures.json` | 完整大小/hash沿用固定request：metadata `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`；signatures `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` | 完整pin后禁止globals解析metadata，核native目标在ordinary registeredAddress中的零命中，不编造AOT |

两个复用helper分别为：`141122120..1411221b1`，145B/40条，SHA `69d44f0ee9fbd535778ea75f6715e03680a1f8b32d334e1296812c53db78ae2e`；`141122e00..141122fa2`，418B/99条，SHA `e7ba3606c5c884e05c5e9e0b9e2068adc20c143f3a29edac1b10aef715e06097`。恢复raw仅写独占忽略目录。旧前者report记录三输入，后者native范围report记录五输入；request保留来源差异，不把旧三输入报告改写成新五stock采集。

本轮来源检查文件为`local/migration72/d02-resume-20261111c/scene-loaded-bundle-semantics/semantic-source-check.private.json`，**6732B / SHA `88a4f3340914b91c358829a30ff8dc7d3846810c5683aabad95e0f6776f95061`**。实际完成候选双fresh9件比较、220B原hex重建、两个既存helper原hex/extent/code SHA核对、tool SHA及固定metadata核验。未重跑Terra八门禁或pure audit，原测试范围见原窗专题；220B来源集成第三仍归主线。

## 220B：参数和所有返回

设`h=assetPathHash:int32`、a/b为原factory两个int32、c为CompressionType数值、P为pageSize。入口保存h到ESI、a到R15D、b到EBP、c到R14D、P到EDI、receiver到RBX。六次push及`sub rsp,38h`后，`[rsp+90h]/[rsp+98h]`分别对应入口栈的c/P。自然flag未读。

| 原PC / 分支 | 观察：准确消费 | 推导与未知 |
| --- | --- | --- |
| `3cc6/3ccd` | byte`1457c995d`非零进wrapper；零进本地路径 | 不能以静态存在断言自然选本地分支 |
| `3ccf..3cd5` | b为0进`3d1c`；b非零且signed≤0进零出口`3d30`；b正数进`3cd7` | 未以unsigned length或64位数学替代这三个分支 |
| `3d1c..3d2e`，b=0 | RCX=`[LoadedBundle+10]`，EDX=h，调用`141122120`。EBP=EAX后执行32位`sub ebp,r15d`；signed>0才进`3cd7` | 局部候选length `L=int32((N-a) mod 2^32)`，N为helper低32返回。零本身不是“0字节文件”；a=0/b=0时先查询N，但N和实际可读范围仍未观察 |
| `3cd7..3cda` | c=0时以EBP替换EDI | 本地page参数先变成L，原1MiB不继续生效；不能由枚举数值0推断算法或已解码 |
| `3cdd/3ce9` | 先调用`159ad6130`，再以ECX=0、EDX=h、R8=前一RAX调用`159c1aeb0` | 后一RAX成为下面调用的第一实参S；两个helper本包只保留原边，不猜S含义或代答 |
| `3cee/3cf0` | signed比较EDI与L，若EDI≥L则改为L | 传入page参数为signed min(前值,L)。本body没有独立拒绝负P；不可自行钳为正数 |
| `3cf3..3d15` | RDX=`[receiver+10]`；RCX=S；R8D=h；R9D=L；第五参=`sign_extend_64(page)`；第六参DWORD=c；第七参=`sign_extend_64(a)`；调用`141122e00` | 64位扩展明确有符号，不能用uint32扩展a/page。局部L正值只证明调用实参，不证明helper实际读取L字节 |
| `3d1a → 3d32..3d3e` | 保留`141122e00`的RAX，恢复栈后唯一RET | 不存在独立尾跳；最终值必须由该helper合同解释 |
| `3d30 → 3d32` | `xor eax,eax`后同RET | 明确64位0；包括原b负值、b零查询后减a非正值 |
| `3d3f..3d74` | RCX=动态wrapper；RDX=原receiver；R8D=h；R9D=a；栈中为b/c/P；调用`147021cf0`后进共同epilogue | wrapper返回RAX原样保留，未执行或确认其自然选择/返回 |
| `3d76/3d7b` | wrapper receiver为null进入`1404c2be0`；末尾INT3 | 未取helper体，不把错误helper当成功或擅定抛出合同 |

因此原a=0、b=0、c=0在本地路径上的**条件实参关系**已闭：N的低32有符号值为正时L=N，page=L，64位a=0；否则返0。这里没有把N填成782517，也没有把已经恢复的文件强制装成自然AssetBundle返回。b非零时不走该长度查询，a仍原样有符号扩展；零参数含义不能脱离这个实际分支泛化。

## 已存helper：长度来源与返回角色纠正

`141122120`先对AssetBundle实参判null，非null经`14145be60`取得另一值；该值为零时直接返0。其后调用已验`141a12b90`，以h完整32位构造八位小写hex名称，再以原转换值/该名称交`141136fe0`，保留EAX并在临时字符串清理后返回。它有两个真实RET：零路径与该EAX路径。这里只复用准确输入/返回关系，不把未审callee的低32结果、长度或自然archive/index状态代为成功。

`141122e00`的99条完整原件进一步支持：

1. AssetBundle实参RDX为null，或`14145be60`返回零时，直接返0。正常继续路径同样通过`141a12b90`取得h对应原名称。
2. 在`141122e8a`请求一块72B/alignment16的native存储；成功后将S转换后的临时值、转换后的AssetBundle值、h名称和调用实参交`141137eb0`。其原参数次序为receiver、S临时地址、转换值、h名称地址、L、a的64位值、page的64位值、c、out-status地址。构造器返回RAX存RBX；未读其raw，不能断言它必等于先前分配指针或已构造成功。
3. out-status字节为非零时，`141122f32`以RCX=RBX调用`141139040`。`f3a`将**完整RAX**保存到RDI；`f3d`再调用`141139030`取得p。原检查为`low16(RDI)≥1`，且`zero_extend_16(RDI) < u64([p+10])`。检查通过才跳过失败清理，最终`f88 mov rax,rdi`返回完整RDI，不把它截成16位。
4. out-status为零或上述检查不通过时，RBX非零先调用`141138bd0`，之后以label`39h`交释放helper，清EDI为0；临时字符串清理后返回0。申请失败同样经零结果路径。不能用“曾得到RBX”代替这些后置条件。

观察支持“最终是经低16位和界限检查的opaque U64值”。据此可推导它具有索引/token特征；**编码、是否含generation/类型位、表身份和所有权仍未知**，不把该推导提升成有效buffer或可调用native对象。reader工厂后续把此完整值写`+20`，与调用`141123260`后低32写`+28`是另一个已有消费者事实；该长度helper及refill仍未闭。

旧`resource-index-astra/native-final-1.json`对`141137eb0..1411387ab`仅保存2299B范围与code SHA `98d7d2a557de8579bfff71293a674a8114d652e148f7cbf5ccbf58afa551ef8e`，无完整hex；不能把该静态记录说成已读构造器。本包不为它补窗。

## 唯一成熟后继

在已有`resources/index`、resource-index/provider/blob/payload相关原件和定向文档/工具引用中，没有找到`141139040`完整带raw body；实际只复用到其原CALL。该范围内的缺输入不等于穷尽本机，更不是外部blocked。

主线已选定优先直接token生产者。固定request：

`local/migration72/d02-resume-20261111c/scene-loaded-bundle-semantics/token-producer-one-window-request.private.json`，**15731B / SHA `eb57e978e7792002c4f8589529621add7080c00093e49125622e6144494a0ba7`**。

| 项目 | 唯一窗口规格 |
| --- | --- |
| 前置与入口 | 先由主线接收220B来源集成第三；新target仅native `141139040`，不是整个native reader或构造器 |
| caller | 复用完整418B/99条`141122e00..141122fa2`原hex和SHA；本轮恢复raw在独占目录`source-141122e00.bin`。新PE仅核`141122f32..f3a`的8B `488bcbe806610100`，其中`f35`的真实E8目标必须是`141139040` |
| AOT | 不适用。完整固定ordinary metadata的registeredAddress没有该目标；不编造owner/method/slot，不为此读取AOT |
| pdata与预算 | 固定exception目录hash；exact与containing各唯一且同一记录，保存诊断/12B pdata/pre-body后才取一个完整正extent，≤1024B/256条，可执行且file-backed。末尾当前未知，只用原记录EndAddress |
| 无记录时 | 保存实际exact/containing诊断，0 body返回主线。不猜leaf长度、不读16/32B前缀、不预加fallback或改门禁；按实际结果再判断受支持源合同 |
| 输出与验收 | 完整raw/asm/SHA、CALL/branch/RIP/RET/tail及来源账；固定五stock、caller/context/E8、正负rel32和64位目标、缺失/重复/冲突pdata、预算/节/短读/连续解码门禁；双fresh、独立纯artifact audit、主线第三 |
| 回答及停止 | 只解释输入值怎样生成完整RAX及字段/位来源；区分低16检查与完整64位返回。首个仍缺callee/data边保存后返回；constructor`141137eb0`、表`141139030`、长度`141123260`和refill均不并采 |

选择它是因为其返回直接决定写入reader`+20`的编码/角色；新构造器或表来源窗口不能代供这个表达式。即使getter后续证明为简单字段读取，也不能据此批准natural backend或refill；生命周期、有效范围和实际对象仍按证据另验。

本包尚不能交完整运行实现、成功driver或自然reader投影。已闭局部算术可供后续真实合同检查复用：b零/非零/负值，32位减法及signed门，c零导致page=L、非零的signed min，以及a/page的64位符号扩展。若后续确需实现这些关系，测试须核上述独立原指令期望，不能以自编自解、文件framing或header成功代替backend/错误/EOF验收；本轮不另开无必要的机械投影任务。

## 交付边界

本有界语义项只新增本页与独占忽略目录的检查、原件派生和request。没有修改旧报告/工具、Project/Harness/HANDOFF、运行代码/Proto/config/patch/resources，也没有Git/index、构建、JVM、进程、端口、客户端、DB或部署操作。无需重启；主线负责第三、共享状态及本地提交。

没有新安全拒绝；A第二allocator/返回/Default依赖及B组合loader仍冻结。未审helper不因当前引用表没有公开A地址就自动独立；若准确CALL/data边指向拒绝项，保存原边并停其依赖；新拒绝保留原文立即停，不换模型/工具/代理/措辞或间接重试。World主动触发及Proud/C7采样不在本包范围，不复采现场。

本项交回即释放分析槽位。下一项仅按上述request取一个原body；源码可定位性和可能无pdata都不预先升级成外部阻塞，不新增Scene/resourceReady/D-02完成标记。
