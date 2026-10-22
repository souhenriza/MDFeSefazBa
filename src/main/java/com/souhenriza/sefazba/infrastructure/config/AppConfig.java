package com.souhenriza.sefazba.infrastructure.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class AppConfig {

    private static final String FILE_NAME = "configs.yaml";
    private static final String HOME_VARIABLE = "SEFAZBA_HOME";
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    private static final String BUNDLED_TESSDATA_PROPERTY = "sefazba.tessdata";

    private final Path baseDir;
    private final Map<String, Object> root;
    private final boolean firstRun;

    private AppConfig(Path baseDir, Map<String, Object> root, boolean firstRun) {
        this.baseDir = baseDir;
        this.root = root != null ? root : Map.of();
        this.firstRun = firstRun;
    }

    public static AppConfig load() {
        Path baseDir = defaultBaseDir();
        Path external = baseDir.resolve(FILE_NAME);
        try {
            boolean firstRun = !Files.isRegularFile(external);
            if (firstRun) {
                Files.createDirectories(baseDir);
                try (InputStream bundled = AppConfig.class.getClassLoader().getResourceAsStream(FILE_NAME)) {
                    if (bundled == null) {
                        throw new IllegalStateException(FILE_NAME + " not found in the classpath");
                    }
                    Files.copy(bundled, external);
                }
            }
            try (InputStream in = Files.newInputStream(external)) {
                return new AppConfig(baseDir, new Yaml().load(in), firstRun);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + external, e);
        }
    }

    public static Path defaultBaseDir() {
        String home = System.getenv(HOME_VARIABLE);
        if (home != null && !home.isBlank()) {
            return Path.of(home).toAbsolutePath();
        }
        if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
            String appData = System.getenv("APPDATA");
            Path parent = appData != null ? Path.of(appData)
                    : Path.of(System.getProperty("user.home"), "AppData", "Roaming");
            return parent.resolve("SefazBa");
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        Path parent = xdgDataHome != null && !xdgDataHome.isBlank() ? Path.of(xdgDataHome)
                : Path.of(System.getProperty("user.home"), ".local", "share");
        return parent.resolve("sefazba");
    }

    public Path baseDir() {
        return baseDir;
    }

    /**
     * True when this run created the data folder's configs.yaml, i.e. the app was just installed.
     */
    public boolean firstRun() {
        return firstRun;
    }

    public String databaseUrl() {
        String url = string("application.database.url", SQLITE_PREFIX + "datas/mdfe.db");
        return url.startsWith(SQLITE_PREFIX) ? SQLITE_PREFIX + resolve(url.substring(SQLITE_PREFIX.length())) : url;
    }

    public String home() {
        return resolve(string("application.home", "."));
    }

    public String keyPath() {
        return resolve(string("application.key-path", "keys/"));
    }

    public boolean browserHeadless() {
        return Boolean.parseBoolean(string("application.browser.headless", "false"));
    }
    public boolean genPDFReport() {return  Boolean.parseBoolean(string("application.reports.gen-report", "true"));}

    /**
     * The configured folder when it exists, otherwise the tessdata shipped with the installer.
     */
    public String tesseractDatapath() {
        String configured = resolve(string("application.tesseract.datapath", "/usr/share/tesseract-ocr/5/tessdata"));
        String bundled = System.getProperty(BUNDLED_TESSDATA_PROPERTY);
        return !Files.isDirectory(Path.of(configured)) && bundled != null ? bundled : configured;
    }

    public String recipientLogin() {
        return string("application.sefaz.links.login-recipient", null);
    }

    public String issuerLogin() {
        return string("application.sefaz.links.login-issuer", null);
    }

    public String accessKeySite() {
        return string("application.sefaz.links.access-key-site", null);
    }

    public String fallbackReportsDir() {
        return resolve(string("application.reports.fallback-dir", "reports"));
    }

    public String accountDeskName() {
        return string("personalization.account-desk.reports.name", "");
    }

    public String accountDeskSlogan() {
        return string("personalization.account-desk.reports.slogan", "");
    }

    private String resolve(String path) {
        return baseDir.resolve(path).normalize().toString();
    }

    @SuppressWarnings("unchecked")
    private String string(String dottedKey, String defaultValue) {
        Object current = root;
        for (String part : dottedKey.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return defaultValue;
            }
            current = ((Map<String, Object>) map).get(part);
        }
        return current != null ? current.toString() : defaultValue;
    }
}
