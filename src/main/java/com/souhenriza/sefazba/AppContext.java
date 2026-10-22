package com.souhenriza.sefazba;

import com.souhenriza.sefazba.application.service.*;
import com.souhenriza.sefazba.domain.impl.CaptchaDestinatarioSolver;
import com.souhenriza.sefazba.domain.impl.ICMSPartialAnticipationImpl;
import com.souhenriza.sefazba.domain.port.browser.NetworkChangesResolver;
import com.souhenriza.sefazba.infrastructure.browser.*;
import com.souhenriza.sefazba.infrastructure.config.AppConfig;
import com.souhenriza.sefazba.infrastructure.config.CryptoService;
import com.souhenriza.sefazba.infrastructure.lock.ExtractionLock;
import com.souhenriza.sefazba.infrastructure.persistence.Jpa;
import com.souhenriza.sefazba.infrastructure.persistence.company.CompanyRepository;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractRepository;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchItemRepository;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchRepository;
import com.souhenriza.sefazba.infrastructure.reports.NFeDetailsPDFReport;
import com.souhenriza.sefazba.infrastructure.reports.NFeDetailsXLSXReport;

import java.nio.file.Path;
import java.util.List;

/**
 * Wires the object graph by hand, in place of the Spring container.
 */
public final class AppContext implements AutoCloseable {

    private final AppConfig config;
    private final Jpa jpa;
    private final PlaywrightBrowserProvider browserProvider;

    public final CompanyService companyService;
    public final SefazSessionService sessionService;
    public final ExtractionBatchService batchService;
    public final ExtractResultService extractResultService;
    public final NFeExtractionService extractionService;
    public final PlaywrightReportExtractor reportExtractor;


    private boolean closed;

    public AppContext(AppConfig config) {
        this.config = config;
        this.jpa = new Jpa(config.databaseUrl());

        CompanyRepository companyRepository = new CompanyRepository(jpa);
        ExtractRepository extractRepository = new ExtractRepository(jpa);
        ExtractionBatchRepository extractionBatchRepository = new ExtractionBatchRepository(jpa);
        ExtractionBatchItemRepository extractionBatchItemRepository = new ExtractionBatchItemRepository(jpa);

        CryptoService cryptoService = new CryptoService(config.keyPath());
        BrowserLockManager lockManager = new BrowserLockManager();
        this.browserProvider = new PlaywrightBrowserProvider(lockManager, config.browserHeadless());

        NetworkChangesResolver networkChangesResolver = new NetworkChangesResolver();

        PlaywrightSefazAuthenticator authenticator =
                new PlaywrightSefazAuthenticator(browserProvider, networkChangesResolver);
        PlaywrightNFeSearchSubmitter searchSubmitter = new PlaywrightNFeSearchSubmitter(browserProvider,
                new CaptchaDestinatarioSolver(config.tesseractDatapath()), config.home());
        NFeDetailsExtractor detailsExtractor = new NFeDetailsExtractor(browserProvider, networkChangesResolver);
        PlaywrightNFeGridExtractor gridExtractor =
                new PlaywrightNFeGridExtractor(browserProvider, searchSubmitter, searchSubmitter, detailsExtractor);
        this.reportExtractor = new PlaywrightReportExtractor(searchSubmitter, browserProvider);

        this.companyService = new CompanyService(companyRepository, cryptoService, jpa);

        this.sessionService = new SefazSessionService(companyService, authenticator, browserProvider, lockManager,
                cryptoService, config);

        this.batchService = new ExtractionBatchService(extractionBatchRepository, extractionBatchItemRepository, jpa);
        this.extractResultService = new ExtractResultService(extractRepository);
        this.extractionService = new NFeExtractionService(sessionService, companyService, gridExtractor, lockManager,
                List.of(
                        new NFeDetailsPDFReport(config.accountDeskName(), config.accountDeskSlogan(),
                                new ICMSPartialAnticipationImpl()),
                        new NFeDetailsXLSXReport()),
                extractRepository, batchService, config.fallbackReportsDir(),
                config.genPDFReport());
    }

    public ExtractionLock acquireExtractionLock() {
        ExtractionLock lock = ExtractionLock.acquire(Path.of(config.home(), "datas", "extraction.lock"));
        batchService.recoverInterruptedBatches();
        return lock;
    }

    public AppConfig config() {
        return config;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        browserProvider.shutdown();
        jpa.close();
    }
}
