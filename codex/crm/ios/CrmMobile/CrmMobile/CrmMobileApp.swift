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
    Text(number.map { String(format: "%.2f", $0) } ?? "-")
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
    @State private var selectedItems: [PhotosPickerItem] = []
    @State private var preview: PortfolioHoldingImportPreview?
    @State private var holdings: [UserFundHolding] = []
    @State private var imports: [PortfolioHoldingBatch] = []
    @State private var keyword = ""
    @State private var loading = false
    @State private var uploading = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            List {
                Section {
                    PhotosPicker(selection: $selectedItems, maxSelectionCount: 3, matching: .images) {
                        Label("选择支付宝持仓截图", systemImage: "photo.on.rectangle")
                    }
                    Button(uploading ? "上传中…" : "上传并识别") {
                        Task { await uploadSelectedImages() }
                    }
                    .disabled(selectedItems.isEmpty || uploading)
                    if let preview {
                        Text("来源 \(preview.sourceLabel) · \(preview.imageCount) 张 · \(preview.screenshotDate ?? "-")")
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

                if let preview {
                    Section("识别预览") {
                        ForEach(Array(preview.rows.enumerated()), id: \.offset) { index, row in
                            PortfolioHoldingRowEditor(row: bindingForPreviewRow(index), isEditable: preview.status == "PREVIEWED", onFundCodeChange: { code in
                                updatePreviewRow(index) { $0.fundCode = code }
                            })
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
                    ForEach(imports) { item in
                        Button {
                            Task { await openImport(item.id) }
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text("批次 \(item.id)").font(.headline)
                                    Spacer()
                                    Text(item.status).font(.caption).foregroundStyle(.secondary)
                                }
                                Text("截图 \(item.screenshotDate ?? "-") · \(item.itemCount) 条")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("持仓导入")
            .task {
                if holdings.isEmpty {
                    await loadHoldings()
                }
                if imports.isEmpty {
                    await loadImports()
                }
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

    private func loadHoldings() async {
        loading = true
        defer { loading = false }
        do {
            holdings = try await session.apiClient.listPortfolioHoldings(current: 1, size: 100, keyword: keyword).records
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
            preview = try await session.apiClient.previewPortfolioHoldings(images: images)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func confirmPreview() async {
        guard let preview else { return }
        guard preview.rows.allSatisfy({ !($0.fundCode ?? "").isEmpty }) else {
            error = "请为每条识别结果选择基金代码"
            return
        }
        uploading = true
        defer { uploading = false }
        do {
            try await session.apiClient.confirmPortfolioHoldingImport(
                importId: preview.importId,
                request: PortfolioHoldingConfirmRequest(
                    screenshotDate: preview.screenshotDate,
                    items: preview.rows.map {
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
                    }
                )
            )
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
                Text("金额 \(decimal(row.holdingAmount))")
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

            cell(decimal(holding.holdingAmount), width: 100)
            cell(percent(holding.estimatedChangeRate) ?? "-", width: 96,
                 color: holding.estimatedChangeRate.map(signedValueColor) ?? .secondary, weight: .semibold)
            cell(decimal(holding.estimatedDailyProfit), width: 96,
                 color: holding.estimatedDailyProfit.map(signedValueColor) ?? .secondary)
            cell(decimal(holding.estimatedHoldingAmount), width: 108)
            cell(percent(holding.estimatedCumulativeChangeRate) ?? "-", width: 96,
                 color: holding.estimatedCumulativeChangeRate.map(signedValueColor) ?? .secondary, weight: .semibold)
            cell(decimal(holding.estimatedCumulativeProfit), width: 96,
                 color: holding.estimatedCumulativeProfit.map(signedValueColor) ?? .secondary)
            cell(decimal(holding.holdingProfit), width: 96,
                 color: holding.holdingProfit.map(signedValueColor) ?? .secondary)
            cell(percent(holding.holdingReturnRate) ?? "-", width: 100,
                 color: holding.holdingReturnRate.map(signedValueColor) ?? .secondary)
            cell(decimal(holding.holdingCost), width: 96)
            cell(decimal(holding.holdingShares), width: 96)
            cell(decimal(holding.costNav), width: 92)
            cell(decimal(holding.yesterdayProfit), width: 92,
                 color: holding.yesterdayProfit.map(signedValueColor) ?? .secondary)
            cell(decimal(holding.todayProfit), width: 92,
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
                DetailLine(title: "持有金额", value: decimal(holding.holdingAmount))
                SignedValueLine(title: "持有收益", value: holding.holdingProfit)
                SignedPercentLine(title: "持有收益率", value: holding.holdingReturnRate)
                DetailLine(title: "持有成本", value: decimal(holding.holdingCost))
                SignedValueLine(title: "昨日收益", value: holding.yesterdayProfit)
                SignedValueLine(title: "今日收益", value: holding.todayProfit)
                DetailLine(title: "持有份额", value: decimal(holding.holdingShares))
                DetailLine(title: "净值成本", value: decimal(holding.costNav))
                DetailLine(title: "估值日期", value: valuationDateTime(holding))
                DetailLine(title: "重仓报告日", value: holding.holdingReportDate)
                SignedPercentLine(title: "当日预估涨跌", value: holding.estimatedChangeRate)
                SignedValueLine(title: "预估当日盈亏", value: holding.estimatedDailyProfit)
                DetailLine(title: "估值后金额", value: decimal(holding.estimatedHoldingAmount))
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
                Text(fund.canBuy == true ? "可购" : "不可购")
                    .font(.caption.bold())
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background((fund.canBuy == true ? Color.green : Color.gray).opacity(0.12), in: Capsule())
                    .foregroundStyle(fund.canBuy == true ? Color.green : Color.secondary)
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
            Text("规模：\(fund.netAssetScale ?? "-")")
                .font(.caption).foregroundStyle(.secondary)
            Text("招商：\(ratingStars(fund.latestRating?.zhaoshangRating))  晨星：\(ratingStars(fund.latestRating?.morningStarRating))")
                .font(.caption).foregroundStyle(.secondary)
            if let valuation = fund.latestValuation {
                SignedPercentLine(title: "当日预估", value: valuation.estimatedChangeRate)
                Text("估值日期 \(preciseValuationDate(timestamp: valuation.quoteUpdatedAt, fallbackDate: valuation.valuationDate) ?? "-") · 行情覆盖 \(percent(valuation.quoteCoverageRate) ?? "-")")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if let performance = fund.latestPerformance {
                PerformanceValuesView(value: performance)
            }
            if let features = fund.features, !features.isEmpty {
                Text(features.map { "\($0.periodLabel) 标准差:\($0.standardDeviation.map(String.init) ?? "-") 夏普:\($0.sharpeRatio.map(String.init) ?? "-")" }.joined(separator: " / "))
                    .font(.caption).foregroundStyle(.secondary)
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
            Text(decimal(value))
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
