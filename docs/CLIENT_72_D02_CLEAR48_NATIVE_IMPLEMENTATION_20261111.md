# F-03d3f4d2/CLEAR48-NATIVE-CAPABILITY-IMPL

本包是独立 Windows x64 原 6B 能力 fixture 的源码和门禁准备，不是已构建或已通过的能力。未编译、未启动 EXE、未读取 host CPUID/XGETBV，未操作客户端、服务、数据库或目标进程。完整 helper、自然 GC、Avatar 与 D-02 均未验。原规格 [CLEAR48 native capability](CLIENT_72_D02_CLEAR48_NATIVE_CAPABILITY_20261111.md) 固定 18582B、SHA-256 `fdba8a6ce03a697c4d9b6512d7bbbef6616d9915eaaf9cc2516b1278d7e61790`；私有规格 JSON SHA `85add258803469ad0ce12be24a437ded217bff7f8b90a406056434bd6287d903`。

接手第一条盘点命令误执行一次只读 `git status --short`，违反主线给本子任务的“不操作 Git”边界；没有 Git 写操作，后续未再调用 Git。

`tools/client72_d02_native_avx/fixture.asm` 以 DB 固定 23B Probe、13B ReadXcr0，另有独立 `0f0b c3` witness。原指令在 Probe+8；map 公开入口、原 site、return 和 end。C 使用三个各 96B、32B 对齐的 owned block，保留完整初始/最终数据，固定输入 01..20；只在 host 两个 CPUID 和 XCR0 前置成立后调用 Probe 一次。两个入口和独立 witness 分别受 SEH 保护，filter 即刻复制异常地址、RIP/RSP、volatile GPR、image base 和 phase。原 post 是写出的真实快照，不声称逐指令动态 trace。异常发生在 Probe+19..+22 时依据固定无分支字节推断 post store 已到达；其他异常保留 false，不能解释为更细粒度 trace。

退出码固定 0 通过、10 来源/字节门或报告写入失败、20 host 前置不足、30 真实异常、31 快照/守卫/返回不符、124 parent 超时；child 超时只停止 parent 新建的那个 child。UD2 成功负证人的预期是 exit 30、illegal-instruction `0xc000001d`、异常地址/RIP 均为 witness site，且 Probe/XGETBV/CPUID 均零次。能力结果仅取每次各自实际寄存器和前置，不要求不同 run 的 CPUID EBX 相等。

Python runner 在新建的私有 run 目录先做 112 项来源及原 904B/6B 门，再用 map 标签绑定 PE RVA、section 和文件偏移，逐条以固定 Capstone 和盘点 dumpbin 解码核字节和助记符。保存两种解码文本、真实 report、两侧源/EXE/hash、stdout/stderr、PID、创建时间和 exit；失败保留 gate-failure 或已有输出。`capability` 需要此前同 PE/map 成功 witness 或能力记录。此记录链是执行顺序约束，不能代替主线独立审计。两次能力结果分别验证，并保留各自 host 值；不把输入或预期值写进 post 模拟成功。

## 实际纯检查

- `python -B -m unittest tools.test_client72_d02_native_avx -v`：5 个测试通过；谓词、合成正常/UD2 报告变异、输入漂移、输出冲突及 PE 映射/截断只使用声明数据，不构造或运行原生 CPU fixture。
- `python -B -c "from tools.client72_d02_native_avx import source_gate; source_gate()"`：112 项固定来源、原 helper 与本包四项实现源 pin 通过。PowerShell parser 无语法错误。初次 unittest 因模块导入路径错误失败，已修复并保留在私有测试记录，不是 native 构建失败。
- 这些检查不证明 C 编译通过、PE 存在、实际依赖完整、SEH/CPU 支持或客户端效果。不得把 synthetic 报告当作真实证据。

## 主线后继：先构建并审，再单独运行

私有目录 `local/migration72/d02-resume-20261110b/a-clear48-native-implementation/` 供本包使用。新 BuildId 不得已有；以下仅为主线后续命令，**本包未执行**：

```powershell
& ./tools/client72_d02_native_avx/build.ps1 -BuildId <new-id>
python -B tools/client72_d02_native_avx.py audit --exe <build-dir>/fixture.exe --map <build-dir>/fixture.map --dumpbin <盘点的dumpbin.exe绝对路径> --output <new-private-audit.json>
```

构建脚本核固定 inventory 四工具、只在新目录子 shell 执行，清空 CL/_CL_/LINK/_LINK_ 并记录 vcvars 环境、完整 cmd、`/showIncludes`、`/Bt+`、`/VERBOSE:LIB`、map、PDB、汇编列表和文件哈希；C 为 baseline，未加 AVX 或 GL。**`dependencyProofComplete=false` 是有意保留的缺口**：日志解析所列 headers/libs 与候选编译 DLL 不能自动证明实际使用的编译器组件或全部链接库路径；需独立审日志及实际依赖并逐项哈希，缺证据则停止，不得手填通过。优先用 `/Bt+` 所记编译器组件实际路径/时间核 c1/c2 等与 SHA；必要时主线对自己启动的编译器进程取模块证据，不要求穷举操作系统 DLL。还须独立核 C caller/unwind、固定实际 SDK/CRT 和源、PE 的两种解码。新构建可能因编译器警告、map/dumpbin 格式或依赖日志不满足而失败；保留日志后审核修复，不预称已通过。

runner 的 `--dependency-review` 指向新私有 JSON，含 `independentlyReviewed: true`、`exeSha256`、`mapSha256`，及非空 `source`、`includes`、`libraries`、`compilerDlls`、`buildLogs` 数组；每项是 `{"path":"实际绝对路径","sha256":"完整小写哈希"}`。该文件只能由独立审查在证据齐备时创建，runner 逐项复核文件 hash，但无法自行证明列表穷尽，主线须对此负责。

依次运行一次 witness、成功后一次 capability、成功后第二 fresh capability；每个输出路径均为不存在的新目录，全部在本包私有目录，`--previous` 指向前一个成功目录。下列命令中的占位符须由主线按实际构建、工具和审查结果替换：

```powershell
python -B tools/client72_d02_native_avx.py ud2 --exe <exe> --map <map> --dumpbin <dumpbin> --dependency-review <review.json> --output <new-witness-dir>
python -B tools/client72_d02_native_avx.py capability --exe <exe> --map <map> --dumpbin <dumpbin> --dependency-review <review.json> --previous <witness-dir> --output <new-capability1-dir>
python -B tools/client72_d02_native_avx.py capability --exe <exe> --map <map> --dumpbin <dumpbin> --dependency-review <review.json> --previous <capability1-dir> --output <new-capability2-dir>
```

任一门/运行失败保留现场并停止，不改指令、CPU、配置或换 loader 重试。证据、工具实际身份、可能编译修复及这三次真实结果均须主线单独复核；旧 112 pin、源 spec、既有工具及生产材料保持只读。本包无生产修改，不要求服务部署或重启。
