package com.souhenriza.sefazba.application.service;

import com.souhenriza.sefazba.domain.exceptions.ExtractionInProgressException;
import com.souhenriza.sefazba.domain.model.company.Company;
import com.souhenriza.sefazba.domain.port.browser.SefazAuthenticator;
import com.souhenriza.sefazba.infrastructure.browser.BrowserLockManager;
import com.souhenriza.sefazba.infrastructure.browser.PlaywrightBrowserProvider;
import com.souhenriza.sefazba.infrastructure.config.CryptoService;
import com.souhenriza.sefazba.infrastructure.persistence.company.CompanyEntity;
import com.souhenriza.sefazba.shared.enums.PreferBrowser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SefazSessionService {

    private final CompanyService companyService;
    private final SefazAuthenticator authenticator;
    private final PlaywrightBrowserProvider browserProvider;
    private final BrowserLockManager lockManager;
    private final CryptoService cryptoService;


    public void login(Integer companyId) {
        if (!lockManager.tryAcquire()) {
            throw new ExtractionInProgressException();
        }

        try {
            CompanyEntity entity = companyService.getEntity(companyId);
            Company company = entity.toDomain();
            String cnpj = entity.getCnpj();

            String decryptedLogin = cryptoService.decrypt(company.credentials().login());
            String decryptedPassword = cryptoService.decrypt(company.credentials().password());

            browserProvider.setDefaultBrowser(PreferBrowser.FIREFOX);

            log.info("Authenticating company {} (CNPJ: {})", entity.getLegalName(), cnpj);
            authenticator.authenticate(decryptedLogin, decryptedPassword);
        } finally {
            lockManager.release();
        }
    }
}
