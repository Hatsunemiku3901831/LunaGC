# F-04c2：便携入口的信任文件供给

2026-11-06。正式security输入供给使用`LUNAGC_TRUST_BUNDLE`读取本地公钥/签名包。本项把已有生成位置接到Windows portable入口，保留版本及部署前置。当前没有安装补丁或启动真实客户端/服务。

## 行为

`Resolve-ClientTrustBundlePath`从显式环境变量读取路径；未指定时选择当前checkout下生成器的默认输出`local/migration72/trust/client72-trust.json`。相对路径按checkout解析为绝对路径，空文件名/目录或缺文件会在入口报错；函数不创建材料、不修改环境或客户端文件。

`tools/local_windows.ps1`在play、patch及启用客户端guard的start入口先验原有客户端版本，再解析信任文件路径、设置本进程环境，随后进入服务/补丁/客户端操作。新启动的PowerShell、guard和游戏子进程继承该绝对路径。禁用客户端guard的server-only start保持原有行为，build/status/reset不新增材料依赖；两个内部补丁/guard函数原有重复版本校验移到调用入口，入口校验后按正常流程执行。

这里预检文件是否就绪及路径传递。材料schema、固定输入、key/root身份与原生签名决定仍由[正式信任合同](CLIENT_72_TRUST_CONTRACT.md)和DLL入口处理；本项的路径夹具`{}`不能当有效密码学材料。直接调用底层patch_game或手动启动游戏/调试器时，调用者须满足相同供给与全部部署前置。

既有管理员x64dbg早于新环境启动。设置当前PowerShell环境不会进入这个既有进程；本项没有修改它的环境、重新启动它或声称其load_executable继承新值。真实调试供给/实际DLL加载继续按Project单独验收，不能据新子进程测试晋级。

## 实际回归

新增9项真实PowerShell隔离测试，和原13项便携入口回归共22项全过，失败/错误/跳过均0（8.887秒）。覆盖checkout及路径含空格、默认/显式绝对/相对路径、目录与缺文件拒绝、真正新PowerShell子进程继承、实际Mode patch把绝对路径交给隔离伪patch子脚本，以及start/play/patch在服务/补丁副作用前拒绝。3个入口由subTest分别验证；所有文件/脚本/伪DLL都在临时目录，Start-Process被拒绝哨守住，没有真实应用启动。

未加生产函数前先实际跑8项，报告9个失败（含subTest），原版本守卫测试仍通过；之后最小接入22项全绿。真实本机Mode patch另实际被7.2/源码遗留7.1守卫拒绝退出1，前后Astrolabe/AccountPlatNative SHA-256保持；不改版本字符串放行。

```powershell
python -m unittest tools.test_client72_trust_launcher tools.test_local_windows -v
```

证据位于`local/migration72/evidence/trust-launcher-verification.json`；日志`trust-launcher-{red,green,final,real-version-rejection}.log`。脚本通过真实PowerShell解析/执行，JAR源码及配置/客户端未变，当前JAR仍为7ca18e10…e8f5f。原Mongo、运行数据和他人资源差异保留。完整P0/SDK/资源及实际信任加载前置不足，未部署，健康/运行PID/运行JAR没有验收对象，D-02未完成。
