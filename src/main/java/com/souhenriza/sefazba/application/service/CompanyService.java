package com.souhenriza.sefazba.application.service;

import com.souhenriza.sefazba.application.dto.CompanyResponse;
import com.souhenriza.sefazba.application.dto.CreateCompanyRequest;
import com.souhenriza.sefazba.application.dto.UpdateCompanyRequest;
import com.souhenriza.sefazba.domain.exceptions.CompanyAlreadyExists;
import com.souhenriza.sefazba.domain.exceptions.CompanyNotFoundException;
import com.souhenriza.sefazba.infrastructure.config.CryptoService;
import com.souhenriza.sefazba.infrastructure.persistence.company.CompanyEntity;
import com.souhenriza.sefazba.infrastructure.persistence.Jpa;
import com.souhenriza.sefazba.infrastructure.persistence.company.CompanyRepository;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CryptoService cryptoService;
    private final Jpa jpa;

    public List<CompanyResponse> create(List<CreateCompanyRequest> createCompanyRequests) {
        return jpa.inTransaction(em -> createAll(createCompanyRequests));
    }

    private List<CompanyResponse> createAll(List<CreateCompanyRequest> createCompanyRequests) {
        List<CompanyResponse> companyResponseList = new ArrayList<>();
        for (CreateCompanyRequest request : createCompanyRequests) {
            if (companyRepository.existsByCnpj(request.cnpj())) {
                throw new CompanyAlreadyExists("This company"+ "("+ request.legalName() + ") "+ "already exists in data base. ");
            }

            String cipheredLogin= cryptoService.encrypt(request.sefazLogin());
            String cipheredPassword = cryptoService.encrypt(request.sefazPassword());

            CompanyEntity entity = CompanyEntity.fromRequest(
                    request.legalName(),
                    request.tradeName(),
                    request.cnpj(),
                    request.dominioSistemasId(),
                    cipheredLogin,
                    cipheredPassword,
                    request.commercialIntention(),
                    request.preferOutputPath()
            );
            CompanyEntity saved = companyRepository.save(entity);
            companyResponseList.add(CompanyResponse.fromEntity(saved));
        }

        return companyResponseList;
    }

    public CompanyResponse findById(Integer id) {
        CompanyEntity entity = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id));
        return CompanyResponse.fromEntity(entity);
    }

    public CompanyResponse findByCnpj(String cnpj) {
        CompanyEntity entity = companyRepository.findByCnpj(cnpj)
                .orElseThrow(() -> new CompanyNotFoundException(cnpj));
        return CompanyResponse.fromEntity(entity);
    }

    public List<CompanyResponse> findAll() {
        return companyRepository.findAll().stream()
                .map(CompanyResponse::fromEntity)
                .toList();
    }

    public CompanyResponse update(Integer id, UpdateCompanyRequest request) {
        CompanyEntity entity = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id));

        if (request.legalName() != null && !request.legalName().isBlank()) {
            entity.setLegalName(request.legalName());
        }
        if (request.tradeName() != null) {
            entity.setTradeName(request.tradeName());
        }
        if (request.cnpj() != null && !request.cnpj().isBlank()) {
            entity.setCnpj(request.cnpj());
        }
        if (request.dominioSistemasId() != null) {
            entity.setDominioSistemasId(request.dominioSistemasId());
        }
        if (request.sefazLogin() != null && !request.sefazLogin().isBlank()) {
            entity.setSefazLogin(cryptoService.encrypt(request.sefazLogin()));
        }
        if (request.sefazPassword() != null && !request.sefazPassword().isBlank()) {
            entity.setSefazPassword(cryptoService.encrypt(request.sefazPassword()));
        }

        if (request.commercialIntention() != null) {
            entity.setCommercialIntention(request.commercialIntention());
        }

        CompanyEntity saved = companyRepository.save(entity);
        return CompanyResponse.fromEntity(saved);
    }

    public void delete(Integer id) {
        if (!companyRepository.existsById(id)) {
            throw new CompanyNotFoundException(id);
        }
        companyRepository.deleteById(id);
    }

    public CompanyEntity getEntity(Integer id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id));
    }

    public List<CompanyEntity> getAllEntities() {
        return companyRepository.findAll();
    }

    public List<CompanyEntity> getAllEntitiesWithCommercialIntention() {
        return companyRepository.findAllIfHasCommercialIntention();
    }
}
