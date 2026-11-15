# A 原请求来源纠错

主线撤回此前“原 followup 执行请求明文全文已恢复”的验收：保存的 1528 字节、1528 字符产物是不透明的序列化字面串，不支持可读执行请求正文已恢复，也不能称正文此前已验只是现在读不到。当前仍缺可核对的执行请求正文；未来可由用户提供经允许的原正文或导出作为解除条件。无需用户猜测内部审查触因，也不自动搜索聊天、本机密钥或尝试解密。

`A-PROMPT-LOCAL-PROVENANCE` 的已完成范围仅保留原事件和不透明字面串的定位与关联；原明文验收已撤回。`A-PROMPT-PLAINTEXT-RECOVERY` **阻塞**，缺允许来源提供可核对正文。先前审阅将字面串误分类为原文，主线据此误验；原数据、旧 review 和 manifest 均保留，不以新结论覆盖或删除历史原件。

固定产物 `local/migration72/d02-resume-20261115/handoff-prompt-provenance/a-original-followup-request.prompt.private.txt` SHA-256 `7e840a42920303a763c1554c2942ff8d68641a28554f22da9941945af416918c` 未漂移，以 `GAAAA` 开头且无尾换行；具体编码或加密算法未知。对应 raw source-event 为 4349B、SHA-256 `d2b2f942d86d09c95d7c4574a02c4998e59800c056a69c5f2a04e045d0e054ca`，其 `arguments.message` 与保存字面串逐字符相等。既有 manifest 的父 JSONL 第 817 行、目标 `a_stock_delta` 和时间 `2026-11-10T23:32:33.155Z` 形成有限事件关联，不能由此恢复或验证技术请求内容、子正文或隐藏被拒工具调用。

新纠错审计 `local/migration72/d02-resume-20261116/resource-entry-only-contract-scope/a-prompt-provenance-correction.private.json` 为 3981B，SHA-256 `5e85da39ba92bd399d813e0b94e5e548a32d312e7c17693b271cab50f2fc9796`；旧 `review.private.json` SHA-256 `e07996713a3c85763533994258b80cd460a66108637eefb1e3f1578b69220206` 与原 manifest、工具及会话原件均保留。

原平台错误与执行请求正文分开：v4 的 `targetTurn.error.message` 仍已恢复，见 `local/migration72/d02-resume-20261115/a-refusal-provenance-review/a-refusal-child-turn-v4-evidence.private.json`，SHA-256 `b8f73dd7a2400908f4254ec311c4771efe25b4bbe62e79a5682a0fb01bd21179`。历史提示包含 `This content was flagged for possible cybersecurity risk.`，及 Daybreak Blue、Advanced Account Security、兼容 FIDO2 硬件密钥的资格后缀；不将后缀归给 B，不据此认定当前资格。

A 最小执行包仍缺可核对正文、技术规格及固定输入；A/B 原动作与准确依赖、preview 始终冻结，旧错误明文结论未导致派工、运行或解冻。B-LOADER-COMPILE 候选及未经用户指示不自动 Daybreak 的边界不变。普通独立 source/host 工作不依赖该错误结论，继续按原范围推进；D-02 / F-05 / D02-R6 仍为 0/6。
