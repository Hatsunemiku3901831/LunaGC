# E-01d1 / R3-PROUD-EC8-STORE-SLICE-WINDOW

2026-11-11C，父 D02-R3。按[已审局部合同](CLIENT_72_D02_PROUD_EC8_STORE_SOURCE_CONTRACT_20261111C.md)只读取 `[0x141126812,0x141126820)` 的 14B。原字节 `488d05672f79ff488905a8e66104` 恰为两条各 7B 指令，独立字面 REX/opcode/ModRM、同寄存器和有符号位移核对与解码一致：

| PC | 局部指令 | 独立位移算术 |
| --- | --- | --- |
| `0x141126812` | `lea rax, [rip+disp32]` | `0x141126819 - 8835225 = 0x1408b9780` |
| `0x141126819` | `mov qword ptr [rip+disp32], rax` | `0x141126820 + 73524904 = 0x145744ec8` |

因此，仅当执行从第一条按局部顺序落到第二条时，该 pair 将地址常量 `0x1408b9780` 写入 EC8 槽。`localBindingProvenUnderFallthrough=true`；全局绑定、当前 callback target、自然执行和其他到达路径均未知。没有读取该地址的目标、EC8 cell、任何 callee 或执行注册。此局部结果不证明初始化、forward/seed epoch、actual encoded/getter、实际 row、resourceReady、D02-R3 或 D-02。

## 输入、预算与验证

[离线工具](../tools/migration72/proud_ec8_store_slice_window.py) 按冻结 request 校验全部来源及 decoder pin，再核五个完整 stock；request 的 `pins` 对象实际包含 **27 项**，均已核验。旧 trace 的三项内容 SHA 与当前固定输入对应；旧 run 的输入大小和生成器 SHA 未保存，当前大小没有倒填为历史实测。每个 fresh 的完整 stock hash 读取总量 550661784B，单列于新 source 预算之外。

新 PE metadata 使用两次读取，区间 `[0,4096)` 和 `[384,904)`，实际读取 4616B、去重 4096B；确认 AMD64、PE32+、preferred base `0x140000000` 和唯一可执行 file-backed section 后，先保存 `sliceRead=0` 的 pre-slice，再一次读取 14B。没有重读 exception 目录、pdata、caller、AOT、RET、邻窗、global/cell、assigned target、callee，亦无 CPU/live、runtime 模型、客户端、构建、JVM、DB 或部署。

两 fresh 四件文件逐 byte 相同，位于 `local/migration72/d02-resume-20261111c/proud-ec8-store-contract/source-window/{fresh1,fresh2}/`：

| 文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| slice.bin | 14 | `c7730f930c4f55575aaa824be335917b027790f67be9b0b5d91fcccc4f62250f` |
| slice.asm.private.json | 906 | `cfec6bd2e9f05ea797041576aafaf709932a603f94386b70410609d7a1771199` |
| pre-slice.private.json | 7208 | `75a676bbcbc622878e4b34cb247dfbb64d62eff3d701eb6bec42955109709e04` |
| result.private.json | 7949 | `d4335ed56286ba101e18be919a793dcd093ce05356b70c416340f23f3e49893f` |

[独立测试](../tools/migration72/test_proud_ec8_store_slice_window.py) 最终 4 项通过：字面合成匹配和位移、错误 opcode/load/宽度/寄存器/目的槽及前缀、控制流/短读/多余字节、pin/stock/base/非执行或歧义映射在 sliceRead=0 时停止。工具纯 artifact 审计通过；另独立只读原件、不调用解码器地核 REX/ModRM、两项 signed displacement、范围、零读取标记和双 fresh 一致，`audit-independent.private.json` 905B，SHA `34b1c8f89786841aaefb80a2e2bcf2f8d24d261c274d8fcf597ea578cc61f5fb`。合成测试不作为实际 source。

## 集成第三次核对

集成执行者确认 `main-third/` 和目标审计文件不存在后，在 checkout 根执行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_ec8_store_slice_window.py --output main-third
python tools/migration72/proud_ec8_store_slice_window.py --audit fresh1 main-third --output audit-main-third.private.json
```

第一条退出 0 后再执行第二条；任何身份、映射或字节漂移均保留原件并停止。另从原 `slice.bin` 独立核两条字面编码、RIP 有符号算术、目的 EC8、同一 rax、两个 fresh/第三逐 byte 一致和局部/全局未知标记；不得把 41 个排序 PC 解释为自然执行边。第三次由集成者执行，本轮未执行或声称通过。未操作 Git index 或提交，共享索引由集成者维护。
