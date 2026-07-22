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
                MainTabView()
            } else {
                LoginView()
            }
        }
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            CustomerListView()
                .tabItem {
                    Label("客户", systemImage: "person.3")
                }
            ProductListView()
                .tabItem {
                    Label("产品", systemImage: "chart.line.uptrend.xyaxis")
                }
            MineView()
                .tabItem {
                    Label("我的", systemImage: "person.crop.circle")
                }
        }
    }
}

struct ProductListView: View {
    @EnvironmentObject private var session: SessionStore

    @State private var funds: [Fund] = []
    @State private var keyword = ""
    @State private var currentPage = 1
    @State private var total = 0
    @State private var isLoading = false
    @State private var isLoadingMore = false
    @State private var errorMessage: String?
    @State private var navigationPath: [Fund] = []
    @State private var wasShowingDetail = false

    private let pageSize = 20

    var body: some View {
        NavigationStack(path: $navigationPath) {
            List {
                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                    }
                }

                ForEach(funds) { fund in
                    NavigationLink(value: fund) {
                        ProductRow(fund: fund)
                            .onAppear {
                                loadMoreIfNeeded(current: fund)
                            }
                    }
                }

                if isLoadingMore {
                    HStack {
                        Spacer()
                        ProgressView()
                        Spacer()
                    }
                }
            }
            .overlay {
                if isLoading && funds.isEmpty {
                    ProgressView("加载产品")
                } else if !isLoading && funds.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "chart.line.uptrend.xyaxis")
                            .font(.system(size: 42))
                            .foregroundStyle(.secondary)
                        Text("暂无产品")
                            .font(.headline)
                        Text("尝试调整搜索关键词")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .searchable(text: $keyword, prompt: "搜索基金代码或名称")
            .onSubmit(of: .search) {
                Task {
                    await reload()
                }
            }
            .refreshable {
                await reload()
            }
            .navigationTitle("产品")
            .navigationDestination(for: Fund.self) { fund in
                ProductDetailView(fund: fund)
            }
            .onChange(of: navigationPath) { newPath in
                if newPath.isEmpty && wasShowingDetail {
                    wasShowingDetail = false
                    Task {
                        await reload()
                    }
                } else if !newPath.isEmpty {
                    wasShowingDetail = true
                }
            }
            .task {
                if funds.isEmpty {
                    await reload()
                }
            }
        }
    }

    private func reload() async {
        currentPage = 1
        total = 0
        funds = []
        await load(page: 1)
    }

    private func loadMoreIfNeeded(current fund: Fund) {
        guard fund.fundCode == funds.last?.fundCode else {
            return
        }
        guard funds.count < total, !isLoadingMore, !isLoading else {
            return
        }
        Task {
            await load(page: currentPage + 1)
        }
    }

    private func load(page: Int) async {
        if page == 1 {
            isLoading = true
        } else {
            isLoadingMore = true
        }
        defer {
            isLoading = false
            isLoadingMore = false
        }

        do {
            let result = try await session.apiClient.listFunds(current: page, size: pageSize, keyword: keyword)
            currentPage = result.current
            total = result.total
            if page == 1 {
                funds = result.records
            } else {
                funds.append(contentsOf: result.records)
            }
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct ProductRow: View {
    let fund: Fund

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                Text(fund.fundName)
                    .font(.headline)
                    .lineLimit(2)
                Spacer()
                Text(fund.fundCode)
                    .font(.caption.bold())
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(.blue.opacity(0.12), in: Capsule())
                    .foregroundStyle(.blue)
            }

            HStack(spacing: 10) {
                if let fundType = nonEmpty(fund.fundType) {
                    Label(fundType, systemImage: "tag")
                }
                if let manager = nonEmpty(fund.fundManager) {
                    Label(manager, systemImage: "person")
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            if let company = nonEmpty(fund.managementCompany) {
                Label(company, systemImage: "building.columns")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 6)
    }
}

struct ProductDetailView: View {
    @EnvironmentObject private var session: SessionStore

    let fund: Fund

    @State private var detail: FundDetail?
    @State private var chartNavs: [FundNav] = []
    @State private var trendPeriod: TrendPeriod = .oneYear
    @State private var isLoading = false
    @State private var errorMessage: String?

    private var displayFund: Fund {
        detail?.fund ?? fund
    }

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 10) {
                    Text(displayFund.fundName)
                        .font(.title2.bold())
                    Text(displayFund.fundCode)
                        .font(.caption.bold())
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(.blue.opacity(0.12), in: Capsule())
                        .foregroundStyle(.blue)
                }
                .padding(.vertical, 6)
            }

            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                }
            }

            Section("基础信息") {
                DetailLine(title: "类型", value: displayFund.fundType)
                DetailLine(title: "基金经理", value: displayFund.fundManager)
                DetailLine(title: "管理人", value: displayFund.managementCompany)
                DetailLine(title: "成立日期", value: displayFund.inceptionDate)
                DetailLine(title: "净资产规模", value: displayFund.netAssetScale)
                DetailLine(title: "规模截止", value: displayFund.scaleDate)
            }

            Section("最新净值") {
                DetailLine(title: "净值日期", value: detail?.latestNav?.navDate)
                DetailLine(title: "单位净值", value: detail?.latestNav?.unitNav.map(String.init))
                DetailLine(title: "累计净值", value: detail?.latestNav?.accumulatedNav.map(String.init))
                DetailLine(title: "日增长率", value: detail?.latestNav?.dailyGrowthRate.map { "\($0)%" })
            }

            Section("净值与收益走势") {
                Picker("区间", selection: $trendPeriod) {
                    ForEach(TrendPeriod.allCases) { period in
                        Text(period.title).tag(period)
                    }
                }
                .pickerStyle(.segmented)

                FundTrendChart(
                    title: "净值走势图",
                    rows: buildTrendRows(navs: chartNavs, period: trendPeriod),
                    series: [
                        TrendSeries(keyPath: \.unitNav, title: "单位净值", color: .blue),
                        TrendSeries(keyPath: \.accumulatedNav, title: "累计净值", color: .green)
                    ]
                )

                FundTrendChart(
                    title: "收益走势图",
                    rows: buildTrendRows(navs: chartNavs, period: trendPeriod),
                    series: [
                        TrendSeries(keyPath: \.returnRate, title: "累计收益率", color: .orange, suffix: "%")
                    ]
                )
            }

            Section("持仓摘要") {
                ForEach(Array((detail?.latestHoldings ?? []).prefix(5)), id: \.self) { holding in
                    DetailLine(title: holding.stockName ?? holding.stockCode, value: holding.netValueRatio.map { "\($0)%" })
                }
            }

            Section("基金评级") {
                ForEach(Array((detail?.ratings ?? []).prefix(6)), id: \.self) { rating in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(rating.ratingDate)
                            .font(.subheadline.bold())
                        Text(ratingText(rating))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                }
                if detail?.ratings.isEmpty ?? true {
                    DetailLine(title: "评级", value: nil)
                }
            }

            Section("特色数据") {
                ForEach(Array((detail?.features ?? []).prefix(6)), id: \.self) { feature in
                    DetailLine(title: "\(feature.periodLabel) \(feature.cutoffDate)", value: "标准差 \(feature.standardDeviation.map(String.init) ?? "-") / 夏普 \(feature.sharpeRatio.map(String.init) ?? "-")")
                }
            }
        }
        .overlay {
            if isLoading {
                ProgressView()
            }
        }
        .navigationTitle("产品详情")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadDetail()
        }
        .refreshable {
            await loadDetail()
        }
    }

    private func loadDetail() async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let detailResult = session.apiClient.fundDetail(fundCode: fund.fundCode)
            async let navResult = session.apiClient.listFundNavs(fundCode: fund.fundCode, current: 1, size: 1000)
            detail = try await detailResult
            let navPage = try await navResult
            chartNavs = navPage.records
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private func ratingText(_ rating: FundRating) -> String {
    [
        "招商 \(ratingStars(rating.zhaoshangRating))",
        "上海3年 \(ratingStars(rating.shanghaiRating3y))",
        "上海5年 \(ratingStars(rating.shanghaiRating5y))",
        "济安 \(ratingStars(rating.jianRating))",
        "晨星 \(ratingStars(rating.morningStarRating))"
    ].joined(separator: " / ")
}

