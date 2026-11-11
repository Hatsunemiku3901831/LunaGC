# E-01d1 / R3-PROUD-EC8-CONDITIONAL-TARGET-PREFIX

2026-11-11C，父 D02-R3。按[已审合同](CLIENT_72_D02_PROUD_EC8_LOCAL_BINDING_SEMANTICS_20261111C.md)核验条件入口 `0x1408b9780` 的首个控制转移。第一条完整指令即为 5B：

```text
0x1408b9780  e9 1b fd ff ff  jmp 0x1408b94a0
```

独立字面计算：`1b fd ff ff` 是 signed rel32 `-741`，`0x1408b9785 - 741 = 0x1408b94a0`。该目标仅是原指令编码的直接边；没有读取目标或 terminal 后一字节。此入口只在前置局部 store 和当次间接 CALL 选中该地址的条件下相关，不证明当前 callback、自然执行、完整函数、seed/quarter 算法或资源 ready。

## 读取与验证

[采集器](../tools/migration72/proud_ec8_conditional_target_prefix.py) 每次先核冻结 request 的 24 项来源/decoder、原 14B LEA/store 字面算术及条件标记，随后核五完整 stock；每次 stock hash 输入 550661784B 单列。新 PE metadata 用两读：`[0,4096)` 与 `[384,904)`，共 4616B、去重 4096B；确认 AMD64/PE32+、preferred base 和唯一可执行 file-backed mapping 后保存 `prefixRead=0` 的 pre-prefix。使用无缓冲文件对象，从入口起每次只读一字节并尝试一次固定 Capstone 解码。五次请求地址依次为 `0x1408b9780` 至 `0x1408b9784`，第五次恰完成 JMP 并立即停止；没有对 `0x1408b9785` 的请求。pending tail 为空。

两次 fresh 的四件文件逐 byte 相同，位于 `local/migration72/d02-resume-20261111c/proud-ec8-local-semantics/conditional-target-prefix/{fresh1,fresh2}/`：

| 文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| prefix.bin | 5 | `f43d94be74a12dacdfa8746d56e13b9f52fdca42bfd1a632d51407e8f8662cda` |
| prefix.asm.private.json | 360 | `87b27f1b7c3b76e38731fbf34df48cc52e5c2fcfd20b1ab8da3395bc8e08a039` |
| pre-prefix.private.json | 8443 | `2d789244d134027fe1a2efc883af4038e42d379bca28b3d87bcc57dafb514167` |
| result.private.json | 10603 | `d931c1113fcc52c7606a33bcb2d8b8d94b2002cae62b811f3961a2a1d896b97d` |

[独立回归](../tools/migration72/test_proud_ec8_conditional_target_prefix.py) 最终 6 项通过：可控 reader 覆盖 E8/E9/EB/Jcc/C3/C2/FF 间接转移的逐字节读取和停止、rel8/rel32/RIP 算术、多字节及 15B/64B/16 指令门、未完尾部、decode gap、unsupported flow、已知拒绝数据边和身份/映射前置失败。合成结果不作为实际 target 证据。纯 artifact 审计通过；另以保存原件独立核字面 E9、signed 位移、五次连续单字节读取/解码尝试、终点和未知标记，不调用 decoder 或读取 PE；`audit-independent.private.json` 896B、SHA `1eb7fba9e136489728edec8cbb941c755ae0d1a51f9c29af25d46c35808598b4`。

本次新 exception/pdata、AOT、caller、邻窗、global/cell、callee、xdata、target CPU/live 读取为 0；没有执行注册、callback、runtime 模型、客户端、构建、JVM、DB 或部署。actual forward、seed 写入/epoch、encoded/getter、当前 callback target 均未知；actualRow、resourceReady、D02-R3 和 D-02 未晋级。A/B 已拒绝依赖保持冻结。

## 集成第三次核对

集成执行者确认 `main-third/` 与目标审计文件不存在后，在 checkout 根执行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_ec8_conditional_target_prefix.py --output main-third
python tools/migration72/proud_ec8_conditional_target_prefix.py --audit fresh1 main-third --output audit-main-third.private.json
```

第一条退出 0 后执行第二条；若身份或读取结果不同则保留原件交主线，不扩窗。另从原 `prefix.bin` 独立核 `e9` 和 signed rel32、五次连续 1B 账、终止位置和未观察状态；不得追读下一跳。第三次由集成者执行，本轮未执行或声称通过。未操作 Git index 或提交，共享索引由集成者维护。
