package com.souhenriza.sefazba.infrastructure.browser;

import com.souhenriza.sefazba.infrastructure.browser.PlaywrightBrowserProvider;
import com.souhenriza.sefazba.domain.model.sefaz.NFeDetails;
import com.souhenriza.sefazba.domain.model.sefaz.NFeDetailsEvent;
import com.souhenriza.sefazba.domain.model.sefaz.NFeDetailsHeader;
import com.souhenriza.sefazba.domain.model.sefaz.NFeEntry;
import com.souhenriza.sefazba.domain.port.browser.NetworkChangesResolver;
import com.souhenriza.sefazba.shared.enums.RetriesEnum;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Getter
@RequiredArgsConstructor
@Slf4j
public class NFeDetailsExtractor {

    private final PlaywrightBrowserProvider browserProvider;
    private final NetworkChangesResolver networkChangesResolver;
    private static final int MAX_TIMEOUT_RETRIES = 5;
    private boolean haveAnyError = false;



    public NFeDetails abrirResumoNFe(Page page, Locator row, NFeEntry entry) {
        haveAnyError = false;
        for (int tentativas = 1; tentativas <= MAX_TIMEOUT_RETRIES; tentativas++) {
            try {
                Locator link = row.locator("td:first-child a");

                Page[] resumoDoSuporteDasAbas = new Page[1];
                networkChangesResolver.resolveAction(page, () -> {
                    resumoDoSuporteDasAbas[0] = page.context().waitForPage(link::click);
                    resumoDoSuporteDasAbas[0].waitForLoadState();
                }, RetriesEnum.MAX_RETRY.getMaxRetries(), 2000);

                Page summaryTab = resumoDoSuporteDasAbas[0];
                summaryTab.waitForTimeout(2000);

                NFeDetails details = abrirNFeDetalhada(summaryTab, entry);

                summaryTab.close();
                return details;
            } catch (TimeoutError e) {
                log.warn("Timeout opening NF-e summary (attempt {}/{}), refreshing page...", tentativas, MAX_TIMEOUT_RETRIES);
                page.reload();
                page.waitForLoadState();
                page.waitForTimeout(2000);
                if (tentativas == MAX_TIMEOUT_RETRIES) {
                    log.error("Failed to open NF-e summary after {} attempts for entry {}", MAX_TIMEOUT_RETRIES, entry.number());
                    return null;
                }

            }
        }
        return null;
    }

    public NFeDetails abrirNFeDetalhada(Page summaryPage, NFeEntry entry) {
        for (int attempt = 1; attempt <= MAX_TIMEOUT_RETRIES; attempt++) {
            try {
                Locator detailButton = summaryPage.locator("button[name='btnNFEDetalhada']");

                networkChangesResolver.resolveAction(summaryPage, () -> {
                    detailButton.click();
                    summaryPage.waitForLoadState();
                }, RetriesEnum.MAX_RETRY.getMaxRetries(), 2000);

                summaryPage.waitForTimeout(5000);

                String url = summaryPage.url().toLowerCase();
                if (url.contains("err")) {
                    log.warn("Error page detected for NF-e {}: {}", entry.number(), url);
                    haveAnyError = true;
                    return null;
                }

                NFeDetailsHeader header = extrairCabecalho(summaryPage, entry);
                List<NFeDetailsEvent> events = extrairEventos(summaryPage);

                if (header == null) {
                    return null;
                }

                return new NFeDetails(header, events);
            } catch (TimeoutError e) {
                log.warn("Timeout opening NF-e details (attempt {}/{}), refreshing page...", attempt, MAX_TIMEOUT_RETRIES);
                abrirNFeDetalhada(summaryPage, entry);
                attempt++;
                if (attempt == MAX_TIMEOUT_RETRIES) {
                    log.error("Failed to open NF-e details after {} attempts for entry {}", MAX_TIMEOUT_RETRIES, entry.number());
                    return null;
                }
            }
        }
        return null;
    }

    public NFeDetailsHeader extrairCabecalho(Page detailPage, NFeEntry entry) {
        Locator nfeTable = detailPage.locator("#tb_nfe .table_geral").first();
        List<Locator> cells;
        int attempt = 0;

        do {
            cells = nfeTable.locator("tbody tr").first().locator("td").all();
            if (cells.size() >= 6) {
                break;
            }
            attempt++;
            log.warn("NF-e header row has fewer than 6 cells (attempt {}/{}), reloading...",
                    attempt, MAX_TIMEOUT_RETRIES);
            detailPage.reload();
            detailPage.waitForLoadState();
        } while (attempt < MAX_TIMEOUT_RETRIES);

        if (cells.size() < 6) {
            log.warn("NF-e header row still has fewer than 6 cells after {} reloads", MAX_TIMEOUT_RETRIES);
            return null;
        }

        String number = cells.get(2).locator("p.linha").textContent().trim();
        String issueDate = cells.get(3).locator("p.linha").textContent().trim();
        String totalValue = "R$".concat(cells.get(5).locator("p.linha").textContent().trim());

        log.info("Cabeçalho NF-e — Número: {} | Data de emissão: {} | Emissor: {} | Valor R${}", number, issueDate,
                entry.companyName(), totalValue);

        return NFeDetailsHeader
                .builder()
                .number(number)
                .accessKey(entry.accessKey())
                .issueDate(issueDate)
                .emitter(entry.companyName())
                .totalValue(totalValue)
                .build();
    }

    public List<NFeDetailsEvent> extrairEventos(Page detailPage) {
        Locator eventRows = detailPage.locator(".tabNFe tbody tr");
        List<NFeDetailsEvent> nFeDetailsEvents = new ArrayList<>();

        for (Locator row : eventRows.all()) {
            List<Locator> cells = row.locator("td").all();
            String date = cells.get(2).textContent().trim().substring(0, 10);

            NFeDetailsEvent nFeDetailsEvent = NFeDetailsEvent
                    .builder()
                    .event(cells.get(0).textContent().trim())
                    .protocol(cells.get(1).textContent().trim())
                    .authorizationDate(date)
                    .build();

            nFeDetailsEvents.add(nFeDetailsEvent);
        }

        for (NFeDetailsEvent event : nFeDetailsEvents) {
            if (event.event().toUpperCase(Locale.ROOT).contains("MDFE") || event.event().toUpperCase(Locale.ROOT).contains("MDF-E")) {
                log.info("Evento: {}", event);
            }
        }

        return nFeDetailsEvents;
    }

}
