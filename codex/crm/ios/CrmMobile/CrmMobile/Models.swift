import Foundation

struct ApiResponse<T: Decodable>: Decodable {
    let code: Int
    let message: String
    let data: T?
}

struct EmptyResponse: Decodable {}

struct PageResult<T: Decodable>: Decodable {
    let records: [T]
    let total: Int
    let size: Int
    let current: Int
}

struct LoginResult: Decodable {
    let token: String
    let username: String
    let permissions: [String]
}

struct Customer: Decodable, Identifiable, Hashable {
    let id: Int?
    let customerName: String
    let customerType: String?
    let industry: String?
    let source: String?
    let level: String?
    let status: String?
    let ownerUserId: Int?
    let phone: String?
    let email: String?
    let province: String?
    let city: String?
    let address: String?
    let remark: String?
    let createdAt: String?
    let updatedAt: String?
}

enum ApiError: LocalizedError {
    case invalidBaseURL
    case invalidResponse
    case server(String)
    case missingData

    var errorDescription: String? {
        switch self {
        case .invalidBaseURL:
            return "服务器地址无效"
        case .invalidResponse:
            return "服务器响应无效"
        case .server(let message):
            return message
        case .missingData:
            return "服务器未返回数据"
        }
    }
}
