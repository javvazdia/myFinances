# iOS host app

The Kotlin framework target is already configured in `composeApp`.

Because this repository was scaffolded on a non-macOS environment, this folder currently contains instructions instead of a generated Xcode project.

## On macOS

1. Open this repo in IntelliJ IDEA.
2. Let Gradle sync the Kotlin Multiplatform project.
3. Create a thin iOS host app in Xcode.
4. Embed the generated `MyFinancesApp` framework from the shared module.
5. Call `MainViewController()` from your Swift entry point.

Example Swift usage:

```swift
import SwiftUI
import MyFinancesApp

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```
