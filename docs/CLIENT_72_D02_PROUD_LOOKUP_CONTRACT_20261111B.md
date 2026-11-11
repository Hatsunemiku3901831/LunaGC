# E-01d1 / Proud 热 lookup 合同（2026-11-11B）

## 本轮中文计划

本包从新取得的 `[154d400d0,154d401bd)` 原237 B / 71条起步，按主线后续准确批准接收289 B准备和802 B索引原件，只解释这三段。复用已验178 B Store→1455 B TryInsert三例自有缓冲CPU结果，不重做其实现、原窗或旧组/等级/文件语法。准确问题是原lookup的必经准备、热字典查询、U32 out和AL返回，以及是否足以形成同自有map的原查询合同。

先固定lookup两fresh六原件、旧closed-AOT/metadata和本轮来源三报告。只读新原窗和相关已验材料，以原PC/hex解释值流；无新PE/code/CPU/live。若现有资料足够，立即给Terra完整输入、条件和验收；首个必要callee缺body时只给它的唯一原边、typed/AOT绑定和一个完整窗口规格，不以host成功stub、省略准备或相邻地址扩树。A/B拒绝项及错误15183fbf0路线继续隔离。共享旧专题/工具只读，主线持有Git、构建、DB及现场。

本分析只写本文和私有 `local/migration72/d02-resume-20261111b/proud/`。新增结论做两fresh离线来源审计；实际执行、静态条件和未知分别记录。客户端、resourceReady及D02不随局部出口晋级；成熟规格交回后释放max。

## 首个确定的必要边界

原 `154d400e4 e877010000` **无条件调用154d40260**，先于 `154d400e9` 读取同wrapper+18。即使已有热字典，237 B本体也不能跳过这个call。其返回后才检查+18非空并进入本地热查询；旧热Store成功没有证明此准备已执行或无副作用。

旧56行closed-AOT唯一绑定154d40260为18192.method138529 `IFIKEDMJMLC`、classInstance50463、methodInstance-1、methodSpec176222、table289807、pointer85616，slot142518030原QWORD `6002d45401000000`。该函数声明零参数，原RCX仍为同wrapper；RDX/R8/R9仅为残留，不据此发明声明参数。不得把相邻m138528/154d401c0混成这个目标。

旧5071实例的157ffc650准备合同只作比较背景，不能替代新50463实例的原字节。本包先交154d40260单窗规格，主线随后取得下述新原件。初次缺body时，没有把wrapper+28设为“已装载”或省略此call。

## 新289 B准备原源及可独立条件

主线按准确E8/AOT及唯一pdata先核、≤2048 B边界，取得 `prepare1/2` 六原件；code为 `[154d40260,154d40381)`，289 B / 82条，SHA-256 `590e4dacad6ef400a0e6e796cd8dd26f7cb32c57fcd476ab5d9b793cc05734cb`。它与旧5071准备body的SHA不同，本节重新依据新原件分析，没有沿用旧实例的code或跳过新检查。

| 新原条件 | 原路径 | 正常结果与限制 |
| --- | --- | --- |
| wrapper+28的byte非0 | `267/26b→312..319 RET` | 11条条件原指令，无callee；没有读取+18/+20或写wrapper。只说明本方法的跳过条件。 |
| +28=0，wrapper+20为null | `271/274/278/27b→30e→312..319` | 16条条件原指令，无callee；`30e`仅写+28 byte=1。没有创建/填字典或加载资源。 |
| +28=0，+20非null且String+10长度0 | `281/285→30e` | 同样仅置+28=1；具体String对象不是当前自有热样本输入。 |
| +20非空字符串 | `28b/28d`清EDX/R8D，`290→14d6e1570` | 只有这一分支调用既有loader地址。返回null直接RET，不写+28；未执行。 |

已验HotStore的自有wrapper原本为+20=null、+28=0、+18为既有dict，因此后继可以**真实执行新准备函数的null-path再查询**，无需伪造已加载或调用冷链。另可验证非零+28的原跳过路径。这是可实施的条件prefix，当前本分析未执行lookup或prepare；只设置+28非零不能代表自然Proud对象、已填map或resourceReady。

非空路径的后继另保留：loader非null后，从reader+20是否非零决定预存reader+28或byte数组+18到EBX；同wrapper与reader在 `2bb/320` 交154d40390。保存的EBX非零且处理器AL非零才进成功清理；其余分支清理后向pool尾派发，不写wrapper+28。成功清理后也须pool的实际klass+f8调用正常返回，才到30e置+28=1。reader+10..1f清零、条件释放reader+20和pool+5d60均为准确原指令，尚未执行或展开。新289 B没有读取/写入wrapper+18，不能据正常RET声称map已填充。该handler、release和pool不属于当前无path热查询的必要依赖，未取新body。

## 237 B后半的U32输出与返回

