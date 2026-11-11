# E-01d1 / R3-PROUD-SCALAR-SETTER-SEMANTICS

2026-11-11C，父项 `D02-R3/R3-OLD-SAVE-DEPENDENCIES`。固定157B setter与已验164B getter足以恢复**正常scalar存储的条件读回合同**：四组正反向byte表满足左逆、两次seed一致、读取仍指向同一有效DWORD时，`Getter(Setter(x))=x`，覆盖全部uint32。对已有key22101两字段桥，可得条件值221／1；实际encoded、实际getter返回、QueryWrapper基址与寿命、reader状态、实际41105行仍未知。

这关闭“setter末尾怎样编码和写入、何时能由getter逆回”的静态语义缺口，不关闭自然codec、实际row、当前采用或资源ready。`threshold==phase+1`已验为下一突破UI预览，继续冻结，不作为当前采用来源。

## 固定来源与本轮操作

复用[完整setter原窗](CLIENT_72_D02_PROUD_GATE_COMPARISON_SCALAR_SETTER_WINDOW_20261111B.md)、[两表示字段桥](CLIENT_72_D02_PROUD_GATE_COMPARISON_ROW_BRIDGE_20261111B.md)及[自然资源codec合同](CLIENT_72_LIVE_RESOURCE_CONTRACT.md)。沿用既有检索、仓外成果和源码来源，不重开GitHub检索、121行/605槽投影、Talent、121 phase6或旧号快照。

| 固定输入 | 本轮核对 |
| --- | --- |
| `local/migration72/d02-resume-20261111b/proud-gate/scalar-setter-window/{fresh2,fresh3,main-third}/code.bin` | 各157B，SHA `5c7bd52bc54dd9ebfa01137bd2b56c741eb5f9bb2ad05331158a9c85aa20d3dc`；三份原件均核SHA。 |
| 同目录 `code.asm.private.json` | 各9940B，SHA `dec833d145c810912894b66eca752cd65a257b7e46ee67f3366d5ce088062a6f`；45条连续原bytes重组等于完整body。 |
| `local/migration72/analysis/scene-helper-14aef9db0.txt` | 1384B，SHA `8b5b2f341f887eef750e7ed4b062556a04148b75cce0de4b15d8022beee004f5`。有效listing到`14aef9e53`，`e54`为body外padding。 |
| `local/migration72/d02-resume-20261108/r3-mapping/main-third.json` | SHA `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e`；getter原164B SHA仍为`ef485aef75c3e3d9d8814d3fb800021f431508269e31f79431adc7915593e6a0`，本轮未重读getter raw/PE。 |
| `proud-gate/row-bridge/conditional-fields-main-third.private.json`（相对上列20261111b根） | 5123B，SHA `91f62c32da64a025ea08db4c95d16d2b470c1368a3025dd01b748adc6046f94a`；准确field/span/presence/输入与actual unknown保持。 |
| `local/migration72/d02-active/plain/initialization-resources-elevated5.json` | 36621B，SHA `c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d`；历史自然inverse/seed原件，非原子快照、非Proud行。 |
| `tools/client72_avatar_natural_tables.py` | 44027B，SHA `920395dfd4817523951d2c766167ca481d8f6b2b64288980fee2cade2ae9ecad`，与固定mapping source pin相同；仅只读复用105..134的既有表构造说明。 |

另核原435B native getter、7674B parser及其汇编；完整17个输入pin在私有`review.private.json`。本轮采用reverse-engineer-anything的原证／推导／未知分层，只读既存原件；PE读取、新callee/global内容读取、target CPU、live、构建、JVM、数据库、部署均为0。既有AGENTS、patch被拒源码与resources差异保持。没有改Git index或提交，共享索引由主线汇总。

## Setter逐路径值流

入口为`58209.m483726`，`[14aefa090,14aefa12d)`；声明参数uint32在EDX，RCX为unboxed DWORD的地址，不能当41105对象。以下`A`为从GLOBAL根`[14586b600]`的`+e970`取得的byte[]对象，`L=U32[A+18]`，`T[i]=byte[A+20+i]`，seed为`U32[145872d30]`。

