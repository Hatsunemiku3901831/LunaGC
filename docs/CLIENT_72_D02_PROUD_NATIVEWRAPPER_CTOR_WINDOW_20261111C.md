# R3-PROUD-EXISTING-HANDLE-NATIVEWRAPPER-CTOR-SOURCE

2026-11-11C，父 D02-R3。已取得唯一指定的 `NativeWrapper` 构造器完整原窗；current 唯一 exact/containing/overlapping pdata 为 `[0x146e03c70,0x146e03d92)`，290B、78 条连续指令，body SHA-256 `b047833a3ea77b249e05e8b6c9b1b2e1fad7463a0696490101c30b25f27fd3a0`。该 source 供后续分析 B/O 与保活条件，不自行断言其语义或实际 lease、row、当前采用。

固定 metadata/signature 将入口唯一对应 owner11712 `MoleMole.NativeConfig.NativeWrapper`、ordinary 83916 `.ctor(INativePage,int32,int32)`，返回类型未恢复。复用此前 544B helper 完整原件和 `[0x146e032c1,0x146e032de)` 的 29B，独立核 `0x146e032d9` E8 目标，以及此前 page 重载：物理调用为 RCX 指向 local32B、RDX 为重载 page、R8D 为 index、R9D 为 GetSize 返回的 EAX 位样；不假设 size 为正。主线已报告该 544B 前置第三方验收通过；本包没有新读取 caller 或执行 helper。

## 实际来源与读账

[采集器](../tools/migration72/proud_nativewrapper_ctor_window.py) 核冻结 request、26 项固定来源及额外复用工具 pin，先验证旧工具 SHA 再导入其已验 `FilePE.at`，独立测试核其 `(bytes, offset)` 返回形状。每次完整 stock hash 输入 550661784B；PE header/section 两读 4616B、去重 4096B；exception 目录一次读取 14344080B，均与 bounded evidence 分列。current ordinary Pcell 与 `P+83916*8` 共 16B，选中 pdata 12B 从已读目录复制；pre-body 保存 `bodyRead=0`，随后 body 290B，bounded evidence 318B，低于 540B。pdata 原记录 RVA `0x1a6ce8d0`，SHA `d926f12dcdecebb562054cd17e2be5074c9c82d8ecd8b95e4614a605d6ae05d4`。

两 fresh 八件原件逐 byte 相同，私有根 `local/migration72/d02-resume-20261111c/proud-querywrapper-borrow-semantics/nativewrapper-ctor-window/{fresh1,fresh2}/`：

| 原件 | 字节 | SHA-256 |
| --- | ---: | --- |
| boundary.private.json | 8901 | `8db9a2b493915893b87169f383dc033e598ab49ce31a7b9eac95bf4149a0bed0` |
| pre-body.private.json | 9296 | `3fef136b01240bdc1a2670baa59927e666fe557a407c04e2bc0569f8679f4426` |
| code.bin | 290 | `b047833a3ea77b249e05e8b6c9b1b2e1fad7463a0696490101c30b25f27fd3a0` |
| code.asm.private.json | 19678 | `92b6c695eec71c4a54be19393feeb09f9a46e2135b18f13adafe74a48506bfdc` |
| result.private.json | 13385 | `52373070e7199c9d58da488e4b967020d54aabd463019b5b3c4eb18ebc1decd4` |

[独立测试](../tools/migration72/test_proud_nativewrapper_ctor_window.py) 的 6 项通过：saved caller/E8 与逻辑和物理参数、已固定旧工具 API、source/stock/AOT/pdata/文件边界/字节预算的 body 前停止、短读及指令预算的实际读取计数、已知拒绝边只索引。纯 artifact 审计通过；另只读保存原件，独立核 saved caller 29B/E8/page 重载、AOT、pdata、连续原 bytes、23 处直接 CALL/branch 和 4 处 RIP 位移，不调用 decoder 或读取 PE；`audit-independent.private.json` 1426B，SHA `75e867bbf06cb1ca8596fd4ff36a75fa36f87e08ba0f60f6cc3d6d3bb06d27d7`。

已知 `0x149a75140` / `0x1405336c0` 直接地址未在索引出现；未知间接依赖不推断。没有读取 TryGetPage/GetSize、generic/manager/refcount/cctor、global/callback、page/B/O 或任何 callee 内容，也未执行原 helper、target CPU/live、runtime 模型、客户端、构建、JVM、DB 或部署。actual B/O/lease/encoded/getter/row/pair hit/current 均为 null，resourceReady、D02-R3 和 D-02 未晋级；A/B 已拒绝依赖保持冻结。

## 集成第三次核对

集成执行者确认 `main-third/` 和目标审计文件不存在后，在 checkout 根执行：

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
python tools/migration72/proud_nativewrapper_ctor_window.py --output main-third
python tools/migration72/proud_nativewrapper_ctor_window.py --audit fresh1 main-third --output audit-main-third.private.json
```

第一条退出 0 后执行第二条；独立核固定 caller/E8、声明、当前 AOT 和 pdata、完整原 bytes 连续重组、读取账与未知标记。任一来源或范围漂移保留诊断并停止，不扩大范围。第三次未由本执行者运行；未操作 Git index 或提交，共享索引由集成者维护。
