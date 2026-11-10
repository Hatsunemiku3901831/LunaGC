# E-01/R3-PROUD-INDEX-HANDLER-WINDOW（2026-11-11）

## 执行前有界计划

准确问题：只取已验 prepare 原两个 E8 的共同目标 `157ffc780` 的完整原函数窗，建立机械指令及引用清单，供后续独立语义审查；不解释 loader、parser、返回或字段别名。必要性来自已定位 PrimaryIndex payload（31858B，SHA-256 `f87adf6f8b1006ccc99483af251b9d491737bf8bc87b4c7097bb893301e8eb05`）及 prepare 将同壳与 typed 42005 交该目标。

来源：固定 prepare 合同、两份 289B 原 caller、其第三审、D 第三审及原 table/spec/声明、旧工具和 helper 哈希。每次 fresh 重解 GAME_PATH 并核五文件完整大小与 SHA；核两个 E8、准确槽 `142515ed0` 原 8B。唯一 exact/containing PE pdata 为同一条后，先保存原 12B 与 pre-body 诊断，再取完整 body；上限 4096B、连续解码不超过 1024 条。只读已批准 slot、pdata、body，header 与五文件完整哈希单列，不读取其他 slot/callee/xdata/literal 或解析 payload。两 fresh 的八原件逐字节一致，离线 audit 重算并运行必要门禁测试，完整列出 call、branch、RIP、显式内存和隐式栈操作。

写入仅本文、同名工具和测试及本包私有目录；旧工具/helper 只读，不改 Project/Harness。无 CPU/live/REA、进程、网络、构建、DB、Git。来源漂移、无或多重 pdata、超过边界及安全/Daybreak 停止并留诊断；不扩 end、猜邻、换工具或重试。复用既有固定 loader/AstaPS 来源，本机械续项不新检索。客户端验收增量为零，root 第三审后另交语义。

## 待解决的执行边界

唯一性检查需要查看完整异常目录记录；本包当前指令同时将新 PE 的 pdata 读取限定为 12B。旧 prepare 工具通过遍历完整目录证明唯一性，现有该窗口诊断仅保留其目标匹配行，不能直接证明本目标的唯一性。已向 root 报告，明确前不读取额外 pdata 或目标 body。

### 预算澄清（执行前）

root 明确批准为唯一性证明扫描 PE 异常目录：每 fresh 核 DataDirectory RVA `1a4e6a40`、长度 14344080B，并计算完整 SHA，逐 12B 检查 1195340 条的 exact/containing；报告实际扫描字节数，不称为仅读 12B。只把选中目标 12B 原记录落盘，其余 slot、body 和禁止项不变。完整 EXE 哈希门另账。

## 结果及复核

上述预算问题已按 root 的明确批准解决。五输入每 fresh 均重新解 GAME_PATH 并核完整大小与 SHA；固定的 D、prepare 合同与工具/helper、第三审和 payload 原件均通过 SHA 门禁。两原 caller 各 289B，同 SHA `0c1d792245409c0df4df69beed8bd9b0a520f8a4d56d14e2fa1dd43ed2b4db7b`；`157ffc6ab e8d0000000` 和 `157ffc710 e86b000000` 均指向 `157ffc780`。D 第三审 `0bff021d1171cde4c1248370da22718887765e472c788ecfba619aa41e5ff5e3` 中原 table/spec/声明绑定 owner18192、method138530、classInstance5071、methodInstance -1、pointerIndex84548；准确 slot `142515ed0` 的 8B 为 `80c7ff5701000000`。跨不同 pointerIndex 同 code 未验。

两 fresh 每次实际扫描目录 RVA `1a4e6a40`、14344080B / 1195340 条，SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；唯一 exact/containing 为同条 `[157ffc780,157ffca5f)`，选中原 pdata `80c7ff175fcaff17146d1905`，SHA `98d3a728cbf8aba882ac52c1d3e8ca251cb2ee6204bfe79e8b1d2cfa683d45f6`。先写 boundary、目标 pdata 和 pre-body，后读完整 735B；连续 189 条，code SHA `8bfd0f4e32334c985f1fb63cbc9535d1d2a675a9b997fafb890030ebbf696b56`。仅记录 unwind RVA，不读取 xdata。

