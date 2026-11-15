# Proud 既存 cache 新现场身份绑定：离线验收

任务 `R3-PROUD-CACHE-NEW-LIVE-PLAN/IDENTITY-BINDING`，父项 `R3-PROUD-EXISTING-CACHE-CAPTURE`。**新包接线及离线验收已完成；真实 RunAs、目标启动与采样仍未验。** 旧 sample wrapper 绑定旧 Launch 回执，不能用于新轮。本包不改变原 sampler 或其获准输入范围。

## 接线与范围

新私有 driver 复用原来同一次 identity 的 11 个 API 调用，把本轮 Launch 的身份与该结果先比对，再将预期身份传给原 sample；不增加身份预读。身份不匹配即在 sample 前停止。sample 上限仍为 68 个 API 调用，合计 identity 11 + sample 68 = 79；目标读取上限仍为 52 RPM、298B。这里的上限是已验合同，不是已经发生的目标读取次数。

协调包维持同一总 90 秒起点和截止；driver 自入口起算 30 秒，协作截止取二者较早者，不通过延后起点或延长等待掩盖失败。正常、取消、超时和身份变化各有独立出口；晚到结果不能覆盖既有协调结果。本包没有新增 getter、cache/Default 创建或对 A/B 拒绝动作及产物的依赖，也不将旧 ready、PID 或回执当成本轮输入。

## 验收与复现入口

作者的 driver 7 项与协调 5 项离线测试通过；独立集成复核绑定/期限/driver.main 10 项、真实自有协调 stub 5 项，3 个 PowerShell 文件语法检查 0 错误；33 个来源 pin 与原9件固定产物匹配。独立报告的模拟成功路径为 11 identity + 68 sample API、52 mock RPM；实际 RunAs、目标启动、目标读取和 PE 读取均为 0。原完整 sampler 测试集本轮未重跑；上述结果只覆盖新接线及离线边界。

固定私有包 `local/migration72/d02-resume-20261115/cache-live-plan/sample-envelope-v1/` 包含 driver、协调/分发/helper 与 `source-seal.private.json`。封存清单 SHA-256 `e85f8a53216151945b5e9695e9d07028516ec2fe89d2a1c34438a7728906ad78`。作者离线测试及原始结果入口为同级 `sample-envelope-tests/`；独立复核脚本为 `local/migration72/d02-resume-20261115/launch-timeout/verify_sample_integration.private.py`，结果在 `sample-integration1/`。该复核脚本使用固定输出目录和独占创建；复现前先审查脚本及输出位置并使用新的独立目录，保留现有原件，不以旧输出覆盖重跑结果。

独立回执 `local/migration72/d02-resume-20261115/launch-timeout/sample-integration1/completion.private.json` 为 4083B，SHA-256 `10dd5cc2bb822e0e71106af77ca75e44f291c9c9de86998bbbe59cf59b6e5c60`。它只证明固定合成输入、mock API 和自有 stub 的离线结果，不证明目标的任何实际值。

## 当前现场状态与未闭范围

主线再次执行只读前检，旧27 pins、149保护/原数据、五 stock、config 与 JAR 匹配；另核 33 个 sample 来源 pin 一致、准确 sample 控制进程为0。前检 `local/migration72/d02-resume-20261115/launch-timeout/fresh-preflight-20261115T0156274741236Z.private.json` SHA-256 `934a0b368a0c6d839c1df05d2e08d7e6e2daa7ba5ebaff780138766a455833ad`，标记 `ready=false`，真实 Launch/UAC/RPM 为0；这是该时点只读快照，不是现场 ready 或启动验收。

主线已请求本轮新实时 ready 和用户反馈，等待回复；现场尚未开始，由主线独占运行控制。真实 RunAs、cold/miss、实际 M、编码输入、row/current 与同 epoch 均未知。即使将来取得一次采样，也须分别核对实际结果和边界，不自动晋级 resourceReady、D02-R3 或 D-02/F-05/R6。A/B 安全拒绝及准确依赖继续冻结。
