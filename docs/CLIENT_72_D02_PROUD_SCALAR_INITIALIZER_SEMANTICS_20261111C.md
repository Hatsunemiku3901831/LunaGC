# E-01d1 / R3-PROUD-SCALAR-INITIALIZER-FAMILY-SEMANTICS

2026-11-11C，父项D02-R3。**131985普通支路的table发布store已准确绑定：它将seed的地址传给`14146af10`，该call正常返回后，把同一RAX写入owner17408.field82448，即`[QWORD(14586b600)+e970]`。本体没有实际写seed，也没有证明返回table的构造、互逆或自然epoch。** 下一准确源仅需`14146af10`一个完整原窗，不继续九函数或整条初始化树。

本页消费[九候选source](CLIENT_72_D02_PROUD_SCALAR_INITIALIZER_FAMILY_WINDOW_20261111C.md)、[准确owner](CLIENT_72_D02_PROUD_SCALAR_INITIALIZER_SOURCE_20261111C.md)和[条件scalar合同](CLIENT_72_D02_PROUD_SCALAR_SETTER_SEMANTICS_20261111C.md)。主线已通知接收source独立验收：第三40件同双fresh，9 tests、28 pins、9 AOT及真实pdata、803B/180条、累计门和冻结边均核。本语义任务只读固定fresh1/fresh2，不重取源。实际forward、seed写入、自然初始化、reader/QueryWrapper及41105行继续未知，不能扩成Java可加载Proud资源。

## 固定原件与验收范围

私有source根为`local/migration72/d02-resume-20261111c/proud-scalar-initializer-source/family-source-window/`。本轮核fresh1/fresh2完整40件文件名与逐byte相同，九body803B/180条、逐体连续原hex恰拼回body。`result.private.json`为34245B，SHA `704a9d935d68f636391a333a101d5054895974bf081421dfd8b0c86f1861bb84`；pre-body15339B，SHA `458975f6468210c899b8faeb08b6d1e464afc3937cd1f4da85169c8ab80bfff6`。

131985原体`[149a752b0,149a75328)`，120B/25条，SHA `4ec401a32ee45eaf65ea28e07a5ef5550d2e8b05901f3fb27a645f7223d5fc02`；asm7150B，SHA `4e987f3d69aa0289198c5394a9f88a8bbc6447c6063ab22342e3dbdc783b7cb4`。两份来源的完整AOT/pdata/body已由source任务限定验收，本页只读复用，不新读PE、callee或global。

## 131985逐路径值流

| 原PC | 可证静态含义 |
| --- | --- |
| `149a752b4/2bb` | 检查byte开关，0进入下列普通支路，非0跳`2ff`替换支路。自然开关值未观察。 |
| `2bd` | `LEA RCX,[145872d30]`：传的是seed DWORD地址，不读seed值，也不写seed。原7B指令的RIP位移独立核算到该地址。 |
| `2c4` | 原`e8475c9ff7`直接CALL`14146af10`，此时RCX=&seed。该callee未知，不据调用成功假设其写seed或建立表。 |
| `2c9` | 原`488b0d3063dffb`从固定root cell14586b600取当前QWORD到RCX；独立RIP位移核算吻合。没有改写callee返回的RAX。 |
| `2d0` | 原`48898170e90000`写`QWORD[RCX+e970]=RAX`。结合前一条root load和准确metadata，可将原“未绑定base写候选”缩为**准确table槽发布store**。 |
| `2d7..2f0` | 按原顺序CALL152e913c0、14b104fe0、14a1b8610、148304e50、153b79dc0、14d4e2e30。本轮只保留顺序，不解释其它codec或callee。 |
| `2f6/2fa` | 恢复栈后尾跳14cb592b0。本体没有普通RET；初始化方法整体完成依赖该尾调用，不能以store已成立称全初始化返回。 |
| `2ff..319` | 非零开关分支从root取+232a0，再取+101ca8对象；非null恢复栈并在31d尾跳14d9638a0。该支路没有本地table store，替换helper行为未审。 |
| `317/322/327` | 上述替换对象为null则CALL1404c2be0，后INT3；错误helper正文未读，不模拟成功或给具体异常类型。 |

因此可写出的精确合同是：

```text
仅当开关为0、CALL14146af10正常返回且root可写：
    r = 该CALL的原RAX
    S = QWORD[14586b600]       // 在CALL之后重新读取
    QWORD[S+e970] = r
```

RAX没有null检查、类型检查或长度检查；能证明它写进byte[]声明的槽，不能证明该值确为合法非null byte[]。root本身也没有本地null门。实际getter仍需原非null/长度门及有效存储条件。

本普通支路没有先读旧table判空，没有本地“初始化一次”标记store；若它再次被正常调用，将再次执行call并覆盖槽。这里只证明**再次调用时的覆盖行为**，未证明调用次数、自然更新触发、seed/table之间的存续期或跨调用一致性。发布发生在后六个CALL及尾跳之前，不能拿此局部store批准整个方法成功。

## 其他八窗与拒绝边分开

七个普通wrapper都在各自开关为0时，从同root的+bee0取对象；非null设置EDX selector，再恢复栈并尾跳同一`15010c580`：

| method | selector |
| --- | ---: |
| 131987 | 0 |
| 131993 | 1 |
| 131991 | 2 |
| 131986 | 3 |
| 131989 | 4 |
| 131980 | 5 |
| 131983 | 6 |

这些原窗自身没有table/seed写入；但tail callee未审，不能把“本地只读+转发”升级成完整纯getter、任意返回类型或无副作用。非零开关则读root+232a0中的各自替换槽并尾跳14d9638a0；对象为空到原错误helper。自然开关、替换对象和tail结果均未知，本轮不为它们另开窗。