| 原PC | 值流／分支 |
| --- | --- |
| `a096/a09d/a0a4/a0a7` | 取root、A并检查A。A为0转`a127`；root本身没有本地null门。 |
| `a0a9..a0b7` | `r8d=EDX`使高32位归零；右移24得`i0=(x>>24)`。读L，以无符号`i0>=L`转`a122`。 |
| `a0b9..a0cc` | `i1=100|((x>>16)&ff)`，无符号越界转`a122`。 |
| `a0ce..a0db` | `i2=200|((x>>8)&ff)`，无符号越界转`a122`。 |
| `a0dd..a0ea` | `i3=300|(x&ff)`，无符号越界转`a122`。 |
| `a0ec/a0f2/a0f8/a0fe` | 四次检查均通过后才分别读取`T[i0..i3]`，逐byte零扩展。 |
| `a104..a111` | 各byte左移24／16／8／0位后OR为一个32位值；四项位域不重叠。 |
| `a113/a119` | 与本次读取seed异或；**唯一目标DWORD写入**是`[RCX]=EDI`。 |
| `a11b..a121` | 恢复栈和非易失寄存器，原RET。没有正常路径callee。 |
| `a122` | 任一索引失败到`CALL 1404c2650`，此前未读表payload、seed，也未写目标DWORD。 |
| `a127/a12c` | A为0直接到`CALL 1404c2be0`；若前一个错误call返回也顺序到此，之后INT3。错误callee正文未读，不给具体异常类型或假定其返回值。 |

这是`x`的U32位运算，不做符号扩展。RCX可写、root/数组头/实际payload/seed可读是执行前置；代码没有把无效RCX转为受控false。只对指定字段需要四个索引小于L；要覆盖所有uint32，setter的L至少1024。逻辑长度不替代物理可访问内存。

## Getter、桥与数学关系

已验scalar getter`[14aef9db0,14aef9e54)`在`db6/dbd`取同一形式的root与A。A为0经`dc7`转`e4e→CALL 1404c2be0`。非零在`dcd/dd3`读取本次seed并与`[RCX]`的stored DWORD异或得到q。四个无符号索引依次为`400|(q>>24)`、`500|((q>>16)&ff)`、`600|((q>>8)&ff)`、`700|(q&ff)`，分别在`de7/dfb/e0a/e19`检查长度。失败到`e49→CALL 1404c2650`，还未读表payload；seed和stored此时已经读过。通过后在`e1b/e21/e27/e2d`取四byte，`e33..e40`按高至低组合EAX，`e48`RET。getter没有目标存储写入。覆盖所有q要求L至少2048。

设`pack`按高至低byte组一个U32，`x_j`为x的四个byte，j从0至3。setter第一次观察的前四quarter记为`Fs_j[b]=Ts[j*100+b]`，getter第二次观察的后四quarter记为`Gg_j[b]=Tg[400+j*100+b]`（索引常量为十六进制），两次seed为ks、kg：

```text
E = pack(Fs_0[x_0], Fs_1[x_1], Fs_2[x_2], Fs_3[x_3]) XOR ks
y_j = Gg_j[ Fs_j[x_j] XOR byte_j(ks XOR kg) ]
```

**充分条件**为`ks==kg`，并且每个实际访问byte满足`Gg_j[Fs_j[x_j]]==x_j`，同时全部原长度/内存门通过，getter所读DWORD仍为E。于是`y==x`。要作全uint32结论，对每quarter的全部256个b满足左逆即可；有限256元素集上的全左逆亦推出对应排列互逆。仅header长度2048、两次table指针相同、两次seed相同，均不单独证明表互逆或内容未变。表指针相同不是数学必要条件；关键是各次实际读取内容满足公式。

现有桥的两个文件token都是present：

