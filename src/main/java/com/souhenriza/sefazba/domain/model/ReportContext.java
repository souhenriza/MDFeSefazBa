package com.souhenriza.sefazba.domain.model;

import java.time.LocalDate;

public record ReportContext(
        String companyName,
        String cnpj,
        LocalDate reportDate,
        LocalDate competencyDate
) {
    public static ReportContext of(String companyName, String cnpj, LocalDate competencyDate) {
        return new ReportContext(companyName, cnpj, LocalDate.now(), competencyDate);
    }
}
