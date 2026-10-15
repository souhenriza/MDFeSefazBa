package com.souhenriza.sefazba.cli;

import com.souhenriza.sefazba.application.dto.*;
import com.souhenriza.sefazba.domain.model.ExtractionResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tables printed by both the command line and the interactive menu.
 */
public final class Output {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Output() {
    }

    public static void printExtraction(ExtractionResult result) {
        System.out.println(result.entries().size() + " NF-e encontrada(s).");
        result.entries().forEach(entry -> System.out.printf("  %-10s %-12s %-40s %14s  %s%n",
                entry.number(), entry.issueDate(), truncate(entry.companyName(), 40), entry.value(), entry.accessKey()));
        result.savedReportPaths().forEach(path -> System.out.println("Relatório salvo: " + path));
    }

    public static void printCompanies(List<CompanyResponse> companies) {
        System.out.printf("%-5s %-14s %-50s %-8s %-8s %s%n", "ID", "CNPJ", "Razão social", "Domínio", "Resp.", "Int. comercial");
        for (CompanyResponse c : companies) {
            System.out.printf("%-5d %-14s %-50s %-8d %-8s %s%n", c.id(), c.cnpj(), truncate(c.legalName(), 50),
                    c.dominioSistemasId(), c.responsibleUserId(), Boolean.TRUE.equals(c.commercialIntention()) ? "sim" : "não");
        }
    }

    public static void printBatches(List<ExtractionBatchSummaryResponse> batches) {
        System.out.printf("%-5s %-22s %-14s %-23s %-17s %6s %6s %6s %6s%n",
                "ID", "Tipo", "Período", "Status", "Início", "Total", "OK", "Erro", "Pend.");
        for (ExtractionBatchSummaryResponse b : batches) {
            System.out.printf("%-5d %-22s %-14s %-23s %-17s %6d %6d %6d %6d%n", b.id(), b.batchType(), b.periodLabel(),
                    b.status(), format(b.startedAt()), b.total(), b.success(), b.error(), b.pending());
        }
    }

    public static void printBatchDetail(ExtractionBatchDetailResponse detail) {
        printBatches(List.of(detail.summary()));
        System.out.println();
        System.out.printf("%-5s %-50s %-8s %4s  %s%n", "Emp.", "Empresa", "Status", "Tent", "Erro / relatórios");
        for (ExtractionBatchDetailResponse.Item item : detail.items()) {
            String info = item.errorMessage() != null ? item.errorMessage() : String.join(", ", item.reportPaths());
            System.out.printf("%-5d %-50s %-8s %4d  %s%n", item.companyId(), truncate(item.companyName(), 50),
                    item.status(), item.attempts(), info);
        }
    }

    public static void printExtracts(List<ExtractResultResponse> extracts) {
        System.out.printf("%-5s %-17s %-50s %-23s %s%n", "ID", "Extraído em", "Empresa", "Período", "Status");
        for (ExtractResultResponse e : extracts) {
            System.out.printf("%-5d %-17s %-50s %-23s %s%n", e.id(), format(e.extractedAt()),
                    truncate(e.company(), 50), e.period(), e.extractStatus());
        }
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? "-" : DATE_TIME.format(dateTime);
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return "-";
        }
        // ASCII on purpose: the Windows console code page has no "…"
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
