import SwiftUI

struct CustomerDetailView: View {
    @EnvironmentObject private var session: SessionStore

    let customer: Customer

    @State private var detail: Customer?
    @State private var isLoading = false
    @State private var errorMessage: String?

    private var displayCustomer: Customer {
        detail ?? customer
    }

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 10) {
                    Text(displayCustomer.customerName)
                        .font(.title2.bold())
                    HStack {
                        StatusBadge(text: displayCustomer.status)
                        if let level = nonEmpty(displayCustomer.level) {
                            Text(level)
                                .font(.caption.bold())
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(.blue.opacity(0.12), in: Capsule())
                                .foregroundStyle(.blue)
                        }
                    }
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
                DetailRow(title: "行业", value: displayCustomer.industry)
                DetailRow(title: "客户类型", value: displayCustomer.customerType)
                DetailRow(title: "来源", value: displayCustomer.source)
                DetailRow(title: "负责人 ID", value: displayCustomer.ownerUserId.map(String.init))
            }

            Section("联系方式") {
                DetailRow(title: "电话", value: displayCustomer.phone)
                DetailRow(title: "邮箱", value: displayCustomer.email)
                DetailRow(title: "省份", value: displayCustomer.province)
                DetailRow(title: "城市", value: displayCustomer.city)
                DetailRow(title: "地址", value: displayCustomer.address)
            }

            Section("备注") {
                Text(nonEmpty(displayCustomer.remark) ?? "-")
                    .foregroundStyle(nonEmpty(displayCustomer.remark) == nil ? .secondary : .primary)
            }

            Section("时间") {
                DetailRow(title: "创建时间", value: displayCustomer.createdAt)
                DetailRow(title: "更新时间", value: displayCustomer.updatedAt)
            }
        }
        .overlay {
            if isLoading {
                ProgressView()
            }
        }
        .navigationTitle("客户详情")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadDetail()
        }
        .refreshable {
            await loadDetail()
        }
    }

    private func loadDetail() async {
        guard let id = customer.id else {
            return
        }
        isLoading = true
        defer { isLoading = false }
        do {
            detail = try await session.apiClient.customerDetail(id: id)
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct DetailRow: View {
    let title: String
    let value: String?

    var body: some View {
        HStack(alignment: .top) {
            Text(title)
                .foregroundStyle(.secondary)
                .frame(width: 86, alignment: .leading)
            Spacer()
            Text(nonEmpty(value) ?? "-")
                .multilineTextAlignment(.trailing)
                .foregroundStyle(nonEmpty(value) == nil ? .secondary : .primary)
        }
    }
}
