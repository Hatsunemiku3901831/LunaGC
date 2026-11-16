# D02-M0-SCENEAVATAR-DIRECT-USES：已有源的非null参数用途

## 中文有限规格

2026-11-16。上项 [SCENEAVATAR_WIRE](CLIENT_72_D02_M0_SCENEAVATAR_WIRE_20261116.md) /16718B、SHA `aefcfa3e4e769b5ccdcc163647f62444a08b19977914b280ed8e63ad80eac196` 保持不改，由唯一集成者收录。本项只读已固定允许的 `local/migration72/analysis/avatar-init-plan-inputs.json`（1394588B/SHA `d52a3fcb2948b27dbfd07de6da9207946dd6cd9ae37ad5b5756a4b6f09dcf24b`）中 `15184e100..15184e6c8` 的完整既存static源1480B/339条，byte SHA `683420b391fcd137642057620895f33f576e76c241cce94c91f1f1fd6dff163a`。

只分类入口R8/type41868的直接字段load与参数转交，并与旧F1/f4d1允许setter/成员合同对照；每条区分数据依赖与完整执行的控制流前置。RCX/type18163仅为符号this，不创建、补全或假设完整构造默认布局；RDX/type48178的身份不移植到R8。未知call只记录参数，不跟新callee、不执行或替换返回；准确冻结callee/必须用准确A返回才能成立的业务用途停该条。

无新PE、body、helper、cctor、global、客户端或CPU读取/执行，不扩相邻函数或调用树，不改g2/原准确A限制。原件如需只新增到忽略的 `local/migration72/d02-m0-sceneavatar-wire/direct-uses-run1/`；本文为本轮唯一tracked输出。只读既有完整static不受“旧owned仅验R8=null”的泛禁，但也不把新static边解释为完整handler通过。

## 固定旧合同与别名

- F-03d3f1摘要 `avatar-contract-observations.json` SHA `a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3`：身份片段只执行 `15184e100→15184e1e4`，R8=0；已准48178 AvatarID编码/具名资源查询及GUID模型来源。
- F-03d3f4d1摘要 `avatar-initializer-observations.json` SHA `1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf`：有限连续前缀与省略片段；151845140只验null Scene输入。没有非null完整Scene输入消费或完整g2许可。
- F-03d3f4d2摘要 `avatar-deep-observations.json` SHA `fc08a41533290f671693ac1a0a0ec3608ac56c1cd93cc039f2c681f8102a44c7` 给出同1480B边界/byte hash；本项不重取该source。
- 本轮31槽wire来源仍是前项的 `static-bindings-run1/result.private.json` SHA `fff68812511578d774332502c6c14974caccef6cfe93f3116c23cebe8741213d`；因此41868+50已确 **tag20/wire2、class17469 AGAKCEEMEAM**。这里不因名字或shape给17469业务角色。

入口 `15184e115 MOV R14,R8` 保存Scene41868；R14在本函数内没有重定义，直到各epilogue恢复caller旧R14。原RDX/48178保存在RBX，RCX/this保存在RDI。完整339条中Scene别名的直接内存load只有 `15184e4d7 [R14+50]`，没有任何13scalar的直接读取，也没有把Scene-derived值直接store到模型成员。

特别是 `15184e472 MOV R8,[RBX+50]` 已把volatile R8改成 **48178的子对象**；后续 `[R8+18/+20/+28/+38]` 不是原Scene41868字段。不能因寄存器名称重用给Scene数值字段补角色。

## 四条独立参数/字段流

