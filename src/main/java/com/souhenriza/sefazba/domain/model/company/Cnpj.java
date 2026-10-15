package com.souhenriza.sefazba.domain.model.company;

/**
 * Self-validating Value Object for Brazilian CNPJ.
 */
public record Cnpj(java.lang.String value) {

    private static final int[] FIRST_WEIGHTS  = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public Cnpj {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CNPJ must not be blank");
        }
        value = value.replaceAll("[^0-9]", "");
        if (value.length() != 14) {
            throw new IllegalArgumentException("CNPJ must contain exactly 14 digits");
        }
        if (!validateDigits(value)) {
            throw new IllegalArgumentException("Invalid CNPJ: " + value);
        }
    }

    public java.lang.String formatted() {
        return value.substring(0, 2) + "." +
               value.substring(2, 5) + "." +
               value.substring(5, 8) + "/" +
               value.substring(8, 12) + "-" +
               value.substring(12);
    }

    private static boolean validateDigits(java.lang.String cnpj) {
        int first  = computeDigit(cnpj, FIRST_WEIGHTS);
        int second = computeDigit(cnpj, SECOND_WEIGHTS);
        return cnpj.charAt(12) - '0' == first
            && cnpj.charAt(13) - '0' == second;
    }

    private static int computeDigit(java.lang.String cnpj, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (cnpj.charAt(i) - '0') * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
