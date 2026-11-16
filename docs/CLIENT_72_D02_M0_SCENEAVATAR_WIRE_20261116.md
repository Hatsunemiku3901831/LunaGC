# D02-M0-SCENEAVATAR-WIRE：所选Avatar单reader

## 有限中文规格

2026-11-16。主线明确继续独立必要source：仅type41868 `FKDEDKIHNDA` 的reader m349487 `MOMMACBEMEK@1504b30c0`，单参p306429/type52310 `GEPIHFBDOKD`。前版 [SCOPE](CLIENT_72_D02_M0_SCOPE_20261116.md) SHA `0444aeac95a14625963ac7fd2d01bbcc5f7c46e533ba7d3703bfc1efcb343107` 保持稳定给集成者；本专题是本轮唯一可写tracked文件。原件只新增于忽略的 `local/migration72/d02-m0-sceneavatar-wire/`，新run不可覆盖。

固定三输入：

| 输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

复用固定metadata cache SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`、signatures SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`；先核owner/参数/ordinary AOT槽，再由固定PE exception目录取唯一完整body，最多16384B。gate通过立即取该一body，全文连续strict Capstone decode并字节回拼；不skip-data/裁尾、不另取helper/cctor/writer/global、不运行客户端或owned/native CPU。源漂移、边界非唯一、strict失败或新拒绝保存原错误停依赖；准确A返回及依赖构造/store、preview/B保持停止。

目标只恢复当前单Avatar所需的独立scalar/string/u64，以及已有nested/list外层tag/wire/变换与准确类型；通用primitive helper仅复用既有已审合同，未知helper保留准确输入，不新添默认模型。真实Cmd→type、type→消费、field→wire分层记录，类型同形/上游旧Proto不能补业务名。成熟raw字段即时交集成者，Proto/Java/独立断言/生成构建由其串行完成；本轮不发不完整包或标ready。

## 先交叉消费来源：纠正+38所属对象

另一Sol已读的共享worker owner56238/m467717 `CJFBCOEMHJM@1507c42c0`，完整5743B/code SHA `18a0a3c055248bfd4c6bd4fd393ffbe67bee33d5b67c94abd5a94560b2ac2753`，原件 `local/migration72/d02-m0-sceneteam-locate/shared-worker-run1/467717-source.private.json`（file SHA `c7b34af37ecece88ad6066358e6c5e663ab950e088181304fb0df33163c379ff`）：`1507c465f` 取76429+18→16796，检查16796+90==10，`1507c4679` 取 **16796+38→41868指针** 并校验class1458cda58，再于 `1507c4699 CALL15184e100(18163*,48178*,41868*)`。本轮只读复用该结论，不重做worker或callee。

这不是41868自身+38的角色ID证明。固定metadata中41868+38为class27196 `LIIKPPLEEOJ`，非u32；禁止把两层+38混用。另一独立对象来自76429+20，helper m467381 `1507b25d0` 在 `1507b286a` 读其+C4/u32用于字典关联AvatarGUID；该字段业务名尚未知，也不能混成上述oneof实例。当前41868共31个instance存储槽：12个u32、1个u64、18个class/容器，没有直接string槽；若reader中没有字符串，不为了任务措辞补字段。

## 实际结果

已实际完成同三输入、metadata/signatures、ordinary AOT、唯一pdata与完整取源。唯一边界 `[1504b30c0,1504b3e26)` /3430B，recordRVA `1ab63bac` / 原12B `c0304b10263e4b1068c27004`；完整679条连续strict指令、EOF及逐字节回拼相同。body SHA `b743a2727f14b5eb72b268f665708744bb9537b6f7b8c1e93f01747c98e657e0`，完整ASM SHA `cc78536bde1e7289f80cfa7d2f9d1d90a1cb17c6d0dcdd0dc7fd216303d15631`。

