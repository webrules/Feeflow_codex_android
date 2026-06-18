import Foundation
import OSLog

enum AppLogger {
    private static let logger = Logger(subsystem: "com.feedflow.app", category: "Feedflow")

    nonisolated static func debug(_ message: @autoclosure () -> String) {
        #if DEBUG
        let value = message()
        logger.debug("\(value, privacy: .public)")
        #endif
    }

    nonisolated static func error(_ message: @autoclosure () -> String) {
        #if DEBUG
        let value = message()
        logger.error("\(value, privacy: .public)")
        #endif
    }

    nonisolated static func redactedList(count: Int) -> String {
        count == 1 ? "<1 item redacted>" : "<\(count) items redacted>"
    }
}
