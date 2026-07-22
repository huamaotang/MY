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

struct Fund: Decodable, Identifiable, Hashable {
    var id: String { fundCode }

    let fundCode: String
    let fundName: String
    let inceptionDate: String?
    let fundManager: String?
    let fundType: String?
    let managementCompany: String?
    let netAssetScale: String?
    let scaleDate: String?
    let canBuy: Bool?
    let createdAt: String?
    let updatedAt: String?
}

struct FundNav: Decodable, Hashable {
    let fundCode: String
    let navDate: String
    let unitNav: Decimal?
    let accumulatedNav: Decimal?
    let dailyGrowthRate: Decimal?
}

struct FundHolding: Decodable, Hashable {
    let fundCode: String
    let reportPeriod: String?
    let reportDate: String
    let rankNo: Int?
    let stockCode: String
    let stockName: String?
    let netValueRatio: Decimal?
    let holdingShares10k: Decimal?
    let holdingMarketValue10k: Decimal?
}

struct FundFeature: Decodable, Hashable {
    let fundCode: String
    let periodLabel: String
    let cutoffDate: String
    let standardDeviation: Decimal?
    let sharpeRatio: Decimal?
}

struct FundRating: Decodable, Hashable {
    let fundCode: String
    let ratingDate: String
    let zhaoshangRating: Int?
    let shanghaiRating3y: Int?
    let shanghaiRating5y: Int?
    let jianRating: Int?
    let morningStarRating: Int?
}

struct FundPerformance: Decodable, Hashable {
    let fundCode: String
    let navDate: String
    let weeklyReturnRate: Decimal?
    let monthlyReturnRate: Decimal?
    let threeMonthReturnRate: Decimal?
    let sixMonthReturnRate: Decimal?
    let oneYearReturnRate: Decimal?
    let twoYearReturnRate: Decimal?
    let threeYearReturnRate: Decimal?
    let yearToDateReturnRate: Decimal?
    let sinceInceptionReturnRate: Decimal?
    let customStartDate: String
    let customEndDate: String
    let customReturnRate: Decimal?
    let originalFeeRate: Decimal?
    let discountedFeeRate: Decimal?
    let cashManagementFeeRate: Decimal?
}

struct FundDetail: Decodable {
    let fund: Fund
    let latestNav: FundNav?
    let latestPerformance: FundPerformance?
    let latestHoldings: [FundHolding]
    let features: [FundFeature]
    let ratings: [FundRating]

    enum CodingKeys: String, CodingKey {
        case fund
        case latestNav
        case latestPerformance
        case latestHoldings
        case features
        case ratings
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        fund = try container.decode(Fund.self, forKey: .fund)
        latestNav = try container.decodeIfPresent(FundNav.self, forKey: .latestNav)
        latestPerformance = try container.decodeIfPresent(FundPerformance.self, forKey: .latestPerformance)
        latestHoldings = try container.decodeIfPresent([FundHolding].self, forKey: .latestHoldings) ?? []
        features = try container.decodeIfPresent([FundFeature].self, forKey: .features) ?? []
        ratings = try container.decodeIfPresent([FundRating].self, forKey: .ratings) ?? []
    }
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
