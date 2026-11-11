# E-01d1 / R3-PROUD-ROW-BRIDGE / NATIVE-SCALAR-SETTER-SOURCE

2026-11-11：取得普通注册方法 `58209.m483726` 的唯一限定静态 source 窗口，供主线对照已验 getter；本项没有执行目标 CPU 或客户端，也没有读取任何被调用函数体、xdata 或 RIP 指向的全局内容。此窗口不证明实际 Proud 行构造、编码值、getter 结果或自然加载。

冻结请求 SHA-256 `611633a8a950d29bda6954b20a6aec0ffc2aaac027231da3092087439e83bc72`。入口重新核验请求列出的本地来源、profile 和当前 `GAME_PATH` 五个完整 stock 文件；严格只读数据的 metadata unpickler 验出唯一 owner 58209 / method 483726 声明，签名来源给出单个 `uint32` 参数。已存 caller 源与连续指令边界上的 27 字节 setup 一致：`[0x149f0d147,0x149f0d162)` SHA-256 `40086706406424fd8dbf205025a9c86c1670dc7658234e073df7153e380f1db5`；当前 PE 的 5 字节直接调用位于 `0x149f0d15d`，实际目标为 `0x14aefa090`。

普通 AOT 表从当前 pointer cell 读取 QWORD，再按 `483726*8` 取当前 slot，slot 值为同一入口；不以历史指针提示代替实际读取，也不使用泛型猜测。当前 PE 异常目录的 exact 与 containing 各唯一且为同一条记录，声明范围 `[0x14aefa090,0x14aefa12d)`、157 字节；记录 RVA `0x1a8dc704`，unwind RVA `0x4174d68`。范围在可执行文件节内，完整原记录与选择诊断在读取 body 前保存；只保留 unwind RVA，未读取或验证 xdata。

实际 body SHA-256 `5c7bd52bc54dd9ebfa01137bd2b56c741eb5f9bb2ad05331158a9c85aa20d3dc`，45 条连续指令，逐条原 bytes 重组完整等于 body，低于 256 字节 / 128 指令预算。报告保留 5 个分支、2 个调用及 2 个 RIP 相对地址；调用目标只记录地址，未跟随或读取。原 getter 的既有来源作为只读上下文，本项只交付 setter 原件，正常存储/逆关系及错误条件由主线静态复核，不能据局部 preclear 声称实际 encoded DWORD 或 getter 值。

## 验证与范围

主线已实际执行第三命令及纯审计，十份对应原件与正式双fresh逐字节相同，`audit-third.private.json` 1509 B，SHA-256 `51eb10d63e4ac831aee610b0dedc5ff2a5fcdc6a4e85b169ad263cea18b3bf6a`；主线重跑下列5项测试全过。本单窗限定出口已验收；用户要求收尾暂停，setter/getter正常存储关系的后继语义未启动。

- `fresh2`、`fresh3` 的十份对应原件逐字节一致；纯 `audit.private.json` 重核源 pin、caller 5/27 字节、AOT 算术、pdata 原 triple、body/汇编覆盖及 call/branch/RIP 索引，当前 PE 读取与目标 native 调用均为 0。审计重用同一固定 Capstone，不是第二个独立反汇编引擎。
- `python -m unittest tools.migration72.test_proud_gate_comparison_scalar_setter_window -v`：5 项通过，覆盖 caller 位移与漂移、pdata 缺失/重复/重叠/边界、普通方法唯一绑定与参数、连续解码/指令预算、来源大小或哈希漂移。
- `fresh1` 是增加最终完整状态字段及测试前的开发记录；正式双 fresh 为 `fresh2`/`fresh3`。主线第三入口：`python tools/migration72/proud_gate_comparison_scalar_setter_window.py --output main-third`；再用 `--audit fresh2 main-third --output audit-third.private.json`。本机原件位于忽略目录 `local/migration72/d02-resume-20261111b/proud-gate/scalar-setter-window/`。

此项是独立 source collector，不依赖或实现 A 第二返回、B 组合 loader 被拒项；无目标执行、构建或部署。`actualEncodedValueObserved=false`、`actualProudRowConstructed=false`、`nativeParserExecuted=false`、`actualQueryWrapperBaseObserved=false`、`naturalBackendProven=false`、`resourceReady=false`、`d02Complete=false`；没有新增客户端验收。
