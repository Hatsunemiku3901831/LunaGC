# R3-NEXT-LAUNCH-WINDOWED-2560X1440：下次启动离线准备

状态：**已完成（仅离线准备与合成验证）**。根据用户提出的下次启动窗口化要求，新独立包使用 2560×1440 窗口化参数；本批未再启动客户端，实际窗口、尺寸和位置尚未验证。未来现场必须重新完成 fresh 前检并取得当次实时 ready；旧现场 ready 不复用。

新包保存在忽略目录 `local/migration72/d02-resume-20261115/launch-timeout/launch-windowed-v1/`，协调入口为该目录的 `coordinate.private.ps1`；适配器、来源 seal 与操作计划 SHA-256 分别为 `9474149d8dcd5109c01a0bde6cc502d27bc3ffe57c3d835da44d10c4d00c4cfd`、`d6a55e810de78f12e5cda0f816507cf747b023d44ed3f8493d0d43f21fd24e1e`、`83225165fbb9aa52f754e5c86b48e10bf83f09e476bc96c00bcca29e83226214`。参数沿用 `tools/local_windows.ps1` 的窗口化约定：`-screen-width 2560 -screen-height 1440 -screen-fullscreen 0`。六个参数、创建 flags 0、原有句柄次序、原90秒协调期限与迟到许可门均经独立核对。

新 seal 的 28 项和旧 seal 的 27 项通过核对；3 个 PowerShell 文件与原件逐字节相同，旧包及旧 seal 保持原状。作者 4 项与独立 4 项合成测试通过。作者首次测试因测试代码的 `sys.argv` 使用错误而失败，修正测试后通过；原始失败流未保留，不能冒充完整原文。独立回执 `local/migration72/d02-resume-20261115/launch-timeout/windowed-integration1/completion.private.json` SHA-256 `29448e6ccda5a8bf0944de43dd05ba913b6005853bbc096a5bb2063f94d345fa`。

离线审核没有目标启动、目标读取、采样、真实 RunAs、进程或端口探测，也没有配置或注册表写入。窗口化准备不提供自然非空 cache 或 key，不证明实际 M、getter、row、current、同 epoch 或 resource ready；`R3-PROUD-EXISTING-CACHE-CAPTURE` 仍阻塞，D-02 六项客户端验收仍为 0/6。原 A/B 内容安全拒绝及其依赖继续冻结。
