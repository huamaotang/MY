import Foundation

final class ApiClient {
    var baseURL: String
    var token: String?

    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    init(baseURL: String, token: String? = nil, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.token = token
        self.session = session
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
    }

    func login(username: String, password: String) async throws -> LoginResult {
        let body = LoginRequest(username: username, password: password)
        return try await request(path: "/auth/login", method: "POST", body: body)
    }

    func listCustomers(current: Int, size: Int, keyword: String?) async throws -> PageResult<Customer> {
        var queryItems = [
            URLQueryItem(name: "current", value: String(current)),
            URLQueryItem(name: "size", value: String(size))
        ]
        if let keyword, !keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            queryItems.append(URLQueryItem(name: "keyword", value: keyword))
        }
        return try await request(path: "/customers", queryItems: queryItems)
    }

    func customerDetail(id: Int) async throws -> Customer {
        try await request(path: "/customers/\(id)")
    }

    func listFunds(current: Int, size: Int, keyword: String?) async throws -> PageResult<Fund> {
        var queryItems = [
            URLQueryItem(name: "current", value: String(current)),
            URLQueryItem(name: "size", value: String(size))
        ]
        if let keyword, !keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            queryItems.append(URLQueryItem(name: "keyword", value: keyword))
        }
        return try await request(path: "/funds", queryItems: queryItems)
    }

    func fundDetail(fundCode: String) async throws -> FundDetail {
        try await request(path: "/funds/\(fundCode)")
    }

    func listFundNavs(fundCode: String, current: Int, size: Int) async throws -> PageResult<FundNav> {
        let queryItems = [
            URLQueryItem(name: "current", value: String(current)),
            URLQueryItem(name: "size", value: String(size))
        ]
        return try await request(path: "/funds/\(fundCode)/navs", queryItems: queryItems)
    }

    private func request<T: Decodable>(
        path: String,
        method: String = "GET",
        queryItems: [URLQueryItem] = []
    ) async throws -> T {
        try await performRequest(path: path, method: method, queryItems: queryItems, httpBody: nil)
    }

    private func request<T: Decodable, B: Encodable>(
        path: String,
        method: String = "GET",
        queryItems: [URLQueryItem] = [],
        body: B
    ) async throws -> T {
        try await performRequest(path: path, method: method, queryItems: queryItems, httpBody: try encoder.encode(body))
    }

    private func performRequest<T: Decodable>(
        path: String,
        method: String,
        queryItems: [URLQueryItem],
        httpBody: Data?
    ) async throws -> T {
        var components = try components(path: path)
        if !queryItems.isEmpty {
            components.queryItems = queryItems
        }
        guard let url = components.url else {
            throw ApiError.invalidBaseURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("ios", forHTTPHeaderField: "X-Client-Source")
        request.setValue("CrmMobile/iOS", forHTTPHeaderField: "User-Agent")
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = httpBody

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse
        }
        let apiResponse = try decoder.decode(ApiResponse<T>.self, from: data)
        guard (200..<300).contains(httpResponse.statusCode), apiResponse.code == 0 else {
            throw ApiError.server(apiResponse.message)
        }
        guard let payload = apiResponse.data else {
            throw ApiError.missingData
        }
        return payload
    }

    private func components(path: String) throws -> URLComponents {
        let normalizedBase = baseURL.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !normalizedBase.isEmpty, var components = URLComponents(string: normalizedBase + path) else {
            throw ApiError.invalidBaseURL
        }
        if components.scheme == nil {
            components = URLComponents(string: "http://" + normalizedBase + path) ?? components
        }
        return components
    }
}

private struct LoginRequest: Encodable {
    let username: String
    let password: String
}
