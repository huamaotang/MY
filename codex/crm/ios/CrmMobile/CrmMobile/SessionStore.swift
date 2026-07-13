import Foundation

@MainActor
final class SessionStore: ObservableObject {
    @Published var isAuthenticated = false
    @Published var username = ""
    @Published var baseURL = UserDefaults.standard.string(forKey: Constants.baseURLKey) ?? Constants.defaultBaseURL
    @Published var errorMessage: String?

    let apiClient: ApiClient
    private let keychain = KeychainStore(service: Constants.keychainService)

    init() {
        self.apiClient = ApiClient(baseURL: baseURL)
    }

    func restore() {
        apiClient.baseURL = baseURL
        if let token = keychain.read(account: Constants.tokenAccount), !token.isEmpty {
            apiClient.token = token
            isAuthenticated = true
            username = UserDefaults.standard.string(forKey: Constants.usernameKey) ?? ""
        }
    }

    func login(server: String, username: String, password: String) async {
        errorMessage = nil
        let normalizedServer = server.trimmingCharacters(in: .whitespacesAndNewlines)
        apiClient.baseURL = normalizedServer
        do {
            let result = try await apiClient.login(username: username, password: password)
            keychain.save(result.token, account: Constants.tokenAccount)
            UserDefaults.standard.set(normalizedServer, forKey: Constants.baseURLKey)
            UserDefaults.standard.set(result.username, forKey: Constants.usernameKey)
            self.baseURL = normalizedServer
            self.username = result.username
            apiClient.token = result.token
            isAuthenticated = true
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func logout() {
        keychain.delete(account: Constants.tokenAccount)
        apiClient.token = nil
        isAuthenticated = false
        username = ""
    }
}

private enum Constants {
    static let defaultBaseURL = "http://192.168.1.100:8780/api"
    static let baseURLKey = "crm.mobile.baseURL"
    static let usernameKey = "crm.mobile.username"
    static let keychainService = "com.example.crm.mobile"
    static let tokenAccount = "crm_token"
}
