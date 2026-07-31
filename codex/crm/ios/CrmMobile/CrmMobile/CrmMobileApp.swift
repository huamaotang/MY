import PhotosUI
import SwiftUI
import UIKit

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
            ProductHubView()
                .tabItem {
                    Label("产品", systemImage: "chart.line.uptrend.xyaxis")
                }
            FinanceNewsView()
                .tabItem { Label("资讯", systemImage: "newspaper") }
            PortfolioHoldingView()
                .tabItem { Label("持仓", systemImage: "square.and.arrow.down") }
            MineView()
                .tabItem {
                    Label("我的", systemImage: "person.crop.circle")
                }
        }
    }
}

struct ProductHubView: View {
    @State private var section = 0
    var body: some View {
        VStack(spacing: 0) {
            Picker("产品类型", selection: $section) {
                Text("基金").tag(0)
                Text("股票").tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.top, 8)
            if section == 0 { ProductListView() } else { StockListView() }
        }
    }
}

struct StockListView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var rows: [StockQuote] = []
    @State private var keyword = ""
    @State private var loading = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            List {
                if let error { Text(error).foregroundStyle(.red) }
                ForEach(rows) { item in
                    NavigationLink(value: item) {
                        VStack(alignment: .leading, spacing: 7) {
                            HStack {
                                Text(item.stockName ?? item.stockCode).font(.headline)
                                Text(item.stockCode).font(.caption).foregroundStyle(.secondary)
                                Spacer()
                                Text(decimal(item.latestPrice)).font(.headline)
                            }
                            HStack {
                                signedPercent(item.changeRate)
                                Text("成交额 \(compact(item.amount))")
                                Spacer()
                                Text(item.tradeDate ?? "-")
                            }.font(.caption)
                            HStack {
                                Text("最后更新时间 \(item.updatedAt ?? "-")")
                                Text("备注 \(item.comment ?? "-")")
                                Spacer()
                            }
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        }.padding(.vertical, 4)
                    }
                }
            }
            .searchable(text: $keyword, prompt: "股票代码或名称")
            .onSubmit(of: .search) { Task { await load() } }
            .navigationTitle("股票行情")
            .navigationDestination(for: StockQuote.self) { StockHistoryView(stock: $0) }
            .refreshable { await load() }
            .overlay { if loading && rows.isEmpty { ProgressView("加载股票行情") } }
            .task { if rows.isEmpty { await load() } }
        }
    }
    private func load() async {
        loading = true; defer { loading = false }
        do { rows = try await session.apiClient.listStocks(current: 1, size: 100, keyword: keyword, sortField: "changeRate", sortOrder: "descend").records; error = nil }
        catch { self.error = error.localizedDescription }
    }
}

struct StockHistoryView: View {
    @EnvironmentObject private var session: SessionStore
    let stock: StockQuote
    @State private var rows: [StockQuote] = []
    @State private var error: String?
    var body: some View {
        List {
            if let error { Text(error).foregroundStyle(.red) }
            Section {
                Text("最后更新时间 \(stock.updatedAt ?? "-")")
                Text("备注 \(stock.comment ?? "-")")
            }
            ForEach(rows) { item in
                VStack(alignment: .leading, spacing: 6) {
                    HStack { Text(item.tradeDate ?? "-").font(.headline); Spacer(); signedPercent(item.changeRate) }
                    HStack { Text("最后更新时间 \(item.updatedAt ?? "-")"); Spacer(); Text("备注 \(item.comment ?? "-")") }
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    HStack { Text("开 \(decimal(item.openPrice))"); Text("高 \(decimal(item.highPrice))"); Text("低 \(decimal(item.lowPrice))"); Text("收 \(decimal(item.latestPrice))") }.font(.caption)
                    Text("成交量 \(compact(item.volume))  成交额 \(compact(item.amount))  换手 \(decimal(item.turnoverRate))%").font(.caption).foregroundStyle(.secondary)
                }.padding(.vertical, 3)
            }
        }
        .navigationTitle(stock.stockName ?? stock.stockCode)
        .task {
            do { rows = try await session.apiClient.stockHistory(stockCode: stock.stockCode).records }
            catch { self.error = error.localizedDescription }
        }
    }
}

private func decimal(_ value: Decimal?) -> String { value.map { NSDecimalNumber(decimal: $0).stringValue } ?? "-" }
private let moneyFormatter: NumberFormatter = {
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.numberStyle = .decimal
    formatter.usesGroupingSeparator = true
    formatter.groupingSeparator = ","
    formatter.groupingSize = 3
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter
}()
private func money(_ value: Decimal?) -> String {
    guard let value else { return "-" }
    return moneyFormatter.string(from: NSDecimalNumber(decimal: value)) ?? "-"
}
private func compact(_ value: Decimal?) -> String {
    guard let value else { return "-" }
    let number = NSDecimalNumber(decimal: value).doubleValue
    if abs(number) >= 100_000_000 { return String(format: "%.2f亿", number / 100_000_000) }
    if abs(number) >= 10_000 { return String(format: "%.2f万", number / 10_000) }
    return String(format: "%.0f", number)
}
@ViewBuilder private func signedPercent(_ value: Decimal?) -> some View {
    let number = value.map { NSDecimalNumber(decimal: $0).doubleValue }
    Text(number.map { String(format: "%.2f%%", $0) } ?? "-")
        .foregroundStyle(number.map { $0 > 0 ? Color.red : $0 < 0 ? Color.green : Color.secondary } ?? Color.secondary)
}
@ViewBuilder private func signedValue(_ value: Decimal?) -> some View {
    let number = value.map { NSDecimalNumber(decimal: $0).doubleValue }
    Text(money(value))
        .foregroundStyle(number.map { $0 > 0 ? Color.red : $0 < 0 ? Color.green : Color.secondary } ?? Color.secondary)
}

struct FinanceNewsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var rows: [FinanceNews] = []
    @State private var categoryTag = -1
    @State private var loading = false
    @State private var error: String?
    private let categories: [(name: String, tag: Int)] = [
        ("全部", -1), ("A股", 10), ("宏观", 1), ("产业", 110), ("公司", 3),
        ("数据", 4), ("市场", 5), ("国际", 102), ("观点", 6), ("央行", 7), ("其他", 8)
    ]
    var body: some View {
        NavigationStack { List {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categories, id: \.tag) { category in
                        Button {
                            categoryTag = category.tag
                        } label: {
                            Text(category.name)
                                .font(.subheadline.weight(categoryTag == category.tag ? .semibold : .regular))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .foregroundStyle(categoryTag == category.tag ? Color.white : Color.primary)
                                .background(categoryTag == category.tag ? Color.accentColor : Color(.secondarySystemBackground))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 2)
            }
            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 0))
            .listRowSeparator(.hidden)
            if let error { Text(error).foregroundStyle(.red) }
            ForEach(rows) { item in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(item.createTime).font(.caption).foregroundStyle(.secondary)
                        Text(item.categoryName).font(.caption.bold()).foregroundStyle(item.categoryTag == 10 ? .red : .blue)
                    }
                    Text(item.content).font(.body)
                    if let url = item.docUrl, let link = URL(string: url) { Link("查看原文", destination: link).font(.caption) }
                }.padding(.vertical, 5)
            }
        }.overlay { if loading && rows.isEmpty { ProgressView("加载资讯") } }
         .navigationTitle("7×24资讯").refreshable { await load() }.task { if rows.isEmpty { await load() } }
         .onChange(of: categoryTag) { _ in Task { await load() } }}
    }
    private func load() async { loading = true; defer { loading = false }; do { rows = try await session.apiClient.listFinanceNews(current: 1, size: 100, categoryTag: categoryTag < 0 ? nil : categoryTag).records; error = nil } catch { self.error = error.localizedDescription } }
}

