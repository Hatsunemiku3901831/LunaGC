# F-03d3f4d2 / CLEAR48-TRACE-EXIT-LIFECYCLE-V2-PARENT

2026-11-11。此包为 parent 源级实现和纯文件审查，尚未编译或执行；不代表 W1/W2/W3、客户端或 D-02 验收。最终合同及机器规格分别固定 `cbeaa19898ee74e7f5f1d71db5d1844f459aff3e15da26f7dabc11d7366fb83b`、`cf52cfafa00eab47316a756a5e04a1edc59ca2b5606d4fa89d6cc2cb552105d8`；共享接口以主线冻结计划为准。原 parent 与冻结副本 SHA-256 均为 `6e0e7d331184dfdc039f081d017419222fa3e312511fcc700ff1d8bb7f148d73`。

## 实现和记录映射

- 从成功 Wait 的实际 owned CREATE_PROCESS / CREATE_THREAD 在 128 项数组中登记一次身份，线程号按值查找；NULL type2 handle 保留实际原值，拒绝 INVALID_HANDLE_VALUE、重复及墓碑 TID。事件因 TRACE 阶段拒绝仍保留已观察来源供失败清理。只有实际 Continue TRUE 使创建变 live 或线程退出变墓碑；日志失败后真实状态仍推进，不重放该 Continue。
- 原 TRACE 仍仅 primary；最后退休、原 TF 恢复及同一 stop 的实际成功 Continue 后才到 WAIT_EXIT。仅允许有来源的 2/4/5/6/7/8，线程先后退出由实际 event TID 决定，最后 process EXIT 无须 primary。EXIT 观察序号、TID 与 DWORD 退出码在事件时记录，成功 Continue 才标已收口。
- 失败后只对 PI 进程句柄请求一次 Terminate 90；TRUE 才按实际 owned 事件做 cleanup-only Continue，foreign PID 不继续。已登记线程来源沿同一逻辑推进；未知线程、额外异常/RIP/未知通知使生命周期不一致，原 W3 outstanding UD2 是唯一例外。清理使用原总事件数、原 deadline 和 wait slice；无第二轮计时。原首失败与 pending 状态保留。
- event 新记 CREATE_PROCESS/CREATE_THREAD 的真实 borrowed raw、valid、origin、start/TLS，CREATE_PROCESS/LOAD 的 file raw/origin，EXIT_THREAD/EXIT_PROCESS 的实际 DWORD，UNLOAD base、debug-string pointer/WORD 元数据、RIP DWORD。没有读取 debug string、增设线程 API、手动关闭 borrowed 句柄或伪造自动关闭 BOOL；有效 file 仍按原身份/path/size/close 门处理。foreign PID 不对其事件文件句柄做 metadata 或 close 操作。
- epoch/summary 顶层直接绑定 `c48-owned-exit-lifecycle-v2` 和机器规格 SHA，summary schema 为 `c48-parent-result-v2`，`sourceProof` 保持原 bootstrap 字典。ownedChild 记 PI 原 handles；清理 PI APIs 记原 handle，Terminate 记请求退出码，GetExit 仅成功时记实际退出码。summary 保留 primary TID 并另记实际 EXIT event 序号/TID及生命周期一致性。

## 纯文件核验和限制

逐段审阅冻结原件和 source diff；用文件文本对比确认原 Get/Set/读/退休函数和 sourceProof 块逐字不变、没有新增线程查询/创建/挂起 API、原事件及 wall 限仍在、Continue 原始 BOOL 的状态更新位于日志完成前；并检查全部新字段与顶层政策出现位置。该核验只证明文本属性，不证明编译、API 语义或任何合成/真实运行行为。一次编辑脚本因 Python 字符串语法错误在执行前失败，未写文件；拆分后重做并检查源差异。未发生安全/Daybreak 拒绝。

修改后 parent SHA-256：`52aea6acc51694932c7ee41c168638cddf137a0d0815eade938b7b83becb8404`。私有源码差异 `a-clear48-trace-exit-parent-v2/parent-source.patch` SHA-256：`6b440d5953b9ed28f0af2b8e6dd596eadb505c9c4f83bef22e9da23ba82c8167`。未更改 shared.h、child、ASM、build、四旧 JSON、validator、runner 或原始证据；未执行 Git、编译、native/Debug API、CPU、进程、网络、DB或客户端操作，也未部署。

下一步主线对 parent 和独立 validator 对照合同审阅并执行人工正负例、runner、build/PE/依赖门；满足前置后再按主线独立决定新 W1。尤其需审查日志失败后的实际 BOOL 账、初始失败事件及追加 drain、foreign/未知 TID、所有事件身份与 raw 字段、有效文件门、primary 先退出、W3 多 drain 与 7/8 人工边界。既有三次 W1 仍失败；7/8 未实际观察，任何 native 行为尚未由本实现验证。


## 主线集成更正

子代理末审发现：W3首失败已为expectedUD2时，drain的wait/log_event元数据失败若仍有outstanding，原实现会继续清理但未必保留cleanupInconsistent。主线已在next==0时置该位，无outstanding则停止，有outstanding仍只作原授权cleanup-only收口；不能因首失败sticky遮蔽后续元数据失败。修前52aea6源码/patch保留，最终源码SHA `53a0a42cc8079677db3657ab81f9d06c9918fe243a2ac32d50110d4c9882db13`。

子代理一次Path.write_text返回errno22，原因未知；原traceback及前后相同长度/SHA已私有留档，没有平台security/approval/Daybreak标记，不能冒记安全拒绝。主线先核原件未变，再用apply_patch完成具体已授权代码更正，没有重做I/O探针、改变权限或运行CPU。新的纯回归、跨emitter审核及build/owned实验仍另验。
