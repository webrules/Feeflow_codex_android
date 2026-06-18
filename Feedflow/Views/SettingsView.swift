import SwiftUI

struct SettingsView: View {
    @State private var geminiKey: String = ""
    @State private var backgroundPrefetchEnabled = UserDefaults.standard.bool(forKey: ThreadListViewModel.backgroundPrefetchEnabledKey)
    @Environment(\.dismiss) var dismiss
    @ObservedObject var localizationManager = LocalizationManager.shared

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("gemini_api_key".localized())) {
                    SecureField("enter_api_key".localized(), text: $geminiKey)
                        .textContentType(.password)

                    Text("api_key_note".localized())
                        .font(.caption)
                        .foregroundColor(.forumTextSecondary)
                }

                Section(header: Text("reading".localized())) {
                    Toggle("background_prefetch".localized(), isOn: $backgroundPrefetchEnabled)

                    Text("background_prefetch_note".localized())
                        .font(.caption)
                        .foregroundColor(.forumTextSecondary)
                }
            }
            .navigationTitle("settings".localized())
            .toolbar {
                ToolbarItem(placement: .bottomBar) {
                    HStack {
                        Button("cancel".localized()) {
                            dismiss()
                        }

                        Spacer()

                        Button("save".localized()) {
                            saveKey()
                            dismiss()
                        }
                    }
                }
            }
            .toolbarBackground(Color.forumBackground, for: .bottomBar)
            .toolbarBackground(.visible, for: .bottomBar)
            .onAppear {
                loadKey()
            }
        }
    }

    private func loadKey() {
        if let key = DatabaseManager.shared.getEncryptedSetting(key: "gemini_api_key") {
            geminiKey = key
        }
    }

    private func saveKey() {
        DatabaseManager.shared.saveEncryptedSetting(key: "gemini_api_key", value: geminiKey)
        UserDefaults.standard.set(backgroundPrefetchEnabled, forKey: ThreadListViewModel.backgroundPrefetchEnabledKey)
    }
}
