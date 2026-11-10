# F-03d3f4d2-owned-thread-abi：独立自有线程 ABI probe

主线已实际独立构建，并执行恰两次 fresh、合计六次class0查询，原始48B/SDK TEB/身份/GS58关系及清理均通过独立复核。本实现只回答本机本次原生x64自有线程的候选布局，不改变完整Avatar、游戏TLS、collector、pool、replay或D-02状态。

## 输入与声明来源

冻结合同为 [TLS 输入规格](CLIENT_72_D02_TLS_INPUT_SPEC_20261110.md)，SHA-256 `81c85ace479805daf2b41eeacb2311b09d2b08d2f24dfd3ac7f654bc43b3cfe3`。忽略目录中的 `a-tls-preparation/owned-abi-implementation.private.json` 为实现入口，SHA-256 `953252c196051d1b7ee697d54c44ba7e02ec1427ce605f5e48dca81cba9af94e`；driver 在 prepare/build/run-two 阶段核对固定输入和源码，不改旧文件。

使用已有 Windows SDK `10.0.26100.0` 的 `NTSTATUS`、`KPRIORITY`、`CLIENT_ID`、`KAFFINITY`、`NT_TIB`、`NtCurrentTeb` 及 `decltype(&::NtQueryInformationThread)`。后者仅用于类型，运行时从已加载的 ntdll 解析；没有链接或安装另一个 NT 库。

