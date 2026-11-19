import XCTest

final class HandyCodeUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testFourTabsAndThemeToggleInSimulator() throws {
        let app = XCUIApplication()
        app.launch()

        let tabCainiao = app.buttons["tab_0"]
        let tabTaobao = app.buttons["tab_1"]
        let tabPdd = app.buttons["tab_2"]
        let tabJd = app.buttons["tab_3"]
        let themeToggle = app.otherElements["theme_toggle"].exists ? app.otherElements["theme_toggle"] : app.buttons["theme_toggle"]

        XCTAssertTrue(tabCainiao.waitForExistence(timeout: 5))
        XCTAssertTrue(tabTaobao.exists)
        XCTAssertTrue(tabPdd.exists)
        XCTAssertTrue(tabJd.exists)

        attachScreenshot(name: "01-initial-taobao")

        tabJd.tap()
        sleep(1)
        attachScreenshot(name: "02-jd-tab")

        tabPdd.tap()
        sleep(1)
        attachScreenshot(name: "03-pdd-tab")

        tabCainiao.tap()
        sleep(1)
        attachScreenshot(name: "04-cainiao-tab")

        if themeToggle.exists {
            themeToggle.tap()
            sleep(1)
            attachScreenshot(name: "05-theme-toggled")
        }
    }

    private func attachScreenshot(name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
