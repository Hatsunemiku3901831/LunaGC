# 1017重号的正常原生网络域

F-03a补充证据：`tools/client72_cmd1017_domain.py`与`protocol/cn-7.2/cmd1017-domain-observations.json`。固定三输入哈希不变；仅脚本执行固定文件的原生代码，没有操作客户端进程。

| 目标类型 | 已观察方向/通道 | 独立来源与边界 |
| --- | --- | --- |
| 63999 / HDPGCNCMACO | Game-session C2S EnterSceneDoneReq | 具名NetworkManager请求入口15121E380→15120D730；完整请求构造和writer见八握手合同 |
| 22065 / NCJGOJMOPHB | 本进程向另一beyond/WPF stream通道出站 | 1514609B0构造→1533D9F30→15011B180→14BBE8100队列入口；目标NetworkStream/Stream、线程/队列字段与独立beyond/WPF诊断相连 |

3个独立bool源样本实际执行空dirty字典枚举、子消息字段赋值、命令1017赋值、包装器18771构造与原始消息指针传递。只模型化托管分配和DateTime输入，不模型化命令/消息赋值或路由。5项回归与两轮trace/摘要相同；摘要SHA-256为`1d068446dbdc3c90df711ed6839dcb4ae06e74db65a59acc433242b1afdac497`。

据此仅在已观察的正常Game C2S路径解除DoneReq域歧义。全局静态命令表仍保留两条1017；不能把22065合并为DoneReq、旧MonsterChess名称或Game S2C。外部peer地址/语义、IFix、反射路径和全局传输闭包未验，D-02未执行。

```powershell
$domainClient = & ./tools/game_path.ps1
python tools/client72_cmd1017_domain.py --client $domainClient --output local/migration72/evidence/cmd1017-domain-trace.json --summary-output protocol/cn-7.2/cmd1017-domain-observations.json
python -m unittest tools.test_client72_cmd1017_domain -v
```

原始报告与重复性证据位于忽略目录`local/migration72/{analysis,evidence}/cmd1017-*`和`analysis/f03-cmd1017-domain.*`。