原237 B SHA-256 `d32cc67e17a5555e1d8550ecb01c93da8bfc3f69e1b1c51ca3fc5fc50bc556bf`。入口保存完整RDX pair至RDI、R8 out至R14、RCX wrapper至RSI。准备正常返回后重读+18：非空直接到169；为空才走本地allocator/Default创建分支，仍不能用空路径准备的正常RET代替字典存在。

`154d40169/16c/16f` 将同字典和同64位pair交 `1593e7870`，原E8为 `e8fc766a04`。本包只能先称它为signed-index helper；准确算法及callee体仍缺，不能用host dict或人工index替代其返回。

| 原helper返回后的本地条件 | 原动作 | 已证边界 |
| --- | --- | --- |
| EAX signed<0 | `176→195`向原out写恰4 B零 | 最后AL=0；没有预先清out，也不能推断准备或helper异常时out为零。 |
| EAX signed≥0 | 检查dict+10数组及unsigned index<array+18；`188`算5×index，`18c`读 `entries+20×zeroExtend(indexU32)+0x30` 的DWORD，`190`写原out | 20 B stride，value位于entry+0x10，与已验TryInsert原写入位置相接；最后AL=1。 |
| 非负index而数组null/越界 | `1ad/1b2`到原错误helper | 不改成正常miss或成功；异常/unwind未执行。 |

最终 `19c/19e` 对原EAX测试signed并只写AL。不得把整个RAX/EAX当0/1；负索引可留下非零高24位。全零U32值仍可伴AL=1，key为0或pair高位为1没有本地特判。这里的signed判断针对索引，不针对原64位pair。没有 `group*100+level` 算式或门槛判断。

## 新802 B FindEntry及真正hit的停止点

主线继续按唯一原E8验证固定metadata的准确tuple10194/50461/-1，取得 `index2/3` 六原件：`[1593e7870,1593e7b92)`，802 B / 208条，SHA-256 `b2262dd40a51785b5af6bfa16914976b61ead0bed05d1d9d68c171262f2a8527`。现在可准确称其为Dictionary1222.FindEntry：table193894/spec294368/pointer202446，slot1425fc320原QWORD `70783e5901000000`，等于原E8目标。首次index1因主线collector把fixedClientInputs字符串当对象，在输入门TypeError、未读body；原失败保留后修本轮私有工具取得双fresh。

在dict+30 comparer=null的分支，原hash确为 `(pair低U32*0x18d xor 高U32)&7fffffff`，与已验TryInsert相同。原bucket DWORD减1得index，entries stride20 B；hash位于entry+0、next位于+4、完整8 B key位于+8。原helper最后直接把EDI写EAX，不将所有越界值归一成-1：合法空bucket0/链尾ffffffff给出负索引；正的非法bucket/next可原样回到外层bounds错误，不能变成正常false。

hash不相等时可沿原next结束，不需任何callee。hash相等时则在7971读entry key，7976加载cell14594e6f0，797d/7981取 `class+38 -> storage[0]` 的Default候选；冻结usage732126/source403933声明这是1234 `EqualityComparer<80067>` / instance50464。热对象非null时，79d0由对象头读派生klass，79d3取klass+BE计数，79da读伴随MI，79ed实际调用 `[klass+110]` 比较完整8 B键。当前没有该准确热对象、派生klass、函数和MI的来源，不能任选AOT Equality方法、host比较器或以hash相等代key相等。

Default候选为null时的798c..79c4冷创建不进入；当前dict+30非null的自定义comparer接口分支也没有被冒充为已验自然路径。新的callee body均未取。真正(221,1)hit及同masked hash的(221,80000001)碰撞对照停在这条热输入缺口。

## 离线审核和复现

本分析只读取固定原件：237 B双fresh六件、289 B双fresh六件、旧AOT双份、metadata cache和已验HotStore三份CPU报告。五stock身份来自主线已验取窗记录，本包没有重读当前PE或执行目标。此前三例HotStore的122条/例、无models、原RET结果只读复用，不冒充本轮查询执行。

- `lookup-contract1/2.private.json` 各7521 B，26个新语义锚点，逐byte同SHA `0ced9d4f45e3d990c99081b9f34468557f389efdcb881c9bc2a81ec4ccf0dd92`。这是取得准备body之前的237 B分析epoch，保留原未闭状态。
- `lookup-prepare-contract1/2.private.json` 各11678 B，31个准备锚点及两条无callee条件路径，逐byte同SHA `cd73d6dba0f12376e6de08940dc707aff8f62aa4d0e0812c591e4a025759449f`。完整来源重新核SHA，原asm连续覆盖、pdata/slot及声明准确绑定；没有运行旧collector或CPU。
- `lookup-index-contract1/2.private.json` 各9323 B，36个索引/热相等边界锚点，逐byte同SHA `50bfa653a29a05a1f1723dcb4469b352ba5903c856db1e867dbca6fbf708742b`。仅新802 B和固定来源审核，未执行其算法或相等虚call。

