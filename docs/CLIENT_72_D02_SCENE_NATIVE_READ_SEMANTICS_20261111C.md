# F-03d3s2 / Native read参数、token门与实际读取首缺（2026-11-11C）

任务：`F-03d3s2/SCENE-NATIVE-READ-SEMANTICS`，父项`D02-R1/R3`。本页分析[208B候选完整原窗](CLIENT_72_D02_SCENE_NATIVE_READ_WINDOW_20261111C.md)，复用[486B refill状态合同](CLIENT_72_D02_SCENE_READER_REFILL_SEMANTICS_20261111C.md)，不重做其来源或状态投影。**派工时208B集成第三待主线，本页不代替验收，也不晋级自然reader、Scene/Proud或resourceReady。**

## 结论

本消费者的本地索引/标签运算使用token低32位：低16位作索引，bits16..31作比较标签。完整T在CALL`141139030`前未改写，未审callee仍可能受高32位影响，不能推断整个API高32无关。通过索引/标签门后，第三参数变成byte[]内的有符号字节偏移d；第四参数L与`1414de9f0(array)`的EAX所限定余量作signed min，得到正K才继续。

**本208B没有直接写array字节。** 唯一实际读取后继是`141138c70(record+8, array+20h+signExtend(d), 原第五实参完整U64, signExtend(K))`；原第五实参不截断、不改写。该CALL完整RAX在原子减操作后直接返回，没有读满K、短读、EOF或清尾判断。零结果路径可闭，实际写入及其返回含义仍不能由本窗建立。

因此下一项只取这个直接read core，避免展开全部registry、长度或错误helper。现有条件输入足以解释本地门和实参，但不能编造这些callee成功或据此运行自然reader。

## 固定来源和本轮实际检查

输入根：`local/migration72/d02-resume-20261111c/scene-native-read-window/{fresh1,fresh2}/`。两份七件逐byte相同；完整body`[141123190,141123260)`为208B，SHA `348c0faf52b09dd5046a3f80ffaacbe346ef59f03faab4446f80d277b9bf1467`，65条原hex、15引用、一RET、无外tail。本轮核完整hash、逐条连续拼回body、五stock与固定request一致及工具SHA；Terra原6门禁/pure audit范围见原窗专题，不重复计数。

原pure audit SHA `543656d20f5b1e1df992012f8ce63f15d727aaf8059c0f295fb343c28ad0f5aa`。工具SHA `8863c09ae1fc14fddbff734b6b1549e8e85829c586fb51d9aa6774f902e96d88`。metadata/signatures完整pin后禁止globals解析；新native目标ordinary registeredAddress零命中，AOT不适用。

本轮检查文件：`local/migration72/d02-resume-20261111c/scene-native-read-semantics/semantic-source-check.private.json`，**5071B / SHA `37c70bdca6fc6e8406d108636587f6f25174cfe9f2b25026a951d52916dc89d6`**。只读既存原件，0新PE/callee/global内容/目标CPU/live。

## 参数与所有路径

记T为原RCX、A为原RDX、d为原R8D的int32、L为原R9D的int32、O为原第五栈参完整QWORD。

| 原PC | 观察 | 推导/未知 |
| --- | --- | --- |
| `31a2..31b5` | 保存T，RDI=signExtend32(d)，R14=T>>16，保存L/A；调用`141139030`得p | p的真实类型、来源与可访问内容未取，不按形状命名getter |
| `31ba..31cb` | low16(T)须unsigned≥1且小于U64`[p+10]`，否则进入`3253`错误CALL | 该门不比较token高32位；未知p不能补成成功表 |
| `31d1..31ea` | i=zeroExtend16(T)，record=`QWORD[p]+16*i`；原子`xadd DWORD[record],1`返回old，再在EAX计算wrap32(old+1)，其高16与low16(T>>16)比较 | 可以称索引/标签合同；counter/tag初始化与所有权未知，不据成对操作宣布完整生命周期 |
| 标签不等，`3250→3253` | 先原子减DWORD[record]，再错误CALL | 不返回本地0；不猜错误函数异常类型或成功返回 |
| `31ec..31f6` | record为0或`QWORD[record+8]`为0，进入`3233`清EAX；record非零时原子减后共同RET | 本地明确返回64位0；不推导已有native对象有效 |
| `31f8..3202` | RCX=A，调用`1414de9f0`，取N=EAX；signed d≥signed N则进入零出口 | N仅为准确helper返回位值，未取得其raw，不把它直接确认为数组长度或安全边界 |
| `3204..320d` | V=wrap32(N−d)，K=minSigned(L,V)；signed K≤0则零出口 | 原body没有独立拒绝负d；不补入口clamp/判空，也不以64位减法替换原32位回绕 |
| `320f..3222`，K>0 | R8=原O完整QWORD；RDX=A+20h+signExtend32(d)；RCX=`QWORD[record+8]`；R9=signExtend32(K)；CALL`141138c70` | d为byte偏移，没有元素倍乘；O可包含32位以上或负数位型，原样保留。callee原型/实际写入尚未闭 |
| `3227..324f` | 原子减DWORD[record]，恢复寄存器并唯一RET，RAX保持read core原返回 | 返回位宽不能假定int32/bool/count；本body不截RAX，也不验证结果等于K |
| `322c..3231` | 清EAX、原子减record，再共同RET | d≥N或K≤0明确返回U64零，不调用read core |
| `3253..325f` | RCX=地址`143b227e8`，CALL`1414d87c0`，随后INT3 | 只保留原错误边，没有显式正常RET；不读literal内容或命名异常 |