两 fresh 八原件逐 byte 相同，离线审计不读当前 PE，重验来源、边界、slot、原始字节、完整解码及引用；6 项门禁测试通过，覆盖缺失/重复/重叠 pdata、长度、slot、解码空洞及条数、直接/间接调用与内存/分支、pre-body 漂移。人工夹具不等于执行。

```powershell
python -B tools/client72_d02_proud_index_handler_window.py --output final1
python -B tools/client72_d02_proud_index_handler_window.py --output final2
python -B tools/client72_d02_proud_index_handler_window.py --audit final1 final2 --output audit.private.json
python -B -m unittest tools.test_client72_d02_proud_index_handler_window -v
```

| 每 fresh 原件 | 字节 | SHA-256 |
| --- | ---: | --- |
| `boundary.private.json` | 8219 | `c8079febe6a285959fb62330f54bb07d535bd794aa3e0633acc9170ecd569cc7` |
| `caller.bin` | 289 | `0c1d792245409c0df4df69beed8bd9b0a520f8a4d56d14e2fa1dd43ed2b4db7b` |
| `code.asm.private.json` | 22447 | `21f9103a85ddbc941d2acec4377a88ec81579c2b8528d6966d682cc703d722b9` |
| `code.bin` | 735 | `8bfd0f4e32334c985f1fb63cbc9535d1d2a675a9b997fafb890030ebbf696b56` |
| `extraction.private.json` | 35302 | `1c90b2f71ebdf374bcf9993a251aae8b064517a9a9982e8aab407def7a037037` |
| `pdata.bin` | 12 | `98d3a728cbf8aba882ac52c1d3e8ca251cb2ee6204bfe79e8b1d2cfa683d45f6` |
| `pre-body.private.json` | 8425 | `fb68709f1d28afb06d8ba60da663f97d84ac2feccff582b505f73a255927c5d3` |
| `slot.bin` | 8 | `f6380c28f63cdcd1687b1708f310032801b5538808cd71f5cf84cd7db6885cf4` |
| `audit.private.json`（离线审计） | 29862 | `88ac1e710db2952673bceeb47e66c4f878bc393b2b2fa8d26d03e26f6001100d` |
| `tools/client72_d02_proud_index_handler_window.py` | 17132 | `db07cd87ec2500364d62cb038cce999331c03273cb9fdbb21d2d20021e09fb26` |
| `tools/test_client72_d02_proud_index_handler_window.py` | 2362 | `396753ca5fa4b304ac03d2c3a0c0931d2e58dc838a986b8ae439d882bdc7bb4c` |

## 完整机械清单

以下只列解码事实；目标为间接引用时保持未知，未追踪目标。显式内存 access 数值为 Capstone 原始标志（1 读、2 写、3 读写、0 未标），不将其补为语义判断。push/pop/call/ret 单列隐式栈指令；显式栈相对 operand 同时保留在内存清单。

### Call（20）

