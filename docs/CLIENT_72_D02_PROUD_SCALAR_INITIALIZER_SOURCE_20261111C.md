# E-01d1 / R3-PROUD-SCALAR-INITIALIZER-SOURCE

2026-11-11C，父项D02-R3。**已把scalar table与seed准确绑定到同一owner17408；尚未证明初始化/更新writer，但已形成一个九入口、同候选族的有界取源规格。** 这是[条件setter/getter合同](CLIENT_72_D02_PROUD_SCALAR_SETTER_SEMANTICS_20261111C.md)的定向后继，直接回答真实Proud行所需的forward/inverse/seed同源规则，不构造通用codec或增加采样。

当前writer未定位属于普通技术首缺，不能写成全线外部阻塞。固定PE可供主线按下述规格继续静态取源；本分析没有读新body。主线报告本轮Proud热采样在O0冷停、未达F/M，此处只保留该范围，不追加live。

## 准确字段归属

复核原scalar报告的17个pin，另固定本包metadata、signature、已存索引/原件。完整metadata以禁止global class的unpickler解析后，按**准确runtime编码offset**定向查字段；两个匹配均唯一属于17408 `BBALBHDMBAP`，不是58209：

| 存储 | 准确声明 | 与原消费者的关联 |
| --- | --- | --- |
| `[QWORD(14586b600)+e970]` | field82448 `FANPLHGGPBK`，`byte[]`，rawOffset=`400e970`，attributes16 | scalar getter/setter明确先读root，再取root+e970的数组；字段类型、reference静态offset与原读一致。没有读取数组内容。 |
| `U32[145872d30]` | field82449 `BMFLGCMHNDE`，uint32，rawOffset=`2007720`，attributes16 | 已验Scene runtime报告的primitiveBase=`14586b610`；`14586b610+7720=145872d30`，正好接原getter/setter的RIP地址。 |
| 同owner第三字段 | field82450 `IFLGGHGKBKF`，System.Random/type280，rawOffset=`1000000` | 准确声明与既有TLS helper资料相接；本轮不验证其返回、分配或归属。 |

