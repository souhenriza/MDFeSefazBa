package com.souhenriza.sefazba.infrastructure.browser;

import com.souhenriza.sefazba.domain.exceptions.InvalidUserException;
import com.souhenriza.sefazba.domain.model.sefaz.SefazSession;
import com.souhenriza.sefazba.domain.exceptions.SefazAuthenticationException;
import com.souhenriza.sefazba.domain.port.browser.NetworkChangesResolver;
import com.souhenriza.sefazba.domain.port.browser.SefazAuthenticator;
import com.souhenriza.sefazba.shared.enums.RetriesEnum;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlaywrightSefazAuthenticator implements SefazAuthenticator {

    private final PlaywrightBrowserProvider browserProvider;
    @Getter @Setter
    private boolean authenticated;
    private final NetworkChangesResolver networkChangesResolver;
    private static final long RETRY_DELAY_MS = 3000;
    private static final int CONTENT_MAX_ATTEMPTS = 10;
    private static final long CONTENT_RETRY_DELAY_MS = 1000;

    public PlaywrightSefazAuthenticator(
            PlaywrightBrowserProvider browserProvider, NetworkChangesResolver networkChangesResolver
    ) {
        this.browserProvider = browserProvider;
        this.networkChangesResolver = networkChangesResolver;
        this.authenticated = false;
    }


    @Override
    public void authenticate(Page page, String login, String password) {
        this.authenticated = false;
        try {
             networkChangesResolver.resolve(page,
                    RetriesEnum.MAX_RETRY.getMaxRetries(), RETRY_DELAY_MS);

            fillLoginForm(page, login, password);

            submitLogin(page);

            validateIfHasErrors(page);

            page.waitForLoadState();

            this.authenticated = true;

        } catch (SefazAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new SefazAuthenticationException("Login failed: " + e.getMessage(), e);
        }
    }


    @Override
    public boolean isSessionActive(SefazSession session) {
        return session != null && session.isValid();
    }

    private void fillLoginForm(Page page, String login, String password) {
        page.fill("#PHCentro_userLogin", login.trim());
        page.waitForTimeout(1000);
        page.fill("#PHCentro_userPass", password.trim());
        page.waitForTimeout(1000);
    }


    private void submitLogin(Page page) {

        page.locator("label[for='PHCentro_btnLogin']").click();

        page.waitForLoadState();
        page.waitForTimeout(5000);
        page.waitForLoadState();
    }

    private void validateIfHasErrors(Page page) {
        String content = currentContent(page);

        if (content.contains("Usuário ou senha Inválidos")) {
            browserProvider.releasePage(page);
            throw new InvalidUserException();
        }

        if (content.contains("Bloqueado")) {
            browserProvider.releasePage(page);
            throw new InvalidUserException("O usuário se encontra bloqueado");
        }
    }

    /* The portal may still be redirecting after the submit; content() refuses to run until the page settles. */
    private String currentContent(Page page) {
        for (int attempt = 1; ; attempt++) {
            try {
                page.waitForLoadState();
                return page.content();
            } catch (PlaywrightException e) {
                boolean stillNavigating = e.getMessage() != null && e.getMessage().contains("navigating");
                if (!stillNavigating || attempt >= CONTENT_MAX_ATTEMPTS) {
                    throw e;
                }
                page.waitForTimeout(CONTENT_RETRY_DELAY_MS);
            }
        }
    }



}
