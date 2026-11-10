# F-03d3f4d2：真实 head block 捕获与单次原生 pop 副本合同

**可以冻结一个可实施的单次 pop 子合同，当前没有真实 block，不能执行。** 它用同epoch实际 List class、TLS context、record及完整head原bytes支持原指令写入；不以host分配器、零页或预制对象返回成功。原构造器有20个直接allocator调用site、12个class usage cell，**单块不能验收完整Avatar构造**。本卡不改当前13/340或18/369 collector，只给主线/Sol xhigh一个新的有界实现proposal。

## 固定源与准确尺寸

旧fresh完整入口报告 SHA `73e55971e1ab3785e9a13b7619bedda89da9a81fbcd548c740bfec90e60a6754` 已完成105条，止 `1405337b9`；旧heap只作规则。新epoch必须重取R1九项312B及全部TLS/record/head输入，不能搬旧指针或寄存器值。

| 来源 | 完整SHA-256 |
| --- | --- |
| 原420B allocator `1405336c0..140533864` bytes | `c6b7251e6025e0accc5fc41e2083852d59a67671875a338738bd3c922250f1a2` |
| `allocator-full1.private.json` | `ee0e3d4b24cbd235b177b8ee7e6edfd20bd49feac89e1bda79b8d759d08274e2` |
| 含98条原bytes/指令的 `full-first.private.json` | `dc7dd494b8c49cf761b5fabe6cd9c4819b99eec0a5ae7cea9f888f73dc6240d1` |
| 312B callee `140563cd0..140563e08` | `8ce320df866d328376e990489907ab22f973e2d087ee7c1e0c415c8fb8ee3a7c` |
| 该callee原12B pdata | `728a683ac9a02a1cdc4c502411b450a9431ef4643351f3e84037135b961f1fc2` |
| 该callee manifest / 74条原bytes | `5bc6ecb1349c6f42f884a98ff1d8634855ff6dded0d813b441ec26d36d1b296c` |
| 1661B完整Avatar入口 `15183fe60..1518404dd` | `1b6d394af556f8647e6ee911cd32de784078ab64e29fb9fa37d3368019cd0f5e` |
| 入口metadata/指令声明 `avatar-initializer-observations.json` | `1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf` |

420B与完整入口来源相对 `local/migration72/d02-parallel-20261109/r1-allocator-successor/`；312B相对 `local/migration72/d02-resume-20261110/a-tls/`。TLS_SPEC `81c85ace479805daf2b41eeacb2311b09d2b08d2f24dfd3ac7f654bc43b3cfe3`、Avatar_TLS_SUCCESSOR `7152c163fb8e8db5ae77d40055ba9579597c5c0dfba4b54fe7bd23831f89bef9` 均保持冻结。

原 `140533793 0fb786b4000000` 读取 **class+b4的U16**，`14053379a movzx edx,ax` 得到请求大小。历史值为32；新epoch仍须从实际208B头取此U16，不把collector附带的U32展示值当native宽度。原 `14053379f` 读class+8低3位，后经setne及OR产生tagged class；旧路径是实际K OR1。新值必须来自原分支，不强设CA、类型低位、C7或tag。

312B入口明确RCX=C、RDX=request、R8=taggedClass；R9被原 `mov r9,rdx`覆盖。size32按原bsr/shift得到bin2：leading-zero56、shift4、index2；`140563d16 shl rcx,4` 后，`140563d34 498b4c0a28` 从 **`C+28+2*10h = C+48h` 读8B指针R**。R不是内嵌在C+48的record。随后 `140563d39` 读 `R+20/8 → H`；`140563d6f 4c8b4118` 读 **`R+18/8 → L`**，L用于QWORD计数增加，旧head bit08置位时还作为清零形状调用的 `L-16`。**原窗没有证明L=32，也没有证明L≤208或≤8000。** `8000`只比较请求size，不是record长度上界。

## 一次完整block读取的条件

本proposal只处理原class请求仍为32、tagged List路径仍由原代码选出、C/R/H均有真实非零来源的有限分支。依次保留同epoch `B+5eef080/4 → TEB+58/8 → vector[index]/8 → M+19d0/8`。index0合法；指针0、short/error不造页、不选别的线程或TLS槽。

先实读R+18得到完整U64 L。只在 **`32≤L≤208`** 且实际H非零时发出唯一请求 **`read(H,L)`**，请求长度就是L，不是固定208。32来自本卡支持的实际class请求；208沿用现有单读上限作为本新profile的拒绝阈值，**是预算政策，不是native尺寸证明**。L超界或小于request时保存实际QWORD并停止，不截断、补齐、分块、换head、重试或扩大cap。