private func ratingStars(_ value: Int?) -> String {
    guard let value, value > 0 else {
        return "-"
    }
    return String(repeating: "★", count: min(value, 5))
}

private enum TrendPeriod: String, CaseIterable, Identifiable {
    case oneMonth
    case threeMonths
    case sixMonths
    case oneYear
    case threeYears
    case all

    var id: String { rawValue }

    var title: String {
        switch self {
        case .oneMonth: return "1月"
        case .threeMonths: return "3月"
        case .sixMonths: return "6月"
        case .oneYear: return "1年"
        case .threeYears: return "3年"
        case .all: return "全部"
        }
    }

    var monthCount: Int? {
        switch self {
        case .oneMonth: return 1
        case .threeMonths: return 3
        case .sixMonths: return 6
        case .oneYear: return 12
        case .threeYears: return 36
        case .all: return nil
        }
    }
}

private struct TrendRow: Identifiable {
    let id = UUID()
    let date: String
    let unitNav: Double?
    let accumulatedNav: Double?
    let returnRate: Double?
}

private struct TrendSeries {
    let keyPath: KeyPath<TrendRow, Double?>
    let title: String
    let color: Color
    var suffix: String = ""
}

private struct FundTrendChart: View {
    let title: String
    let rows: [TrendRow]
    let series: [TrendSeries]

