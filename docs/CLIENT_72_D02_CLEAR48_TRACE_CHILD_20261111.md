# CLEAR48 自有 witness child：有界实现计划

任务：F-03d3f4d2/CLEAR48-TRACE-WITNESS-CHILD。先固定四个私有 JSON 与 root 唯一共享头的 SHA，逐字段消费共享 ABI，不修改头或定义 Windows 布局。只写本卡 child、汇编及本文；不构建、不启动进程、不执行 CPU/调试 API、网络或数据库。安全/Daybreak 事件立即停止，不重试。

1. 汇编以字节定义约定的 wrapper 和四个 leaf，wrapper 使用真实 x64 FRAME/unwind，导出头声明全部标签；逐字节静态复核与相对偏移。
2. child 严格验证 CLI、绑定本进程/线程/image/RVA/NT_TIB 和 run ID；W1/W3 不进行 CPU 探测，W2 只在支持条件满足时调用两次 CPUID 和一次 XGETBV。真实拥有的单页全部 poison，再按固定案例初始化三块数据。
3. 调用前 READY；真实返回后记录 continuation、返回值和完整块，CreateNew 写原始 JSON。W3 不捕获 UD2，也不写正常结果。
4. 只做静态文件和字节核查，记录源码 SHA 及未构建/未执行边界；交 root 统一编译、PE/unwind 审查和 CPU 验收。

## 实现与交接口

- 输入：`interface.v1.private.json` `0230c5212b0f1645345069455716e50a1635b575d6708670f03cf0bd2b7ae832`；`witness-cases.private.json` `1250c0bdc83182de47b89b33981d08b741679e8ac68b4978e6014cbf1932be99`；`event-schema.v1.private.json` `5cc14c09ac7682101f2af9f052920b503a9b7e7e79c7771d877eae4f6f5fb461`；`frozen-inputs.private.json` `a965f6309a988ea221b4789e02e794a17c9616090c1534a460ae9e11049627f5`。四者均为同一合同私有目录下原件；共享头 `tools/client72_d02_native_trace/shared.h` 的 SHA-256 为 `689c722e78d116d8de876238eea125958d289a1c2b7971ad7b34baa3cacb61b1`，仅读取消费。
- 文件：`tools/client72_d02_native_trace/child.c` SHA-256 `e813928be9f452bc24f8f651c74a1c5246b83570cf88678ba33b0f386bd08f85`；`tools/client72_d02_native_trace/witness.asm` SHA-256 `f49449b12da6a294c6b6d24dead479f39366bdbcb8926f29dbd8cc01b1dd3e19`。
- 命令形状：`child.exe --case 1|2|3 --run-id 32位小写十六进制 --output 新结果文件`，参数顺序固定；parent 传绝对新文件。run ID 转为 16 字节且绑定 PID、TID、image、代码 RVA、`NT_TIB` 栈范围。W2 在已支持 XSAVE/OSXSAVE/AVX 后读取 XCR0；失败停止，绝不尝试不受支持的 XGETBV。成功路径两次 CPUID、一次 XGETBV；W1/W3 无此探测。W2 reserve 65536、commit 一页、对整页先填 `a7`，再初始化 3×96 字节及六侧 guard；descriptor 区分 reserve 与已 commit 域，只有三块为操作数。
- `c48-child-result-v1`：`caseId`、`pid`、`tid`、`cpuidCount`、`xgetbvCount` 和 `exit` 为整数；`runId` 为 32 位小写十六进制；`imageBase`、`returnValue`、`xcr0` 为 16 位小写十六进制；`continuedToC` 为布尔值；`cpuidLeaf0`、`cpuidLeaf1` 各为四个 U32；`blocksBefore`、`blocksAfter` 为 288 字节完整小写十六进制，W1 均为空串。before 在调用前实际复制，after 在真实返回后读取。W1 的零 CPUID 次数表示 xcr0 和 CPUID 数组是未观测的 descriptor 初始值，不能解释为实测寄存器。报告 `exit: 0` 是写入时拟正常退出，真实退出由 parent 的进程退出事件核验。W2 的原始 RAX 不被解释为 D。输出 `CREATE_NEW`，W3 不捕获 UD2；意外返回时失败退出且不写正常结果。

## 静态检查与缺口

独立文本解析实际 `DB`：wrapper 25B、return 11B、YMM 23B、UD2 3B、XCR 13B，逐字节匹配冻结合同；wrapper Arm/Call/After/End 相对偏移分别为 16/17/20/25。wrapper 的 `.allocstack 28h`、`.endprolog` 以 `PROC FRAME` 标记真实 unwind 意图，但 PE 中生成的 unwind、标签/RVA、编译告警和链接结果均尚未验证。

本卡未调用编译器或构建，未运行 CPU/进程/调试 API，未检查实际输出、真实 CALL/RET、YMM 保存或异常收尾；无客户端验收，不等于 F-03d3f4d2 完成。root 按冻结输入统一编译、source/PE/unwind 审查，再独立授权执行与验收；本卡未执行 Git 提交。

## 主线源级审核与独立 artifact 门

上述源码 SHA 是 Terra 交回 epoch。root 发现 CPUID maximum-leaf 检查原先位于 leaf1 调用后，已将其移至 leaf1 前；正常路径仍为两次 CPUID/一次 XGETBV。当前 child SHA 为 `f063b3a7d5ee34720139a97ec295d3daa7c3162c4b4cf832d049b4d0c446629d`；旧源码按唯一差异逆变换后与 Terra 原 SHA 完全相符，单独保存且明确标记为重建原件，不冒称修改前复制。ASM/header 未变。主线源级审核 SHA `cbbda65b163adc2f7192299df7ceb85e33fb4b976a622cf02a74a9cece678f77`；尚未编译或执行。

[离线 bootstrap 工具](../tools/client72_d02_trace_artifacts.py)仅从真实构建后 PE/map 生成 128B 原 bootstrap，不创建进程。独立 Terra 的[8组人工 PE/map 测试](../tools/test_client72_d02_trace_artifacts.py)及主线复跑通过，覆盖精确布局、64位地址、SizeOfImage/文件长区别、source/标签/代码/权限/输出拒绝。测试发现旧版本接受空/缺/重复源清单；原红测与旧工具完整保留，root 固定五源精确集合后全绿。当前工具 SHA `ad8a2bb2e52ace70d95b3795914040fa4859631730c974764b70f62dd65550b4`，测试 SHA `8bc76ffc7d3e87ae8ee3bdcdb99b629f2612bf4907447a39913281f4b6a8c8dd`。

这些是纯门与源审核，人工映像没有编译或执行，不能代验实际 MSVC/SDK/PE/unwind、parent API、W1/W2/W3 或原 904B。实际构建和 native 结果另行验收；生产服务无需重启。
