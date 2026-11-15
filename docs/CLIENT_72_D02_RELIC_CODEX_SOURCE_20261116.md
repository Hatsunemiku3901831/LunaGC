# Relic Codex typed入口：完整边界与静态来源

`D02-INCR-A2-CODEX-BOUNDARY-20261116`与`D02-INCR-A2-CODEX-SOURCE-20261116`已完成各自限定来源出口。固定方法为`MoleMole.MonoSceneUISetup`（21946）的`ShowCodexReliquary`（168869）；真实参数包含目标类型74316，因而可作为[A2有界自主研究](CLIENT_72_D02_INCREMENTAL_REUSE_PLAN_20261116.md)的新typed消费者候选。**完整2792B来源已取得；整窗`semanticInterpretation=false`，rankLevel/setId职责、实际资源行及ready均未闭。**

## 固定身份与两个独立出口

| 输入或产物 | SHA-256 / 准确身份 |
| --- | --- |
| 固定目标PE | `YuanShen.exe`，450927000B，`8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| metadata / signatures | `current-structures.json`：`d5d7f671a622439b2f9a3ed0018ce551aa5c573cd9ca68bed3b006712b8a98aa`；`client72-signatures.json`：`28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` |
| 原边界请求 | `codex-boundary-request.private.json`，8499B，`eb15628b4dce59380405ea2e26a3009c0d35127f800d8b11a322e755e2c314ea` |
| 边界run2结果 / manifest | 6991B，`8d17e8d2379690f8f25e3583ca619332dc65f5c1c8bf6402ee7bef60e11fa841`；manifest：`1dbb2f25cb7b801b77182fadf6913cc6fe9e804d16df239e91d7118b5547c881` |
| 独立source请求 | `request.private.json`，4185B，`b84fcfb5591576c1683daddb74639db5502442cc95acecc363ebeb2878f3a1cd` |
| source结果 / manifest | 4440B，`103c75c3894aebbfad1c9528de599884e379087b32070fbf0399512b89e16a9f`；manifest：`47c52a54633c0a2d4be301a5c62fd48a05914d0d9541d233f98e4830f363665e` |
| 完整body | 2792B，`3fd04ec7192e2347533827703f673cf834ef5b13963b7b452c791de9953df284` |

固定metadata/signatures只给出唯一21946/168869别名组合，receiver为instance21946。两个真实参数分别是类型引用339772→class74316 `DFHAIDJGOEG`，及类型引用124→uint32；返回类型仍为`not-recovered`，不按方法名称推断资源字段角色。

边界任务独立确认fresh AOT root/slot共16B均指向`0x14c3fa5a0`；完整exception目录中唯一包含且唯一begin的pdata记录给出`[0x14c3fa5a0, 0x14c3fb088)`，完整大小2792B、文件offset196750752。pdata的unwindRVA只记录，不读取xdata；该边界与既有冻结区间无相交。边界阶段读取PE header两次共4616B、4096 distinct，完整exception目录14344080B；五stock仅hash总550661784B。body读取/解码均0，不把边界通过当作body语义。

后继source使用已验边界，固定PE SHA后以**同一文件句柄只读一次2792B body**，Capstone5.0.7一次解码632条，完整references259条。新header/AOT/pdata/exception目录读取均0，callee/global内容/xdata/payload读取均0；未执行目标CPU、RPM、客户端、JVM或数据库。

## 准确冻结边与下一分析范围

完整静态索引确认直接`CALL 0x14c3fa784 → 0x1405336c0`，这是已有A精确冻结目标，保持该CALL返回、构造及依赖后继停止。整窗`semanticInterpretation=false`，不能凭body完整或方法名批准rank/set用途，也不将此CALL泛化为所有独立Relic来源都受限。

不依赖该返回的前置片段交Sol max继续有界分析。下一出口须实际建立目标字段到等级表key、套装用途或独立非零role oracle的关系；若仍只有typed身份，则保留该缺边，不读候选值选标签、不执行被冻结返回。没有批准新callee采集、全域扫描、资源行投影或实现合同；A/B自主研究继续，父R3及D-02六项0/6不变。

## 首次失败、工具限制与原件

REA `inspect_binary_layout`在本机Windows不支持，边界任务沿固定已有helper完成回退。首次边界尝试在identity阶段因固定helper的执行环境未绑定`ast`而报`NameError: name 'ast' is not defined`；`stockHashReads=[]`、`peReads=[]`，body/read/decode为0。该失败和原脚本保留于原目录；run2只补`ast`绑定后通过，不追认首次成功。

原件分别保存在忽略目录`local/migration72/d02-incremental-relic-codex-boundary-20261116/`（失败与`run2/`）和`local/migration72/d02-incremental-relic-codex-source-20261116/`。完整asm、references、body、driver、输入账及manifest仅留本机；共享文档只提交本摘要。集成只读核对固定result/body和manifest身份，未重跑PE、采集或测试；没有运行代码、Proto、配置、补丁或资源变更，无构建或部署。

## 两个typed UI转交路径：来源与配对局部语义

`A2-RELIC-REMAINING-TYPED-20261116/PAIRED-UI-TRANSFER`已完成两条新准确来源及其有界局部转交分析；不改变上述2792B Codex整窗的`semanticInterpretation=false`或其中准确A冻结边。

| 方法 / 完整来源 | body SHA-256 | result SHA-256 |
| --- | --- | --- |
| 60698.m503451，`[14d27a6a0,14d27a6fc)`92B/23条 | `c1bcf9bdc87494e2cd3eeb869f0c60107899330ff8b94acfb0fcec80916b79a5` | `50dc95e3ef63b53a0499ed76011f1772772813179c27fbc6ecd5366bae73d5ae` |
| 49417.m411125，`[14cc953d0,14cc95462)`146B/35条 | `ca61bd8d4312f9724a273c7ae796fc2f7c6130f635122307fbba0feb8341156c` | `bf6484596194a643738bcdd770f21e0cbc7835b4d81a838aff072204574a8e5e` |

两个来源共238B/58条，分别以固定metadata/真实签名、fresh AOT和完整pdata边界取得，每body只读/解码一次；两个source结果均未报告直接已知冻结site/target。它们各自的source阶段仍记`semanticInterpretation=false`，后续配对proof只验下述局部值流，不追动态实现或Codex返回。两个任务均无callee/global内容/xdata/payload读取或目标CPU/RPM/client/JVM/DB/build/service。

真实typed链为`49417 → parent61340.field303743/+38:60698 → parent16396.field77631/+10:MonoSceneUISetup21946`。503451接收`(this60698 Q, resource74316 X, uint32 U)`；普通分派条件满足且setup非空时，从`Q+10`取得setup，把原X/U原样转为`(setup,X,U)`，在`14d27a6c9`尾跳`ShowCodexReliquary/14c3fa5a0`。411125接收`(this49417 Z,X,U)`；其普通分支先取`[Z+38]=Q`，再内联同一setup与分派过程，于`14cc9540b`直接尾跳Codex。**411125并不CALL503451。**

动态分支只证明原X/U与对应Q/Z的参数转交到`14e1c9920`，未读取自然分派flag或动态接收者global内容、未追尾目标实现。空receiver/setup分支保留原helper调用，不假设异常分类或返回。完整两body均没有X或raw46431成员读；互换rank/set候选标签不改变这些已观察的本地指令或转交，所以这两条UI路径不能区分rankLevel/setId。此有限结论不套用到60446的字典及byRef边；其public501523独立来源线另行研究。

配对proof为`local/migration72/d02-incremental-relic-remaining-typed-20261116/consumer411125-and-503451-route-proof.private.json`，8674B，SHA-256 `f2587593c1c74e6be4150701e75a5fca014d8b22cbd4b7e1388938862d6e628d`。原source分别在`local/migration72/d02-incremental-relic-consumer503451-source-20261116/`和`d02-incremental-relic-consumer411125-source-20261116/`，各自manifest SHA为`310b6ad85d76bdeba405762954687dc2dcf6dde474475bee26eaad9a6c4d5e07` / `96edf99a305a6e8e0aca02f23a75c54071ead364b64e55fd5eb7d5be8dfcad8b`。集成只读核上述body/result/manifest/proof身份全匹配，未重拼、采集或运行测试；没有动态/客户端或回归验收。A1与Relic其他独立路径继续，rank/set、资源ready和D-02六项0/6保持。

## 501523 typed缓存入口与剩余边覆盖

`A2-RELIC-REMAINING-TYPED-20261116/CONSUMER501523-SEMANTICS`已完成60446唯一public入口`m501523/FIGBFDOHGGD`的准确来源及局部语义。完整`[147c38270,147c384e8)`632B/134条，body SHA-256 `a303ba3b502007213d446aaad2c14d1aaac66dca0f0bd78a3d1643bd82ed2496`，result SHA-256 `5a6e8efa25d0c4401bd26225ee9d188e1db8da84e96e972c147ccc2a87b4d9d2`。来源阶段`semanticInterpretation=false`及直接冻结引用0按原件保留，后继proof只判本地值流。

真实入口为`(uint32 K,74316& O,bool C)`，方法先将输出槽`[O]`清0；不读取传入资源。实际static字段选择绑定60446.field299590（`13732<U32,74316>`，runtime raw`4035468`，代码root`+35468`）和field299591（`Dictionary<U32,74316>`，runtime raw`4035470`，代码root`+35470`）。自然flag未读，不把容器命名成等级表或套装表。

299590路线向helper传原K与同输出槽；fallback `147c3832d→m501535/147c38a60`仍是原K/O，AL非零后将`[O]`作为整个74316指针连同原K转交同类helper。299591路线把原K交候选索引helper，本地r非负且经原array边界门后，将`A+24*r+30h`的整个指针写O；helper是否真正匹配原key仍未知。没有本地raw46431字段被读取并用作另一张表的key，也未证明非空有效资源或实际cache插入。

返回只闭AL状态，声明返回类型仍未知：`SETNS BL`仅替换低字节，随后`MOV EAX,EBX`；字典索引路线EAX高24位仍带字典指针低32的对应位，负索引的AL0不等于完整EAX0。只有显式`XOR EBX,EBX`出口证明完整零。自定义helper普通ABI路径也只将低字节置1，不能推成规范bool/u32返回。

本批固定的3字段、6参数最终覆盖如下；它只覆盖列明边，不把未读body写成不存在：

| 边 | 已验范围与准确剩余 |
| --- | --- |
| 52700.field261342/+80→74316 | 既有74316.+20→raw46431及raw+88 getter链复用；`151851c21→1405336c0`的dictionary返回/分组语义准确A冻结，不据容器形状选rank/set |
| 60446.field299590 | 501523实际选择root+35468，原K/O查找及whole-value转交已闭；不证明key职责或容器角色 |
| 60446.field299591 | 501523实际选择root+35470，候选索引/whole-pointer输出已闭；匹配和rank/set角色未知 |
| 21946.m168869(74316,U32) | 2792B来源及既验前108条String-only范围复用；整窗semanticfalse、准确A CALL仍停 |
| 49417.m411125(74316,U32) | 146B/35条及普通typed setup→Codex转交已闭；动态尾未追，无rank/set桥 |
| 60446.m501523(U32,74316&,bool) | 632B/134条、两缓存实际选择、原K和whole-output流及AL限制已闭；没有raw角色字段到不同表key |
| 60446.m501529(U32,74316&) | 声明/真实参数已核；不是501523直接CALL，未选中有独立role判别力的消费边，body未取 |
| 60446.m501535(U32,74316&) | 已证501523以同K/O调用；只有同lookup后继，body未读，不继续扩树；不宣其永不读raw |
| 60698.m503451(74316,U32) | 92B/23条及普通setup→Codex转交已闭；动态尾只转交，无rank/set桥 |

501523其余helper只保留当前同查找/整体值边，没有新的独立raw/role证据选中它们；不预取501529或继续501535。交换rank/set标签仍与全部这些typed、UI和原U32→whole-resource关系相容。准确缺的是raw成员进入等级/套装用途的区分消费边，或绑定目标row/字段/单位且独立具名的非零oracle；raw+88准确A依赖与此普通缺边分列。

source原件在`local/migration72/d02-incremental-relic-consumer501523-source-20261116/`，manifest SHA-256 `9eab9b6dc5c640360f6cc86348de668ea14c5b646fc66c4131ca13924fcd8377`。局部proof `consumer501523-semantics.private.json`为8666B/SHA`e351b41270157dedcd82a9855d2eb338a148a829636a8d8779a65ce9ec09aee9`；最终覆盖表`FINAL_REMAINING_TYPED.zh.md`为5306B/SHA`247c5502adaee2dd5ab6ba4e72b7c766f3d5ade061a9f8ee8a0bd6f001e1c425`，均在`d02-incremental-relic-remaining-typed-20261116/`。集成只核固定身份，未重跑、重拼或测试。没有新字段值、资源行/ready或客户端验收；当前A1准确source门失败的离线归因仍进行中，不因此最终收口或交接。
