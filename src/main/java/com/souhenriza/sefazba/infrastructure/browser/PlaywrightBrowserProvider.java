package com.souhenriza.sefazba.infrastructure.browser;

import com.souhenriza.sefazba.shared.enums.PreferBrowser;
import com.microsoft.playwright.*;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class PlaywrightBrowserProvider {

    private final BrowserLockManager lockManager;

    private final boolean headless;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    @Getter
    private PreferBrowser currentType;
    @Setter
    private PreferBrowser defaultBrowser = PreferBrowser.FIREFOX;
    private final Map<String, Object> firefoxUserPrefs =
            Map.of(
                    "security.osclientcerts.autoload", false,
                    "browser.formfill.enable", false
            );

    public Page acquirePage() {
        return acquirePage(defaultBrowser);
    }

    public Page acquirePage(PreferBrowser preferBrowser) {
        if (playwright == null) {
            playwright = Playwright.create();
        }

        if (browser == null || !browser.isConnected() || currentType != preferBrowser) {
            closeContext();
            closeBrowser();
            browser = launchBrowser(preferBrowser);
            currentType = preferBrowser;
        }

        if (context == null || page == null || page.isClosed()) {
            closeContext();
            context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setAcceptDownloads(true)
                            .setIgnoreHTTPSErrors(true)
            );
            page = context.newPage();
        }

        PlaywrightAssertions.setDefaultAssertionTimeout(10000);
        return page;
    }

    public void releasePage(Page page) {
        if (page != null && page == this.page) {
            closeContext();
        }
    }

    private void closeContext() {
        if (page != null && !page.isClosed()) {
            page.close();
        }
        page = null;
        if (context != null) {
            context.close();
        }
        context = null;
    }

    private Browser launchBrowser(PreferBrowser preferBrowser) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setFirefoxUserPrefs(firefoxUserPrefs);

        Browser launched = switch (preferBrowser) {
            case FIREFOX -> playwright.firefox().launch(options);
            case CHROME, MSEDGE -> playwright.chromium().launch();


        };

        launched.onDisconnected(this::onBrowserClosed);
        return launched;
    }

    private void onBrowserClosed(Browser closedBrowser) {
        log.warn("Browser disconnected — releasing extraction lock so pending requests can proceed");
        page = null;
        context = null;
        if (browser == closedBrowser) {
            browser = null;
        }
        lockManager.forceRelease();
    }

    private void closeBrowser() {
        if (browser != null && browser.isConnected()) {
            browser.close();
            browser = null;
        }
    }

    public void shutdown() {
        closeContext();
        closeBrowser();
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}
