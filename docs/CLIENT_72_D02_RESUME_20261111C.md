# D-02 恢复执行索引（2026-11-11C）

本页记录 `DOC-D02-RESUME-20261111C` 的本轮派工、输入、证据和停止点；任务状态和验收以 [Project](../Project.md) 为准，复现命令及完整原件清单见各专题。接手基线 HEAD `1410df707cf52794afadbd2e9bfec9c7be01026b`；国服 7.2、Windows portable。此前用户要求暂停及文档重规划均为历史事实，用户现已要求继续至 D-02 全过或所有可独立任务明确阻塞。该恢复不解除安全拒绝，也不放宽 ready 或验收门禁。

D-02 / F-05 / D02-R6 六项客户端验收仍 **0/6**；R4、R5、R6 均未开始。现有局部成果和本轮交回均不等于自然客户端入场或资源 ready。主线负责管理、独立验收；用户允许需要联调的部分由主线承担。共享 Git/index/提交、构建和运行现场须唯一执行；本轮现场已明确交由主线接管，`intake_audit` 负责独立验收及只读收尾，不双执行。各专题写者不操作共享 Git/index。本页与 Project/Harness 的写者为文档代理 GPT-6.1 Terra xhigh；不碰 README、交接、其他专题、源码或冻结报告。

## 本轮任务与证据状态