    private var values: [Double] {
        rows.flatMap { row in
            series.compactMap { row[keyPath: $0.keyPath] }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title)
                    .font(.subheadline.bold())
                Spacer()
                ForEach(series, id: \.title) { item in
                    HStack(spacing: 4) {
                        Circle()
                            .fill(item.color)
                            .frame(width: 7, height: 7)
                        Text(item.title)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            if rows.count < 2 || values.count < 2 {
                Text("暂无走势数据")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 180)
                    .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 8))
            } else {
                Canvas { context, size in
                    drawChart(context: context, size: size)
                }
                .frame(height: 200)
            }
        }
        .padding(.vertical, 4)
    }

    private func drawChart(context: GraphicsContext, size: CGSize) {
        let left: CGFloat = 42
        let right: CGFloat = 12
        let top: CGFloat = 14
        let bottom: CGFloat = 28
        let plotWidth = max(size.width - left - right, 1)
        let plotHeight = max(size.height - top - bottom, 1)
        let minValue = values.min() ?? 0
        let maxValue = values.max() ?? 1
        let padding = max((maxValue - minValue) * 0.08, 0.01)
        let yMin = minValue - padding
        let yMax = maxValue + padding

        func x(_ index: Int) -> CGFloat {
            left + plotWidth * CGFloat(index) / CGFloat(max(rows.count - 1, 1))
        }

        func y(_ value: Double) -> CGFloat {
            top + plotHeight - CGFloat((value - yMin) / max(yMax - yMin, 0.000001)) * plotHeight
        }

        for tick in [yMax, (yMax + yMin) / 2, yMin] {
            var grid = Path()
            grid.move(to: CGPoint(x: left, y: y(tick)))
            grid.addLine(to: CGPoint(x: size.width - right, y: y(tick)))
            context.stroke(grid, with: .color(Color(.separator)), lineWidth: 0.6)
            context.draw(Text(formatChartNumber(tick, suffix: series.first?.suffix ?? "")).font(.caption2).foregroundColor(.secondary), at: CGPoint(x: left - 4, y: y(tick)), anchor: .trailing)
        }

        let xTickIndexes = [0, max((rows.count - 1) / 2, 0), rows.count - 1]
        for index in xTickIndexes {
            let anchor: UnitPoint = index == 0 ? .leading : (index == rows.count - 1 ? .trailing : .center)
            context.draw(Text(formatNavDate(rows[index].date)).font(.caption2).foregroundColor(.secondary), at: CGPoint(x: x(index), y: size.height - 8), anchor: anchor)
        }

        for item in series {
            var path = Path()
            var started = false
            for (index, row) in rows.enumerated() {
                guard let value = row[keyPath: item.keyPath] else {
                    continue
                }
                let point = CGPoint(x: x(index), y: y(value))
                if started {
                    path.addLine(to: point)
                } else {
                    path.move(to: point)
                    started = true
                }
            }
            context.stroke(path, with: .color(item.color), lineWidth: 2)
        }
    }
}