这是普通reader，不是保护跳板；没有重做原root317B/167B失败或OnAccept。四个fresh nested分配路径在 `1504b38b4/3901/3951/39a1` 有准确A CALL，保持返回、构造/store依赖停止；下面只用独立scalar和既有对象/容器路径。原16796/rootlist的准确A/未知初始化缺口仍分列，不自动阻本reader。

## 13个scalar：可直接编码的raw字段

`this=R15`、stream=RSI。未IFix普通分支中，12个u32调用既有helper147520110，一个u64调用1475202d0；每次CALL后下一条就是EAX/RAX直接store，没有算术、掩码、换位或中间helper。只复用这些已审primitive合同，不推定IFix分支动态行为。没有直接string槽或string helper调用。

| tag / wire | clientField / offset | raw类型 | 准确CALL / store |
| --- | --- | --- | --- |
| 1 / 0 | PJAJDHBBMGB / +cc | u32 | `1504b3728 /372d` |
| 2 / 0 | NLNJDCDLHHE / +a8 | u32 | `1504b3158 /315d` |
| 3 / 0 | FBLKPHOCBIK / +d8 | u64 | `1504b383d /3842` |
| 4 / 0 | AANCEPCPHKJ / +c4 | u32 | `1504b33e6 /33eb` |
| 6 / 0 | BLBHOJMCOJN / +b0 | u32 | `1504b34cb /34d0` |
| 11 / 0 | ACNMNNPAHPA / +c0 | u32 | `1504b345b /3460` |
| 17 / 0 | HCHEIDINIMO / +bc | u32 | `1504b3749 /374e` |
| 18 / 0 | APNACEAKHPN / +d4 | u32 | `1504b31b7 /31bc` |
| 19 / 0 | MHAIENEPEIP / +c8 | u32 | `1504b385e /3863` |
| 22 / 0 | GBBBBHEGCDL / +ac | u32 | `1504b3314 /3319` |
| 23 / 0 | BPGODHNDNNJ / +b4 | u32 | `1504b387f /3884` |
| 24 / 0 | MKFOJONFDMA / +d0 | u32 | `1504b38a0 /38a5` |
| 26 / 0 | JIDLIPEAKLI / +b8 | u32 | `1504b379f /37a4` |

这13项是wire/offset/type/identity合同，不凭旧Proto或混淆字段名赋予UID、AvatarID、GUID、depot等业务名。可先建立raw Proto与独立wire断言；同Player真实模型投影须有实际消费桥。主线/集成者已即时收到全部13项，不等待完整实体图。

## 18个引用槽：准确外层分派与可复用子图

普通reader从key比较/跳转直接选择相应field，再交既有pointer。七个u32 list分支先对key做 `ANDfffffffd`，即接受同tag的wire0/2；这是key归一化，不是列表值变换。下面均为准确外层tag/type/既有pointer关系，本次没有执行codec或非空nested/list，也未恢复本class的具体静态codec实例。

| tag / wire | clientField / offset | 目标类型 / 当前内容来源 |
| --- | --- | --- |
| 5 / 0,2 | DIIEILGCADP / +98 | repeated u32，definition35584/instance11 |
| 7 / 0,2 | KLODODOMJPF / +30 | 同上 |
| 12 / 0,2 | HEHFONFNHEH / +60 | 同上 |
| 16 / 0,2 | CBEKIIICDOM / +a0 | 同上 |
| 29 / 0,2 | EIJBACPBACO / +18 | 同上 |
| 30 / 0,2 | LKJJFCJNENG / +80 | 同上 |
| 32 / 0,2 | EIBCGPEOJLB / +58 | 同上 |
| 13 / 2 | PJGIPBJMBOP / +40 | map<u32,u32>，definition17961/instance3723 |
| 14 / 2 | JOJLFAOOLMH / +20 | 同上；与tag13独立 |
| 9 / 2 | HBJEBJGADND / +28 | repeated26952；本轮未见已批准内部schema |
| 15 / 2 | CDOAFDGJGDF / +48 | repeated73976；已有SceneInfo五字段raw |
| 27 / 2 | ADLKOJNELLC / +78 | repeated27196；已有e2小树raw |
| 28 / 2 | LGDGBGNKEFO / +68 | repeated27216；本轮未见已批准内部schema |
| 31 / 2 | AMLLHJJIMNI / +88 | repeated53824；已有e1外层/e8非空raw闭包 |
| 8 / 2 | INBCHOJBJMP / +70 | singular53824；同上；不能只凭外观命名Weapon |
| 20 / 2 | NKAABDLJKCF / +50 | singular17469；本轮未见已批准内部schema |
| 21 / 2 | BNHMCGLAMLE / +90 | singular27246；已有五u64叶子raw |
| 25 / 2 | CKDDHADKCBK / +38 | singular27196；已有e2小树raw |

