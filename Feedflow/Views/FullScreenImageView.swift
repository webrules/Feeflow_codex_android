import SwiftUI

struct FullScreenImageView: View {
    let imageURL: String
    @Binding var isPresented: Bool
    @State private var rotation: Double = 0

    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    private let minScale: CGFloat = 1.0
    private let maxScale: CGFloat = 5.0

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
                         .offset(offset)
                         .gesture(panGesture)
                         .gesture(zoomGesture)
                         .onTapGesture(count: 2) {
                             withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                 if scale > minScale {
                                     resetZoom()
                                 } else {
                                     scale = 2.5
                                     lastScale = 2.5
                                 }
                             }
                         }
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

    private var zoomGesture: some Gesture {
        MagnificationGesture()
            .onChanged { value in
                scale = min(max(lastScale * value, minScale), maxScale)
            }
            .onEnded { _ in
                lastScale = scale
                if scale <= minScale {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                        resetZoom()
                    }
                }
            }
    }

    private var panGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                guard scale > minScale else { return }
                offset = CGSize(
                    width: lastOffset.width + value.translation.width,
                    height: lastOffset.height + value.translation.height
                )
            }
            .onEnded { _ in
                lastOffset = offset
            }
    }

    private func resetZoom() {
        scale = minScale
        lastScale = minScale
        offset = .zero
        lastOffset = .zero
    }
}
