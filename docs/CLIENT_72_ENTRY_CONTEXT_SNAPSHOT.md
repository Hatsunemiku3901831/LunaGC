当前状态：第二轮ecab38c9源已完成用户配合采样，36读2232B零读错；保守partial仅因System.Object的实际kind28与kind18合同不符。该Object表示已由原生表/producer解释，原partial保留；父class1234/NativeConfig typed头/Pool精确绑定已取得，原生后继已到接口1169。各epoch与范围见末节，旧“仅准备/未执行”按对应源读取。

# D02-R1/R4：登录页入口上下文只读快照

此卡仅准备离线审核的采样器，尚未对客户端执行。自然模式、存储和场景根的当前值均未知；本工具不启动客户端。需由主线审核后，在用户新一轮就绪和唯一监测启动完成且目标仍存活时使用。

两份固定合同 SHA-256 分别为 `6503b65872f093d503c2db8668607542c0a5d3a336002e829e5aa82b924734a4`、`149c5ebcacf726009dbd028becf2d75f6caa1b2178ccf38e5566c3de26b4a45d`。五输入逐一核 profile 和文件 SHA；两合同共16个固定机器码窗口使用实际 PE 可执行文件内容复核，Scene窗口还逐字节核指令。合同不符、机器码漂移、监测器源不符或输入/配置变化均在打开目标前拒绝。监测器必须 completed/alive；PID、创建 FILETIME、映像及实际主模块与最后监测样本一致，访问权限为 `0x101010`。

模式与静态根独立读：`B+586ceb2` 一字节，0、1、其他、失败分别保留；`B+586b600` 为64位指针。根非空时读取 `+60e8` HashSet<int32> 指针及非空对象40字节头、`+6100` List<int32> 指针及非空对象32字节头。HashSet头 `+10` slots、`+18` comparer、`+20` buckets；不把这些字段当 count。List头 `+10` items、`+18` size、`+1c` version。字段来源为 GlobalVars owner11325 的53821、53824、53913；非空不证明动态泛型 class 身份。

场景四槽为 PoolCluster `+5d60`、两个注册接口 `+c608/+c6d0`、默认 String `+c698`，非空对象仅读8字节 klass；前三者 klass 非空再读 class `+0..cf`，String 不读取 class头、长度或字符。读错或部分读取停止该分支并标记 partial；未读取不是 null。最大17次、785字节、单次最多208字节。不读容器内容、接口列表、Allocate槽或字符串，不调用客户端函数。快照非原子，后续 writer 可变；即使非空也不能证明构造已执行、注册非空、World、实体或 SDK 请求。相应布尔结论固定 false，仅表示未证明。

离线验证：五输入、16窗口实际匹配，7项夹具通过，覆盖高64位指针、最大预算、根/槽/class空、部分读取、未知模式、输出限制与合同/机器码漂移。脱敏证据和测试日志在忽略目录 `local/migration72/d02-active/main/entry-context-tool/`。冻结旧采样器 SHA `2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae`，其测试 SHA `184ccce26d606f11fbc19a1700cc4ca41e09cd6df52860fd21f8a78983e340b5`；监测器 SHA `e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e`，均未修改。此工具 SHA `58148689977a1d2cbe863a4e2cf27d128d1d0199731b8f166599c55c6b8c7d7b`，测试 SHA `d320afe0ec53a4dc7d5cdf2e13e4c2a73aa1f06781664ed3e37ec9c4f165b0cb`。

仅供主线审核及满足现场条件后执行；`<新监测报告>` 和 `<新输出文件>` 必须替换为本轮实际路径，输出须位于专属忽略目录且不存在：

```powershell
python -m unittest tools.test_sample_client72_entry_context -v
python tools/sample_client72_entry_context.py --observation <新监测报告> --output <新输出文件>
```

报告含地址、原始字节、哈希和时间，只留忽略目录；进程退出或任何部分读取不返回成功。登录/入场、能力注册与 D-02 均未验收。