| 任务 / 执行者 | 固定输入和输出 | 当前状态 / 验收出口与停止点 |
| --- | --- | --- |
| `F-03d3s2/SCENE-READER-FACTORY-SEMANTICS` / GPT-6.1 Sol max | [工厂语义专题](CLIENT_72_D02_SCENE_READER_FACTORY_SEMANTICS_20261111C.md)；工厂238B SHA-256 `f3c65163277d38595ff0b48bac94e1a6a047c097f71b28f518c545b028a9c905`，旧资源索引 `a57966dabbc34b177d6a03bd76dfc91e02ba6bd9fe606e9974e66c41bb9dba13`。完整输入 pin 和私有审计命令见专题。 | **已完成限定验收**；第三8610B报告 SHA `38ab32d772b1d03ed367e5dc69889e244d4b325bb4069312efc6d01ad26def69` 同双 fresh，重建4旧窗1338B/356条。普通路同对象返回且 `+1c=0` 是静态事实；自然有效 backend/cursor/refill、对象生命周期及 wrapper 路径仍未知。其430B后端单窗现已单独通过；不自动扩展 callee 或碰拒绝依赖。 |
| `E-01d1/R3-PROUD-SCALAR-SETTER-SEMANTICS` / GPT-6.1 Sol max | [setter语义专题](CLIENT_72_D02_PROUD_SCALAR_SETTER_SEMANTICS_20261111C.md)；setter157B SHA-256 `5c7bd52bc54dd9ebfa01137bd2b56c741eb5f9bb2ad05331158a9c85aa20d3dc`，历史自然 inverse/seed 输入 `c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d`；17 个输入 pin 见专题私有审计。 | **已完成限定验收**；第三11502B review SHA `b440b24c5729992e569a3c62d25ccd5b7f7c798bd8ac39ab0a95663e63ea09ed` 与4795B oracle同原，17 pin、22/13 锚点、10 向量及1024 byte左逆条件核对通过。历史自然 inverse/seed 与重建 forward 分立；仅条件读回关系，不是实际 encoded、实际 row、自然当前采用或资源 ready。 |
| `E-01d1/R3-PROUD-CONDITIONAL-SCALAR-ROW-PROJECTION` / GPT-6.1 Terra xhigh | [条件投影专题](CLIENT_72_D02_PROUD_CONDITIONAL_SCALAR_PROJECTION_20261111C.md)、`tools/migration72/proud_conditional_scalar_projection.py` 及对应纯回归；固定两字段桥 SHA-256 `91f62c32da64a025ea08db4c95d16d2b470c1368a3025dd01b748adc6046f94a`，双 fresh 输出 SHA-256 `46965626ff8c1c1441eb8dc109f6706fe24b9174631b1a4b1568a80e4f877839`。 | **已完成限定验收**；第三5079B与双 fresh同字节，5项纯回归、原桥字面字段、10 U32向量及1024 byte左逆核对通过。actual 保持 null/null/false；fixture 不证明实际对象、编码、行或采用。固定 pin 漂移、来源冲突、需新现场/PE/global/callee 或触冻结依赖即停，不生成 Java 可加载资源。 |
| `F-03d3s2/SCENE-READER-BACKEND-IDENTIFIER-ONE-WINDOW` / GPT-6.1 Terra xhigh | `tools/migration72/scene_reader_backend_identifier_window.py`、对应测试及[机械窗专题](CLIENT_72_D02_SCENE_READER_BACKEND_IDENTIFIER_WINDOW_20261111C.md)；固定 request SHA-256 `4d64dbb5a6189096ef413f0ea7f8380a45b09fd451d705ea4517cd65a68f6f7f`，预期430B code SHA-256 `a0da494e959a2705129d4e6c772f9d0c3c8fb58de17842556e6117dbd667d2d1`。规格与完整门禁见[工厂语义专题](CLIENT_72_D02_SCENE_READER_FACTORY_SEMANTICS_20261111C.md)。 | **已完成限定验收**；430B/110条/38引用，第三九件同双 fresh，9测试与纯 audit 通过；audit 1366B SHA `308096b48e49c8c19db58899b4d7bddc0d929d18d5bb1541c02d5fa3099c50ee`。限制为单个机械窗，不跟 callee。来源、caller/AOT/pdata、范围、可执行/文件映射、hash或连续解码不符即留诊断并停；遇已拒绝 CALL/data 边只保存边并停止该边及依赖。 |
| `F-03d3s2/SCENE-READER-BACKEND` 语义 / GPT-6.1 Sol max | [backend语义专题](CLIENT_72_D02_SCENE_READER_BACKEND_SEMANTICS_20261111C.md)，430B已验原窗及其固定来源。 | **已完成限定验收**；9183B来源检查 SHA `9d187719bde08e7a117d00529c10b010fc25f17ada4bffa306ee6d1b1e2f68de`，22 pin/19值流锚点及metadata/signature独立核对。自然backend/cursor/refill仍未知。 |
| Scene LoadedBundle 单窗 / GPT-6.1 Terra xhigh | 固定request SHA-256 `0174f4ee26b991b3c417dd0d9e2dfba2d283dcb6a6eb25632e09eca92551af05`；双fresh真实pdata完整220B code SHA-256 `9f1480af9cb4063240eb6445a62813f945f1ac73bfc0730e8edec0dbb4690e9e`，70条/15引用，8门禁及纯audit同。 | **已完成限定验收**；第三九件同双fresh，8测试，纯audit SHA `1909e4981b904fa9b5edd198200adfdb28c1757c81196b942a4c88ebf07b5ca1`。不升级自然reader。 |
| `F-03d3s2/SCENE-SLOT114-TRIGGER-CONDITIONS` / 独立分析 | [触发条件专题](CLIENT_72_D02_SCENE_SLOT114_TRIGGER_CONDITIONS_20261111C.md)第三6548B SHA `b89e43451a91f53d4b62e3dd158cc822feeeed7763cc3220c2131378327f7427`，11 pin/11坐标。 | **分析已完成限定验收**，allocator停止边、RSP规则和224B非总预算核对通过；World观察包仍**阻塞**：共用allocator `14828ba31→1405336c0` 停止主动触发依赖；真实World/44512缺实体、受支持同帧和RSP规则、Pool来源与整轮预算均缺。三读224B不是成熟现场包。 |
| `F-03d3s2/SCENE-READER-LOADED-BUNDLE-SEMANTICS` / GPT-6.1 Sol max | [LoadedBundle语义](CLIENT_72_D02_SCENE_LOADED_BUNDLE_SEMANTICS_20261111C.md)交回，来源检查 SHA `88a4f3340914b91c358829a30ff8dc7d3846810c5683aabad95e0f6776f95061`。 | **待独立核验**；报告区分零长度查询、有符号长度门和参数符号扩展，最终返回为 `141139040` 生产的opaque U64，RBX对象不能称buffer；自然有效范围/refill未知。 |
| Scene token生产者单窗 / GPT-6.1 Terra xhigh | 固定request 15731B SHA `eb57e978e7792002c4f8589529621add7080c00093e49125622e6144494a0ba7`；[规格](CLIENT_72_D02_SCENE_LOADED_BUNDLE_SEMANTICS_20261111C.md)。 | **进行中**；owner非ordinary、AOT实际不适用；unique pdata真实extent≤1024B/256条，无unique先存诊断并0 body停止，拒绝边不续。 |
| Proud初始化writer来源定位 / GPT-6.1 Sol max | 已将table/seed绑定owner17408，九个ordinary候选正文/写入xref仍待缩窄。 | **进行中**；TLS helper `149a75140` 在与A共享边停止，不把整个独立分析标阻塞或完成。实际hot input未取得。 |
| `DOC-D02-RESUME-20261111C` / GPT-6.1 Terra xhigh | 本页、[Project](../Project.md)、[Harness](../Harness.md)；输入为既有计划、专题及本轮主线交接事实。 | 文档初稿供集成执行者审核；不做测试、构建、客户端、DB、部署或 Git。后续由同一写者按实际集成结论更新，不提前声明已验。 |

