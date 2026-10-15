package com.souhenriza.sefazba.domain.model.sefaz;

/**
 * Strictly immutable Value Object holding SEFAZ login credentials.
 * To change any field, instantiate a new record.
 */
public record SefazCredentials(
        String login,
        String password
) {
    public SefazCredentials {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Login must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
    }

    public SefazCredentials withPassword(String newPassword) {
        return new SefazCredentials(this.login, newPassword);
    }

    @Override
    public String toString() {
        return "SefazCredentials[login=" + login + ", password=***]";
    }
}