准确共同入口：u32 list在 `1504b370e CALL158db8e10`；message list在 `1504b35bd CALL158dc41f0`；两个u32 map在 `1504b3823 CALL15893d460`；四个singular原pointer非null分支跳公共 `1504b39ef CALL1474d7e60`。metadata class与泛型参数均逐字段保持，不以相同shape互换tag8/tag31或tag25/tag27。

u32 map的具体17961/instance3723 helper绑定已存在于 [inventory-container-observations.json](../protocol/cn-7.2/inventory-container-observations.json)，SHA `352f094fec870074abb70207af317870525b025c9b68805500c6410347feb72d`：方法definition136431/table304856/spec190836/pointer99558→15893d460，arguments明确u32/u32。本轮只读复用，不取该helper新body或执行它。列表/nested同样复用既有raw ABI与schema，但当前41868 codec实例和非空路径未重放，外层分派不等于完整解析成功。

已有内容的准确来源：

| 子类型 | 可只读复用的批准范围 | 固定摘要SHA-256 |
| --- | --- | --- |
| 27196 | 两个76885子对象及小树，含独立非空/合并/错误边界 | avatar-trees `e6c348533cc99c7f8af291ef3e019efdbfa21c60f3152a498c8c8b68024f9789` |
| 27246 | 五个u64，含高位/上界 | avatar-core `81a0411e8fe6b7cc3ca606473ae684744a8df73e97009666df1c2775e9bdb3d4` |
| 53824 | 12外字段、e1两叶子；后续e8补完整复杂raw图，e1旧空范围不冒称本次新恢复 | complex-outer `fa5559ebad448331432dcb9e42701df71bb4531cf9dee56fd64e290aa0129312`；complete-raw `37c90e4dea9fccc4ac20ce68616612250b5a57169e564081c33dc69a25def837` |
| 73976 | 五字段u32/bool | scene-info-raw `f52f41a817241646d63d692b120d5c134c1b30dfe8b2da5d440de5d9557f7c75` |

见 [e2](CLIENT_72_AVATAR_TREES.md)、[e1](CLIENT_72_AVATAR_COMPLEX_OUTER.md)、[e8](CLIENT_72_AVATAR_COMPLETE_RAW.md)、[SceneInfo raw](CLIENT_72_SCENE_INFO_RAW.md)。这些是既验raw，不自动产生当前SceneAvatar的业务来源、fresh对象构造或实机验收。

## 消费交叉：只闭已证关系

[SceneTeam MIN-WIRE](CLIENT_72_D02_M0_SCENETEAM_MIN_WIRE_20261116.md) 的helper m467381 `1507b25d0..1507b29d2` /1026B/243条，code SHA `21ffce122e3fd78b5ad994b7bbc43806cac6a5f4292ce477a159cbae52eb2293`。原件 `local/migration72/d02-m0-sceneteam-locate/min-wire-field-run1/467381-source.private.json`，file SHA `2964b1385c19f769d43c3a0f90a418752e0f5d4e92d4ede0de20d7ab54330b1f`。

