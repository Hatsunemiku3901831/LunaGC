# F-03d3s2 / CODEC9-INTERNAL-HEADER-READER-WINDOW

2026-11-11：按主线批准的单窗口静态读取范围完成来源采集。该结果只证明固定输入上的文件字节和静态指令边，不证明算法、内部流类型、自然加载器、Scene 或 D-02 已就绪。无目标代码执行、输入流读取或转换、数据表读取、新 helper 读取、客户端操作及部署。

## 固定来源与前置

请求文件 `local/migration72/d02-resume-20261111b/scene-header-conflict/codec9-header-reader-source-request.private.json` 的实际 SHA-256 为 `df478a7797a0632e10ebecdd1a9cf88286a5249e0eb93db7396c041afe08b2fc`。主线独立复核后确认初始指派中的 `...ebeccd...` 是转录错误；第一次 `fresh1` 按原指派 pin 在 source-gate 正确拒绝，失败文件保留且 `newBodies=0`。随后仅纠正固定 pin，原授权范围和门禁未变。

两次新运行均重新核验 profile、请求中全部 sourceTools 和 frozenSource、游戏路径解析脚本及固定 Capstone 依赖，再对当前 `GAME_PATH` 的五个 stock 文件逐个完整核对大小和 SHA-256。预存 core 的指令列表在 `0x141c80707` 有唯一调用行；当前 PE 对应原 5 字节 `e8b45e0a00`，其相对位移确指 `0x141d265c0`。该绑定是普通 native C++ 直接调用，不按托管元数据、名称或类型猜测。旧 `exactPdata` 只证明 entry 成员资格，不作为旧结束位置的证据，也未重新提取旧 core 或 wrapper。

## 本次实际边界

当前 PE 异常目录 RVA `0x1a4e6a40`，长度 14,344,080 字节；扫描目录后，入口精确匹配及包含匹配各唯一且为同一记录：记录 RVA `0x1a6996b0`，原始 triple 字节 `c065d2013869d2012802bb03`，区间 `[0x141d265c0, 0x141d26938)`，888 字节，unwind RVA `0x3bb0228`。区间处于实际可执行文件节 `[0x140001000, 0x1421c2200)`。按更窄授权只保留 unwind RVA，未读取 xdata，故未验证 unwind 内容或声明其格式有效。

唯一 extent、section 和 4096 字节 / 2048 指令预算在读取 body 前保存到 `pre-body.private.json`。完整窗口 SHA-256 `718bf9317f95ea38c022c4eac243f472f49ebf65987614766ebcd4bf1bf5abf4`，连续反汇编 271 条，逐条保留地址、大小、原 hex、直接调用和跳转目标及 RIP 目标。两次运行的五份产物逐字节相同。

线性窗口内有两个直接调用和一个间接调用；有一个间接跳转、两个指向窗口外的直接跳转，目标和原指令保存在私有提取记录中，均未跟随。因此来源覆盖标记为 `incomplete-external-branches`。该窗口本身不建立输入长度、字段含义、错误条件或全函数语义，相关判断须由主线的第三次核验及后续独立证据完成。

## 验证与复现

- `python -m unittest tools.migration72.test_scene_mplevel_actual_codec9_header_reader_window -v`：4 项通过，覆盖 caller 位移、异常记录缺失/重复/重叠/超预算、文件 pin 漂移、连续解码及边。
- `python tools/migration72/scene_mplevel_actual_codec9_header_reader_window.py --output fresh2` 和 `--output fresh3`：两次新读取均为 888 字节、271 条指令；五份输出逐字节相等。
- `python -m tools.migration72.test_scene_mplevel_actual_codec9_header_reader_window --audit fresh2 fresh3 audit.private.json`：独立审计程序核对来源及 stock 证明、原 pdata triple、读取前预算、body 哈希、逐条连续覆盖与边索引；审计无当前 PE 读取。指令重建使用与采集相同的固定 Capstone，因此不是第二个独立反汇编引擎。

主线已在 `main-third/` 完成第三次采集，五份原件与 fresh2 逐字节相同；4项门禁与 fresh2/main-third 独立产物审计通过。原文件与审计位于忽略目录 `local/migration72/d02-resume-20261111b/scene-codec9-header-reader-window/`；研究工具和测试位于 `tools/migration72/scene_mplevel_actual_codec9_header_reader_window.py` 与对应 `test_` 文件。尚无客户端验收；此离线工具及文档不触发运行构建或部署。仅限现有证据的语义继续交 Sol max，不能将本项局部来源验收提升为 Scene 或 D-02 ready。