主线审核将旧原型的World/SDKrequest等固定false字段更名为明确的Verified结论标记，表示未证明，不把未知运行状态解释为false；除这些输出字段名外源码AST完全相同，原型源/测试已先备份，旧离线proof不冒称新执行源。新7项检查通过，未进入execute或现场；因用户随后要求本批收口后暂停，不启动下一轮实机。恢复须新ready与新monitor，不复用旧PID。

## F-03d3f4d2：fresh 父 descriptor 最小合同

此前冻结的 `sampler-planned-v6.py` 和99 span 自然样本确定以当次主模块基址 B 的 `B+5bc8f18` 解引用8B class cache，`cache+1239*8` 解引用8B class；原生 cold factory 证据在 `1404ed9c3` 首次需要父 descriptor+8 的4B，稳定链见 [handler closure](CLIENT_72_AVATAR_HANDLER_CLOSURE.md)。该类读取完整 `0xd0` 头，验证 +10 的u64为1239、+18 的u32之16..23位为18；从 +b0 取无符号32位 compact offset。另以 `B+5bc8ad0` 解引用8B registry slot，再解引用8B registry base；只在这些根和offset均非零、头身份匹配时读 `registryBase+offset+10` 的完整16B。父 descriptor 的kind/index/bits 未知，不能预设其值；不跟随其字段或读取其他父class内容。此链是已验采样器和固定原生执行证据，不把模型分配或旧绝对地址当 fresh 值。

新增最多6读256B，连同原有最多23读1041B，单读最大208B。根/项为零、offset为零、身份不符均分别记录为未取得，不按空 descriptor 解读；读错或短读保留原始结果并标 partial；未完成链使整体 partial。每次成功或失败均留逐span时间与字节，快照非原子；终点16B本身不证明冷 comparer、完整 Avatar handler 或实际入场通过。仍须同一 fresh monitor/profile/五输入、进程身份和固定窗口核验；不得复用旧地址。输出包含地址及原始内存，只可存专属忽略目录，不公开。无现场采样；NativeConfig 成员、IFix、完整后继仍未知。

根地址和间接层级来自冻结采样源及该自然样本；本次没有独立定位这两个全局根的写入 producer 或新增其机器码窗口，不能将它们表述为本次新验证的 producer。执行前仍须主线审核该证据边界。离线测试 `python -m unittest tools.test_sample_client72_entry_context -v` 的9项通过，覆盖完整预算、高64位、各链读错、空根/项、类身份不符和零offset。采样器 SHA `961b414c0e7c37c77f622ee73f985da6fbf774105a1e6de76b1d215c3511ce71`，测试 SHA `33ad6abf7e4bfbc3ee57b221310a2af2ec6fa14f7b5449902d7a2099225412c1`。运行命令沿用上文，必须使用新监测报告与不存在的输出文件。

### 原生 reader 窗口补核

固定 EXE hash 与上文一致；以下五窗现由采样器在打开进程前从实际 PE 的可执行段逐个核 SHA，连同原16窗共21窗：

| 首选VA半开区间 | SHA-256 | 有界证据 |
| --- | --- | --- |
| `140517980..1405179f7` | `b49101213851b66dcf7162f91b28b6fee7224c0c2c9c551d07109de5068dae72` | cold caller 从 GenericClass +0 取 definition index，调用 `1405269f0`，返回class存于r15。 |
| `1405269f0..1405269f5` | `bb3bd836fbc8d866dc73ea8958b74331d207192b579c09e99d87e8637454f8ad` | 跳到 `15a4d6b60`。 |
| `15a4d6b60..15a4d6c21` | `5245ce48b3f06b60e060ab85ba8d28e0dc98a43bf112b515c74a14ea1cb610a0` | `15a4d6bd4` 读 `B+5bc8f18`，`15a4d6be9` 把参数符号扩展到r14，`15a4d6bf1` 读 `[cache+r14*8]`；片段含混淆指令，不能跳过控制流推论其他路径。 |
| `140517aa4..140517acc` | `fe29c00adc8ebc21f1374ab96d7dd93cfc71985eb2782603e0589f4b5ba41a98` | caller 从该class +b0取u32且零值跳过；`140517ab0`读 `B+5bc8ad0`、`140517ab7`再解引用，加入offset和10后调用 `1404ed990`。 |
| `1404ed9c3..1404ed9d5` | `0d967f0101c7f9cbef0d4b846e8e0dcd24ee03c04760159fc134fc6e2c15067a` | 被调用者从descriptor +8读取u32并提取16..23位；这是此前首次未采阻塞。 |