准确关系：`1507b2618` 读76429+78→R14（已证AvatarGUID）；`1507b284a` 取76429+20→RCX（独立41868）；`1507b286a` 读此对象+C4→R9D；`1507b287e` 取manager字典，`288e/2891/2894` 形成 `(dictionary,AvatarGUID,C4,1)`，`2897 CALL15946e710`。结合本轮tag4→C4，可以交叉 **SceneTeam的该独立41868实例tag4与其AvatarGUID的字典关联**；C4具体角色仍未知，GUID来自76429+78，不是41868+D8。

另一oneof 41868实例仍只证16796+38的对象指针整体交15184e100；上述两个body没有直接读取它的+D8或其它内部scalar。不能把worker的栈偏移或C4字典关系套成同实例AvatarID证明。当前需要的角色ID/武器/资源身份投影不能由本reader自行命名。

## 本轮实际检查与原件

driver `sceneavatar-source.private.py` SHA `34c2f585bd8601520afe47f3b83419ead799c98fe4c941d0759539618486b2c7`，原件 `reader-run1/`；完整result SHA `0f88b7c050119ed1bdfe3e8f0cad260a512590b075a9f3972d2a89cda2a41994`。helper及Capstone源/库hash与SCOPE既固定值相同，记录在pre-body中；只取这一个reader，没有新cctor/helper/global/body或CPU。

`sceneavatar-bindings.private.py` SHA `4a44d8191440d48f228450172fb1300dffa6f5f4253d77b47d5fb24a438c0c03`，原件 `static-bindings-run1/result.private.json` /113275B，SHA `fff68812511578d774332502c6c14974caccef6cfe93f3116c23cebe8741213d`：

- 固定已保存原bytes/ASM与metadata，全文回拼；13个CALL/store、18个field load/共同入口逐项assert。31个唯一tag恰覆盖metadata全部31存储槽。
- 按源中的key比较、AND归一化和有向跳转做128个**纯静态**分派判定（tag1..32×wire0/1/2/5），全部符合上述字段表；这是source有限决策树核对，不是执行指令、原生样本数量或protobuf合法tag上限。tag10在此reader没有field，错wire回skip。
- 7个手写字面wire/既有WireInput断言通过：tag3的 `2^53+1/u64 max/显式0`、u32 packed `[0,1,128,0xffffffff]`、同field unpacked，以及两个已有子类型的非空字面envelope。4个模型拒绝通过：u64截断/溢出、packed-u32截断、nested长度截断。
- 这些是编码/model断言与静态源验证，未声称当前reader/codec原生执行或目标错误行为。没有把旧native样本计为本轮，也没有新模型、JUnit、构建、部署、数据库或客户端验收。

## 可编码边界与旧初始化任务准确归属

三层结果分别为：Cmd层复用原嵌套来源（41868无本轮新独立Cmd）；type层闭child16796的目标Avatar oneof与typed SceneTeam消费；field层本轮闭31外层、13scalar identity及已有子图的准确引用。可立即编码13个raw scalar，按既有批准schema设计所选嵌套；不发送完整包、不标entry-ready，不把未知子类型补空壳作为验收。

### 15184e100已有原研究，不是新独立入口

主线要求核旧source及准确限制后，确认 `owner18163/m138207 GLJODPOHMME@15184e100` 正是原 `F-03d3f1`、`F-03d3f4d1` 及 `F-03d3f4d2` 的AvatarInfo应用入口；不能换名成新的SceneAvatar初始化以重取/执行完整handler。精确签名仍为p126300/type48178与p126301/type41868，与共享worker真实CALL一致；它也不是Avatar `.ctor` 的地址。

既有完整静态source已存在，无需新取EXE/body：`local/migration72/analysis/avatar-init-plan-inputs.json` /1394588B，SHA `d52a3fcb2948b27dbfd07de6da9207946dd6cd9ae37ad5b5756a4b6f09dcf24b`，其中已保存本函数 `[15184e100,15184e6c8)` /1480B/339条和byte SHA `683420b391fcd137642057620895f33f576e76c241cce94c91f1f1fd6dff163a`。已审 `avatar-deep-observations.json` SHA `fc08a41533290f671693ac1a0a0ec3608ac56c1cd93cc039f2c681f8102a44c7` 给出相同PE-exception边界/byte hash。旧同地址disasm包含邻函数，未以其长窗冒充本函数；本轮只核固定既有source/批准范围，不重跑它，也不生成第二份新取源。

