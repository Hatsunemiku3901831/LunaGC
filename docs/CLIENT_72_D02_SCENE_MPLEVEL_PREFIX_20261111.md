# F-03d3s2 MPLevel 前缀与置位原消费者

[原 131 B parser](CLIENT_72_D02_SCENE_MPLEVEL_PARSER_WINDOW_20261111.md)先检查 reader。仅 `reader+0x20` 非零时调用原 helper 请求 1 B，随后重新读取 `+0x10` 数组和 `+0x1c` 游标。数组与游标通过原门槛后，内联读 1 B，游标加 1，再按 `(((byte ^ 0x08) + 0x3e) & 0xff) & 0x20` 分流。

- 置位分支将同一 reader 和 `this+0x10` 的 byref 地址交给静态 22554.m173974，输出槽为 field350252 的 `Dictionary<string,string[]>`。上游 PathHash 的第二 QWORD 及同对象注册顺序复用既有结论。
- 清位分支将输出地址和另一个 RIP 取值交给原尾跳。其精确泛型绑定和输出行为仍未知。条件 reader helper 的执行效果也单独保留。

原窗语义审计三次 125 项断言、27,312 B，SHA-256 `698cf34f6544786f6343dbdbb63ecd3a354d05e76a2dee97228ce051681d5632`。第三次首次运行因计划新增同行前缀而在 audit 报错，按确切任务行匹配后复核一致。REA 核对了原 131 B 哈希，Windows provider 对 headerless x86-64 深度解码不可用，逐指令复核使用既有 Capstone 结果和原 bytes。

主线用五份 stock 哈希、原 131 B、原 E9、唯一 typed/普通 AOT 和唯一 exact/containing PE 边界绑定置位消费者。初始 2,048 B 上界在 body 读取前拒绝了真实 2,675 B 边界，原报告保留。随后重新固定同一完整 extent，两个 fresh 各 628 条连续指令，八份原件哈希一致，body SHA-256 `4a6f5e8cdc39dd4be6924ca459e12d1c401a446ffd30903bf5c391fdf963cbfd`。仅新增这一个原窗。

复现在 `local/migration72/d02-resume-20261111-asc/` 的 `scene-parser/audit.private.py` 与 `scene-mplevel-positive-window/collect2.private.py`，使用新输出名。实际分组值、World 实际对象与 slot114、Pool/Scene、组件生命周期和正式发送仍待独立证据。
