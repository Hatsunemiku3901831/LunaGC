# D02-M0-AUTHORITY-CFG：精确入口的有限可达控制流

## 中文有限计划

本项只补owner28174/m218972 BPHJMGBGICJ、普通AOT精确entry148D6E520/slot88，与已具名IsAuthority的authority语义桥。原SCENEINFO-MIN的unique-pdata=0失败保持原文和hash，不把本项窗口改称完整function，也不更改MIN-WIRE已冻结专题。

精确AOT entry与完整function边界是两种证据。叶函数可以没有Windows pdata；在不知道完整边界时，仍可对精确entry起的固定窗口独立证明有限可达路径。只有与问题相关的全部可达路径在该窗口内被逐条覆盖、没有未知依赖时，才给相应静态语义。这个方法不恢复完整函数尺寸，不推定首个RET或16B对齐为函数结尾。

1. 固定CN7.2同EXE/global metadata/startup metadata三输入hash，核metadata缓存/signatures、m218972精确owner/slot/零参数、普通AOT指针及同PE可执行section身份。复用旧IsAuthority完整source及0-pdata失败；getter阶段不重写旧失败、不取A/slot155/constructor。后续主线单独批准的setter仅按第二阶段边界处理。
2. 仅在`[148D6E520,148D6E620)`全部256B位于同一PE文件映射可执行section时读取并原样保存完整window、文件offset、section信息、SHA。若映射门失败则0B读取，不缩窗取巧。
3. 从精确entry做worklist可达CFG，每条指令独立严格解码/字节一致核对，维护指令起点、字节归属、跳转目标和分支覆盖。条件分支覆盖两侧；RET为终端。窗口外、间接控制流、没有已审合同的CALL、指令失败或重叠均精确停该依赖，不扩大window、不取callee、不跳data或裁tail。保存未访问字节范围，不将它们宣称为代码或函数tail。
4. 对可闭合的路径核是否读取同Entity28174+42C、是否与23977.OOJNMMAIJMI的已验静态槽比较、返回值如何形成。只给“实际虚分派目标为此base getter”条件下的结论，不将base slot88推成所有derived运行态目标。
5. 原始证据只写忽略的`local/migration72/d02-m0-authority-cfg/`；唯一tracked产物为本文。成熟部分及时交集成者。本项无nativeCPU、客户端/动态进程、hook、global数据新读或默认模型，不取第三source，不自动派工。

## 第一阶段实际结果：getter只读+460

