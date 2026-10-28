# 无线调试与安装

Android 11 及以上支持配对码无线调试。手机和电脑需连接同一 Wi-Fi。

1. 手机进入「设置 → 开发者选项 → 无线调试」，开启并选择「使用配对码配对设备」。
2. 在电脑普通 PowerShell 中运行：

```powershell
cd E:\handyCode
.\tools\wireless-debug.ps1
```

3. 按提示输入手机配对弹窗的 IP:端口，在 ADB 提示处输入临时配对码。然后返回手机无线调试主页面，输入主页面的 IP:端口。连接端口与配对端口通常不同。
4. 脚本仅在指定且已成功配对的手机上安装并启动本项目 APK，不会清除应用数据，也不会修改防火墙或监听所有网卡。

## 2026-10-27 实测进展

本轮已完成无线配对、连接、安装和启动。手机为 Android 16 设备；手机包管理器确认 `com.fairycrane.handycode` 已安装，版本 `0.2.0` / versionCode `2`。`adb install -r` 返回 `Success`，启动返回 `Status: ok`、`LaunchState: COLD`。

自动发现的配对地址形如 `<手机IP>:<配对端口>`，连接地址形如 `<手机IP>:<连接端口>`。这些是当时有效的端口，重开无线调试后应重新读取，不能视为永久地址。临时配对码不保存。

ADB 服务未运行时先执行 `adb start-server`，随后配对和安装均可正常完成。服务使用默认本机端口 5037，不需要修改防火墙或改为全网卡监听。

已检查应用版本、运行进程和应用进程警告日志，未见本次启动崩溃。用户随后完成手机操作，已通过前台应用截图确认淘宝官方内嵌页面显示身份条形码。登录重启保留、亮码、拼多多身份码及驿站扫码仍待验收；不在记录中保存手机号、码值或临时配对码。

## 历史失败记录

之前受限代理执行环境中 ADB 启动失败，前台诊断报：

`failed to create fdevent interrupt socketpair: Invalid argument`

该失败发生在此前会话，已由上述本轮成功结果更新。交互脚本此前通过 PowerShell 语法检查；本轮使用直接 ADB 命令完成配对、连接、安装和启动，没有执行完整交互脚本。

后续更新（使用无线调试主页面的连接地址）：

```powershell
$adb='D:\developer\Unity\6000.3.26f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK\platform-tools\adb.exe'
& $adb connect '手机IP:连接端口'
& $adb -s '手机IP:连接端口' install -r 'E:\handyCode\handyCode-0.2.0-debug.apk'
```

配对会授权此电脑调试手机；测试完可在手机的无线调试页面关闭调试，或在已配对设备中移除此电脑。

官方说明：https://developer.android.com/tools/adb#wireless
