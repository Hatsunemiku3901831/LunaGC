# D02-GITHUB-INCREMENT-AB：有界外部对照

日期：2026-11-10；结论 **partial / no-new-evidence**。本轮仅供 A 的清零 helper 职责对照及 B 的 managed route 检索，未发现可以直接接入本客户端的源码。外部相似源码不是客户端执行证据，也不是本机准确 helper 分支实施的门禁。

| 分类 | 结果及边界 |
| --- | --- |
| 可直接复用 | 无。本轮没有新的受证实 managed 配置选择/请求采用实现，也没有可融合的 A 实现。 |
| 仅背景 | [Microsoft/STL 固定源码](https://github.com/microsoft/STL/blob/e1ed302683e5e822f663fbb0f44cee7e9ddc6af0/stl/inc/charconv#L561) 中 `_CSTD memset(_Xval._Mydata, 0, _Unit_shift * sizeof(uint32_t));` 是调用方按长度清零数据的例子；它不是 memset 的 AMD64 实现，也没有说明目标 allocator 的标志或对象生命周期。固定 commit `e1ed302683e5e822f663fbb0f44cee7e9ddc6af0`，tree `5f71dc2bdb015fbf0c3c758fc08e0d68ddad757e`，该文件 blob `bd9dfd723a30339d316aaac146ae121249f55fcb`。此次只核 `main`，其他分支未盘点；[许可证原文](https://github.com/microsoft/STL/blob/e1ed302683e5e822f663fbb0f44cee7e9ddc6af0/LICENSE.txt)表述 Apache 2.0 with LLVM Exception，API 的 SPDX 值为 `NOASSERTION`。 |
| 不适用 | [固定 patch 的 HTTP 模块](https://github.com/KitsukiDev/hk4e-patch-universal/blob/2b589125874b0b96f8694bfad31e34811eba8573/src/modules/http.rs#L57) 的 `on_make_initial_url` 从寄存器读取 URL 并执行拦截改写；它不是 `LoadLocalConfig/SetEnv/GetURL/RequestComboLogin → PostRequest` 的 managed 选择或实参采用证据。不能将其重定向、验签或完整 DLL 行为作为本客户端实现。 |
| 未知 | 目标 7.2 的 managed 选择和自然请求采用、官方 CRT/IL2CPP 源码与当前客户端字节的身份关系、其他仓库及未查分支。A 本样本 size 16 纯 SSE 分支由本机研究提供证据；本轮没有执行目标或验证更大尺寸的外部边。 |

2026-11-10 12:51 UTC 的 [patch 可见分支响应](https://api.github.com/repos/KitsukiDev/hk4e-patch-universal/branches?per_page=30) 为一个 `main`，tip `2b589125874b0b96f8694bfad31e34811eba8573`；无后续页，复用旧冻结 tree `822e0b73e04a6ec63461e0d7ce87c923f7e5e964` 和已核对的文件。旧 native map 检索不代表此前已覆盖新的 managed 缺口；本轮只对缓存文件搜索了计划中的相关词，并查看上述 HTTP 文件，不代表所有源码的语义审计。GPLv3 原文 blob `07d7288fe61f7bd217ecd661b13dd5324fbe1b48`，是否含 only-or-later 未确定。

四个不同的计划中 B 精确词均提交过网页检索；搜索服务返回合并空结果，无法证明 GitHub 无命中或已覆盖每词前 20 个命中。GitHub code search 对第一词返回 **401 Requires authentication**，因此未继续调用其余三词；现有固定 tree 是受限 fallback，fork 和其他分支的 managed 覆盖未建立。执行时混合网页请求误将 B 第一词重复一次：实际为四个不同词、五次词条执行；这是预算偏差，原账本未改。A 按保守逐词计数用了 9 次网页词条和 3 次 GitHub API 请求，达到 12 次上限；非主线索引结果没有当作候选接入，也没有新 clone。已有外部 clone 只有资源与 phnt，未发现 IL2CPP/GC/CRT clone；旧 IL2CPP 缓存不等于 clone 或本次新证据。

私有独立目录 `local/migration72/d02-resume-20261110b/github-increment/` 保存检索计划、逐请求账本、原响应、来源 SHA 和分支/许可清单。未改运行代码、Proto、配置、补丁或资源，未构建、部署、启动服务或客户端，客户端验收新增 0；本卡未提交，交主线复核。下一项由主线按固定本机证据实施并验证 A 准确分支；B 仍需独立 managed 证据，不因检索空结果推断请求成功。

主线2026-11-11核8项输入及16项产物大小/SHA通过，proof SHA `63688e2e59f3606b215dcd1cc46e094d17f8bfeda1aea90482042528761e259f`。B专题在研究中继续增长，派工时5407B计划已按原SHA精确恢复为`b-plan-at-dispatch.snapshot.md`，不修改旧manifest/pin、不把当前文档改作旧来源。README优先的新AGENTS规则在本run后由用户新增，后继Scene已遵守；本run覆盖和错误不回写成完整。
