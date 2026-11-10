# E-01 / R3-PROUD-DERIVED-FILE-CONTRACT（2026-11-11）

## 结论与唯一下一项

**已审既存7674B derived原体；同key22101在明确普通文件条件下从142消费到318，随后必调同reader的17208.method130225 / `147e91d10`。当前仍不足以实现“单key完整条件文件reference”。** 唯一下一source已收敛为这个静态数组parser；不需要重开ctor/factory/loader/index/backend，也不为数值存储或英文alias扩helper树。

原caller在该nested正常返回后还必读4B，再走末数组默认/清理/RET。因此当前只证明条件式 `finalCursor = nestedNormalReturnCursor + 4`；候选终点334要求新证据证明nested返回330，不能反推它消费12B。物理end始终2132778，334不作reader长度或强制停止条件。

本项接续[parser](CLIENT_72_D02_PROUD_PARSER_CONTRACT_20261111.md)、[同key输入](CLIENT_72_D02_PROUD_ACTUAL_ROW_CONTRACT_20261111.md)、[base](CLIENT_72_D02_PROUD_BASE_PARSER_CONTRACT_20261111.md)、[数组](CLIENT_72_D02_PROUD_NATIVE_STRUCT_ARRAY_CONTRACT_20261111.md)和[元素](CLIENT_72_D02_PROUD_ELEMENT_READER_CONTRACT_20261111.md)合同。已先读AGENTS、README、Project、Harness、迁移/部署手册，私有中文计划2589B / SHA-256 `98c03a9b17a79a7dd2b922a56ee448240e9e44307e7d03496a914935a3d27fdd`。同必经链静态小续项直接复用固定GitHub成果，没有新检索或招募。

## 冻结输入与独立审核

唯一derived原体是63831.method529236 / `149f0c1f0`，完整范围 `[149f0c1f0,149f0dfea)`，7674B/1778条，SHA-256 `949868da171fd252ad9f8486d1a20bf5c211dbdc71065106db7775b6b18e682b`。只读 `c-proud-parser-contract/parser1`、parser2的既存八原件，双份逐byte相同；未打开当前PE、AOT、异常目录、xdata或literal。旧cell/slot/pdata仅核已存记录，不当新采证据。

root已验元素合同第三与双fresh各54095B，SHA-256 `16f23d450d86b56dfeeba5721db57c2ec8f4f3b7397b20478349df248a9fc68c`；冻结8283B reference规格SHA-256 `311bf6644f362d9aa602406b8dfe5b1e72485590924a37eb88437525a47e8a8d`。本项只依赖这些冻结原件，不读取/依赖正在实现的base-prefix工具、测试或活动Project pin。

完整Proud payload2132778B，SHA-256 `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835`；同key22101的A=4/B=330，候选 `[4,334)` SHA-256 `cfe85822a6606b824d731b5bb7ba442c8d0d4113187294de5457e3a1e04c8a6b`。既有prefix31、25个4B项、22B块、count2及两个a090元素的普通文件合同使base返回142；两个float输入+0.0，实际encoded DWORD仍unknown。本项没有重跑其CPU、locator或全表解析。

新的独立host脚本重新解码全部1778条、逐byte拼回，核stored pdata/AOT算术、双份八原件、83个准确锚点；保存204个call、299个branch、749个显式memory operand及隐式栈清单。完整metadata/signature在反序列化前核SHA。必要上下文固定11份快照，不用并行共享文档的后续变化放宽source pin。

## 普通文件条件与两套presence

必须分别声明：derived+40=null、base+20=null；此前及各选中读取处的reader+20检查走inline；相应原对象/allocator/storage/cleanup调用正常返回。文件抽象不建立typed对象，不为没有reader实参的opaque存储call赋予额外文件消费。这些条件不由INativeAllocator0推出，也不是自然模式/backend观察。

`149f0c2eb`在 `[142,146)` 读原DWORD `e2fe8a1b`，rawH=`1b8afee2`，低32位cursor加4。原 `149f0c3a1` 才执行xor576386bb，得到P=`4ce97859`；body同时保留rawH槽 `[rbp+34]` 和P槽 `[rbp+30]`。第一mask40以及许多后续字段用rawH，其余用P，不能把所有测试统一成解xor后的presence。