| 地址 | 原 bytes | 指令 | 目标 / operand |
| --- | --- | --- | --- |
| `0x157ffc7c2` | `e8098ec2fa` | `call 0x152c255d0` | `0x152c255d0` |
| `0x157ffc7f1` | `e8ca6e53e8` | `call 0x1405336c0` | `0x1405336c0` |
| `0x157ffc829` | `e852ae51e8` | `call 0x140517680` | `0x140517680` |
| `0x157ffc835` | `e8f6b953e8` | `call 0x140538230` | `0x140538230` |
| `0x157ffc83d` | `e8de1eb701` | `call 0x159b6e720` | `0x159b6e720` |
| `0x157ffc87b` | `e8f0c3c601` | `call 0x159c68c70` | `0x159c68c70` |
| `0x157ffc89e` | `e8cdc3c601` | `call 0x159c68c70` | `0x159c68c70` |
| `0x157ffc8ee` | `e8dd8cc2fa` | `call 0x152c255d0` | `0x152c255d0` |
| `0x157ffc917` | `e8b48cc2fa` | `call 0x152c255d0` | `0x152c255d0` |
| `0x157ffc934` | `e8978cc2fa` | `call 0x152c255d0` | `0x152c255d0` |
| `0x157ffc960` | `e85b6d53e8` | `call 0x1405336c0` | `0x1405336c0` |
| `0x157ffc9a1` | `e88a3f3cfd` | `call 0x1553c0930` | `0x1553c0930` |
| `0x157ffc9c9` | `e8b2ac51e8` | `call 0x140517680` | `0x140517680` |
| `0x157ffc9d5` | `e856b853e8` | `call 0x140538230` | `0x140538230` |
| `0x157ffc9dd` | `e83e1db701` | `call 0x159b6e720` | `0x159b6e720` |
| `0x157ffca1f` | `e85ca1f0f5` | `call 0x14df06b80` | `0x14df06b80` |
| `0x157ffca4a` | `e891614ce8` | `call 0x1404c2be0` | `0x1404c2be0` |
| `0x157ffca4f` | `e88c614ce8` | `call 0x1404c2be0` | `0x1404c2be0` |
| `0x157ffca54` | `e887614ce8` | `call 0x1404c2be0` | `0x1404c2be0` |
| `0x157ffca59` | `e882614ce8` | `call 0x1404c2be0` | `0x1404c2be0` |

### Branch（21）

| 地址 | 原 bytes | 指令 | 目标 / operand |
| --- | --- | --- | --- |
| `0x157ffc7d3` | `7410` | `je 0x157ffc7e5` | `0x157ffc7e5` |
| `0x157ffc7da` | `0f8f88000000` | `jg 0x157ffc868` | `0x157ffc868` |
| `0x157ffc7e0` | `e9be000000` | `jmp 0x157ffc8a3` | `0x157ffc8a3` |
| `0x157ffc7fc` | `0f8452020000` | `je 0x157ffca54` | `0x157ffca54` |
| `0x157ffc816` | `753b` | `jne 0x157ffc853` | `0x157ffc853` |
| `0x157ffc856` | `7404` | `je 0x157ffc85c` | `0x157ffc85c` |
| `0x157ffc866` | `7e3b` | `jle 0x157ffc8a3` | `0x157ffc8a3` |
| `0x157ffc86f` | `0f84da010000` | `je 0x157ffca4f` | `0x157ffca4f` |
| `0x157ffc8cb` | `0f8d5b010000` | `jge 0x157ffca2c` | `0x157ffca2c` |
| `0x157ffc8fd` | `0f85f7000000` | `jne 0x157ffc9fa` | `0x157ffc9fa` |
| `0x157ffc907` | `751d` | `jne 0x157ffc926` | `0x157ffc926` |
| `0x157ffc952` | `7541` | `jne 0x157ffc995` | `0x157ffc995` |
| `0x157ffc96b` | `0f84d9000000` | `je 0x157ffca4a` | `0x157ffca4a` |
| `0x157ffc985` | `742e` | `je 0x157ffc9b5` | `0x157ffc9b5` |
| `0x157ffc98a` | `7404` | `je 0x157ffc990` | `0x157ffc990` |
| `0x157ffc9ad` | `0f8c2dffffff` | `jl 0x157ffc8e0` | `0x157ffc8e0` |
| `0x157ffc9b3` | `eb77` | `jmp 0x157ffca2c` | `0x157ffca2c` |
| `0x157ffc9f6` | `7594` | `jne 0x157ffc98c` | `0x157ffc98c` |
| `0x157ffc9f8` | `eb96` | `jmp 0x157ffc990` | `0x157ffc990` |
| `0x157ffca12` | `7445` | `je 0x157ffca59` | `0x157ffca59` |
| `0x157ffca26` | `0f851effffff` | `jne 0x157ffc94a` | `0x157ffc94a` |

