package com.souhenriza.sefazba.domain.model;

import com.souhenriza.sefazba.shared.enums.PreferBrowser;

public record User(
        Long id,
        String email,
        PreferBrowser preferredBrowser
) {
}
