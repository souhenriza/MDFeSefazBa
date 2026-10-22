package com.souhenriza.sefazba.application.dto;

import com.souhenriza.sefazba.domain.model.company.Cnpj;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CSVReportFormat(
        String nfeNumber,
        Cnpj cnpj,
        String issuerName,
        LocalDateTime issueDate,
        LocalDateTime authorizationDate,
        BigDecimal value,
        String accessKey,
        String uf,
        String situation,
        String tpOper
) {
}
