import SwiftUI

struct FullScreenImageView: View {
    let imageURL: String
    @Binding var isPresented: Bool
    @State private var rotation: Double = 0
    @State private var scale: CGFloat = 1.0

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            AsyncImage(url: URL(string: imageURL)) { phase in
                switch phase {
                case .empty:
                    ProgressView()
                        .tint(.white)
                case .success(let image):
                    image.resizable()
                         .aspectRatio(contentMode: .fit)
                         .rotationEffect(.degrees(rotation))
                         .scaleEffect(scale)
                         .gesture(
                            MagnificationGesture()
                                .onChanged { value in
                                    scale = value.magnitude
                                }
                         )
                case .failure:
                    Text("failed_load_image".localized())
                        .foregroundColor(.white)
                @unknown default:
                    EmptyView()
                }
            }

            // UI Controls overlay
            VStack {
                HStack {
                    Button(action: { isPresented = false }) {
                        FeedflowSymbol(name: FeedflowIcon.close, size: 30, color: .white)
                            .padding()
                    }
                    Spacer()
                }

                Spacer()

                HStack(spacing: 40) {
                    Button(action: {
                        withAnimation {
                            rotation -= 90
                        }
                    }) {
                        VStack {
                            FeedflowSymbol(name: "rotate.left.fill", size: 24, color: .white)
                            Text("rotate_left".localized())
                                .font(.caption)
                        }
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.black.opacity(0.5))
                        .cornerRadius(10)
                    }

                    Button(action: {
                        withAnimation {
                            rotation += 90
                        }
                    }) {
                        VStack {
                            FeedflowSymbol(name: "rotate.right.fill", size: 24, color: .white)
                            Text("rotate_right".localized())
                                .font(.caption)
                        }
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.black.opacity(0.5))
                        .cornerRadius(10)
                    }
                }
                .padding(.bottom, 40)
            }
        }
    }
}
