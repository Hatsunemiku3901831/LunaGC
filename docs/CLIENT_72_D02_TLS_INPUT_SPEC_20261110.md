# F-03d3f4d2：TEB owned ABI 独立实现出口与TLS输入规格

**当前可以独立实施的是x64自有probe验证，不需要启动游戏，也不依赖完整collector/replay。** 本卡已固定Microsoft SDK和phnt声明来源；ThreadBasic布局仍是待owned实测的候选。主线可直接将下列owned范围交Sol xhigh，编译并执行两fresh后审核。所有游戏live、13/340、15/82、block采集、native replay与capture副本写权限继续未批准，不因owned ABI通过自动放行。

## 固定来源

本机Windows SDK10.0.26100.0：`um/processthreadsapi.h` SHA `436ae4cbb017d1cdb381aaf2119a3f89b04bae257fd4c881ef251daa02533540`给PROCESS_INFORMATION与线程身份/创建时间API；`um/winternl.h` SHA `a43424486349c38f697c009512dfe4eb8fca733d7665afab42f50752170b9785`给NtQueryInformationThread原型、CLIENT_ID的两个HANDLE及LONG基础类型，未给ThreadBasic声明；`um/winnt.h` SHA `8693f0ad4ada355c912d40ca41ce929b480e92291bda2f9227acf56736bffbff`给NT_TIB与AMD64的NtCurrentTeb实现：通过`__readgsqword(FIELD_OFFSET(NT_TIB,Self))`返回当前TEB。路径和原文片段在忽略证据，公共文档不写机器SDK路径。

主线已完成Terra max有界检索，唯一phnt公开分支master tip为`53fbbdc5b5d2b08761db1c7b26bfa8c820924356`、tree `afe5bee33acd5802bc56affb8bd791b4d7f22e93`。7调用在10分钟内，三原文223KB量级、MIT、仓库外no-checkout clone只含.git且pack2220B；未编译/执行。它是NT声明原始项目，**不是Microsoft对当前OS ABI的保证**。

