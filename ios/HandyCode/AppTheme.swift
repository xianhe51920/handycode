import UIKit

enum AppThemeMode: Int {
    case light = 1
    case dark = 2
}

enum AppTheme {
    static let accent = UIColor(argb: 0xFFEB6238)

    static func systemDefault(for traitCollection: UITraitCollection = UIScreen.main.traitCollection) -> AppThemeMode {
        traitCollection.userInterfaceStyle == .dark ? .dark : .light
    }

    static func bg(_ dark: Bool) -> UIColor { UIColor(argb: dark ? 0xFF0F1218 : 0xFFF7F8FB) }
    static func surface(_ dark: Bool) -> UIColor { UIColor(argb: dark ? 0xFF181C24 : 0xFFFFFFFF) }
    static func elevated(_ dark: Bool) -> UIColor { UIColor(argb: dark ? 0xFF232936 : 0xFFEDF0F5) }
    static func ink(_ dark: Bool) -> UIColor { UIColor(argb: dark ? 0xFFECEFF4 : 0xFF242B37) }
    static func muted(_ dark: Bool) -> UIColor { UIColor(argb: dark ? 0xFF949EB0 : 0xFF697386) }
    static func border(_ dark: Bool) -> UIColor { UIColor(argb: dark ? 0xFF262C36 : 0xFFE9ECF0) }
    static func divider(_ dark: Bool) -> UIColor { UIColor(argb: dark ? 0xFF232833 : 0xFFF0F2F5) }
    static func accentText(_ dark: Bool) -> UIColor { UIColor(argb: dark ? 0xFFFF8E6B : 0xFFBD451F) }
}

extension UIColor {
    convenience init(argb: UInt32) {
        let a = CGFloat((argb >> 24) & 0xFF) / 255.0
        let r = CGFloat((argb >> 16) & 0xFF) / 255.0
        let g = CGFloat((argb >> 8) & 0xFF) / 255.0
        let b = CGFloat(argb & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b, alpha: a)
    }
}
