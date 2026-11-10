# E-01 / R3-PROUD-NATIVE-STRUCT-ARRAY-WINDOW（2026-11-11）

## 结论与边界

唯一授权的静态源窗已取得：52802.method439380 / `14ee1eb20` 的唯一 pdata 完整体为 `[14ee1eb20,14ee1ec69)`，329B / 88 条连续指令。它在本体内有一次同 reader 的直接调用 `14ee1ebef → 14ee1ea00`；该目标是下一文件消费者源，**没有采集目标体**。当前窗不能确定调用返回后 reader 的 cursor/array，也不能确定 63831 本体 presence 起点、候选 end 334 或完整 row。不能据此批准原生对象、allocator 返回、自然 backend/mode、实际 map 或 D-02 客户端验收。

本卡遵照上游已授权单窗，不新增 GitHub 检索。请求原件 5528B / SHA-256 `23420dfa373c10d4f4a9998cf7511bd3e0dfdf3bc7fa8e7f37938330b804188f`；上游主线第三 100731B / `2814615c93d8ebfafdd20c64832012c5f623dadcaafcdadb37aa2c2060b552f2`。两份上游 caller 完整 2097B / `bfdb81e46bcd151507ccc2ae8d2c58e71c1922551be67b19f67a73b785958785`；每 fresh 对照当前 PE caller、七条 setup/branch/E8 及相对目标，并复核固定 metadata/signature 唯一声明，返回类型 unknown。

## 门禁与结果

两次新 Python 进程分别通过游戏路径重新解析、五项固定输入完整尺寸和 SHA-256 以及源材料 pin。完整文件哈希读取不代表打开其它代码窗。每次重新读取 ordinaryAOT cell `1422f9950`，本次新得 P=`1428a54d0`，再读 P+439380×8=`142bff770`，slot QWORD=`14ee1eb20`；未复用旧 P。异常目录完整选择扫描实际 14344080B、1195340 条，SHA-256 `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；exact 和 containing 各唯一且同一条，pdata record RVA `1aaae514`，原 12B SHA-256 `ca2875edd83d270add89e476218e25db193b9671e6bf0026f466f2fbed597cd7`。先保存边界和 pre-body，检查 ≤8192B 后才读唯一完整 body，并要求 ≤2048 条连续解码与原字节完全拼回。

两 fresh 八原件逐字节一致，body SHA-256 `de050d512bbd5446892a4b68e226f17af504607d6617a18ee2f1675699b89573`。确定性双份原件审核报告 16984B / `16b255d425747d86cffdf941472e04c48b32ff9edec15fea5b63e8f90b88ecaf`；这不是 root 独立第三审。原件在忽略的 `local/migration72/d02-resume-20261110b/c-proud-native-struct-array-window/`，报告保留全部 PC、bytes、操作数及 12 call、14 branch、4 RIP 引用、23 显式 memory 操作数和 25 个 push/pop/call/ret 隐式栈指令。没有读取 xdata、邻 body、literal 或新 helper 体。

## 限定机械关系

| PC / 原字节 | 本窗可见操作及边界 |
| --- | --- |
| `14ee1eb2d 4d89c6`、`eb30 4989d7`、`eb33 4889cf` | 保存 incoming R8、RDX、RCX；此前 caller 将同 reader、this+10 byref、R8D=0 传入。 |
| `eb36..eb56` | R8 非零分支直接继续；零分支经 RIP 全局和 `[rax+7510]` 赋 R14。相关调用/加载未执行，不断言 allocator 有效。 |
| `eb66..eb75` | `[rdi+20]` 非零时，RCX=同 reader、EDX=4，call `14f614760`；此 helper 未审。 |
| `eb7a..eb9e` | 从 `[rdi+10]` 指针及 `[rdi+1c]` 下标取 DWORD，`[rdi+1c]` 加4，值异或 `7aef1999`；原检查仅比较起始下标与 `[array+18]`，不把它改写为全宽 EOF 结论。 |
| `eba5..ebd1` | 异或结果零走 `ec17`；非零路径 call `1405336c0`，并以返回值、EBX、R14、R9D=0 调 `155fb6570`，随后写 `[r15]`。只报告机器关系，不假定返回或分配成功。 |
| `ebe0..ec13` | 栈局部清零，`RCX=&[rsp+20]`、`RDX=同 reader`，`14ee1ebef e80cfeffff` call `14ee1ea00`；正常返回后取 `[r15]` 并 call `155fb6be0`，ESI 加一并与 RBX 比较回环。该元素调用可以影响同 reader；其行为与完整消费尚未知。 |
| `ec17..ec27` | 零分支从全局结构读取值写 `[r15]`；不赋予对象/业务语义。 |
| `ec38..ec67` | 原直接错误/初始化边分别调用 `1404c2be0`、`1404c2650`、`140525e00`；仅列机械调用，未执行或合并为统一 EOF。 |

本窗还包含条件跳转、RIP 引用、调用和栈操作，完整机械清单以私有两 fresh 原件及报告为准。所有前置调用正常返回只是条件，不是本卡实际结果。未读取候选 payload 或调用其他目标；不得在 cursor 134 猜读本体 presence。

## 测试、复现与交付

新工具 SHA-256 `8b76dfb4496eb4857e425e8133353d65c6f68ae6c8723eafbb9e3a7ea3f9e65f`；测试 SHA-256 `bfbb582d1c45cda4c3faf9d5c339ed32048e4ae13cc4890d0be86c99a88c3942`。分别执行以下两次 fresh，再独立审核，均退出 0：

```powershell
python -B tools/client72_d02_proud_native_struct_array_window.py --output fresh1
python -B tools/client72_d02_proud_native_struct_array_window.py --output fresh2
python -B tools/client72_d02_proud_native_struct_array_window.py --output audit.private.json --audit fresh1 fresh2
python -B -m unittest tools.test_client72_d02_proud_native_struct_array_window -v
```

最后一条 5 项通过，覆盖唯一/重叠/重复 pdata、代码上界、slot 不匹配、断续与超条数解码、人工调用清单，以及两份相同但 pre-body 被篡改的非镜像负例。已有输出不可覆盖，复跑必须使用新 basename。没有失败采集原件、安全或 Daybreak 事件；没有 target CPU/emulator/native/live/REA、进程、网络、Git、构建、数据库或部署。仅独有工具、测试、本文及私有目录有写入；Project/Harness、第三审核和提交由 root 独占。客户端验收增量 0。

下一可执行动作是 root 对已留原件做第三审，交语义审核确定准确缺口；任何 `14ee1ea00` 新源采集都须另行授权和单独边界，不由本窗自动扩大。
