# D02-R3 managed float getter 单窗来源

任务 `R3-PROUD-MANAGED-FLOAT-GETTER-ONE-SOURCE`。普通 typed 声明、当前 AOT 和当前唯一 pdata 门后，两次 fresh 取得同一完整窗口：`[14f905140,14f9051e6)`，**166B／45 条**，SHA-256 `852140f52b31030047d743fe9fda0a8d2b59a107f8ad154b6e9b6d8b5208bf64`。本包只验来源和物理指令索引；没有执行 getter、读取 codec table／seed、访问目标进程或取得实际 float 值。两个必要列的业务 alias、实际行和 resourceReady 不由此成立，语义交后续独立分析。

## 来源门与边界

原请求 `next-float-getter-source-request.private.json` 5333B SHA `9fc0e152d1bb1944e454ddafc1926c1d1d1b2c55a6fcf17e181bca97026539db` 未改；主线批准的补门固定于私有 `float-getter-window/spec-addendum.private.json` SHA `d502d718d8718772f4b15d359501860595376320afecdbb3741a930362fbc66f`。包含 profile SHA `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`、原 FindEntry 请求与 Capstone／路径工具 pins、Scene getter 请求及 FilePE 源 pin。五份 stock 完整 hash 和 profile 每次先核。

metadata 中 owner54724 的唯一普通 m454810 `GOCKGKFKOID`，flags `0x886`、显式参数 0，登记地址等于窗口起点；field271929 为 U32，boxed metadata offset 0x10，unboxed 存储 offset 0。logical return type 没有恢复。当前 AOT cell `1422f9950` 读 8B 得 `1428a54d0`，槽 `P+454810*8=142c1d9a0` 再读 8B 得入口。**没有原始 caller E8**；已有 listing 中的 caller 只是提示，不伪称其为 caller 原件。

当前 exception directory 的 exact／containing／overlap 各唯一同一记录：`[14f905140,14f9051e6)`，record RVA `1ab02fac`，原 12B `4051900fe651900f34996304`，SHA `8e70708aeeab6cc35a6627001a95dcd92c1e046d2b540aa62a8936222db87b95`。先保存 boundary、`bodyRead=0` 的 pre-body；以真实 pdata 和文件可执行区间确定 extent，再读 body。166B listing 长度只是旧提示，不作为边界；未读 xdata、相邻函数或任何下游目标。

## 机械索引和读取账

完整原窗含 7 个直接相对控制位移：`14f905156→14f9051e0`，`14f905177/18a/199/1a8→14f9051db`，`14f9051db→1404c2650`，`14f9051e0→1404c2be0`；两个 RIP 位移为 `14f905145→14586b600`、`14f90515c→145872d30`。`14f9051d1` 的原 `660f6ec6` 是物理 `movd xmm0,esi`，`14f9051da` 是 RET；这不是 metadata 逻辑返回类型恢复。上述目标仅作位移索引，没有读取其内容。

已有 listing 的 45 行落入原窗，文本比对有 2 处仅因 RIP 行尾附带注释而不同；listing 另有窗口外内容，未纳入新原窗。纯产物审计逐 byte 重组、独立按原位移重新计算 7 个相对控制和 2 个 RIP 目标；两次 fresh 的 8 个原件逐 byte 一致，审计 SHA `909f60d5ec879ff6281185249fa59845fe7830a8c7237838aedc79e64b041c02`。

每次实际文件读账：五 stock hash 550661784B；PE 头／节表两次共 4616B、去重 4096B（上限8192B）；异常目录一次 14344080B；AOT cell／slot 各8B，body 一次166B。选中 pdata 12B 从已读取目录内提取，**没有额外文件读取**。不含 stock hash 的文件请求合计 14348878B；AOT16B＋pdata12B＋body166B＝194B（上限284B）。fresh caller、其他函数体、global／table／seed、目标 CPU、native 执行和 live 均为0。哈希读取和 PE／目录读取分账，不把文件 hash 误记为有界证据字节。

## 复现、测试与独立 intake

先执行纯测试，再执行各 fresh；`main-third` 为 intake 的单独第三次，输出文件名均须不存在：

```powershell
python -X utf8 -B -m unittest tools.migration72.test_proud_managed_float_getter_window -v
python -X utf8 -B tools/migration72/proud_managed_float_getter_window.py --output fresh1
python -X utf8 -B tools/migration72/proud_managed_float_getter_window.py --output fresh2
python -X utf8 -B tools/migration72/proud_managed_float_getter_window.py --output audit-fresh.private.json --audit fresh1 fresh2
python -X utf8 -B tools/migration72/proud_managed_float_getter_window.py --output main-third
python -X utf8 -B tools/migration72/proud_managed_float_getter_window.py --output audit-third.private.json --audit fresh1 main-third
```

本包 7 项纯测试通过，覆盖当前 FilePE.at 双返回值及短读记账、typed 声明、唯一／歧义 pdata、越界、完整／不完整解码、相对位移及 listing 边界。Capstone `disasm(count=128)` 与迭代前门双重限制经 129 个单字节指令夹具验证：仅产生128条即停，不解第129条。两次 fresh 与独立纯产物审计均 exit 0；没有失败采集版本，也没有代主线执行第三次或 Git。测试、命令日志、完整报告与原窗留私有 `float-getter-window/`，共享索引由主线维护。
