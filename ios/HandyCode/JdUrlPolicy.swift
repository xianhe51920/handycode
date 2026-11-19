import Foundation

enum JdUrlPolicy {
    static let orders = "https://trade.m.jd.com/order/orderlist_jdm.shtml"
    static let identity = "https://convenient-h5.jd.com/identityCode?channel=xueshengjirukou"
    static let login = "https://plogin.m.jd.com/login/login?appid=100&returnurl=https%3A%2F%2Ftrade.m.jd.com%2Forder%2Forderlist_jdm.shtml"

    static func isIdentity(_ raw: String?) -> Bool {
        guard allowed(raw), let components = UrlPolicy.httpsComponents(raw) else { return false }
        return components.host?.caseInsensitiveCompare("convenient-h5.jd.com") == .orderedSame
            && components.path == "/identityCode"
    }

    static func isLoginHost(_ raw: String?) -> Bool {
        guard let components = UrlPolicy.httpsComponents(raw) else { return false }
        return components.host?.caseInsensitiveCompare("plogin.m.jd.com") == .orderedSame
    }

    static func allowed(_ raw: String?) -> Bool {
        guard let components = UrlPolicy.httpsComponents(raw), let host = components.host?.lowercased() else { return false }
        return host == "jd.com" || host.hasSuffix(".jd.com")
    }

    static func allowedImage(_ raw: String?) -> Bool {
        guard let raw, raw.count <= 500,
              let components = UrlPolicy.httpsComponents(raw),
              let host = components.host?.lowercased() else { return false }
        return host == "360buyimg.com" || host.hasSuffix(".360buyimg.com")
            || host == "jd.com" || host.hasSuffix(".jd.com")
    }
}