RDI最初保存incoming reader；`149f0c341`将它保存到 `[rbp+48]`，全body没有第二次写此槽。reader+20/+10/+1c地址又分别保留于栈槽；原调用后会重新读取它们，不能由入口inline推出任意未审callee都不改变reader。

## 选定样本的准确消费路径

下表从header后的146开始。`nativeData`是原QueryWrapper输出指针，**不是41105管理对象**；packed slot尚未凭准确getter/布局绑定原managed fieldIndex时，明确保留unknown。不能因管理字段顺序、同名或数值巧合把这些slot指定成203373/+84或203376/+90。

| 顺序 / 存储位置 | 原presence与样本结果 | 准确wire/转换 | 条件cursor |
| --- | --- | --- | --- |
| nativeData+8 | rawH&40，置位 | PCc394读4B，U32 xor67cfbc0a，直接DWORD写；原字段index映射unknown | 146→150 |
| this+30 / field316247 | P&1，置位 | PCc40e读4B，零扩展后xor51fb290d得count5；PCc4f6每项读LE16 xor59f5，五项均0，不消费字符串块 | 150→164 |
| nativeData+c | rawH&4000，置位 | PCc974读LE16，以16位加3cc2得L20；块 `[166,186)` 各LE64组xor3d4852595ff73cc2，尾4B先复制到零QWORD再xor；不批准String/Intern对象 | 164→186 |
| nativeData+14 | rawH&2000，置位 | PCcccf读4B xor0ee3a93d，直接DWORD写 | 186→190 |
| this+58 / field316252 | P&10000，置位 | PCcd28读4B，`((raw+5928ebcd) mod2^32) xor e5110d4d`得count2；PCcdd9每项4B加8f18178f mod2^32，两项输入均0 | 190→202 |
| nativeData+18 | rawH&4，未置位 | 不读wire；从原permutation/key来源形成encoded默认，实际DWORD unknown | 202 |
| nativeData+1c | P&8，置位 | PCcf56读4B，加ed7d386e mod2^32，直接DWORD写 | 202→206 |
| nativeData+20 | P&2，未置位 | 不读wire，直接DWORD默认0 | 206 |
| nativeData+24 | rawH&80000，置位 | PCd00c读4B，加e88bf06f mod2^32，直接DWORD写 | 206→210 |
| nativeData+28 | rawH&8000000，置位 | PCd065读4B，先xorc12fa19e、再加afca1ce0 mod2^32，结果1直接写 | 210→214 |
| nativeData+2c | P&10，置位 | PCd1d9读4B xor3735b1d5；输入U32为00005655，在d1ea交58209 setter | 214→218 |
| nativeData+30 | P&4000000，置位 | PCd14c读4B xorf6fdb5f0；输入U32为000000dd，在d15d交同setter | 218→222 |
| this+48 / field316250 | rawH&100000，未置位 | 不读wire；原Empty来源写输出槽，实际对象unknown | 222 |
| nativeData+34 | P&200000，置位 | PCd55f读4B，先xorf87a5ca3、再加c311e70e mod2^32；输入1，在d579交同setter | 222→226 |
| nativeData+38 | rawH&800，置位 | PCd49d读4B，加bcba8385 mod2^32；输入1，在d4ae交同setter | 226→230 |
| nativeData+3c | P&2000000，未置位 | 不读wire；原table[0] xor byte-key来源，实际byte unknown | 230 |
| this+28 / field316246 | rawH&1000，置位 | PCd701读4B xor6d228175得count20；PCd7d9每项4B xor79de8e23 | 230→314 |
| nativeData+3d | P&40000000，置位 | PCd871读4B xor35fae5db，直接DWORD写 | 314→318 |
| nativeData+41 | P&8000，未置位 | 不读wire，直接bool默认false | 318 |
| this+50 / field316251 | rawH&800000，置位 | d911必调17208.m130225/147e91d10，同reader；当前首未闭文件边 | 318→unknown |
| nativeData+42 | P&400000，置位 | nested正常返回后PCdb23再读4B，加bbf30334 mod2^32，在db34交58209 setter | unknown→+4 |
| this+38 / field316248 | rawH&20000000，未置位 | 不读wire；原Empty来源，随后清理/RET | 保持前项cursor |