两个短声明依据 phnt 固定提交 `53fbbdc5b5d2b08761db1c7b26bfa8c820924356` 的 [ThreadBasic](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/ntpsapi.h#L1767-L1775) 和 [TEB 前缀](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/ntpebteb.h#L1037-L1067)，源码保留完整 [MIT notice](https://github.com/winsiderss/phnt/blob/53fbbdc5b5d2b08761db1c7b26bfa8c820924356/LICENSE)。没有 vendor 完整 phnt、pack1 或自造保留槽。来源只给候选声明，不能代替当前 OS 实测。

driver 固定 SDK 四文件哈希（`processthreadsapi.h`、`winternl.h`、`winnt.h`、`basetsd.h`）以及 Terra 六 pin（manifest、result、request-ledger、两 header、license）；具体完整 SHA 与前批检索/clone 证据见冻结合同和源码常量。静态发现现有 MSVC `14.44.35207`；未安装或升级工具链。

## 独立实现

- [C++ probe](../tools/client72_d02_owned_teb_probe.cpp)：仅原生 AMD64，另核 native system architecture。默认对齐，输出标量/结构的真实 `sizeof`、`alignof` 和相关 `offsetof`；与候选不符即失败。
- [driver](../tools/client72_d02_owned_teb_abi.py)：分离 `prepare`、`build`、`run-two` 和已捕获 stdout 的 `validate`。只有 `run-two` 执行 probe；第一 fresh 失败即停止，不重试。已有 build/runs 目录拒绝覆盖。
- [纯 Python 测试](../tools/test_client72_d02_owned_teb_abi.py)：合成报告明确只作校验器夹具；测试不会调用 NT API 或执行 C++。

每个 probe 保留 CreateThread 返回的原 hThread 和 TID，只创建一个 worker 及两个自有 manual-reset event。成功路径严格三次 class0：main self、worker self、main 通过原 hThread 查询 worker。每次保存实际 NTSTATUS、ReturnLength、完整原 48 字节、解码字段及 GetLastError 观察值；NTSTATUS 是权威，GetLastError 不作为 NT 错误翻译。非零状态或错误长度直接失败，无其他 class、布局或地址 fallback。

两个线程各自先从 SDK `NtCurrentTeb()` 获取本线程 TEB，读 `NT_TIB.Self`、实际 `GS:[58]` 和 `[SDK TEB+58]`；没有从候选 TBI 的 TEB 指针解引用内存。各自记录 PID/TID、当前线程伪句柄和 GetThreadTimes。主线程另从原 worker handle 取 GetThreadId、GetProcessIdOfThread、创建时间，要求与 worker 独立记录一致，ready 后零等待要求 worker 仍活着。

ready/release 保持 worker；ready 与 join 各 5000ms，worker release wait 也为 5000ms，driver 每个子进程 30 秒。失败保留阶段、实际 API 返回、状态、raw 和自有清理结果。worker 在 SetEvent 前完成的 observation 可供主线程读取；signal/wait 等后续字段只在 join 后读取，避免把 signal 本身当作后续字段发布。未 join 时记录未取得的 worker 后续数据，不关闭仍可能被等待的 event；进程正常结束释放剩余自有资源，不枚举或终止外部线程。

校验器逐项核对 raw 的完整 64 位字段、独立 TEB/身份关系、创建时间和清理 API。允许 TLS vector 为零；不造 TLS。不比较两次 worker query 的整个 raw，Affinity/Priority 只保留原值，允许优先级变化。`gsBaseRegisterReadDirectly=false`，只证明本案 GS58 与 SDK TEB+58 的观察关系。

## 主线构建与两 fresh 入口

在 checkout 根目录运行；`--output` 必须是新的 `owned-teb/` 子目录。以下命令顺序执行；build 不执行 probe，run-two 最多两个新进程，每个三次 query。主线应复用本实现已生成的 plan，不再次 prepare 或 preview。实际 plan 路径与 SHA 由私有实现交付记录给出。

```powershell
python -m unittest tools.test_client72_d02_owned_teb_abi -v
python tools/client72_d02_owned_teb_abi.py layout
python tools/client72_d02_owned_teb_abi.py prepare --output local/migration72/d02-resume-20261110/a-tls-preparation/owned-teb/<new-plan-directory>
python tools/client72_d02_owned_teb_abi.py build --plan local/migration72/d02-resume-20261110/a-tls-preparation/owned-teb/<new-plan-directory>/plan.json
python tools/client72_d02_owned_teb_abi.py run-two --plan local/migration72/d02-resume-20261110/a-tls-preparation/owned-teb/<new-plan-directory>/plan.json
```

prepare 核完整来源 pin，保存三个源码快照、vswhere 实际 argv/原始 stdout/stderr、已有 cl/link/c1xx/c2/SDK/CRT 文件 hash，生成独立 `build-owned.cmd`。该脚本只对子进程调用现有 vcvars64，指定 SDK 和 MSVC 版本，用实际绝对 cl 路径编译本 probe；没有输出完整环境或收集凭据。build 保存真实调用/exit/产物 SHA，`/Bv` 记录实际工具版本，`/showIncludes` 核编译实际使用的四个固定 SDK header。

两 fresh 各保存原始 stdout JSONL、stderr、实际 argv、PID、起止 UTC、exit、超时状态。API 错误不会丢弃原件。driver 核报告 PID 等于实际 Popen 子进程 PID，并报告 fresh 间身份差异；跨进程不要求 raw/TEB/TID 同值或异值。若全部通过，准确结论只可写“该机器本次两 fresh 的 x64 owned 线程上，class0 候选布局与独立 TEB/身份/GS58 观察相符”。

## 本实现批次实际验证与后继

最终v3计划为 `local/migration72/d02-resume-20261110/a-tls-preparation/owned-teb/implementation-20261110-sol-xhigh-v3/plan.json`，SHA `315e5b65da3e60768f92042c9d15a09e83327bd6485826ca7d090d33cee2814c`。主线使用已有MSVC14.44.35207/SDK10.0.26100.0实际cl退出0，四个固定SDK header由真实showIncludes证明。EXE SHA `1171a329b441a1a54dedbe952a7dcc3dae64f2d278902ebb67908503ddd1a008`；`runs/two-fresh-report.json` SHA `829fde6fea17a4b6733a0e92776bed5f508a5e5fec5e7ec431875c02258c2f79`，恰2个新进程、每进程3query，无preview或追加probe。主线另用独立struct解48B原件，核当前两线程关系、handle创建时间、join/exit0及关闭；`main-independent-review.private.json`记录通过。

最终9项Python测试通过。v2构建本身退出0，但driver只认识英文showIncludes，实际中文UTF8前缀导致四SDK来源判false，probe没有执行；原stdout/stderr/失败报告保留。v3只补实际中英文精确include前缀及路径规范化，未改C++；成功与原失败分目录保存。下述6测试/未构建段是实现交付历史，不代表当前结论。

准确结论只为“该机器本次两fresh的x64 owned线程上，class0候选布局与独立TEB/身份/GS58观察相符”。后继只读TLS采集器可进入明确实现，真正stock现场、目标线程选择、游戏TLS/池、捕获副本写域和native回放仍需独立合同；`naturalAvatarAllocatorThreadObserved=false`，`canReplayPop=false`。

已执行 6 项纯 Python 测试，全部通过；包含 28 个独立 ABI/身份/状态/清理/越权负例子项、48 字节与 64 位 raw 检查、非零动态 Priority 差异及 TLS=0 保留。另执行纯 Python ctypes 候选布局输出，明确 `candidateLayoutOnly=true`、`osAbiValidated=false`。未执行 C++ 编译、API probe、JVM、Cargo、Gradle、客户端、数据库、调试器或网络。

主线接手顺序为：审核冻结源码与准确 build argv → 一次独立构建 → 恰两 fresh/合计六次 class0 → 审核原件与关系 → 更新本专题和 Project/Harness 并本地提交。构建失败可以修复后重建；任何实际 probe 失败均保留并先归因，不能自动多跑来取得通过。尚无构建、产物 SHA 或 OS ABI 通过声明；没有部署要求，因为只新增独立研究工具/测试。

`liveApproved`、`replayApproved`、`capturedWriteApproved`、`R1Only13_340Approved`、`pool15_82Approved` 和 `naturalAvatarAllocatorThreadObserved` 始终为 false。本子出口不批准 primary 线程采样、13/340 图、15/82 池、任何 capture 写或 CPU/native Avatar；primary 后继 proposal 仍按冻结合同另审。