private func buildTrendRows(navs: [FundNav], period: TrendPeriod) -> [TrendRow] {
    let sorted = navs
        .filter { !$0.navDate.isEmpty && ($0.unitNav != nil || $0.accumulatedNav != nil) }
        .sorted { $0.navDate < $1.navDate }
    let filtered = filterTrendPeriod(navs: sorted, period: period)
    let base = filtered
        .compactMap { decimalToDouble($0.accumulatedNav) ?? decimalToDouble($0.unitNav) }
        .first { $0 != 0 }

    return filtered.map { nav in
        let unitNav = decimalToDouble(nav.unitNav)
        let accumulatedNav = decimalToDouble(nav.accumulatedNav)
        let returnValue = accumulatedNav ?? unitNav
        return TrendRow(
            date: nav.navDate,
            unitNav: unitNav,
            accumulatedNav: accumulatedNav,
            returnRate: base.flatMap { baseValue in returnValue.map { ($0 / baseValue - 1) * 100 } }
        )
    }
}

private func filterTrendPeriod(navs: [FundNav], period: TrendPeriod) -> [FundNav] {
    guard let months = period.monthCount,
          let lastDateText = navs.last?.navDate,
          let lastDate = parseNavDate(lastDateText),
          let startDate = Calendar.current.date(byAdding: .month, value: -months, to: lastDate) else {
        return navs
    }
    return navs.filter { nav in
        guard let date = parseNavDate(nav.navDate) else {
            return true
        }
        return date >= startDate
    }
}

private func decimalToDouble(_ value: Decimal?) -> Double? {
    guard let value else {
        return nil
    }
    return NSDecimalNumber(decimal: value).doubleValue
}

private func parseNavDate(_ value: String) -> Date? {
    guard value.count == 8 else {
        return nil
    }
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter.date(from: value)
}

private func formatNavDate(_ value: String) -> String {
    guard value.count == 8 else {
        return value
    }
    let year = value.prefix(4)
    let monthStart = value.index(value.startIndex, offsetBy: 4)
    let monthEnd = value.index(value.startIndex, offsetBy: 6)
    let dayStart = value.index(value.startIndex, offsetBy: 6)
    return "\(year)-\(value[monthStart..<monthEnd])-\(value[dayStart...])"
}

private func formatChartNumber(_ value: Double, suffix: String) -> String {
    let digits = abs(value) >= 10 ? 2 : 4
    return String(format: "%.\(digits)f%@", value, suffix)
}

private struct DetailLine: View {
    let title: String
    let value: String?

    var body: some View {
        HStack(alignment: .top) {
            Text(title)
                .foregroundStyle(.secondary)
            Spacer()
            Text(nonEmpty(value) ?? "-")
                .multilineTextAlignment(.trailing)
                .foregroundStyle(nonEmpty(value) == nil ? .secondary : .primary)
        }
    }
}

struct MineView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        NavigationStack {
            List {
                Section("账号") {
                    DetailLine(title: "当前用户", value: session.username)
                    DetailLine(title: "服务器", value: session.baseURL)
                }
                Section {
                    Button("退出登录", role: .destructive) {
                        session.logout()
                    }
                }
            }
            .navigationTitle("我的")
        }
    }
}
