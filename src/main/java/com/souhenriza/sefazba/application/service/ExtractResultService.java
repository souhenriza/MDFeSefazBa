package com.souhenriza.sefazba.application.service;

import com.souhenriza.sefazba.application.dto.ExtractResultResponse;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ExtractResultService {

    private final ExtractRepository extractRepository;

    public List<ExtractResultResponse> findRecent(int limit) {
        return extractRepository.findAllByOrderByExtractedAtDesc(limit).stream()
                .map(ExtractResultResponse::fromEntity)
                .toList();
    }
}
