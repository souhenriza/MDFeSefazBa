package com.souhenriza.sefazba.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.souhenriza.sefazba.AppContext;
import com.souhenriza.sefazba.application.dto.*;
import com.souhenriza.sefazba.domain.model.ExtractionResult;
import com.souhenriza.sefazba.domain.model.StartedBatch;
import com.souhenriza.sefazba.infrastructure.lock.ExtractionLock;
import com.souhenriza.sefazba.shared.enums.ReportFormat;
import com.souhenriza.sefazba.shared.enums.SearchPeriod;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.*;

import static com.souhenriza.sefazba.cli.Output.*;

/**
 * Non-interactive subcommands ({@code sefazba company list}, {@code sefazba extract-all ...}). Kept for scripts and
 * scheduled tasks; running {@code sefazba} without arguments opens the interactive menu instead.
 */
public class CommandLine {

    public static final String USAGE = """
            Uso: sefazba <comando> [opções]
                 sefazba --version | --help

            Os dados (configs.yaml, datas/, keys/, logs/, Tesseract-OCR/) são lidos de $SEFAZBA_HOME,
            ou da pasta atual quando a variável não está definida.

            Empresas
              company list
              company show <id> | company show --cnpj <cnpj>
              company import <arquivo.json>         lista de empresas no formato de main_company.json
              company update <id> [--legal-name X] [--trade-name X] [--cnpj X] [--dominio-id N]
                                  [--login X] [--password] [--responsible-user N]
                                  [--commercial-intention true|false]
                                  --password pede a nova senha no terminal
              company delete <id>

            SEFAZ
              login <companyId>                     testa o login da empresa no portal

            Extração (PERIODO = LAST_MONTH | CURRENT_MONTH | CUSTOM)
              extract <companyId> --period PERIODO [--start AAAA-MM-DD --end AAAA-MM-DD]
                                  [--report PDF|XLSX|BOTH --output DIR]
              extract-all --period PERIODO [--report PDF|XLSX|BOTH --output DIR]
              extract-commercial --period PERIODO

            Lotes e histórico
              batch list [--limit N]
              batch show <id>
              batch resume <id> | batch resume latest
              extracts [--limit N]                  extrações recentes
            """;

    private final AppContext context;
    private final Arguments args;

    public CommandLine(AppContext context, String[] args) {
        this.context = context;
        this.args = new Arguments(args);
    }

    public void run() {
        switch (args.command()) {
            case "company" -> company();
            case "login" -> login();
            case "extract" -> extract();
            case "extract-all" -> extractAll();
            case "extract-commercial" -> extractCommercial();
            case "batch" -> batch();
            case "extracts" -> printExtracts(context.extractResultService.findRecent(args.intOption("--limit", 20)));
            default -> throw new UsageException("Comando desconhecido: " + args.command());
        }
    }

    // ==================== company ====================

    private void company() {
        switch (args.positional(0, "subcomando de company")) {
            case "list" -> printCompanies(context.companyService.findAll());
            case "show" -> {
                String cnpj = args.option("--cnpj");
                CompanyResponse company = cnpj != null
                        ? context.companyService.findByCnpj(cnpj)
                        : context.companyService.findById(args.intPositional(1, "id da empresa"));
                printCompanies(List.of(company));
            }
            case "import" -> {
                File file = new File(args.positional(1, "arquivo JSON"));
                List<CompanyResponse> created = context.companyService.create(readCompanies(file));
                System.out.println(created.size() + " empresa(s) cadastrada(s).");
                printCompanies(created);
            }
            case "update" -> {
                Integer id = args.intPositional(1, "id da empresa");
                String commercialIntention = args.option("--commercial-intention");
                UpdateCompanyRequest request = new UpdateCompanyRequest(
                        args.option("--legal-name"),
                        args.option("--trade-name"),
                        args.option("--cnpj"),
                        args.intOption("--dominio-id"),
                        args.option("--login"),
                        args.flag("--password") ? readPassword() : null,
                        args.longOption("--responsible-user"),
                        commercialIntention == null ? null : Boolean.parseBoolean(commercialIntention));
                printCompanies(List.of(context.companyService.update(id, request)));
            }
            case "delete" -> {
                Integer id = args.intPositional(1, "id da empresa");
                context.companyService.delete(id);
                System.out.println("Empresa " + id + " removida.");
            }
            default -> throw new UsageException("Subcomando desconhecido: company " + args.positional(0, ""));
        }
    }

    public static List<CreateCompanyRequest> readCompanies(File file) {
        try {
            return new ObjectMapper().readValue(file, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + file + ": " + e.getMessage(), e);
        }
    }

    private static String readPassword() {
        Console console = System.console();
        if (console != null) {
            return new String(console.readPassword("Nova senha SEFAZ: "));
        }
        System.out.print("Nova senha SEFAZ: ");
        return new Scanner(System.in).nextLine();
    }

    // ==================== extraction ====================

    private void login() {
        Integer companyId = args.intPositional(0, "id da empresa");
        try (ExtractionLock ignored = context.acquireExtractionLock()) {
            context.sessionService.login(companyId);
        }
        System.out.println("Login realizado com sucesso.");
    }