冻结 `first.cold.json` SHA `a599ab66270243189c71e84cdbd791f563f3f5dcec40bfb7eccdf2afc43a12aa` 记录 caller、consumer 和实际停止；冻结 `flag-region.json` SHA `a4af071ff1036db8bc02c86af366d858bd3473bbbe826084f17874ee1a884a1` 记录cache reader指令及目标。此前已验自然13类与固定metadata逐字节对应，其中class1239的+10为1239、+18中kind为18；这两个检查是捕获样本和metadata约束，**不是上述机器码自行断言1239及kind18**。父descriptor自身的kind/index/bits依然未知。`mode-registration-static.json` SHA `40b5f1481d22c3c2fc44ce1ac2743b919b90c95b61476ed52614e67a3b5ce1e9` 另记录 `14053f09f` 向cache全局写入分配并清零的表；这说明该写入片段，并不声称已闭合全局生命周期。

本次实际固定EXE离线核对21窗成功，10项测试通过（包括新增父窗漂移拒绝）；未打开目标进程。当前采样器 SHA `e75bf02d6fdb507cf6f326cced320b7b4d030b6332b217dff1db07d81f3cad68`，测试 SHA `ea8b37e5c9c9432d754f994f3899873a8f9c5392fd14c3e35ea7fa3c9592362b`，替代上段历史hash。


## 本轮自然采样实际结果

主线完成21窗独立核验和10项测试后，在用户新确认ready下原始启动90秒，44监测样本且最终存活；随后新版采样器实际23读/1041B、0错误、status completed。原始monitor `plain/run-resume-20261108-1.json`，SHA `a48b46ab9206ec8809efc67601d406df067634d44a4230c7aad1497a5662daa2`；snapshot `main/entry-context-tool/live-resume-20261108-1.json`，SHA `c790c70f1e5882666d556ad4ba54250e8cec3e66c4874667576209b05097d152`。执行源先复制并核hash在 `d02-resume-20261108/sampling-source/`，wrapper启动前重核。源码epoch是上文e75bf02d，不把旧7/9项准备计作本次实际采样。

父descriptor16B已取得，kind21，静态注册索引405416；新旧class1239身份/descriptor片段、parent compact offset及尺寸字段一致。NativeConfig mode真实为1/true，HashSet/List对象头以及Scene四根非空；这些头不包含HashSet/List的完整typed class身份/成员，非空不证明自然注册或消费者已运行。父数据用于下一离线重定位模型，不能混写成同一进程完整cold回放。读取是分时非原子，未采字段仍未知；完整Handler/g2/Scene/SDK请求和D-02仍未验。

用户确认本次SDK登录页正常、无报错，随后从窗口正常退出；主线OS复核无目标。此次无补丁、调试器、注入或本服服务，不是本服登录验收。OS/执行源/数据保护与采样预算证明在 `d02-resume-20261108/`；不沿用此次PID到后继。


## 第二轮 Pool/NativeConfig 初步准备（历史源epoch）

按[Scene Pool准确reader合同](CLIENT_72_SCENE_RUNTIME_CONTEXT.md)追加最多7读673B，总预算30读1714B，单次最多208B。已有两个NativeConfig对象头非空才读其klass头；Pool头必须为普通class11008才读+f0，target必须精确为fresh模块+70687c0才读32B代码，按固定SHA核绑定。未知class/target不跟随，记录Observed与BindingVerified的不同含义，未执行分配/注册/生命周期。独立pool mode、type46314 cell及非空class头分开采集，null/短读保持原错误语义；没有扩大到任意池内容或字符串。

