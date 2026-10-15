package com.souhenriza.sefazba.domain.exceptions;

public class CompanyNotFoundException extends RuntimeException {

    public CompanyNotFoundException(Integer id) {
        super("Company not found with id: " + id);
    }

    public CompanyNotFoundException(String cnpj) {
        super("Company not found with CNPJ: " + cnpj);
    }
}