原子加/减是本body明确的内存写，地址是record首DWORD，不是array内容。标签比较采用**加1后的**高16，而非直接比较old高16；不得忽略原32位进位。成对原子操作也不证明跨线程状态前后相同，或将其与A拒绝的分配/Default所有权合同合并。

## 对refill和Proud实际span的意义

既有refill交d=0、L=数组容量字段、O=signExtend32(base+cursor)。本API进一步得到N和K；只有额外证明长度helper的N与数组长度一致，才可把本地K解释为容量裁剪。Proud同样以d0、原B和sign-extended A调用本API，故该条件门可复用；不能用已经恢复的行bytes替代native读取。

例如在**显式条件N=512**下，d0/L330产生K330、目的A+20h；d500/L330产生K12；d512直接返回0。这些是原signed算术的独立期望，不是观察到512B数组、实际330/12B读取或EOF。固定p及record输入时，token高32位改变不影响已列本地索引/标签运算；这不代表此前未审CALL或整个API的结果不受影响。

full/short/EOF/error仍未知：这里没有byte store/copy/清尾，没有根据O对资源长度裁剪，没有在CALL后比较返回或补有效数。refill本身又忽略本API返回，所以不能用两层正常RET推断已填满buffer；应由read core原窗给出实际效果。初始reader`+18`来源/Pool仍是另一未知，没有准确A边就不泛封；也不为解这个调用推进通用registry或constructor。

## 唯一成熟后继

已在旧resource/index/provider/blob/payload/index/locator、scene-runtime、astra报告和工具/文档中定向查四个直接目标；只见旧418B对`141139030`的引用，未找到这些目标完整带raw。结论限于实际检查范围。优先read core，因为它是当前唯一得到真实目的地址/O/K的直接CALL；表、N和错误helper先按准确条件输入保留，不能以名称猜行为。

request：`local/migration72/d02-resume-20261111c/scene-native-read-semantics/read-core-one-window-request.private.json`，**13753B / SHA `b4193995e22c2633463e27e1401a1fbd0df3b636924014e6a76f09f41fdd1828`**。

| 项目 | 精确范围 |
| --- | --- |
| 前置/入口 | 主线接收208B集成第三后才采；唯一native target`141138c70`。ordinary零命中，AOT不适用/0读 |
| caller | 固定208B/65条及完整SHA；新PE只核`14112320f..3227`的24B `4c8b442460488d5720488b4b084803d64c63c8e8495a0100`，`3222`原E8 signed rel32必须同target |
| pdata/body | 固定exception目录14344080B及SHA；exact/containing各唯一同记录，先12B pdata/pre-body；完整正extent≤8192B/2048条、可执行/file-backed。end未知，只用原记录，无记录则0 body诊断返回，不猜leaf/邻窗 |
| 产物/验收 | 单body/raw/asm/SHA、全部CALL/branch/RIP/RET/tail、来源账；双fresh、纯artifact audit、主线第三；固定源/E8/64位rel32/ordinary0/pdata/节/短读/连续解码门禁 |
| 回答与停止 | 四个实际寄存器实参怎样影响读取/字节写入/O/K及完整返回；full/partial/EOF/error或准确首缺。0其它callee/global/xdata/constructor/CPU/live；只为真实读取必要边继续，不展开全部native库 |

本包不实现driver/model，不给未知callee假返回。已闭的索引/标签和参数裁剪关系可供后续条件测试复用；实际字节/错误期望必须等直接read core来源，不能由本地min或目标名称制造。

## 交付边界

本轮只新增本页及独占忽略目录的来源检查和唯一request。其它tools/专题、Project/Harness/HANDOFF/config/Proto/patch/resources/Git均只读；无runtime/model/targetCPU、构建/Java/DB/端口/client/调试器/部署操作，无需重启。没有新安全拒绝，既有A/B冻结继续；准确拒绝CALL/data边保存并停对应依赖，新拒绝保留原文立即停，不换模型/工具/包装间接重试。

主线负责第三、后继派工、共享状态和本地提交。本有界语义项交回即释放槽位，可本机取raw的技术首缺不标外部blocked。
