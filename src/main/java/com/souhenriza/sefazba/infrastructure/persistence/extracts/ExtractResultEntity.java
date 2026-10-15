package com.souhenriza.sefazba.infrastructure.persistence.extracts;


import com.souhenriza.sefazba.domain.model.executions.ExtractStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "extract_result")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExtractResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String company;
    private String period;
    private LocalDateTime extractedAt;
    private String document;
    @Enumerated(EnumType.STRING)
    private ExtractStatus extractStatus;
}
