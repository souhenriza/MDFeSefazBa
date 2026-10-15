package com.souhenriza.sefazba.infrastructure.browser;

import com.souhenriza.sefazba.infrastructure.browser.PlaywrightSefazAuthenticator;
import com.souhenriza.sefazba.infrastructure.browser.PlaywrightBrowserProvider;
import com.souhenriza.sefazba.domain.port.browser.CaptchaSolver;
import com.souhenriza.sefazba.domain.port.browser.InputAccessKey;
import com.souhenriza.sefazba.domain.exceptions.InputAccessKeyException;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class PlaywrightAccessKeyInput implements InputAccessKey {

    private final PlaywrightBrowserProvider browserProvider;
    private final PlaywrightSefazAuthenticator sefazAuthenticator;
    private final CaptchaSolver captchaSolver;
    private final String accessKeySite;
    private final String applicationHome;
    private boolean imageUsed;

    public PlaywrightAccessKeyInput(PlaywrightBrowserProvider browserProvider, PlaywrightSefazAuthenticator sefazAuthenticator,
                                    CaptchaSolver captchaSolver, String accessKeySite, String applicationHome) {
        this.browserProvider = browserProvider;
        this.sefazAuthenticator = sefazAuthenticator;
        this.captchaSolver = captchaSolver;
        this.accessKeySite = accessKeySite;
        this.applicationHome = applicationHome;
        this.imageUsed = false;
    }

    @Override
    public void input(String accessKey) throws InputAccessKeyException {
        if (!canBeInput(sefazAuthenticator.isAuthenticated())) {
            throw new InputAccessKeyException("Not authenticated or wrong page");
        }

        Page page = browserProvider.acquirePage();

        try {
            page.navigate(accessKeySite);
            page.waitForLoadState();

            page.fill("#txt_chave_acesso", accessKey);

            page.locator("#img_captcha").waitFor();
            byte[] captchaImage = page.locator("#img_captcha")
                    .screenshot();
            File file = saveImage(captchaImage);
            String captchaText = captchaSolver.solve(file).trim();
            this.imageUsed = true;

            page.fill("#txt_cod_antirobo", captchaText);
            page.click("#btnConsulta");
            page.waitForLoadState();
            deleteImage(file);
            page.click("#btnNFEDetalhada");
        } catch (Exception e) {
            throw new InputAccessKeyException("Failed to input access key: " + e.getMessage());
        }
    }

    @Override
    public boolean canBeInput(boolean isAuthenticated) {
        return isAuthenticated;
    }


    private File saveImage(byte[] byteImage) {


        Path path = Path.of(applicationHome, "png", "captcha.png");

        try {
            Files.createDirectories(path.getParent());
            Files.write(path, byteImage);
            System.out.println("Arquivo salvo. ");

            return  path.toFile();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void deleteImage(File file) {
        if (this.imageUsed) {
            if (file.delete()) {
                log.info("Arquivo deletado. ");
            }
        }
    }
}
