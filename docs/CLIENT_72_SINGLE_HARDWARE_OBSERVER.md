# D-01b5g：单硬件入口/返回观察的前置验证

当前只完成实现、守卫与一次固定自有fixture验证，**尚未执行本卡原始游戏load**。目的在于把D-01b5e唯一GlobalDispatch观察点从软件改为硬件执行点，减少主模块字节改写这一变量，并取得自然HTTP正文/返回。没有修改客户端检查、参数、返回、文件、环境或信任材料；不能由本卡前置通过宣称R2/D-02通过。

用户最新指令要求当前在途项验收后暂停、不开新任务。本前置子合同经主线收口后，原始游戏轮保持未开始；恢复后先重新核现场和持久点归属，不能直接执行下方历史示例。

## 实施合同

[client72_single_hardware.py](../tools/client72_single_hardware.py)是合成driver与原观察器共用的单槽控制实现。系统点与主模块EP先核六DR为0、HW表空；仅已验证EP之后安装slot0执行点。原游戏模式为`global-dispatch-hardware`，主模块软件观察集合为空，仍保留原三个系统终止API诊断点。下一次游戏的持久4点必须以e的raw ledger按名字、当前模块/RVA与属性逐个识别清理；未知点拒绝，不按前缀批清。

| 状态 | 接受条件 |
| --- | --- |
| 未持有自有点 | HW表空，DR0-3均0；通用idle允许DR7保留位400与DR6保留位ffff0ff0，但本实验系统/EP另明确要求六DR全0 |
| 持有执行slot0 | 唯一表项的模块/地址/名字/slot/type/执行大小匹配；DR0为该地址，DR1-3为0，DR7仅1或401 |
| 普通非HW停止 | DR6只接受0或ffff0ff0，不将B0状态当普通应用异常状态 |
| 自有HW事件 | EVENT_BREAKPOINT type2、slot0、typeEx2、hwSize0、addr=name/module与CIP匹配，enabled/active且无条件命令；DR6仅允许上述idle或其B0版本1/ffff0ff1，拒绝其余槽/状态位 |
| 线程/返回 | TEB ClientId的PID匹配独立OS目标，TID匹配当前调试上下文；返回点还必须等于原TID及RSP+8。PUB的Breakpoint数据不独立携带TID，不把这些读取宣称为原始内核事件线程快照 |

GlobalDispatch入口仍是固定原VA `1512250E0`；RCX为管理器、RDX为托管正文string、R8为completion Action。读取有界header/UTF16，原始内容只留local。唯一已核返回点是`14F894D43`；其他返回地址保留现场并停。入口命中后先清自有slot及DR，再把同一slot换到返回点。实际void返回只记录完成关联，不读取RAX为对象；匹配后清HW并停止本次观察。

set/clear前后保存完整16个GPR、cip/flags及六DR，双API至少逐值核cip/rsp/flags/六DR。只允许此前已经证明的EFLAGS reserved bit1差异，不允许算术flags/GPR/RIP/RSP变化。清理只能针对当前已证明的唯一slot；先按API移除、核其表及值，再显式清DR0/6/7，后核六DR0。入口/返回的原字节窗口在安装和解除前后都通过脚本API与固定文件比较，不把它称作对整个目标内存的独立物理快照。

首异常交付仍为匹配code/addr/firstchance的AV/C++及已验证线程命名结构才con1；系统/EP/TLS/自有点裸con。未知/二次/事件缺失、表或DR/线程/栈不符均停，不盲run。若结束时自有HW状态仍不能核清，保留精确ledger与未知理由，不将“已调用clear”当成恢复成功。120秒预算到时显式pause，核新PAUSE/OS/PID后再按同合同清自有点。

## 一次新增HW切换回归

使用原固定`channel_fixture.exe`（SHA-256 `54b68323ec091af156788d08cb36d412f3d812cd6b596cc9c21978766f5196b7`），未重建，也没有重复f已经闭合的软件点基础。新[hardware fixture driver](../tools/observe_client72_hardware_fixture.py)只运行有前序AV+C++的一轮，30秒上限。

