# F-03d3f4d2 / CLEAR48-OWNED-PREP

已准备 `tools/client72_d02_clear48_owned.py` 和纯门禁测试。该驱动只面向独立人工 64B owned buffer、owned stack 与两组显式人工控制，不使用目标 heap、stock 控制量或自然 caller。固定原 904B helper 和 GC64 第三合同及其十源逐项核完整大小/SHA；另固定实施规格、既有 first-pop 驱动和 emulator loader。门禁结果不证明现场 controls、自然 GC 或 Avatar。

两 case 固定为 SSE 的 I=2/T0=48/原表目标 RVA `01fdfd35`，以及 AVX 的 I=3/T0=47/T1=48/F=0/原表目标 RVA `01fdfb94`；只授权相应 PC 读取各自控制 span、原 RET 读取 owned 栈首 8B。原指令限 904B，非授权读、写、原指令边界及未知栈控制均停止。仅 H+16..H+64 的原 PC/地址/宽度可写；每条原写保留写前/写后 inspector bytes 与后端拆分事件，AVX 两条重叠写不合并。原 RET、sentinel、RAX、RSP、保护前 16B、每笔原写、后端覆盖和人工输入读在 inspector 中检查；失败保留已发生的原事件和后端异常，不替换 CPU 或伪造返回。

本包只运行 `python -B -m unittest tools.test_client72_d02_clear48_owned`：7 项通过，覆盖来源/十源漂移、控制值、按 PC 的读写权限、越界、输出目录/禁止覆盖、来源失败原件及拒绝不返回的结果。独立纯门禁报告 `local/migration72/d02-resume-20261110b/a-clear48-owned/pure-gates.private.json` SHA `1ed6a921d24d9a66980b3b9b7ddad828ed0fd072607d576583f70c743a3e8ea4`，包含实际命令/输出和 17 项完整来源执行前后 SHA。此测试不导入/构造 Unicorn、不运行原 helper 或人工 CPU；被测模块的 Python 导入不会调用 loader。

主线完成独立审核后可选尚不存在的私有输出名，串行执行首次原 CPU 能力测试：

```powershell
python -B -m tools.client72_d02_clear48_owned --output local/migration72/d02-resume-20261110b/a-clear48-owned/owned-first.private.json
```

输出以独占创建保留，既有文件拒绝覆盖；各 case 5000 指令/5 秒。原指令在当前 Unicorn 的 SSE/AVX 支持尚未知，结果必须以实际首次运行及随后复验为准；异常即失败并保留，不能按静态推导填成功。人工 buffer 写范围仅 48B；capturedCopyWriteApproved、naturalGC、completeAvatar、d02Complete 均 false。本包没有目标 API/进程/DLL、调试器、现场 CPU、数据库、JVM、构建或 Git 操作；D-02 实机不因此晋级。

## 主线实际验收与后端停止

上述准备后主线实际执行已保留两原报告。首轮在CPU构造前因误将distribution版本当binding版本而停止；核固定原文件后准确区分distribution5.0.9、binding5.0.7和native(5,0,1280)，另加两个原库文件pin，不安装或换版本。纯测试pin数量14→16同步，7项通过；修正期间曾在纯count断言失败后继续到独立owned命令，该次真实来源门禁通过，原日志保留。后续命令均先核exit再执行依赖。

第二实际报告SHA `426223c136b5872cca7f01ae5393c23408582905d86d81a6e5b936109b1781fd`：SSE35条、三笔16B原写/原RET通过；AVX20条后原141fdfab2的6B与native hook width4冲突，严格guard停止，0AVX写/RET。旧reason文字说outside-code，实际是长度不符，未删guard或补host结果。当前两路径完整执行未完成。

独立Sol max后端诊断及Terra max官方源审见[CLEAR48-BACKEND](CLIENT_72_D02_CLEAR48_BACKEND_20261111.md)、[官方源](CLIENT_72_D02_UNICORN_AVX_SOURCES_20261111.md)；主线纯第三核112pin/真实报告同SHA `7dd3de897180454714d381569513e7bfe8b29e0046a255892c334bd224542674`。后续新stock原I6选择AVX，因此不能用已验SSE替代；独立可执行AVX后端仍须另定规格，当前不重跑失败。
