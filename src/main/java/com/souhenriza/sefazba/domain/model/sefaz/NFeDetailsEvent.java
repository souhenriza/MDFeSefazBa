package com.souhenriza.sefazba.domain.model.sefaz;

import lombok.Builder;

@Builder
public record NFeDetailsEvent(
        String event,
        String protocol,
        String authorizationDate
) {
}
