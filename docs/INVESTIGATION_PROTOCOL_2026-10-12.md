# 调查手册 7.1 协议恢复

参考 AstaPS 固定提交 `7cc148d1f752a43d4b9146b3b73f26875902546f` 的手册通知与领取实现，
再由本机提供的客户端核对实际字段、嵌套类型、读取／写入代码与字面包号。
当前项目此前只有调查怪物目录协议，没有调查手册章节、目标及奖励消息的 `.proto`。

## 客户端与字段依据

当前客户端文件按下列 SHA-256 校验，只读使用：

- YuanShen.exe：`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`。
- global-metadata.dat：`469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161`。
- 复用的字段／方法／泛型元数据缓存：`2560a32135910327b975dc2701afa063a0d8cefa95f79ef738c61ae7b890a8c5`。

字段编号由本次读取的实际代码核对，字段命名对应参考实现及同名混淆字段。
状态使用 0～3 的协议数值，名称沿用无效／进行中／完成／已领取；本批仅验证数值编码。
`r2mcp` 打开文件后连接关闭，使用 Capstone 对已确认的方法地址作静态反汇编。
客户端文件和补丁没有被修改，未运行外部仓库安装脚本。

| 消息 | 客户端类型 | 代码地址 | 包号 | 字段编号 |
| --- | --- | --- | --- | --- |
| Investigation | CNCJMKCKFPE | `0x15252e840` 读取 | 嵌套 | total_progress=1、id=7、state=15、progress=13 |
| InvestigationTarget | HPDFKIKJMCJ | `0x1499671a0` 读取 | 嵌套 | investigation_id=5、quest_id=8、state=6、progress=2、total_progress=13 |
| PlayerInvestigationAllInfoNotify | KKFJNFDCFMO | `0x148fdc560` 读取 | 20026 | investigation_list=2、investigation_target_list=10 |
| PlayerInvestigationNotify | CAGEHFLCHEP | `0x1476088f0` 读取 | 25214 | investigation_list=6 |
| PlayerInvestigationTargetNotify | KKOAFFKHOFD | `0x1477787c0` 读取 | 5903 | investigation_target_list=1 |
| TakeInvestigationTargetRewardReq | INBBJMODBNE | `0x14818ec70` 写入 | 2703 | quest_id=9 |
| TakeInvestigationTargetRewardRsp | ADCNNGMFIJC | `0x14c97e340` 读取 | 22263 | quest_id=14、retcode=9 |
| TakeInvestigationRewardRsp | AKMDMFFKPBF | `0x14eb68c80` 读取 | 27528 | id=3、retcode=10 |

加入以上 8 份源协议，构建按现有流程生成 Java。已确认目标领取请求的包号替换原负占位；
章节领取请求包号仍未确认，继续保留负占位。

## 验证与后续

先新增 14 项测试，再恢复协议：固定上一版对照 **14 项全部失败**，错误与跳过均为 0。
用本次客户端代码所确定的标签构造独立二进制样本，验证标量、嵌套列表、0～3 状态数值、
重新编码后的数据含义、目标领取包号与截断请求拒绝。样本没有声称来自实机抓包。
最终 **25 项／4 套件**通过，含现有登录／心跳兼容和恢复协议描述器初始化检查；编译与打包通过。
784 处输入、45 个变化运行类校验一致，
其中新增 44 个生成协议运行类；没有非预期变化或删除运行类。
JAR SHA-256：`3f941064fa69e5ccb3885d9a10e659c52c635beb5eace5f51f57cb76b95017bd`。
固定记录：`debug/aster-mid-20261011/batch128/`。

本批完成协议恢复，调查手册的玩家进度保存、登录通知、事件触发和奖励领取处理仍需接入。
首次通关与首次领取应分别记录，领取逻辑需做有效目标、开启条件、完成进度与重复领取检查。
原生界面、状态语义与章节领取请求仍待验证；本批没有重启日常服务或游戏。
