# R2-CONFIGSYNC-OBSERVE：准确同步入口观察

## 实施前计划

本卡承接已验 `local_config → 原handler lookup → 378850入口` 与[托管生产链](CLIENT_72_SDK_CONFIG_SELECTION.md)，目标是取得自然配置同步事件的有限分类，解除 R2 自然 env/资源等值关系的具体缺口。已有零hook加载、47转发、SDK绑定/持有、ilhook wrapper 和 key核心直接复用，不重做SDK响应或安装旧整DLL。

实施仅新增独立 config proxy example/观察核心、owned fixture、必要的 example 声明及审计/观察工具；旧 clean/key 核心、loader、table、Cargo.lock与生产 ext/lib.rs 全部冻结。允许复用旧 SDK loader 的 `wait_loaded/bind_loaded`，**不调用 key activate/install，不安装 key 或任何重定向/信任 hook**。若复用旧绑定模块在产物中留下 dormant 符号，必须逐个核 EAT 并说明用途，不能放开未知导出。

唯一 hook 为固定 SDK `HoYoSDK_ConfigSync` / RVA13d5b0，必须固定原窗口和实际模块身份；原调用/返回/寄存器/flags/XMM/栈/LastError保持。入口只接受准确原 EXE `AccountPlatNative.ConfigSyncWithNative` 返回地址 `B+6d809cf`（实际代码窗亦核对）；未知caller透明通过且不读正文。准确 name 逐字节匹配 `local_config\0` 后，才在原同步调用的参数生命周期内复制有界自有bytes。一次捕获最多65536B，仅为观察上限，超限保持固定失败，不声称全部合法配置都必能通过。

自有副本仅在内存按已审字段分类：既证CN env代号、对应固定资源 app_key 相等bool、url:string中 comboLogin 与同源资源相等bool。不得记录或hash自然正文、key、URL、device ID、账号/token或它们的长度。固定引用由忽略目录的已审 class49原文生成，只在内存/忽略构建输入使用，保留来源与完整资源extent等值证明，不把敏感材料写入patch源或共享文档。

产物按阶段独立验收：源码/保密分类和正反输入 → 实际release构建/固定EAT/原生owned ABI与透明原调用 → 主线审核新准确读取与恢复合同 → 等新user-ready → 单次90秒实际加载及只读state/code身份观察 → 用户正常退出/stock恢复与保护文件核对。代理不能调用Git、Cargo/Gradle、安装、DB或客户端；主线统一调度。原始资料在 `local/migration72/d02-resume-20261109/r2-config-observer/`。

owned验收须覆盖准确caller/name、非目标不读正文、env/shape/reference失配及固定失败、正文哨兵不输出、实际ilhook原调用一次/原返回/Win64 GPR/XMM/flags/stack/LastError与drop恢复；只在自有模块/代码运行，不调用真实SDK。实际入口、材料、ABI或装载不满足即停，保留首失败；不启额外hook，不换协议/版本/校验，不无假设重试。自然事件分类成功也不等于writer返回、路由生效、认证或D-02通过；这些边界分别保留。
