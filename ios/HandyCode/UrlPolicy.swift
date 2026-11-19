import Foundation

enum UrlPolicy {
    static let localLogoutAction = "https://handycode.invalid/actions/logout"
    static let pddIdentityAppLink = "pinduoduo://com.xunmeng.pinduoduo/mdkd/package?tab=ID_CODE"
    static let taobaoEntry = "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code"
    static let taobaoParcelsEntry = "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"
    static let taobaoAddPackageEntry = "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/school-search-ssr?from=XY&openFrom=emptyPackage"
    static let pddEntry = "https://m.pinduoduo.net/mdkd/package?tab=ID_CODE"
    static let cainiaoEntry = "https://page.cainiao.com/cn-app-web/identity-code/index.html#/"
    static let cainiaoPath = "/cn-app-web/identity-code/index.html"

    private static let allowedHosts = ["taobao.com", "tmall.com", "alipay.com", "cainiao.com", "alibaba.com"]

    static func isAllowed(_ raw: String?) -> Bool {
        guard let components = httpsComponents(raw), let host = components.host?.lowercased() else { return false }
        return allowedHosts.contains { host == $0 || host.hasSuffix("." + $0) }
    }

    static func isPddAllowed(_ raw: String?) -> Bool {
        guard let components = httpsComponents(raw), let host = components.host else { return false }
        return host.caseInsensitiveCompare("m.pinduoduo.net") == .orderedSame
    }

    static func isTaobaoPhonePage(_ raw: String?) -> Bool {
        guard let components = httpsComponents(raw),
              components.host?.caseInsensitiveCompare("pages-fast.m.taobao.com") == .orderedSame else { return false }
        return matches(components.path, pattern: "^/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/phone-manage(?:-ssr|-xy-ssr)?$")
    }

    static func isTaobaoAddPhonePage(_ raw: String?) -> Bool {
        guard let components = httpsComponents(raw),
              components.host?.caseInsensitiveCompare("pages-fast.m.taobao.com") == .orderedSame else { return false }
        return matches(components.path, pattern: "^/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/add-friend-phone(?:-ssr)?$")
    }

    static func isTaobaoAddPackagePage(_ raw: String?) -> Bool {
        guard let components = httpsComponents(raw),
              components.host?.caseInsensitiveCompare("pages-fast.m.taobao.com") == .orderedSame else { return false }
        return matches(components.path, pattern: "^/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/school-search(?:-ssr)?$")
    }

    static func isPddLoginPage(_ raw: String?) -> Bool {
        guard isPddAllowed(raw), let components = httpsComponents(raw) else { return false }
        return components.path == "/login.html"
    }

    static func isPddPackagePage(_ raw: String?) -> Bool {
        guard isPddAllowed(raw), let components = httpsComponents(raw) else { return false }
        return components.path == "/mdkd/package"
    }

    static func opensPddIdentity(current: String?, target: String?, mainFrame: Bool, gesture: Bool) -> Bool {
        mainFrame && gesture && isPddLoginPage(current) && target == pddIdentityAppLink
    }

    static func isCainiaoEntry(_ raw: String?) -> Bool {
        guard isAllowed(raw), let components = httpsComponents(raw) else { return false }
        return components.host?.caseInsensitiveCompare("page.cainiao.com") == .orderedSame && components.path == cainiaoPath
    }

    static func isCainiaoLoginPage(_ raw: String?) -> Bool {
        guard isAllowed(raw), let components = httpsComponents(raw), let host = components.host?.lowercased() else { return false }
        if host.hasPrefix("login.") || host.hasPrefix("passport.") { return true }
        return matches(components.path, pattern: "^.*/login(?:[./].*)?$")
    }

    static func httpsComponents(_ raw: String?) -> URLComponents? {
        guard let raw, !raw.isEmpty, raw.count <= 8192 else { return nil }
        for scalar in raw.unicodeScalars {
            if CharacterSet.controlCharacters.contains(scalar) || CharacterSet.whitespacesAndNewlines.contains(scalar) || scalar == "\\" {
                return nil
            }
        }
        guard let components = URLComponents(string: raw),
              components.scheme?.caseInsensitiveCompare("https") == .orderedSame,
              components.user == nil, components.password == nil,
              (components.port == nil || components.port == 443),
              let host = components.host, !host.isEmpty else {
            return nil
        }
        return components
    }

    private static func matches(_ value: String, pattern: String) -> Bool {
        value.range(of: pattern, options: .regularExpression) != nil
    }
}
