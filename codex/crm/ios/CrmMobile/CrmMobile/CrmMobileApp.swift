import SwiftUI

@main
struct CrmMobileApp: App {
    @StateObject private var session = SessionStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .task {
                    session.restore()
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        Group {
            if session.isAuthenticated {
                CustomerListView()
            } else {
                LoginView()
            }
        }
    }
}
