# 国服 7.2 上游研究阶段交接（2026-11-14）

## 当前状态

`UP-72-20261114` 为公开资料与静态研究的阶段交付，尚未完成所有搜索分片及长尾候选。用户明确要求当前智能体独立执行，不派子智能体；本批未修改生产 Java、Proto、配置、补丁或资源，未启动服务端、数据库或客户端，未运行上游安装/构建。D-02 / F-05 / D02-R6 仍未完成；先前六项为 0/6 的记录没有被本轮改变。本轮的继续范围仅为所授权的公开资料与静态研究，不自动恢复研发或联调。

当前接手主仓库起点 `2cfddb655665d7eb8da90904761f8aa9fe5b217d`。AGENTS.md、patch、resources 原有差异及子模块引用须继续保留；完整现场、配置、进程与端口均未在本轮运行核验，不能沿用历史 PID 或运行状态。旧详细交接在覆盖前已备份到外置研究根的 `handoff_before/HANDOFF2.md`，备份和原件 SHA-256 均为 `d5a023cad5fa238b5cb4e0bded46afcb7b35425bdc4ba60fd431b6eb424fd3d`。原始响应与仓外 clone 留在该研究根，不进入 Git。

## 已完成的限定成果

- [研究报告](docs/CLIENT_72_UPSTREAM_SURVEY_20261114.md)记录需求、既有证据、候选、谱系、复用建议与下一步。新发现的 `capybr2222/LunaGC_7.2.0` 有登录、Avatar、Store 和场景源码，但 `PlayerLoginReq` 与固定国服合同冲突，不能直接替换协议。AstaPS 和资源旧审计继续限定复用。
- [分支表](docs/CLIENT_72_UPSTREAM_BRANCHES_20261114.md)固定 19 个仓库 67 个分支的 tip、根 tree、README blob、README SHA 及相对默认分支历史差异。`Neona-Xi` 的 Hard 分支相对原 7.2 tip 只改 README；AstaPS Fork 的 `quest-content-fixes-7.2.0` 有任务事件/条件增量，开放 PR #244 是另一分支，均未在本项目运行。
- [来源清单](docs/CLIENT_72_UPSTREAM_SOURCES_20261114.md)固定 23 个重点文件的 blob/SHA-256 与提交链接。[检索账本](docs/CLIENT_72_UPSTREAM_LEDGER_20261114.md)记录查询、分页、PR、失败与未覆盖入口。
- 外置 `verify_review.py` 复核 67 个根 tree/README blob、23 个文件 blob/SHA 和资源 303 的 JSON 对照通过；本地及新来源子任务按 ID 对齐后 16 行相同、整体对象不相等。该核对是离线资料检验，不是目标客户端或生产回归。GitHub 一次 502 的原记录保留，同页重试成功。

## 未完成、阻塞与避免弯路

- 依据账本继续超 1000 的日期分片和未执行关键词；Grasscutter 全部公开 Fork 列表已分页取回并按名称、描述、默认分支初筛，但尚未逐个检查所有 Fork README/非默认分支。搜索引擎结果不能代替此工作。其他平台和标签/发布未逐项深审。Codeberg 关联地址网页读取返回 `Failed to fetch restricted URL`，本批未换入口重试；Gitee 具体仓库网页不可访问。
- AstaPS PR #244 作者声称国服任务有进展，本轮没有独立实机验证；本项目已有技能事件，PR 中宽泛映射不能直接采用。资源 README 声明 7.2、同一文件名或同一行均不能证明目标 revision/权利/语义。保留非零、64 位、嵌套、列表、错误和边界的独立 wire 断言及账号、经济、保存回归。
- 原 `A-SECOND-RETURN-CONTRACT` 及依赖、B 组合 loader 继续冻结；原 A 完整拒绝提示缺失，B 原句 `This content was flagged for possible cybersecurity risk.` 已在既有资料保留。不得通过换项目、模型、代理或工具间接重试；未经用户指示不派 Daybreak。World slot114、Scene reader 和 Proud 实际语义等原有缺口详见[原恢复索引](docs/CLIENT_72_D02_RESUME_20261111C.md)及旧交接备份。
- 原 cache1 启动回执超时曾取消，sample 未派、目标读取为零；不能把旧包或旧 ready 用于新现场。用户禁止 computer-use 的专项限制继续有效。不要把本次未操作解释为当前没有相关进程。

## 下一步

1. `UP-72-N1`：用报告固定提交和现有国服输入/映射，逐层离线对照 LoginReq、AvatarData、PlayerStore、EnterScene、SceneInitFinish；输出差异、嵌套闭包与独立 wire 用例。未知不晋级；遇原 A/B 冻结依赖停止该字段。
2. `UP-72-N2`：按目标来源与真实消费者核对必要资源及 ID；不拼接 303 或改资源，未明 revision/许可时停止复制。
3. `UP-72-N3`：从账本列出的超限分片、缺页和未执行查询续查，继续完整枚举新候选的分支、README、树、Fork 和 PR。若仍有预算、访问或权限限制，保留准确入口和未覆盖范围。

状态与验收维护见 [Project](Project.md)，复现索引见 [Harness](Harness.md)。本批提交仅收录本次文档，不推送；不把既有 dirty 子模块或用户 AGENTS 变更混入。
