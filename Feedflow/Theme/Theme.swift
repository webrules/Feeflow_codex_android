import SwiftUI
import UIKit

extension UIColor {
    convenience init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }

        self.init(
            red: CGFloat(r) / 255.0,
            green: CGFloat(g) / 255.0,
            blue: CGFloat(b) / 255.0,
            alpha: CGFloat(a) / 255.0
        )
    }
}

extension Color {
    static let forumBackground = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: "0B101B") : UIColor(hex: "F2F2F7") })
    static let forumCard = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: "151C2C") : UIColor.white })
    static let forumAccent = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: "2D62ED") : UIColor.systemBlue })
    static let forumAccentSoft = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: "203A78") : UIColor(hex: "E8F1FF") })
    static let forumTextPrimary = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor.white : UIColor.black })
    static let forumTextSecondary = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: "949BA5") : UIColor.secondaryLabel })
    static let forumInputBackground = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: "1C2436") : UIColor(hex: "E5E5EA") })
    static let forumSeparator = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: "273247") : UIColor(hex: "D7D7DD") })
}

// MARK: - Conditional View Modifier

extension View {
    /// Conditionally applies a view modifier.
    /// Usage: `.if(condition) { view in view.someModifier() }`
    @ViewBuilder
    func `if`<Content: View>(_ condition: Bool, transform: (Self) -> Content) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }
}