该完整原span一次供给 `N=block[0:8]` 和 `oldFlags=block[8]`，替代15/82方案中的 `head+0/8`、`head+8/1` 两次首读；所有其余bytes都是原始存储，不以header字段重建整个block。N为0合法，不跟随N，不声称已取下一节点或完整freelist。完整read成功仅证明这段bytes可读取；结合C+48/R+20/L得到的是“该记录声明的freelist head span”，不是GC独占所有权或无writer证明。

建议新纯collector顺序为：原TLS四项及R1九项通过 → `M+19cc/1` → `C+48/8` → `R+18/8`及长度门 → `R+20/8` → `R+10/2` → `C+8/8` → `B+5699680/1` → `M+19e0/8` → 其pointee+a0/1 → `H,L`完整span。依赖指针0或首个读/语义失败即停首遍扩展；E=`M+19e0`为0不能当notify bit0清除。flags或counter真实0合法，原state byte不要求初始1，U16 lock计数允许实际wrap，不猜字段业务名。

成功的raw span在整个首遍后统一final，仍读原地址/宽度，包含完整 `H,L`；任何错误、漂移或重叠矛盾即partial并停止，不重新追指针。R、H、L相关cell及原class头也要final一致。两遍相同不能排除ABA、并发修改或跨字段非原子性；只有如实标记的稳定候选快照，不能称原子分配时刻。

若另保留旧head两次读，必须逐byte核其与完整block的重叠，再把所有成功unique统一final；不能用一次读值覆盖矛盾或声称同时取得。所选profile**不保留这两个冗余读**。新图也不能拼接已结束的18/369报告后再称同一epoch事务。

## 首遍与统一final预算

15/82去掉head两项9B，再加完整L span，得到 **14读 / (73+L)B**。R1九项图为312B；当前合并Pool的共同TLS前三项已经在本图内，不重复取。Pool额外最坏仍5读29B，`M+1990`与Avatar `M+19d0`互不代替。

| 新profile | 首遍上界 | 含统一final总上界 | L=32时首遍；总计 | L=208时首遍；总计 |
| --- | --- | --- | --- | --- |
| Avatar TLS/record/block独立 | 14读 / 73+L B | 28读 / 146+2L B | 14/105；28/210 | 14/281；28/562 |
| R1九项+Avatar capture | 23读 / 385+L B | 46读 / 770+2L B | 23/417；46/834 | 23/593；46/1186 |
| R1+Pool+Avatar capture | **28读 / 414+L B** | **56读 / 828+2L B** | **28/446；56/892** | **28/622；56/1244** |

完整组合单读上界仍208B。表中total是首遍加final；失败请求也记账，成功unique最多一遍final，无重试。若旧head两项仍存在，组合将是首遍30读/(423+L)B、总60读/(846+2L)B，不能沿用所选28/56预算。当前collector仅有13/340、18/369两个profile，**本卡没有修改或授权第三profile、live或任何CPU预算**。primary身份API账本仍单列，合并不增加ThreadBasic或换线程。

## 只读回放与六个精确副本写域

只读回放保持旧captured-write拒绝：C非空时，callee首先在 **`140563d2d c682cc19000005`** 写 `M+19cc/1`，必须在该指令前停。没有先读record再偷偷允许state写。C为空的本卡分支不再扩展；若已有足够入口输入执行到原null路径，首写是 **`140533858 488988d0190000`** 向 `M+19d0/8`存原LEA得到的 `B+5699688`，随后原jump回call。此处没有已审“初始化成功”callee可供host替代；不能因为固定global地址存在就虚构其record。null初始化/slow分配若需要后继，必须另有实际global/context bytes及实际callee合同。

拟议captured-copy-write仅允许在**与捕获原件分离的离线bytes副本**上发生以下native写入，权限按真实地址/宽度绑定，不放开整页、整个record或整个block：

| 原site | 唯一可写span | 必须由原指令得到的结果 |
| --- | --- | --- |
| `140563d2d`、`140563da4` | `M+19cc / 1B` | 实际初始byte保存；先5、后1，保存两个事件 |
| `140563d46 66f0ff4110` | `R+10 / 2B` | 原lock-inc，`(oldU16+1) mod 2^16` |
| `140563d4e 48894120` | `R+20 / 8B` | 写实际原 `N=H[0:8]`，不能以host新节点替换 |
| `140563d59 488906` | `H+0 / 8B` | `taggedClass & ~7`；另核是否等于该轮实际List K |
| `140563d6b 44884608` | `H+8 / 1B` | `(taggedClass & 7) OR 实读 B+5699680 byte` |
| `140563d73 4d014208` | `C+8 / 8B` | `(oldU64 + 实际L) mod 2^64`，不能加猜测的32 |