131984保留`149a750ed→149a75140`及`dependentSemanticsFrozen=true`。不跨该返回分析、不解释其后虚调、不执行TLS/Random模型；149a75140已有`149a751f3→1405336c0`准确拒绝依赖继续停止。删除这些动作/产物及B组合loader后，131985本地store证明、七wrapper原值流和下一直接source请求仍可独立完成。没有将未知helper泛封为A依赖；如后继实际出现准确拒绝边，停该边及依赖，不借table研究续接。

## 唯一直接callee：已有listing与仍缺的raw

本轮定向库存找到`local/migration72/analysis/disasm-14146ae70.txt`，2166B，SHA `890c9fa0584463d91ef6eb87892d252537b026ad41a4be66f8ca4d49a595848f`。其中保存了`14146af10..14146af84 RET`的完整文本listing，但没有每条hex、唯一pdata、完整code SHA或原body。旧`disasm-14146ada0.txt`仅重复其开头，不是另一份完整机器码来源。相关resource native静态报告中也没有本helper原件。

该listing可作为准确后继的范围/问题提示：它把输入RCX移R8，EDX=800，RCX=&14574d560，随后间接CALL`[145744ec8]`；按EAX==0/非0分支选择800或0参数，再有返回对象和复制步骤。**这些是尚未核完整原码的文本线索**，不是本轮已经证明的seed写入、表内容、allocator结果或业务成功/失败。145744ec8的实际target与14574d560内容未读；也没有用旧Ec2b注册片段或相邻地址猜该callback。

固定metadata对`14146af10`的ordinary registeredAddress匹配为0。它是准确E8目标，不能虚构owner/methodIndex/AOT槽；下一入窗依据用**固定caller原E8＋当前唯一pdata**。当前pdata/end仍未知；文本末RET可提示117B，但不能代替完整边界。

这一个原窗直接服务D02-R3：只有它能先核清seed地址怎样转交、返回table的数据/长度/错误分支来源，才可再判断原setter的前四quarter与getter后四quarter是否同源。当前已经不需要九窗重取、其它selector tail、Proud ctor或自然map hit来回答这一首缺。该窗也可能只把问题推进至一个准确callback；不预承诺它单独证明全部互逆规则。

## 交Terra的最小source规格

任务：`E-01d1/R3-PROUD-SCALAR-TABLE-BUILDER-ONE-WINDOW`。完整冻结request在`local/migration72/d02-resume-20261111c/proud-scalar-initializer-semantics/next-window-request.private.json`。

| 项目 | 唯一限定 |
| --- | --- |
| target | entry14146af10，只取这个完整函数。 |
| caller | 固定131985完整120B及source SHA；准确CALL149a752c4、5B=`e8475c9ff7`。原`[149a752bd,149a752d7)`26B完整覆盖seed LEA、CALL、root load、table store。 |
| 当前边界 | fresh五完整stock核验后，核原caller site/setup；PE exception exact与containing各唯一且同一条，begin必须14146af10，完整范围在可执行段。先保存原12B及诊断/pre-body。 |
| budget | 1 body≤256B/128连续指令；caller site5B与setup26B重叠，distinct caller26B；pdata12B；distinct bounded evidence≤294B。ordinary AOT0、xdata0、callee0、邻窗0、global内容0、CPU/live0。 |
| 单列开销 | exception目录14344080B一次索引、五完整stock的550661784B hash及来源pin实际大小，不能描述成整轮只读294B。 |
| 必须保持 | 旧文本末RET/下一注册地址不作end；无唯一边界或超限时保留诊断、bodyRead=0，不截短或自动扩大。 |
| 输出/权限 | 只在新`table-builder-window/`子目录写collector、独立测试、双fresh、主线第三及审计；已有工具/专题/共享索引/源码/资源/Git index只读。 |

按原caller displacement、完整连续bytes、pdata缺失/重复/重叠/边界/预算拒绝和source-only引用索引作独立测试；双fresh及主线第三原件一致。不能读取145744ec8/14574d560或跟随1414d5b30、1404bb0a0、141fdf310等call，不运行任何旧runtime/model。遇准确A/B拒绝依赖或新安全拒绝，保留原site/原提示并立即停止对应动作及依赖，不换方式重试。

这个source成熟包只取得下一原窗。此时不交两字段codec扩展测试：返回表的forward/inverse及实际seed写尚未成立，不能把历史自然inverse的数学反表改称实际forward。即使后继证明算法，实际自然epoch、reader、QueryWrapper寿命、41105、addProps/paramList/OpenConfig和当前采用仍分别待证；不补空配置。

## 本轮实际验证与交付

新私有`audit-family-semantics.private.py`复核40件双fresh、803B/180条的重组、12个publication坐标、七个独立字面selector、准确seed/root/CALL位移及metadata无ordinary target，最终exit0。初版读取旧schema字段`ripTargets`触发KeyError，尚未产出报告；原脚本和失败记录保留，改按本族`memory`结构并独立核RIP位移后通过，旧原件未改。

- `semantics-review.private.json`：11939B，SHA `00c15ab83c8539396d14f3e6fa9111f33fe2bf1cee0195509c1c07848513d08c`。
- `next-window-request.private.json`：8457B，SHA `a73aa4be02ede49fb25a29625d37594a92226747e5565845b4b03be4b9ecac59`。
- 复现：`python local/migration72/d02-resume-20261111c/proud-scalar-initializer-semantics/audit-family-semantics.private.py`。

本轮只写本新专题与独立私有根。新PE/callee/global读取、runtime/model导入、CPU/live、Git/index、构建/Java/DB/客户端/调试器/部署均0。没有新增客户端验收或ready/父项晋级。下一可执行动作是主线审查并交Terra取这一个source窗，max交回后释放。
