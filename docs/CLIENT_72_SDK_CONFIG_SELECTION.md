# R2-ROUTE-CONFIG-SELECT：托管配置选择与原生同步

## 实施前边界

复用已验 SDKDelegate.LoadFile、实际 server_pc 包、map/app_key writer、签名与真实 HTTP 往返，不重开普通文件搜索或完整解包。当前缺口是 ConfigManager 的 env/cp_app_id 选择、返回模型与 managed→native 同步的准确调用关系，以及自然执行时选中的值；本卡不以 UI 稳定或全局非空代替自然输入。

固定五 stock 输入与现有结构提取；先只读取 LoadLocalConfig、SetEnv/GetModulesConfig、AccountPlatNative.ConfigSyncWithNative/HoYoSDK_ConfigSync 的直接 PE 窗口和必要元数据，最多12个直接函数。原始反汇编、类型投影、失败与 hash 留 `local/migration72/d02-resume-20261109/r2-config-select/`。优先确定完整必要 payload 与可复现原始消费者片段；不模型替代 JSON 选择成功、不调用真实客户端或网络、不采凭据。

允许新增本卡专题、离线工具及对应定向测试；不修改旧冻结工具或运行材料。产物须区分静态读取、离线原始执行、模型与自然未知，明确与 R2-ROUTE/KEY/SESSION 汇合的准确事件及输入。输入漂移、无源虚调用、首次真实缺内存或预算耗尽即保留停止点；不补空对象/默认env/locale成功桩。后继实机需单独准备准确观察合同并等新用户确认。

## 已确定的后继入口

主线直接核固定 PE、方法元数据与全局 literal usage，找到 ComboManager.LocalConfigSync / method40117 / `159806d70`。它把 ConfigModel.env、local.app_key 等字段放进 JSON，调用 `LoadLocalConfig(env,true)` 取得的 URL 模型再转为字符串，最终以准确 literal7643 **local_config** 交插件。正常调用边在 `159807966` 装 key 后通过 `159807989` tail-jump 分发；附近 `1598079a0 call` 属不同控制流，不能混作同一调用。

`AccountPlatformPluginImpl.ConfigSyncWithNative` / `146e363b0` 在普通非IFix分支保留两参数，tail-jump 到 `AccountPlatNative.ConfigSyncWithNative` / `146d80910`；后者 Marshal 两个字符串、同步调用解析得到的 native export，返回后释放两个临时转换缓冲。该参数生命周期提供了比异步读取可释放 URL-map 更具体的观察边界；自然到达尚未验。

固定 SDK 的 `HoYoSDK_ConfigSync` 导出 RVA `13d5b0` 把 name/payload 转给 engine `VT694980+70 → 1343b0`，再经 Config `VT6c6cd8+3a0 → 36fba0` 查配置 handler map。原始指令、各窗口 SHA 及方法/literal投影在本卡忽略目录；新 REA 独立 import 证据保存于 `local/migration72/d02-resume-20261109/sdk-imports-rea.private.json`，只作为当前 DLL 静态身份/导入边界，不能宣称自然 transport。

后继收敛为 [R2-CONFIGSYNC-EVENT](CLIENT_72_SDK_CONFIGSYNC_EVENT.md)：核 `local_config` 注册、原生 dispatcher 与 `378850` writer 的准确关系，并用有界原始执行验证正反分发。此前完整 `378850` 的 CRT locale 首缺不改成成功桩；本卡也不以 ConfigManager.configLoaded 的单个静态写入当作 native Init 完成。当前未实现 hook、未读取自然 payload/env/key/route，完整托管选择与原生自然完成仍未验。
