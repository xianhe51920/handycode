# 在电脑测试 APK

使用 Android 官方模拟器运行实际 APK。需要 JDK、可用的硬件虚拟化和能够访问 Google Android SDK 下载服务器的网络。

本次检查没有检测到已安装/运行的安卓模拟器。已有 Unity Android SDK，但没有 emulator 或系统镜像。执行 sdkmanager 查询后返回 “Failed to download any source lists / IO exception while downloading manifest”。因此本次还未启动 Android 或进行电脑运行测试；这不是 APK 安装或运行失败的证据。

## 本机操作

在普通 PowerShell 终端进入项目，首次运行：

```powershell
cd E:\handyCode
.\tools\test-on-pc.ps1 -Setup
```

脚本会复用 Unity 的命令行工具到本项目测试目录，下载 Google 官方模拟器和 Android 15 Google APIs 系统镜像，创建名为 HandyCode_API35 的虚拟手机，然后安装并启动当前 APK。首次下载需要较多空间与网络流量；涉及的许可由你在终端阅读确认，脚本不自动接受。

后续直接启动：

```powershell
.\tools\test-on-pc.ps1
```

下载内容保存在 android-test-sdk，虚拟手机数据保存在 android-test-data。脚本使用专用端口 5580 并核对已有模拟器名称，不向其他已连接手机安装。首次启动最多等待 3 分钟。

如果出现加速/虚拟化检查失败，需要先根据 Android 官方要求配置电脑；本脚本不修改 BIOS、Windows 虚拟化或安全设置。如果官方镜像下载仍失败，应先解决下载网络问题。

## 测试范围

可以测试 APK 安装、启动、平台切换、设置、刷新、返回、网页加载和屏幕亮度操作。模拟器网页登录与真实手机的风控表现可能不同；首次登录由你在官方网页完成。

淘宝真实出码仍需验证；拼多多当前默认只是官方移动首页，不应将其当作身份码功能已完成。驿站扫码取件最终需要手机和实际包裹验收。

## 本次脚本检查

仅进行 PowerShell 语法检查。由于缺少可用模拟器与系统镜像，没有声称该脚本已在本机完整运行成功。

官方说明：https://developer.android.com/studio/run/emulator
