# Material 与特殊能量枚举的固定 literal

`R3-MATERIAL-SPECIAL-ENUM-CONSTANTS-STATIC-20261116`已完成纯静态限定验收：复用`extract_client72_enums.recover(indices=(34165,61184))`，仅保存MaterialType的94个literal及特殊能量类别的3个literal。97项均为明确presence/int32，95项非零；实际`MATERIAL_NONE=0`，`SPECIAL_ENERGY_NONE=0`、`SPECIAL_ENERGY_MAVUIKA=1`、`SPECIAL_ENERGY_SKIRK=2`。名称、self-valuetype声明、underlying类型、default-row及dataIndex均固定，不按顺序赋值。

RUN2对固定PE和global-metadata各完整读/hash一次，读取915840B默认表结构和388B不同literal数值；PE结构/类型引用语义读取保守上界892B。startup metadata未读；既有74813控制只读复用。原recover附带的四个旧wire fieldBindings未保存为本轮成果。输出49382B，原件`debug/d02-skill-energy-search-20261116/material-special-enum-constants/run2/`：result SHA-256 `206351858a5ae43c5c222762f0f1a721c01fb8990999d131a85c8a3fe7b7da29`，manifest SHA-256 `00a47d09dd707601ac38736faee6bed534a784ddc5c3829372480fd19f69c9c5`。

规格`debug/d02-skill-energy-search-20261116/post-depot-gap/enum-constants-scope.private.json`10048B，SHA-256 `fe0925f0a32da834eabb2c54d31db16006fde7554a1138dd6a03eaf879a9e54c`。入口[material_special_enum_constants.py](../tools/migration72/material_special_enum_constants.py)，冻结工具SHA-256 `63a1e2fb592403a9ced56e73a2fd5ef5c15a5b59da867883c689351e8d73397c`；命令`python -X utf8 -B tools/migration72/material_special_enum_constants.py`，已有输出目录不可覆盖。

首次resolver退出1，尚未打开PE/global或产值；原stderr未保留，先前推断“缺Astrolabe”已撤回。相同环境只读重现的stderr为PowerShell执行策略禁止脚本；沿现有便携入口补`-ExecutionPolicy Bypass`成功，目录及Astrolabe存在。主线审定后RUN2仅改该argv、输出目录和selected初始化。原failure SHA-256 `d946607bce45bd183645977ba7fda74b58b0612eec1893f62b2f8a3d652ecaf3`、重现原stdout/stderr摘要 SHA-256 `6d74c957d78213b72c8ac96b2093fdb40d987b1368d0cea37f96b17309320b99`保留，不能把重现stderr称为首次原件；此普通包装错误不是安全拒绝或外部路径缺失。

本项只闭合两个选定enum的literal数值，不提供Material实际row/分类值、Skill分类token值、Java枚举投影、资源采用或ready。实际Material源输入及同row/key/span/cursor、Relic rank/set职责仍另项；无native/getter/allocator/GC/TLS/JVM/DB/客户端或部署。
