# F-03d3f4d2/CLEAR48-NATIVE-CAPABILITY-SPEC：单条原AVX的自有EXE规格

## 中文有界计划与当前范围

本卡只规划，不实现工具、不编译或执行任何native/目标CPU，不操作client/live/PID、共享构建或Git。C-mode包已经交回，只读。先读当前AGENTS/README/Project/Harness/迁移/部署及CLEAR48-OWNED/BACKEND、UNICORN-AVX-SOURCES、GC-DEFAULT-STOCK-CAPTURE；复用旧`a-clear48-backend/github-plan.zh.md`、官方12调用台账和主线MSVC14.44.35207四工具完整hash盘点。

新stock I6/T0=524288原条件唯一选AVX；固定Unicorn单条已invalid，官方源审已说明准确拒绝，故不重跑或改I/guard/model。本6B是已有任务的有界后继，没有新GitHub仓库/分支复用方向，不重做大检索或Terra职责。下一实现采用本机已有C+ml64+Windows SDK/CRT，不安装/引入第三方库，不依赖默认PATH的rustup。

1. 固定原helper904B SHA `34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31`、原site141fdfab2/offsetf2的完整6B`c4e37d18c001`，及旧失败/CPUID/112pin原件SHA；只读现有原文件，不重取目标窗。
2. 仅为准确ISA与Windows ABI/SEH/API合同补必要primary来源。独立于已用完的Unicorn12调用账：本卡最多8次web工具调用、最多10个具体URL读取与2条定向search query；限Intel官方ISA/编译器文档与Microsoft Learn官方API/ABI，不泛搜仓库或二手教程。先记录必要性/URL/检索预算，再把实际URL/抓取时间/内容边界/SHA留本卡私有目录；固定源已足够则不追加。遇平台安全门禁/Daybreak立即记停止，不重试/换模型。
3. 给出可机械实现的独立Windows x64 EXE：baseline C只做来源/host CPUID门禁与SEH/报告；ml64保留原6B，显式owned YMM输入与前后快照、可核load/store/VZEROUPPER/RET；说明ABI、原指令与wrapper写域、异常和退出。host CPUID缺XSAVE/OSXSAVE/AVX任一即不执行XGETBV/AVX；XGETBV(0)仅有条件只读，不改CR4/XCR0、目标配置或CPU特性。
4. 来源前后/PE原bytes与解码、独占原件、一次call/超时/exit、必要的不同依赖负门禁分别列清。当前只冻结规格和来源manifest；实现、两fresh及主线第三执行需主线另批。原6B能力通过也不等于完整904B/helper/捕获副本/自然GC/Default/完整Avatar/D-02验收。成熟后交Sol xhigh/可用Terra并释放max。

## 当前状态

计划已写，尚未实现或执行新EXE。下文结果仅在来源核实后填入；原失败/112pin保持只读。
