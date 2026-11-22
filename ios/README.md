# HandyCode iOS（测试分支 `codex/ios-version`）

基于 Android **0.8.7** 功能与前端适配脚本构建的原生 Swift / UIKit / `WKWebView` 版本，工程定义位于 [project.yml](project.yml)。

## 目录结构

- `HandyCode/`：iOS 原生壳与 4 个平台页面视图（`CainiaoPageView`、`TaobaoPageView`、`PddPageView`、`JdPageView`）
- `HandyCodeTests/`：URL 白名单、退出隔离、`SwitchMotion`、`WebAssets`、`WKWebView` 脚本桥与四平台控制器单元测试
- `HandyCodeUITests/`：iOS Simulator 四标签切换与昼夜模式 UI 自动化截图测试
- 共享脚本与图标资源：直接打包引用仓库根目录的 `../app/src/main/assets` 与 `../app/src/main/res/drawable-nodpi`

## 本版能力与 WebKit 差异

- **淘宝已登录直出合卡与回退**：在淘系已登录时通过 `loadHTMLString` 配合官方 `baseURL` 直接并行加载身份码与包裹子框架；检测到子框架跳转登录页或鉴权失败时自动回退到顶层官方登录流。
- **拼多多同源适配与定位补齐**：`pdd-same-origin.js`、`pdd-section-refresh.js`、`pdd-layout.js` 等通过 `WKUserScript(.atDocumentStart)` 注入，并由 `PddPageView` 通过 `CLGeocoder` 响应 `__HANDYCODE_PDD_GEOCODE__:` 完成「添加包裹」站点定位校验。
- **后台错峰预热与导航容错**：前台页就绪后（或 2 秒兜底）按 450ms 错峰静默预热其余已登录页面，屏蔽跳转重定向上报的 `NSURLErrorCancelled`（`-999`）误报。
- 无付费 Apple Developer 证书时，CI 产出 **Simulator 测试结果/截图** 与 **通用/TrollStore `.ipa`**（`HandyCode-0.8.7-trollstore.ipa`，iOS 14 ～ 27.2.1 均可按对应路线重签安装）。