| 分类 | R8来源→tag/type→准确指令 | 已有callee/成员合同 | 是否真正使用准确A派生值 |
| --- | --- | --- | --- |
| DU1：整个Scene参数 | 在BPL!=0、R14非null分支，`e2c4 TEST R14`、`e2c9 RCX=RDI`、`e2cc RDX=R14`，`e2cf CALL15184e8e0` | 旧source call metadata为owner18163/m138247 `GMFIKLLLBGP`、单参；当前已审合同没有其非nullScene字段/效果定义。只闭(this,41868*)转交 | **此参数流不需要A返回。** R14来自入口，不来自allocator/未知call返回。真实callee的副作用未知，不推定其内部A来源或成功 |
| DU2：唯一直接field | `e4d2 TEST R14`、nonnull后 `e4d7 RDX=[R14+50]`，`e4db RCX=RDI`，`e4de CALL151850020`。字段为tag20/w2/class17469 | owner18163/m138256 `ENMDCKLEDEL`、单参。未找到已有具名setter/目标成员或null清旧/保留合同；不把方法名当角色 | **load与转交不需要A返回。** 数据是入口对象+50；没有读取pool返回、this+A8或allocator对象。若要证明业务效果，缺callee合同，当前未知而非已证明A冻结 |
| DU3：普通尾转交 | `e566 R9=R14`，随后epilogue恢复R14，`e57e JMP151845140`；R9仍是原Scene指针 | owner18163/m138148 `JOBMLLOEEBI`、三参；已准仅null Scene正常返回。本处RCX=this、EDX取模型+2d0、R8D为已审decoder14b1049f0读取模型+250的结果；模型成员不从Scene字段得来 | **Scene参数流不需要A返回。** 其余参数只按符号this成员和旧decoder合同记录，不补建this。非null tail职责/效果未准，不能推广null样本 |
| DU4：IFix参数转交 | 入口patch分支中 `e5a1 RDX=RDI`、`e5a4 R8=RBX`、`e5a7 R9=R14`，`e5bf JMP14d936f00` | owner33459/m265452 `ILFixDynamicMethodWrapper.__Gen_Wrap_80`；receiver来自源中的global lookup，未读取现场。只闭wrapper收到(this,48178*,41868*) | **Scene实参值不需要A返回。** wrapper真实patch实例/职责未知；不执行wrapper或读global，不把未知来源写成准确A |

表中PC省略共同前缀 `15184`，如e4d7即15184e4d7。DU1/DU2的返回值不用于上述Scene参数值流；DU3/DU4是尾跳，返回行为当然未因此通过。所有项目都是条件成立时的静态边，不声称实际到达、必经或完整返回。

## DU2的pool前置与准确限制分层

该body在 `15184e3fb` 引用61980 class、`e402 CALL14d4480c0`，`e407` 保存返回到RSI、`e40a` 写this+A8，后续 `e426` 对该返回对象+24写0。这一段是pool/对象返回路径；本项不执行、重建或取其返回，也不据其结果证明完整handler状态。

DU2在指令顺序上位于这段之后，但其数据链是 **入口R8→R14→[R14+50]→RDX**。它不经RAX/RSI/this+A8/61980+24；因此读取+50并转交的证明不需要那个返回对象。完整函数若要连续执行至该处，仍须先满足真实pool/this/前序calls合同；这是完整执行前置，不能反过来把独立字段流也列为A返回恢复。

本1480B没有1405336c0的直接CALL。完整18163 `.ctor15183fe60`、第二allocator准确A及其构造/后缀限制保持；本项没有使用这些返回。也没有证据把15184e8e0、151850020、151845140的所有可能行为统称准确A依赖：本轮仅缺相应 **非null字段消费/目标成员/效果合同**，不跟新callee或猜其内部实现。

## 首发最小性：null参数与实体信息必须分开

主线补充要求只用本1480B及SceneTeam已验worker判断必要性。复用worker m467717的固定原件file SHA `c7b34af37ecece88ad6066358e6c5e663ab950e088181304fb0df33163c379ff`、code SHA `18a0a3c055248bfd4c6bd4fd393ffbe67bee33d5b67c94abd5a94560b2ac2753`；没有取新body/callee。其条件事实由该专题执行者再次交叉核定：

| 当前source条件 | 明确行为 | 对最小集合的意义 |
| --- | --- | --- |
| worker的76429+70等于network+180对象+168 | `1507c4633` 比较后，4638/4649执行对应赋值，`4650 JMP469e`，完全跳过本处15184e100 | 这个分支不消费本处SceneAvatar。+70的具名服务器来源另属SceneTeam合同，不能为了选分支猜owner值 |
| 不等，且76429+18实体信息为null | `1507c465f` 取+18，`4666→47aa CALL1404c2be0` | 此分支必须有实体信息对象；不是Scene参数null fallback |
| 不等，实体信息非null，但oneof case+90!=10 | `1507c4677→4693 XOR R8D,R8D`，`4699 CALL15184e100` | 本处明确允许额外SceneAvatar参数为null，无需31个Scene字段齐全 |
| case10但oneof对象本身null | `1507c4680→4693`，同样传null | 与实体信息缺失有不同路径，不能合并 |
| case10、对象非null但不是精确41868 class | `1507c468e→47be/47c4` 类型错误入口 | 若选择非null分支必须是真实目标类型；不填同shape外壳 |

