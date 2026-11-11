# D02-R3 标准缓存单 key FindEntry 固定来源

任务 `R3-PROUD-STANDARD-CACHE-SINGLE-KEY-FINDENTRY-SOURCE`；本包仅准备后续 separately authorized 的既有标准缓存 key 22101 只读方案。实际缓存内容、命中、P、当前采用和客户端入场均未观察，不推进 `resourceReady`、D02-R3 或 D-02。父线独立第三次来源验收尚待执行；本包不声明普通或泛型 AOT 绑定。

## 来源与边界

固定请求 `proud-nativewrapper-ctor-borrow-contract/findentry-source-request.private.json` 为 12006B，SHA-256 `8bc6f7abafa33dce6df4010c226e212559323c564c310eb036ed999e14abb903`。采集器核五份 stock 全量 hash、请求所有 pin、已验两份 689B caller 完整重组、准确 10B `4889d989fae84ea3100a` 和 E8 算术、字段 400191 的 `Dictionary<U32,63831>`、open generic m10194 登记地址零与普通精确匹配零；旧 manifest 仅取 entry/end/hash。没有读取或猜测泛型槽。

修正版 `v2-fresh1/v2-fresh2` 两次前检均先保存 bodyRead=0 的 boundary/pre-body。当前 pdata 唯一 exact/containing/overlap 均为 `[1578b66f0,1578b6987)`，record RVA `1b0736cc`，12B `f0668b1787698b170c1a0d05`，SHA `92f64c0409636d823d95b97cb32c624090eb0d3199e7e5c4d47847d668d2b36f`。完整 663B 的 SHA `eb3bd9ba203b582c2e9b151b1ea8b369e0da775a6aeeccdf81f5b6450bff8b3b`，连续 177 条。独立产物审计重组全部原字节并检查 33 个直接相对控制位移、两个 RIP 位移及 caller E8；修正版 raw/asm/pdata 与独立审计原件完全相同。所有 CALL/RIP 仅索引，未读取其目标内容。

每次实际读取账：五 stock hash 输入合计 550661784B；PE 头/节表两次读取 4616B、去重 4096B（上限 8192B）；exception directory 一次读取 14344080B；所选 pdata 12B 摘自该内存索引、不重复读取；body 一次 663B，bounded evidence 675B。AOT、fresh caller、其他函数体、global/comparer、目标进程/CPU、live 各为 0。原始 path/账与哈希在私有目录；未生成实际采样。

## comparer=0 的限定原窗观察

以下仅描述已读机器码的正常比较分支，不是通用 Dictionary 合同，也不是实际 cache 命中：

- `rcx+20h` 是此路径的 bucket array；为空时返回 `-1`。非空后取 `rcx+18h` entries、`rcx+28h` comparer；comparer 非零转另一支，本文不跟该支虚调用。
- comparer 为零时，hash 为输入 U32 的 `key & 7fffffffh`；22101 的 hash 位样为 `5655h`。以 bucket array `+18h` DWORD 长度作有符号除数，求余并作无符号边界检查；长度为零会触发除法错误，不可当作正常 miss。随后 entries 空指针转错误边。
- bucket DWORD 位于 array `+20h+4*remainder`；初始 index 为 bucket DWORD 减一。entries 数组长度为 `+18h` DWORD，entry 起点为 `+20h+24*index`：hash `+0`、next `+4`、key `+8`。调用者在已验 689B 中以 entries `+30h+24*index` 取对应值/P；本体没读取 value。
- 只有从选定 bucket 可达、且 index 经无符号比较小于 entries length 的 entry 才进入比较；hash 和 key 都相等即返回该 index。不匹配时先递减本地循环预算（初值 `max(signed length,0)+1`），为零转错误边，再读 next；next 不在无符号长度范围则返回该 next 位样。初始 index 不在范围也直接返回其位样。因此只有正常空链的 `-1` 可以按调用者约定判无命中；不能把任意异常/out-of-range 返回都强行归为 miss，也不能仅扫描相同 key 或信任未验证的 stale/free entry。
- 并发修改、对象有效性与同 epoch 稳定性未由本窗保证。碰撞链超过后续授权读取上限应报告 incomplete；后续必须另制只读预算、观察支持与授权，不使用此前热 Equals 范围。

## 验证、命令和保留失败

运行 `python -m unittest discover -s tools/migration72 -p test_proud_standard_cache_findentry_window.py -v`：6 项通过，含 caller、类型、唯一 pdata、短读/hash 漂移、body0 和严格 384 指令 cap。两次采源分别为 `python tools/migration72/proud_standard_cache_findentry_window.py --output v2-fresh1` 和 `--output v2-fresh2`；纯产物审计为 `python tools/migration72/proud_standard_cache_findentry_window.py --output audit-v2.private.json --audit v2-fresh1 v2-fresh2`。父线可另执行完整第三次 `python tools/migration72/proud_standard_cache_findentry_window.py --output main-third`，再将其与 `v2-fresh1` 审计；本包没有代行第三次。

首版测试发现超 cap 时先解码第 385 条才停止；实际首版采集为 177 条，从未超过 cap，但该实现不满足上限，因此 `fresh1/fresh2` 和首审计保留、不作最终 epoch。首版 collector SHA `19874b54a27a34ba8a762ce9f4e9519cce84f0af3704025fefca7aabf421f8b1`、tests SHA `0022d4c90dc2b07633fb0f6d35b96dff6fae61a2158e30d52723415be6cd1f47`；修正版分别为 `f8911b89d8338e2c72020730fd896848c0433f7ba9b7c31a82716449fdeff7a3` 和 `b55253d9c008dadb1c11c697a1d485ea2878aa0fc78fe6ba8a75baf8f4257518`。真实采源无其他失败或输入冲突；未触及 A/B 停止枝。没有运行目标代码、helper、构建、客户端、Git 或新采样。

私有审计 `audit-v2.private.json` SHA `37d37fb4d0269ebdf28e8a24ba5289729caede5b359d5c2f16d3b6e9ad02d8e1`；`independent-v2-artifact-audit.private.json` SHA `fce0521d1e2a8f0fefb19be3297523c953311dd19059ac1c01fa3164c9f9682b`。共享索引、第三次独立 intake 和 Git 由主线负责。
