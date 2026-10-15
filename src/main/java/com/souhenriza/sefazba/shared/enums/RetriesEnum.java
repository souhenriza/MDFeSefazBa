package com.souhenriza.sefazba.shared.enums;

import lombok.Getter;

public enum RetriesEnum {
    MAX_RETRY(5);

    @Getter
    private final int maxRetries;

    RetriesEnum(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
