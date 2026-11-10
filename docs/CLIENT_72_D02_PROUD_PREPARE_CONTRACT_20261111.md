# E-01/R3-PROUD-PREPARE-CONTRACT（2026-11-11）

## 本轮中文有界计划

只解释已接受的 owner18192/method138529 `IFIKEDMJMLC`、classInstance5071/methodInstance-1 的原 `[157ffc650,157ffc771)`：289B/82条。先核 D/W、原236B caller、lazy/index/cctor/literal 合同及其全部固定来源，再逐PC/bytes/operands复核普通路径、替换/usage边界、壳+20路径、+28状态和+18建立条件。metadata/signature只读既有缓存；第一准确loader/path/type边出现就给root/Terra最小机械规格，不扩callee树。

本包只写本文与私有 `local/migration72/d02-resume-20261110b/c-proud-prepare-contract/`。新PE/code/pdata/xdata/AOT slot/literal、CPU/live/REA、网络、目标进程、构建、DB、Git均0；不碰Native trace或旧B Daybreak。已有fixed/partial GitHub/README-first成果直接复用，小续项不重新检索。来源漂移或安全门禁先留诊断交root，不放宽pin或换方法；共享索引/旧专题/工具由root维护。

## 当前结论

本有界静态出口完成。**同壳+20非空路径确实被原 `157ffc680 e8eb4e6ef5` 交给已有500132/14d6e1570，参数为 `(path,0,0)`；其已验145983438/type8026和实际type-map后缀来源可以复用。** 下一机械项只需定位400190的准确 `PrimaryIndex` 完整payload，不需新loader窗或先读157ffc780。

**本 prepare 的正常返回不统一表示已装载。** 壳+28非零直接返回；壳+20为null或空字符串会写+28=1；非空路径的loader返回null却不写+28。只有下述成功分支在pool派发正常返回后才写+28=1。这个byte是本方法的跳过条件，不能据它声称resourceReady/loadAll。

**289B内没有壳+18读写，未证明实际index建立或填充。** 它将同壳和loader非null结果交给157ffc780；该方法的准确闭合注册/参数类型已有D证据，但body未读。原lookup在prepare正常返回后才读+18，并保留已验的本地Dictionary fallback；fallback不代真实index内容。

## 固定来源与实际审核

私有manifest固定185项已有实际来源，共302489408B；manifest37159B/SHA `0e33c2b26fab21d557dd596f97aa918caa4c94e606331fdf24d745b34439003b`。包括旧lazy的138项固定来源、D/W来源与八原件、已接受literal/cctor/lookup合同、原metadata/signature缓存，以及既有loader/type-map/locator来源；未把可追加公共文档当数据pin。原五输入完整身份只消费D/W已验记录，本包没有重解GAME_PATH、读取当前客户端或声称新实机采样。

| 来源 | 完整范围/结果 | SHA-256 |
| --- | --- | --- |
| prepare原code | 289B/82条，157ffc650..157ffc771 | `0c1d792245409c0df4df69beed8bd9b0a520f8a4d56d14e2fa1dd43ed2b4db7b` |
| W第三/最终audit | 各14701B，同byte | `c322aa7f0d5d397c21e35edf86422cab2794a401d0784fd68045e6e7884d81cf` |
| D第三/最终双audit | 各24456B，同byte | `0bff021d1171cde4c1248370da22718887765e472c788ecfba619aa41e5ff5e3` |
| 原lookup code | 236B，4d3/E8先调用prepare | `479e010ba8e0b7fbea184c024408f2a1ce266aa519b92445bf03279672f2fb4e` |
| 旧lazy第三/双fresh | 各12550B，同byte | `2c35406ac5f3ac296fc55e7975c882fb375e22bba2f0c9ef10c2f76641f24224` |
| 既有loader/type/hash locator双报告 | 各1452522B，同byte | `fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda` |
| 原资源index报告 | 实际types/assets/location来源 | `e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9` |

纯静态脚本重新校验185项完整SHA；两份W八原件逐byte相同并对照主线第三；只对冻结289B重新反汇编，82条原PC/bytes/operands全部与已接受asm一致、连续到准确end，另有58项关键原PC/hex/operand断言，重算每条E8和两条RIP目标。没有执行或模拟原指令，没有调用旧collector或重跑旧门禁。

