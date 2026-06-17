import SwiftUI

enum FeedflowIcon {
    static let add = "plus"
    static let addCircle = "plus.circle.fill"
    static let ai = "sparkles"
    static let back = "chevron.left"
    static let bookmark = "bookmark"
    static let bookmarkFill = "bookmark.fill"
    static let browser = "safari.fill"
    static let close = "xmark.circle.fill"
    static let comments = "bubble.left.and.bubble.right.fill"
    static let communities = "square.grid.2x2.fill"
    static let compose = "square.and.pencil"
    static let feed = "dot.radiowaves.left.and.right"
    static let feedManager = "list.bullet.rectangle.portrait.fill"
    static let forward = "chevron.right"
    static let home = "house.fill"
    static let importFile = "tray.and.arrow.down.fill"
    static let link = "link.circle.fill"
    static let login = "person.fill"
    static let more = "ellipsis.circle.fill"
    static let refresh = "arrow.triangle.2.circlepath"
    static let settings = "slider.horizontal.3"
    static let share = "square.and.arrow.up"
    static let summary = "sparkles.rectangle.stack.fill"
    static let theme = "circle.lefthalf.filled"
    static let trash = "trash.fill"
    static let upload = "paperplane.fill"
    static let web = "globe"
}

struct FeedflowSymbol: View {
    let name: String
    var size: CGFloat = 20
    var color: Color = .forumAccent
    var background: Color? = nil
    var frameSize: CGFloat? = nil
    var shape: IconShape = .roundedRectangle
    
    enum IconShape {
        case circle
        case roundedRectangle
    }
    
    var body: some View {
        Image(systemName: name)
            .symbolRenderingMode(.hierarchical)
            .font(.system(size: size, weight: .semibold))
            .foregroundStyle(color)
            .frame(width: frameSize, height: frameSize)
            .background(backgroundView)
    }
    
    @ViewBuilder
    private var backgroundView: some View {
        if let background, let frameSize {
            switch shape {
            case .circle:
                Circle().fill(background)
            case .roundedRectangle:
                RoundedRectangle(cornerRadius: max(8, frameSize * 0.22), style: .continuous)
                    .fill(background)
            }
        }
    }
}

struct ToolbarSymbolButton: View {
    let name: String
    var isActive: Bool = true
    var activeColor: Color = .forumAccent
    var inactiveColor: Color = .forumTextSecondary.opacity(0.45)
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            FeedflowSymbol(
                name: name,
                size: 18,
                color: isActive ? activeColor : inactiveColor,
                background: (isActive ? activeColor : inactiveColor).opacity(0.12),
                frameSize: 34,
                shape: .circle
            )
        }
        .disabled(!isActive)
    }
}

struct SiteIcon: View {
    let service: ForumService
    var size: CGFloat = 44
    
    var body: some View {
        ZStack {
            Circle()
                .fill(Color.forumAccent)
                .overlay(
                    Circle()
                        .stroke(Color.white.opacity(0.24), lineWidth: 1)
                        .padding(1)
                )
                .overlay(
                    Circle()
                        .stroke(Color.forumAccent.opacity(0.28), lineWidth: 1)
                )
            
            if service.logo.hasPrefix("http") {
                AsyncImage(url: URL(string: service.logo)) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable()
                            .aspectRatio(contentMode: .fit)
                            .clipShape(Circle())
                    default:
                        Image(systemName: fallbackSymbol)
                            .symbolRenderingMode(.hierarchical)
                            .foregroundStyle(Color.white)
                    }
                }
                .padding(size * 0.24)
            } else {
                Image(systemName: service.logo)
                    .symbolRenderingMode(.monochrome)
                    .font(.system(size: size * 0.43, weight: .bold))
                    .foregroundStyle(Color.white)
            }
        }
        .frame(width: size, height: size)
        .shadow(color: Color.forumAccent.opacity(0.18), radius: 10, x: 0, y: 5)
    }
    
    private var fallbackSymbol: String {
        service.id == "linux_do" ? "terminal.fill" : FeedflowIcon.web
    }
}
