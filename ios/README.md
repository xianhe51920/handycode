# HandyCode iOS（测试分支 `codex/ios-version`）

基于 Android **0.8.4** 功能与前端适配脚本构建的原生 Swift / UIKit / `WKWebView` 版本，工程定义位于 [project.yml](project.yml)。

## 目录结构

- `HandyCode/`：iOS 原生壳与 4 个平台页面视图（`CainiaoPageView`、`TaobaoPageView`、`PddPageView`、`JdPageView`）
- `HandyCodeTests/`：URL 白名单、退出隔离、`SwitchMotion`、`WebAssets`、`WKWebView` 脚本桥与四平台控制器单元测试
- `HandyCodeUITests/`：iOS Simulator 四标签切换与昼夜模式 UI 自动化截图测试
- 共享脚本与图标资源：直接打包引用仓库根目录的 `../app/src/main/assets` 与 `../app/src/main/res/drawable-nodpi`

## WebKit 差异与已知边界

- iOS `WKWebView` 无法在网络层像 Android `shouldInterceptRequest` 那样改写第三方 HTTPS CDN JS 源码，因此拼多多同源适配（`pdd-same-origin.js`）与京东订单采集（`jd-orders.js`）统一通过 `WKUserScript(.atDocumentStart)` 提前注入；脚本日志通过 `WKScriptMessageHandler`（`hcLog`）回传给原生层。
- 拼多多在 iOS 端优先保证同源代理、双卡片投影与整页恢复能力；底层 Webpack chunk 级局部刷新 hook 在 `WKWebView` 上暂不提供。
- 无付费 Apple Developer 证书时，CI 产出 **Simulator 测试结果/截图** 与 **未签名 `.ipa`**（`HandyCode-0.8.4-ios-unsigned.ipa`，可供自签工具安装）。
- 由于当前无真机 iOS 设备登录实测，真实账号登录后在 `WKWebView` 下的平台会话保持与拼多多同源代理端到端表现仍待真机确认。
