package com.souhenriza.sefazba.application.service;

import com.souhenriza.sefazba.application.service.CompanyService;
import com.souhenriza.sefazba.application.service.SefazSessionService;
import com.souhenriza.sefazba.domain.exceptions.BatchNotResumableException;
import com.souhenriza.sefazba.domain.exceptions.ExtractionInProgressException;
import com.souhenriza.sefazba.domain.exceptions.NoDataFoundException;
import com.souhenriza.sefazba.domain.model.ExtractionResult;
import com.souhenriza.sefazba.domain.model.ReportContext;
import com.souhenriza.sefazba.domain.model.StartedBatch;
import com.souhenriza.sefazba.domain.model.executions.BatchRequest;
import com.souhenriza.sefazba.domain.model.executions.BatchStatus;
import com.souhenriza.sefazba.domain.model.executions.BatchType;
import com.souhenriza.sefazba.domain.model.executions.ExtractStatus;
import com.souhenriza.sefazba.domain.model.sefaz.NFeDetails;
import com.souhenriza.sefazba.domain.port.browser.NFeGridExtractor;
import com.souhenriza.sefazba.domain.port.utils.NFeDetailsReport;
import com.souhenriza.sefazba.infrastructure.browser.BrowserLockManager;
import com.souhenriza.sefazba.infrastructure.persistence.company.CompanyEntity;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractRepository;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractResultEntity;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchEntity;
import com.souhenriza.sefazba.infrastructure.persistence.extracts.ExtractionBatchItemEntity;
import com.souhenriza.sefazba.shared.enums.ReportFormat;
import com.souhenriza.sefazba.shared.enums.SearchPeriod;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class NFeExtractionService {

    private final SefazSessionService sessionService;
    private final CompanyService companyService;
    private final NFeGridExtractor nfeGridExtractor;
    private final BrowserLockManager lockManager;
    private final Map<ReportFormat, NFeDetailsReport> reportGenerators;
    private final ExtractRepository extractRepository;
    private final ExtractionBatchService batchService;
    private final String fallbackReportsDir;
    private static final String CONSOLIDATED_PDF_FILENAME = "Relatório Consolidado - MDFe.pdf";
    private final boolean genPDFReport;

    public NFeExtractionService(SefazSessionService sessionService,
                                CompanyService companyService,
                                NFeGridExtractor nfeGridExtractor,
                                BrowserLockManager lockManager,
                                List<NFeDetailsReport> reportImplementations, ExtractRepository extractRepository,
                                ExtractionBatchService batchService,
                                String fallbackReportsDir, boolean genPDFReport) {
        this.sessionService = sessionService;
        this.companyService = companyService;
        this.nfeGridExtractor = nfeGridExtractor;
        this.lockManager = lockManager;
        this.reportGenerators = reportImplementations.stream()
                .collect(Collectors.toMap(NFeDetailsReport::supportedFormat, Function.identity()));
        this.extractRepository = extractRepository;
        this.batchService = batchService;
        this.fallbackReportsDir = fallbackReportsDir;
        this.genPDFReport = genPDFReport;
    }


    public ExtractionResult extractByPeriod(Integer companyId, SearchPeriod period,
                                             boolean genReport, ReportFormat reportFormat, String outputPath) {
        ExtractionResult result = extract(companyId, period.toDateRange(), period.name());

        saveExtractResult(companyId, period.startDate(), period.endDate());

        if (genReport) {
            List<String> savedPaths = generateAndSaveReports(companyId, result.details(), reportFormat, outputPath,
                    period.startDate());
            return new ExtractionResult(result.entries(), result.details(), savedPaths);
        }

        return result;
    }

    public ExtractionResult extractByDateRange(Integer companyId, LocalDate start, LocalDate end,
                                                boolean genReport, ReportFormat reportFormat, String outputPath) {
        SearchPeriod.DateRange dateRange = SearchPeriod.DateRange.of(start, end);
        ExtractionResult result = extract(companyId, dateRange, "CUSTOM");

        saveExtractResult(companyId, start, end);

        if (genReport) {
            List<String> savedPaths = generateAndSaveReports(companyId, result.details(), reportFormat, outputPath,
                    start);
            return new ExtractionResult(result.entries(), result.details(), savedPaths);
        }

        return result;
    }

    public StartedBatch startExtractAll(SearchPeriod period, boolean genReport, ReportFormat reportFormat,
                                        String outputPath) {
        BatchRequest request = new BatchRequest(BatchType.ALL_COMPANIES,
                "Extração de todas as empresas | " + period.name(), period.name(),
                period.startDate(), period.endDate(), genReport, reportFormat, outputPath);
        return startBatch(request, companyService::getAllEntities);
    }

    public StartedBatch startCommercialIntention(SearchPeriod period) {
        BatchRequest request = new BatchRequest(BatchType.COMMERCIAL_INTENTION,
                "Extração de empresas com intenção comercial | " + period.name(), period.name(),
                period.startDate(), period.endDate(), true, null, null);
        return startBatch(request, companyService::getAllEntitiesWithCommercialIntention);
    }

    public StartedBatch resumeBatch(Integer batchId) {
        ExtractionBatchEntity batch = batchService.getBatch(batchId);
        if (!batch.getStatus().isResumable()) {
            throw new BatchNotResumableException(batchId, batch.getStatus());
        }

        List<ExtractionBatchItemEntity> items = batchService.getRetryableItems(batchId);
        log.info("Resuming extraction batch {} | {} companies to reprocess", batchId, items.size());
        LocalDateTime requestedAt = LocalDateTime.now();
        run(batchService.reopen(batchId), items);

        return new StartedBatch(batchId, batch.getDescription(), requestedAt, items.size());
    }

    public StartedBatch resumeLatestBatch() {
        return resumeBatch(batchService.getLatestResumableBatch().getId());
    }

    private StartedBatch startBatch(BatchRequest request, Supplier<List<CompanyEntity>> companies) {
        List<CompanyEntity> batchCompanies = companies.get();
        LocalDateTime requestedAt = LocalDateTime.now();
        ExtractionBatchEntity batch = batchService.createBatch(request, batchCompanies);
        run(batch, batchService.getRetryableItems(batch.getId()));

        return new StartedBatch(batch.getId(), batch.getDescription(), requestedAt, batchCompanies.size());
    }

    private void run(ExtractionBatchEntity batch, List<ExtractionBatchItemEntity> items) {
        try {
            runItems(batch, items);
        } catch (Exception e) {
            log.error("Extraction batch {} aborted: {}", batch.getId(), e.getMessage(), e);
            batchService.interrupt(batch.getId());
        }
    }

    private void runItems(ExtractionBatchEntity batch, List<ExtractionBatchItemEntity> items) {
        int succeeded = 0;
        List<CompanyEntity> processedCompanies = new ArrayList<>();

        for (ExtractionBatchItemEntity item : items) {
            batchService.markRunning(item.getId());
            try {
                CompanyEntity companyEntity = companyService.getEntity(item.getCompanyId());
                processedCompanies.add(companyEntity);

                ExtractionResult result = batch.getBatchType() == BatchType.COMMERCIAL_INTENTION
                        ? processCommercialIntentionCompany(companyEntity, batch.getStartDate(), batch.getEndDate(),
                                batch.getPeriodLabel())
                        : processCompany(companyEntity, batch);

                batchService.markSuccess(item.getId(), result.savedReportPaths());
                succeeded++;
                log.info("Extraction for {} completed successfully", companyEntity.getLegalName());
            } catch (Exception e) {
                batchService.markError(item.getId(), e);
                log.error("Extraction failed for company {}: {}", item.getCompanyName(), e.getMessage());
            }
        }

        BatchStatus finalStatus = batchService.finishBatch(batch.getId());
        log.info("Extraction batch {} finished with status {}: {}/{} companies succeeded in this run",
                batch.getId(), finalStatus, succeeded, items.size());

        if (batch.getBatchType() == BatchType.COMMERCIAL_INTENTION) {
            resolvePathThroughListOfCompanyEntity(processedCompanies, batch.getStartDate())
                    .ifPresentOrElse(
                            // Joined so the JVM does not exit mid-merge; a failure is already logged by concatPdfForGroups
                            directory -> concatPdfForGroups(directory).exceptionally(error -> null).join(),
                            () -> log.info("No company with MDF-e communication found; skipping consolidated PDF"));
        }
    }

    private ExtractionResult processCompany(CompanyEntity companyEntity, ExtractionBatchEntity batch) {
        ExtractionResult result = extract(companyEntity.getId(),
                SearchPeriod.DateRange.of(batch.getStartDate(), batch.getEndDate()), batch.getPeriodLabel());
        saveExtractResult(companyEntity.getId(), batch.getStartDate(), batch.getEndDate());

        if (!batch.isGenReport()) {
            return result;
        }
        List<String> savedPaths = generateAndSaveReports(companyEntity.getId(), result.details(),
                batch.getReportFormat(), batch.getOutputPath(), batch.getStartDate());
        return new ExtractionResult(result.entries(), result.details(), savedPaths);
    }

    private ExtractionResult processCommercialIntentionCompany(CompanyEntity companyEntity, LocalDate start,
                                                               LocalDate end, String periodLabel) {
        ExtractionResult result;
        try {
            result = extract(companyEntity.getId(), SearchPeriod.DateRange.of(start, end), periodLabel);
        } catch (NoDataFoundException e) {
            // An empty period is a valid outcome: the company simply had no NF-e to report.
            log.info("No NF-e found for {} between {} and {}; nothing to report", companyEntity.getLegalName(), start, end);
            saveExtractResult(companyEntity.getId(), start, end);
            return new ExtractionResult(List.of(), List.of());
        }
        saveExtractResult(companyEntity.getId(), start, end);

        ReportFormat format = hasMDFeCommunicationWithCostumer(companyEntity) ? ReportFormat.BOTH : ReportFormat.XLSX;
        String preferOutputPath = companyEntity.getPreferOutputPath();
        String outputPath = preferOutputPath != null && !preferOutputPath.isBlank() && Path.of(preferOutputPath).toFile().exists()
                ? preferOutputPath
                : fallbackReportsDir;

        List<String> savedPaths = generateAndSaveReports(companyEntity.getId(), result.details(), format, outputPath, start);
        return new ExtractionResult(result.entries(), result.details(), savedPaths);
    }

    private void saveExtractResult(Integer companyId, LocalDate startDate, LocalDate endDate) {
        CompanyEntity companyEntity = companyService.getEntity(companyId);
        ExtractResultEntity entity = new ExtractResultEntity();
        entity.setCompany(companyEntity.getLegalName());
        entity.setPeriod(startDate.toString().concat("-").concat(endDate.toString()));
        entity.setExtractedAt(LocalDateTime.now(Clock.systemDefaultZone()));
        entity.setDocument("Vinculações de MDF-e");
        entity.setExtractStatus(ExtractStatus.OK);
        extractRepository.save(entity);
    }

    private Optional<Path> resolvePathThroughListOfCompanyEntity(List<CompanyEntity> companyEntities,
                                                                 LocalDate startDate) {
        return companyEntities.stream()
                .filter(this::hasMDFeCommunicationWithCostumer)
                .map(CompanyEntity::getPreferOutputPath)
                .filter(path -> path != null && !path.isBlank())
                .findFirst()
                .map(path -> Path.of(path).resolve(competencyFolder(startDate)).toAbsolutePath());
    }

    //
    private boolean hasMDFeCommunicationWithCostumer(CompanyEntity companyEntity) {
        return genPDFReport;
    }

    private ExtractionResult extract(Integer companyId, SearchPeriod.DateRange dateRange, String periodLabel) {
        if (!lockManager.tryAcquire()) {
            throw new ExtractionInProgressException();
        }

        try {
            sessionService.login(companyId);

            log.info("Starting NF-e extraction for company {} | Period: {} | {} to {}",
                    companyId, periodLabel, dateRange.start(), dateRange.end());

            ExtractionResult result = nfeGridExtractor.extractAll(dateRange);

            log.info("Extraction completed: {} entries found", result.entries().size());
            return result;
        } finally {
            lockManager.release();
        }
    }

    private List<String> generateAndSaveReports(Integer companyId, List<NFeDetails> details,
                                                ReportFormat format, String outputPath, LocalDate startDate) {
        if (details == null || details.isEmpty()) {
            log.info("No outbound NF-e found for company {} in period starting {}; skipping report generation",
                    companyId, startDate);
            return List.of();
        }

        ReportContext context = buildReportContext(companyId, startDate);
        Path outputDir = Path.of(outputPath);

        List<ReportFormat> formatsToGenerate = (format == ReportFormat.BOTH)
                ? List.of(ReportFormat.PDF, ReportFormat.XLSX)
                : List.of(format);

        List<String> savedPaths = new ArrayList<>();
        for (ReportFormat fmt : formatsToGenerate) {
            CompletableFuture<byte[]> reportBytes = generateReport(details, fmt, context);
            String extension = fmt == ReportFormat.XLSX ? ".xlsx" : ".pdf";
            Path filePath = buildFilename(startDate, outputDir, companyId, extension);
            saveToFile(filePath, reportBytes);
            savedPaths.add(filePath.toString());
            log.info("This report be saved in {}", filePath);
        }

        return savedPaths;
    }


    private ReportContext buildReportContext(Integer companyId, LocalDate competencyDate) {
        CompanyEntity entity = companyService.getEntity(companyId);
        return ReportContext.of(entity.getLegalName(), entity.getCnpj(), competencyDate);
    }

    private Path buildFilename(LocalDate startDate, Path outputDir, Integer id, String extension) {
        String competency = competencyFolder(startDate);
        return outputDir
                .resolve(competency)
                .resolve(competency + " - MDFe - " + companyService.getEntity(id).getLegalName() + extension);
    }

    private String competencyFolder(LocalDate startDate) {
        return String.format("%02d.%d", startDate.getMonthValue(), startDate.getYear());
    }

    public CompletableFuture<byte[]> generateReport(List<NFeDetails> details, ReportFormat format, ReportContext context) {
        NFeDetailsReport reportGenerator = reportGenerators.get(format);
        if (reportGenerator == null) {
            throw new IllegalArgumentException("Unsupported report format: " + format);
        }
        log.info("Generating {} report with {} entries", format, details.size());
        return CompletableFuture.supplyAsync(() -> reportGenerator.generate(details, context).join());
    }

    private void saveToFile(Path filePath, CompletableFuture<byte[]> content) {
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, content.join());
            log.info("Report saved to {}", filePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save report to " + filePath, e);
        }
    }

    private CompletableFuture<Void> concatPdfForGroups(Path directory) {
        Path destination = directory.resolve(CONSOLIDATED_PDF_FILENAME);

        PDFMergerUtility mergerUtility = new PDFMergerUtility();
        mergerUtility.setDestinationFileName(destination.toString());

        return CompletableFuture.runAsync(() -> {
            if (!Files.isDirectory(directory)) {
                log.info("Directory {} not found; skipping consolidated PDF", directory);
                return;
            }

            try (Stream<Path> files = Files.list(directory)) {
                List<Path> pdfs = files
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                        .filter(path -> !path.equals(destination))
                        .sorted()
                        .toList();

                if (pdfs.isEmpty()) {
                    log.info("No PDF found in {}; skipping consolidated PDF", directory);
                    return;
                }

                for (Path pdf : pdfs) {
                    mergerUtility.addSource(pdf.toFile());
                }

                mergerUtility.mergeDocuments(IOUtils.createTempFileOnlyStreamCache());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                log.error("Failed to generate consolidated PDF in {}: {}", directory, error.getMessage(), error);
            } else if (Files.exists(destination)) {
                log.info("Consolidated PDF saved to {}", destination);
            }
        });
    }


}