| field／槽 | 从文件到setter及复制 | 条件结论 |
| --- | --- | --- |
| 41105.field203376/+90 ↔ nativeData+30 | span[218,222)，LE32`f6fdb52d XOR f6fdb5f0=221`；`d152`清local、`d15d`调setter、正常返回后`d169`复制local到+30。presence为P&4000000。 | 上述条件成立且该复制结果后续可读时，scalar getter为221。 |
| 41105.field203373/+84 ↔ nativeData+38 | span[226,230)，U32(`43457c7c+bcba8385`)=1；`d4a3`清local、`d4ae`调setter、正常返回后`d4ba`复制到+38。presence为rawH&800。 | 同条件下scalar getter为1。 |

清local发生在call前，不能当setter结果；编码0通常也不是存储全零。缺presence的其他路径不由这两个present样本推成0。

producer对同wrapper的管理分支分别读41105+90/+84，packed分支取去低8位handle并用offset30/38调435B native getter，两分支都把encoded DWORD交scalar getter。非零且非负handle路线在`148ba97ee`调`146e031f0`，`97f6`取out0、`9800`按带符号扩展offset读DWORD；parser在`149f0c32b`也调该函数，以out0作nativeData再复制。相同callee/实参形式与偏移提供用途对应，**没有观察两个out0相同或其存续期**。native getter还有零handle的静态DWORD分支、负handle的独立地址分支，以及class-init和owner清理门；本合同不把它们改写成正handle成功路径。managed表示也仍需真实41105构造来源，不批准将nativeData强转为它。

## 历史自然codec能证明什么及首个剩余输入

固定自然样本只读取A+420起的四段inverse（共1024B）和seed，没有读取A+20起的前1024B。inverse SHA为`8717ded77f717cf44988dee2720b99bbd60c751426b130f6166a2ffc379a57f6`；四段均为0..255排列。seed原LE bytes为`2e000a3b`，U32为`3b0a002e`。既有NaturalRuntime以这些inverse**数学求逆**得到forward，SHA`2eed64897eabc0ebc89c970de74665c05325c4b55c2ab259ef685f49b44c7931`，合成2048B fixture SHA`2c01c4289cd5c8e363f8b9f7d02446ea2bbad008ecc5f752d91e3c8769cfc76a`。

该fixture下key22101两个字段的编码数学期望为`403d555f`（输入221）和`403d55c4`（输入1），getter期望221／1。它们只能写在`fixtureEncoded`／`fixtureGetter`，`actualEncoded=null`、`actualGetterU32=null`继续保留。历史inverse/seed是真实读数，forward是重建输入，整个fixture没有证明自然Proud行用过它。

从条件合同走到真实encoded，首缺是**实际setter forward内容及其与getter inverse/seed的同源关系**。对这两个特定x，前半表访问索引只有0、100、200、3dd、301这五项；getter还依赖对应后半表项、读写时seed关系和实际存储。五项是数学依赖集合，不是本轮新增live采样规格，也不单独解除整个row。当前允许Proud现场仍仅原7读50B，未扩大或启动。

静态另一路可以是准确初始化writer证明前后quarter和seed的建立/变更规则。但当前setter、getter和已固定mapping/NaturalRuntime只提供读取与人工装入fixture来源；已检查的相关文档/工具没有给出该writer的唯一来源。setter正常路径没有可追的初始化CALL。不能按58209名称猜`.cctor`，也不能交出虚构的CALL/AOT/pdata窗口。准确现有data边止于`a09d/dbd→[root+e970]`与`a113/dcd→[145872d30]`；需要后续另定来源定位任务，本轮不追PE、全局或新callee。尚不能声称必须再启动自然客户端；可验证静态writer若取得同样能解除codec关系，但现成输入尚不足。

即使codec关系闭合，实际row仍各自需要：正确reader/backend/cursor和原presence路径；准确class/allocator正常返回；this+8的自然handle；QueryWrapper out0、owner处理与后续读取的存续；实际41105或packed表示选择；其余必要row/config字段。`addProps`、`paramList`、OpenConfig没有随此恢复，不能补空让`ProudSkillData.onLoad()`通过。无自然热Equals输入仍不能声称map hit或当前采用。

