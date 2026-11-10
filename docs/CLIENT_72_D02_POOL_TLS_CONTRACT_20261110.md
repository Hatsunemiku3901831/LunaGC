# F-03d3s2-pool / F-03d3f4d2：Pool TLS 分支门合同

**现有 Avatar 13读340B合同不能补齐 Pool 的下一输入。** 两者共用 loader index、`TEB+58` 与 module TLS 基址；Pool 原 `14050a8c1` 读取 **moduleTLS+1990/8B**，Avatar 原 allocator 读取 **moduleTLS+19d0/8B**。这两个字段、pointee 布局及写域不能互换。13/340 目前是独立 collector 的实现范围，不在本卡改动，也不声称其已完成游戏采集。

本卡选定一个后续可直接实现的最小出口：**条件只读 Pool 分支门诊断**。独立最坏首遍8读49B，含统一 final 上界16读98B；若主线以后另批与13/340在同一采集事务合并，增量最多5读29B，合计首遍18读369B、含 final 上界36读738B。这些是新 proposal，当前未批准 live、CPU、capture 写或 collector 扩展。该诊断一次回答分支和准确停止点，不沿数组、链表或对象继续递进采样。

## 冻结证据与本轮强度

原 Pool 两 fresh 报告仍各4174B、逐 byte 相同，SHA `3a650294fbd979a775f98e01db480a85bc6784edff157113c98aa0e10ebcc811`。mode1 原 `1470687c0 → 14050a890` 已执行到 `14050a8ae` 之前，未执行 TLS index 读，未消费 class46314 头，也未返回分配结果。mode0 是独立控制，不改写为自然路径。[原 Pool 验收](CLIENT_72_SCENE_POOL_CONTINUATION.md)

| 冻结来源 | 完整 SHA-256 |
| --- | --- |
| `scene-pool/native-windows.private.json`，原3窗反汇编 | `76f75b7057432b6a37f868becb94b02cd60005dbc83e62b87b2438c14dc4dbfb` |
| mode1 `14050a890..14050ad50`，1216B，保存311条指令 | `7b3f2a829a31d0f3aaeaa16dc46628a996c41d8431779f9d06bddc108234c73b` |
| 32B leaf；174B mode0 窗 | `98a93ee052b474915ef1212a25986b49326f99a9f91f23530bb52b0196432861`；`4ebcea02f7a4568b35fc295eb99d1cb93f1d01d1cef2a74124b0998d76db07d2` |
| 原 TLS_SPEC | `81c85ace479805daf2b41eeacb2311b09d2b08d2f24dfd3ac7f654bc43b3cfe3` |
| Avatar TLS 单窗专题 | `7152c163fb8e8db5ae77d40055ba9579597c5c0dfba4b54fe7bd23831f89bef9` |
| owned v3 两 fresh / 六 query 原报告 | `829fde6fea17a4b6733a0e92776bed5f508a5e5fec5e7ec431875c02258c2f79` |
| owned v3 主线独立 raw 审核 | `88d59287628a883f62a372acfc40e902076455fc27fa4ccbb2e4c37bc80ebd46` |

Pool 私有根为 `local/migration72/d02-resume-20261108/scene-pool/`。原提取脚本只保存地址、mnemonic、operands 和代码 SHA，**没有保存 Pool 机器码 hex**。本轮可核冻结文本、三窗 pin、46条关键指令及其分支关系；不能从反汇编重造原 bytes 或声称重新核了1216B机器码。后来的 `r1-interface-sampling/offline-source-proof.json` SHA `98c6d4eec1d998017c16e913f095cae24d5036e5db04f5d74cc7a2fa61cdef31` 已独立记录该1216B同一代码 SHA；本轮复用此事实，没有重读 PE。下文 RVA 由保存的下一指令地址加保存的 RIP displacement 得出，不称原 rel32 bytes 独立复算。

owned ABI 仅证明当前机器两次自有 x64 进程中 class0/TEB/身份/GS58关系相符。它不证明游戏 primary 是 Pool 或 Avatar 自然 allocator caller；旧 SDK 登录页和旧 heap 均不能提供新 epoch 的线程或对象输入。[owned 实际范围](CLIENT_72_D02_OWNED_TEB_ABI_20261110.md)

