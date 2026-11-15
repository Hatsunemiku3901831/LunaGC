# 旧号 121 Avatar 基础属性与成长投影计划

`R3-OLD121-AVATAR-PROJECTION-SCOPE` 的限定来源审定及五字段计划现已实施，投影与限定真实 JVM loader 已验收，见[实际结果](CLIENT_72_D02_OLD121_AVATAR_BASE_GROWTH_20261115.md)。以下为原计划、输入、预算与许可边界，不代表完整资源、旧号恢复、R4 或 ready 已完成；D-02 / F-05 / D02-R6 仍为 0/6。

## 范围与输入

仅针对实际旧号 121 个 Avatar 的 `id`、`hpBase`、`attackBase`、`defenseBase` 和 `propGrowCurves`，形成片段和限定真实 loader 验证。已有逻辑读出与旧号交集的 `10000007` 直接复用，其余 120 行需要新的实际读出。旧号 Avatar、当前 Depot 和保存 Skill 的键/span 覆盖不能当作完整 Avatar 逻辑字段；121 Depot 已有原字段报告直接复用。

同 typed `58272` 继承 `63287` 的共享 consumer、既有 136 非零样本及准确原 getter/字段来源支持新跨行读取的限定规格，并非新行结果。新行须核固定全表中唯一 key/span 与原 payload hash，再用原资源 ctor、parser 与已验 getter 取得五字段；ID 必须等于 key，growth 列表保留原序和准确 enum，不能把 raw 对象记录直接当逻辑值。固定来源及完整代码、资源、codec 和 native 范围 pin 见私有 `local/migration72/d02-resume-20261115/old121-avatar-projection-scope/spec.private.json`，SHA-256 `315499f7309678d215c313c8250ad58e94024a5f070c33cc21352102a6497b7b`，与同目录 `范围与规格.zh.md`；资源 snapshot/table/seed 沿该规格固定，不作为 Proud 实际输入。

新 120 行各以原资源 ctor/parser 和已验原 helper `153a7f940` 对 property 1、4、7 在 level 100 构造独立 oracle，共 360 例；以原 getter 基值、原 binding、已验 Curve100 factor 的 float32 结果逐 bits 对照。Curve100 仅重建该单行作为此 oracle 输入，不重跑整套曲线；第 7 号 Avatar 的旧结果直接复用。缺失状态仍 unknown；未知 enum、缺失或重复 binding、非有限 float、错误 span/ID 均拒绝，不补零或空值。

## 执行限制与验收

旧 runtime 的 `1405336c0` 预注册须从两张 model 表移除，并在任何原生调用前使模型分派优先拒绝该地址。仅允许既有四个 model 和原 pin/native 范围，不新增 callee、allocator、IFix 或 allowlist；顶层 host typed 资源存储只用于资源 row 和 Curve100，不代表完整 Avatar 构造。新行若遇 A 冻结地址，保存准确 site/target/trace 并立即停止，不取返回或换路径续接。

初始化、120 行 ctor/parser、所有 getter、Curve100 和 360 次 oracle 共用 **2M 累计 hook 上限**；不按行重置。一轮首错或达界即保存 partial，未完成结果不判通过，不增加预算或自动重试。输出应包含 121 条片段、逐字段 source/span/hash/floatBits、360 个原 consumer 结果、完整调用账及保留的未知。

Terra 仅新建指定 driver、Java 测试及私有输出；不修改生产代码、资源、旧工具或旧报告。真实 JVM 仅由唯一 `launch_timeout` 执行：隔离 worker 先用真实 ResourceLoader 加载已验 100 Curve，再加载 121 个五字段片段，核行数、字段 bits、level 100 三 getter 与原 consumer、完整拒绝记录、map/fetters 恢复及 loadedAll 不变。只运行新测试类，不建 Player 或完整 Avatar，不做保存恢复；Java 缺字段的零/null 不构成来源许可。无客户端、数据库或部署。

critical、Skill、defaultDepot、initialWeapon、Promote、equipment、Proud 和 Scene 均排除；121 Depot、phase6、Talent 等已验结果只读复用，不重跑。A/B、preview 和冻结 tail 及其依赖保持冻结；此包不消费其返回或产物。后续仅在实际输出、原 consumer 对照和限定 loader 均通过后登记其具体验收范围，不以局部成功替代父项或客户端验收。
