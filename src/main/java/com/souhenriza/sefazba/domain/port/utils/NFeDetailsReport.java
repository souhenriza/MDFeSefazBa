package com.souhenriza.sefazba.domain.port.utils;

import com.souhenriza.sefazba.domain.model.sefaz.NFeDetails;
import com.souhenriza.sefazba.domain.model.ReportContext;
import com.souhenriza.sefazba.shared.enums.ReportFormat;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface NFeDetailsReport {

    CompletableFuture<byte[]> generate(List<NFeDetails> nFeDetailsList, ReportContext context);

    ReportFormat supportedFormat();
}
