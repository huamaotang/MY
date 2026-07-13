import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore

    @State private var server = ""
    @State private var username = "admin"
    @State private var password = "admin123"
    @State private var isLoading = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("CRM")
                        .font(.largeTitle.bold())
                    Text("客户信息移动端")
                        .font(.headline)
                        .foregroundStyle(.secondary)
                }

                VStack(spacing: 14) {
                    TextField("服务器地址", text: $server)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                        .textContentType(.URL)
                        .textFieldStyle(.roundedBorder)

                    TextField("用户名", text: $username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.username)
                        .textFieldStyle(.roundedBorder)

                    SecureField("密码", text: $password)
                        .textContentType(.password)
                        .textFieldStyle(.roundedBorder)
                }

                if let errorMessage = session.errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Button {
                    Task {
                        await submit()
                    }
                } label: {
                    HStack {
                        if isLoading {
                            ProgressView()
                                .tint(.white)
                        }
                        Text(isLoading ? "登录中" : "登录")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(isLoading || username.isEmpty || password.isEmpty || server.isEmpty)

                Spacer()

                Text("真机访问时请填写电脑或服务器的局域网 IP，例如 http://192.168.1.10:8780/api。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding(24)
            .navigationTitle("登录")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                server = session.baseURL
            }
        }
    }

    private func submit() async {
        isLoading = true
        defer { isLoading = false }
        await session.login(server: server, username: username, password: password)
    }
}