六span共28B字段权限；state写两次，正常fast路径七个写事件。R+18只读，H的其余bytes只读；旧block的 **`[H+9,H+L)`** 必须逐byte保持（若实际走未审clear边则在call前停）。captured原文件、真实目标、class头、global、其他record/next block均不可写。null分支额外 `M+19d0/8`不并入本最低非空C合同，不因这条写可审就批准初始化成功。

原件每个span及完整block保留SHA；replay装载副本前后再次核原件未变。读权限只覆盖真实捕获bytes、已固定代码及明确owned栈，未知地址在原指令前拒绝；不得把映射周边或旧heap当输入。不生成第二个block、对象header、TLS state或host分配返回。

未来验收应保留原始initial/final bytes、每次native写PC/地址/宽度/old/new和原计数。真正非零dirty证明来自实采bytes，例如实际非零free-link被原class指针覆盖；其余实际非零header/payload也原样保存，不能为测试注入脏值。若block实际全0，应明确 `dirtyNonzeroBlockDemonstrated=false`，不能声称通过dirty覆盖；非零 `R+20=H` 的来源和计数更新可独立证明单次pop数据流，但不替代block脏字节证据。全零样本也不许换head或重复登录页求通过。

单次pop验收核：初始 `R+20=H`；初始block内N被原 `mov`写到R+20；实际word计数、counter加L、class/header和state事件全符；只允许上述writes；原 `140563dbe ret` 的 **RAX=实际H**，真实返回到 `1405337dd`。不能先把H放入host返回寄存器再称pop通过。只证明一个源link被消费，不证明N的节点身份、整个free链或GC所有权。

## 未审分支与已补的outer必经函数

oldFlags的bit08来自**完整block原byte8**，不是store后的新flags。若置位，原pop/header/counter写已经发生，随后在 **`140563d85 → 141fdf9c0`** call前停：RCX=H+10，EDX=0，R8=L-16。清零形状不等于API/body已验，不能用旧metadata clear模型或host清零继续。保留已发生的副本dirty写与state5，不冒称返回或偷偷回滚成成功。

若实际notify byte `E+a0` bit0置位，原state已恢复1，在 **`140563e01 → 14056c050`** call前停；显式RCX=B+56cee0，其余formal参数未明。H为0或请求大于8000的原slow路径在 **`140563df5 → 140563e10`** 尾跳前停，不能补对象；该窗没有替slow边恢复state5。

pop返回后，420B caller的准确后继是：

- `1405337dd mov rdi,rax`；`1405337e0 test byte [class+ca],1`。bit0置位时 `1405337f3 e8f8b40200 → 14055ecf0`，显式RCX=H、RDX=`B+4c42c0`地址，return `1405337f8`。body未知，必须call前停。旧CA=62只是bit0清除的历史例证，不强设新值。
- 随后 **`1405337f8 mov rcx,rsi`；`1405337fb e80026ffff → 140525e00`**，return `140533800`。显式RCX=实际class K，RDI=H。RDX/R8/R9的残留值不宣称formal参数。caller随后 `mov rax,rdi`，不消费该callee返回值。

主线本轮已独立核五stock并提供这**唯一**post-pop原窗：`a-avatar-postpop-window/code.bin`，`140525e00..140525f99`、409B/108条、SHA `cd03c4e7f6d80a3f5f1d3f3744f0013dead6bdec82615559db73b25b8bd39e14`；pdata原12B `005e5200995f5200a06a5005`，SHA `960a22a35f97650cc63a14b932140ff58565946be12539db1c2164edb0c71859`，unwind5506aa0，未取xdata。提取proof SHA `619e3b6fb12786aa7766d305d98d285b03429ba5af579ab858500590422de912`。本分析代理没有新提PE或执行它；先前“待提单窗”需求由主线这个原件解除，不再要求第二窗。

该窗 `140525e15 80b9c700000000` 只比较 **K+C7 byte**。实际非零走原15条入口/epilogue至 **`140525e26 ret`**，没有class/heap写或额外runtime输入，C7已在九项R1的208B头里。若本轮批准原代码CPU，且真实C7非零、CA bit0清除及两条callee内可选边均未取，可继续原outer `14053380a ret` 回 `15183fe89`。这给出了可实际前移的准确出口，而非必经host stub。

实际C7=0则首次额外读在 **`140525e2a → B+5bc90f0/8`**，当前未采，必须在读前停；若以后另行取得该cell，下一条 **`140525e31`** 是经 **`B+3bac1f0`** 的未知IAT call，call前停。该窗后面的C6/C7/C40原子写、OS形状call及异常路径均未获权限，不清C7、不借owned锁API、不把fast-return当初始化语义。当前没有进一步窗口请求。

## 完整构造确实还需要什么

即便首个outer返回，原ctor下一新增源已经固定：**`15183fe92` 读 `B+5b74628/8`**，随后 `15183fe99` 写 `H+10/8`，`15183fe9d`把H写Avatar this+70。这个空数组形状usage、其身份及H+10写均不在本卡六写域中，不能拿旧空数组fixture或全零payload补齐。

