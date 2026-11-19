import UIKit

final class LiquidGlassBar: UIView {
    private let shadowHolder = UIView()
    private let pill = UIVisualEffectView(effect: UIBlurEffect(style: .systemChromeMaterialDark))
    private let shellTint = CAGradientLayer()
    private let shellSheen = CAGradientLayer()
    private let borderLayer = CALayer()
    private let indicator = UIView()
    private let indicatorFill = CAGradientLayer()
    private let indicatorSheen = CAGradientLayer()
    private var buttons: [UIButton] = []
    private var titles: [String] = []
    private var position: CGFloat = 0
    private var dark = false
    private var dragStart: CGFloat = 0

    var onSelect: ((Int) -> Void)?
    var onScrubBegan: (() -> Void)?
    var onScrubChanged: ((CGFloat) -> Void)?
    var onScrubEnded: ((CGFloat, CGFloat, Bool) -> Void)?

    init(titles: [String]) {
        self.titles = titles
        super.init(frame: .zero)
        setup()
    }

    required init?(coder: NSCoder) { nil }

    private func setup() {
        shadowHolder.backgroundColor = .clear
        shadowHolder.layer.shadowOffset = CGSize(width: 0, height: 5)
        shadowHolder.layer.shadowRadius = 12
        shadowHolder.layer.shadowOpacity = 0.16
        addSubview(shadowHolder)

        pill.layer.cornerRadius = 26
        pill.clipsToBounds = true
        shadowHolder.addSubview(pill)

        pill.contentView.layer.addSublayer(shellTint)
        shellSheen.cornerRadius = 22
        pill.contentView.layer.addSublayer(shellSheen)

        borderLayer.cornerRadius = 26
        borderLayer.borderWidth = 1.15
        pill.layer.addSublayer(borderLayer)

        indicator.isUserInteractionEnabled = false
        indicator.layer.cornerRadius = 22
        indicator.layer.borderWidth = 1.2
        indicator.clipsToBounds = true
        indicator.layer.addSublayer(indicatorFill)
        indicatorSheen.cornerRadius = 18
        indicator.layer.addSublayer(indicatorSheen)
        pill.contentView.addSubview(indicator)

        for (idx, title) in titles.enumerated() {
            let btn = UIButton(type: .custom)
            btn.backgroundColor = .clear
            btn.tintColor = .clear
            btn.setTitle(title, for: .normal)
            btn.titleLabel?.font = .systemFont(ofSize: 13.5, weight: .semibold)
            btn.accessibilityLabel = "切换到" + title
            btn.accessibilityIdentifier = "tab_\(idx)"
            btn.tag = idx
            btn.addTarget(self, action: #selector(tapped(_:)), for: .touchUpInside)
            pill.contentView.addSubview(btn)
            buttons.append(btn)
        }

        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        pill.addGestureRecognizer(pan)
        setDark(false)
    }

    func setDark(_ dark: Bool) {
        self.dark = dark
        pill.effect = UIBlurEffect(style: dark ? .systemUltraThinMaterialDark : .systemUltraThinMaterialLight)
        shadowHolder.layer.shadowColor = (dark ? UIColor.black : UIColor(argb: 0xFF182438)).cgColor
        shadowHolder.layer.shadowOpacity = dark ? 0.36 : 0.12

        shellTint.colors = dark
            ? [UIColor(argb: 0xB8212836).cgColor, UIColor(argb: 0x9E161B24).cgColor, UIColor(argb: 0xAE1C222E).cgColor]
            : [UIColor(argb: 0xC8FFFFFF).cgColor, UIColor(argb: 0xA8F6F8FC).cgColor, UIColor(argb: 0xBAFFFFFF).cgColor]
        shellTint.locations = [0, 0.55, 1]

        shellSheen.colors = dark
            ? [UIColor(argb: 0x26FFFFFF).cgColor, UIColor.clear.cgColor]
            : [UIColor(argb: 0x70FFFFFF).cgColor, UIColor.clear.cgColor]

        borderLayer.borderColor = UIColor(argb: dark ? 0x3AFFFFFF : 0xB8FFFFFF).cgColor

        indicatorFill.colors = dark
            ? [UIColor(argb: 0x36FF784E).cgColor, UIColor(argb: 0x2A2A3446).cgColor, UIColor(argb: 0x42FF784E).cgColor]
            : [UIColor(argb: 0xD8FFF5EF).cgColor, UIColor(argb: 0xC8FFEEE6).cgColor, UIColor(argb: 0xE6FFE2D4).cgColor]
        indicatorFill.startPoint = CGPoint(x: 0.2, y: 0)
        indicatorFill.endPoint = CGPoint(x: 0.8, y: 1)

        indicatorSheen.colors = dark
            ? [UIColor(argb: 0x38FFFFFF).cgColor, UIColor.clear.cgColor]
            : [UIColor(argb: 0xA8FFFFFF).cgColor, UIColor.clear.cgColor]

        indicator.layer.borderColor = UIColor(argb: dark ? 0x66FFFFFF : 0xEEFFFFFF).cgColor
        updateColors()
    }

    func setPosition(_ position: CGFloat, selected: Int) {
        self.position = position
        updateIndicator()
        for (i, btn) in buttons.enumerated() {
            let active = i == selected
            btn.titleLabel?.font = .systemFont(ofSize: 13.5, weight: active ? .bold : .semibold)
        }
        updateColors()
    }

    var slotSpan: CGFloat {
        guard !buttons.isEmpty else { return 80 }
        return (pill.bounds.width - 8) / CGFloat(buttons.count)
    }

    private func updateColors() {
        let activeColor = AppTheme.accentText(dark)
        let normalColor = AppTheme.muted(dark)
        let nearest = Int(round(position))
        for (i, btn) in buttons.enumerated() {
            btn.setTitleColor(i == nearest ? activeColor : normalColor, for: .normal)
        }
    }

    @objc private func tapped(_ sender: UIButton) {
        onSelect?(sender.tag)
    }

    @objc private func handlePan(_ pan: UIPanGestureRecognizer) {
        switch pan.state {
        case .began:
            dragStart = pan.location(in: pill).x
            onScrubBegan?()
        case .changed:
            let delta = pan.location(in: pill).x - dragStart
            onScrubChanged?(delta)
        case .ended, .cancelled:
            let delta = pan.location(in: pill).x - dragStart
            let vx = pan.velocity(in: pill).x
            onScrubEnded?(vx, delta, pan.state == .cancelled)
        default:
            break
        }
    }

    private func updateIndicator() {
        let count = max(1, buttons.count)
        let innerW = pill.bounds.width - 8
        let slotW = innerW / CGFloat(count)
        let frac = abs(position - round(position))
        let stretch = 1 + 0.14 * sin(.pi * min(1, frac * 2))
        let indW = min(innerW, max(40, slotW) * stretch)
        let centerX = 4 + (position + 0.5) * slotW
        let clampedX = min(max(3, centerX - indW / 2), pill.bounds.width - indW - 3)
        let rect = CGRect(x: clampedX, y: 4, width: indW, height: max(36, pill.bounds.height - 8))
        indicator.frame = rect
        indicator.layer.cornerRadius = rect.height / 2
        indicatorFill.frame = indicator.bounds
        indicatorSheen.frame = CGRect(x: 4, y: 1, width: max(0, rect.width - 8), height: rect.height * 0.45)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = min(bounds.width - 24, 420)
        shadowHolder.frame = CGRect(x: (bounds.width - w) / 2, y: 5, width: w, height: 50)
        pill.frame = shadowHolder.bounds
        pill.layer.cornerRadius = pill.bounds.height / 2
        borderLayer.frame = pill.bounds
        borderLayer.cornerRadius = pill.layer.cornerRadius
        shellTint.frame = pill.bounds
        shellSheen.frame = CGRect(x: 2, y: 1, width: max(0, pill.bounds.width - 4), height: pill.bounds.height * 0.48)

        let count = max(1, buttons.count)
        let slotW = (pill.bounds.width - 8) / CGFloat(count)
        for (i, btn) in buttons.enumerated() {
            btn.frame = CGRect(x: 4 + CGFloat(i) * slotW, y: 3, width: slotW, height: pill.bounds.height - 6)
        }
        updateIndicator()
    }
}
