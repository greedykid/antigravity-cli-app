import SwiftUI

@main
struct CodexRemoteApp: App {
    @StateObject private var state = AppState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(state)
        }
    }
}
