package com.souhenriza.sefazba.shared.enums;

public enum PreferBrowser {
    CHROME("chrome"),
    FIREFOX("firefox"),
    MSEDGE("msedge");

    private final String browserValue;

    PreferBrowser(String browserValue) {
        this.browserValue = browserValue;
    }

    public String getBrowserValue() {
        return browserValue;
    }

    public static PreferBrowser of(String browserValue) {
        for (PreferBrowser browser : PreferBrowser.values()) {
            if (browserValue.equalsIgnoreCase(browser.browserValue)) {
                return browser;
            }
        }
        throw new IllegalArgumentException("Unknown browser: " + browserValue);
    }
}