- [ThreadBasic声明与原型](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/ntpsapi.h#L1767-L1775)，原文件180987B/SHA `3477b3fa901715c5ad139ae154f86d6c36f3415962731d6c0de0dd3c8417f4d8`。同文件首枚举给ThreadBasicInformation的C枚举值0；不是由hide flag的11推导。
- [TEB前缀](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/ntpebteb.h#L1037-L1067)，42029B/SHA `9869d45323706a6bf7cb3ed168b4ca6560aef194f1c6a4d37aaad74a356ad248`，依次为NT_TIB、EnvironmentPointer、CLIENT_ID、ActiveRpcHandle、ThreadLocalStoragePointer、PEB pointer。
- [MIT原件](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/LICENSE)，SHA `ad2ab542c56c606e4c19d66a7f3cdcd3ef83beffb638e7c3893b22c7a6a6c0df`。复制声明时保留相应notice，不批量vendor上游依赖。

完整原件在`local/migration72/d02-resume-20261110/a-tls-preparation/abi-retrieval-20261110T001446Z/`：manifest SHA `0bb50a41fe14f61b26ac69603f0c92a597bdaf3e2c69ecf2c00089bf645e5941`，result SHA `382be666a0f91353495d474d0091a7ac9578c91973df025613a008304e8c646d`，ledger SHA `0d1c62d6dc1de854ce929270fea0282356728b779099fa25f125c17d27091a53`。此前C1/C2 partial/超时和所有旧pin保持，不把本次准确ABI小检索记成旧runtime全审计完成。

## Owned probe：准确实现范围

建议主线独立派工只新增`tools/client72_d02_owned_teb_probe.cpp`、Python构建/校验driver及必要定向测试，输出放独立`a-tls-preparation/owned-teb/`。不改原monitor、A/TLS工具、共享String、旧报告；不依赖当前GAME_PATH、不加载游戏代码、不枚举/打开外部进程或线程、不读取游戏内存、不安装DLL。由root/Sol xhigh核现有x64 C++工具链后实际构建及两fresh执行；本规划代理没有构建或执行owned/native probe。

只编译AMD64、非ARM64EC、非WOW64；采用SDK已有NTSTATUS/LONG、HANDLE、CLIENT_ID、KAFFINITY类型，按上述固定声明写一个独立ProbeTBI和一个TEB短前缀，不需要下载完整phnt依赖。不要设置pack1或加入保留数组猜偏移。编译与driver都必须给出真实sizeof/alignof/offsetof，并与以下**候选**一致，否则失败：

| 类型/字段 | x64候选size或offset |
| --- | --- |
| pointer、KAFFINITY；LONG/NTSTATUS | 8B；4B |
| CLIENT_ID；NT_TIB | 10h；38h |
| ProbeTBI整体 | 30h，alignment8 |
| ExitStatus、TebBaseAddress、ClientId | 0、8、10h |
| AffinityMask、Priority、BasePriority | 20h、28h、2ch |
| TEB前缀NtTib、ThreadLocalStoragePointer | 0、58h |

这些数值由固定声明、SDK基础类型和x64默认布局推导，不能只因static_assert编译通过就标OS ABI通过。NtQueryInformationThread用SDK原型动态解析，class0、输出sizeof(ProbeTBI)、ReturnLength指针；每次要求实际NTSTATUS=0、ReturnLength=30h，保存完整原30h bytes。任何错误/长度不符均失败，无布局/信息类/地址fallback。

每个自有probe进程仅一个main和一个用CreateThread创建的owned worker。用自有ready/release event保持worker活着，主线程保留CreateThread原hThread和TID。每进程恰3次ThreadBasic query：main查询self、worker查询self、main用原hThread查询该worker；不枚举线程或扫描信息类。两次fresh是两个新probe进程，总计6次query，没有额外开发preview，若实际增加必须如实记账。

两个线程各自通过独立SDK NtCurrentTeb取得实际TEB，并取实际PID/TID及GetThreadTimes创建FILETIME；取NT_TIB.Self、实际`GS:[58]`和`[SDK当前TEB+58]`。只从SDK返回的本线程TEB读自有内存，**不能先信候选query里的TEB再解引用它**。worker保存这些独立结果后signal ready并等待release；main再查询原worker handle。

验收必须全部满足：

1. main/worker self query的TebBaseAddress各等于SDK NtCurrentTeb；ClientId各等于GetCurrentProcessId/GetCurrentThreadId。NT_TIB.Self各回指自身，GS58与TEB+58读值各相等；TLS vector实际为0时原样记录，不为得到非零而造TLS。
2. main通过保留的worker handle查询，TebBaseAddress等于worker独立记录，PID/TID同时等于CreateThread TID、GetThreadId/GetProcessIdOfThread和worker self记录；handle的GetThreadTimes创建值等于worker self创建值。main和worker是两个同时存活的不同线程。
3. 不要求两次ThreadBasic完整raw bytes相等：Priority等字段可以变化；只按上述有独立来源的身份/TEB关系判定。Affinity/Priority原值保留，不猜业务值。
4. 报告明确`gsBaseRegisterReadDirectly=false`。这里只验证本案需要的GS58与TEB+58地址等价，不能升级为恢复全部GS/runtime语义。
5. release/join与关闭仅操作probe自己创建的worker/events/handles；ready/join各≤5秒，外driver单进程≤30秒。失败也保留API status/GetLastError、raw、阶段和cleanup结果，不静默重试或操作其他进程。

driver保留source/SDK/两phnt header/license pin、实际编译argv/工具链/exit/产物SHA、两fresh argv/起止UTC/exit/stdout/stderr及每次真实API结果。各fresh的PID/TID/TEB本应不同，按关系验收，不为报告同SHA而改写原件。可另做纯Python候选ctypes布局与JSON/schema负例，但不能用它们代替上述真实API出口。

本独立子出口通过后的准确结论仅为：**该机器本次x64 owned线程上，class0候选布局与独立TEB/身份/GS58观察相符**。它不证明游戏primary/natural Avatar线程、游戏TLS slot或GC pool，不授权collector/live/replay。

## Primary thread诊断：后续独立proposal

现有`observe_client72_uninstrumented.py` SHA `e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e`的launch在CreateProcessW成功后立即关闭hThread，只返回hProcess/PID；不能从其旧报告重建primary身份。其NtQuery class11只用于hide flag，不能充ThreadBasic。

后续若主线批准，新增backend wrapper在同一次正常CreateProcessW返回时保留hThread/dwThreadId，保持creationFlags0与原两个返回值接口，最终关闭原primary handle一次。原monitor不改；不Suspend/Debug、不从线程列表挑“看着像主线程”的TID，也不在原句柄丢失后fallback重选。创建句柄、GetThreadId/GetProcessIdOfThread、GetThreadTimes及process image/creation/module身份构成`launch-primary-thread`来源。

owned ABI通过后才可在主线另批的同epoch snapshot入口，对这个实际primary做ThreadBasic前/后两query，核身份和TEB一致；实际非0status/错误长度/线程退出即partial，不重选线程。新增API账本最多2次ThreadBasic、GetThreadId/GetProcessIdOfThread/GetThreadTimes各1、线程零等待检查2，加finally关闭primary1；输出buffer与API结果单列，不藏入RPM cap。**primary只证明启动主线程及该时刻TLS快照，不证明它执行了自然Avatar allocator。** 如选择其作为有来源的受控离线线程上下文，报告须明确这种选择，naturalAvatarAllocatorThreadObserved继续false。

当前最小只读TLS诊断为`B+5eef080/4 → TEB+58/8 → vector[index]/8 → moduleTLS+19d0/8`，index0合法；0pointer/short/error保留partial，不造页/指针、不走其他TLS槽。只是首遍4读28B、统一final最多8读56B、单读8B的新proposal，未执行/批准。使用原client指令中的模块TLS vector，不以公开TEB.TlsSlots代替它。无须也不允许为了此诊断单独让用户再等同一SDK登录页；可在主线已有必要的新stock轮、ABI/预算/输入都满足时再单独批准。

## 同epoch R1必要对象与回放边界

冻结fresh报告`73e55971e1ab3785e9a13b7619bedda89da9a81fbcd548c740bfec90e60a6754`的source.audit27–35已覆盖完整Avatar首105条需要的图，native180–189实际10次读52B。纯Avatar前置可以与String分开，旧epoch只提供地址来源/规则，不能提供新heap：

| 新epoch地址来源 | bytes |
| --- | --- |
| B+588ca20；实际List class header | 8；208 |
| class.GenericClass；GC.classInst | 32；16 |
| classInst唯一argument pointer；UInt32 descriptor | 8；16 |
| B+5bc8f18普通class root；root+1255*8 | 8；8 |
| B+5bc96f8原comparison cell | 8 |

九项首遍9读312B。保持definition1255、count1/UInt32 kind9/byref0、methodInst0/backref及完整64位指针，真实ca/size/比较分支原样记录；不拿closed backref替普通槽，不强设flags以走旧路径。合并上述4/28，只读首遍**13读340B**，全部成功unique在最后统一复读上界**26读680B、单读208B**。它不是旧76/3264或其他预算的自动继承，也尚未批准；ThreadBasic API另记。

后续15/82池前沿依然只给字段/指针，没有真实block，**不得执行pop**。只有实际blockSize、完整head block原bytes、前沿和同epochR1图齐全、写域与native/read预算另获批准，才可执行捕获副本上的原指令。精确可写域仅已审的TLS+19cc/1B、record+10/2B、record+20/8B、head+0/8B、head+8/1B、context+8/8B；null context原caller的TLS+19d0/8B另列。不能全页放写、预填对象、补零页或host allocator成功。真实原件和live目标均不可写。

旧head bit08、threadState+a0 bit0或空head引出的未审call/尾跳保持执行前停止，不改条件。真实block内容缺失、实际线程来源不成立、同epoch图不齐或未批准写域时，canReplayPop必须false。完整allocator/Avatar/g2/D-02不因ABI独立通过而晋级。

## 本卡实际验证与交付

本卡只读本机primary source、旧fresh/单窗证据及Terra固定原件，写新专题与`a-tls-preparation/`。初次7pin标准库静态双报告各4986B/SHA `b9ac0d8350ec321aae3534da1e226d7982da7e60d348bff0ff7ab40ba6c7a5c6`，逐byte一致，核backend丢primary事实、SDK缺ThreadBasic事实、9/312来自原27–35、旧实际52B与新13/340/26/680。该报告在phnt返回前生成，仍保留ABI缺口历史，不覆写其状态或pin；新owned合同另绑定上述Terra源。

新PE、original/owned native CPU、live、网络/clone均未由本规划代理执行。Terra检索/clone的7调用是独立实际证据，不冒称本代理执行或OS ABI成功。具体probe源码、构建、两fresh、审核/提交和共享文档索引归root/Sol xhigh。owned子出口规格已明确，本卡返回释放max；不等待完整live/replay方案或另开泛化runtime。
