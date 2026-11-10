# F-03d3f4d2：Avatar allocator TLS 单窗后继

本卡按主线授权完成唯一直接callee `140563cd0` 的真实PE exception完整窗提取与静态解释。**312B、74条原指令；尚无原生执行、TLS/GC自然输入或allocator返回。** 输入和写入范围已明确，可以交Sol xhigh/Terra准备后继；本卡不建立通用运行时，不再提第二窗。

## 来源和单窗边界

主线范围文件为忽略目录`local/migration72/d02-resume-20261110/a-tls/main-approved-scope.zh.md`，SHA `c4df1c5dd10088b37ab730d7e0dc9aa294df008dbe492a1505c779fc04873cb8`。本次是已有直接callee小后继，复用上一轮upstream branch/source的partial事实和allocator成果，没有新网络检索或克隆。

独立`powershell.exe -NoProfile ... -File tools/game_path.ps1`实际exit0，重新解析当时GAME_PATH；`protocol/cn-7.2/profile.json` SHA `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`的五文件完整SHA/大小全部匹配。读取EXE后再次完整核SHA `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`。机器路径和原始resolver输出只在忽略目录。

| 原件，相对本卡`a-tls/` | 实际范围、大小和SHA-256 |
| --- | --- |
| `code.bin` | `140563cd0..140563e08`，312B；`8ce320df866d328376e990489907ab22f973e2d087ee7c1e0c415c8fb8ee3a7c` |
| `pdata.bin` | 12B；`728a683ac9a02a1cdc4c502411b450a9431ef4643351f3e84037135b961f1fc2` |
| `manifest.private.json` | `5bc6ecb1349c6f42f884a98ff1d8634855ff6dded0d813b441ec26d36d1b296c`，五输入、唯一匹配记录、原bytes及74指令 |

pdata原bytes为`d03c5600083e5600e4d2cb03`，记录RVA `1a57a550`；begin RVA `563cd0`、end `563e08`、unwind `3cbd2e4`。只检索PE header/exception目录里覆盖准确target的一条记录；唯一匹配且begin等于target，代码必须全部file-backed可执行且完整连续解码。没有按相邻函数补窗，没有提取callee或解读完整unwind/异常透明性。

获批来源是原420B allocator的`1405337d8 e8f3040300`，重新按rel32计算目标`140563cd0`，return `1405337dd`。该420B原报告SHA `ee0e3d4b24cbd235b177b8ee7e6edfd20bd49feac89e1bda79b8d759d08274e2`保持。真实提取UTC为`2026-11-09T23:59:47.869514+00:00`至`23:59:48.851615+00:00`；本地日期为2026-11-10。stock读取完成后已通知主线释放互斥，后续仅用冻结材料。

## 本轮准确ABI与size32分支

原fresh完整Avatar仍是连续105条后在`1405337b9`缺TLS index4B，旧cold普通10294保持另一输入/路径。读取原fresh报告`73e55971e1ab3785e9a13b7619bedda89da9a81fbcd548c740bfec90e60a6754`，准确边界寄存器证明RDX为32、R8为同轮实际List class完整64位pointer OR1。没有把旧heap或指针移植到新epoch。

新callee入口把RDX保存R9、RCX保存R10；入口旧R9不是本函数读取的输入。RCX来源是实际moduleTLS+19d0的allocator context；若该字段为null，原caller会先把固定`B+5699688`写入它并以该地址继续。该原生写入不授权host预设TLS或假对象。

原无符号size阈值为`8000`。小请求按原bsr/shift算slot；本轮32得到leading-zero数56、shift4、index2，实际pool record pointer从**context+48**读取。这个host推导只解释原指令，未执行新CPU。record+18的实际block size仍必须采到，不能由请求32预填32。

池非空时的原native操作为：

1. 以模块TLS index `B+5eef080`，经`GS:[58] → TLSVector[index]`取得该线程moduleTLS，将`moduleTLS+19cc`写5。
2. 取record+20的head；原`lock inc word [record+10]`，把真实`head.next`写回record+20。
3. 将原tagged class去低3位写到head+0；head+8的低字节改为tag低3位OR实际`B+5699680` byte。该byte和flags的枚举含义尚未恢复。
4. 用实际record+18 block size增加context+8计数。旧head+8的bit08置位时另有清零形状调用。
5. 重取TLS，读取moduleTLS+19e0的pointee，并把moduleTLS+19cc写1；检查该pointee+a0的bit0，条件调用后返回实际head pointer。

下文将+19e0的pointee简称threadState，只表示该窗中的pointer职责，不赋予未恢复的GC类型名、enum或同步语义。重复一致快照不是原子快照或无writer证明。

## 全部外部直接边与出口

这些目标都由新窗原rel32 bytes独立重算；**没有第二窗或直接API实现证明**。新窗没有`call [IAT]`指令，不能从地址/旧host模型给它们冠以已验API名。

