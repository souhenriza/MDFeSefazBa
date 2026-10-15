package com.souhenriza.sefazba.infrastructure.browser;

import com.souhenriza.sefazba.domain.exceptions.InvalidUserException;
import com.souhenriza.sefazba.domain.model.sefaz.SefazSession;
import com.souhenriza.sefazba.domain.exceptions.SefazAuthenticationException;
import com.souhenriza.sefazba.domain.port.browser.NetworkChangesResolver;
import com.souhenriza.sefazba.domain.port.browser.SefazAuthenticator;
import com.souhenriza.sefazba.shared.enums.RetriesEnum;
import com.microsoft.playwright.Page;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlaywrightSefazAuthenticator implements SefazAuthenticator {

    private final PlaywrightBrowserProvider browserProvider;
    private final String rootSite;
    @Getter @Setter
    private boolean authenticated;
    private final NetworkChangesResolver networkChangesResolver;
    private static final long RETRY_DELAY_MS = 3000;

    public PlaywrightSefazAuthenticator(
            PlaywrightBrowserProvider browserProvider,
            String rootSite, NetworkChangesResolver networkChangesResolver
    ) {
        this.browserProvider = browserProvider;
        this.rootSite = rootSite;
        this.networkChangesResolver = networkChangesResolver;
        this.authenticated = false;
    }


    @Override
    public void authenticate(String login, String password) {
        this.authenticated = false;
        try {
            Page page = browserProvider.acquirePage();

             networkChangesResolver.resolve(page, rootSite,
                    RetriesEnum.MAX_RETRY.getMaxRetries(), RETRY_DELAY_MS);

            fillLoginForm(page, login, password);

            submitLogin(page);


            validateIfHasErrors(page);
            page.waitForLoadState();
             log.info("pressing esc");
             page.keyboard().press("Escape");


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
        if (page.content().contains("Usuário ou senha Inválidos")) {
            browserProvider.releasePage(page);
            throw new InvalidUserException();
        }

        if (page.content().contains("Bloqueado")) {
            browserProvider.releasePage(page);
            throw new InvalidUserException("O usuário se encontra bloqueado");
        }
    }



}
