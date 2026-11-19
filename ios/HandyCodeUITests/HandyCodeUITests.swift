import XCTest

final class HandyCodeUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testFourTabsAndThemeToggleInSimulator() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ui_test_reset"]
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))

        let tabCainiao = app.buttons["tab_0"]
        let tabTaobao = app.buttons["tab_1"]
        let tabPdd = app.buttons["tab_2"]
        let tabJd = app.buttons["tab_3"]
        let themeToggle = app.buttons["theme_toggle"].exists ? app.buttons["theme_toggle"] : app.otherElements["theme_toggle"]

        XCTAssertTrue(tabCainiao.waitForExistence(timeout: 15))
        XCTAssertTrue(tabTaobao.exists)
        XCTAssertTrue(tabPdd.exists)
        XCTAssertTrue(tabJd.exists)

        attachScreenshot(name: "01-initial-taobao")

        tabJd.tap()
        sleep(2)
        attachScreenshot(name: "02-jd-tab")

        tabPdd.tap()
        sleep(2)
        attachScreenshot(name: "03-pdd-tab")

        tabCainiao.tap()
        sleep(2)
        attachScreenshot(name: "04-cainiao-tab")

        if themeToggle.waitForExistence(timeout: 5) {
            themeToggle.tap()
            sleep(1)
            attachScreenshot(name: "05-theme-toggled")
        } else {
            XCTFail("theme_toggle button not found")
        }
    }

    private func attachScreenshot(name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