| 原site | 真实条件、ABI和出口 | 未闭边界 |
| --- | --- | --- |
| `140563d85 → 141fdf9c0`，return `140563d8a` | 仅旧headFlags bit08置位；RCX=head+10，EDX=0，R8=实际blockSize-16 | 是memset形状推断，未取body/API绑定；旧metadata storage清零模型只批准旧caller/尺寸，不能自动用于本caller |
| `140563df5 → 140563e10`尾跳 | size无符号大于8000或实际head为null；RCX=context，RDX=原tagged class，R8D=大对象49或当前bin index，R9=原size；恢复原栈后尾跳 | 此窗没有恢复state5为1；slow target内行为、分配/失败/返回均未知，不得补host成功 |
| `140563e01 → 14056c050`，return `140563e06` | 实际threadState+a0 bit0置位；显式RCX=`B+56cee0`地址参数，其函数/数据职责未知；其余参数意义未恢复 | 不能强清bit0或假定callback无副作用/会返回 |
| `140563dbe ret` | 非空池路径；可选清零和通知若实际经过，必须真实返回；RAX为实际popped head | 这里只是静态出口；没有自然managed对象分配或完整Avatar成功 |

## 可执行后继先缺什么

只增加4B TLS index不够，index0也不是失败或成功判断。需要同一新epoch的完整Avatar/List/ordinary图及**有来源的实际线程GSbase**，不能取任意线程/旧TEB或人工GSbase。最小TLS链是index4、GSbase+58的8、vector[index]的8、moduleTLS+19d0的8，首遍4读28B。null context保留caller的准确原写入边界，不host初始化。

在真实线程来源已明确后，新报告给出size32有限前沿：上述4项，另取context+48 pointer、record+10/2B、record+18/8B、record+20/8B、head+0/8B、head+8/1B、context+8/8B、全局flags byte1B、moduleTLS+19cc/1B、+19e0/8B及threadState+a0/1B。共**首遍15读82B，全部成功unique统一final上界30读164B、单读8B**。这是未执行、未批准的新proposal，仅新增TLS/池前沿，不含同epochAvatar图、整个block或旧collector预算；失败请求仍须计账并保留。

还有一个输入之外的必要权限：旧回放拒绝captured写。若context原非空，进入新callee后首先会在`140563d2d`写TLS+19cc；若context原null，原caller更早在`140533858`写+19d0。故只添加只读输入仍不能真实推进分配。主线若选择实施，必须另批**仅离线捕获副本**的精确native写域：

- TLS+19cc的1B；null分支另列TLS+19d0的8B，不全页放写。
- record+10的2B lock-inc、record+20的8B head更新。
- 实际head+0的8B和head+8的1B。
- context+8的8B原计数更新。

原文件和真实目标不能写；实际指针、原宽度和原指令保持。head必须来自真实freelist，不能造对象/页零或用host allocator。获得实际record.blockSize后，仍需另审该真实block的完整source-backed bytes和可选清零范围。当前仅head两字段不足以为完整Avatar提供对象存储。

遇空head或大对象，在`140563df5`外部尾跳前停止；遇bit08清零或threadState bit0通知，在相应未审call前停止。不能为走fast path把这些实际条件改0。本卡未批准capture副本写域、额外read/CPU预算或上述callee体；不让用户重复等待SDK页采样，因为当前仍无准确自然线程/producer合同。

## 实际静态验证与返回

新增仅本文档和独占`a-tls/`原件/私有一次性脚本。提取脚本SHA `ecd716e7043079c810993cdb1c4d84cca324764263bb6ba1e5dc36c822f1982a`；复用PE parser SHA `7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d`。独立分析脚本SHA `4307ae736cb422afe95236f12945b20e5e31d6322cd3d00da5526f02806bd13d`。

实际两次标准库静态分析通过5完整source pin、312B/12B/74指令连续覆盖、14关键opcode、3外部rel32边、3准确RIP地址、旧fresh32/tagged class、bin2及15/82账本断言。两报告各6953B、逐byte相同，SHA `3518e1fb7623a62b2a435bef29ef5bb83407cdf20a46b412183bce2da6db0ed7`；未把静态断言记为原生CPU、Java或客户端测试。`extract/analysis-*.execution.private.json`、stdout/stderr、双报告和`determinism.private.json`保留实际argv/UTC/exit；三个进程均exit0。

```powershell
python local/migration72/d02-resume-20261110/a-tls/analyze.private.py --output local/migration72/d02-resume-20261110/a-tls/analysis-main-third.private.json
```

没有live目标/客户端/DB/调试器/安装、Cargo/Gradle/Git、第二函数窗或新CPU。旧A工具、共同String、原报告/pin、配置/资源/数据均未写，无运行材料变化不重启GS。主线负责文档索引/提交和交接，本卡没有本地提交。

结论是**单窗与准确后继合同已交付，完整allocator/Avatar仍未闭**。实现规格交Sol xhigh/Terra；先由主线明确真实线程来源、同epoch输入及精确捕获副本写权限，再准备有界collector/replay。若必须扩大为通用TLS/GC/runtime，先交中文增量计划，不在本卡继续扩展。
