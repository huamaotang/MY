import SwiftUI

struct CustomerListView: View {
    @EnvironmentObject private var session: SessionStore

    @State private var customers: [Customer] = []
    @State private var keyword = ""
    @State private var currentPage = 1
    @State private var total = 0
    @State private var isLoading = false
    @State private var isLoadingMore = false
    @State private var errorMessage: String?
    @State private var navigationPath: [Customer] = []
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

                ForEach(customers) { customer in
                    NavigationLink(value: customer) {
                        CustomerRow(customer: customer)
                            .onAppear {
                                loadMoreIfNeeded(current: customer)
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
                if isLoading && customers.isEmpty {
                    ProgressView("加载客户")
                } else if !isLoading && customers.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "person.3")
                            .font(.system(size: 42))
                            .foregroundStyle(.secondary)
                        Text("暂无客户")
                            .font(.headline)
                        Text("尝试调整搜索关键词")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .searchable(text: $keyword, prompt: "搜索客户名称")
            .onSubmit(of: .search) {
                Task {
                    await reload()
                }
            }
            .refreshable {
                await reload()
            }
            .navigationTitle("客户")
            .navigationDestination(for: Customer.self) { customer in
                CustomerDetailView(customer: customer)
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
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button("刷新") {
                            Task {
                                await reload()
                            }
                        }
                        Button("退出登录", role: .destructive) {
                            session.logout()
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .task {
                if customers.isEmpty {
                    await reload()
                }
            }
        }
    }

    private func reload() async {
        currentPage = 1
        total = 0
        customers = []
        await load(page: 1)
    }

    private func loadMoreIfNeeded(current customer: Customer) {
        guard customer.id == customers.last?.id else {
            return
        }
        guard customers.count < total, !isLoadingMore, !isLoading else {
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
            let result = try await session.apiClient.listCustomers(current: page, size: pageSize, keyword: keyword)
            currentPage = result.current
            total = result.total
            if page == 1 {
                customers = result.records
            } else {
                customers.append(contentsOf: result.records)
            }
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct CustomerRow: View {
    let customer: Customer

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                Text(customer.customerName)
                    .font(.headline)
                    .lineLimit(2)
                Spacer()
                StatusBadge(text: customer.status)
            }

            HStack(spacing: 10) {
                if let industry = nonEmpty(customer.industry) {
                    Label(industry, systemImage: "building.2")
                }
                if let city = nonEmpty(customer.city) {
                    Label(city, systemImage: "mappin.and.ellipse")
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            if let phone = nonEmpty(customer.phone) {
                Label(phone, systemImage: "phone")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 6)
    }
}
