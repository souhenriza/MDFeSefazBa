package com.souhenriza.sefazba.domain.model.sefaz;

import lombok.Builder;

@Builder
public record NFeDetailsHeader(
        String number,
        String accessKey,
        String issueDate,
        String emitter,
        String totalValue
) {
}
