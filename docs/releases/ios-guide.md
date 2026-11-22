# HandyCode iOS 0.8.7 — 全版本安装与续签指南

> 下载包说明：下方附件中的 **`HandyCode-0.8.7-trollstore.ipa`** 虽然文件名带有 `trollstore`，但本身就是**通用 `.ipa` 安装包**——无论你用的是老系统还是最新的 **iOS 27.2.1**，都直接下载这同一个文件，再按下面对应系统版本的路线安装即可。本次 **0.8.7** 已同步 Android 最新版的拼多多新版前端分段刷新修复、定位/HTTP 模块动态识别及地理编码参数兼容。

---

## 先看你的 iOS 系统版本选路线

```text
你的 iPhone / iPad 系统版本是？
  ├─ iOS 14.0 ～ 16.6.1 / 17.0（早期版本）──→ 【路线一：TrollStore 永久免签】（装完永不过期）
  └─ iOS 16.7+ / 17.1+ / 18 / 26 / 27（含 iOS 27.2.1）
       └─ Apple 已封堵 TrollStore 漏洞，必须走 ─→ 【路线二：新系统通用安装方案】
                                                   ├─ 方案 A：SideStore（推荐，首次连电脑，之后手机端续签）
                                                   ├─ 方案 B：爱思助手 / AltStore / Sideloadly（电脑自签，最简单）
                                                   └─ 方案 C：个人开发者 / UDID 定制证书（1 年免折腾）
```

> [!WARNING]
> **iOS 17.1+ ～ iOS 27.2.1 用户注意**：不要尝试找所谓“免越狱巨魔 TrollStore 支持 iOS 27”的工具，全部是虚假/骗局教程。新系统请直接看下方**【路线二】**。

---

## 路线一：TrollStore 永久免签（仅限 iOS 14.0 ～ 16.6.1 / 17.0）

此路线利用系统漏洞，**安装后永久可用，永不过期，无需任何续签**：

1. **先装好 TrollStore**：
   - **iOS 14.0 ～ 16.6.1**：电脑用签名工具安装 [TrollInstallerX.ipa](https://github.com/alfiecg24/TrollInstallerX/releases)，在手机打开点击 **Install TrollStore**。
   - **iOS 15.0 ～ 17.0**：电脑下载 [TrollRestore](https://github.com/JJTech0130/TrollRestore/releases)，手机连电脑按提示写入。
   - 多版本支持明细见官方对照表：[TrollStore CFW 指南](https://ios.cfw.guide/installing-trollstore/)。
2. **安装 HandyCode**：
   - 用 iPhone Safari 下载本页附件中的 **`HandyCode-0.8.7-trollstore.ipa`**；
   - 下载完成后点文件“分享”到 **TrollStore** → 点击 **Install** 即可永久使用。

---

## 路线二：新系统通用安装方案（支持 iOS 16.7+ / 17.1+ / 18 / 26 / 27，含 iOS 27.2.1）

新系统安装第三方 `.ipa` 有两项**必须先完成的系统设置**（很多人装好打不开都是漏了这两步）：
1. **开启开发者模式**：安装自签 App 后，打开手机 **设置 → 隐私与安全性 → 开发者模式**，打开开关并按提示重启手机；
2. **信任开发者证书**：打开手机 **设置 → 通用 → VPN 与设备管理**，点开你登录签名的 Apple ID 邮箱，点击 **“信任”**。

> 免费个人 Apple ID 限制说明：自签应用每次有效期 **7 天**，每台设备同一时间最多保留 **3 个**激活的自签应用（SideStore 本身占用 1 个）；**7 天内重新点刷新/覆盖安装不会丢失 App 内的网页登录状态**。

---

### 方案 A：SideStore（推荐 · 首次连电脑，之后手机端免电脑续签）

- **适合人群**：不想每隔 7 天就要找电脑连数据线的用户（支持最新 **iOS 27.2.1**）。
- **准备**：首次配置需要 Windows / Mac 电脑与数据线、任意免费 Apple ID（建议单独用小号 Apple ID）。
- **步骤**：
  1. 按照官方引导 **[sidestore.io](https://sidestore.io/)**，用电脑首次把 **SideStore** 装进手机，并导入一次设备配对文件（Pairing File）；
  2. 按官方指引在手机配好本地回路 VPN（LocalDevVPN / 官方推荐工具），并确保已在 **设置 → 隐私与安全性** 开启 **开发者模式**；
  3. 用 iPhone **Safari** 下载本页附件 `HandyCode-0.8.7-trollstore.ipa`；
  4. 开启本地 VPN 后打开 **SideStore**，点左上角 `+` 号选择刚下载好的 `.ipa` 即可自动签名安装；
  5. **后续续签**：以后每 7 天内，只要手机连着 Wi‑Fi 并打开本地 VPN，在 SideStore 里点一下 **Refresh All** 即可直接续期（也可配合 iOS「快捷指令」设成每周自动触发），全程不再需要电脑。

---

### 方案 B：电脑直接自签（爱思助手 / Sideloadly / AltStore · 上手最快）

- **适合人群**：手边随时有 Windows 或 Mac 电脑、希望 3 分钟直接装好体验的用户。
- **以爱思助手 / Sideloadly 为例（均支持 iOS 27.2.1）**：
  1. 电脑下载安装 [爱思助手](https://www.i4.cn/)（或 [Sideloadly](https://sideloadly.io/)）以及先在电脑下载本页的 `HandyCode-0.8.7-trollstore.ipa`；
  2. 手机用数据线连接电脑，并在手机弹窗点击 **“信任此电脑”**；
  3. **爱思助手路径**：打开顶部 **“工具箱” → “IPA 签名”** → 添加 `HandyCode-0.8.7-trollstore.ipa` → 选择 **“使用 Apple ID 签名”** → 登录个人 Apple ID 并开始签名，完成后点击安装到手机；  
     **AltStore 路径**：也可使用 AltServer 在同一局域网 Wi‑Fi 下实现电脑开机即自动续签。
  4. 装好后若系统提示未受信任或需要开发者模式：依次完成上方的 **打开开发者模式（重启）+ VPN 与设备管理中信任证书**；
  5. **7 天到期处理**：第 7 天前后手机连接电脑重新点一次签名并覆盖安装，**无需卸载**，原有登录态可继续保留。

---

### 方案 C：个人开发者 / 独立 UDID 定制证书（1 年免续签）

- **适合人群**：完全不想折腾 7 天续签的用户。
- **做法**：
  1. 若持有 Apple 个人开发者账号（\$99/年），或通过正规渠道为自己设备 UDID 签发的 1 年期个人开发证书（`.p12` + `.mobileprovision`）；
  2. 在 iOS 端使用签名工具导入证书和本页 `HandyCode-0.8.7-trollstore.ipa` 完成签名安装（同样需按提示信任对应描述文件/开启开发者模式）；
  3. 有效期内（最长 1 年）无需每 7 天续签。*(注：不建议使用来路不明的免费共享企业证书，随时可能被苹果集中吊销导致闪退。)*