### RIP 引用（10）

| 地址 | 原 bytes | 指令 | 目标 / operand |
| --- | --- | --- | --- |
| `0x157ffc7ea` | `488b0d6f9293ed` | `mov rcx, qword ptr [rip - 0x126c6d91]` | `operand 1, 0x145935a60` |
| `0x157ffc802` | `488b056f3595ed` | `mov rax, qword ptr [rip - 0x126aca91]` | `operand 1, 0x14594fd78` |
| `0x157ffc818` | `488b05c1c6bced` | `mov rax, qword ptr [rip - 0x1243393f]` | `operand 1, 0x145bc8ee0` |
| `0x157ffc845` | `488b0d2c3595ed` | `mov rcx, qword ptr [rip - 0x126acad4]` | `operand 1, 0x14594fd78` |
| `0x157ffc8f6` | `803df6957eed00` | `cmp byte ptr [rip - 0x12816a0a], 0` | `operand 0, 0x1457e5ef3` |
| `0x157ffc959` | `488b0d009193ed` | `mov rcx, qword ptr [rip - 0x126c6f00]` | `operand 1, 0x145935a60` |
| `0x157ffc971` | `488b05003495ed` | `mov rax, qword ptr [rip - 0x126acc00]` | `operand 1, 0x14594fd78` |
| `0x157ffc9b5` | `488b0524c5bced` | `mov rax, qword ptr [rip - 0x12433adc]` | `operand 1, 0x145bc8ee0` |
| `0x157ffc9e5` | `488b0d8c3395ed` | `mov rcx, qword ptr [rip - 0x126acc74]` | `operand 1, 0x14594fd78` |
| `0x157ffc9fa` | `488b05ffeb86ed` | `mov rax, qword ptr [rip - 0x12791401]` | `operand 1, 0x14586b600` |

### 显式内存 operand（70）

