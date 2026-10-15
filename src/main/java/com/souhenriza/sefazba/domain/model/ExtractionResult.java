package com.souhenriza.sefazba.domain.model;

import com.souhenriza.sefazba.domain.model.sefaz.NFeDetails;
import com.souhenriza.sefazba.domain.model.sefaz.NFeEntry;

import java.util.List;

public record ExtractionResult(
        List<NFeEntry> entries,
        List<NFeDetails> details,
        List<String> savedReportPaths
) {
    public ExtractionResult(List<NFeEntry> entries, List<NFeDetails> details) {
        this(entries, details, List.of());
    }

    public boolean hasReports() {
        return savedReportPaths != null && !savedReportPaths.isEmpty();
    }
}
