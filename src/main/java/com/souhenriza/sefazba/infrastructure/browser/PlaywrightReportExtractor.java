package com.souhenriza.sefazba.infrastructure.browser;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.souhenriza.sefazba.application.dto.CSVReportFormat;
import com.souhenriza.sefazba.domain.exceptions.DownloadNotAvailableException;
import com.souhenriza.sefazba.domain.model.company.Cnpj;
import com.souhenriza.sefazba.domain.port.browser.NFeSearchSubmitter;
import com.souhenriza.sefazba.shared.enums.SearchPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jspecify.annotations.NonNull;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


@RequiredArgsConstructor
@Slf4j
public class PlaywrightReportExtractor {
    private final NFeSearchSubmitter searchSubmitter;
    private final PlaywrightBrowserProvider browserProvider;


    public void extractRecipientReport(SearchPeriod.DateRange range, String outputPath) {
        int attempt = 0;

        retry(range);
        log.info("O captcha foi resolvido em {} tentativas", attempt + 1);

        Page page = browserProvider.acquirePage();

        page.waitForLoadState();

        Download csvDownload = page.waitForDownload(() -> page.click("#btn_GerarPlanilha"));

        page.onDownload(d -> log.info("downloading {}", d.suggestedFilename()));

        String baseName = resolvePathName(range, page);
        Path xlsDest = Paths.get(outputPath, baseName + ".csv");

        try {
            csvDownload.saveAs(xlsDest);

            List<CSVReportFormat> formats = readCSVReport(xlsDest.toFile());

            convertsCsvToXlsx(formats, xlsDest.toString());

        } catch (DownloadNotAvailableException e) {
            throw new DownloadNotAvailableException(csvDownload);
        }

        Page popup = page.waitForPopup(() -> page.click("#btn_Imprimir"));
        page.onPopup(o -> log.info("opening popup pdf report:  {}", o.title()));

        Page.PdfOptions pdfOptions = new Page.PdfOptions();

        pdfOptions.setPath(Paths.get(outputPath, baseName + ".pdf"))
                .setFormat("A4")
                .setPrintBackground(true);

        popup.pdf(pdfOptions);

    }

    private void retry(SearchPeriod.DateRange range) {
        int attempt = 0;
        do {
            attempt++;
            searchSubmitter.submit(range);
            int MAX_TIMEOUT_RETRIES = 20;
            if (attempt >= MAX_TIMEOUT_RETRIES && !searchSubmitter.isSuccess()) {
                throw new RuntimeException("Failed to solve captcha after " + attempt + " attempts");
            }
        } while (!searchSubmitter.isSuccess());
    }

    private String resolvePathName(SearchPeriod.DateRange range, Page page) {
        return (page.url().contains("emit")) ? String.format("%02d.%d - NFE EMITENTE", range.start().getMonthValue(), range.start().getYear()) :
                String.format("%02d.%d - NFE DESTINATÁRIO", range.start().getMonthValue(), range.start().getYear());

    }