本1480B的null行为也明确：

- 48178参数本身null时，普通分支在 `15184e12b/12e→e251..e266` 直接返回。这里没有“强制非nullAvatarInfo”的异常合同，但也没有初始化身份/装备的证据；原M0实际AvatarData身份与装备数据来源不因早返回而删去。
- 48178非null、BPL!=0时，R14=null跳过DU1的15184e8e0，转 `e2d6/e2da/e2e4`：把 **48178+18及+88** 交既有15184ec00。BPL=0时该阶段两条分支都跳过。null Scene并不等于跳过装备路径，非空原装备仍须同Avatar/Store来源，不能为了避后缀改成空装备。
- R14=null时，`e4d2/e4d5` 跳过DU2的+50读取及151850020。只有 **外层Scene非null且实际到达该块** 才读tag20；这里没有对+50内层指针的非null要求/检查，其null效果在callee中未知。
- 普通tail仍把null R14送R9→151845140；旧f4d1只验证过这种null Scene返回。省略Scene不会消除此前pool、状态、装备或其它完整后缀；没有将它包装成完整g2通过。

就这两份source而言，31个Scene raw字段、13scalar业务getter、tag20非空对象均 **未证为首发必需**；它们是选择非null补充输入后才可能需要的内容，不因为raw表齐全就追完整业务树。可以在限定SceneTeam初始化路径后移这些未证必要的补充，保留已验AvatarInfo/Store实际来源，并遵守上述实体信息存在性及type分支。

这不批准把另一个SceneEntityAppear入口也省成没有Avatar oneof；那个消费者、root列表、字段身份仍各有准确合同/缺口。当前已证会使本worker分支失败的是“非匹配owner分支缺实体信息”和“非null oneof类型不对”，不是tag20未发或Scene scalar未填。实际首发其余协议/会话/资源/场景依赖由对应任务验收；本项不把完整g2或准确A作为所有服务器最小实现的统一前置。

## 可编码结论与准确缺边

本项新增闭合的事实是 **41868 tag20/class17469在非nullScene分支送入151850020**，以及另三处整个Scene参数传递；这些可作后续来源索引和跨项wire约束。它们不是新的具名业务getter投影：不能把tag20命名成载具/外观等角色，不能用0/空对象填未知效果。

对前项13scalar，本1480B没有直接读取，因此没有新增可编码语义；它们仍可独立raw Proto/字面wire回归。48178身份/生命等已准投影保持，但不搬成41868的同名/同号字段。

| 精确缺边 | 当前已验输入 | 最小尚缺合同 / 停止点 |
| --- | --- | --- |
| 整个Scene的早期消费 | DU1/m138247、call15184e2cf、原Scene指针 | 该单参callee对41868字段的实际独立读取/已知成员。未知effects，不以其名称或旁邻disasm补齐；未跟source |
| tag20的角色/空值效果 | DU2/m138256、call15184e4de、field+50/type17469 | 它对17469的具体输入用途/目标成员，及null输入清旧/保留语义。当前不是已证准确A依赖；未跟source |
| 非nullScene尾部职责 | DU3/m138148、jump15184e57e、已有null回归 | 非null分支的独立合同；旧null成功不覆盖。不给完整构造/第二返回，也不重跑g2 |
| patch变体 | DU4/m265452、jump15184e5bf | 真实receiver/patch职责，当前范围不取global、wrapper或返回；不作为首发默认来源 |

因此 **没有新增具名Scene业务role，但也不能把四条都判为准确A阻塞**。当前完成的直接数据流可在完全移除准确A/未知返回后由既有source独立验证；进一步业务含义需要上述准确单边输入，是否必要及是否已有允许source由主线另判。本项不扩窗口、tree或全g2，不发送不完整SceneEntity/SceneTeam，也不标entry-ready。原31槽raw、SceneTeam/SceneInfo/旧号生命周期的独立工作继续有效。

## 实际核对与交付

只做固定文件hash、函数边界/339条内容一致性、Scene别名完整枚举、唯一字段load及四处调用实参核对，并只读对照F1/f4d1旧摘要/已审工具中的允许片段。原件 `direct-uses-run1/receipt.private.json` 保存每条源指令、call metadata、wire关联、A数据依赖分类及旧合同hash；没有新body、CPU、模型、hook、构造或运行状态修改。本文和receipt交唯一集成者，本执行者不做Git提交。
