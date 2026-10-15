package com.souhenriza.sefazba.infrastructure.reports;

import com.souhenriza.sefazba.domain.model.sefaz.NFeDetails;
import com.souhenriza.sefazba.domain.model.sefaz.NFeDetailsEvent;
import com.souhenriza.sefazba.domain.model.sefaz.NFeDetailsHeader;
import com.souhenriza.sefazba.domain.model.ReportContext;
import com.souhenriza.sefazba.domain.port.utils.NFeDetailsReport;
import com.souhenriza.sefazba.shared.enums.ReportFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class NFeDetailsXLSXReport implements NFeDetailsReport {

    private static final String[] COLUMN_HEADERS = {
            "Número", "Chave de Acesso", "Data de Emissão", "Emissor", "Evento MDFe", "Data do MDFe", "Protocolo"
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public ReportFormat supportedFormat() {
        return ReportFormat.XLSX;
    }

    @Override
    public CompletableFuture<byte[]> generate(List<NFeDetails> nFeDetailsList, ReportContext context) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("NF-e Report");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);

            int rowIndex = 0;
            rowIndex = writeCompanyHeader(sheet, boldStyle, rowIndex, context);
            rowIndex++;
            writeTableHeader(sheet, headerStyle, rowIndex);
            rowIndex++;

            for (NFeDetails details : nFeDetailsList) {
                NFeDetailsHeader header = details.header();
                NFeDetailsEvent mdfeEvent = details.events().stream()
                        .filter(e -> e.event() != null && (e.event().toUpperCase().contains("MDFE")
                                || e.event().toUpperCase().contains("MDF-E"))).min(Comparator.comparing(NFeDetailsEvent::authorizationDate))
                        .orElse(null);

                Row row = sheet.createRow(rowIndex++);
                writeNFeColumns(row, header);

                if (mdfeEvent == null) {
                    row.createCell(4).setCellValue("Sem MDFe vinculado");
                    row.createCell(5).setCellValue("-");
                    row.createCell(6).setCellValue("-");
                } else {
                    row.createCell(4).setCellValue(safeText(mdfeEvent.event()));
                    row.createCell(5).setCellValue(safeText(mdfeEvent.authorizationDate()));
                }
            }

            for (int i = 0; i < COLUMN_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return CompletableFuture.completedFuture(outputStream.toByteArray());
        } catch (IOException e) {
            log.error("Failed to generate XLSX report", e);
            throw new IllegalStateException("Failed to generate XLSX report", e);
        }
    }

    private int writeCompanyHeader(Sheet sheet, CellStyle boldStyle, int rowIndex, ReportContext context) {
        Row companyRow = sheet.createRow(rowIndex++);
        Cell companyCell = companyRow.createCell(0);
        companyCell.setCellValue("Empresa: " + safeText(context.companyName()));
        companyCell.setCellStyle(boldStyle);

        Row cnpjRow = sheet.createRow(rowIndex++);
        Cell cnpjCell = cnpjRow.createCell(0);
        cnpjCell.setCellValue("CNPJ: " + formatCnpj(context.cnpj()));
        cnpjCell.setCellStyle(boldStyle);

        Row dateRow = sheet.createRow(rowIndex++);
        Cell dateCell = dateRow.createCell(0);
        dateCell.setCellValue("Data de emissão " + LocalDate.now().format(DATE_FMT));
        dateCell.setCellStyle(boldStyle);

        return rowIndex;
    }

    private void writeTableHeader(Sheet sheet, CellStyle headerStyle, int rowIndex) {
        Row headerRow = sheet.createRow(rowIndex);
        for (int i = 0; i < COLUMN_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(COLUMN_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeNFeColumns(Row row, NFeDetailsHeader header) {
        row.createCell(0).setCellValue(safeText(header.number()));
        row.createCell(1).setCellValue(safeText(header.accessKey()));
        row.createCell(2).setCellValue(safeText(header.issueDate()));
        row.createCell(3).setCellValue(safeText(header.emitter()));
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createBoldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String safeText(String text) {
        return text != null ? text : "-";
    }

    private String formatCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) return safeText(cnpj);
        return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." +
                cnpj.substring(5, 8) + "/" + cnpj.substring(8, 12) + "-" + cnpj.substring(12);
    }
}
