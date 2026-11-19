import UIKit

final class LiquidGlassBar: UIView {
    private let pill = UIVisualEffectView(effect: UIBlurEffect(style: .systemChromeMaterialDark))
    private let borderLayer = CALayer()
    private let indicator = UIView()
    private var buttons: [UIButton] = []
    private var titles: [String] = []
    private var position: CGFloat = 0
    private var dark = false
    private var dragStart: CGFloat = 0
    private var dragStartPos: CGFloat = 0

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
        pill.layer.cornerRadius = 26
        pill.clipsToBounds = true
        borderLayer.cornerRadius = 26
        borderLayer.borderWidth = 1
        pill.layer.addSublayer(borderLayer)
        addSubview(pill)

        indicator.layer.cornerRadius = 22
        indicator.layer.borderWidth = 1
        pill.contentView.addSubview(indicator)

        for (idx, title) in titles.enumerated() {
            let btn = UIButton(type: .system)
            btn.setTitle(title, for: .normal)
            btn.titleLabel?.font = .systemFont(ofSize: 14, weight: .medium)
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
        pill.effect = UIBlurEffect(style: dark ? .systemChromeMaterialDark : .systemChromeMaterialLight)
        borderLayer.borderColor = UIColor(argb: dark ? 0x30FFFFFF : 0x18000000).cgColor
        indicator.backgroundColor = UIColor(argb: dark ? 0x26FFFFFF : 0xFFFFFFFF)
        indicator.layer.borderColor = UIColor(argb: dark ? 0x38FFFFFF : 0x20000000).cgColor
        updateColors()
    }

    func setPosition(_ position: CGFloat, selected: Int) {
        self.position = position
        updateIndicator()
        for (i, btn) in buttons.enumerated() {
            let active = i == selected
            btn.titleLabel?.font = .systemFont(ofSize: 14, weight: active ? .semibold : .regular)
            btn.isSelected = active
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
            dragStartPos = position
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
        let x = 4 + position * slotW
        indicator.frame = CGRect(x: x, y: 4, width: max(40, slotW), height: max(36, pill.bounds.height - 8))
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = min(bounds.width - 32, 380)
        pill.frame = CGRect(x: (bounds.width - w) / 2, y: 6, width: w, height: 52)
        borderLayer.frame = pill.bounds
        let count = max(1, buttons.count)
        let slotW = (pill.bounds.width - 8) / CGFloat(count)
        for (i, btn) in buttons.enumerated() {
            btn.frame = CGRect(x: 4 + CGFloat(i) * slotW, y: 4, width: slotW, height: 44)
        }
        updateIndicator()
    }
}
