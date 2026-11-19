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
    private let track = UIView()
    private let thumb = UIView()
    private let glyph = UIImageView()
    private var dark = false
    var onToggle: ((Bool) -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        accessibilityIdentifier = "theme_toggle"
        track.isUserInteractionEnabled = false
        track.layer.cornerRadius = 15
        track.layer.borderWidth = 1
        addSubview(track)

        thumb.isUserInteractionEnabled = false
        thumb.layer.cornerRadius = 12
        thumb.layer.shadowColor = UIColor.black.cgColor
        thumb.layer.shadowOpacity = 0.16
        thumb.layer.shadowRadius = 4
        thumb.layer.shadowOffset = CGSize(width: 0, height: 1)
        track.addSubview(thumb)

        glyph.contentMode = .scaleAspectFit
        thumb.addSubview(glyph)

        addTarget(self, action: #selector(handleTap), for: .touchUpInside)
        setDark(false, animated: false)
    }

    required init?(coder: NSCoder) { nil }

    func setDark(_ dark: Bool, animated: Bool) {
        self.dark = dark
        accessibilityLabel = dark ? "切换外观模式，当前为深色" : "切换外观模式，当前为浅色"
        let apply = {
            self.track.backgroundColor = dark ? UIColor(argb: 0xFF141822) : UIColor(argb: 0xFFE6EAF2)
            self.track.layer.borderColor = AppTheme.border(dark).cgColor
            self.thumb.backgroundColor = dark ? UIColor(argb: 0xFF283246) : .white
            let symbolName = dark ? "moon.fill" : "sun.max.fill"
            self.glyph.image = UIImage(systemName: symbolName)?.withRenderingMode(.alwaysTemplate)
            self.glyph.tintColor = dark ? UIColor(argb: 0xFF9EC5FF) : UIColor(argb: 0xFFE68A1E)
            self.layoutThumb()
        }
        if animated {
            UIView.animate(withDuration: 0.22, delay: 0, options: [.curveEaseInOut], animations: apply)
        } else {
            apply()
        }
    }

    @objc private func handleTap() {
        onToggle?(!dark)
    }

    private func layoutThumb() {
        let h: CGFloat = 30
        let w: CGFloat = 56
        track.frame = CGRect(x: (bounds.width - w) / 2, y: (bounds.height - h) / 2, width: w, height: h)
        let x: CGFloat = dark ? w - 27 : 3
        thumb.frame = CGRect(x: x, y: 3, width: 24, height: 24)
        glyph.frame = CGRect(x: 5, y: 5, width: 14, height: 14)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layoutThumb()
    }
}
