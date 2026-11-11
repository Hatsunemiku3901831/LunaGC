# E-01d1 / R3-PROUD-INITIAL-QUARTER-CONSTANT-DATA

2026-11-11C，父 D02-R3。固定文件映像中，按已验 717B 条件 source 的 16 组 load/store 顺序拼成的初始 256 字节 **是 0..255 的排列**；本次还观察到它恰是按 `00` 至 `ff` 顺序排列的 identity。`sorted(C)==bytes(range(256))` 独立成立，直方图每个字节均为 1，重复与缺失均为空，没有修补数据。

这满足[已有交换／inverse 证明](CLIENT_72_D02_PROUD_TABLE_GENERATION_SEMANTICS_20261111C.md)所需的**文件映像初始排列谓词**。只有在已列正常返回、ABI、工作区和非干扰等条件成立时，才能接上条件左逆；运行时是否读取同一未改写常量、provider 是否非干扰、实际绑定及 seed/table epoch、当前 callback、actual encoded/getter 和实际 41105 row 均未知。resourceReady、D02-R3 与 D-02 未晋级。

## 实际读取与验证

[离线工具](../tools/migration72/proud_initial_quarter_constants.py) 核冻结 request、其 14 项固定来源及路径工具，再用独立字面表核已存 717B 原窗的 16 组 load/store、RIP 有符号位移、16B 宽度、寄存器配对和 store 偏移顺序；没有重读 code。随后每个 fresh 完整核五个 stock 身份（550661784B hash 输入单列），PE header/section 两读 4616B、去重 4096B，证明两个范围均有唯一可读 file-backed mapping；保存 `staticDataRead=0` 的 pre-data 后恰两次读：

| 文件映像范围 | 字节 | SHA-256 |
| --- | ---: | --- |
| `[0x1422502f0,0x142250300)` | 16 | `be45cb2605bf36bebde684841a28f0fd43c69850a3dce5fedba69928ee3a8991` |
| `[0x1439725a0,0x143972690)` | 240 | `696f3cc5a663419ba675bf849182801e5fed5c5e86d3f0d7508b1eef32b84152` |
| 按 store 顺序拼接的 C | 256 | `40aff2e9d2d8922e47afd4648e6967497158785fbd1da870e7110266bf944880` |

双 fresh 的五件原件逐 byte 相同，私有根为 `local/migration72/d02-resume-20261111c/proud-table-generation-semantics/constant-data/{fresh1,fresh2}/`。`pre-data.private.json` 为 8020B，SHA `3e55695662617a684152498d19b83539499cdc2cecd6066c7a1417fb52039ef5`；`result.private.json` 为 10833B，SHA `ec609e15159e0b97f9275bd63ed152a9945334596bf8db5597155768fef42a6a`。

[独立测试](../tools/migration72/test_proud_initial_quarter_constants.py) 最终 5 项通过：16 组字面来源与顺序、引用/长度/寄存器/原字节漂移、映射歧义和不可读/文件边界、preferred base、source/stock 失败时零数据读取、两读 256B，以及排列与重复／缺失反例和实际状态分离。纯 artifact 审计通过；另只读保存原件与冻结 source，独立核字面 16 组来源、位移、原字节、计数和 sorted 谓词，`audit-independent.private.json` 为 979B、SHA `09ec24242a02d54535d6f743ba4fc48fde7349384ebb5ecc4e64544292e2d0f1`。合成测试不是实际数据证据。

本次新 code、W、seed、cookie、provider、其他 global/cell、pdata、exception、AOT、target CPU/live 读取均为 0；未运行 runtime 模型、客户端、构建、JVM、DB 或部署。A/B 已拒绝依赖保持冻结。有限两字段生成投影未在本包实施。

## 集成第三次核对

集成执行者确认 `main-third/` 和目标审计文件不存在后，在 checkout 根执行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_initial_quarter_constants.py --output main-third
python tools/migration72/proud_initial_quarter_constants.py --audit fresh1 main-third --output audit-main-third.private.json
```

第一条退出 0 后执行第二条；若固定来源、映射或谓词不符，保留原件和准确结果，不补数据或扩大范围。另独立核原 16 组来源与 RIP/store 顺序、两段原件拼接、256 项计数及文件／运行时状态分离。第三次由集成者执行，本轮未执行或声称通过。未操作 Git index 或提交，共享索引由集成者维护。