## 从共用链到 Pool 条件字段

记本轮模块基址为B，有来源且在采集前后核对的同一线程 TEB 为T；V为该线程TLS vector，M为目标模块TLS，P为 `M+1990` 的实际 QWORD。

| 保存的原site | 指令原文 | 实际消费 |
| --- | --- | --- |
| `14050a8ae` | `mov eax, dword ptr [rip + 0x59e47cc]` | `B+5eef080/4`，unsigned loader index |
| `14050a8b4` | `mov rcx, qword ptr gs:[0x58]` | `T+58/8 → V`；owned ABI为地址对应提供限定证明 |
| `14050a8bd` | `mov rax, qword ptr [rcx + rax*8]` | `V+index*8/8 → M` |
| `14050a8c1` | `mov r14, qword ptr [rax + 0x1990]` | `M+1990/8 → P`；13/340缺少此字段 |
| `14050a8c8 / 8cb` | `test r14,r14 / je 14050a9d7` | P为空走全局分支；非空才解引用P |
| `14050a8d1` | `mov r15d, dword ptr [r14]` | `P+0/4`；完整U32暂存，业务含义未知，不命名为TID |
| `14050a8d4 / 8d9` | `test byte ptr [r14+8],1 / je 14050aac5` | `P+8/1`，只检查bit0；其他bit原样保留 |
| `14050a8df / 8e4` | `cmp qword ptr [r14+18],0 / je 14050aac0` | bit0置位才读 `P+18/8`；原后文用它作head-array基址 |
| `14050a8ea / 8f1` | `mov r8,qword ptr [r14+20] / ... / je 14050aac0` | 仅上一字段非零才读 `P+20/8`；这是完整U64循环界限 |
| `14050a932 / 935` | `cmp rdx,r8 / jae 14050aac0` | RDX为递增64位索引，使用unsigned比较；不得截成U32或当pointer |

`P+20` 的零值表示原分支进入 `14050aac0`；任意非零大值只记录原U64，不按其值分配数组、截断、钳制或扩大读取。`P+18` 非零也只记录地址。本次不读head-array条目、`P+40`备用数组、节点或头。

P为空的两个精确模块量是：

| 原site | 模块RVA / 宽度 | 条件与下一个原边界 |
| --- | --- | --- |
| `14050a9d7`，`cmp qword ptr [rip+56bddf9],0` | **`B+5bc87d8/8`** | 原值为0直接到 `14050ab6b`，随后 `14050ab6e call 1405336c0`；非零才读下一byte |
| `14050a9e5`，`cmp byte ptr [rip+56bcd15],0` | **`B+5bc7701/1`** | 非零到上述fallback；0到 `14050a9f5 call 14050a210` |

第一QWORD在此处只与0比较，即使非零也不解引用。不能用其他epoch的全局值、PE映射默认0或常量代替。

## 选定实现：一次条件分支门诊断

后续交Sol xhigh的范围是一个独立纯collector步骤：使用现有ledger和同次已核身份的B/T，依次读取index、`T+58`、TLS slot、`M+1990`，再严格按上表条件读取。若与13/340另行合并，只复用同一事务内前三项原始成功记录；`M+19d0`继续属于Avatar，不能充Pool字段。本卡不改已有四tool、旧TLS_SPEC或其审批状态。

输出每项实际地址/来源reader/请求宽度/原bytes/错误与分支；给出下表准确 `stopBefore`，不调用目标函数，不写目标或捕获副本。P非空时 `P+0` 先于flag读取，不因为暂不理解其含义就省略原消费。后续纯分类测试可使用明确标记的人工输入，但不得叫自然Pool图。

