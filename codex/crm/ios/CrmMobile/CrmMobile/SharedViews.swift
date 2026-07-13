import SwiftUI

struct StatusBadge: View {
    let text: String?

    var body: some View {
        Text(label)
            .font(.caption.bold())
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.14), in: Capsule())
            .foregroundStyle(color)
    }

    private var label: String {
        switch text {
        case "DEAL":
            return "成交"
        case "POTENTIAL", nil, "":
            return "潜在"
        default:
            return text ?? "潜在"
        }
    }

    private var color: Color {
        switch text {
        case "DEAL":
            return .green
        default:
            return .orange
        }
    }
}

func nonEmpty(_ value: String?) -> String? {
    let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed?.isEmpty == false ? trimmed : nil
}
