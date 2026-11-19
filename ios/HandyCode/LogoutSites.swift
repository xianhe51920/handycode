import Foundation

enum LogoutSites {
    static func domains(for page: Int) -> [String] {
        switch page {
        case 1: return ["taobao.com", "tmall.com", "alipay.com", "cainiao.com", "alibaba.com"]
        case 2: return ["pinduoduo.net"]
        case 3: return ["jd.com"]
        default: return []
        }
    }

    static func matches(host: String, domains: [String]) -> Bool {
        let lower = host.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
        return domains.contains { lower == $0 || lower.hasSuffix("." + $0) }
    }
}
