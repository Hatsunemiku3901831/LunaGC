# F-03d3f4d2 / CLEAR48：三项自有native witness验收

2026-11-11。主线按v2退出生命周期合同完成新source、37纯门、main6真实构建、PE/caller/unwind与依赖第三审核，顺序各执行一个新W1/W2/W3并立即用独立checker验收。**三项自有debug backend witness全部通过；完整904B、自然allocator线程/GC所有权、第二次分配、Default消费者和完整Avatar仍未通过。** 前三次失败W1原件与false结论不改。

## 实际材料

| 材料 | SHA-256 |
| --- | --- |
| parent源 | `53a0a42cc8079677db3657ab81f9d06c9918fe243a2ac32d50110d4c9882db13` |
| validator / test | `50d71da7f89486c753edba030e445871137f9f551dccc837b2b2b430910f71ad` / `1e27f52b99e8bc75e8700339a4df21c5ebf99b3fe473ce56653a1c40ad5a8823` |
| child EXE / map | `78d5e180f120793e5883e8dbbcfcbaaabfbeed67560833e9fed5f9c02a584401` / `f8422956fda132dcb0d44d1a8fd46529572bd6cde62ac9b9199d10a437cb548c` |
| parent EXE / map | `ba9c7442d5b572eb0b9acc67cded81e6ec06a6ce06bd86b21d7cece1e75c9c30` / `7dd0072e2ea6e3300aad7df0ebb7f5e2b3eeff74b3d03f3dcef6610472ca5255` |
| bootstrap128B | `cd2e8696933067f31b679cf4fa1afe23df5325f4eb523eabbd54236dbbf8f0a4` |
| root PE/caller/unwind审核 | `65a3c652d5fea88b8169cf2f568f406ef23e90313e08becc85a95854b978d963` |
| root依赖审核42410B | `11641653fa70bb4a42868436ee6fcd5ef9815f5a2860b5d972946d3ee5e8d50e` |
| root三案例独立汇合 | `71824960ea1aa0f8c1542df8b46607566f970e3fb2e23c63f6de9223202f77f2` |

编译记录五源+epoch六项，另将冻结补充合同和三个实际Python工具作为完整性对象并入review.source/before-after，共10项；后四者不是编译输入。129 include、5关联链接库、2仅搜索库、2 compiler DLL、4工具/10日志逐项尺寸/hash已核。当前文件hash不代编译时独立采样；map+搜索路径关联不代逐成员加载，完整模块清单未知。

共享header与四旧JSON保持原hash，policy=`c48-owned-exit-lifecycle-v2`与补充合同SHA `cf52cfafa00eab47316a756a5e04a1edc59ca2b5606d4fa89d6cc2cb552105d8`在epoch/summary/验证报告绑定。sourceProof仍为原bootstrap字典。实际union字段/阶段/关闭责任经[独立接口审阅](CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_INTERFACE_AUDIT_20261111.md)；其中W3早Term缺口已最小修复。原W3后续metadata失败状态遗漏及普通errno22写错误沿[parent更正](CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_PARENT_20261111.md#主线集成更正)留档，没有平台安全/Daybreak重试。

## 各案例实际验收

相对私有根`local/migration72/d02-resume-20261110b/a-clear48-trace-witness/`。每次新runId、PID/TID、creation/句柄、ASLR地址和输出目录；同build不意味着复用现场身份。

| 案例与目录 | 实际结果 | 预算及完整验证SHA |
| --- | --- | --- |
| W1 `w1-main4-20261111/` | 真CALL/MOV64/RET退休3，RAX=f123456789abcdef；实际栈写/RET同槽/RSP成立；parent/child均0 | 15event/9Get/4Set/9读330B；`3e5c08e225d3a6b6b0bd6b314d9519481bb871e1608e41bd4be86c549e6fad77` |
| W2 `w2-main1-20261111/` | CALL+6叶指令退休7；原6B在真实23B leaf内运行；两笔32B store逐次old/new和288B首尾/全部guard吻合；正常回C/OS0 | 18event/17Get/8Set/20读1090B；`da7068ddfe41898fd55514616d6d5b1c9ab4e0fc5384322ae21ef47dc16672de` |
| W3 `w3-main1-20261111/` | 仅CALL退休1，原first-chance C000001D/UD2保持pending不退休；未回C/无child result；先实际fault/tx再Term90和真实drain；parent30、child90 | 9event/6Get/2Set/7读305B；`3fa72e15da1c5a4d2f1182d27f5c420cfc86dcad057980e3632c352ceb30bfec` |

三轮均严格checker accepted；W1/W2 normalTracePassed=true，W3 normalTracePassed=false且expectedNegativeAccepted=true。线程生命周期/EXIT/Continue/signal/GetExit/PI两个真实Close及cleanupComplete均通过，root receipt before=after、未超时；W3之后fresh OS查询无自有parent/child残留。借用event handle释放只标API合同推导，不能改称直接观测内核Close。

W2实际CPUID2/XGETBV1及XCR0必要位通过，CONTROL-only Set往返没有破坏本证人的向量结果；没有取FLOAT/XSTATE组或从Get声称读到了YMM。各Set保持原提交TF-only，raw回读仅已批bit1单向表示；其余位和GPR/控制值没有mask。7/8通知未在这三轮观察，只由人工边界测试覆盖。

root额外按未改raw重算Set输入/回读关系、W1非零64位值、W2两个真实32B结果和所有其余guard、W3未退休/无C结果/实际90；不是只信最终buffer或child自报。首个汇合stdin脚本将编码误写utf8-sig，在输出前停止；改为utf-8-sig后纯读汇合通过，没有重复任何native。

## 后继边界

本批没有启动/附加游戏、安装DLL、改客户端/原DB/生产服务或部署。只有离线工具、自己的受控EXE及文档变化；无需便携重启，D-02六项客户端验收增量仍为0。

下一任务：复用[完整904B既有规格](CLIENT_72_D02_CLEAR48_NATIVE_TRACE_SPEC_20261111.md)，在这三个已验backend基础上冻结新helper child/parent/validator ABI、精确原bytes/RIP控制映射/允许读写与预算，再分包实现和独立构建/owned验证。当前没有把904B链接或执行，也没有因此批准captured copy写域、自然GC、第二allocator或完整Avatar。