准确允许边界：

| 原任务 / 固定来源 | 已验允许片段 | Scene41868参数与本轮可投影范围 |
| --- | --- | --- |
| F-03d3f1，avatar-contract摘要SHA `a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3` | `15184e100→15184e1e4` 身份前缀；48178 ID编码至模型+250，再独立解码/具名Avatar资源查询；GUID写模型+38 | 实际调用 `R8=0`。闭的是 **48178 tag1/2** 的AvatarID/GUID投影，不能移植为41868 tag2/3的业务证明 |
| F-03d3f4a，avatar-state摘要SHA `6cbc46b1d447883a0d4c3ede00b43af56ccf3950a37de85db50840396f1b4cc9` | `15184e1e4/1fd→151849670` 的48178 tag4生命态片段 | 这是AvatarInfo状态消费，不读本轮41868的同号tag4；不能互换 |
| F-03d3f4d1，initializer摘要SHA `1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf`；旧完整native SHA `3b314e34d191a01bd18b3394146d4cf082391fe105ca2c2dac44a363ab19af63` | 分段构造对象上的连续身份/生命/装备前缀；空装备仍停15183f290，非空停15184a5f0；有限省略/尾部片段 | 连续调用同样 `R8=0`，相关尾函数151845140的已验省略输入也为null Scene参数。没有已准非null41868完整消费或13scalar业务投影 |

见 [Avatar Contract](CLIENT_72_AVATAR_CONTRACT.md)、[Initializer](CLIENT_72_AVATAR_INITIALIZER.md)、[Live Contract](CLIENT_72_AVATAR_LIVE_CONTRACT.md)、[Deep Research](CLIENT_72_AVATAR_DEEP_RESEARCH.md)。本次只读核对原工具 `identity(...r8=0)` 与initializer连续前缀/停止表，未重放旧native或读取被拒动作返回。

本函数既存static call表没有准确A的直接CALL，不能据“其后继里有A”泛封所有前缀；但完整18163构造地址是 `15183fe60`，原完整ctor/第二allocator返回准确A依赖、池/必经后缀与自然输入仍冻结/未闭。本轮不以既存source存在或31槽wire完成解除这些限制，不再以新任务名取完整构造、读第二返回或连续执行完整g2。允许的旧片段只读复用；完整handler/构造不是本轮新增许可。

### 当前真正可编码与缺口

13个41868 scalar可编码raw Proto并做独立wire/64位/错误边界；31个外层可据准确type设计增量schema。**当前没有新增获准的41868业务getter投影**：tag4/C4只闭上面的字典关联，tag3/D8等只闭wire类型和identity。不能把既验48178的同名/同号字段，或服务端AvatarID/GUID/depot值直接填到这些未知语义槽。未知保持未知，不以默认0、空对象或省略给完整发送验收补洞。

17469/26952/27216虽暂无本轮批准内部schema，目前也没有“新号必须非空”的消费证明，不自动扩成M0前置。对其它必要非null Scene输入，应先在已有允许source中确定准确独立消费边、相应字段与所需输入，再另定最小范围；现在没有可直接重命名执行的“新15184e100 source”，也没有证明所有独立来源已穷尽。

因此剩余不是一个统一A封锁：root列表仍是现夹具未知初始化/metadata事实缺口，其cctor对象构造才是准确A依赖；完整g2的准确限制不变。raw Proto/回归、SceneTeam收尾、SceneInfo独立getter CFG及旧号资源/生命周期均可继续由主线分项推进。本轮结束于稳定raw字段与上述准确旧许可边界，没有新增客户端/CPU/cctor/helper/source窗或默认模型。