固定 hash 只标识所列输入或产物，不表示本页写者重新读取目标客户端或执行相关研究；所有来源范围、歧义、具体复现与停止条件以各专题原文为准。旧成果包括 Talent 六个 null/空串、720 必要键与目录加载、121 Depot/605槽两叶 Java、phase6 和旧号已有结果直接复用。`threshold==phase+1` 已确定为下一突破 UI 预览，冻结该分支，不将其当当前采用。

## 现场事实与下一步边界

集成执行者报告只读接手时五 stock 匹配，portable 和 `GAME_PATH` 有效，相关进程与端口为空。PRELIVE 实际通过8项纯测试与1项 Win32 自进程测试；preflight、20保护项及138原数据文件对历史逐项零差异，原件位于 `local/migration72/d02-resume-20261111c/integration/prelive1/`。run1 没有执行启动命令，一次 CIM 未发现 YuanShen 候选，identity/sample调用0、游戏内存读取0；用户确认当时游戏未打开、没有窗口。此为历史未启动记录，不是异常退出或采样成功。

run2 主线明确接管并取得新ready后正常启动一次，用户确认标题/SDK页；identity/sample各一次，实际 partial 3读24B，Q/S非零、O为0后按cold停止，K/N/F/M未读，无重试或冷创建。用户确认正常关闭、界面正常。集成执行者只读 closeout 独立通过：原记录纯重放一致；Launch CloseHandle 实际返回 true，3个UAC父 Dispose=true，helper退出分别为 Launch 0、Identity 0、Sample 1；target/helper PID均不存在，相关进程与固定TCP/UDP为0，五stock/source/config/JAR/20保护/138原数据逐项零差异。sampler句柄JSON不记录关闭返回值，仅AST/finally保证调用且helper已退出，不称返回值已观测。原件 `local/migration72/d02-resume-20261111c/integration/acceptance1/live2-closeout1.private.json`。

当前没有待用户ready事项，不沿同一页面重复观察；以后若确有必要新现场须新实时 ready，Proud 与World/slot114不混采。World合法同帧触发和受支持观察机制未成熟，三读224B只是输入规格。具体来源、命令与停止规则见[采样准备](CLIENT_72_D02_PROUD_HOT_EQUALITY_SAMPLER_20261111B.md)及[World触发条件](CLIENT_72_D02_SCENE_SLOT114_TRIGGER_CONDITIONS_20261111C.md)。

## 拒绝冻结与独立后继

A-SECOND-RETURN-CONTRACT 及依赖、B组合 loader 仍因安全审查拒绝冻结，不运行草稿、不换模型或工具、不间接重试。A 的完整原拒绝仍缺，不能将摘要冒充原提示；B 保留原句 `This content was flagged for possible cybersecurity risk.`。现有[最小包队列](CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md)只读复用，队列开头的暂停叙述属于历史状态，不自动新增或派发 Daybreak。移除被拒动作后，仅推进能靠已有输入独立完成且产物不直接实现被拒动作的任务；局部通过不升级父项或 ready。真实门禁、认证、资源和完整旧号恢复仍约束 R4。

下一执行顺序：独立核验LoadedBundle语义，继续token生产者单窗和Proud writer来源定位；按各自真实缺口继续可独立工作。现场只在完成门禁并取得新 ready 后单独判断；R1/R2/R3 必要合同齐备后才进入 R4，继而 R5/R6。

## 已验本地提交索引

首批五项本地提交的身份与路径已核验，未推送；原核验索引 `local/migration72/d02-resume-20261111c/integration/acceptance2/commits-accepted1.private.json`。`ad1d52c` 工厂语义、`3bed8c9` Proud setter语义、`d1144c2` Proud条件投影、`f8ab2f0` Scene 430B单窗、`e9eaca5` 本轮状态与冷采样文档。新增三项验收与本次状态尚待集成提交，不预写提交号。
