# F-03d3f4d2 / GC-DEFAULT-STOCK-CAPTURE

本轮新实时 ready 后完成独立 stock 只读采集及退出核验。验收只覆盖同一 epoch 的 GC 必要条件量与 Default 原引用；没有执行原 helper、第二 allocator 或 Default 消费者，也没有证明自然 GC 所有权、完整 Avatar 或 D-02。

## 固定范围与实际结果

复用[已验准备](CLIENT_72_D02_GC_DEFAULT_STOCK_PREPARATION_20261111.md)的第六 profile、107 源 seal、原 primary、45 时点/90 秒元数据及一次采样。启动前重新解析 GAME_PATH、核五 stock/载体/13 保护文件及配置、进程和固定端口；没有 DLL 安装、客户端文件修改、目标写或 CPU 回放。预算仍为首 49/1209B、总 98/2418B、单次 208B。

实际首遍 **42 读/824B**，统一末遍复读后 **84 读/1648B**；42 个区间全部稳定。第一块 L32/flags8，第二块 request56/bin4、独立 record、完整 L64/flags8。HashSet UInt32 身份及其来源仍按当轮图核对，未复用旧地址。

GC 输入为 I=6、T0=524288、R8=48，所以原条件直接选择 AVX table；本路径不需要读取 T1 或 F。唯一原 DWORD 为 `01fdfb94`，与准确窗内 target 一致。Default 的 usage→pointer→value 三步均非零，结果只是 **opaque nonzero input**，未读取 value 所指对象，不称 comparer 身份、cctor 或 Default 调用已成功。cold 路径仍未验。

原第二块 `unknown-post-pop-edge` 标记保留；新数据组件完整并不意味着原 pop/helper/allocator 已返回。当前固定 Unicorn 后端不支持原 AVX 指令，见[后端诊断](CLIENT_72_D02_CLEAR48_BACKEND_20261111.md)及[官方源核查](CLIENT_72_D02_UNICORN_AVX_SOURCES_20261111.md)。不改 I、不走人工 SSE、不放宽 guard、不重复原失败；后续独立 owned 后端须先完成准确规格和能力验收，再讨论捕获副本。

## 主线独立核验与原件

私有根为 `local/migration72/d02-resume-20261110b/a-gc-default-inputs/stock-helper/`。`review-live1.private.py` 只用原 records 重建纯 collector，逐项核完整结果（仅忽略时钟）、原 PI24、两次 TBI48/输出 buffer96、primary/进程各关闭一次及最终 source/stock。没有调用目标 API 或 native CPU。

| 证据 | SHA-256 |
| --- | --- |
| 原 capture | `18fea5b455257a4ebde0709cb23ac96adb663033492198814bf6495842d0a840` |
| 主线独立 review | `55f26e400f0a9db3b221462c19d8b7771d37e7ea7a81519937c5112619ffd5b0` |

107 个封存源逐一核 SHA 后另存 `live1-source-snapshot/`；原 capture、计划、ready、组记录和退出回执均保留。新 ready 已消费，不能复用。

用户确认“已退出”；没有补称用户本轮已确认页面正常或无异常。退出后实际确认相关客户端/调试器/JVM/Mongo 进程及固定 TCP/UDP 监听为空，stock/载体/13 保护文件与缺席状态保持。首个退出核验 helper 已结束但无回执，启动时使用了相对 plan 参数，具体 stderr 未取得；保留该事实，改用绝对 plan 路径的退出核验 exit0。没有重启客户端、重采或强杀。

## 剩余与恢复

本输入子项完成，F-03d3f4d2 父项继续进行。下一步是准确 AVX owned 后端能力与完整 helper 写域/返回，再分别验第二分配、Default 消费、完整构造共 20 处分配/12 根及子构造的剩余依赖；自然线程与 GC 所有权独立验收。旧 cold 普通10294、enum、fresh 和 String 自然 Hash/FindEntry 的未知不因此消失。正式 Avatar g2、Scene、R4/R5/R6 均未晋级。

本次没有运行代码、Proto、资源、配置或补丁部署变更；离线工具无需重启 GS。遇到安全门禁或 Daybreak 的问题按[队列](CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md)记录后停该包，继续独立工作。
