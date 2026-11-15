# 保存引用的固定候选来源覆盖

`R3-SAVED-REF-CANDIDATE-BLOB-PLAN`、`R3-SAVED-REF-CANDIDATE-BLOB-FETCH-COVERAGE` 和主线 `R3-SAVED-REF-CANDIDATE-COVERAGE-REVIEW` 已完成限定验收：对三个固定公开候选文件，仅按 ID 核查旧号保存引用的候选存在性。62 个分表不同引用均各有一行；这不证明目标族、实际 row、属性值、alias、Java 或 ready，也未穷尽自动材料授予来源。

固定来源为 `MeChen618/AstaPS-Resource` commit `6cfecfb55f889f8e3b7156adb12809608db4f74b`；九项固定输入及来源 branch、README、root/Excel 树的三条 path、type、blob 和 size 均已核。六个精确 local reuse path 不存在，实际对固定 API 匿名 GET 三次，未重试、重定向或获取新 metadata。

| 候选文件 | decoded 字节 | 总行数 | 保存引用唯一命中 | SHA-256 |
| --- | ---: | ---: | ---: | --- |
| `ReliquaryMainPropExcelConfigData.json` | 10646 | 66 | 28 / 28 | `692c4423e4b716356831d141bc19bd9f8abab06fc6bb18a38e1e2596763e7a2f` |
| `ReliquaryAffixExcelConfigData.json` | 66748 | 350 | 22 / 22 | `a841807665f4f55ec6a711ac51ece684616d387b9b0e59c4cfdfa190d1d8b64d` |
| `TpsWeaponAccessoryExcelConfigData.json` | 10383 | 12 | 12 / 12 | `df6840076cd9de5da7f5ef3774fe7404fc272b110f194a6759e358625ccb8cfe` |

三表 ID 均唯一，引用缺失及歧义均为 0；只核 ID，不解释其他属性。decoded 合计 87777B。实际 encoded 响应正文分别为 14962 / 92260 / 14598B，共 121820B；可见请求行/头 864B、重建响应状态/头 3948B，应用层可观察合计 126632B。decoded 不重复计入网络；raw framing 与 TLS 字节未知。

**HTTP 状态记录限制：**已执行脚本先写 `response.status` 到 ledger 的 `status`，候选检查完成后被 `checked` 覆盖。三次 HTTP 200 是由每次到达 `checked` 前的实际成功门禁路径推得；保存的 HTTP 原件只有响应正文，未保留原始状态行或数字状态字段。原 ledger、脚本和响应未改，未重发请求。

固定计划 `local/migration72/d02-resume-20261116/fixed-saved-ref-candidate-blob-plan/spec.private.json` SHA-256 `a9976891ef47f6d2fcc0378b64ef7dbc542ccdba1b4e698349b157cc88089e9e`，同目录 `计划.zh.md` 保留八字段规格；结果位于 `local/migration72/d02-resume-20261116/fixed-saved-ref-candidate-blobs/`：`request.private.json` SHA-256 `18e7aa6b91bbf3afde0eb327cb36f2c96923f128cb61624c846ed0e7741f3a78`、`download-ledger.private.json` SHA-256 `dea6413ec651c2f197d852d86c16fbfa1bd9f78c8403475c25051b2a2cbd2293`、`coverage.private.json` SHA-256 `c08db24a4062692a388eff5d57c12bc7bc03c7d8893e8321e46406650e1e3823`。request 中历史 queue pin `8db6…` 与当前已更正队列分列，不是输入数据漂移。

本包仅产生私有候选与证据，不修改 tracked 代码或生产资源，无目标执行、native、JVM、DB、客户端或新测试。A 请求明文误验已撤回，A/B、preview 和准确依赖冻结保持；D-02 / F-05 / D02-R6 仍为 0/6。