| 地址 | 原 bytes | 指令 | 目标 / operand |
| --- | --- | --- | --- |
| `0x157ffc790` | `488d6c2450` | `lea rbp, [rsp + 0x50]` | `operand 1, access 1` |
| `0x157ffc795` | `48c745f8feffffff` | `mov qword ptr [rbp - 8], 0xfffffffffffffffe` | `operand 0, access 2` |
| `0x157ffc7a3` | `48c745e000000000` | `mov qword ptr [rbp - 0x20], 0` | `operand 0, access 2` |
| `0x157ffc7ab` | `48c745d800000000` | `mov qword ptr [rbp - 0x28], 0` | `operand 0, access 2` |
| `0x157ffc7b3` | `48c745e800000000` | `mov qword ptr [rbp - 0x18], 0` | `operand 0, access 2` |
| `0x157ffc7bb` | `488d55e8` | `lea rdx, [rbp - 0x18]` | `operand 1, access 1` |
| `0x157ffc7c7` | `448b75e8` | `mov r14d, dword ptr [rbp - 0x18]` | `operand 1, access 1` |
| `0x157ffc7cb` | `498b7c2418` | `mov rdi, qword ptr [r12 + 0x18]` | `operand 1, access 1` |
| `0x157ffc7d5` | `8b5f3c` | `mov ebx, dword ptr [rdi + 0x3c]` | `operand 1, access 1` |
| `0x157ffc7e5` | `498b5c2410` | `mov rbx, qword ptr [r12 + 0x10]` | `operand 1, access 1` |
| `0x157ffc7ea` | `488b0d6f9293ed` | `mov rcx, qword ptr [rip - 0x126c6d91]` | `operand 1, access 1` |
| `0x157ffc802` | `488b056f3595ed` | `mov rax, qword ptr [rip - 0x126aca91]` | `operand 1, access 1` |
| `0x157ffc809` | `488b4038` | `mov rax, qword ptr [rax + 0x38]` | `operand 1, access 1` |
| `0x157ffc80d` | `488b00` | `mov rax, qword ptr [rax]` | `operand 1, access 1` |
| `0x157ffc818` | `488b05c1c6bced` | `mov rax, qword ptr [rip - 0x1243393f]` | `operand 1, access 1` |
| `0x157ffc824` | `480308` | `add rcx, qword ptr [rax]` | `operand 1, access 1` |
| `0x157ffc845` | `488b0d2c3595ed` | `mov rcx, qword ptr [rip - 0x126acad4]` | `operand 1, access 1` |
| `0x157ffc84c` | `488b4938` | `mov rcx, qword ptr [rcx + 0x38]` | `operand 1, access 1` |
| `0x157ffc850` | `488901` | `mov qword ptr [rcx], rax` | `operand 0, access 2` |
| `0x157ffc858` | `48895f20` | `mov qword ptr [rdi + 0x20], rbx` | `operand 0, access 2` |
| `0x157ffc85c` | `49897c2418` | `mov qword ptr [r12 + 0x18], rdi` | `operand 0, access 2` |
| `0x157ffc861` | `8b5f3c` | `mov ebx, dword ptr [rdi + 0x3c]` | `operand 1, access 1` |
| `0x157ffc868` | `488b4f30` | `mov rcx, qword ptr [rdi + 0x30]` | `operand 1, access 1` |
| `0x157ffc875` | `448b4118` | `mov r8d, dword ptr [rcx + 0x18]` | `operand 1, access 1` |
| `0x157ffc88a` | `4889473c` | `mov qword ptr [rdi + 0x3c], rax` | `operand 0, access 2` |
| `0x157ffc88e` | `c7474400000000` | `mov dword ptr [rdi + 0x44], 0` | `operand 0, access 2` |
| `0x157ffc895` | `488b4f10` | `mov rcx, qword ptr [rdi + 0x10]` | `operand 1, access 1` |
| `0x157ffc8ae` | `ff4738` | `inc dword ptr [rdi + 0x38]` | `operand 0, access 3` |
| `0x157ffc8b5` | `894df4` | `mov dword ptr [rbp - 0xc], ecx` | `operand 0, access 2` |
| `0x157ffc8b8` | `48c745e000000000` | `mov qword ptr [rbp - 0x20], 0` | `operand 0, access 2` |
| `0x157ffc8c3` | `4c8d6de8` | `lea r13, [rbp - 0x18]` | `operand 1, access 1` |
| `0x157ffc8c7` | `443b7df4` | `cmp r15d, dword ptr [rbp - 0xc]` | `operand 1, access 1` |
| `0x157ffc8d1` | `6666666666662e0f1f840000000000` | `nop word ptr cs:[rax + rax]` | `operand 0, access 1` |
| `0x157ffc8e0` | `48c745e800000000` | `mov qword ptr [rbp - 0x18], 0` | `operand 0, access 2` |
| `0x157ffc8f3` | `8b7de8` | `mov edi, dword ptr [rbp - 0x18]` | `operand 1, access 1` |
| `0x157ffc8f6` | `803df6957eed00` | `cmp byte ptr [rip - 0x12816a0a], 0` | `operand 0, access 1` |
| `0x157ffc903` | `807de400` | `cmp byte ptr [rbp - 0x1c], 0` | `operand 0, access 1` |
| `0x157ffc909` | `48c745e800000000` | `mov qword ptr [rbp - 0x18], 0` | `operand 0, access 2` |
| `0x157ffc91c` | `8b45e8` | `mov eax, dword ptr [rbp - 0x18]` | `operand 1, access 1` |
| `0x157ffc91f` | `8945e0` | `mov dword ptr [rbp - 0x20], eax` | `operand 0, access 2` |
| `0x157ffc922` | `c645e401` | `mov byte ptr [rbp - 0x1c], 1` | `operand 0, access 2` |
| `0x157ffc926` | `48c745e800000000` | `mov qword ptr [rbp - 0x18], 0` | `operand 0, access 2` |
| `0x157ffc939` | `8b45e8` | `mov eax, dword ptr [rbp - 0x18]` | `operand 1, access 1` |
| `0x157ffc93c` | `8945dc` | `mov dword ptr [rbp - 0x24], eax` | `operand 0, access 2` |
| `0x157ffc93f` | `8b4de0` | `mov ecx, dword ptr [rbp - 0x20]` | `operand 1, access 1` |
| `0x157ffc942` | `894dd8` | `mov dword ptr [rbp - 0x28], ecx` | `operand 0, access 2` |
| `0x157ffc947` | `894de0` | `mov dword ptr [rbp - 0x20], ecx` | `operand 0, access 2` |
| `0x157ffc94a` | `498b5c2418` | `mov rbx, qword ptr [r12 + 0x18]` | `operand 1, access 1` |
| `0x157ffc954` | `4d8b742410` | `mov r14, qword ptr [r12 + 0x10]` | `operand 1, access 1` |
| `0x157ffc959` | `488b0d009193ed` | `mov rcx, qword ptr [rip - 0x126c6f00]` | `operand 1, access 1` |
| `0x157ffc971` | `488b05003495ed` | `mov rax, qword ptr [rip - 0x126acc00]` | `operand 1, access 1` |
| `0x157ffc978` | `488b4038` | `mov rax, qword ptr [rax + 0x38]` | `operand 1, access 1` |
| `0x157ffc97c` | `488b00` | `mov rax, qword ptr [rax]` | `operand 1, access 1` |
| `0x157ffc98c` | `4c897320` | `mov qword ptr [rbx + 0x20], r14` | `operand 0, access 2` |
| `0x157ffc990` | `49895c2418` | `mov qword ptr [r12 + 0x18], rbx` | `operand 0, access 2` |
| `0x157ffc995` | `4c8b45d8` | `mov r8, qword ptr [rbp - 0x28]` | `operand 1, access 1` |
| `0x157ffc9a9` | `443b7df4` | `cmp r15d, dword ptr [rbp - 0xc]` | `operand 1, access 1` |
| `0x157ffc9b5` | `488b0524c5bced` | `mov rax, qword ptr [rip - 0x12433adc]` | `operand 1, access 1` |
| `0x157ffc9bc` | `488b08` | `mov rcx, qword ptr [rax]` | `operand 1, access 1` |
| `0x157ffc9e5` | `488b0d8c3395ed` | `mov rcx, qword ptr [rip - 0x126acc74]` | `operand 1, access 1` |
| `0x157ffc9ec` | `488b4938` | `mov rcx, qword ptr [rcx + 0x38]` | `operand 1, access 1` |
| `0x157ffc9f0` | `488901` | `mov qword ptr [rcx], rax` | `operand 0, access 2` |
| `0x157ffc9fa` | `488b05ffeb86ed` | `mov rax, qword ptr [rip - 0x12791401]` | `operand 1, access 1` |
| `0x157ffca01` | `488b80a0320200` | `mov rax, qword ptr [rax + 0x232a0]` | `operand 1, access 1` |
| `0x157ffca08` | `488b8838bd1700` | `mov rcx, qword ptr [rax + 0x17bd38]` | `operand 1, access 1` |
| `0x157ffca14` | `488d55e0` | `lea rdx, [rbp - 0x20]` | `operand 1, access 1` |
| `0x157ffca1b` | `4c8d4dd8` | `lea r9, [rbp - 0x28]` | `operand 1, access 1` |
| `0x157ffca2c` | `443b7df4` | `cmp r15d, dword ptr [rbp - 0xc]` | `operand 1, access 1` |
| `0x157ffca33` | `8945f4` | `mov dword ptr [rbp - 0xc], eax` | `operand 0, access 2` |
| `0x157ffca36` | `8b45f4` | `mov eax, dword ptr [rbp - 0xc]` | `operand 1, access 1` |

