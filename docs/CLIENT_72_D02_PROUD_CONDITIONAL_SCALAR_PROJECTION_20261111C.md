# E-01d1 / R3-PROUD-CONDITIONAL-SCALAR-ROW-PROJECTION

2026-11-11C，父项 D02-R3 / R3-OLD-SAVE-DEPENDENCIES。仅对固定桥中 key 22101 的两个 present token 做离线条件投影；这不是实际 Proud row、目标 CPU 执行或客户端验收。依据与限制见 [scalar setter 语义](CLIENT_72_D02_PROUD_SCALAR_SETTER_SEMANTICS_20261111C.md) 和 [行桥](CLIENT_72_D02_PROUD_GATE_COMPARISON_ROW_BRIDGE_20261111B.md)。

| 41105 字段 | managed / native offset | token span；presence | setter 输入 | fixture 编码 | fixture getter |
| --- | --- | --- | ---: | --- | ---: |
| 203376 | 0x90 / 0x30 | [218,222)；P & 0x4000000 | 221 | 403d555f | 221 |
| 203373 | 0x84 / 0x38 | [226,230)；rawH & 0x800 | 1 | 403d55c4 | 1 |

条件读回分别为 221 和 1，要求所选 setter/getter 长度及物理内存门通过、读取的 DWORD 仍为 setter/copy 的结果、两次 seed 相等、四个实际访问位置满足左逆。历史样本的 inverse 和 seed 为既有真实读数，但不是原子快照；forward 通过 inverse 数学重建，未被实际观察。这个 fixture 不证明自然 Proud 行曾使用它。实际 encoded、实际 getter 均为 null，actualManagedObject 为 false；reader/backend/cursor、实际 handle、QueryWrapper out0 及寿命、实际表示和其余必需字段仍未知。map hit、当前采用、resourceReady、D02-R3 和 D-02 均未由此通过。下一突破 UI 预览分支继续冻结。

## 实现与实际验证

- [离线工具](../tools/migration72/proud_conditional_scalar_projection.py) 固定校验九个规格输入及审核请求的大小/SHA；入口核对两个字段身份、偏移、span、presence、原 token 及其变换，缺失或不符即拒绝。仅重建四段 forward 和计算两项；不解析完整 row。输出中的来源包含完整相对路径及 pin；PC 由原桥保留。
- [独立回归](../tools/migration72/test_proud_conditional_scalar_projection.py) 实际运行 `python tools/migration72/test_proud_conditional_scalar_projection.py -v`，5 项通过。独立字面表核两个字段及未知状态；十个固定 oracle 覆盖 0、非零、跨 byte、32 位高位和最大值；对 221 的实际 forward 项及 getter seed 分别做不满足条件的反例，读回均不相等。入口还测试错误字段、span、token、presence、source pin 及缺失 presence 的拒绝。
- 静态路径模型用字面 PC 核 null、每个长度门以及 0xffffffff 的最高边界；区分 setter 错误前未读取 payload/seed、getter 长度错误前已读 seed/stored。错误路径只记录原 CALL 边，不模拟异常返回。此模型测试不是原生 CPU 执行。
- 在 `PYTHONDONTWRITEBYTECODE=1` 下，两个独立 Python 进程分别生成私有 `fresh1.private.json` 与 `fresh2.private.json`；逐 byte 相同，均为 5079 B、SHA-256 `46965626ff8c1c1441eb8dc109f6706fe24b9174631b1a4b1568a80e4f877839`。输出根为 `local/migration72/d02-resume-20261111c/proud-setter-semantics/conditional-row-projection/`；创建模式拒绝覆盖已有文件。
- 未执行客户端、JVM、构建、数据库、端口、部署、PE/global/live 读取；无新增实机验收。A 第二返回/allocator 同 epoch 归属及 B 组合 loader 保持原拒绝冻结，本包不依赖或处理它们。未操作 Git index 或提交；公共索引交集成执行者更新。

## 集成第三次执行及独立核对

由集成执行者在 checkout 根执行一次：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_conditional_scalar_projection.py --output main-third.private.json
```

输出写至上述私有根。先确认 `main-third.private.json` 不存在；若已存在，保留原件并停止，不覆盖。核对该进程退出码为 0，再逐 byte 比较三个文件及 SHA；独立对照原桥的字段、偏移、token、presence、PC 和上表，使用固定 `fixture-oracle.private.json` 的字面向量核对编码和 getter，核对 scope 的 null/false/零值及非原子、重建 forward 标记。不得由 driver 输出反造期望；第三次尚未由本执行者运行或宣称通过。

真实 encoded 首缺是实际 forward 及其与 getter inverse/seed 的同源关系；即使闭合仍需自然 reader、表示、wrapper/生命周期及其他行配置证据。不能补空 addProps、paramList 或 OpenConfig，也不能将本结果用于构造 Java 可加载资源。若 pin 漂移、来源冲突、需要新现场/PE/global/callee 或涉及被冻结依赖，停止相应动作并保留证据。
