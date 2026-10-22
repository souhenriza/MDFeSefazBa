package com.souhenriza.sefazba.domain.exceptions;

import com.microsoft.playwright.Download;

import java.util.Locale;

public class DownloadNotAvailableException extends RuntimeException {
    public DownloadNotAvailableException(Download download) {super("O download do arquivo não pode ser concluído " + download.suggestedFilename().toLowerCase(Locale.ROOT));
    }
}
