package com.souhenriza.sefazba.domain.port.browser;

import com.souhenriza.sefazba.domain.exceptions.CaptchaSolveException;

import java.io.File;


public interface CaptchaSolver {

    String solve(File imageFile) throws CaptchaSolveException;
}
