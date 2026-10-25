import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Edge to edge like Android: the app's shells pad themselves out of the notch, the home indicator and the
        // keyboard
        ComposeView()
                .ignoresSafeArea()
    }
}



