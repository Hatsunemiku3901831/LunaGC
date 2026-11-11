# E-01d1：Proud packed storage 与管理字段桥

2026-11-11B，Sol max 有界续项。**已有原件足以将 nativeData+30／+38 对应到 typed41105.field203376/+90、field203373/+84 的两个表示读取分支。** 这是同 wrapper 消费者的准确对应，不是把 nativeData 当作41105，也不证明发生了两个表示间的物理复制或真实行构造。

先核独立性：本项只用已存 Proud derived parser、3179B producer、435B native getter、metadata 和条件单key文件输出。删除A第二返回及B组合loader动作／产物后仍成立，且不需要热Equals或自然map hit。没有分析被拒的第二 allocator 返回或热Default归属合同，也没有发现本静态桥到它们的确定CALL/data依赖；不将整个row/parser研究泛封为A阻塞。

## 已有准确桥：无需新窗

| 同一 wrapper 的分支 | group 字段对应 | 基础等级字段对应 |
| --- | --- | --- |
| `wrapper+40`非null，metadata声明41105 | `14d7ad3bb`读41105+90，field203376，typed58209 | `14d7ad3fb`读41105+84，field203373，typed58209 |
| `wrapper+40`为null | `14d7ad3d0/3d4`取同wrapper+8并清低8位；`3db`参数offset30，`3e0`调435B getter | `14d7ad410/414`同样取去tag handle；`41b`参数offset38，`420`调同getter |
| 两表示汇合 | `14d7ad3e5`保存encoded DWORD，`3eb`经58209 getter | `14d7ad425`保存encoded DWORD，`42b`经同58209 getter |

435B getter是57708.m479099 / `148ba9780`。非零、非负handle路线在 `148ba97ee` 调 `146e031f0`，`97f6`取第一个out QWORD，`9800`按带符号扩展的offset读DWORD。derived parser则在 `149f0c2f5/2f9`从本wrapper+8取得相同形式的去tag handle，在 `c32b`调**同一** `146e031f0`，`c330/c34c`把第一个out QWORD保留为nativeData基址，再于 `d169/d4ba`写+30／+38。

两处调用的函数与实参形式相同，足以对齐存储槽用途；当前没有实际观察两个调用返回同一指针、存储存续期或自然wrapper状态。getter的零handle、负handle、class-init及owner清理分支均继续保留原条件，不以正handle路线覆盖全体。

固定key22101的已验条件文件记录为：

| native槽 | 对应管理字段 | 原文件span | setter入参 | 原presence |
| --- | --- | --- | ---: | --- |
| +30 | 41105.field203376 / +90 | [218,222) | 221 | P&4000000，置位 |
| +38 | 41105.field203373 / +84 | [226,230) | 1 | rawH&800，置位 |

只批准这两个字段对应，不按内存次序、数值相似或混淆同名补其它字段。+2c的22101、+34的1、+42的2仍不据此分配管理fieldIndex。组／基础等级用途直接复用 [旧pair合同](CLIENT_72_D02_OLD_PROUD_PAIR_20261111.md)，没有重做该用途研究；完整81 span文件语法直接复用 [单key reference](CLIENT_72_D02_PROUD_SINGLE_KEY_REFERENCE_20261111.md)。旧 [derived专题](CLIENT_72_D02_PROUD_DERIVED_FILE_CONTRACT_20261111.md)冻结时的两字段unknown已被后续getter证据缩窄，原专题不重写。

## 现在可交 Terra 的独立小出口

两字段的**输入归属schema**已完整，可直接实现私有 `bind-conditional-row-fields.private.py`：读取固定单key报告，为上述两个token输出准确typed41105字段身份、native槽、span、presence、setter输入和call/copy/decode来源PC；不重解析文件，不改旧报告。保留 `actualEncoded=null`、`actualGetterU32=null`、`actualManagedObject=false`。

允许该私有脚本、独立同目录测试、新basename的 `conditional-fields-*.private.json`，均位于 `local/migration72/d02-resume-20261111b/proud-gate/row-bridge/`。测试以独立字面表核两项字段／span／输入，保持实际值unknown；错误slot、token、field或来源即拒绝，不补fallback名称。两fresh与主线第三要求逐byte一致。该产物关闭条件文件token到字段的输入归属，不声称原getter已返回221/1。

