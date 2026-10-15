package com.souhenriza.sefazba.infrastructure.config;

import ch.qos.logback.core.PropertyDefinerBase;

/**
 * Gives logback.xml the logs folder inside the data folder. Logback starts before main() runs, so the folder is
 * computed here rather than passed in. -Dlog.dir still overrides it.
 */
public class LogDirDefiner extends PropertyDefinerBase {

    @Override
    public String getPropertyValue() {
        String override = System.getProperty("log.dir");
        return override != null ? override : AppConfig.defaultBaseDir().resolve("logs").toString();
    }
}
