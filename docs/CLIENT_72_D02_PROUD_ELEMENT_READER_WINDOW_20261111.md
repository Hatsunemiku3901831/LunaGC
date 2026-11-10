# E-01 / R3-PROUD-ELEMENT-READER-WINDOW（2026-11-11）

## 结果与范围

按主线采纳的唯一 source 请求，取得 `52802.method439383 / 14ee1ea00` 的唯一完整原体 `[14ee1ea00,14ee1eb1d)`：285B，SHA-256 `8f64b2b9c0020c75eff11786e33d7e257d9925a08b83b8b1975de0b9524c5247`。连续解码 80 条并完整拼回原字节，低于 128 条上限。这只完成机械 source，不证明元素语法、reader 消费、成员、wire、默认值或所有权。

已读工作规范、README、Project、Harness、迁移和部署手册、[数组合同](CLIENT_72_D02_PROUD_NATIVE_STRUCT_ARRAY_CONTRACT_20261111.md)及[数组窗口](CLIENT_72_D02_PROUD_NATIVE_STRUCT_ARRAY_WINDOW_20261111.md)。私有请求为 6324B / SHA-256 `83e28adb3d957f1120570a542ba2d5ad781a1ad801a7999071af7aed953d5218`；主线已采纳授权文件为 543B / `188dcae6508f5ccc45ed066398e3cef4ac69d4ecce27ca68f8ce3d4048e92be0`；上游 root 第三合同为 207092B / `ef4347336c719c56b9546edb4ae0e86ae05b4e007e0d1b9f5243b0e1f92ff6b1`。准确地址库存此前已查，本次未重复 GitHub 或广搜。

## 原件门禁与记账

每次 fresh 以当前 `GAME_PATH` 重新核对五固定文件完整尺寸和 SHA-256；分别核对两份相同的 329B caller、当前 PE caller、指定 setup 与 `14ee1ebef e80cfeffff` 的相对目标，以及固定 metadata/signature 唯一声明。声明为 owner52802、实例方法439383、flags `0x83`、单个非 byref class42005 参数；返回类型 unknown。每次新读 ordinaryAOT cell `1422f9950`，得到 P=`1428a54d0` 后计算 P+439383×8=`142bff788`，该槽 QWORD 为 `14ee1ea00`；这里的 P 是本次读取结果，不是沿用历史值。

每次对完整异常目录实际扫描 14344080B、1195340 条，目录 SHA-256 `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`。exact 和 containing 各唯一且同一条，record RVA `1aaae508`，12B pdata SHA-256 `c3afe28dcab8d0806e2e0edfdaa3442124886e40d0cf7716f05b5d955637dfc1`。先保存 boundary 与 pre-body，再验证精确范围和大小，才读取完整本体并核独立旧摘要。异常目录是一次完整扫描，不记为仅读 12B；每次新 body 285B、AOT 两 QWORD 共 16B、选定 pdata 12B，另有 329B 当前 caller 复核及五文件完整哈希。

两 fresh 的 boundary、cell、slot、pdata、pre-body、code、asm、extraction 八原件逐字节相同。双份确定性 audit 各 13768B，SHA-256 `5a9632b259cfc673d9b7457ae9af1b54e9b53f276a059fd3fe4573fd7ffc81fe`。这不是 root 独立第三审核。原件与审核在忽略目录 `local/migration72/d02-resume-20261110b/c-proud-element-reader-window/`；审计报告保存完整指令及机械清单。

## 机械清单与未闭边界

本体含 9 个 call、12 个 branch、0 个 RIP-relative 引用、25 个显式 memory 操作数及 18 个 push/pop/call/ret 隐式栈指令。九个直接调用分别为：`14ee1ea27`、`14ee1ea6d`、`14ee1eab5` 到 `14f614760`；`14ee1eaec` 到 `14f905250`；`14ee1eb03` 到 `1404c2650`；`14ee1eb08`、`14ee1eb0d`、`14ee1eb12`、`14ee1eb17` 到 `1404c2be0`。这些仅为指令目标，不表示任何 callee 成功、语义或所有权。完整 PC、bytes、操作数、分支目标与各 memory 操作以私有报告为准。

先前同 key count2、首元素 cursor138 依赖已记录的普通文件/base inline 和先前调用正常返回条件；不能把局部 QWORD 解释为 8B wire，也不能跳到 63831 presence。元素语法及消费、第二元素 cursor、base 最终 cursor 与候选 end334 仍未由本机械卡审核。下一步由 root 对原件独立第三审核，再交语义审核按同 reader/receiver 的实际指令与必要调用边界确定准确首缺；若仍有未闭调用，须另行确定唯一 source 和授权，不自动展开 helper 树。

## 实际验证与交付

工具 `tools/client72_d02_proud_element_reader_window.py` SHA-256 `d12863956a033710411ad2b6a994bfa3d22921f3d00154a2cf4933b527e44799`；测试 `tools/test_client72_d02_proud_element_reader_window.py` SHA-256 `035db26f428bce98a210641cf4b06dc526002fa920afaf667166ecce9cf6099b`。实际执行下列命令均退出 0，测试 5 项通过，覆盖唯一/重叠/重复 pdata、精确边界、slot/完整 body 身份、断续/超条数解码与两副本同样篡改 pre-body 的非镜像负例：

```powershell
python -B tools/client72_d02_proud_element_reader_window.py --output fresh1
python -B tools/client72_d02_proud_element_reader_window.py --output fresh2
python -B tools/client72_d02_proud_element_reader_window.py --output audit1.private.json --audit fresh1 fresh2
python -B tools/client72_d02_proud_element_reader_window.py --output audit2.private.json --audit fresh1 fresh2
python -B -m unittest tools.test_client72_d02_proud_element_reader_window -v
```

输出必须使用新的任务内 basename，不覆盖已有原件。没有采集失败或安全/Daybreak 拒绝；没有读取 xdata、邻体、新 literal 或新 helper 体，也没有 target CPU、native、emulator、live、REA、进程、网络、Git、构建或数据库操作。无运行代码、Proto、配置或资源改动，无部署及客户端验收增量；D-02 仍未完成。Project/Harness、root 第三审核、本地提交与交接由主线统一处理。
