package com.souhenriza.sefazba.domain.model.sefaz;

import java.util.List;

public record NFeDetails(
        NFeDetailsHeader header,
        List<NFeDetailsEvent> events
) {
}