struct PortfolioHoldingView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var selectedTab = 0
    @State private var overview: PortfolioOverview?
    @State private var holdings: [UserFundHolding] = []
    @State private var keyword = ""
    @State private var sortField = "holdingAmount"
    @State private var sortOrder = "desc"
    @State private var loading = false
    @State private var error: String?
    @State private var showImport = false
    @State private var importSourceLabel = "alipay"
    @State private var importType = "holding"

    private let tabs = ["账户汇总", "全部", "支付宝", "腾讯理财通"]
    private var scope: String {
        switch selectedTab {
        case 1: return "all"
        case 2: return "alipay"
        case 3: return "tencent"
        default: return "all"
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(tabs.indices, id: \.self) { index in
                            Button(tabs[index]) {
                                selectedTab = index
                            }
                            .font(.body.weight(selectedTab == index ? .semibold : .regular))
                            .foregroundStyle(selectedTab == index ? .white : .primary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(selectedTab == index ? Color.accentColor : Color(.secondarySystemBackground))
                            .clipShape(Capsule())
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                }
                Divider()
                if let error {
                    Text(error).font(.caption).foregroundStyle(.red).padding(.horizontal).padding(.top, 8)
                }
                if selectedTab == 0 {
                    accountSummary
                } else {
                    holdingList
                }
            }
            .navigationTitle("持仓")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        openImport(type: "holding")
                    } label: {
                        Label("导入", systemImage: "square.and.arrow.down")
                    }
                }
            }
            .sheet(isPresented: $showImport, onDismiss: { Task { await reload() } }) {
                PortfolioImportView(
                    initialSourceLabel: importSourceLabel,
                    initialImportType: importType
                )
                    .environmentObject(session)
            }
            .task { await reload() }
            .onChange(of: selectedTab) { _ in
                if selectedTab > 0 { Task { await loadHoldings() } }
            }
        }
    }

    private var accountSummary: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
                if let total = overview?.total {
                    PortfolioSummaryCard(summary: total, prominent: true)
                }
                ForEach(overview?.accounts ?? [], id: \.sourceLabel) { account in
                    Button {
                        selectedTab = account.sourceLabel == "tencent" ? 3 : 2
                    } label: {
                        PortfolioSummaryCard(summary: account, prominent: false)
                    }
                    .buttonStyle(.plain)
                }
                if overview == nil && loading { ProgressView("加载账户") }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
        .refreshable { await reload() }
    }

    private var holdingList: some View {
        VStack(spacing: 0) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("搜索基金", text: $keyword)
                    .submitLabel(.search)
                    .onSubmit { Task { await loadHoldings() } }
                if !keyword.isEmpty {
                    Button { keyword = ""; Task { await loadHoldings() } } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                    }
                }
            }
            .padding(8)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            if let summary = selectedSummary {
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text("资产").foregroundStyle(.secondary)
                    Text(money(summary.holdingAmount))
                        .font(.headline.weight(.semibold))
                    Spacer()
                    Text("今日").foregroundStyle(.secondary)
                    Text(money(summary.todayProfit))
                        .foregroundStyle(summary.todayProfit.map(signedValueColor) ?? .secondary)
                    Spacer()
                    Text("\(summary.holdingCount) 只")
                }
                .font(.subheadline)
                .padding(.horizontal, 12)
                .padding(.bottom, 2)
                .fixedSize(horizontal: false, vertical: true)
            }
            if selectedTab >= 2 {
                accountImportActions
            }
            if holdings.isEmpty && !loading {
                VStack(spacing: 10) {
                    Image(systemName: "tray").font(.largeTitle).foregroundStyle(.secondary)
                    Text(selectedTab == 3 ? "暂无腾讯理财通持仓" : "暂无持仓").font(.headline)
                    Text(selectedTab >= 2
                         ? "点击上方“导入持仓列表”上传账户截图"
                         : "点击右上角“导入”上传账户截图")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                PortfolioCompactTable(
                    holdings: holdings,
                    sortField: sortField,
                    sortOrder: sortOrder,
                    onSort: { field in
                        if sortField == field {
                            sortOrder = sortOrder == "asc" ? "desc" : "asc"
                        } else {
                            sortField = field
                            sortOrder = field == "fundName" || field == "fundType" ? "asc" : "desc"
                        }
                        Task { await loadHoldings() }
                    }
                )
                .refreshable { await reload() }
            }
            if loading { ProgressView().padding(.vertical, 8) }
        }
    }

    private var accountImportActions: some View {
        HStack(spacing: 8) {
            Button {
                openImport(type: "holding")
            } label: {
                Label("导入持仓列表", systemImage: "list.bullet.rectangle")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)

            Button {
                openImport(type: "trade")
            } label: {
                Label("导入交易记录", systemImage: "arrow.left.arrow.right")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
        .font(.subheadline.weight(.semibold))
        .padding(.horizontal, 12)
        .padding(.bottom, 4)
    }

    private func openImport(type: String) {
        importSourceLabel = selectedTab == 3 ? "tencent" : "alipay"
        importType = type
        showImport = true
    }

    private var selectedSummary: PortfolioAccountSummary? {
        if selectedTab == 1 { return overview?.total }
        return overview?.accounts.first { $0.sourceLabel == scope }
    }

    private func reload() async {
        loading = true
        defer { loading = false }
        do {
            overview = try await session.apiClient.portfolioOverview()
            if selectedTab > 0 { await loadHoldings(manageLoading: false) }
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func loadHoldings(manageLoading: Bool = true) async {
        if manageLoading { loading = true }
        defer { if manageLoading { loading = false } }
        do {
            holdings = try await session.apiClient.listPortfolioHoldings(
                current: 1, size: 200, keyword: keyword, scope: scope,
                sortField: sortField, sortOrder: sortOrder
            ).records
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

private struct PortfolioSummaryCard: View {
    let summary: PortfolioAccountSummary
    let prominent: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(summary.displayName).font(prominent ? .title2.bold() : .title3.bold())
                Spacer()
                Text("\(summary.holdingCount) 只").font(.subheadline).foregroundStyle(.secondary)
                if !prominent { Image(systemName: "chevron.right").font(.subheadline).foregroundStyle(.secondary) }
            }
            Text(money(summary.holdingAmount))
                .font(prominent ? .system(size: 32, weight: .bold) : .title2.bold())
            HStack {
                summaryMetric("持有收益", summary.holdingProfit, percentValue: false)
                Spacer()
                summaryMetric("收益率", summary.holdingReturnRate, percentValue: true)
                Spacer()
                summaryMetric("今日收益", summary.todayProfit, percentValue: false)
            }
        }
        .padding(14)
        .background(prominent ? Color.accentColor.opacity(0.12) : Color(.secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func summaryMetric(_ title: String, _ value: Decimal?, percentValue: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.subheadline).foregroundStyle(.secondary)
            Text(percentValue ? (percent(value) ?? "-") : money(value))
                .font(.body.bold())
                .foregroundStyle(value.map(signedValueColor) ?? .secondary)
        }
    }
}

private struct PortfolioCompactTable: View {
    let holdings: [UserFundHolding]
    let sortField: String
    let sortOrder: String
    let onSort: (String) -> Void

    var body: some View {
        ScrollView([.horizontal, .vertical], showsIndicators: true) {
            LazyVStack(spacing: 0) {
                HStack(spacing: 0) {
                    header("基金 / 金额", "fundName", 132, .leading)
                    header("当日收益", "estimatedDailyProfit", 92)
                    header("类型", "fundType", 76)
                    header("持有收益 / 率", "holdingProfit", 116)
                }
                .background(Color(.secondarySystemGroupedBackground))
                ForEach(holdings) { holding in
                    Divider()
                    NavigationLink {
                        PortfolioHoldingDetailView(holding: holding)
                    } label: {
                        HStack(spacing: 0) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(portfolioFundName(holding.fundName)).font(.subheadline.bold()).foregroundStyle(.blue)
                                Text(money(holding.holdingAmount)).font(.subheadline).foregroundStyle(.secondary)
                            }
                            .frame(width: 132, alignment: .leading)
                            .frame(minHeight: 54)
                            .padding(.horizontal, 4)
                            valueCell(holding.estimatedDailyProfit ?? holding.todayProfit, width: 92)
                            Text(holding.fundType ?? "-")
                                .font(.subheadline)
                                .lineLimit(1)
                                .frame(width: 76)
                                .frame(minHeight: 54)
                                .padding(.horizontal, 4)
                            VStack(alignment: .trailing, spacing: 2) {
                                Text(money(holding.holdingProfit))
                                Text(percent(holding.holdingReturnRate) ?? "-")
                            }
                            .font(.subheadline)
                            .foregroundStyle(holding.holdingProfit.map(signedValueColor) ?? .secondary)
                            .frame(width: 116, alignment: .trailing)
                            .frame(minHeight: 54)
                            .padding(.horizontal, 4)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func header(_ title: String, _ field: String, _ width: CGFloat,
                        _ alignment: Alignment = .trailing) -> some View {
        Button {
            onSort(field)
        } label: {
            HStack(spacing: 3) {
                Text(title)
                if sortField == field {
                    Image(systemName: sortOrder == "asc" ? "chevron.up" : "chevron.down")
                }
            }
            .font(.subheadline.bold())
            .frame(width: width, alignment: alignment)
            .frame(minHeight: 40)
            .padding(.horizontal, 4)
        }
        .buttonStyle(.plain)
    }

    private func valueCell(_ value: Decimal?, width: CGFloat) -> some View {
        Text(money(value))
            .font(.subheadline)
            .foregroundStyle(value.map(signedValueColor) ?? .secondary)
            .frame(width: width, alignment: .trailing)
            .frame(minHeight: 54)
            .padding(.horizontal, 4)
    }
}

struct PortfolioImportView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    @State private var selectedItems: [PhotosPickerItem] = []
    @State private var preview: PortfolioHoldingImportPreview?
    @State private var holdings: [UserFundHolding] = []
    @State private var imports: [PortfolioHoldingBatch] = []
    @State private var keyword = ""
    @State private var sourceLabel: String
    @State private var importType: String
    @State private var loading = false
    @State private var uploading = false
    @State private var error: String?
    @State private var notice: String?

    init(initialSourceLabel: String = "alipay", initialImportType: String = "holding") {
        _sourceLabel = State(initialValue:
            initialSourceLabel == "tencent" ? "tencent" : "alipay")
        _importType = State(initialValue:
            initialImportType == "trade" ? "trade" : "holding")
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("账户来源", selection: $sourceLabel) {
                        Text("支付宝").tag("alipay")
                        Text("腾讯理财通").tag("tencent")
                    }
                    .pickerStyle(.segmented)
                    Picker("导入类型", selection: $importType) {
                        Text("持仓列表").tag("holding")
                        Text("交易记录").tag("trade")
                    }
                    .pickerStyle(.segmented)
                    PhotosPicker(selection: $selectedItems, maxSelectionCount: 3, matching: .images) {
                        Label(importType == "trade" ? "选择交易记录截图" : "选择持仓列表截图",
                              systemImage: "photo.on.rectangle")
                    }
                    Button(uploading ? "上传中…" : "上传并识别") {
                        Task { await uploadSelectedImages() }
                    }
                    .disabled(selectedItems.isEmpty || uploading)
                    if let preview {
                        Text("\(preview.sourceLabel == "tencent" ? "腾讯理财通" : "支付宝") · \(preview.importType == "trade" ? "交易增减" : "持仓覆盖") · \(preview.imageCount) 张 · \(preview.screenshotDate ?? "-")")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if !preview.warnings.isEmpty {
                            Text(preview.warnings.joined(separator: "；"))
                                .font(.caption)
                                .foregroundStyle(.red)
                        }
                    }
                }

                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }
                if let notice {
                    Section { Text(notice).foregroundStyle(.green) }
                }

                if let preview {
                    Section("识别预览") {
                        if preview.importType == "trade" {
                            ForEach(Array(preview.tradeAdjustments.indices), id: \.self) { index in
                                PortfolioTradeAdjustmentEditor(
                                    adjustment: bindingForTradeAdjustment(index),
                                    isEditable: preview.status == "PREVIEWED"
                                )
                            }
                        } else {
                            ForEach(Array(preview.rows.enumerated()), id: \.offset) { index, _ in
                                PortfolioHoldingRowEditor(row: bindingForPreviewRow(index), isEditable: preview.status == "PREVIEWED", onFundCodeChange: { code in
                                    updatePreviewRow(index) { $0.fundCode = code }
                                })
                            }
                        }
                        if preview.status == "PREVIEWED" {
                            Button("确认入库") {
                                Task { await confirmPreview() }
                            }
                            .disabled(uploading)
                        } else {
                            Label("该批次已入库", systemImage: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                        }
                    }
                }

                Section {
                    HStack {
                        TextField("筛选当前持仓", text: $keyword)
                        Button("查询") { Task { await loadHoldings() } }
                    }
                }

                Section("当前持仓") {
                    Text("左右滑动查看全部数据，点击一行查看持仓详情")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if holdings.isEmpty && !loading {
                        Label("暂无持仓", systemImage: "tray")
                            .foregroundStyle(.secondary)
                    } else {
                        PortfolioHoldingsTable(holdings: holdings)
                            .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
                    }
                }

                Section("导入历史") {
                    ForEach(imports.filter { $0.sourceLabel == sourceLabel }) { item in
                        Button {
                            Task { await openImport(item.id) }
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text("批次 \(item.id)").font(.headline)
                                    Spacer()
                                    Text(item.status).font(.caption).foregroundStyle(.secondary)
                                }
                                Text("截图 \(item.screenshotDate ?? "-") · \(item.itemCount) 只基金")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text("\(item.sourceLabel == "tencent" ? "腾讯理财通" : "支付宝") · \(item.importType == "trade" ? "交易增减 \(item.appliedCount)/\(item.skippedCount)" : "持仓覆盖")")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("\(sourceLabel == "tencent" ? "腾讯理财通" : "支付宝")导入")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("关闭") { dismiss() }
                }
            }
            .task {
                if holdings.isEmpty {
                    await loadHoldings()
                }
                if imports.isEmpty {
                    await loadImports()
                }
            }
            .onChange(of: sourceLabel) { _ in
                selectedItems.removeAll()
                preview = nil
                notice = nil
                Task { await loadHoldings() }
            }
            .refreshable {
                await loadHoldings()
                await loadImports()
            }
            .overlay { if loading && holdings.isEmpty { ProgressView("加载持仓") } }
        }
    }

    private func bindingForPreviewRow(_ index: Int) -> Binding<PortfolioHoldingImportRow> {
        Binding(
            get: { preview?.rows[index] ?? PortfolioHoldingImportRow(rowNo: 0, fundCode: nil, fundName: "", holdingAmount: nil, holdingProfit: nil, holdingReturnRate: nil, holdingCost: nil, yesterdayProfit: nil, todayProfit: nil, holdingShares: nil, costNav: nil, screenshotDate: nil, confidence: nil, rawTexts: [], candidates: []) },
            set: { newValue in updatePreviewRow(index) { $0 = newValue } }
        )
    }

    private func updatePreviewRow(_ index: Int, mutate: (inout PortfolioHoldingImportRow) -> Void) {
        guard var current = preview, current.rows.indices.contains(index) else { return }
        var row = current.rows[index]
        mutate(&row)
        current.rows[index] = row
        preview = current
    }

    private func bindingForTradeAdjustment(_ index: Int) -> Binding<PortfolioTradeAdjustment> {
        Binding(
            get: {
                preview?.tradeAdjustments[index] ?? PortfolioTradeAdjustment(
                    groupKey: "",
                    fundCode: nil,
                    fundName: "",
                    buyAmount: 0,
                    sellAmount: 0,
                    netAmount: 0,
                    currentHoldingAmount: nil,
                    projectedHoldingAmount: nil,
                    transactionCount: 0,
                    skippedCount: 0,
                    applicable: false,
                    warnings: [],
                    candidates: []
                )
            },
            set: { newValue in
                guard var current = preview,
                      current.tradeAdjustments.indices.contains(index) else { return }
                current.tradeAdjustments[index] = newValue
                preview = current
            }
        )
    }

    private func loadHoldings() async {
        loading = true
        defer { loading = false }
        do {
            holdings = try await session.apiClient.listPortfolioHoldings(
                current: 1,
                size: 100,
                keyword: keyword,
                scope: sourceLabel
            ).records
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func loadImports() async {
        do {
            imports = try await session.apiClient.listPortfolioImports(current: 1, size: 20).records
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func uploadSelectedImages() async {
        guard !selectedItems.isEmpty else { return }
        uploading = true
        defer { uploading = false }
        do {
            var images: [Data] = []
            for item in selectedItems.prefix(3) {
                guard let data = try await item.loadTransferable(type: Data.self), let image = UIImage(data: data), let jpeg = image.jpegData(compressionQuality: 0.9) else {
                    continue
                }
                images.append(jpeg)
            }
            guard !images.isEmpty else {
                error = "未能读取所选图片，请重新选择截图"
                return
            }
            preview = try await session.apiClient.previewPortfolioHoldings(
                images: images,
                sourceLabel: sourceLabel,
                importType: importType
            )
            error = nil
            notice = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func confirmPreview() async {
        guard let preview else { return }
        guard preview.importType == "trade"
                || preview.rows.allSatisfy({ !($0.fundCode ?? "").isEmpty }) else {
            error = "请为每条识别结果选择基金代码"
            return
        }
        uploading = true
        defer { uploading = false }
        do {
            let result = try await session.apiClient.confirmPortfolioHoldingImport(
                importId: preview.importId,
                request: PortfolioHoldingConfirmRequest(
                    screenshotDate: preview.importType == "holding" ? preview.screenshotDate : nil,
                    items: preview.importType == "holding" ? preview.rows.map {
                        PortfolioHoldingConfirmItemRequest(
                            rowNo: $0.rowNo,
                            fundCode: $0.fundCode ?? "",
                            fundName: $0.fundName,
                            holdingAmount: $0.holdingAmount,
                            holdingProfit: $0.holdingProfit,
                            holdingReturnRate: $0.holdingReturnRate,
                            holdingCost: $0.holdingCost,
                            yesterdayProfit: $0.yesterdayProfit,
                            todayProfit: $0.todayProfit,
                            holdingShares: $0.holdingShares,
                            costNav: $0.costNav,
                            screenshotDate: $0.screenshotDate,
                            confidence: $0.confidence,
                            rawTexts: $0.rawTexts
                        )
                    } : nil,
                    tradeMappings: preview.importType == "trade"
                        ? preview.tradeAdjustments.compactMap {
                            guard let fundCode = $0.fundCode, !fundCode.isEmpty else { return nil }
                            return PortfolioTradeMappingRequest(
                                groupKey: $0.groupKey,
                                fundCode: fundCode
                            )
                        }
                        : nil
                )
            )
            notice = preview.importType == "trade"
                ? "已调整 \(result.affectedHoldingCount) 只基金，应用 \(result.appliedTransactionCount) 条，跳过 \(result.skippedTransactionCount) 条"
                : "已覆盖 \(result.affectedHoldingCount) 只基金持仓"
            if !result.warnings.isEmpty {
                notice = [notice, result.warnings.joined(separator: "；")]
                    .compactMap { $0 }
                    .joined(separator: "\n")
            }
            self.preview = nil
            selectedItems.removeAll()
            error = nil
            await loadHoldings()
            await loadImports()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func openImport(_ importId: Int) async {
        do {
            preview = try await session.apiClient.portfolioHoldingImport(importId: importId)
            notice = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

struct PortfolioHoldingRowEditor: View {
    @Binding var row: PortfolioHoldingImportRow
    let isEditable: Bool
    let onFundCodeChange: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(row.fundName).font(.headline)
            if !row.rawTexts.isEmpty {
                Text(row.rawTexts.joined(separator: " / "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Picker("基金代码", selection: Binding(get: { row.fundCode ?? "" }, set: { value in
                row.fundCode = value.isEmpty ? nil : value
                onFundCodeChange(value)
            })) {
                Text("请选择").tag("")
                ForEach(row.candidates, id: \.fundCode) { candidate in
                    Text("\(candidate.fundCode) \(candidate.fundName)").tag(candidate.fundCode)
                }
            }
            .pickerStyle(.menu)
            .disabled(!isEditable)
            TextField("也可手工输入基金代码", text: Binding(
                get: { row.fundCode ?? "" },
                set: { value in
                    row.fundCode = value.isEmpty ? nil : value
                    onFundCodeChange(value)
                }
            ))
            .textInputAutocapitalization(.never)
            .keyboardType(.numberPad)
            .disabled(!isEditable)
            HStack {
                Text("金额 \(money(row.holdingAmount))")
                Text("净值成本 \(decimal(row.costNav))")
                Spacer()
                signedPercent(row.holdingReturnRate)
            }
            .font(.caption)
            HStack {
                Text("收益")
                signedValue(row.holdingProfit)
                Text("昨收")
                signedValue(row.yesterdayProfit)
                Text("置信度 \(decimal(row.confidence))")
                Spacer()
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 6)
    }
}

struct PortfolioTradeAdjustmentEditor: View {
    @Binding var adjustment: PortfolioTradeAdjustment
    let isEditable: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(adjustment.fundName).font(.headline)
            Picker("现有持仓", selection: Binding(
                get: { adjustment.fundCode ?? "" },
                set: { value in
                    adjustment.fundCode = value.isEmpty ? nil : value
                    if let candidate = adjustment.candidates.first(where: { $0.fundCode == value }) {
                        adjustment.fundName = candidate.fundName
                    }
                    adjustment.applicable = !value.isEmpty
                }
            )) {
                Text("不应用").tag("")
                ForEach(adjustment.candidates, id: \.fundCode) { candidate in
                    Text("\(candidate.fundCode) \(candidate.fundName)").tag(candidate.fundCode)
                }
            }
            .pickerStyle(.menu)
            .disabled(!isEditable)

            HStack {
                Text("买入 \(money(adjustment.buyAmount))")
                Text("卖出 \(money(adjustment.sellAmount))")
                Spacer()
                Text("净额 \(money(adjustment.netAmount))")
                    .foregroundStyle(signedValueColor(adjustment.netAmount))
            }
            .font(.caption)
            Text("\(money(adjustment.currentHoldingAmount)) → \(money(adjustment.projectedHoldingAmount))")
                .font(.caption)
            HStack {
                Text("应用 \(adjustment.transactionCount) 条")
                Text("跳过 \(adjustment.skippedCount) 条")
                Spacer()
                Label(adjustment.applicable ? "可应用" : "将跳过",
                      systemImage: adjustment.applicable ? "checkmark.circle" : "exclamationmark.triangle")
                    .foregroundStyle(adjustment.applicable ? .green : .orange)
            }
            .font(.caption2)
            if !adjustment.warnings.isEmpty {
                Text(adjustment.warnings.joined(separator: "；"))
                    .font(.caption2)
                    .foregroundStyle(.orange)
            }
        }
        .padding(.vertical, 6)
    }
}

private struct PortfolioHoldingsTable: View {
    let holdings: [UserFundHolding]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 0) {
                header
                    .background(Color(.secondarySystemGroupedBackground))
                Divider()
                ForEach(holdings) { holding in
                    NavigationLink {
                        PortfolioHoldingDetailView(holding: holding)
                    } label: {
                        row(holding)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Divider()
                }
            }
        }
    }

    private var header: some View {
        HStack(spacing: 0) {
            cell("基金", width: 124, alignment: .leading, weight: .semibold)
            cell("持有金额", width: 100, weight: .semibold)
            cell("当日预估", width: 96, weight: .semibold)
            cell("预估盈亏", width: 96, weight: .semibold)
            cell("估值后金额", width: 108, weight: .semibold)
            cell("累计预估", width: 96, weight: .semibold)
            cell("累计盈亏", width: 96, weight: .semibold)
            cell("持有收益", width: 96, weight: .semibold)
            cell("持有收益率", width: 100, weight: .semibold)
            cell("持有成本", width: 96, weight: .semibold)
            cell("持有份额", width: 96, weight: .semibold)
            cell("净值成本", width: 92, weight: .semibold)
            cell("昨日收益", width: 92, weight: .semibold)
            cell("今日收益", width: 92, weight: .semibold)
            cell("行情覆盖", width: 92, weight: .semibold)
            cell("估值日期", width: 148, alignment: .center, weight: .semibold)
        }
    }

    private func row(_ holding: UserFundHolding) -> some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text(shortFundName(holding.fundName))
                    .font(.caption.bold())
                    .foregroundStyle(.blue)
                Text(holding.fundCode)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .frame(width: 124, alignment: .leading)
            .frame(minHeight: 58)
            .padding(.horizontal, 6)

            cell(money(holding.holdingAmount), width: 100)
            cell(percent(holding.estimatedChangeRate) ?? "-", width: 96,
                 color: holding.estimatedChangeRate.map(signedValueColor) ?? .secondary, weight: .semibold)
            cell(money(holding.estimatedDailyProfit), width: 96,
                 color: holding.estimatedDailyProfit.map(signedValueColor) ?? .secondary)
            cell(money(holding.estimatedHoldingAmount), width: 108)
            cell(percent(holding.estimatedCumulativeChangeRate) ?? "-", width: 96,
                 color: holding.estimatedCumulativeChangeRate.map(signedValueColor) ?? .secondary, weight: .semibold)
            cell(money(holding.estimatedCumulativeProfit), width: 96,
                 color: holding.estimatedCumulativeProfit.map(signedValueColor) ?? .secondary)
            cell(money(holding.holdingProfit), width: 96,
                 color: holding.holdingProfit.map(signedValueColor) ?? .secondary)
            cell(percent(holding.holdingReturnRate) ?? "-", width: 100,
                 color: holding.holdingReturnRate.map(signedValueColor) ?? .secondary)
            cell(money(holding.holdingCost), width: 96)
            cell(decimal(holding.holdingShares), width: 96)
            cell(decimal(holding.costNav), width: 92)
            cell(money(holding.yesterdayProfit), width: 92,
                 color: holding.yesterdayProfit.map(signedValueColor) ?? .secondary)
            cell(money(holding.todayProfit), width: 92,
                 color: holding.todayProfit.map(signedValueColor) ?? .secondary)
            cell(percent(holding.valuationCoverageRate) ?? "-", width: 92, color: .secondary)
            cell(valuationDateTime(holding), width: 148, alignment: .center, color: .secondary)
        }
        .background(Color(.systemBackground))
    }

    private func cell(
        _ value: String,
        width: CGFloat,
        alignment: Alignment = .trailing,
        color: Color = .primary,
        weight: Font.Weight = .regular
    ) -> some View {
        Text(value)
            .font(.caption.weight(weight))
            .foregroundStyle(color)
            .lineLimit(2)
            .frame(width: width, alignment: alignment)
            .frame(minHeight: 58)
            .padding(.horizontal, 6)
    }
}

struct PortfolioHoldingDetailView: View {
    @EnvironmentObject private var session: SessionStore

    let holding: UserFundHolding

    @State private var detail: FundDetail?
    @State private var chartNavs: [FundNav] = []
    @State private var trendPeriod: TrendPeriod = .oneYear
    @State private var isLoading = false
    @State private var errorMessage: String?

    private var displayFund: Fund {
        detail?.fund ?? Fund(
            fundCode: holding.fundCode,
            fundName: holding.fundName,
            inceptionDate: nil,
            fundManager: nil,
            fundType: nil,
            managementCompany: nil,
            netAssetScale: nil,
            scaleDate: nil,
            canBuy: nil,
            createdAt: nil,
            updatedAt: nil,
            latestPerformance: nil,
            latestRating: nil,
            features: nil,
            latestValuation: nil
        )
    }

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text(holding.fundName)
                        .font(.title2.bold())
                    Text(holding.fundCode)
                        .font(.caption.bold())
                        .foregroundStyle(.blue)
                    NavigationLink {
                        ProductDetailView(fund: displayFund)
                    } label: {
                        Label("查看基金详情", systemImage: "arrow.up.right.square")
                    }
                }
                .padding(.vertical, 4)
            }

            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                }
            }

            Section("持仓数据") {
                DetailLine(title: "持有金额", value: money(holding.holdingAmount))
                SignedValueLine(title: "持有收益", value: holding.holdingProfit)
                SignedPercentLine(title: "持有收益率", value: holding.holdingReturnRate)
                DetailLine(title: "持有成本", value: money(holding.holdingCost))
                SignedValueLine(title: "昨日收益", value: holding.yesterdayProfit)
                SignedValueLine(title: "今日收益", value: holding.todayProfit)
                DetailLine(title: "持有份额", value: decimal(holding.holdingShares))
                DetailLine(title: "净值成本", value: decimal(holding.costNav))
                DetailLine(title: "估值日期", value: valuationDateTime(holding))
                DetailLine(title: "重仓报告日", value: holding.holdingReportDate)
                SignedPercentLine(title: "当日预估涨跌", value: holding.estimatedChangeRate)
                SignedValueLine(title: "预估当日盈亏", value: holding.estimatedDailyProfit)
                DetailLine(title: "估值后金额", value: money(holding.estimatedHoldingAmount))
                DetailLine(title: "预估单位净值", value: decimal(holding.estimatedUnitNav))
                SignedPercentLine(title: "累计预估涨跌", value: holding.estimatedCumulativeChangeRate)
                SignedValueLine(title: "累计预估盈亏", value: holding.estimatedCumulativeProfit)
                DetailLine(title: "行情覆盖率", value: percent(holding.valuationCoverageRate))
                DetailLine(title: "行情更新时间", value: holding.valuationUpdatedAt)
                DetailLine(title: "截图日期", value: holding.screenshotDate)
                DetailLine(title: "导入批次", value: holding.latestImportId.map(String.init))
                DetailLine(title: "导入时间", value: holding.latestImportAt)
                DetailLine(title: "记录创建时间", value: holding.createdAt)
                DetailLine(title: "记录更新时间", value: holding.updatedAt)
            }

            Section("净值与收益走势") {
                Picker("区间", selection: $trendPeriod) {
                    ForEach(TrendPeriod.allCases) { period in
                        Text(period.title).tag(period)
                    }
                }
                .pickerStyle(.segmented)

                let rows = buildTrendRows(navs: chartNavs, period: trendPeriod)
                FundTrendChart(
                    title: "净值走势图",
                    rows: rows,
                    series: [
                        TrendSeries(keyPath: \.unitNav, title: "单位净值", color: .blue),
                        TrendSeries(keyPath: \.accumulatedNav, title: "累计净值", color: .green)
                    ]
                )
                FundTrendChart(
                    title: "收益走势图",
                    rows: rows,
                    series: [
                        TrendSeries(keyPath: \.returnRate, title: "累计收益率", color: .orange, suffix: "%")
                    ]
                )
            }

            Section("基金基础信息") {
                DetailLine(title: "基金名称", value: displayFund.fundName)
                DetailLine(title: "基金代码", value: displayFund.fundCode)
                DetailLine(title: "类型", value: displayFund.fundType)
                DetailLine(title: "基金经理", value: displayFund.fundManager)
                DetailLine(title: "管理人", value: displayFund.managementCompany)
                DetailLine(title: "成立日期", value: displayFund.inceptionDate)
                DetailLine(title: "净资产规模", value: displayFund.netAssetScale)
                DetailLine(title: "规模截止", value: displayFund.scaleDate)
                DetailLine(title: "购买状态", value: displayFund.canBuy.map { $0 ? "可购买" : "不可购买" })
                DetailLine(title: "基金创建时间", value: displayFund.createdAt)
                DetailLine(title: "基金更新时间", value: displayFund.updatedAt)
            }

            Section("最新净值") {
                DetailLine(title: "净值日期", value: detail?.latestNav?.navDate)
                DetailLine(title: "单位净值", value: detail?.latestNav?.unitNav.map(String.init))
                DetailLine(title: "累计净值", value: detail?.latestNav?.accumulatedNav.map(String.init))
                SignedPercentLine(title: "日增长率", value: detail?.latestNav?.dailyGrowthRate)
            }

            Section("每日估值") {
                DetailLine(
                    title: "估值日期",
                    value: preciseValuationDate(
                        timestamp: detail?.latestValuation?.quoteUpdatedAt,
                        fallbackDate: detail?.latestValuation?.valuationDate
                    )
                )
                SignedPercentLine(title: "预估涨跌幅", value: detail?.latestValuation?.estimatedChangeRate)
                DetailLine(title: "预估单位净值", value: detail?.latestValuation?.estimatedUnitNav.map(String.init))
                DetailLine(title: "基准净值日期", value: detail?.latestValuation?.baseNavDate)
                DetailLine(title: "基准单位净值", value: detail?.latestValuation?.baseUnitNav.map(String.init))
                DetailLine(title: "重仓报告日", value: detail?.latestValuation?.holdingReportDate)
                DetailLine(title: "重仓占净值", value: percent(detail?.latestValuation?.holdingWeight))
                DetailLine(title: "有行情占净值", value: percent(detail?.latestValuation?.quotedHoldingWeight))
                DetailLine(title: "行情覆盖率", value: percent(detail?.latestValuation?.quoteCoverageRate))
                DetailLine(title: "重仓数量", value: detail?.latestValuation?.holdingCount.map(String.init))
                DetailLine(title: "有行情数量", value: detail?.latestValuation?.quotedHoldingCount.map(String.init))
                DetailLine(title: "行情更新时间", value: detail?.latestValuation?.quoteUpdatedAt)
            }

            Section("业绩表现") {
                DetailLine(title: "净值日期", value: detail?.latestPerformance?.navDate)
                SignedPercentLine(title: "近一周", value: detail?.latestPerformance?.weeklyReturnRate)
                SignedPercentLine(title: "近一月", value: detail?.latestPerformance?.monthlyReturnRate)
                SignedPercentLine(title: "近三月", value: detail?.latestPerformance?.threeMonthReturnRate)
                SignedPercentLine(title: "近六月", value: detail?.latestPerformance?.sixMonthReturnRate)
                SignedPercentLine(title: "近一年", value: detail?.latestPerformance?.oneYearReturnRate)
                SignedPercentLine(title: "近两年", value: detail?.latestPerformance?.twoYearReturnRate)
                SignedPercentLine(title: "近三年", value: detail?.latestPerformance?.threeYearReturnRate)
                SignedPercentLine(title: "今年以来", value: detail?.latestPerformance?.yearToDateReturnRate)
                SignedPercentLine(title: "成立以来", value: detail?.latestPerformance?.sinceInceptionReturnRate)
                DetailLine(title: "自定义区间", value: performanceWindow(detail?.latestPerformance))
                SignedPercentLine(title: "区间收益", value: detail?.latestPerformance?.customReturnRate)
                SignedPercentLine(title: "原手续费", value: detail?.latestPerformance?.originalFeeRate)
                SignedPercentLine(title: "折后手续费", value: detail?.latestPerformance?.discountedFeeRate)
                SignedPercentLine(title: "活期宝手续费", value: detail?.latestPerformance?.cashManagementFeeRate)
            }

            Section("最新重仓") {
                ForEach(detail?.latestHoldings ?? [], id: \.self) { item in
                    VStack(alignment: .leading, spacing: 5) {
                        Text("\(item.rankNo.map { "\($0). " } ?? "")\(item.stockName ?? item.stockCode) \(item.stockCode)")
                            .font(.subheadline.bold())
                        Text("报告 \(item.reportPeriod ?? "-") / \(item.reportDate) · 占净值 \(percent(item.netValueRatio) ?? "-") · 最新价 \(decimal(item.latestPrice)) · 涨跌 \(percent(item.changeRate) ?? "-") · 持股 \(decimal(item.holdingShares10k))万股 · 市值 \(decimal(item.holdingMarketValue10k))万元")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 3)
                }
                if detail?.latestHoldings.isEmpty ?? true {
                    DetailLine(title: "重仓", value: nil)
                }
            }

            Section("基金评级") {
                ForEach(detail?.ratings ?? [], id: \.self) { rating in
                    DetailLine(title: rating.ratingDate, value: ratingText(rating))
                }
                if detail?.ratings.isEmpty ?? true {
                    DetailLine(title: "评级", value: nil)
                }
            }

            Section("特色数据") {
                ForEach(detail?.features ?? [], id: \.self) { feature in
                    DetailLine(
                        title: "\(feature.periodLabel) \(feature.cutoffDate)",
                        value: "标准差 \(feature.standardDeviation.map(String.init) ?? "-") / 夏普 \(feature.sharpeRatio.map(String.init) ?? "-")"
                    )
                }
                if detail?.features.isEmpty ?? true {
                    DetailLine(title: "特色数据", value: nil)
                }
            }
        }
        .navigationTitle("持仓详情")
        .navigationBarTitleDisplayMode(.inline)
        .overlay {
            if isLoading {
                ProgressView()
            }
        }
        .task {
            if detail == nil {
                await loadDetail()
            }
        }
        .refreshable {
            await loadDetail()
        }
    }

    private func loadDetail() async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let detailResult = session.apiClient.fundDetail(fundCode: holding.fundCode)
            async let navResult = session.apiClient.listFundNavs(
                fundCode: holding.fundCode,
                current: 1,
                size: 1000
            )
            detail = try await detailResult
            let navPage = try await navResult
            chartNavs = navPage.records
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private func shortFundName(_ value: String) -> String {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    guard trimmed.count > 6 else {
        return trimmed.isEmpty ? "-" : trimmed
    }
    return String(trimmed.prefix(6)) + "..."
}

private func portfolioFundName(_ value: String) -> String {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? "-" : String(trimmed.prefix(8))
}

private func valuationDateTime(_ holding: UserFundHolding) -> String {
    formatDateTimeSeconds(holding.valuationUpdatedAt) ?? holding.valuationDate ?? "-"
}

private func preciseValuationDate(timestamp: String?, fallbackDate: String?) -> String? {
    formatDateTimeSeconds(timestamp) ?? fallbackDate
}

private func formatDateTimeSeconds(_ value: String?) -> String? {
    guard let value = nonEmpty(value) else {
        return nil
    }
    let normalized = value.replacingOccurrences(of: "T", with: " ")
    return String(normalized.prefix(19))
}

struct ProductListView: View {
    @EnvironmentObject private var session: SessionStore

    @State private var funds: [Fund] = []
    @State private var keyword = ""
    @State private var fundType = ""
    @State private var purchaseFilter = -1
    @State private var sortField = "fundCode"
    @State private var sortOrder = "ascend"
    @State private var currentPage = 1
    @State private var total = 0
    @State private var isLoading = false
    @State private var isLoadingMore = false
    @State private var errorMessage: String?
    @State private var navigationPath: [Fund] = []
    @State private var wasShowingDetail = false

    private let pageSize = 20
    private let fundTypes = ["", "股票型", "混合型", "债券型", "指数型", "货币型"]

    private var querySignature: String {
        "\(fundType)|\(purchaseFilter)|\(sortField)|\(sortOrder)"
    }

    var body: some View {
        NavigationStack(path: $navigationPath) {
            VStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: true) {
                    HStack(spacing: 10) {
                        Picker("基金类型", selection: $fundType) {
                            ForEach(fundTypes, id: \.self) { value in
                                Text(value.isEmpty ? "全部类型" : value).tag(value)
                            }
                        }
                        .pickerStyle(.menu)

                        Picker("购买状态", selection: $purchaseFilter) {
                            Text("全部购买状态").tag(-1)
                            Text("可购买").tag(1)
                            Text("不可购买").tag(0)
                        }
                        .pickerStyle(.menu)

                        Button("清除筛选") {
                            fundType = ""
                            purchaseFilter = -1
                            sortField = "fundCode"
                            sortOrder = "ascend"
                        }
                        .disabled(fundType.isEmpty && purchaseFilter == -1
                                  && sortField == "fundCode" && sortOrder == "ascend")
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                }

                HStack {
                    Text("已加载 \(funds.count) / \(total)")
                    Spacer()
                    Text("点击表头排序，左右滑动查看指标")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.horizontal)
                .padding(.bottom, 6)

                if let errorMessage {
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .padding(.horizontal)
                }

                FundSpreadsheet(
                    funds: funds,
                    sortField: sortField,
                    sortOrder: sortOrder,
                    isLoadingMore: isLoadingMore,
                    onSort: changeSort,
                    onLastRowAppear: loadMoreIfNeeded
                )
            }
            .overlay {
                if isLoading && funds.isEmpty {
                    ProgressView("加载产品")
                } else if !isLoading && funds.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "tablecells")
                            .font(.system(size: 42))
                            .foregroundStyle(.secondary)
                        Text("暂无产品")
                            .font(.headline)
                        Text("尝试调整搜索或筛选条件")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .searchable(text: $keyword, prompt: "搜索基金代码、名称或经理")
            .onSubmit(of: .search) { Task { await reload() } }
            .refreshable { await reload() }
            .navigationTitle("产品")
            .navigationDestination(for: Fund.self) { fund in
                ProductDetailView(fund: fund)
            }
            .onChange(of: querySignature) { _ in
                Task { await reload() }
            }
            .onChange(of: navigationPath) { newPath in
                if newPath.isEmpty && wasShowingDetail {
                    wasShowingDetail = false
                    Task { await reload() }
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

    private func changeSort(_ field: String, _ defaultOrder: String) {
        if sortField == field {
            sortOrder = sortOrder == "ascend" ? "descend" : "ascend"
        } else {
            sortField = field
            sortOrder = defaultOrder
        }
    }

    private func reload() async {
        currentPage = 1
        total = 0
        funds = []
        await load(page: 1)
    }

    private func loadMoreIfNeeded() {
        guard funds.count < total, !isLoadingMore, !isLoading else { return }
        Task { await load(page: currentPage + 1) }
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
            let result = try await session.apiClient.listFunds(
                current: page,
                size: pageSize,
                keyword: keyword,
                fundType: fundType.isEmpty ? nil : fundType,
                canBuy: purchaseFilter < 0 ? nil : purchaseFilter == 1,
                sortField: sortField,
                sortOrder: sortOrder
            )
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

private struct FundSpreadsheet: View {
    let funds: [Fund]
    let sortField: String
    let sortOrder: String
    let isLoadingMore: Bool
    let onSort: (String, String) -> Void
    let onLastRowAppear: () -> Void

    private let headerHeight: CGFloat = 46
    private let rowHeight: CGFloat = 64

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            HStack(alignment: .top, spacing: 0) {
                frozenColumns
                    .background(Color(.systemBackground))
                    .shadow(color: .black.opacity(0.08), radius: 2, x: 2)

                ScrollView(.horizontal, showsIndicators: true) {
                    metricColumns
                }
            }
        }
    }

    private var frozenColumns: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                sortableHeader("基金名称", field: "fundName", defaultOrder: "ascend",
                               width: 150, alignment: .leading)
                sortableHeader("代码", field: "fundCode", defaultOrder: "ascend",
                               width: 88, alignment: .leading)
            }
            ForEach(Array(funds.enumerated()), id: \.element.id) { index, fund in
                NavigationLink(value: fund) {
                    HStack(spacing: 0) {
                        cell(fund.fundName, width: 150, alignment: .leading,
                             color: .blue, weight: .semibold)
                        cell(fund.fundCode, width: 88, alignment: .leading,
                             color: .secondary, weight: .semibold)
                    }
                    .background(rowBackground(index))
                    .onAppear {
                        if fund.id == funds.last?.id { onLastRowAppear() }
                    }
                }
                .buttonStyle(.plain)
            }
            if isLoadingMore {
                ProgressView().frame(width: 238, height: rowHeight)
            }
        }
    }

    private var metricColumns: some View {
        VStack(alignment: .leading, spacing: 0) {
            metricHeader
            ForEach(Array(funds.enumerated()), id: \.element.id) { index, fund in
                NavigationLink(value: fund) {
                    metricRow(fund)
                        .background(rowBackground(index))
                }
                .buttonStyle(.plain)
            }
            if isLoadingMore {
                ProgressView().frame(width: 2940, height: rowHeight)
            }
        }
    }

    private var metricHeader: some View {
        HStack(spacing: 0) {
            sortableHeader("可购买", field: "canBuy", defaultOrder: "descend", width: 84)
            plainHeader("当日预估", width: 96)
            plainHeader("估值日期", width: 148, alignment: .center)
            sortableHeader("类型", field: "fundType", defaultOrder: "ascend", width: 100)
            sortableHeader("基金经理", field: "fundManager", defaultOrder: "ascend", width: 120)
            sortableHeader("管理人", field: "managementCompany", defaultOrder: "ascend", width: 180)
            sortableHeader("规模", field: "netAssetScale", defaultOrder: "descend", width: 120)
            sortableHeader("成立日期", field: "inceptionDate", defaultOrder: "descend", width: 110)
            sortableHeader("招商评级", field: "zhaoshangRating", defaultOrder: "descend", width: 100)
            sortableHeader("晨星评级", field: "morningStarRating", defaultOrder: "descend", width: 100)
            performanceHeaders
            sortableHeader("标准差", field: "standardDeviation", defaultOrder: "ascend", width: 150)
            sortableHeader("夏普比率", field: "sharpeRatio", defaultOrder: "descend", width: 150)
        }
        .background(Color(.secondarySystemGroupedBackground))
    }

    private var performanceHeaders: some View {
        Group {
            sortableHeader("近一周", field: "weeklyReturnRate", defaultOrder: "descend", width: 96)
            sortableHeader("近一月", field: "monthlyReturnRate", defaultOrder: "descend", width: 96)
            sortableHeader("近三月", field: "threeMonthReturnRate", defaultOrder: "descend", width: 96)
            sortableHeader("近六月", field: "sixMonthReturnRate", defaultOrder: "descend", width: 96)
            sortableHeader("近一年", field: "oneYearReturnRate", defaultOrder: "descend", width: 96)
            sortableHeader("近两年", field: "twoYearReturnRate", defaultOrder: "descend", width: 96)
            sortableHeader("近三年", field: "threeYearReturnRate", defaultOrder: "descend", width: 96)
            sortableHeader("今年以来", field: "yearToDateReturnRate", defaultOrder: "descend", width: 100)
            sortableHeader("成立以来", field: "sinceInceptionReturnRate", defaultOrder: "descend", width: 100)
            sortableHeader("区间收益", field: "customReturnRate", defaultOrder: "descend", width: 96)
            sortableHeader("原手续费", field: "originalFeeRate", defaultOrder: "ascend", width: 96)
            sortableHeader("折后手续费", field: "discountedFeeRate", defaultOrder: "ascend", width: 104)
            sortableHeader("活期宝手续费", field: "cashManagementFeeRate", defaultOrder: "ascend", width: 116)
        }
    }

    private func metricRow(_ fund: Fund) -> some View {
        HStack(spacing: 0) {
            cell(fund.canBuy == true ? "可购" : "不可购", width: 84,
                 color: fund.canBuy == true ? .green : .secondary, weight: .semibold)
            percentCell(fund.latestValuation?.estimatedChangeRate, width: 96)
            cell(preciseValuationDate(timestamp: fund.latestValuation?.quoteUpdatedAt,
                                      fallbackDate: fund.latestValuation?.valuationDate) ?? "-",
                 width: 148, alignment: .center, color: .secondary)
            cell(fund.fundType ?? "-", width: 100)
            cell(fund.fundManager ?? "-", width: 120)
            cell(fund.managementCompany ?? "-", width: 180, alignment: .leading)
            cell(fund.netAssetScale ?? "-", width: 120)
            cell(fund.inceptionDate ?? "-", width: 110, alignment: .center)
            cell(ratingStars(fund.latestRating?.zhaoshangRating), width: 100, color: .orange)
            cell(ratingStars(fund.latestRating?.morningStarRating), width: 100, color: .orange)
            performanceCells(fund.latestPerformance)
            cell(featureSummary(fund.features, keyPath: \.standardDeviation), width: 150)
            cell(featureSummary(fund.features, keyPath: \.sharpeRatio), width: 150)
        }
    }

    private func performanceCells(_ value: FundPerformance?) -> some View {
        Group {
            percentCell(value?.weeklyReturnRate, width: 96)
            percentCell(value?.monthlyReturnRate, width: 96)
            percentCell(value?.threeMonthReturnRate, width: 96)
            percentCell(value?.sixMonthReturnRate, width: 96)
            percentCell(value?.oneYearReturnRate, width: 96)
            percentCell(value?.twoYearReturnRate, width: 96)
            percentCell(value?.threeYearReturnRate, width: 96)
            percentCell(value?.yearToDateReturnRate, width: 100)
            percentCell(value?.sinceInceptionReturnRate, width: 100)
            percentCell(value?.customReturnRate, width: 96)
            percentCell(value?.originalFeeRate, width: 96)
            percentCell(value?.discountedFeeRate, width: 104)
            percentCell(value?.cashManagementFeeRate, width: 116)
        }
    }

    private func sortableHeader(_ title: String, field: String, defaultOrder: String,
                                width: CGFloat, alignment: Alignment = .trailing) -> some View {
        Button {
            onSort(field, defaultOrder)
        } label: {
            headerText(title + sortIndicator(field), width: width, alignment: alignment)
        }
        .buttonStyle(.plain)
    }

    private func plainHeader(_ title: String, width: CGFloat,
                             alignment: Alignment = .trailing) -> some View {
        headerText(title, width: width, alignment: alignment)
    }

    private func headerText(_ value: String, width: CGFloat, alignment: Alignment) -> some View {
        Text(value)
            .font(.caption.weight(.semibold))
            .lineLimit(2)
            .frame(width: width, height: headerHeight, alignment: alignment)
            .padding(.horizontal, 6)
            .background(Color(.secondarySystemGroupedBackground))
    }

    private func cell(_ value: String, width: CGFloat, alignment: Alignment = .trailing,
                      color: Color = .primary, weight: Font.Weight = .regular) -> some View {
        Text(value)
            .font(.caption.weight(weight))
            .foregroundStyle(color)
            .lineLimit(2)
            .frame(width: width, height: rowHeight, alignment: alignment)
            .padding(.horizontal, 6)
    }

    private func percentCell(_ value: Decimal?, width: CGFloat) -> some View {
        cell(percent(value) ?? "-", width: width,
             color: value.map(signedValueColor) ?? .secondary,
             weight: .semibold)
    }

    private func sortIndicator(_ field: String) -> String {
        guard sortField == field else { return "" }
        return sortOrder == "ascend" ? " ↑" : " ↓"
    }

    private func rowBackground(_ index: Int) -> Color {
        index.isMultiple(of: 2) ? Color(.systemBackground) : Color(.secondarySystemBackground)
    }

    private func featureSummary(_ features: [FundFeature]?,
                                keyPath: KeyPath<FundFeature, Decimal?>) -> String {
        guard let features, !features.isEmpty else { return "-" }
        return features.map { feature in
            let value = feature[keyPath: keyPath].map { NSDecimalNumber(decimal: $0).stringValue } ?? "-"
            return "\(feature.periodLabel):\(value)"
        }.joined(separator: " / ")
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
                DetailLine(title: "购买状态", value: displayFund.canBuy == true ? "可购买" : "不可购买")
            }

            Section("最新净值") {
                DetailLine(title: "净值日期", value: detail?.latestNav?.navDate)
                DetailLine(title: "单位净值", value: detail?.latestNav?.unitNav.map(String.init))
                DetailLine(title: "累计净值", value: detail?.latestNav?.accumulatedNav.map(String.init))
                SignedPercentLine(title: "日增长率", value: detail?.latestNav?.dailyGrowthRate)
            }

            Section("每日估值") {
                DetailLine(
                    title: "估值日期",
                    value: preciseValuationDate(
                        timestamp: detail?.latestValuation?.quoteUpdatedAt,
                        fallbackDate: detail?.latestValuation?.valuationDate
                    )
                )
                SignedPercentLine(title: "预估涨跌幅", value: detail?.latestValuation?.estimatedChangeRate)
                DetailLine(title: "预估单位净值", value: detail?.latestValuation?.estimatedUnitNav.map(String.init))
                DetailLine(title: "基准净值日期", value: detail?.latestValuation?.baseNavDate)
                DetailLine(title: "重仓报告日", value: detail?.latestValuation?.holdingReportDate)
                DetailLine(title: "重仓占净值", value: percent(detail?.latestValuation?.holdingWeight))
                DetailLine(title: "行情覆盖率", value: percent(detail?.latestValuation?.quoteCoverageRate))
                DetailLine(title: "行情更新时间", value: detail?.latestValuation?.quoteUpdatedAt)
            }

            Section("业绩表现") {
                SignedPercentLine(title: "近一周", value: detail?.latestPerformance?.weeklyReturnRate)
                SignedPercentLine(title: "近一月", value: detail?.latestPerformance?.monthlyReturnRate)
                SignedPercentLine(title: "近三月", value: detail?.latestPerformance?.threeMonthReturnRate)
                SignedPercentLine(title: "近六月", value: detail?.latestPerformance?.sixMonthReturnRate)
                SignedPercentLine(title: "近一年", value: detail?.latestPerformance?.oneYearReturnRate)
                SignedPercentLine(title: "近两年", value: detail?.latestPerformance?.twoYearReturnRate)
                SignedPercentLine(title: "近三年", value: detail?.latestPerformance?.threeYearReturnRate)
                SignedPercentLine(title: "今年以来", value: detail?.latestPerformance?.yearToDateReturnRate)
                SignedPercentLine(title: "成立以来", value: detail?.latestPerformance?.sinceInceptionReturnRate)
                DetailLine(title: "自定义区间", value: performanceWindow(detail?.latestPerformance))
                SignedPercentLine(title: "区间收益", value: detail?.latestPerformance?.customReturnRate)
                SignedPercentLine(title: "原手续费", value: detail?.latestPerformance?.originalFeeRate)
                SignedPercentLine(title: "折后手续费", value: detail?.latestPerformance?.discountedFeeRate)
                SignedPercentLine(title: "活期宝手续费", value: detail?.latestPerformance?.cashManagementFeeRate)
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
                    SignedPercentLine(title: holding.stockName ?? holding.stockCode, value: holding.netValueRatio)
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

private func percent(_ value: Decimal?) -> String? {
    value.map { "\($0)%" }
}

private struct PerformanceValuesView: View {
    let value: FundPerformance

    var body: some View {
        VStack(spacing: 2) {
            SignedPercentLine(title: "近一周", value: value.weeklyReturnRate)
            SignedPercentLine(title: "近一月", value: value.monthlyReturnRate)
            SignedPercentLine(title: "近三月", value: value.threeMonthReturnRate)
            SignedPercentLine(title: "近六月", value: value.sixMonthReturnRate)
            SignedPercentLine(title: "近一年", value: value.oneYearReturnRate)
            SignedPercentLine(title: "近两年", value: value.twoYearReturnRate)
            SignedPercentLine(title: "近三年", value: value.threeYearReturnRate)
            SignedPercentLine(title: "今年以来", value: value.yearToDateReturnRate)
            SignedPercentLine(title: "成立以来", value: value.sinceInceptionReturnRate)
            SignedPercentLine(title: "区间收益", value: value.customReturnRate)
            SignedPercentLine(title: "原手续费", value: value.originalFeeRate)
            SignedPercentLine(title: "折后手续费", value: value.discountedFeeRate)
            SignedPercentLine(title: "活期宝手续费", value: value.cashManagementFeeRate)
        }.font(.caption)
    }
}

private func performanceWindow(_ value: FundPerformance?) -> String? {
    guard let value else { return nil }
    return "\(value.customStartDate) 至 \(value.customEndDate)"
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

private struct SignedPercentLine: View {
    let title: String
    let value: Decimal?

    var body: some View {
        HStack(alignment: .top) {
            Text(title).foregroundStyle(.secondary)
            Spacer()
            Text(percent(value) ?? "-")
                .multilineTextAlignment(.trailing)
                .foregroundStyle(value.map(signedValueColor) ?? .secondary)
        }
    }
}

private struct SignedValueLine: View {
    let title: String
    let value: Decimal?

    var body: some View {
        HStack(alignment: .top) {
            Text(title).foregroundStyle(.secondary)
            Spacer()
            Text(money(value))
                .multilineTextAlignment(.trailing)
                .foregroundStyle(value.map(signedValueColor) ?? .secondary)
        }
    }
}

private func signedValueColor(_ value: Decimal) -> Color {
    value > 0 ? .red : value < 0 ? .green : .primary
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
