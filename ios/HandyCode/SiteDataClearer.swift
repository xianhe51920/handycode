import Foundation
import WebKit

final class SiteDataClearer {
    static let shared = SiteDataClearer()
    private(set) var isBusy = false

    func clear(page: Int, completion: @escaping () -> Void) {
        let domains = LogoutSites.domains(for: page)
        guard !domains.isEmpty else {
            completion()
            return
        }
        isBusy = true
        let store = WKWebsiteDataStore.default()
        let cookieStore = store.httpCookieStore
        cookieStore.getAllCookies { cookies in
            let matchingCookies = cookies.filter { LogoutSites.matches(host: $0.domain, domains: domains) }
            let group = DispatchGroup()
            for cookie in matchingCookies {
                group.enter()
                cookieStore.delete(cookie) { group.leave() }
            }
            group.enter()
            let types = WKWebsiteDataStore.allWebsiteDataTypes()
            store.fetchDataRecords(ofTypes: types) { records in
                let targetRecords = records.filter { record in
                    LogoutSites.matches(host: record.displayName, domains: domains)
                }
                if targetRecords.isEmpty {
                    group.leave()
                } else {
                    store.removeData(ofTypes: types, for: targetRecords) {
                        group.leave()
                    }
                }
            }
            group.notify(queue: .main) {
                self.isBusy = false
                completion()
            }
        }
    }
}
