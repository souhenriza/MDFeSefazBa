package com.souhenriza.sefazba.domain.impl;

import com.souhenriza.sefazba.domain.exceptions.CaptchaSolveException;
import com.souhenriza.sefazba.domain.port.browser.CaptchaSolver;

import java.io.File;
import java.util.Random;

public class CaptchaAccessKeySolver implements CaptchaSolver {

    private static final int SCALE_FACTOR = 4;

    private Random random = new Random();

    private static final String letters = "abcdefghijklmonpqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @Override
    public String solve(File imageFile) throws CaptchaSolveException {
        String[] letterArray = letters.split("");

        StringBuilder builder = new StringBuilder();

        while (builder.length() < 4) {
            String s = letterArray[random.nextInt(letterArray.length)];
            System.out.println(s);
            builder.append(s);
        }

        return builder.toString();
    }

}
