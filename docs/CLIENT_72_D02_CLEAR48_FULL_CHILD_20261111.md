# F-03d3f4d2 / CLEAR48-FULL-CHILD-V1

主线已完成集成及两组人工 native 验收。下文保留子包交付时的状态，最终构建、BOOT 修正与验收见 [人工首组验收](CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md)。

本子包实现固定 257 SSE-long 与 258 AVX-long 的自有 child 源码与 27B 包装器。它消费 root 冻结的共享 ABI、生成的 case 数据和构建 epoch；904B 原输入只从固定忽略文件以准确长度和 SHA-256 校验后复制，不进入受跟踪源码。单个 child 只调用一次真实 helper；尚未编译或执行，设计期望不是实际退休证据。

入口固定 `--helper ABS --case 257|258 --run-id 32lowerhex --output ABS_CREATE_NEW_CHILD_RESULT`。先核文件，真实 CPUID leaf0/leaf1 和门控的单次 XGETBV，再核页大小和粒度。一次 0x06d20000 reserve；四个 4KB 页面分别提交，代码仅复制 904B 并复核 SHA，controls 依生成 case 精确写 17B 或 25B，代码页 RX、其余三页 RO，精确 904B flush。另 reserve 64KB、只 commit offset 0x1000 一页，仅写 case 的 128B；H=known+32、D=H+16。没有初始化或读取其他 padding。

描述符在 READY 前填本次 image、PID/TID、标签、栈界、页、host 原值和三项 SHA。FRAME wrapper 的源码字节为 `4883ec284989cb4889d131d241b830000000cc41ffd34883c428c3`，Arm/Call/AfterCall/End 偏移分别为 18/19/22/27，XCR leaf 为 `31c90f01d048c1e2204809d0c3`。不预写返回槽、不注册目标或更改保护策略。返回 C 后实际复制 final128、调用两次 VirtualFree 并保存返回值，再 CreateNew 写结果；写、flush 和 close 都成功且释放均成功才返回 OS0。结果文件不自证其自身 close 成功。

setup 原记录按顺序为 arena reserve、四次 commit、四次 protect、flush、buffer reserve 和 commit 共12项；release 为 buffer、arena 两项。字段的准确形状由 root 的私有 `child-api-records.v1.private.json` 冻结，失败时保存实际错误，成功时错误为 null，oldProtect 只在 TRUE 时记录。使用系统 BCrypt 对源和复制后的 904B 求 SHA，链接及导入由 root 构建审核。失败时不重分配、降低保护或重试。

纯源码字节与顺序断言已通过：wrapper 27B/标签、XCR 13B、单一调用位置、四页与 128B 范围、C 返回后释放与结果顺序、禁止 RWX/CFG/动态函数表，以及共享头、root bindings 和生成 case 的既定 SHA。私有摘要位于 `local/migration72/d02-resume-20261110b/a-clear48-full-helper-child/source-check.private.json`。这些不是编译、PE、unwind、实际 API 或运行结果；root 仍需独立审核构建/依赖、串行进行两 case 并核真实 raw 与退出。没有新客户端、GC 所有权、第二 allocation、完整 Avatar 或 D-02 验收。
