# E-01d1 旧号 Proud 组、等级和第一个 tuple

固定原 3179 B producer、Avatar 消费和全 121 个保存 Depot 的 typed 字段。Proud 管理分支从同一 wrapper 的 `41105+0x90` / `+0x84` 解组与基础等级，分别写 pair 的低、高 U32。Avatar 用同一组作键读取基础等级和附加值，再以加和查询。目标 pair map 先返回 U32 行 ID，随后按 ID 查询原行。Java 入场现行 `group*100+1` 的对应关系需逐个核原 map 命中。

旧号实际当前 Depot 中，81127 的 `+0x14` 值 221 唯一命中一项，同行 `+0x10` 为 1，presence 仍未知。它经目标原调用链形成首个待验样本 `lookup(221,1,false)`。固定文件 key 22101 的条件 setter 入参在 nativeData `+0x30/+0x38` 为 221/1。由真实 group 值和独立的文件证据选中这个样本。

新增 `57708.m479099` 原 435 B getter，以两处原 E8、typed 和普通 AOT/唯一 pdata 绑定。对非零非负 handle，原 getter 与已存 parser 以同一去 tag handle 调 `QueryWrapperInternal`，从第一个 out QWORD 取基址，再按带符号扩展的 offset 返回 DWORD。于是已有 parser 写偏移和正 handle getter 读偏移相接。负 handle 的独立分支和自然对象的实际 handle、codec、DWORD、map 命中均保留待验。门槛还需同一 typed 81127 的 `+0x10` 对实际 Avatar 突破值作比较并决定采用或跳过。

主线 21 来源、67 指令锚点和 605 Depot 槽位的第三次只读审核与两次原报告一致，SHA-256 `dd9bc467b25c8edb36319136972cfb43eedd8bdf1b4e0dacdd1c71fb6c197228`。原 getter 两 fresh 各 87 条，body SHA-256 `7f9905cee0b667965999c65de05ab2f7f0b7b104ffdd2c097aafcc2e13553442`。复现见私有 `c-proud/audit.private.py` 与 `c-proud-getter-window/collect.private.py`。