## 交Terra的最小可执行包

任务为`E-01d1/R3-PROUD-CONDITIONAL-SCALAR-ROW-PROJECTION`，仅实现key22101两个字段与上述**既存codec fixture**的离线条件投影。准确字段/presence/raw跨度复用冻结桥，不重解析完整row，不生成Java可加载资源。标准库私有driver对固定原件做一次入口pin校验，然后输出两个字段的`setterInputU32`、`conditionalDecodedU32`、fixture编码／读回、原PC和来源；actual三个值保持null/null/false，并保留QueryWrapper/reader/row未知。

可写范围仅`local/migration72/d02-resume-20261111c/proud-setter-semantics/conditional-row-projection/`中新建driver、独立测试、新basename输出和日志；本专题、已有工具、其他源码/配置/资源/索引只读。不得导入会运行目标或初始化全局的runtime，不执行原CPU/模拟器/JVM/客户端/DB，不读新PE/global，不写Git index/提交。完整固定pin、argv、schema和停止条件在同根`terra-request.private.json`。

必要测试及独立oracle：

1. 独立字面表核两项fieldIndex、managed/native偏移、span、presence、输入221／1及最终actual未知；缺/错来源、field、token、presence应在入口拒绝，不填默认。
2. 使用已固定`fixture-oracle.private.json`中的字面期望核两真实输入，另以0、单byte跨位的100/10000/1000000、12345678、80000000、80000001、ffffffff检查U32和byte顺序。fixture的0编码为403d5515，不能把stored 0当逻辑0。
3. 表或seed不满足逆关系的反例必须读回不相等，并标合同条件未满足；不以原输入回填getter。至少改变221的forward实际使用项，以及使setter/getter seed差1两个反例。
4. 原无符号索引门用独立字面期望核：setter x=0的索引0/100/200/300，L为0/100/200/300分别止于b7/cc/db/ea；L=301正常。getter以stored=seed令q=0，L为400/500/600/700分别止于de7/dfb/e0a/e19；L=701正常。setter x=ffffffff在L=3ff失败、400通过；getter q=ffffffff在L=7ff失败、800通过。A=null分别预期停setter a127／getter e4e；保留原错误CALL边，不模拟异常成功。这些是静态路径模型测试，不计原生执行。
5. 两个fresh新进程和主线第三输出逐byte相同。主线直接用原桥的独立字面字段表及本分析的固定oracle复核；不以driver自己的输出生成expected。

任一pin漂移、来源矛盾、需新callee/global/现场、涉及A第二返回/同epoch归属或B组合loader、需补未审row字段即停止相应依赖并交回；未知语义不自行设计。安全拒绝立即保留原提示与停止点，不重试。本包删去A/B动作和产物仍能独立完成，也不实现它们。

## 本轮实际验收与交付

仅执行新私有`audit-semantics.private.py`：17固定来源、setter22锚点／getter13锚点、原指令重组、桥两字段与两wire变换核对通过；数学fixture验证10个U32向量和1024个quarter左逆关系，exit0。不是目标CPU、真实行、自然parser或新客户端验收。

- `review.private.json`：11502B，SHA `b440b24c5729992e569a3c62d25ccd5b7f7c798bd8ac39ab0a95663e63ea09ed`。
- `fixture-oracle.private.json`：4795B，SHA `d75e0c38ed5d4fdf7a907268582ce327629086a9012ca33d0927babda1a8923e`。
- 私有根为`local/migration72/d02-resume-20261111c/proud-setter-semantics/`；可复现命令：`python local/migration72/d02-resume-20261111c/proud-setter-semantics/audit-semantics.private.py`。

Terra投影/独立测试尚未在本分析中实施，下一项即上述成熟包。初始化writer、真实codec、QueryWrapper寿命与完整row继续列缺口；不晋级resourceReady、D02-R3或D-02。未构建、未部署、未本地Git提交；无新增实机范围。
