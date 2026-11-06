# F-03d3e5：中层 list 与 int64

限定 87867、57431、29317、45040、63939 五类，24 个存储槽经 640 次 tag1..32/wire0/1/2/5 原生扫描确认 24 wire。复用 [e3 primitive 叶子](CLIENT_72_AVATAR_PRIMITIVE_LEAVES.md)和[e4 signed 子树](CLIENT_72_AVATAR_SIGNED_TREES.md)，不重建下层模型；51050 非空仍拒绝。完整复杂图/Avatar、业务语义与实机仍未验，`targetProtocolReady=false`。

每次全新进程重新核三输入 `extract_client72.HASHES`，与[核心](CLIENT_72_AVATAR_CORE.md)相同，重新提取 metadata、全部有界 usage cells、五 reader/cctor 类型引用和选定 AOT。实例字段与原生引用共同确认外部消息依赖恰为 12876/19829/21103/65248/66229/89623；五类不含 Object。固定库存摘要 hash 检查沿用原合同。

10 条直接解码的 AOT 是五个 message-list 的 ctor/reader，实例 17213→21103、6111→89623、14050→19829、34712→66229、6010→12876；具体 reader 均 `0x158dc41f0`，runtime 按实际 codec class 检查 child，不按共享地址猜类型。45040、63939、87867 三个消息 cctor 实际执行到返回。所选 ctor 记录不代表全部 constructor 已运行。

## 精确新增 wire

message/list 为 wire2，所有标量 wire0；87867 u32-list 接受 wire0/2。字段、偏移和执行写点完整保存在[脱敏摘要](../protocol/cn-7.2/avatar-middle-trees-observations.json)。

| 类型 | tag 与字段 | raw |
| --- | --- | --- |
| 29317 | 1 OCICCDJIIEO/+18 | →66229 |
| 45040 | 2 GPDEGHEDMHM/+18 | list→66229 |
| 57431 | 1 DHLPKMNMONN/+18；2 HAAGBOCHHBH/+20 | →29317；→45040 |
| 63939 | 1 ANKEOLKBECN/+20；2 OCJGKKLACBO/+18 | int64；list→12876 |
| 87867 | 1 HOPABANFAJI/+78；2 NIHNNHMGENL/+48；3 NKBMECCPKPJ/+20；4 BGKOAMHLPPK/+30；5 ICMFFOLOBDL/+40；6 KPDMPKFJHED/+38；7 IBEBCEIBAEC/+50；8 PLPNCLOFNIC/+18；9 ALKNGMBFIKO/+54；10 HPHMLMEMKLB/+28；11 JMKGJDGGBLM/+74；12 KFKNPMLLODH/+58；13 PBHALLIPDIP/+70；14 IKGNHBFNDDK/+60；15 EBEOEICOFOK/+68；16 JMICLCAEOPO/+6c；17 PEDCHFIHFML/+64；18 GFIJIOEBBAG/+5c | 1/2 u64；3 list→89623；4 list→19829；5 bool；6 u32 list；8 list→21103；10→65248；7/9/11/12/18 int32；13..17 u32 |

## 非空组合与范围

21 个独立正向图覆盖五类全部字段非零。87867 三种 message-list 各放两份不同非空对象，第一份分别复用 e4 完整字段向量，第二份给不同值；两个 u64 同时覆盖 2^53+1 与 max，再交换到 max/2^63，不能把高位丢失当通过。所有 signed 标量给不同负值，u32-list 同时验 packed/unpacked/0/上界，标量重复清零与 bool 规范化已验。

63939 int64 min/max/-1/2^53+1 均按实际八字节写入完整回读，没有转 double；其 list→12876 同时包含两份不同非空 u32/float 列表。负零原始位仍为 `0x80000000`，5 次实际 float Add 写点 `0x1571dc0c9` 与 87867 内 6 次 signed Add 写点 `0x1568b8bc4` 留在摘要中，未用预设结果模拟容器。

57431 两个子树都非空，分别含独立 66229 字段和列表。重复 singular→29317 合并到既有 66229 并追加 u32 列表；另一侧 list→66229 追加一个独立空对象，不串值。所有 empty、显式空 singular、错误 int64 fixed64 wire 跳过已验。

8 个拒绝分为：1 个 51050 研究范围拒绝，3 个输入模型拒绝（非法 tag、int64 overflow、深层 signed packed 截断），4 个原生 ReadMessage 错误入口（负长度、截断、两层不同递归限额）。错误入口到达即停，未执行完整异常派发。managed metadata/allocation、boxing、primitive input/codecs/delegates、固定 64 元素数组继续为明确模型；具体 reader、ReadMessage/limits 与 list append 执行原生代码，没有 live ABI/IFix/业务 Handler 或部署通过声明。

8 项新增、130 项关联研究回归实际全过；两次正式全新进程各重核三 hash、提取及完整样本，完整轨迹和摘要逐字节一致。证据 `local/migration72/evidence/avatar-middle-trees-{native,native2,summary,summary2,determinism}.json`，日志 `local/migration72/logs/avatar-middle-trees-{run1,run2,tests}.log`。无生产/Proto/配置/资源改动，不需重启；共享台账与提交由主线审核维护。

```powershell
python tools/trace_client72_avatar_middle_trees.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-middle-trees-native.json --observations local/migration72/evidence/avatar-middle-trees-summary.json
python -m unittest tools.test_client72_avatar_middle_trees -v
```

下一 e6 两个大节点及 e7 多份 message-list；e8 双 Object/完整图继续依赖这些合同。已验 raw 不批准未知业务名。
