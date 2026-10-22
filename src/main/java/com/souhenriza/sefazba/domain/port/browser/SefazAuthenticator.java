package com.souhenriza.sefazba.domain.port.browser;

import com.microsoft.playwright.Page;
import com.souhenriza.sefazba.domain.model.sefaz.SefazSession;

public interface SefazAuthenticator {



    void authenticate(Page page, String login, String password);

    /**
     * Checks whether the given session is still valid on the SEFAZ portal.
     */
    boolean isSessionActive(SefazSession session);
}