| 实际条件 | Pool独立首遍 读/B | 独立含final最坏 读/B | 相对13/340新增首遍 读/B | 合并首遍；含final最坏 读/B | 准确停止点 |
| --- | --- | --- | --- | --- | --- |
| P=0，全局QWORD=0 | 5/36 | 10/72 | 2/16 | 15/356；30/712 | `14050ab6e` 外部call前 |
| P=0，全局QWORD非零，读取条件byte | 6/37 | 12/74 | 3/17 | 16/357；32/714 | byte=0停 `14050a9f5`；byte非零停 `14050ab6e` |
| P非零，flag bit0=0 | 6/33 | 12/66 | 3/13 | 16/353；32/706 | `14050aac8` 外部call前 |
| P非零，bit0=1，`P+18`=0 | 7/41 | 14/82 | 4/21 | 17/361；34/722 | **`14050aac0` 首个P写之前** |
| P非零，bit0=1，`P+18`非零，读取U64 bound | 8/49 | 16/98 | 5/29 | 18/369；36/738 | bound=0停 `14050aac0`；非零停 **`14050a8f7` 首个未采全局读之前** |

独立最大单读8B；未来合并最大单读仍208B，来自已有Avatar头而非Pool新读。表中“含final”为首遍加统一末尾复读的总上界，final单独最多为对应首遍成功unique的同样读数/字节数。拒绝、失败、短读仍计首遍账；成功unique只复读原地址和原宽度，无重试、无新指针展开。末尾必须核原bytes与最初相同，不将多次读取称原子快照或无writer证明。

所有零值逐项处理：

- loader index=0合法，照常读 `V+0`，不是成功判据。V或M实际为0则保留partial，在首次需要其pointee前停止，不读零页。
- P=0是合法原分支，读上述全局条件；不是“allocator未初始化”语义结论。
- `P+0`=0合法且完整记录；flag byte=0或bit0清除合法，选择原 `14050aac8`，不改flag。
- `P+18`=0、bound=0均为合法读值，预告原byte写入；只读诊断停在写前。非零bound不是pointer。
- 全局QWORD=0或byte=0均按原条件分类；短读/错误与真实0分开，未知分支不得猜测，不从失败分支继续读其他候选。

共用线程证据只可复用同次保留的 CreateProcess primary handle/TID、创建时间与前后ThreadBasic核对。Pool步骤无需增加NtQuery/API调用；若不是同一事务或原handle已丢失，不拼旧报告、不枚举替代线程。输出必须有 `threadSource=launch-primary-thread`、`naturalPoolAllocatorThreadObserved=false`；只有另有准确自然caller来源才能改变该结论。primary候选的实际读值最多支持“该primary当时的Pool TLS状态”，不能叫自然Pool allocation。API账本仍与RPM分开，既有cap不因本表扩大。

若合并，所有Pool首遍必须放在共同final之前，重新审核完整ledger/身份门禁；不能在已冻结13/340报告结束后追加读取再称同一快照。旧Pool mode1、class46314头和旧Avatar/List heap仅提供规则。此诊断没有取得新Pool模式、目标类或调用帧；原入口的新epoch native消费仍须同epoch重新绑定必要Pool输入，不能把旧heap接到新primary TLS。

## 窗内随后读写与外部边界

选定诊断不会越过下列边界。列出它们是为了拒绝误用Avatar的写域，**没有授予Pool capture写权限**。

`14050aac0 mov byte ptr [r14+8],0` 是bit0置位且head-array为空、bound为0或清理循环结束时的P字段写。它写的是P的+8，不是 `M+19cc`。flag bit0清除时原流跳过这个写，直接 `14050aac8 call 14050a210`；P为空的两条call路径在call之前也没有对P或M的写。函数入口的push/栈局部写另属原owned栈，不当作TLS状态。

非零head-array和bound会在 `14050a8f7`、`14050a8fe` 分别读 **`B+5bc7758/8`、`B+5bc7798/8`**。随后以64B stride读counter DWORD+0与override DWORD+1c，用unsigned `jbe`跳过无需处理的项。只有需要处理才读 `[P+18 + index*8]/8`；必要时另读 `P+40/8` 及备用条目。最早可能发生的捕获数据写包括 `14050a986 xchg qword [备用数组+index*8],rcx`、`14050a9b8 lock cmpxchg qword [node+8],rdi`，或 `14050a912 mov qword [主数组+index*8],rcx`；后者随后有 `14050a919 lock add dword [counterRecord],ebx`。这些条件、节点和竞争结果均未采，不循环展开、不清标志、不迁入Avatar record/head/header模型。

