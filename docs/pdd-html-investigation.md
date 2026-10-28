# 拼多多 H5 同源兼容实验

日期：2026-10-27。分支 `feature/pdd-html`，版本 0.6.0 / versionCode 17。用户已在手机登录后确认真实身份码显示。

## 已确认的现象

- 用户在电脑 Edge 打开 `https://m.pinduoduo.net/mdkd/package?tab=ID_CODE`，登录后仅显示页面框架，随后返回登录。未显示实际条形码。
- 官方验证流程一度落在 `/index.html`，可能丢失返回目标。重新打开身份码地址后又返回登录页。
- 用户提供的前置检查 `/api/apoc/binding/mobile/dsd/fast/bind/c/mobile/pre/check` 返回 `error_code: 40001`。官方页面代码将此码解释为会话未登录。
- 用户的请求列表没有 `generate_identification_code`，尚未进入真正取码步骤。
- DevTools 提示一枚未指定 SameSite 的 Cookie 因跨站响应被阻止。其名称为 `api_uid`。公开前端也把该字段用于访客/日志标识，不能仅凭此认定登录凭据被拒收，更不能把允许第三方 Cookie 当成已验证修复。

## 官方公开代码中的路径差异

- [身份码页面](https://m.pinduoduo.net/mdkd/package?tab=ID_CODE) 的应用脚本将浏览器业务请求发往 `https://api.pinduoduo.com`，两者不是同站点。
- [登录共用脚本](https://static.pddpic.com/assets/js/react_pdd_21cc9d1274e37d35761f.js) 中，普通网页登录的默认 API 基址为当前站点 `/proxy/api/`。
- 无登录、无凭据的公开请求 `https://m.pinduoduo.net/proxy/api/api/server/_stm` 返回 HTTP 200 和 `server_time`，确认官方同源代理路由存在。该检查不能证明受保护业务接口已接受用户登录。
- 身份码组件先检查快递手机号绑定，再由官方业务逻辑请求码值并绘制 `canvas#barcode`；不是静态 HTML 中已有可复制的身份码。

## 测试版实现

PddPage 直接加载原官方 H5，登录、验证和业务请求仍由网页执行。仅拦截官方公开 CDN 的 `/mdkd/mdkd/_next/static/chunks/pages/_app-<hash>.js`，用正常 HTTPS 下载其公开内容，在执行前加上 `pdd-same-origin.js`。该脚本只在 `https://m.pinduoduo.net/mdkd/` 内，把 `https://api.pinduoduo.com/api/...` 改为本站 `/proxy/api/api/...`。

兼容代码不读取 Cookie、令牌、手机号或验证码，不复制请求头到原生网络层，不修改服务器 Cookie 属性，不伪装原生客户端，不关闭证书验证。其他域名、端口、登录页和验证页不改写。公开脚本保存在内存，不持久化用户信息。真正 API 请求的请求体、请求选项及 Cookie 管理由原网页和 WebView 继续负责。

官方验证后落到首页时，最多自动尝试一次返回身份码入口；仍失败时保留官方登录画面，避免重复强制跳转。客户端按钮为用户手动备用入口。

## 检查与限制

- 34 项淘宝导航、19 项 PDD 导航/公开脚本范围、PDD 请求 URL/查询/方法/请求体/选项保持、作用域和重复注入保护检查，以及现有登录与刷新回归均通过。
- Java 编译、DEX、对齐、v2/v3 签名通过。APK 50,129 字节，SHA256 `870a2038a677529969e807d25aa6637a9dacf9bdb8c16afe3e956d798eb8f325`。
- 真机通过 `<手机IP>:<端口>` 连接，同签名覆盖安装 Success，冷启动 Status: ok，code17/0.6.0 与前台运行核对正确，本次进程未见 AndroidRuntime fatal 标记。
- 用户完成官方登录后回复「已显示真实身份码」，确认手机 H5 实际取码成功。没有采集、保存或复述码值。尚未验收长时间保活、码过期后的自动更新和驿站扫码；不能将本次用户反馈扩大为所有业务已通过。
- 浏览工具阻止观察另一个官方域名；未继续通过其他通道读取该被阻止页面。后续原地址的 Windows 浏览器操作也因无法可靠识别当前网址而停止，诊断数据由用户手动提供。没有导出浏览器 Cookie 或完整网络记录。
- 旧端口 36633 已废弃，本次使用 40547。无线调试端口可能变化，下次执行前重新核实连接。验收后不继续操作手机。