    private void extract() {
        Integer companyId = args.intPositional(0, "id da empresa");
        String period = args.requiredOption("--period");
        ReportFormat format = reportFormat();
        boolean genReport = format != null;
        String outputPath = genReport ? args.requiredOption("--output") : null;

        ExtractionResult result;
        try (ExtractionLock ignored = context.acquireExtractionLock()) {
            if ("CUSTOM".equalsIgnoreCase(period)) {
                result = context.extractionService.extractByDateRange(companyId,
                        args.dateOption("--start"), args.dateOption("--end"), genReport, format, outputPath);
            } else {
                result = context.extractionService.extractByPeriod(companyId, searchPeriod(period),
                        genReport, format, outputPath);
            }
        }

        printExtraction(result);
    }

    private void extractAll() {
        SearchPeriod period = searchPeriod(args.requiredOption("--period"));
        ReportFormat format = reportFormat();
        boolean genReport = format != null;
        String outputPath = genReport ? args.requiredOption("--output") : null;

        StartedBatch batch;
        try (ExtractionLock ignored = context.acquireExtractionLock()) {
            batch = context.extractionService.startExtractAll(period, genReport,
                    genReport ? format : ReportFormat.PDF, outputPath);
        }
        printBatchDetail(context.batchService.findDetail(batch.batchId()));
    }

    private void extractCommercial() {
        SearchPeriod period = searchPeriod(args.requiredOption("--period"));
        StartedBatch batch;
        try (ExtractionLock ignored = context.acquireExtractionLock()) {
            batch = context.extractionService.startCommercialIntention(period);
        }
        printBatchDetail(context.batchService.findDetail(batch.batchId()));
    }

    private ReportFormat reportFormat() {
        String value = args.option("--report");
        return value == null ? null : ReportFormat.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static SearchPeriod searchPeriod(String value) {
        SearchPeriod period = SearchPeriod.valueOf(value.toUpperCase(Locale.ROOT));
        if (period == SearchPeriod.CUSTOM) {
            throw new UsageException("CUSTOM só é aceito no comando extract; use LAST_MONTH ou CURRENT_MONTH");
        }
        return period;
    }

    // ==================== batches ====================

    private void batch() {
        switch (args.positional(0, "subcomando de batch")) {
            case "list" -> printBatches(context.batchService.findRecent(args.intOption("--limit", 20)));
            case "show" -> printBatchDetail(context.batchService.findDetail(args.intPositional(1, "id do lote")));
            case "resume" -> {
                String target = args.positional(1, "id do lote ou 'latest'");
                StartedBatch batch;
                try (ExtractionLock ignored = context.acquireExtractionLock()) {
                    batch = "latest".equalsIgnoreCase(target)
                            ? context.extractionService.resumeLatestBatch()
                            : context.extractionService.resumeBatch(args.intPositional(1, "id do lote"));
                }
                printBatchDetail(context.batchService.findDetail(batch.batchId()));
            }
            default -> throw new UsageException("Subcomando desconhecido: batch " + args.positional(0, ""));
        }
    }

    // ==================== argument parsing ====================

    public static class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }

    /**
     * {@code <command> [positionals...] [--option value | --flag]...}
     */
    private static final class Arguments {

        private static final Set<String> FLAGS = Set.of("--password");

        private final String command;
        private final List<String> positionals = new ArrayList<>();
        private final Map<String, String> options = new HashMap<>();

        Arguments(String[] args) {
            this.command = args[0];
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                if (!arg.startsWith("--")) {
                    positionals.add(arg);
                } else if (FLAGS.contains(arg)) {
                    options.put(arg, "true");
                } else if (i + 1 < args.length) {
                    options.put(arg, args[++i]);
                } else {
                    throw new UsageException("Falta o valor de " + arg);
                }
            }
        }

        String command() {
            return command;
        }

        String positional(int index, String description) {
            if (index >= positionals.size()) {
                throw new UsageException("Informe o " + description);
            }
            return positionals.get(index);
        }

        Integer intPositional(int index, String description) {
            String value = positional(index, description);
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException e) {
                throw new UsageException("Número inválido para " + description + ": " + value);
            }
        }

        String option(String name) {
            return options.get(name);
        }

        String requiredOption(String name) {
            String value = options.get(name);
            if (value == null) {
                throw new UsageException("Informe " + name);
            }
            return value;
        }

        boolean flag(String name) {
            return options.containsKey(name);
        }

        Integer intOption(String name) {
            String value = options.get(name);
            try {
                return value == null ? null : Integer.valueOf(value);
            } catch (NumberFormatException e) {
                throw new UsageException("Número inválido em " + name + ": " + value);
            }
        }

        int intOption(String name, int defaultValue) {
            Integer value = intOption(name);
            return value == null ? defaultValue : value;
        }

        Long longOption(String name) {
            String value = options.get(name);
            try {
                return value == null ? null : Long.valueOf(value);
            } catch (NumberFormatException e) {
                throw new UsageException("Número inválido em " + name + ": " + value);
            }
        }

        LocalDate dateOption(String name) {
            String value = requiredOption(name);
            try {
                return LocalDate.parse(value);
            } catch (Exception e) {
                throw new UsageException("Data inválida em " + name + " (use AAAA-MM-DD): " + value);
            }
        }
    }
}
