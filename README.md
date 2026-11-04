# LunaGC CN 7.2 开发分支

目标是原生适配所提供的国服 7.2 客户端，包括网络指令、消息及字段、客户端补丁与资源。**尚未支持 7.2 登录或进入世界。** 本分支不维护 7.1 兼容；遗留版本号、Proto、包号仍是研究输入，不能视为目标协议。

接手顺序：[工作规范](AGENTS.md) → [任务与验收](Project.md) → [研发索引](Harness.md)。详细方法见 [迁移研究](docs/CLIENT_72_MIGRATION.md)。历史专题保留业务背景，不构成 7.2 验收。

| 支持层次 | 当前状态 |
| --- | --- |
| 计划支持 | 新旧账号登录、选人、入场、基础玩法及后续内容，按 Project 分项验收 |
| 已实现 | 继承业务/便携脚本，7.2安装清单校验、基础元数据提取、协议静态盘点；不代表业务已匹配7.2 |
| 测试通过 | 提取/清单/盘点及脚本测试已执行，见Project；Java回归仍在验证，历史7.1结果不算目标证据 |
| 客户端验收通过 | 暂无；进入世界与完整玩法分别验收 |

前置缺口：基础类型/字段/方法已恢复，泛型/父类/布局仍缺；缺已验证的三层映射和 7.2 资源来源；补丁需 ABI、对象布局与实机验证；本地安装清单7,830条大小/MD5已匹配。未知项不发布为正式包号或字段。

使用 Windows 便携方式。需要 JDK 17、便携 MongoDB、Python 3.11+（研究工具）、Git/LFS；补丁构建另需 Rust nightly 和 MSVC。依赖/本机配置不随 Git 交付。准备及排障见 [部署手册](docs/CODEX_ONE_CLICK_DEPLOY.md)，本分支约束和路径以 AGENTS、Project 和本机配置为准。

在仓库根目录运行，先按 Harness 核对配置及占用：

```powershell
# GAME_PATH 需从已核对的本机配置赋给当前 shell；Python 不自动读取 .env
python tools/audit_client.py --client $env:GAME_PATH --output local/migration72/evidence/client-audit.json
python -m unittest tools.test_audit_client tools.test_local_windows tools.test_patch_game tools.test_runtime -v
# 先设置 JAVA_HOME/PATH 为便携 JDK 17，确认目标 JAR 没有被占用
.\gradlew.bat --no-daemon jar -PskipHandbook=1 --console=plain
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/local_windows.ps1 -Mode status
```

当前 JAR 名称仍由 build.gradle 派生为 LunaGC-7.1.0.jar，并非可连接 7.2 的产物。Java 测试/集成测试先配置独立测试数据库，见 Harness。

| 入口 | 作用与限制 |
| --- | --- |
| Start-GS.cmd / Stop-GS.cmd / GS-Status.cmd | 启动或复用、正常保存停服、检查；start 不构建，默认可能安装客户端补丁 |
| Rebuild-GS.cmd / local_windows.ps1 -Mode build | 停服后构建服务端 |
| Rebuild-Patch.cmd / -Mode buildpatch | 构建 Rust 补丁，不证明适用于目标客户端 |
| Play-GS.cmd / -Mode play | 服务与客户端入口；当前 7.2 前置不满足，不能当作可玩入口 |
| Restore-Client.cmd | 退出目标客户端后恢复补丁，不用于无关客户端 |

Proto Java 由 Gradle/protoc 生成，不编辑 build/generated。版本无关业务回归保留；旧协议断言在有证据的 7.2 fixture 接管后替换，不能删除后声称兼容。

上游与署名：[girluh/LunaGC](https://github.com/girluh/LunaGC)（基于 Grasscutter）、[capyb2222/animegamepatch](https://github.com/capyb2222/animegamepatch)；Terax（nt）。资源来源和权属见 [资源 README](resources/README.md)。