### 隐式栈指令（37）

| 地址 | 原 bytes | 指令 | 目标 / operand |
| --- | --- | --- | --- |
| `0x157ffc780` | `55` | `push rbp` | `隐式栈` |
| `0x157ffc781` | `4157` | `push r15` | `隐式栈` |
| `0x157ffc783` | `4156` | `push r14` | `隐式栈` |
| `0x157ffc785` | `4155` | `push r13` | `隐式栈` |
| `0x157ffc787` | `4154` | `push r12` | `隐式栈` |
| `0x157ffc789` | `56` | `push rsi` | `隐式栈` |
| `0x157ffc78a` | `57` | `push rdi` | `隐式栈` |
| `0x157ffc78b` | `53` | `push rbx` | `隐式栈` |
| `0x157ffc7c2` | `e8098ec2fa` | `call 0x152c255d0` | `隐式栈` |
| `0x157ffc7f1` | `e8ca6e53e8` | `call 0x1405336c0` | `隐式栈` |
| `0x157ffc829` | `e852ae51e8` | `call 0x140517680` | `隐式栈` |
| `0x157ffc835` | `e8f6b953e8` | `call 0x140538230` | `隐式栈` |
| `0x157ffc83d` | `e8de1eb701` | `call 0x159b6e720` | `隐式栈` |
| `0x157ffc87b` | `e8f0c3c601` | `call 0x159c68c70` | `隐式栈` |
| `0x157ffc89e` | `e8cdc3c601` | `call 0x159c68c70` | `隐式栈` |
| `0x157ffc8ee` | `e8dd8cc2fa` | `call 0x152c255d0` | `隐式栈` |
| `0x157ffc917` | `e8b48cc2fa` | `call 0x152c255d0` | `隐式栈` |
| `0x157ffc934` | `e8978cc2fa` | `call 0x152c255d0` | `隐式栈` |
| `0x157ffc960` | `e85b6d53e8` | `call 0x1405336c0` | `隐式栈` |
| `0x157ffc9a1` | `e88a3f3cfd` | `call 0x1553c0930` | `隐式栈` |
| `0x157ffc9c9` | `e8b2ac51e8` | `call 0x140517680` | `隐式栈` |
| `0x157ffc9d5` | `e856b853e8` | `call 0x140538230` | `隐式栈` |
| `0x157ffc9dd` | `e83e1db701` | `call 0x159b6e720` | `隐式栈` |
| `0x157ffca1f` | `e85ca1f0f5` | `call 0x14df06b80` | `隐式栈` |
| `0x157ffca3d` | `5b` | `pop rbx` | `隐式栈` |
| `0x157ffca3e` | `5f` | `pop rdi` | `隐式栈` |
| `0x157ffca3f` | `5e` | `pop rsi` | `隐式栈` |
| `0x157ffca40` | `415c` | `pop r12` | `隐式栈` |
| `0x157ffca42` | `415d` | `pop r13` | `隐式栈` |
| `0x157ffca44` | `415e` | `pop r14` | `隐式栈` |
| `0x157ffca46` | `415f` | `pop r15` | `隐式栈` |
| `0x157ffca48` | `5d` | `pop rbp` | `隐式栈` |
| `0x157ffca49` | `c3` | `ret ` | `隐式栈` |
| `0x157ffca4a` | `e891614ce8` | `call 0x1404c2be0` | `隐式栈` |
| `0x157ffca4f` | `e88c614ce8` | `call 0x1404c2be0` | `隐式栈` |
| `0x157ffca54` | `e887614ce8` | `call 0x1404c2be0` | `隐式栈` |
| `0x157ffca59` | `e882614ce8` | `call 0x1404c2be0` | `隐式栈` |

本包没有读取其他 slot/callee/xdata/literal，没有解析 PrimaryIndex 内容或 row，也没有 CPU/live/REA、进程、网络、构建、DB 或 Git。没有来源漂移、安全或 Daybreak 事件。这里不解释 loader/parser、返回或字段别名；实际 index 填充、row、资源 ready 和客户端验收均未证明。仅离线工具、测试和文档，无运行变更与部署；交 root 第三审后另交 Sol max 做语义工作。
