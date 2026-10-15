package com.souhenriza.sefazba.infrastructure.persistence.company;

import com.souhenriza.sefazba.domain.model.company.Company;
import com.souhenriza.sefazba.domain.model.sefaz.SefazCredentials;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "company")
@Getter
@Setter
@NoArgsConstructor
public class CompanyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "legal_name", nullable = false)
    private java.lang.String legalName;

    @Column(name = "trade_name")
    private java.lang.String tradeName;

    @Column(name = "cnpj", nullable = false, unique = true, length = 14)
    private java.lang.String cnpj;

    @Column(name = "dominio_sistemas_id", nullable = false)
    private int dominioSistemasId;

    @Column(name = "sefaz_login", nullable = false)
    private java.lang.String sefazLogin;

    @Column(name = "sefaz_password", nullable = false)
    private java.lang.String sefazPassword;

    @Column(name = "responsible_user_id", nullable = false)
    private Long responsibleUserId;

    @Column(name = "commercial_intention")
    private boolean commercialIntention;


    @Column(name = "prefer_output_path")
    private String preferOutputPath;

    public Company toDomain() {
        return new Company(
                legalName,
                tradeName,
                cnpj,
                dominioSistemasId,
                new SefazCredentials(sefazLogin, sefazPassword),
                responsibleUserId,
                commercialIntention,
                preferOutputPath
        );
    }

    public static CompanyEntity fromRequest(
            java.lang.String legalName, java.lang.String tradeName, java.lang.String cnpj,
            int dominioSistemasId, java.lang.String sefazLogin, java.lang.String sefazPassword,
            Long responsibleUserId, boolean commercialIntention, String preferOutputPath
    ) {
        CompanyEntity entity = new CompanyEntity();
        entity.setLegalName(legalName);
        entity.setTradeName(tradeName);
        entity.setCnpj(cnpj);
        entity.setDominioSistemasId(dominioSistemasId);
        entity.setSefazLogin(sefazLogin);
        entity.setSefazPassword(sefazPassword);
        entity.setResponsibleUserId(responsibleUserId);
        entity.setCommercialIntention(commercialIntention);
        entity.setPreferOutputPath(preferOutputPath);
        return entity;
    }
}
