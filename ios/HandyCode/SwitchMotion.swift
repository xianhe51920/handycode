import Foundation

final class SwitchMotion {
    private let maxPage: Int
    private(set) var position: CGFloat = 0
    private(set) var selected: Int = 0

    init(initial: Int, pageCount: Int = 4) {
        self.maxPage = max(1, pageCount - 1)
        commit(initial)
    }

    var pageCount: Int { maxPage + 1 }

    func setPosition(_ value: CGFloat) {
        position = max(0, min(CGFloat(maxPage), value))
    }

    func drag(start: CGFloat, delta: CGFloat, width: CGFloat) {
        guard width > 0 else { setPosition(start); return }
        let raw = start - delta / width
        let minPos = max(0, CGFloat(selected - 1))
        let maxPos = min(CGFloat(maxPage), CGFloat(selected + 1))
        setPosition(max(minPos, min(maxPos, raw)))
    }

    func scrub(start: CGFloat, delta: CGFloat, stepWidth: CGFloat) {
        setPosition(stepWidth > 0 ? start + delta / stepWidth : start)
    }

    func target(cancelled: Bool) -> Int {
        if cancelled || abs(position - CGFloat(selected)) == 0.5 { return selected }
        return max(0, min(maxPage, Int(round(position))))
    }

    func target(cancelled: Bool, velocityPt: CGFloat, distancePt: CGFloat) -> Int {
        if cancelled { return selected }
        if abs(velocityPt) >= 600 && abs(distancePt) >= 24 {
            return velocityPt < 0
                ? min(maxPage, Int(floor(position)) + 1)
                : max(0, Int(ceil(position)) - 1)
        }
        return target(cancelled: false)
    }

    func commit(_ page: Int) {
        selected = max(0, min(maxPage, page))
        position = CGFloat(selected)
    }

    func offset(page: Int, extent: CGFloat) -> CGFloat {
        (CGFloat(page) - position) * extent
    }

    static func duration(distance: CGFloat) -> TimeInterval {
        (140.0 + 180.0 * min(1.4, abs(Double(distance)))) / 1000.0
    }
}
