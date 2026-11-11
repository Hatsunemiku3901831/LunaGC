# F-03d3s2 / Reader refill 条件状态投影（2026-11-11C）

任务 `F-03d3s2/READER-REFILL-CONDITIONAL-STATE-PROJECTION`，父项 D02-R1，同时服务 Proud reader。此纯标准库投影只在显式给定的原PC及前缀条件下计算已验字段位值和读取意图；条件输入不证明自然可达。没有执行或模拟间接CALL、`140514ee0`分配、`141123190`读取、返回 count 或字节内容；naturalReady=false，不升级自然reader、Scene或Proud。

固定规格 `local/migration72/d02-resume-20261111c/scene-reader-refill-semantics/conditional-state-projection-spec.private.json` 为4870B / SHA `b4d2dec7931b58e453f08edcc9e5d6a83f71d19e1e9a7dbb3cebd419ee5b4331`。固定原 reader body 486B / SHA `dcf901e0952f9fa49adcf090c5b97510953019358c32f64bdf298840f8441999`，asm SHA `2f86e6324b5b82c4c3f5388144ed935d83e09180c8d1e006e30913184da18e48`；入口验规格/原源 pin，按原地址和hex拼回整个body。主线已接受原窗 `integration/acceptance7/scene-refill-independent1.private.json` 的第三次独立验收。

输入入口验证一次：token零入口只需显式 `start` 与零 token，未提供的+10/+18/+1c/+28均为 `unread/unknown`，不读取count或slot；若调用方提供字段值，则作为条件输入原样保留，仍标记未被原路径读取，不代表自然状态。其他两种条件起点使用完整U64 token、I32位值 base/cursor/count/+28、数组身份与容量及已给slot；不对count附加正数或clamp。三种起点为 token零入口、已知slot非零且原array存在的 `47b2` 条件后、已给slot值的 `48ef`。输出 before/after 已知字段位值、选定原PC、比较结果与 pending 外部调用/准确stop；未知包括外部效果、有效字节数和数组内容。非零token原64位及+28保持；初次领入不重置base/cursor；已有array路径恰好相等也补读，offset按wrap32后signed扩展至64位。对于不够容量的分配需求在 `47f1` 前停；null领入先写+10空，再停在 `493b` 前；补读在 `4929` 前停。没有对未知外部返回或异常作任何成功假设。

标准库工具 `tools/migration72/scene_reader_refill_state_projection.py` SHA `3422ccdb7d297c361bace01e4fb19c5eb5584d0e010c474377a4beac41a4bfc5`；独立字面期望测试 `tools/migration72/test_scene_reader_refill_state_projection.py` SHA `0a5fe733d3974d2708616ff97907e6e892ca8563e149706d1af73240780b942c`。`python -B -m unittest -v tools.migration72.test_scene_reader_refill_state_projection` 共5项通过，逐项断言规格11个固定案例并新增仅token输入及已提供值保持两项回归：token零、高64位token缓存命中、恰好相等、扩容停止、领入保持、负offset、空slot、零count末尾、负count、I32和base回绕。期望值为测试中文字面常量，不从driver结果反造；测试日志在私有根 `tests-v2.log`。

私有根 `local/migration72/d02-resume-20261111c/scene-refill-state-projection/`；返修后 `fresh3`、`fresh4` 的 `projection.private.json` 实际逐byte一致、各16060B，SHA `f93550192fc128da766e260d4c221d74e27d2fe61d48c2bc54a3f21e1821327e`。旧版误要求零token路径提供其他字段，且注入合成默认值；主线已在 `integration/acceptance9/projection-v1-freeze/manifest.private.json` 封存旧源码、输出及拒绝日志，旧 `fresh1/fresh2` 不覆盖。每项均含 `naturalReachabilityProven=false`；输出账 `nativeExecution=false`、`currentPEReads=0`、`naturalReady=false`。这不是后续API的成功、短读或EOF测试。

主线唯一集成执行者第三次（输出名先确认不存在）：

```powershell
python -B -m tools.migration72.scene_reader_refill_state_projection --output main-third-v2
python -B -m unittest -v tools.migration72.test_scene_reader_refill_state_projection
python -B -c "from pathlib import Path; p=Path('local/migration72/d02-resume-20261111c/scene-refill-state-projection'); a=(p/'fresh3/projection.private.json').read_bytes(); b=(p/'main-third-v2/projection.private.json').read_bytes(); assert a==b; print('byte-identical',len(a))"
```

期望11 cases、5测试通过、byte-identical 16060，上述 SHA 一致；任何失败保留实际输出并返回。只新增指定离线工具、测试、专题及忽略目录；未操作Git/index、构建、Java、DB、端口、客户端、调试器或部署。Project/Harness/HANDOFF与本地提交归主线。