两独立fresh host Python均退出0，最终报告各36303B/同SHA `9eb26c03c1ceb6f9cd9a000e7b9de688a2c9c8f2e2cf893902f8e119fbff5e27`；实际argv/UTC/exit/stdout/stderr留 `executions.private.json`。私有脚本17810B/SHA `4a26ebb1ba987bd503c78f541ff2d7fa51c92d1f9e8c3823e9286f9ab5a7b37e`。没有新增公共wrapper或重复实现型test。

```powershell
python -B local/migration72/d02-resume-20261110b/c-proud-prepare-contract/verify-contract.private.py <新basename>.json
```

## 普通路径、+20与+28状态

入口 `157ffc657 80792800` 比较同this+28的byte；`65b 0f85a1000000` 非零直接到702恢复栈并在709 RET。只有为零才在661保存RDI=this。此窗没有独立IFix dispatch/usage guard；两个RIP引用均为14586b600的同global根。不能因此断言loader、157ffc780或pool实际派发中没有替换/usage前置，不能把旧factory的flag合并为这里的条件。

`664 488b4920` 从同壳+20取RCX；null经66b到6fe。非null时 `671 83791000` 只检查String+10长度是否等于0，空串经675也到6fe。6fe的 `c6472801` 写壳+28=1再RET；这两条“无路径”分支没有调用loader、没有创建/填index。

非空路径保持RCX=该String，`67b 31d2`、`67d 4531c0` 清EDX/R8D后执行 `680 e8eb4e6ef5`→14d6e1570。metadata唯一声明是60257.method500132 `GBPNKANHNCH(String,int32,int32)`；这里两个int32实际都为0，不借其参数名猜offset/size。返回RAX为null时 `688 7478` 直接到702/RET，**本地没有+28 store**；原壳+20也没有本地store或改写。

generic18192的四字段声明为String/IEqualityComparer/Dictionary/bool；+20路径与+28 byte的本窗用法与声明兼容。这里只新增准确原偏移和值流，没有恢复全部generic runtime field offset表、自然字段值或构造成功。

## reader、157ffc780与成功门槛

非null loader结果在68a保存为RSI。它随后作为138530的唯一声明参数42005 `OFMKHIMCGKE` 使用；42005已有字段为+10 byte[]、+18 int32、+1c int32、+20 U64、+28 int32。底层请求asset type8026与这里的reader载体42005不能混成同一对象类型。

| 原条件与site | 保存值/调用 | 本地后继 |
| --- | --- | --- |
| `68d/692`：reader+20非零 | `694 8b5e28` 把reader+28 dword存EBX | 到6a5，用同壳RCX、同reader RDX调用157ffc780。 |
| reader+20为零，`699/69d/6a0`：reader+10非null | `6a2 8b5818` 保存该byte[]的+18长度dword | 同上。 |
| reader+20与reader+10均零 | 70a/70d重新传同壳/reader，`710 e86b000000`也调用157ffc780 | 不检查AL或EBX，直接715清理后尾派发；没有本地+28=1。 |
| 上面已得到EBX的路线 | `6ab e8d0000000`→157ffc780；6b0检查AL，6b4检查原已保存EBX | **AL非零且EBX非零**才到6b8成功清理；任一为零都到715。 |

这里的EBX在处理器调用前已经保存；它不是处理后的剩余长度或已填key数。`test ebx,ebx; je`只判零，不是signed正数判断；不能把条件改成`count>0`。AL是实际局部决定宽度，不据它恢复整个RAX或方法声明返回类型。

准确处理器身份直接复用D的16原槽：owner18192/method138530 `CDMPEFBPCLP`，classInstance5071/methodInstance-1，methodSpec175105/tableIndex13205/pointerIndex84548；原14B `444a010001ac0200ffff83650000`、原12B `221d0200cf130000ffffffff`，slot142515ed0原8B `80c7ff5701000000`→157ffc780。声明参数42005与两个原call的RDX同reader一致；**没有读其body，也未命名Load/EnsureLoaded或宣称它已建立index**。跨其他pointerIndex同code仍未知。

## 清理、pool派发与错误边界

成功路线6b8清XMM0，`6bb 0f114610`把reader+10..1f恰16B写零。随后6bf重新读reader+20；非零才在6c8调用原1411230d0，正常返回后6cd写reader+28 dword0、6d4写reader+20 QWORD0；若本次读出的+20为零，则跳过helper与这两次store。失败路线715/718/71c/725/72a/731具有对应的同一清理顺序。处理器可改变reader，因此不能用调用前的+20或EBX代替这些原重读。

