package com.souhenriza.sefazba.domain.port.browser;

import com.souhenriza.sefazba.domain.exceptions.InputAccessKeyException;

public interface InputAccessKey {

    void input(String accessKey) throws InputAccessKeyException;


    boolean canBeInput(boolean isAuthenticated);
}