P为空且选 `14050a9f5` 后，返回slot被用于64B全局record；首个窗内后继写为 `14050aa12 lock cmpxchg dword [records+slot*64+20],ecx`。slot来源的callee未在本卡审计，不能凭类id或猜值产生它。P非空在 `14050aac8` 返回后也会依据原slot扩展两个vector、pop真实head和CAS对象+8；本轮在call前停，未获得节点尺寸、完整对象bytes或所有权。

| 窗中外部边 | 精确site / 显式输入与当前限制 |
| --- | --- |
| `14050a210` | `14050a9f5`、`14050aac8`；RCX=原R12完整class指针。返回RAX作slot使用，其算法/body未知；不得以classid或Avatar bin2替代 |
| `14050a4e0` | `14050aae4`、`14050aaf3`；RCX=P+10或P+38，RDX=slot+1，R8D=0。是两个容器扩展形状，未审实现，不建vector成功模型 |
| **`1405336c0`** | **`14050ab6e`、`14050ac0d`**；RCX=原class。确为已有420B Avatar allocator同一入口，原bytes SHA `c6b7251e6025e0accc5fc41e2083852d59a67671875a338738bd3c922250f1a2` 已冻结。这只复用callee身份/指令；Pool class46314不等于List class，不沿用size32、bin2、旧类头或分配结果。若实际进入此边，再依原callee条件消费 `M+19d0`，不是前述P布局 |
| `14051a870` | `14050ab85`、`14050aba6`、`14050ac24`、`14050ac46`；RCX=class，RDX=栈cursor地址，初值0；循环返回记录并检查+2f bit1和+2e，条件调用 `[record+8]`。记录、回调身份及副作用未知 |
| `1404c2e30` | `14050ad03`；RCX=class；窗外body未审 |
| RIP间接call | `14050aa2e`经 `B+3bac1f0`；`14050aab5`与`14050ac8f`经 `B+3bac5f8`，显式RCX取 `B+5bc7780` QWORD。冻结材料无对应import绑定，不能命名为已验Windows API |
| 其余间接call | `[rax+8]`在 `14050abc2 / 14050ac62`；`call rdx`在 `14050abfc / 14050acb4`；`call rax`在 `14050acb0 / 14050ad0a`。全部保持未闭 |

窗内公共出口为 `14050acee mov rax,rdi`，经原epilogue到 **`14050acff ret`**。只有真实经过的外部函数、节点操作和原分支均已闭合，才可称返回；本卡没有执行这些路径。已有Avatar六处写域与15/82前沿也不会批准上述Pool全局counter、数组或QWORD对象头写。

## 实际静态验证与交回

本轮只写本专题与独立 `local/migration72/d02-resume-20261110/a-pool-tls/`。先写中文有界计划；标准库审计核16个完整pin、原三窗记录、两fresh4174B一致、46条关键mnemonic/operand、所有20个call site、精确RIP字段、U64 bound和各条件预算；复用owned v3原件及主线通过范围，没有重跑probe。

两个新Python静态进程均exit0，报告各20007B、逐byte一致，SHA **`4fd7d618c280339d98e58b26f3b8223bb6651412f3408670a306d9965601ef8f`**；脚本、实际argv/UTC/exit/stdout/stderr、双报告及determinism在本卡忽略目录。主线可独立第三次运行：

```powershell
python local/migration72/d02-resume-20261110/a-pool-tls/audit-static.private.py --output local/migration72/d02-resume-20261110/a-pool-tls/audit-main-third.private.json
```

新PE窗、原/owned native CPU、replay、live/RPM、Windows backend API、网络、构建、Git均为0；未改旧工具、collector、原专题/pin、Project/Harness或资源。当前冻结来源足够解释本卡问题，不需要新的ABI声明或GitHub补查。

明确交付为上述**条件分支门纯collector规格**，可交Sol xhigh；是否独立扩展或与后续必要stock轮合并由主线按D-02必要性决定。本卡不要求用户再跑SDK页：在自然Pool caller未证、外部slot helper/真实节点/同epochPool输入未闭时，仅得到primary状态不会解除完整Pool分配依赖。原 `13/340` 保持冻结，所有新live/replay/write布尔继续false，Pool/Scene/Avatar/D-02父项不晋级。