- 9个停止：系统、EP、AV、C++、硬件入口、硬件返回、三个正常终止诊断点。系统/EP六DR均0；EP后才装HW。
- 原生AV/C++各被应用处理一次；两个硬件事件均为slot0/type2，入口参数1、返回TID/RSP+8吻合。应用最终phase40、marker3。
- 安装入口→清除→安装返回→清除共4个动作；实际DR7为0→1→0→1→0，DR6始终0，四次flags XOR均0，完整GPR保持，代码窗口不变。允许的其他保留位形态属于守卫合同，并非声称本轮都实际出现。
- OS/PUB/log均exit0，且driver明确断言一个EXIT和一个STOP；PUB线程存活、eviction0、最终无HW残留。成功raw为`evidence/d02-r2-hw-switch-fixture.json`，逐动作核对为`d02-r2-hw-switch-verification.json`。

原观察器19项+6项新slot/status/CPU守卫+3项实际嵌套函数集成守卫，共28项实际通过，失败/错误/跳过0。集成守卫执行原观察器的真实`snapshot`与`observe`函数体：核g模式确实输出全部GPR/TEB身份；`cn72_r2_return_hw`能选择返回分支、匹配后只清HW并停止、不会解引用void RAX；错误TID/RSP保持pending且不清点。它们不是对真实游戏自然调用的替代。

硬件控制模块与实际fixture raw中的源码hash相同。fixture运行时共享观察器为已提交e版本`bfdf0847…`；集成后已独立比较`stop_action/register_action/matching_returns/return_observation`四函数AST与`942205c`逐项不变，新增集成选择另由上述测试覆盖。f的原始源码hash与结论没有追写。

最终只读审计同一管理员会话无游戏/fixture，API表空；游戏持久库仍为e的4点且hash未变，fixture持久库只有3个自有终止诊断点。见`d02-r2-hw-switch-final-audit.json`。本卡未开始原始游戏轮，下一步由主线审阅实现及该raw后协调一次观察，不允许原样重试软件点。

主线已审8个交付文件、三份raw来源hash，重跑28项全过；重新用同一守卫核9停点、4次set/clear的全GPR/flags及代码窗口、入口返回TID/RSP、系统/EP六DR0、三方exit0及EXIT/STOP，另核四核心函数AST与942205c相同。未再运行fixture或原游戏。证明 `local/migration72/evidence/d02-r2-single-hardware-main-review.json`，主线测试日志 `logs/d02-r2-single-hardware-main-tests.log`。最终新增只读暂停审计 `evidence/d02-r2-d01b5g-pause-state.json` 保留两个既有调试器及各自持久ledger，原游戏HW轮false；不是D-01b5g真实观察或R2验收。

## R2汇合范围

候选解释仍分层：f证明通用con1前序能正常进入软件点；固定源码显示普通软件点需要恢复原字节后才进入高层回调，且Automate不是原始内核debug-event出口。g的单变量是改用硬件观察，不能预先把缺事件归为游戏保护、隐藏线程或特定恢复失败。

若取得正文及自然返回，将其接到已验RegionList/当前区Java→native合同，核真实路由和信任/SDK加载前置。若再退出、未知停止或预算未命中，按新OS/PUB/log/WER和精确point/DR状态收口，不重复load。主线h已发现固定dfde JAR的BuildConfig仍为7.1.0，真实CNRELWin7.2.0请求走正式加密签名的版本拒绝；旧builder消费通过不代表实际服务准入。positive路由接受仍待R4正式构建，本卡不改版本或绕准入，也不连接猜测服务。

```powershell
# 已完成的唯一新增fixture验证；再次执行须核相应fixture ledger。
& local/migration72/debug-venv/Scripts/python.exe tools/observe_client72_hardware_fixture.py `
  --prior local/migration72/evidence/d02-r2-synthetic-sw-no-exceptions-v3.json `
  --output local/migration72/evidence/d02-r2-hw-switch-fixture.json
python -m unittest tools.test_client72_startup_observer tools.test_debug_channel72 `
  tools.test_client72_single_hardware tools.test_client72_hardware_integration -v

# 原始游戏：尚未执行；仅在主线审核并明确允许本轮后使用新输出文件。
& local/migration72/debug-venv/Scripts/python.exe tools/observe_client72_startup.py `
  --main-observations global-dispatch-hardware `
  --prior-observation local/migration72/evidence/d02-r2-d01b5e-run1.json `
  --output local/migration72/evidence/d02-r2-d01b5g-run1.json `
  --summary-output local/migration72/evidence/d02-r2-d01b5g-summary.json --seconds 120
```
