# CLEAR48 TRACE WAIT_EXIT 的 LOAD_DLL：parent 源码交付

任务 `F-03d3f4d2/CLEAR48-TRACE-WAIT-EXIT-LOAD-PARENT`，2026-11-11。此交付仅为主线已批准的小范围源码修复和静态审核，不是运行或客户端验收。

## 来源和唯一差异

新 W1 的原始记录显示三条真实退休，但完整 W1 因 WAIT_EXIT 收到同 owned PID/TID 的正常 late `LOAD_DLL_DEBUG_EVENT` 而失败。原 freeze SHA-256 为 `e869cfffe4513e785443e0a3e5829c0fc225bb86169f7dcc881a4a952de304af`。此处依计划复用既有观察，不改写原件；两次旧 W1 均保持失败，不能追认其局部证据为完整通过。

修改前 `parent.c` 为 75342 字节，SHA-256 `781e5ee0c22f2d7d1583f6b59d692db827af03a84e343081cea92a2640771c7e`；修改后为 75500 字节，SHA-256 `8b04815c1b7484d7439a7d8424d378d9168fd113124c9b5887f56fee29608586`。按 WAIT_EXIT 锚点逐字节重建可得新文件；唯一差异是在原 EXIT_PROCESS 检查之前插入与 BOOT 完全相同的处理块：

```c
if (code == LOAD_DLL_DEBUG_EVENT) {
    if (!continue_event(run, DBG_CONTINUE, 0)) break;
    continue;
}
```

在现有 `wait_event`/`log_event` 返回成功及循环顶部同 PID/TID 校验后才进入该分支。有效事件文件句柄的身份、路径、image 大小及关闭沿用原条件门；原代码对无效或空句柄的既有行为也未改。`continue_event` 的 `cleanup` 参数为 0，即记录 `cleanupOnly=false`；成功后回到循环且 phase 保持 WAIT_EXIT，失败走既有退出/清理路径。后续 EXIT_PROCESS 的处理原样保留，UNLOAD、新线程、异常及其他事件仍走 `unexpectedExitEvent`。

## 静态审核与边界

核对了指定基线、唯一锚点插入、与 BOOT 事件块逐字节一致、事件日志及同 PID/TID 门先于分支、既有 file identity/close 失败门、非 cleanup wall 检查以及原 128 事件、10000ms 总时限和 50ms 等待片常量。因为整文件仅有上述插入，BOOT、PENDING、其他状态/失败与清理逻辑、API 调用定义、上下文与内存写域、预算和状态字段均未改；新增分支本身不调用 Get/Set context、读写内存或创建事务。

首次审核脚本使用无锚点的首次匹配删除来反推原件，误删了内容完全相同的 BOOT 块，断言在源码已写入后失败；原基线已事先备份。随后以 WAIT_EXIT 精确锚点重建并通过，未为消除该断言而再改源码。基线副本、差异及静态审核记录位于 `local/migration72/d02-resume-20261110b/a-clear48-trace-wait-exit-parent/`。

本包只执行文件读取、字节修改、哈希、源码差异及有界静态流程审查；未编译，未执行 native/CPU 探针、Debug API、目标进程、网络、Git、数据库、validator 或客户端。未改 shared header、四 JSON、runner 或 validator/test；Project/Harness 和集成、提交归主线。安全/Daybreak 拒绝包保持记录并停止，不重试。无生产服务或资源改动，无部署；新 epoch 的独立审核、构建和新 W1 仍待主线进行，W2/W3 仍未验，完整 Avatar 和 D-02 不晋级。