不生成可直接加载的完整 `ProudSkillData`。其当前 `onLoad()`会消费 `addProps.length` 和 `paramList.length`；本桥没有这两个数组、OpenConfig或其它必要配置的准确来源，不能填空数组／空ability使其通过。

## 条件可读值的唯一下一source

若下一目标是证明同codec/storage条件下 setter输入经过原存储后可由原getter读回，首个完整源缺口是 **58209.m483726 / `14aefa090` setter**，不是Proud ctor、热Default或另一UI树。

本机已存 `analysis/scene-helper-14aef9db0.txt` 覆盖getter完整listing至 `14aef9e53`；`e54`是范围外padding。其正常解码明确读取GLOBAL+e970的后四个byte quarter和seed `145872d30`，将stored DWORD xor seed后逐byte反查并组合U32。旧mapping记录其完整164B code SHA `ef485aef75c3e3d9d8814d3fb800021f431508269e31f79431adc7915593e6a0`；本轮没有新读raw code。

setter文本 `analysis/disasm-14aefa090.txt` 仅到 `14aefa0ea` 的边界分支，没有完整末尾取表、组合、store与RET；固定mapping只存157B extent/hash及两个RIP引用，没有完整正文。这个有界库存结论不声称全盘无其它源。mapping的旧codec证明明确使用捕获inverse quarter与重建forward quarter，不能冒称本Proud行当时自然codec状态已观察。

机械规格冻结在私有 `row-bridge/native-scalar-setter-window-request.private.json`：19810 B / SHA `611633a8a950d29bda6954b20a6aec0ffc2aaac027231da3092087439e83bc72`。其中同时给出上项无需新窗的字段绑定实现规格。

- 唯一原边：7674B parser内 `149f0d15d e82ecffe00` → `14aefa090`。原 `d147..d162` 27B完整setup保留EDX的U32来源、局部清零及RCX=&local；原call后 `d169`才复制到nativeData+30。
- 准确声明：owner58209、method483726、flags886、一个U32参数；RCX为unboxed DWORD receiver，返回类型未恢复。fresh普通CodeReg cell1422f9950取P，再读P+483726×8；旧slot142c56140仅提示，原指针必须唯一等于14aefa090。
- 先固定五完整输入及既存caller SHA，再保存fresh site5B、setup27B、cell8B、slot8B、唯一exact且containing同一条pdata12B及pre-body；随后才读body。完整extent须吻合旧manifest `[14aefa090,14aefa12d)`157B、SHA `5c7bd52bc54dd9ebfa01137bd2b56c741eb5f9bb2ad05331158a9c85aa20d3dc`，总上限256B／连续128条；不按邻方法推end。
- 两fresh和主线第三原件逐byte一致，完整连续decode拼回；xdata、callee、邻窗、RIP目标内容、全扫、CPU、live均0。新source只用于静态恢复setter正常数据输出及与已有getter的条件关系，不能自动读取globals或执行未知helper。

## 实际构造的其它前置仍分开

`derived+40`、`base+20`分别决定表示分支；INativeAllocator0不证明管理表示成立。reader backend、每次reader+20的inline条件、真实class/allocator正常返回、this+8 handle、QueryWrapper两个out与owner清理、codec表/seed一致性和存储存续期各有独立前置。本桥没有用清零局部冒充setter输出，也没有以文件语法正常终点替代这些运行事实。

这批前置没有被整体判作A依赖；若某个后继确实出现与被拒第二返回／热Default归属相同的CALL或data依赖，只停止那一项及其依赖并保留准确边。不得通过本C桥接续它。管理child14f6f13c0、实际native对象构造和未知helper执行均未获本包许可。

本轮实际只做固定来源、准确声明和原窗字节／listing的定向只读核对；没有运行旧parser、原CPU、JVM、数据库、构建、Git或现场。下一动作可先交Terra实现上面的两字段输入绑定；若要升级条件可读值，再由主线决定单157B source机械包。实际Proud行、pair hit、Java公式、完整资源／Avatar／D02和ready均未晋级。本max交回清晰桥及唯一最小source规格后释放。