固定PE23窗实际核对通过，12项离线检查通过，覆盖高64位、预算、未知class/target不跟随、代码漂移/读取错误及原父descriptor错误分支。一项旧窗口mock起初未覆盖新增Pool窗口而失败；仅扩充合成mock与独立Pool漂移负例，生产校验不放宽。证据`d02-resume-20261108/entry-context-pool-preparation-tests.log`与`entry-context-pool-preparation-proof.json`。R1 class1234合同另待合并，当前不据30读声称已闭下一轮全部输入，也没有启动新客户端。

## 第二轮联合合同与实际结果

在上项准确Pool合同上合并[父class最小输入](CLIENT_72_AVATAR_PARENT_CONTINUATION.md)：1234/371各cache槽8B、非空头208B、身份相符后准确metadata70B；parent/declaring compact offset非零才按registry链读descriptor16B，零值记录且不跟随。最多新增10读636B，联合上限40读2350B；不读层级/接口数组，不复用旧绝对地址。新增精确reader窗使固定PE共24窗，两个metadata行SHA实际核对，14项检查通过；采样器源SHA `ecab38c9e39daf5ea48bb017cc7060c61c8b142c4f3844d37b54c3e7374f9c7c`。执行前原字节副本与核对在`d02-resume-20261108/sampling-source2/`及`sampling-source2-proof.json`，wrapper启动时重核源。

用户新确认ready后原始客户端自然启动90秒，44监测样本且最终存活；monitor `plain/run-resume-20261108-2.json` SHA `5ca5b6e5af9bba74889495426e983d5b8d55048f82091fb10dcd3ed08d732fb2`。snapshot `main/entry-context-tool/live-resume-20261108-2.json` SHA `119f8a0b3c3ecc8829eda9bf7422c3646c643afa34ac041ea69b5607a02a7384`，实际36读2232B、0读取错误、24窗已核；status=partial、sampler exit1保留，不能改写成完整成功。

- class1234实际id1234/kind18，70B metadata逐字节匹配，parent descriptor16B已取，declaring offset0；371实际id371/kind28，合同只允许kind18，因此停止该分支，未读其metadata。这是实际类型表示与采样预期的分歧，不是ReadProcessMemory失败；是否可按准确System.Object合同继续由新离线卡核对，不放宽成任意kind。
- NativeConfig mode1；HashSet/List实际klass头分别kind21/index12184与2089，对应最小合同固定startup泛型索引。头身份仍不能证明predicate消费前状态或成员调用时序，HashSet内容未读。
- Pool实际class11008、+f0 slot4及32B代码与准确`1470687c0`绑定，独立pool mode1；type46314头id46314/kind18。只证明读取时的绑定/输入，不执行allocator、ctor或实体生命周期，不据此声称注册或世界初始化。

主线逐read核宽度/hash、零错、预算、唯一partial原因及monitor身份，采后14检查通过，证明`d02-resume-20261108/sampling2-main-review.private.json`。用户确认SDK登录页正常并从窗口正常退出，OS复核目标不存在，证明`client-exit2.private.json`。无补丁、调试器、凭据或本服请求；完整cold/default/Handler、Scene发送和D-02仍未完成。新样本交后继复用，不因保守partial重启同样实验。

[fresh后继](CLIENT_72_AVATAR_FRESH_ANCESTOR.md)已证明371本来就是kind28，原跳转表直接选canonical Object且该支不需要新371metadata；不更改本次冻结sampler或原partial。跨epoch受控重定位后，原指令已发布1239/1234两个类壳，实际首缺推进到cache1169，其条件后继1249也有准确固定接口行来源。下一最小合同只采这两个cache/head及按需验证，不重采旧Array/Type或371头，不泛化放行任意kind28。
