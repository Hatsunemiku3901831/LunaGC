# E-01/R3-PROUD-PREPARE-WINDOW-W（2026-11-11）

## 执行前有界计划

仅按root批准的W合同处理 `157ffc650`。每次fresh重新解析当前GAME_PATH，核五文件完整大小和SHA；固定source合同、两份原236B caller、其第三审、D两份报告及第三审和既有tuple来源完整SHA，并复核原E8与准确slot QWORD。唯一exact/containing pdata必须同条，先保存12B记录及pre-body诊断；仅在完整body不超过4096B、连续解码不超过1024条时保存窗口及完整call/branch/RIP列表。两次独立fresh后以原始产物离线复算并测试门禁。歧义、缺pdata、超界或来源漂移停止并保留证据；不读取xdata、其他callee或第三literal，不执行CPU/live/REA、进程、网络、构建、DB或Git。语义由root第三审后另行处理，未知保持未知。

## 结果与范围

D注册身份复核为owner18192 / method138529 `IFIKEDMJMLC`、classInstance5071 / methodInstance -1、table289813 / spec175101 / pointerIndex84545；slot `142515eb8` 的原QWORD `50c6ff5701000000` 指向 `157ffc650`。唯一exact/containing pdata是同一条记录，范围 `[157ffc650,157ffc771)`，289B；12B原记录 `50c6ff1771c7ff17086d1905`，SHA-256 `6ca1d81ee6dc0712fbe3cb26ba755be75693443df24ce9b978e924322b87df7c`。先写出边界诊断和pre-body，再读取窗口；连续82条指令，无解码空洞。未读取unwind/xdata。

原caller两份236B SHA-256均为 `479e010ba8e0b7fbea184c024408f2a1ce266aa519b92445bf03279672f2fb4e`；`157ffc4d3 e878010000` 指向此入口。D两报告SHA-256均 `69ab19ff424019a5d38d02a42ae6573a4731788519b136a38a74378cd19f02b6`；第三审 `0bff021d1171cde4c1248370da22718887765e472c788ecfba619aa41e5ff5e3`。source规格 `ef4109fa7efc3ffa2a284302ed55d494423f33fa3ed017fcf6c524603ee1f62a`；全部来源门禁详见工具固定表。两fresh各重新解析GAME_PATH并核五文件完整大小和SHA，五项与D基线一致。

## 完整机械引用

以下仅列原指令和直接目标；间接目标未知，未追踪任何目标或解释语义。

### Call（8条）

| 地址 | 原bytes | 指令 | 目标 |
| --- | --- | --- | --- |
| `0x157ffc680` | `e8eb4e6ef5` | `call 0x14d6e1570` | `0x14d6e1570` |
| `0x157ffc6ab` | `e8d0000000` | `call 0x157ffc780` | `0x157ffc780` |
| `0x157ffc6c8` | `e8036a12e9` | `call 0x1411230d0` | `0x1411230d0` |
| `0x157ffc6f8` | `ff90f8000000` | `call qword ptr [rax + 0xf8]` | `未知（间接）` |
| `0x157ffc710` | `e86b000000` | `call 0x157ffc780` | `0x157ffc780` |
| `0x157ffc725` | `e8a66912e9` | `call 0x1411230d0` | `0x1411230d0` |
| `0x157ffc766` | `e875644ce8` | `call 0x1404c2be0` | `0x1404c2be0` |
| `0x157ffc76b` | `e870644ce8` | `call 0x1404c2be0` | `0x1404c2be0` |

### Branch（14条）

