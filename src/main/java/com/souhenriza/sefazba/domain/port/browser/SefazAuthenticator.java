package com.souhenriza.sefazba.domain.port.browser;

import com.souhenriza.sefazba.domain.model.sefaz.SefazSession;

public interface SefazAuthenticator {



    void authenticate(String login, String password);

    /**
     * Checks whether the given session is still valid on the SEFAZ portal.
     */
    boolean isSessionActive(SefazSession session);
}
