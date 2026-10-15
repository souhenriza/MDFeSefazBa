package com.souhenriza.sefazba.infrastructure.browser;

import com.souhenriza.sefazba.infrastructure.browser.PlaywrightBrowserProvider;
import com.souhenriza.sefazba.domain.model.*;
import com.souhenriza.sefazba.domain.model.ExtractionResult;
import com.souhenriza.sefazba.domain.model.sefaz.NFeDetails;
import com.souhenriza.sefazba.domain.model.sefaz.NFeEntry;
import com.souhenriza.sefazba.domain.port.browser.NFeGridExtractor;
import com.souhenriza.sefazba.domain.port.browser.NFeSearchSubmitter;
import com.souhenriza.sefazba.shared.enums.SearchPeriod.DateRange;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class  PlaywrightNFeGridExtractor implements NFeGridExtractor {

    private static final String AUTHORIZATION = "Autorizada";
    private static final int MAX_TIMEOUT_RETRIES = 20;



    private final PlaywrightBrowserProvider browserProvider;
    private final NFeSearchSubmitter searchSubmitter;
    private final PlaywrightNFeSearchSubmitter nFeSearchSubmitter;
    private final NFeDetailsExtractor nFeDetailsExtractor;

    @Override
    public ExtractionResult extractAll(DateRange dateRange) {
        int attempt = 0;

        retry(dateRange);

        log.info("Captcha solved successfully on attempt {}", attempt + 1);

        Page page = browserProvider.acquirePage();
        page.waitForLoadState();

        List<NFeEntry> outboundEntries = new ArrayList<>();
        List<NFeDetails> detailsList = new ArrayList<>();
        int currentPage = 1;

        while (true) {
            processCurrentPage(page, outboundEntries, detailsList);

            if (!goToNextPage(page, currentPage + 1)) {
                break;
            }
            currentPage++;
            log.info("Processing {}", currentPage);
        }

        log.info("Total outbound NF-e entries: {}", outboundEntries.size());
        browserProvider.releasePage(page);
        return new ExtractionResult(outboundEntries, detailsList);
    }

    private void processCurrentPage(Page page, List<NFeEntry> outboundEntries, List<NFeDetails> detailsList) {
        List<Locator> rows = page.locator("#Grid tr:not(.tituloAzul)").all();

        for (Locator row : rows) {
            List<Locator> cells = row.locator("td").all();
            if (cells.size() < 10) continue;

            NFeEntry entry = new NFeEntry(
                    cells.get(0).textContent().trim(),
                    cells.get(1).textContent().trim(),
                    cells.get(2).textContent().trim(),
                    cells.get(3).textContent().trim(),
                    cells.get(4).textContent().trim(),
                    cells.get(5).textContent().trim(),
                    cells.get(6).textContent().trim(),
                    cells.get(7).textContent().trim(),
                    cells.get(8).textContent().trim(),
                    cells.get(9).textContent().trim()
            );

            if (!entry.isFromBahia() && entry.status().equalsIgnoreCase(AUTHORIZATION)) {
                outboundEntries.add(entry);

                int errorAttempts = 0;
                do {
                    NFeDetails details = nFeDetailsExtractor.abrirResumoNFe(page, row, entry);
                    if (details != null) {
                        detailsList.add(details);
                    }
                    errorAttempts++;
                    if (nFeDetailsExtractor.isHaveAnyError()) {
                        log.warn("Error opening NF-e {} (attempt {}/{}), retrying...",
                                entry.number(), errorAttempts, MAX_TIMEOUT_RETRIES);
                    }
                } while (nFeDetailsExtractor.isHaveAnyError() && errorAttempts < MAX_TIMEOUT_RETRIES);

                if (nFeDetailsExtractor.isHaveAnyError()) {
                    log.error("Failed to open NF-e {} after {} attempts due to error page",
                            entry.number(), MAX_TIMEOUT_RETRIES);
                }
            }
        }
    }

    private boolean goToNextPage(Page page, int nextPageNumber) {
        for (int attempt = 1; attempt <= MAX_TIMEOUT_RETRIES; attempt++) {
            try {
                Locator pageLink = page.locator(
                        "#Grid a[href*=\"Page$" + nextPageNumber + "\"]"
                );

                if (pageLink.count() == 0) {
                    return false;
                }

                pageLink.click();
                page.waitForLoadState();
                page.waitForTimeout(2000);
                return true;
            } catch (TimeoutError e) {
                log.warn("Timeout navigating to page {} (attempt {}/{}), refreshing...", nextPageNumber, attempt, MAX_TIMEOUT_RETRIES);
                page.reload();
                page.waitForLoadState();
                page.waitForTimeout(2000);
                if (attempt == MAX_TIMEOUT_RETRIES) {
                    log.error("Failed to navigate to page {} after {} attempts", nextPageNumber, MAX_TIMEOUT_RETRIES);
                    return false;
                }
            }
        }
        return false;
    }

    private void retry(DateRange range) {
        int attempt = 0;
        do {
            attempt++;
            searchSubmitter.submit(range);
            if (attempt >= MAX_TIMEOUT_RETRIES && !searchSubmitter.isSuccess()) {
                throw new RuntimeException("Failed to solve captcha after " + attempt + " attempts");
            }
        } while (!searchSubmitter.isSuccess());
    }
}