    private List<CSVReportFormat> readCSVReport(File outputPath) {
        List<CSVReportFormat> csvReportFormatList = new ArrayList<>();

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setDelimiter(';')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setAllowMissingColumnNames(true)
                .setTrim(true)
                .get();

        try (InputStream stream = new FileInputStream(outputPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
             CSVParser parser = CSVParser.parse(reader, csvFormat)) {

            for (CSVRecord record : parser) {
                CSVReportFormat csvReportFormat = getCsvValues(record);
                csvReportFormatList.add(csvReportFormat);
            }

        } catch (IOException e) {
            throw new RuntimeException("Não foi possível analisar o documento " + outputPath.getName(), e);
        }

        return csvReportFormatList;
    }

    private void convertsCsvToXlsx(List<CSVReportFormat> csvReportFormatList, String outputPath) {
        String finalOutputPath = outputPath.replace(".csv", "");

        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream stream = new FileOutputStream(finalOutputPath + ".xls")) {
            XSSFSheet sheet = workbook.createSheet();


            CellStyle dateCellStyle = dateFormatCellStyle(workbook);

            drawHeader(workbook, sheet);


            int rowOne = 1;

            for (CSVReportFormat format : csvReportFormatList) {
                Row row = sheet.createRow(rowOne++);

                if (format != null) {
                    int colIndex = 0;
                    String cnpjValue = format.cnpj().value();

                    row.createCell(colIndex++).setCellValue(format.nfeNumber());
                    row.createCell(colIndex++).setCellValue(cnpjValue!= null ? cnpjValue : "");
                    row.createCell(colIndex++).setCellValue(format.issuerName());
                    Cell issueDate = row.createCell(colIndex++);

                    if (format.issueDate() != null) {
                        issueDate.setCellValue(format.issueDate());
                        issueDate.setCellStyle(dateCellStyle);
                    }

                    Cell authDate = row.createCell(colIndex++);
                    if (format.authorizationDate() != null) {
                        authDate.setCellValue(format.authorizationDate());
                        authDate.setCellStyle(dateCellStyle);
                    }
                    Cell valueCell = row.createCell(colIndex++);
                    if (format.value() != null) {
                        valueCell.setCellValue(format.value().doubleValue());
                    } else {
                        valueCell.setCellValue(0.0);
                    }
                    row.createCell(colIndex++).setCellValue(format.accessKey());
                    row.createCell(colIndex++).setCellValue(format.uf());
                    row.createCell(colIndex++).setCellValue(format.situation());
                    row.createCell(colIndex++).setCellValue(format.tpOper());
                }


            }
            adjustColumnWidths(sheet, 10);

            workbook.write(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    private @NonNull CSVReportFormat getCsvValues(CSVRecord record) {
         try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            String nfNumber = record.get(0);
            Cnpj cnpj = new Cnpj(record.get(1));
            String issuerName = record.get(2);
            LocalDateTime issueDate = LocalDateTime.parse(record.get(3), dateTimeFormatter);
            LocalDateTime authorizationDate = LocalDateTime.parse(record.get(4), dateTimeFormatter);
            BigDecimal value = parseBigDecimal(record.get(5));
            String accessKey = record.get(6).replace("'", "").trim();
            String uf = record.get(7);
            String situation = record.get(8).trim();
            String tpOper = record.get(9).trim();


            return new CSVReportFormat(nfNumber, cnpj, issuerName, issueDate, authorizationDate, value, accessKey,
                    uf, situation, tpOper);
        } catch (IllegalArgumentException e) {
             throw new RuntimeException("Não foi possível tratar das conversões de valores: " + e.getMessage());
         }
    }

    private CellStyle dateFormatCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper helper = workbook.getCreationHelper();
        style.setDataFormat(helper.createDataFormat().getFormat("dd/MM/yyyy HH:mm:ss "));

        return style;
    }

    private Font drawHeaderFont(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 12);

        return font;
    }

    private void drawHeader(XSSFWorkbook workbook, Sheet sheet) {
        Row headerRow = sheet.createRow(0);

        Font font = drawHeaderFont(workbook);

        CellStyle headerCellStyle = workbook.createCellStyle();

        headerCellStyle.setFont(font);




        String[] headerColumns = {"Número NFe", "CNPJ/CPF Emitente", "Razão Social Emitente","Data de Emissão", "Data de Autorização", "Valor",
                "Chave de Acesso", "UF Emit.", "Situação", "Tipo Operação"};

        for (int i = 0; i < headerColumns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headerColumns[i]);
            cell.setCellStyle(headerCellStyle);


        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null) {
            return null;
        }

        String formated = value.replace("R$", "")
                .replace(".", "")
                .replace(",", ".");


        return new BigDecimal(formated);
    }

    private void adjustColumnWidths(Sheet sheet, int totalColumns) {
        for (int i = 0; i < totalColumns; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