这里PC均为完整地址149f0xxxx的短写，完整PC/原bytes/操作数、43个读取或块token、22个presence决定及缺省事实保存在私有报告。derived已知消费176B，连先前base只把条件cursor确定到318；完整330B消费尚未证明。字符串变换后的bytes在报告保留，不把可显示文本当业务alias或对象成功。

直接对象字段的metadata类型分别是：316247为`NativeClassArray<NativeCString,String>`；316252为`NativeClassArray_Int32<51628>`；316250/316248为`NativeClassArray_NativeStruct<58209>`；316246为`NativeClassArray_UInt32`；316251为`NativeClassArray_NativeStruct<17208>`。原this+offset写入与这些fieldIndex有直接指令/声明来源；它们与41105的管理数组/字段分开记录。

## 必经调用、既有覆盖与存储边界

上表是本样本全部分支决定，未逐叶假称完整Proud。普通正常路径在首缺之前还有以下原调用；只有同reader的消费边决定当前文件首缺，其余运行依赖也没有被补成成功。

| 阶段 | 原必经call / 条件次数 | 已有来源与保留边界 |
| --- | --- | --- |
| nativeData取得 | c32b→146e031f0 | 既有QueryWrapper合同；实际handle/输出/所有权未验。native-header分配与class初始化另受原状态条件，不宣称每次必调用。 |
| 五项string数组 | c450→1405336c0一次；c48e `call rsi`一次；c7a8→155b4b4f0五次 | count5及五个零长项是新文件事实；间接构造目标/返回和setter实现未知。零长项不走string块builder三call。 |
| 20B独立string | cae3→141fdf9c0、cbd8→141fdf310、cbfd→147155030、cc17→147099fb0、cc35→14715a160各一次 | 原体已闭字节变换与推进；memset/尾复制/Builder/Intern实际运行和对象结果未验，不接同reader。 |
| 两项enum数组 | cd44→1405336c0一次；cd7e `call r14`一次；cdef→155679770两次 | 元素wire与输入0可读；间接构造/存储未知，不借历史typed数组。 |
| 四项58209输入 | d1ea/d15d/d579/d4ae→14aefa090各一次 | owner58209/m483726的唯一声明是实例方法、一个uint32参数，无reader参数；只输出各输入U32，call后的encoded DWORD未知。历史fixture使用的permutation/key不是本样本自然值。 |
| 20项U32数组 | d741→1405336c0、d76d虚调用、d77e→15895c460各一次；d7eb→156478780二十次 | wire/count已闭，实际allocator/virtual绑定/数组所有权与setter未知。 |
| 17208数组 | d911→147e91d10一次，尚未闭 | 新样本必经同reader消费，不可从输出元素存储宽度估wire。 |

各class-init、RGCTX fallback、TLS/native分配和错误callee是否执行仍取决于原运行状态；表内次数是在normal-return文件条件下的静态分支次数，**不是native hook visits**。本卡没有旧Proud whole-parser执行覆盖可复用，也没有将旧Promote/Avatar的CPU覆盖冒作当前样本。

当前setter准确owner为58209。它的输入U32与实际encoded DWORD分开；默认encoded字段还读取global permutation/key。本卡没有当前值，不用call前清零的局部或输入bits冒充call后存储。field203376/+90、field203373/+84在41105中均声明58209，但当前packed slot到它们的精确映射和group/level业务alias仍unknown。该缺口不自动阻断已闭wire推进，也不自动把全部未映射字段升级为P0；81127+10门槛、Java公式与旧号全部恢复另验。

原reader/array null与unsigned起始下标越界有独立原错误边；header首例分别到dc71/dc76→1404c2be0和dc7b→1404c2650。后续inline读同样不能改称全宽检查：原通常只判起始cursor与DWORD长度，没有统一`cursor+width<=length`、A+B或候选334限制。host物理全宽可读、原start check和候选边界分别记录；本样本已知token均可读且未跨334。错误callee未执行，不统一成EOF，不将host拒绝当原throw已执行。

