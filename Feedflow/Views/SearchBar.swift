import SwiftUI

struct SearchBar: View {
    @Binding var query: String
    let isSearching: Bool
    let placeholder: String
    let onSubmit: () -> Void
    let onClear: () -> Void

    @FocusState private var isFocused: Bool

    var body: some View {
        HStack(spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.forumTextSecondary)

                TextField(placeholder, text: $query)
                    .font(.system(size: 15))
                    .foregroundColor(.forumTextPrimary)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .focused($isFocused)
                    .onSubmit(onSubmit)
                    .onChange(of: query) { _ in
                        onSubmit()
                    }

                if isSearching {
                    ProgressView()
                        .scaleEffect(0.7)
                        .frame(width: 16, height: 16)
                } else if !query.isEmpty {
                    Button(action: onClear) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.forumTextSecondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(Color.forumCard)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(Color.forumSeparator.opacity(0.4), lineWidth: 1)
            )

            if isFocused {
                Button("取消") {
                    isFocused = false
                    onClear()
                }
                .font(.system(size: 15))
                .foregroundColor(.accentColor)
                .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: isFocused)
    }
}
