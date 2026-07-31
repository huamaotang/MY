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

    func listFunds(current: Int, size: Int, keyword: String?, fundType: String? = nil,
                   canBuy: Bool? = nil, sortField: String? = nil,
                   sortOrder: String? = nil) async throws -> PageResult<Fund> {
        var queryItems = [
            URLQueryItem(name: "current", value: String(current)),
            URLQueryItem(name: "size", value: String(size))
        ]
        if let keyword, !keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            queryItems.append(URLQueryItem(name: "keyword", value: keyword))
        }
        if let fundType, !fundType.isEmpty {
            queryItems.append(URLQueryItem(name: "fundType", value: fundType))
        }
        if let canBuy {
            queryItems.append(URLQueryItem(name: "canBuy", value: String(canBuy)))
        }
        if let sortField { queryItems.append(URLQueryItem(name: "sortField", value: sortField)) }
        if let sortOrder { queryItems.append(URLQueryItem(name: "sortOrder", value: sortOrder)) }
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

    func listFinanceNews(current: Int, size: Int, categoryTag: Int? = nil) async throws -> PageResult<FinanceNews> {
        var queryItems = [
            URLQueryItem(name: "current", value: String(current)),
            URLQueryItem(name: "size", value: String(size))
        ]
        if let categoryTag { queryItems.append(URLQueryItem(name: "categoryTag", value: String(categoryTag))) }
        return try await request(path: "/news", queryItems: queryItems)
    }

    func listPortfolioHoldings(current: Int, size: Int, keyword: String? = nil,
                               scope: String = "raw", sortField: String = "holdingAmount",
                               sortOrder: String = "desc") async throws -> PageResult<UserFundHolding> {
        var queryItems = [
            URLQueryItem(name: "current", value: String(current)),
            URLQueryItem(name: "size", value: String(size)),
            URLQueryItem(name: "scope", value: scope),
            URLQueryItem(name: "sortField", value: sortField),
            URLQueryItem(name: "sortOrder", value: sortOrder)
        ]
        if let keyword, !keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            queryItems.append(URLQueryItem(name: "keyword", value: keyword))
        }
        return try await request(path: "/portfolio/holdings", queryItems: queryItems)
    }

    func portfolioOverview() async throws -> PortfolioOverview {
        try await request(path: "/portfolio/overview")
    }

    func listPortfolioImports(current: Int, size: Int) async throws -> PageResult<PortfolioHoldingBatch> {
        try await request(path: "/portfolio/imports", queryItems: [
            URLQueryItem(name: "current", value: String(current)),
            URLQueryItem(name: "size", value: String(size))
        ])
    }

    func previewPortfolioHoldings(
        images: [Data],
        sourceLabel: String = "alipay",
        importType: String = "holding"
    ) async throws -> PortfolioHoldingImportPreview {
        let imageParts = images.enumerated().map { index, data in
            MultipartBody.Part(name: "images", filename: "screenshot-\(index + 1).jpg", mimeType: "image/jpeg", data: data)
        }
        return try await performMultipartRequest(
            path: "/portfolio/imports/ocr?sourceLabel=\(sourceLabel)&importType=\(importType)",
            multipart: MultipartBody(parts: imageParts)
        )
    }

    func portfolioHoldingImport(importId: Int) async throws -> PortfolioHoldingImportPreview {
        try await request(path: "/portfolio/imports/\(importId)")
    }

    func confirmPortfolioHoldingImport(
        importId: Int,
        request: PortfolioHoldingConfirmRequest
    ) async throws -> PortfolioHoldingConfirmResponse {
        try await performRequest(
            path: "/portfolio/imports/\(importId)/confirm",
            method: "POST",
            queryItems: [],
            httpBody: try encoder.encode(request)
        )
    }

    func listStocks(current: Int, size: Int, keyword: String = "", sortField: String? = nil, sortOrder: String? = nil) async throws -> PageResult<StockQuote> {
        var items = [URLQueryItem(name: "current", value: String(current)), URLQueryItem(name: "size", value: String(size))]
        if !keyword.isEmpty { items.append(URLQueryItem(name: "keyword", value: keyword)) }
        if let sortField { items.append(URLQueryItem(name: "sortField", value: sortField)) }
        if let sortOrder { items.append(URLQueryItem(name: "sortOrder", value: sortOrder)) }
        return try await request(path: "/stocks", queryItems: items)
    }

    func stockHistory(stockCode: String, current: Int = 1, size: Int = 100) async throws -> PageResult<StockQuote> {
        try await request(path: "/stocks/\(stockCode)/history", queryItems: [
            URLQueryItem(name: "current", value: String(current)), URLQueryItem(name: "size", value: String(size))
        ])
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

    private func performRequestNoData(path: String, method: String, httpBody: Data?) async throws {
        let components = try components(path: path)
        guard let url = components.url else {
            throw ApiError.invalidBaseURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
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
        let apiResponse = try decoder.decode(ApiResponse<EmptyResponse>.self, from: data)
        guard (200..<300).contains(httpResponse.statusCode), apiResponse.code == 0 else {
            throw ApiError.server(apiResponse.message)
        }
    }

    private func performMultipartRequest<T: Decodable>(path: String, multipart: MultipartBody) async throws -> T {
        let components = try components(path: path)
        guard let url = components.url else {
            throw ApiError.invalidBaseURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("ios", forHTTPHeaderField: "X-Client-Source")
        request.setValue("CrmMobile/iOS", forHTTPHeaderField: "User-Agent")
        request.setValue("multipart/form-data; boundary=\(multipart.boundary)", forHTTPHeaderField: "Content-Type")
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = multipart.body

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

struct PortfolioHoldingConfirmRequest: Encodable {
    let screenshotDate: String?
    let items: [PortfolioHoldingConfirmItemRequest]?
    let tradeMappings: [PortfolioTradeMappingRequest]?
}

struct PortfolioTradeMappingRequest: Encodable {
    let groupKey: String
    let fundCode: String
}

struct PortfolioHoldingConfirmItemRequest: Encodable {
    let rowNo: Int
    let fundCode: String
    let fundName: String
    let holdingAmount: Decimal?
    let holdingProfit: Decimal?
    let holdingReturnRate: Decimal?
    let holdingCost: Decimal?
    let yesterdayProfit: Decimal?
    let todayProfit: Decimal?
    let holdingShares: Decimal?
    let costNav: Decimal?
    let screenshotDate: String?
    let confidence: Decimal?
    let rawTexts: [String]
}

private struct MultipartBody {
    struct Part {
        let name: String
        let filename: String
        let mimeType: String
        let data: Data
    }

    let boundary = "Boundary-\(UUID().uuidString)"
    let body: Data

    init(parts: [Part]) {
        var data = Data()
        for part in parts {
            data.append("--\(boundary)\r\n")
            data.append("Content-Disposition: form-data; name=\"\(part.name)\"; filename=\"\(part.filename)\"\r\n")
            data.append("Content-Type: \(part.mimeType)\r\n\r\n")
            data.append(part.data)
            data.append("\r\n")
        }
        data.append("--\(boundary)--\r\n")
        self.body = data
    }
}

private extension Data {
    mutating func append(_ string: String) {
        if let value = string.data(using: .utf8) {
            append(value)
        }
    }
}