固定三输入实际核对通过：EXE450927000B/SHA `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata83644188B/SHA `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup4116148B/SHA `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。metadata缓存SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` 与signatures SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` 同时固定。signatures工具只输出非零参数方法，零参getter在该表缺席符合该产物合同，未伪造返回类型。

普通AOT cell `142A50FB0`（文件offset2A4F5B0）的8B为 `20E5D64801000000`，准确指向148D6E520。整个256B window位于同一个PE可执行文件映射section `il2cpp`（index9/start146D2B000/flags60000020）；window文件offset8516D20。完整window SHA `49d2d42507e00a17476e1a330d7309905526263b76dc7506f271a0be960f069d`，未缩短为8B保存。

从精确entry只有以下可达路径：

| PC | 原始字节 | 严格可达指令 |
| --- | --- | --- |
| `148D6E520` | `0FB68160040000` | `movzx eax,byte ptr [rcx+460]` |
| `148D6E527` | `C3` | `ret` |

共2条/8B可达指令，唯一fallthrough边520→527，全部路径到RET；无分支、CALL、间接边、窗口外依赖、指令重叠或解码失败。`[148D6E528,148D6E620)` 的248B仍保存，但未作为可达指令解码；RET终端不被当成完整函数尺寸证据。

精确字段是owner28174/field138517 `FGHOOBCMDMI`，offset460，metadata type bool/attributes4。getter直接零扩展该存储字节到EAX并返回，没有读取+42C，也没有比较23977.OOJNMMAIJMI。前次“沿slot88会直接找到+42C比较”的假设因此撤回。

结合已验具名IsAuthority源（m359954/1494D52C0，code SHA `56d9caff3fe6e864b7495cb47b0ee5529e27cd345319881121097a1c3a602fa9`）的对象链与尾跳，准确结论为：**当该对象slot88实际目标为m218972时，IsAuthority返回同Entity28174+460的已有bool状态。** 这不是所有derived实体动态分派的证明，也没有证明+460由+42C或某种peer ID生成。

原完整body取源仍是0-pdata失败：`218972-failure.private.json` SHA `003c5c974c62912e86308de1177882f1bbee060c6e5c513676c476b900f5cec4`，原错误`ValueError('Nonunique pdata')`；精确诊断`containing=[]`、bodyBytes0、strictDecodeStarted=false，诊断SHA `08bf3e34bc94131849c8b78dc833ccc355c385a4f6904b37ad2ee7b46d2e20f7`。本项没有重写旧失败，也没有称本window为完整body。

本阶段证据均相对忽略根`local/migration72/d02-m0-authority-cfg/`：

| 原件 | 大小 | SHA-256 |
| --- | --- | --- |
| `authority_entry_cfg.private.py` | 11460B | `5fa14c1a488c5c1ff59983997948f87faab0fd2e4941a63b009dfbd3d64437da` |
| `run1/window.bin` | 256B | `49d2d42507e00a17476e1a330d7309905526263b76dc7506f271a0be960f069d` |
| `run1/window-source.private.json` | 5636B | `802cf3701f7567b2635ad45bfc39995cb7564e83654c90584b64984bec579631` |
| `run1/result.private.json` | 6984B | `30f2d67ce05c3cc9630848fa8eac53cf4fe20bef2592e6eacfca42acd67b7d9d` |

Capstone5.0.7、skipdata=false，模块SHA `94571d1314355e261aff0ddfc94985fe8761232ddff6874181fd21efa9fab47f`、DLL SHA `76958e18380023a68fd1714fa2e01c594cc6db1955a07ad6937b66e66dc5d6c3`。没有执行目标CPU、读取新的global值、取callee或创建模型；本阶段没有新的安全拒绝。

## 第二阶段：单个base setter确实写同+460

主线在getter结论后单独授权base slot155/m219460@148D96B30(bool)，仅核同字段的写入，不跟新callee。实际声明owner28174、混淆名LGKKECCILEG、flags9C6/slot155；signatures唯一参数为bool/byRef=false。普通AOT cell142A51EF0的8B `306BD94801000000` 准确指向148D96B30。

同固定exception目录SHA下，该setter的containing pdata也为0。保存独立前置和0-pdata结果后，按授权读取同section的完整`[148D96B30,148D96C30)`/256B window（文件offset853F330），没有尝试邻居function边界、完整body或缩窗。唯一可达路径为：

| PC | 原始字节 | 严格可达指令 |
| --- | --- | --- |
| `148D96B30` | `889160040000` | `mov byte ptr [rcx+460],dl` |
| `148D96B36` | `C3` | `ret` |

共2条/7B，全部路径到RET，无外边、CALL、间接边、重叠或指令失败。其余`[148D96B37,148D96C30)`的249B保存但未解码。setter直接写入收到的bool字节，不自行比较+42C、读取OOJ或归一化参数。

| 原件（相对本项忽略根） | 大小 | SHA-256 |
| --- | --- | --- |
| `authority_setter_source.private.py` | 5710B | `2f1cf43dc8aaae7290862f4bcb0903a1d167864aef763fe628ad0c47a2c6101c` |
| `setter-run1/preflight.private.json` | 2727B | `b79e66f81bb3107e099bcb333dcbcf6ab505a04572f2fdcc454d905f695e038e` |
| `setter-run1/result.private.json`（0-pdata，未取完整body） | 2757B | `bbddeab6684c780656407e717ae96ffaa2fb748d355e2d5c09e945d7f9e24112` |
| `authority_setter_window.private.py` | 8164B | `839065117e312a6b0a91dbe624cd546b127093a0b05aace54eb1bb618f4edd92` |
| `setter-window-run1/window.bin` | 256B | `5e09f79252ffbe0086beec9602e91b1b813ee90a71b2e5497d20b8a748759657` |
| `setter-window-run1/window-source.private.json` | 4865B | `abf592abb0dde526e7e12bbc72982cc8b0fb7ff3ef824ac0309f5dcde0a14cd1` |
| `setter-window-run1/result.private.json` | 6172B | `df2e02e646eed8a271442682951c9aa0cd2974d01343edf7ae79a6110033f89b` |

两个window合计512B原样保存，仅15B/4条可达指令参与上述证明；本项始终不声称获得两份完整function body。getter旧失败和setter本次0-pdata诊断各自保留，不互相覆盖。

## 既采factory交叉：比较先于setter

只读复用已采m584597@151540800完整source（code SHA `df3ef4b592ed89a4aa91730a8ce9cb65405719fff2e09495b0ed0205b3f31c6a`），实际关系如下：

| 已采PC | 实际关系 |
| --- | --- |
| `151540983` | factory第二u32参数写同Entity+42C |
| `1515409A8 / 9AD` | 第三bool真分支令DL=1，调用对象class+5A8，即slot155 |
| `1515409B5 / 9B8 / 9BE / 9CA` | 第三bool假分支取同第二u32，与静态槽14586F438比较，sete DL，再调用同slot155 |

因此在此factory分支确实到达、且slot155实际目标为m219460的条件下，`Entity+460 = 第三bool ? true : (第二u32 == OOJNMMAIJMI)`；再在slot88实际目标为m218972的条件下，具名IsAuthority读取该已存flag。这形成**有明确动态目标条件的authority状态生产/读取桥**，没有实现或验证allocator返回、constructor/IFix自然选择、所有derived虚分派或真实实体创建。

World factory15154BF20的既采完整source976B/code SHA `1d46e69c9304becd4b7efef4671bf31541fb433e6e55e4a58655f5653df08504` 在15154C05B写第二u32到+42C；其15154C07E为`call[class+460]`，是vtable slot114，**不是**对象字段+460。该body没有直接slot155调用，不能据同一个十六进制offset推成authority已设。slot114后继保持原专项边界，不追新callee。

已有实体更新m219141@148D813D0仍是先比较旧+42C，新值不同则148D813FF调用148D6F5C0(entity,new,old,0)，148D81404才写+42C。当前没有该callee的已批准语义，不能把“更新+42C”直接当成“重算+460”；本项不新增该source。

## OOJNMMAIJMI：可变静态u32，已知初始化写0

精确metadata为owner23977/field115994 OOJNMMAIJMI，uint32，attributes11：private/static=true，InitOnly=false、Literal=false。它是可变静态字段，不是readonly或literal常量。原runtime offset02003E28的低24位3E28，加已验primitiveBase14586B610，地址恰为14586F438。

本项按既有SceneInfo索引找到`local/migration72/d02-active/main/scene-runtime-context-third.json`，3870401B/SHA `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`。其中23977.cctor/m185483的既采完整source `[14D2CA110,14D2CA6A8)`/1432B/code SHA `abf656cc69c8a1eaf7aff9d7ee5663d1d8c208eeca6ba56e973c3bec9d1f6633`，连续bytes回拼与旧SHA一致；没有重新从EXE取该source，也没有执行.cctor。

已存指令14D2CA658、字节`C705D64D5AF800000000`，其RIP目的地址准确为14586F438，立即数为0。它只证明执行到这条初始化store时写0，不证明当前运行值为0、没有其他writer，或生产通知应该发0。14D2CA403写14586F444=90000002是另一个模板槽，不能混作OOJ初始化或authority key。

只读交叉原件`cross-source-join.private.json`，8015B/SHA `62b7ce24ba7c9ede03ac749f214eec29c6a89854d72a5aa7eb75adda2e23248e`，含字段flags/地址、旧完整source固定hash、精确初始化指令及两factory/219141的所选原指令。新增global数据读取为0。

## 可交付与准确剩余

已交集成者：base getter/单setter对同bool字段+460的静态合同，以及已有factory的条件authority判定关系。root14/tag1所到+42C现在有“参与authority判定的u32 key”用途证据；**仍没有把它命名为peerID/hostPeerID或接现有world.getHostPeerId()的依据**。OOJ真实后续write producer、本地key何时由哪个目标typed消息设置，以及World/已有实体分支的实际状态刷新仍未闭。base声明不能代替derived运行态分派证明。

本项没有真正外部文件缺失，也没有新的安全拒绝；叶函数缺pdata的工具门已经用独立精确entry可达CFG得到有限答案。剩余是准确且未扩追的producer/动态目标问题，不能以“没分析”或“永久缺完整源码”概括。下一最小独立研究可只定位14586F438这个可变槽的实际后续writer及typed输入，不能从初始化0或旧Peer布局填正包号/字段名。

有限任务完成。实际核验为固定输入/AOT/section身份、两份完整256B window、可达指令字节/边界/分支覆盖、metadata字段类型与flags，以及既采完整source只读回拼。无目标CPU、客户端、hook、新模型、global新读、运行/Proto/生成/构建/DB/Git变更；M0、SceneInfo完整sender与entry-ready均未升级。MIN-WIRE等前项专题保持冻结。