| 地址 | 原bytes | 指令 | 目标 |
| --- | --- | --- | --- |
| `0x157ffc65b` | `0f85a1000000` | `jne 0x157ffc702` | `0x157ffc702` |
| `0x157ffc66b` | `0f848d000000` | `je 0x157ffc6fe` | `0x157ffc6fe` |
| `0x157ffc675` | `0f8483000000` | `je 0x157ffc6fe` | `0x157ffc6fe` |
| `0x157ffc688` | `7478` | `je 0x157ffc702` | `0x157ffc702` |
| `0x157ffc692` | `7405` | `je 0x157ffc699` | `0x157ffc699` |
| `0x157ffc697` | `eb0c` | `jmp 0x157ffc6a5` | `0x157ffc6a5` |
| `0x157ffc6a0` | `7468` | `je 0x157ffc70a` | `0x157ffc70a` |
| `0x157ffc6b2` | `7461` | `je 0x157ffc715` | `0x157ffc715` |
| `0x157ffc6b6` | `745d` | `je 0x157ffc715` | `0x157ffc715` |
| `0x157ffc6c6` | `7414` | `je 0x157ffc6dc` | `0x157ffc6dc` |
| `0x157ffc6ed` | `747c` | `je 0x157ffc76b` | `0x157ffc76b` |
| `0x157ffc723` | `7414` | `je 0x157ffc739` | `0x157ffc739` |
| `0x157ffc74a` | `741a` | `je 0x157ffc766` | `0x157ffc766` |
| `0x157ffc763` | `48ffe0` | `jmp rax` | `未知（间接）` |

### RIP引用（2条）

| 地址 | 原bytes | 指令 | 目标 |
| --- | --- | --- | --- |
| `0x157ffc6dc` | `488b051def86ed` | `mov rax, qword ptr [rip - 0x127910e3]` | `0x14586b600` |
| `0x157ffc739` | `488b05c0ee86ed` | `mov rax, qword ptr [rip - 0x12791140]` | `0x14586b600` |

## 复现与离线审计

```powershell
python -B tools/client72_d02_proud_prepare_window.py --output final1
python -B tools/client72_d02_proud_prepare_window.py --output final2
python -B tools/client72_d02_proud_prepare_window.py --audit final1 final2 --output audit.private.json
python -B -m unittest tools.test_client72_d02_proud_prepare_window -v
```

两fresh八个原始文件逐byte一致，离线audit重验源、slot、pdata、窗口、完整反汇编及引用，不读取当前PE。六项测试通过：无/重复/重叠pdata、范围上限、slot、解码中断及指令上限、间接call/内存/分支、pre-body漂移拒绝。人工夹具不代表客户端执行。无取数失败、来源漂移或安全/Daybreak事件。

| 两fresh各产物 | 字节 | SHA-256 |
| --- | ---: | --- |
| `boundary.private.json` | 7762 | `0909197aa3959760b1d2cd7ceaffe71c29d1fe8f901c45529173e8d8a9003797` |
| `cell.bin` | 8 | `0dd98708676985c256b3f7d5482a908695e34a2033b4ed5e1b134b64ea64ce88` |
| `code.asm.private.json` | 9569 | `bf66d523183a3967408f579b8e7b2536cf18611deed67e5e28c6377f8480ca5e` |
| `code.bin` | 289 | `0c1d792245409c0df4df69beed8bd9b0a520f8a4d56d14e2fa1dd43ed2b4db7b` |
| `extraction.private.json` | 19091 | `f38870a803673e733231a949603a1375d0961f05030fe104e5bbb681a73f2f59` |
| `pdata.bin` | 12 | `6ca1d81ee6dc0712fbe3cb26ba755be75693443df24ce9b978e924322b87df7c` |
| `pre-body.private.json` | 7968 | `56838ab77a544fdebe93b6d8ec5a7417f9a0cf6414a0c1e364415d3ca43b2831` |
| `slot.bin` | 8 | `5de04e8c2390313feebe591bb534e593bfe9259cadc24a8b0e80aeb9870312f3` |
| `audit.private.json` | 14701 | `c322aa7f0d5d397c21e35edf86422cab2794a401d0784fd68045e6e7884d81cf` |
| `tools/client72_d02_proud_prepare_window.py` | 16261 | `ea83d1195754ea2dc785252f55945b318f416735c84fa985f44f0412ed60e243` |
| `tools/test_client72_d02_proud_prepare_window.py` | 2401 | `f2dc48166820ce00f47a7b1de562e7a4844866a92064312fb56a9f8932521018` |

未读其他callee、第三literal、xdata；未CPU/live/REA、进程、网络、构建、DB或Git。跨其他pointerIndex同code仍未知；本包不解释loader、路径、实际index或row语义，不改变resourceReady；客户端验收增量为0。交root第三审后由Sol max处理语义，不能据此晋级D-02。
