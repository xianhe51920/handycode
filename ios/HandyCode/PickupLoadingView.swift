import UIKit

final class PickupLoadingView: UIView {
    private let card1 = UIView()
    private let card2 = UIView()
    private let dotsLayer = CAReplicatorLayer()
    private let dotLayer = CALayer()
    private let statusLabel = UILabel()
    private var dark = false
    private let singleCard: Bool

    init(singleCard: Bool = false) {
        self.singleCard = singleCard
        super.init(frame: .zero)
        setup()
    }

    required init?(coder: NSCoder) { nil }

    private func setup() {
        isUserInteractionEnabled = true
        for card in [card1, card2] {
            card.layer.cornerRadius = 16
            card.layer.borderWidth = 1
            addSubview(card)
        }
        card2.isHidden = singleCard

        dotLayer.bounds = CGRect(x: 0, y: 0, width: 7, height: 7)
        dotLayer.cornerRadius = 3.5
        dotsLayer.instanceCount = 3
        dotsLayer.instanceTransform = CATransform3DMakeTranslation(14, 0, 0)
        dotsLayer.instanceDelay = 0.16
        dotsLayer.addSublayer(dotLayer)
        layer.addSublayer(dotsLayer)

        statusLabel.text = "正在准备取件信息"
        statusLabel.font = .systemFont(ofSize: 13)
        statusLabel.textAlignment = .center
        addSubview(statusLabel)
        setDark(false)
    }

    func setDark(_ dark: Bool) {
        self.dark = dark
        backgroundColor = AppTheme.bg(dark)
        for card in [card1, card2] {
            card.backgroundColor = AppTheme.surface(dark)
            card.layer.borderColor = AppTheme.border(dark).cgColor
        }
        dotLayer.backgroundColor = AppTheme.muted(dark).cgColor
        statusLabel.textColor = AppTheme.muted(dark)
    }

    func setAnimating(_ animating: Bool) {
        dotLayer.removeAnimation(forKey: "pulse")
        guard animating else { return }
        let anim = CABasicAnimation(keyPath: "opacity")
        anim.fromValue = 0.25
        anim.toValue = 1.0
        anim.duration = 0.55
        anim.autoreverses = true
        anim.repeatCount = .infinity
        dotLayer.add(anim, forKey: "pulse")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        let h = bounds.height
        guard w > 40, h > 80 else { return }
        let cardW = w - 20
        let identityH = max(220, min(280, 320 - 0.2 * w))
        card1.frame = CGRect(x: 10, y: 12, width: cardW, height: singleCard ? min(identityH + 40, h - 24) : identityH)
        if !singleCard {
            let y2 = card1.frame.maxY + 14
            card2.frame = CGRect(x: 10, y: y2, width: cardW, height: max(180, min(260, h - y2 - 24)))
        }
        let centerRef = card1.frame
        dotsLayer.bounds = CGRect(x: 0, y: 0, width: 35, height: 8)
        dotsLayer.position = CGPoint(x: centerRef.midX, y: centerRef.midY - 8)
        dotLayer.position = CGPoint(x: 3.5, y: 4)
        statusLabel.frame = CGRect(x: 24, y: centerRef.midY + 10, width: w - 48, height: 20)
    }
}