之后原 **`15183fea1`读 `B+5897cf0/8`，`15183fea8`第二次call1405336c0**，return15183fead，中间没有可省掉此call的成功分支。其class头、请求大小、tag、bin和实际record均未由当前R1九项提供；不能假定会pop已知R或已取N。后面还有明确的泛型/子构造调用，不能按分配次数给它们host成功。

对已有1661B原窗逐个call字节重算，得到20个直接allocator site、12个usage cell；这不是动态20次pop或同一bin需要20块的证明：

| class usage RVA | 原 `call 1405336c0` sites |
| --- | --- |
| `588ca20`，本卡List<uint32>源 | `15183fe84`、`151840256` |
| `5897cf0` | `15183fea8` |
| `58fd7c8` | `15183fec8` |
| `588f388` | `15183ff21`、`15184029c` |
| `58fd7d0` | `15183ff45` |
| `58fd7d8` | `15183ff81`、`15183ffc1`、`151840004` |
| `58fd7e0` | `151840044`、`151840082`、`1518400c0` |
| `58fd7e8` | `1518400fe`、`151840176`、`1518402c0` |
| `58ed020` | `1518401ee` |
| `58a31c8` | `15184022c` |
| `58a31c0` | `15184023b` |
| `58d6e70` | `15184027a` |

同List usage后面还有一次明确分配；若usage/class/线程context在受控执行中保持本轮值，则该class再次请求32，首块不能同时充当第二块。中间其他类是否同bin、各callee内部是否继续分配、N链长度及真实所有权未知，因此不能从20site推出可采N。当前准确第二对象缺口是 `B+5897cf0` 的同epochclass及其原allocator输入，不是盲跟首节点N。本卡不展开这12图或后继链，也不让用户完成单块采样后才被告知“完整ctor原来要多块”。

primary只用作有来源的受控离线上下文时，可验“指定primary快照的真实record/head，在离线副本上由原指令完成一次pop和已闭fast后继”；必须保留 `naturalAvatarAllocatorThreadObserved=false`、`naturalPoolAllocatorThreadObserved=false`、`gsBaseRegisterReadDirectly=false`。只使用owned ABI已验证的TEB+58对应关系，不扩展全部GS/线程/GC语义。受控选择能支持这个子验收，不能证明自然构造或活堆所有权。

若完整验收要求自然Avatar生命周期，准确未取producer是 **18163 `.ctor`入口15183fe60的真实this及实际caller/线程tuple**，以及其 **15183fe84→1405336c0（return15183fe89）→1405337d8→140563cd0（return1405337dd）** 的同次调用关联；应从明确业务构造trigger下的这些已固定site取得来源。当前没有这个trigger/tuple，不从SDK页任意线程选择来替代。若主线验收限定为原构造的受控执行，仍须闭合上述第二对象、空数组、20个直接site及实际经过的子构造/间接边；已有owned768B this不得改称自然Avatar，本卡不批准生成新的this或对象。

## 本卡实际验证与可执行交付

本轮先写独立中文计划。两次标准库源审计均exit0：核27个完整pin、420B/98条和312B/74条逐byte连续覆盖、44个关键bytes site、真实pdata、metadata入口、R1九项及U16尺寸、原bin2、177个可接受L的预算公式、409B/108条主线新窗、20个原call及12个usage根。没有运行emulator、Windows backend或原指令。

两报告各23139B，逐byte相同，SHA **`c522c0fc42d7eb79aa122a5bed980dd3f1a345287c1371f8c89a402731f31e14`**；私有根 `local/migration72/d02-resume-20261110/a-avatar-capture-contract/` 保存脚本、argv/UTC/exit/stdout/stderr、双报告和determinism。可由主线独立第三次核对：

```powershell
python local/migration72/d02-resume-20261110/a-avatar-capture-contract/audit-source.private.py --output local/migration72/d02-resume-20261110/a-avatar-capture-contract/audit-main-third.private.json
```

本代理新PE、CPU/replay、live/API、GAME_PATH、网络、Git、构建均0。主线提供的409B新窗单独记源，不冒称本代理提取或CPU通过。旧docs/tools/collector/原件/pin、Project/Harness/资源均未写。

规格可交Sol xhigh：先实现上述单span真实长度捕获、唯一final、固定预算和严格输出，再另审captured-copy-write及原代码CPU；实际block/dirty bytes/自然所有权目前仍未取得，所有权限proposal保持false。单次pop和已闭outer fast-return是本卡验收上限，完整allocator其他分支、完整Avatar/Pool/Scene/g2/D-02仍未通过；本卡立即交回，不继续扩通用runtime或现场。
