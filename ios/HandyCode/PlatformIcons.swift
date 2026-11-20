import UIKit

enum PlatformIcons {
    static func icon(for index: Int) -> UIImage? {
        switch index {
        case 0: return loadImage("ic_cainiao_official", ext: "webp") ?? makeBadge(text: "菜", color: UIColor(argb: 0xFF0078FF))
        case 1: return loadImage("ic_taobao", ext: "png") ?? makeBadge(text: "淘", color: UIColor(argb: 0xFFFF5000))
        case 2: return loadImage("ic_pinduoduo", ext: "png") ?? makeBadge(text: "拼", color: UIColor(argb: 0xFFE02E24))
        case 3: return makeBadge(text: "JD", color: UIColor(argb: 0xFFE1251B))
        default: return nil
        }
    }

    static func themeImage(dark: Bool) -> UIImage? {
        let name = dark ? "glass-theme-dark" : "glass-theme-light"
        guard let url = Bundle.main.url(forResource: name, withExtension: "png", subdirectory: "assets")
                ?? Bundle.main.url(forResource: name, withExtension: "png", subdirectory: "WebAssets")
                ?? Bundle.main.url(forResource: name, withExtension: "png") else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    private static func loadImage(_ name: String, ext: String) -> UIImage? {
        guard let url = Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "drawable-nodpi")
                ?? Bundle.main.url(forResource: name, withExtension: ext, subdirectory: "PlatformIcons")
                ?? Bundle.main.url(forResource: name, withExtension: ext) else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    private static func makeBadge(text: String, color: UIColor) -> UIImage {
        let size = CGSize(width: 28, height: 28)
        return UIGraphicsImageRenderer(size: size).image { ctx in
            let rect = CGRect(origin: .zero, size: size)
            color.setFill()
            UIBezierPath(roundedRect: rect, cornerRadius: 6).fill()
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: text.count > 1 ? 13 : 15, weight: .bold),
                .foregroundColor: UIColor.white
            ]
            let strSize = (text as NSString).size(withAttributes: attrs)
            (text as NSString).draw(
                at: CGPoint(x: (size.width - strSize.width) / 2, y: (size.height - strSize.height) / 2),
                withAttributes: attrs
            )
        }
    }

    static func stationIcon(color: UIColor) -> UIImage {
        let size = CGSize(width: 16, height: 16)
        return UIGraphicsImageRenderer(size: size).image { ctx in
            let cg = ctx.cgContext
            cg.setStrokeColor(color.cgColor)
            cg.setLineWidth(1.3)
            cg.setLineCap(.round)
            cg.setLineJoin(.round)
            let base = UIBezierPath()
            base.move(to: CGPoint(x: 2, y: 7))
            base.addLine(to: CGPoint(x: 2, y: 13.5))
            base.addLine(to: CGPoint(x: 14, y: 13.5))
            base.addLine(to: CGPoint(x: 14, y: 7))
            base.stroke()

            let roof = UIBezierPath()
            roof.move(to: CGPoint(x: 1, y: 6))
            roof.addLine(to: CGPoint(x: 3, y: 2.5))
            roof.addLine(to: CGPoint(x: 13, y: 2.5))
            roof.addLine(to: CGPoint(x: 15, y: 6))
            roof.addLine(to: CGPoint(x: 15, y: 7))
            roof.addCurve(to: CGPoint(x: 11.5, y: 7), controlPoint1: CGPoint(x: 13.9, y: 8.1), controlPoint2: CGPoint(x: 12.6, y: 8.1))
            roof.addCurve(to: CGPoint(x: 8, y: 7), controlPoint1: CGPoint(x: 10.4, y: 8.1), controlPoint2: CGPoint(x: 9.1, y: 8.1))
            roof.addCurve(to: CGPoint(x: 4.5, y: 7), controlPoint1: CGPoint(x: 6.9, y: 8.1), controlPoint2: CGPoint(x: 5.6, y: 8.1))
            roof.addCurve(to: CGPoint(x: 1, y: 7), controlPoint1: CGPoint(x: 3.4, y: 8.1), controlPoint2: CGPoint(x: 2.1, y: 8.1))
            roof.close()
            roof.stroke()
        }
    }
}

final class ThemeToggleButton: UIControl {
    private let imageView = UIImageView()
    private var dark = false
    var onToggle: ((Bool) -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isAccessibilityElement = true
        accessibilityTraits = .button
        accessibilityIdentifier = "theme_toggle"

        imageView.contentMode = .scaleAspectFit
        imageView.isUserInteractionEnabled = false
        addSubview(imageView)

        addTarget(self, action: #selector(handleTouchDown), for: .touchDown)
        addTarget(self, action: #selector(handleTouchUp), for: [.touchCancel, .touchDragExit, .touchUpOutside])
        addTarget(self, action: #selector(handleTap), for: .touchUpInside)
        setDark(false, animated: false)
    }

    required init?(coder: NSCoder) { nil }

    func setDark(_ dark: Bool, animated: Bool) {
        self.dark = dark
        accessibilityLabel = dark ? "切换外观模式，当前为深色" : "切换外观模式，当前为浅色"
        let nextImage = PlatformIcons.themeImage(dark: dark)
        if animated {
            UIView.transition(with: imageView, duration: 0.22, options: .transitionCrossDissolve) {
                self.imageView.image = nextImage
            }
        } else {
            imageView.image = nextImage
        }
    }

    @objc private func handleTouchDown() {
        UIView.animate(withDuration: 0.12) {
            self.imageView.transform = CGAffineTransform(scaleX: 0.94, y: 0.94)
        }
    }

    @objc private func handleTouchUp() {
        UIView.animate(withDuration: 0.18, delay: 0, usingSpringWithDamping: 0.7, initialSpringVelocity: 0.5) {
            self.imageView.transform = .identity
        }
    }

    @objc private func handleTap() {
        handleTouchUp()
        onToggle?(!dark)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        imageView.frame = bounds
    }
}
