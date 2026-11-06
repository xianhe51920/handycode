# 无线调试与安装

Android 11 及以上支持配对码无线调试。手机和电脑需连接同一 Wi-Fi。

1. 手机进入「设置 → 开发者选项 → 无线调试」，开启并选择「使用配对码配对设备」。
2. 在电脑普通 PowerShell 中运行：

```powershell
cd <项目目录>
.\tools\wireless-debug.ps1
```

3. 按提示输入手机配对弹窗的 IP:端口，在 ADB 提示处输入临时配对码。然后返回手机无线调试主页面，输入主页面的 IP:端口。连接端口与配对端口通常不同。
4. 脚本仅在指定且已成功配对的手机上安装并启动本项目 APK，不会清除应用数据，也不会修改防火墙或监听所有网卡。

## 更新安装

在项目目录运行，使用 Android SDK 中的 adb：

```powershell
$adb = Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe'
& $adb connect '<手机IP>:<连接端口>'
& $adb -s '<手机IP>:<连接端口>' install -r '.\HandyCode-0.8.3-release.apk'
```

配对会授权此电脑调试手机；测试完可关闭无线调试或移除已配对电脑。不要提交配对码、设备地址、截图中的账号或身份码。

官方说明：https://developer.android.com/tools/adb#wireless