offset和attributes上列常量为十六进制。固定metadata为117201003B，SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`；primitiveBase原报告为`local/migration72/d02-active/main/scene-runtime-context-third.json`，3870401B，SHA `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`。本轮没有重新读取PE中的primitive根或目标global。

58209仅有一个实例DWORD字段，没有`.cctor`。17408完整17个声明也没有`.cctor`，所以不能指定一个猜测的静态构造器。固定signature明确标记返回类型not-recovered；零参方法不在其参数方法列表出现，不能据此补返回类型。

## 候选族与可缩减项

17408的17个方法可按准确声明缩成以下范围，**不按混淆名字选择第一个**：

| methodIndex | 普通registered entry | 本包处理 |
| --- | --- | --- |
| 131980 | `149a75020` | 保留 |
| 131983 | `149a75080` | 保留 |
| 131984 | `149a750e0` | 保留 |
| 131985 | `149a752b0` | 保留；名字GGDILPOJMCP不作已证Init/writer |
| 131986 | `149a75330` | 保留 |
| 131987 | `149a75390` | 保留 |
| 131989 | `149a753f0` | 保留 |
| 131991 | `149a75450` | 保留 |
| 131993 | `149a754b0` | 保留 |
| 131995 | `149a75140` | 排除：已有TLS Random helper来源及准确allocator边 |
| 131979/131981/131982/131988/131990/131992/131994 | registeredAddress均0 | 排除本族：已有签名是七个Dictionary&lt;MVAR,encoded scalar&gt;、MVAR key及byref primitive参数方法；不猜泛型实例/AOT，也不为本题展开其字典业务。 |

九个保留项均flags96、static、零显式参数、virtualSlotFFFF，注册entry互异。`analysis/business-methods.json`独立保存的地址→owner/index记录与固定metadata相同，但它只是声明索引，不是writer xref或函数体。当前没有可用的返回类型、实际写引用或caller把九项进一步可靠区分；把其中任一项按名字或相邻间距当唯一writer，都会补入现成证据没有给出的语义。因此此轮最小**候选族定位**范围为九项，采完仍不承诺一定找到writer或覆盖所有外部writer。

固定`analysis/disasm-149a75140.txt`（3070B，SHA `d6aed2f2c89931be36e95d8973e02b238015451a1a275ea6f75d1da197fb4a15`）记录`149a751f3→1405336c0`。已保留并停下此TLS helper的返回/归属及执行依赖，没有读该callee。既有SessionRuntime对它的Random模型不作为自然初始化成功证据，也不导入执行。本族删去该helper和A/B动作/产物后仍可独立取得另外九个静态source；若某候选再出现准确拒绝CALL/data边，只记录并停止对应依赖，不跨其返回分析或运行模型。

## 已查来源和工具支持

本轮定向检查固定metadata/signature、`analysis/`、`evidence/`、`d02-active/`中的准确九entry、两个runtime编码offset及table/seed地址，以及既存r3-mapping的native source索引。命中主要为metadata声明复制、已验scalar读写器、数学forward重建和TLS Random helper；没有在这些已查来源中得到九项完整原body或明确writer site。旧`codec-table-xrefs.json`引用的是独立archive codec的1457573e0/3e8/3f0，不能冒充resource scalar的e970/seed来源。此结论限于实际检查的材料，不宣称所有来源均不存在writer。

采用reverse-engineer-anything的原证/推导/未知分层，实际调用一次只读`binary_session({})`：当前open=false、retained records=0、provider binding=null；Ghidra可用，但xrefs需已打开target。四份已有REA snapshot的target SHA均为固定AccountPlatNative.dll的`adecdf0b…55a6c25`，不是本题固定YuanShen.exe；resource-index的REA export为105B、records/providers空。没有把SDK项目当EXE交叉引用，也没有重新打开它们。

现有Ghidra文档说明只有默认auto-analysis完成后才暴露查询socket，当前无可复用EXE项目。因此本轮不为一个seed地址启动全PE分析。这里是工具状态及成本约束，**不意味着本机PE不可继续研究**。更小的优先路线仍是：如果取得同一固定EXE的已分析引用索引，只查准确数据地址`145872d30`的incoming site、read/write/unknown和containing procedure，不读body/global；一个可靠write owner就能把九项缩成单窗。已查材料没有这份索引，九入口取源比新建全PE分析范围更小。

## 中文最小规划：九入口源定位包

拟交Terra的任务为`E-01d1/R3-PROUD-SCALAR-INITIALIZER-FAMILY-SOURCE`，**由主线审查后决定执行**。目标只找原seed/RIP写及table+e970写候选，候选不预先叫writer。没有这九项的独立caller来源，入窗依据明确为“准确ordinary方法声明＋fresh普通AOT＋唯一pdata”，不伪造E8。

1. 一次入口核原17pin、本包固定metadata/signature/索引及当前GAME_PATH五完整stock文件。只读现有工具，输出放独立私有子目录；不运行目标、JVM或任何现场。
2. 普通CodeReg cell1422f9950仅取一次8B得到当前P；只取九个`P+methodIndex*8`槽，共72B。各当前QWORD必须精确等于各自声明entry。旧P1428a54d0只是提示，不能直接套旧slot地址。
3. 共用一次当前PE exception目录索引，为九个entry各核exact与containing均唯一且是同一条12B原记录。先保存全部诊断/原record，要求完整可执行范围、互不重叠，按相同entry去重，拒绝范围覆盖被排除149a75140或1405336c0。实际九entry互异，当前去重预期仍九个；规则不把相邻声明当结束。
4. **先取得所有真实extent并计算去重累计字节，全部门通过才读任一body。** 每体上限512B/128条，去重总上限2048B/512条。现在九个具体pdata/end/code SHA均未知；这些数值是明确预算，不是按邻entry估出的长度。任何字节预算或边界失败留下pre-body报告，body读取为0；交主线调整，不能截短函数。
5. 只读这些完整body，连续decode、逐byte重组，记录实际SHA与指令数；指令预算或decode失败保留原件后停，不修代码或扩窗。抽取准确seed RIP引用及operand读/写/unknown、e970偏移操作的base register/写候选、root14586b600引用及CALL/branch索引。`[未绑定寄存器+e970]`不是已经证明的table写，不能把它升级。
6. 不跟随callee、不读xdata、table/seed内容或邻窗。准确拒绝边只保留源site并冻结其依赖；不跨返回推导，不让另一个候选成为继续该helper的入口。其他独立source可完成。两fresh及主线第三原件逐byte核一致，再交Sol恢复真正writer语义。

### 真实累计预算

| 项目 | 上限／已知来源 |
| --- | --- |
| 新unique函数体 | 最多9个；每个≤512B/128条，去重累计≤2048B/512条；真实extent待pdata。 |
| 普通AOT证据 | 共享cell8B＋九slot72B＝80B。 |
| 选中pdata证据 | 最多9×12B＝108B；必须先保存，不能猜end。 |
| 上述bounded evidence payload | ≤2236B；不包含下述索引和完整身份核验开销。 |
| exception目录 | 已存来源给RVA1a4e6a40、14344080B；当前文件须重核目录并只索引一次，单列真实开销。它是元数据索引，不是所有代码扫描。 |
| 五完整stock身份hash | 固定输入合计550661784B，逐份完整核验；源pin文件另计实际大小。不能把这轮描述为只读2KB。 |
| 明确为0 | caller字节、callee body、xdata、运行global/table/seed内容、CPU/live、构建/JVM/DB/部署。 |

若全体真实extent超过预算，结果是可复核的普通预算停止，不能将其改写为外部输入阻塞或在未通知主线时只读一部分。预算以完整函数和distinct entry计，不能把重叠/别名当额外可读空间。

## 实现、验收与后继出口

完整request为`local/migration72/d02-resume-20261111c/proud-scalar-initializer-source/family-window-request.private.json`。只允许其`family-source-window/`中新建collector、独立测试、fresh1/fresh2/main-third及审计日志；本页、旧工具/专题、共享索引、生产/Proto/配置/patch/resources和Git index只读。

必要测试覆盖：九声明/slot与排除项；当前AOT漂移；pdata缺失/重复/exact-containing分歧/重叠及可执行边界；**累计门必须先于首次body**；完整连续原bytes/指令预算；独立合成指令对seed read/write/unknown的区分；未绑定base+e970保持候选；拒绝边不跟随/不执行；原件双fresh与主线第三确定性。不是把collector自己的write分类当最终writer语义，Sol须核原PC和根值流。

取源完成后的最小语义包只消费命中的准确body及原引用，区分初始化、更新、仅getter或未绑定store，检查table前四quarter与后四quarter怎样形成、seed何时写及正常/错误条件。需要未审callee时交准确单边与最小完整窗，不自动追。若遇149a75140、1405336c0或其它拒绝CALL/data边，停对应返回/归属依赖；不能用固定Random或先前数学forward模型造成功。即使静态证明互逆，实际codec epoch、QueryWrapper寿命、reader和41105行仍分别保留；后继投影仍只覆盖key22101两字段，不补addProps/paramList/OpenConfig。

本候选族通过只表示限定source/index齐备。它不保证九项包含全部writer、不证明自然初始化已执行，也不晋级actualEncoded/getter、resourceReady、父项或D-02。不需要用户再次ready才能做这个静态源包。

## 本轮实际验证与交付

新私有`audit-source-owner.private.py`已实际exit0：复核原17pin、本包14固定源、两个准确offset唯一owner、完整17声明/九普通候选、七泛型参数角色、primitiveBase算术、排除TLS准确CALL及四REA target身份。只做元数据/原件核对，没有运行旧runtime模型或研究被拒返回。

- `source-review.private.json`：12703B，SHA `874a12ac800427e0668a22217ca27fdc95ed00f310269d46e5123297657c8be1`。
- `family-window-request.private.json`：13307B，SHA `7b9d4bac21de80805534b04867d96b57b5957766d8502b6a2e8480488a7d6cd8`。
- 复现：`python local/migration72/d02-resume-20261111c/proud-scalar-initializer-source/audit-source-owner.private.py`。

当前只写本专题及独立私有根；新PE函数/global内容读取、进程/端口/客户端/调试器/live/CPU/Git/构建/Java/DB均0。没有新安全拒绝；已知TLS allocator边保持停止。下一可执行项是主线审核并派出这个九候选源定位包，max不做机械实现。