成功清理后6dc/6e3从global14586b600→+5d60取得pool；非null时取实际klass，RCX=pool、RDX=同reader、R8D=0，`6f8 ff90f8000000`间接call `[klass+f8]`。**只有它正常返回，才在6fe写壳+28=1并RET。** pool null经6ed到76b原1404c2be0，没有本地正常false/成功替代。

失败清理后739/740取相同pool，非null时74f取 `[klass+f8]`，恢复本方法栈后 `763 48ffe0` 尾跳RAX，**没有本地壳+28 store**。pool null经74a到766原1404c2be0；末770为INT3。没有把这些错误函数或尾派发转换成普通RET/false，也没有原异常unwind样本。

既有metadata把global+5d60关联为11061.field52910 `_cluster`（raw4005d60），声明11054 `MoleMole.IObjectPoolCluster`。这支持pool职责来源；实际klass/vtable/目标未观察，**不由f8猜准确虚方法、slot映射或构造函数**。清理后提交reader的调用/尾跳不是实体ctor证据；1411230d0的本次执行、释放所有权和pool返回也都未验。

## +18的建立与第一必要type边

本289B没有壳+18读取或store，没有allocator/Dictionary显式构造或entry插入。157ffc780收到同壳，因而是下游index生产的准确候选边；但真实写入位置、创建/替换条件和输入格式只有其body或自然结果能证明。本卡只给这个边准确身份，不扩窗。旧236B正常返回后读取壳+18；若仍为空，原fallback请求Dictionary并缓存+18，仍不证明resource index已加载。

本轮已有足够来源走**纯数据定位**：

1. 旧cctor把cell145b29758的58B literal `Data/_ExcelBinOutput/ProudSkillExcelConfigDataPrimaryIndex` 写400190关联壳+20；明文SHA `058b4298fbe652d492fcc60b5c10a0214bb87e4ada40a4247a3f04c6a79f344a`。prepare确实将这个字段交给同一14d6e1570。
2. [既有loader来源](CLIENT_72_RESOURCE_LOCATOR.md)已记录14d6e158d的准确RIP：nextRIP14d6e1594减7d5e15c为145983438，usage735837→methodSpec16871→method80127/methodInstantiation58257→8026 `UnityEngine.MiHoYoBinData`。旧完整loader span `[14d6e1570,14d6e165f)`/SHA `d6851231b0cec0ca2a2bae158bebaa924ace7afddc6604906b18a93342d06c87`只核已落盘身份，没有重新取code或metadata字节。
3. 固定31049740实际types section为19661B/357项，唯一type-map配对是 `UnityEngine.MiHoYoBinData -> .MiHoYoBinData`；本卡复核固定完整index bytes及该section SHA `04b2158ee612b2ef87d0ec5f2da25c63c615668307b7c429cf40097b0ea245a4`和已接受报告。suffix由实际type来源给出，不是试拼求命中，也不从reader42005名推导。

以上是已证静态来源链；**没有执行本次loader/hash、计算新PrimaryIndex key、扫描其record或定位其payload**。路径存在、自然激活的revision/variant、actualindex和row/backend继续未知。

## 最小后继与交回

私有 `next-primary-index-locator-spec.zh.md` 6580B/SHA `0409a7e3a5a83dd7ace77f89a299bac8943f1997f2b081eafdf7591d980a2e5b` 已交root/Terra：只把已解PrimaryIndex literal交既有host reference hash/pack/serialized定位逻辑；完整扫两固定assets section最多16561287B/1182911条，保留全部命中/variant。唯一selector无关record、唯一location且确属已验25539185/bundle178时，才消费固定71555980B serialized取唯一class1208 payload；否则留partial，不换path/suffix/bundle或读新blk。每fresh120秒，两fresh一致；全程不读当前PE、不执行native或新parser，不取157ffc780窗。

下游真实index格式/内容、同key两个U32、有效row span与选中backend、factory/+20/+40、group/level/门槛alias、Java proud算式或空ability许可、121角色完整恢复、resourceReady/Avatar/D-02均未因此完成。客户端验收增量0；仅文档与离线材料，无运行变更、部署或重启。

准备期已有报告查询曾遇JSON结构键名、默认GBK与AST literal读取错误；均在已有文件读取/准备阶段终止，改用准确schema、byte JSON和接受报告的sources后完成最终两fresh。没有来源漂移、取新窗失败或安全/Daybreak事件；细节留私有preflight notes。未Git或本地提交，root统一第三审、共享Project/Harness更新和提交；本包全部写权交root，成熟机械规格已交回，不留max等待新取数。