## 唯一source与终点证据

准确原边为 `149f0d911 e8fa43f8fd`→`147e91d10`。d8d5恢复RCX=同reader；rawH mask800000的d8e7分支直接到d90a，期间不改RCX；d90a令RDX=&this+50/field316251，d90e令R8D0。唯一typed声明owner17208/m130225、flags0x93、三参数：class42005、byref generic11506/instance5954 `<17208>`、INativeAllocator。return type仍unknown。17208两个字段81537/+10、81538/+14均声明58209，不能由这8B存储推出8B wire或套用52802。

对local/migration72、docs、tools的bounded exact文本及命名原窗库存检索，14份命中都是当前caller/摘要或声明/index；五份大JSON各一个原地址上下文另存5529B审计。没有取得可重解码的完整callee原体；这是选定库存的有界负结果，不声称所有未审二进制绝无该body。

唯一机械建议私有 `next-native-17208-array-source-request.zh.md`，5887B / SHA-256 `3c61c935475bd1979685b51dce5d2689795c4e0ce9b2bbc4881984a7458ac327`：须root另批；新进程固定五输入/7674B caller/精确setup与typed声明，ordinaryAOT cell1422f9950→新P+130225×8（旧P下1429a3a58仅提示），唯一exact/containing pdata先于完整code，建议≤8192B/连续≤2048条、两fresh和root第三。只建议一个body，不顺取元素、helper或管理child14f6f13c0。

终点的新证据是：d916测试P&400000，样本经d91d到dae1，db23读4B、db34交setter；db43/db4a检查rawH&20000000，样本未置位，末数组走默认来源至db85，再清理到dbc7 RET，无额外inline文件消费。在同文件/inline/正常返回条件下，最终cursor=nested返回cursor+4；**nested是否返回330及完整终点匹配仍待它的消费源**，当前不批准完整Proud reference或actual row span。

## 实际复核与交付

唯一私有根 `local/migration72/d02-resume-20261110b/c-proud-derived-file-contract/`。冻结清单10588B / SHA-256 `dbee74d7c62e241e6694e0fda2369a1611027338a1be498e614be4faff480d7e`，36源/1282942902B及11份上下文快照；大JSON按流核hash，仅提取有界文本，不将其当新增code。独立脚本26966B / SHA-256 `1b9a36476cfd5d4c543bc04840cbab58eb82f996c601434abc4aa772d4dfccd7`。

两个新host Python进程实际退出0，报告各576784B、逐byte相同，SHA-256 `4849718ddb2679ad26d821d2c74b867968e31ed11b5593d4abeb903f03eb355f`：

```powershell
python -B local/migration72/d02-resume-20261110b/c-proud-derived-file-contract/verify-contract.private.py audit fresh1.private.json
python -B local/migration72/d02-resume-20261110b/c-proud-derived-file-contract/verify-contract.private.py audit fresh2.private.json
```

复现须用此前不存在的新basename，输出排他创建。除完整静态原体与样本算术外，保留6个start-vs-width/候选边界和5个U32顺序/溢出的纯host见证，不冒称原错误执行、新单元测试套数或旧资源回归重跑。准备时过量上下文显示、一次metadata tuple误显示/TypeError、一次大JSON探查无捕获输出及一个错误旧文档名均已只读纠正并记read-notes；原件未改，没有安全/Daybreak事件或目标重试。

状态仍为`targetCPU=0`、`nativeParserExecuted=false`、`actualCursorObserved=false`、`naturalBackendProven=false`、`naturalModeObserved=false`、`actualIndexFilled=false`、`actualRowSpanApproved=false`、`typedObjectSuccessProven=false`、`completeProudReference=false`、`resourceReady=false`、`completeAvatar=false`、`d02Complete=false`。客户端新增验收0；无新PE/code/AOT/pdata/xdata/literal采集、CPU/native/emulator/live/REA、进程、网络、Git、构建、DB或部署。仅本文与指定私有目录写入，无需重启；本max交回单source建议、双fresh和全部写权，root统一第三审核、Project/Harness、提交及交接，不等待A或现场。
