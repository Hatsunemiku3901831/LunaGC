# F-03d3f4d2 / FIRST-POP-COPY：首 pop 精确副本执行

本包实施合同先于执行冻结。只运行国服7.2已固定原 bytes 的离线 CPU 副本：`1405336c0`入口，经本轮真实 class/普通查询/TLS、`140563cd0`首 pop、`141fdf9c0`实际16B分支、`140525e00`已闭快返回，再由原 outer ret 返回 owned Win64 sentinel。没有自然 ctor frame，不把 sentinel 称作 `15183fe89` 自然返回。

输入以 [A helper 合同](CLIENT_72_D02_GC_HELPER_20261110.md) 的18源完整pin及本轮AB raw为准。原件只读；先核完整pin与31+31统一复读。允许从当前 `tools/game_path.ps1` 解析的固定EXE只读重提两个原批准查询窗：`1405269f0..1405269f5`（SHA `bb3bd836fbc8d866dc73ea8958b74331d207192b579c09e99d87e8637454f8ad`）、`15a4d6b60..15a4d7671`（SHA `2ae927cf60f8d3e5a4a1ba9595f41e61bc9a6d325d84bfc1bace8c6c27d2fff1`）。历史manifest只证明窗身份，EXE须重新核450927000B/SHA `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；不引入旧heap、模型或普通槽替代。

权限只有七个唯一 captured-copy span：M+19cc/1、R+10/2、R+20/8、H+0/8、H+8/1、C+8/8、H+10/16。按原PC/准确地址/宽度逐条批准；state两次及helper同址两次均独立记录，共九个captured写事件。不是整页、整record或整对象写权限。使用已有 `trace_client72_token.load_emulator` 与 `local/migration72/python`，不安装依赖。GS仅采用已审GS58/TEB等价，明确owned栈/sentinel；不构造对象/TLS/槽或host返回。

每条原指令先核代码边界、显式/隐式内存读写及callee；未提供读、未经批准写或callee立即停止，不执行出界指令，不扩大范围。停止后只留离线失败副本与已发生事件，销毁emulator即恢复，不回滚后冒充成功；原件前后逐项SHA核对。所有私有输出只进 `local/migration72/d02-resume-20261110b/a-first-pop-copy/`，正式输出新文件拒绝覆盖。

第二allocation/HashSet/Default、items和Avatar写不在本包。目标进程写、新live/API、GC所有权、自然allocator线程及完整Avatar/g2/Scene/D-02均不授权/不验收。只允许本页和两个新tool/test；旧helper、共享输入、Proto/生成器/矩阵、Project/Harness/README、配置、数据、资源及patch只读。无Git提交/切分支、构建、JVM/DB/服务或客户端操作；主线负责共享索引与提交。

实现前曾考虑不完整的branch入口，最初误记`140533785`（实际在call中部），立即更正为`14053372a`，未执行两者。主线随后批准两原查询窗，正式实现优先真正函数入口，不把分支入口冒充完整allocator。

## 实际结果与证据

**首个受控 allocator 函数入口路径已完成限定离线执行：200条原指令、九个captured写事件、七个唯一span/44B，原outer ret实际返回owned sentinel。** 不是全部allocator分支、自然ctor或完整Avatar验收。六个原code窗合计4883B；两个旧lookup窗从当前重新解析的EXE原bytes取得，完整EXE前后size/SHA相同。只复用旧代码，不复用旧heap或host模型。本轮自然普通查询返回实际ordinary slot1255值，和本轮comparison cell不等，原分支自行到`14053372a`，没有host改RIP跳过查询。

18源及额外两项旧lookup来源均在执行前后逐文件验证；83项历史source-snapshot逐一核原SHA，当前被用户修改的AGENTS不回退、不改pin。运行用本轮前23个真实捕获span，不装入Pool/第二对象图；31次原CPU数据读取全部有已捕获bytes来源。当前EXE仅作为两个既有窗的静态源读取，没有自然进程API或新窗口。

| 原PC | 精确副本span | 实际old→new |
| --- | --- | --- |
| `140563d2d` | M+19cc/1 | `01→05` |
| `140563d46` | R+10/2 | U16 `218→219`，原lock-inc |
| `140563d4e` | R+20/8 | 原H→原H首QWORD N |
| `140563d59` | H+0/8 | 原N→本轮真实List class K |
| `140563d6b` | H+8/1 | `08→81`，旧08先保存于AL |
| `140563d73` | C+8/8 | U64 `7616→7648`，原加L=32 |
| `141fdfa7a` | H+10h/16 | 实采`01000000000000000000000000000000→`16个00 |
| `141fdfa7e` | 同一H+10h/16 | 16个00→16个00，独立第二写事件 |
| `140563da4` | M+19cc/1 | `05→01` |

Unicorn底层以页承载bytes；批准权限仍由每条指令执行前的PC、准确地址和宽度控制，不把页、record或对象作为写授权。SSE的两条16B原store在后端分别产生两次8B回调；报告同时保留11个底层写回调和九个原指令写事件，不把后端拆分误计成四条SSE指令。下一指令前从副本读取真实new bytes，不从预测值填报告。未批准span外的全部已提供bytes逐byte核保持；原始文件不参与副本写。

| 原call | 实际原return | 可审核结果 |
| --- | --- | --- |
| `14053371c→1405269f0`，wrapper原jmp到`15a4d6b60` | VM原ret `15a4d7670→140533721` | 本轮ordinary cell原读取，无host返回 |
| `1405337d8→140563cd0` | `140563dbe→1405337dd` | 原RAX=H |
| `140563d85→141fdf9c0` | `141fdfa85→140563d8a` | 完整15条准确路径，原RAX=H+10h |
| `1405337fb→140525e00` | `140525e26→140533800` | 实际C7=1原快返回 |
| outer原ret `14053380a` | owned sentinel | 原RAX=H，RSP=初始RSP+8；不是自然`15183fe89` |

每项原call opcode、实际ASLR地址、RSP、原栈return及寄存器在私有报告的`native.calls/returns`；所有写old/new、原PC/实际PC/opcode在`capturedWrites`，每个输入span的原始/最终bytes在`originalAndFinalSpans`。完整helper实际路径及全部200PC单列，GS只用已审GS58/TEB等价，不宣称直接读取自然GS base。

## 实际测试、双fresh与主线第三复现

两个新文件为`tools/client72_d02_first_pop_copy.py`和`tools/test_client72_d02_first_pop_copy.py`。`python -B -m unittest tools.test_client72_d02_first_pop_copy -v`实际8项通过：真实入口/九写正向；禁写前停；缺普通查询cell；缺TLS字节（即使底层同页已映射）；撤销helper callee；撤销首SSE写；更宽span不能替代准确宽度；撤销第二同址SSE事件。负例只收紧本地副本门禁，不修改captured原件、不注入dirty、槽或对象。失败保留已发生副本事件，例如helper call前止仍为state5，未回滚冒充返回。

最终两fresh各exit0，报告`final-first.private.json`与`final-repeat.private.json`均36425B、逐byte相同，完整SHA **`be49a4e995e1f9c9ab65cea6810e044ccfebd280a1ff89fd2de79ec1699b0e74`**。真正argv/UTC/exit/stdout/stderr分别以`*.execution.private.json`及`*.stdout/stderr.private.txt`保留；时间不塞进语义报告凑确定性。初次成功的`first.private.json`是较早实现版本，保留而不改写，不作为最终SHA。

主线可用新文件名独立第三运行（重复名称会在CPU执行前拒绝）：

```powershell
python -B -m unittest tools.test_client72_d02_first_pop_copy -v
python -B tools/client72_d02_first_pop_copy.py --output local/migration72/d02-resume-20261110b/a-first-pop-copy/main-third.private.json
```

这次只关闭首pop/helper/outer准确路径的副本执行子出口。`completeAvatar/completeG2/completeScene/secondAllocation/d02Passed`及自然线程/GC所有权仍false，没有items写、第二HashSet/Default或自然调用成果。无构建/服务/客户端/配置/资源变更，不需GS重启；本子代理未Git提交，交主线独立审核、更新共享Project/Harness与提交。下一项可执行任务是按既有第二allocation规格准备真实record/block及Default准确来源，不能把首块复用为第二对象。

主线2026-11-11独立重跑8项测试及真正入口第三报告，36425B/SHA `be49a4e995e1f9c9ab65cea6810e044ccfebd280a1ff89fd2de79ec1699b0e74` 与双fresh逐字节一致，接受本页限定副本执行出口；自然调用/所有权和全部父项继续未验。
