package com.souhenriza.sefazba.infrastructure.browser;

import com.souhenriza.sefazba.infrastructure.browser.PlaywrightBrowserProvider;
import com.souhenriza.sefazba.domain.port.browser.CaptchaSolver;
import com.souhenriza.sefazba.domain.exceptions.CaptchaSolveException;
import com.souhenriza.sefazba.domain.exceptions.NoDataFoundException;
import com.souhenriza.sefazba.domain.port.browser.NFeSearchSubmitter;
import com.souhenriza.sefazba.shared.enums.SearchPeriod.DateRange;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

@Slf4j
public class PlaywrightNFeSearchSubmitter implements NFeSearchSubmitter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PlaywrightBrowserProvider browserProvider;
    private final CaptchaSolver captchaSolver;

    private final String applicationHome;

    @Getter
    private boolean success;

    public PlaywrightNFeSearchSubmitter(
            PlaywrightBrowserProvider browserProvider,
            CaptchaSolver captchaSolver,
            String applicationHome
    ) {
        this.browserProvider = browserProvider;
        this.captchaSolver = captchaSolver;
        this.applicationHome = applicationHome;
    }

    @Override
    public void submit(DateRange dateRange) {
        this.success = false;

        Page page = browserProvider.acquirePage();

        page.waitForLoadState();
        page.waitForTimeout(1000);
        log.info("Navigated to destinatario page");

        if (!isFilledStartPeriod(page) || !isFilledEPeriod(page)){
            fillPeriod(page, dateRange);
        }

        try {
            solveCaptchaAndClick(page);
        } catch (CaptchaSolveException e) {
            log.warn("Captcha OCR failed ({}), reloading page to retry", e.getMessage());
            page.reload();
            page.waitForLoadState();
            this.success = false;
            return;
        }

        page.waitForTimeout(3000);

        String pageContent = page.content();

        if (pageContent.contains("A consulta não retornou resultados")
                || pageContent.contains("A consulta não retornou dados")) {
            log.info("Query returned no data for the given period. Closing browser and releasing session.");
            browserProvider.releasePage(page);
            throw new NoDataFoundException();
        }

        int gridRowCount = page.locator("#Grid tr:not(.tituloAzul)").count();
        log.info("After submit — grid rows: {}", gridRowCount);

        boolean captchaStillVisible = page.locator("#txt_cod_antirobo").isVisible();

        if (gridRowCount == 0 && !captchaStillVisible) {
            log.info("Captcha was accepted but no data was returned. Closing browser and releasing session.");
            browserProvider.releasePage(page);
            throw new NoDataFoundException();
        }

        this.success = gridRowCount > 0;
        log.info("Submit success: {}", this.success);
    }

    public boolean isFilledStartPeriod(Page page) {
        return page.locator("txtPeriodoInicial").inputValue() != null;
    }

    public boolean isFilledEPeriod(Page page) {
        return page.locator("txtPeriodoInicial").inputValue() != null;
    }

    private void fillPeriod(Page page, DateRange dateRange) {
        String start = dateRange.start().format(DATE_FORMAT);
        String end = dateRange.end().format(DATE_FORMAT);
        page.click("#rbt_filtro3");
        page.waitForTimeout(1000);

        page.locator("#txtPeriodoInicial").fill(start);
        page.waitForTimeout(1000);

        page.locator("#txtPeriodoFinal").fill(end);
        page.waitForTimeout(1000);

        log.info("Period set: {} to {}", start, end);
    }

    private void solveCaptchaAndClick(Page page) {


        page.locator("#img_captcha").waitFor();
        page.waitForTimeout(1500);

        byte[] captchaImage = page.locator("#img_captcha").screenshot();
        File imageFile = saveCaptchaImage(captchaImage);

        String captchaText = captchaSolver.solve(imageFile);

        page.click("#txt_cod_antirobo");
        page.waitForTimeout(500);
        page.fill("#txt_cod_antirobo", captchaText);
        page.waitForTimeout(1000);
        log.info("Captcha field filled with: '{}'", page.inputValue("#txt_cod_antirobo"));

        Response response = page.waitForResponse(
                r -> r.url().contains("consulta_destinatario"),
                () -> page.click("#AplicarFiltro")
        );

        page.waitForLoadState();
        page.waitForTimeout(1000);
        log.info("Response status: {}", response.status());
    }

    private File saveCaptchaImage(byte[] imageBytes) {
        Path path = Path.of(applicationHome, "png", "captcha_destinatario.png");
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, imageBytes);
            return path.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save captcha image", e);
        }
    }

}