全部报告、审核源码及请求位于忽略目录 `local/migration72/d02-resume-20261111b/proud/`。使用尚不存在的basename：

```powershell
python -X utf8 -B local/migration72/d02-resume-20261111b/proud/lookup-contract-audit.private.py --output <new>.json
python -X utf8 -B local/migration72/d02-resume-20261111b/proud/lookup-prepare-contract-audit.private.py --output <new>.json
python -X utf8 -B local/migration72/d02-resume-20261111b/proud/lookup-index-contract-audit.private.py --output <new>.json
```

## 可立即实施的原热miss

主线已接受的机器合同 `hot-lookup-miss-case-contract.private.json` 为36416 B / SHA `4231abaa5331649247f78145d34f8a98ffb12ea2a96cbacbecc251fd2a383aeb`，配套完整中文规格已交Terra。五个原body共2961 B，只用现有代码和自有buffers；删除A/B拒绝产物后仍可独立完成。

先必须原执行一次已验Store，把(221,1)→22101写入自有map；不能复制预期entry到目标内存。随后保留map连续原查：221/2（同bucket1、hash不等、nextffffffff）、221/3（bucket0为空）、221/80000002（完整64位高位键、hash不等）。三个条件原路径分别109/87/104条，第一prepare由原null-path置+28=1，后两次由原byte直接skip，均不触Equals。完整寄存器/XMM/flags、允许PC/原E8/读写域、1000指令/次及总4000预算、原RET/sentinel、4 B out及相邻guards、map不变和AL0/EAX低32ffffff00均已冻结。

这只验合法map的条件原miss；正越界不得返回host false，模型调用须为空。两个fresh及主线第三通过后才可批准 `originalConditionalHotLookupMissExecuted`。本分析没有执行该合同；原热hit、实际Proud行和producer不随它晋级。

## 热Equals所缺的准确输入

已只读检查plain顶层23份JSON及已有warm/String五件，共28文件，逐项完整SHA保存在 `hot-equality-existing-input-review.private.json`（14500 B / SHA `b7b18a0d3a7b6b4bc568bc07600646cd8408c25d0e02c72ef770bb8194227d50`）。该声明/根记录范围中没有同modulebase下B+594e6f0的读取，也没有typed80067/instance50464输入。已有UInt32/String/enum20060 warm或fileRouter Default不是本对象；没有拼接。此为有界缺输入结果，不声称全客户端或全本机不存在。

私有 `hot-equality-missing-input-spec.zh.md` 给出同epoch最小7读/50 B：`B+594e6f0 -> class+38 -> storage[0] -> object[0]=K`，再读K+BE的2 B计数N、K+110的8 B函数、K+110+N*8的8 B伴随MI。须带固定模块/来源及原始读取状态，不拼不同epoch；null/短/错即停下游，不调用cold factory或置Default slot。F若有多重AOT共享声明才另交M的最小56 B身份补项；在实际function/MI来源出现前，没有合格的唯一新Equals code窗口。

## Proud原payload完整性复核与其余剩余项

Scene新证据表明部分location的B只是首压缩块边界。本包只读复核Proud与PrimaryIndex既有来源：两者均来自bundle178/block25539185、location A=0/B=0，旧提取并未按B截断。固定原decoded header的logicalBlobBytes=physicalFileBytes=25793006，dataOffset2321+最后compressedEnd25790685恰好total，546块/tail0；uncompressed71555980与本次重新SHA核对的完整serialized文件一致（SHA `0311cb19ace1cac5475f197043af14b33657f22c9915e93eb9bffa54cae5e8c7`）。Proud span[46722364,48855142) 2132778 B/SHA `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835`，PrimaryIndex span[48955772,48987630) 31858 B/SHA `f87adf6f8b1006ccc99483af251b9d491737bf8bc87b4c7097bb893301e8eb05` 均保持。未重提取、重跑旧语法或读取当前客户端；复核4171 B报告SHA `60648646a3406740492831c056f5bb986c8d6937162b03f622db85a53c38b590` 留私有目录。该完整性结论不批准active revision/runtime。

即使后续原查询在该自有map返回22101，也只证明条件map算法的这一次命中。真实Proud行仍须由同源原parser/对象状态给出221/1，原producer用同枚举ID填map，并将其查询输出接回同一22101行；固定文件条件setter输入和人工调用不能代这条原行链。自然receiver/interfaceOffset/vtable、81127+10与同Avatar突破值的比较/采用、全121 Proud及Java算式仍未验。错误15183fbf0路线的更正继续保留，不执行新扫描。

本包新增CPU/live/JVM/DB/进程/网络/构建/部署均0；无安全拒绝，也未触A/B被拒产物。`actualLookupExecuted`、`actualPairMapHit`、原Proud行构造/producer、门槛、resourceReady/完整Avatar/D02均false。公共旧专题/工具保持只读，Git及Project/Harness由主线统一处理。成熟miss实现合同与热相等输入缺口交回，分析槽不等待取数。
