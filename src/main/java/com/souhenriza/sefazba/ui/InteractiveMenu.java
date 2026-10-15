package com.souhenriza.sefazba.ui;

import com.souhenriza.sefazba.AppContext;
import com.souhenriza.sefazba.SefazBaApplication;
import com.souhenriza.sefazba.application.dto.CompanyResponse;
import com.souhenriza.sefazba.application.dto.CreateCompanyRequest;
import com.souhenriza.sefazba.application.dto.ExtractionBatchSummaryResponse;
import com.souhenriza.sefazba.application.dto.UpdateCompanyRequest;
import com.souhenriza.sefazba.cli.CommandLine;
import com.souhenriza.sefazba.domain.model.ExtractionResult;
import com.souhenriza.sefazba.domain.model.StartedBatch;
import com.souhenriza.sefazba.domain.model.company.Cnpj;
import com.souhenriza.sefazba.infrastructure.lock.ExtractionLock;
import com.souhenriza.sefazba.shared.enums.ReportFormat;
import com.souhenriza.sefazba.shared.enums.SearchPeriod;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;
import org.jspecify.annotations.NonNull;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static com.souhenriza.sefazba.cli.Output.*;

/**
 * Arrow-key menu opened when sefazba runs without arguments. Each action asks what it needs, runs on the same
 * services as the command line, prints the result and returns to the menu; errors are shown and the menu goes on.
 */
public class InteractiveMenu {

    private static final DateTimeFormatter INPUT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final AppContext context;

    public InteractiveMenu(AppContext context) {
        this.context = context;
    }

    public void run() {
        try (Prompts prompts = new Prompts()) {
            new Session(prompts).loop();
        } catch (UserInterruptException e) {
            System.out.println();
        }
    }

    /* Per-run state; split from the outer class so every action can use the open prompts. */
    private final class Session {

        private final Prompts prompts;

        Session(Prompts prompts) {
            this.prompts = prompts;
        }

        void loop() {
            firstRunSetup();
            while (true) {
                // Each return to the menu starts on a clean screen with freshly loaded numbers
                prompts.clearScreen();
                printHeader();

                Map<String, String> options = createOptions();

                String choice = prompts.select("O que deseja fazer?", options);

                if (choice == null) {
                    continue;
                }
                if (choice.equals("exit")) {
                    return;
                }
                runAction(choice);
            }
        }

        private static @NonNull Map<String, String> createOptions() {
            Map<String, String> options = new LinkedHashMap<>();
            options.put("extract", "Extrair NF-e de uma empresa");
            options.put("extract-all", "Extrair todas as empresas");
            options.put("extract-commercial", "Extrair empresas com intenção comercial");
            options.put("resume", "Retomar lote interrompido");
            options.put("batches", "Ver lotes de extração");
            options.put("extracts", "Extrações recentes");
            options.put("companies", "Empresas ▸");
            options.put("login", "Testar login na SEFAZ");
            options.put("folder", "Abrir pasta de dados e relatórios");
            options.put("flush", "Limpar o terminal");
            options.put("exit", "Sair");
            return options;
        }

        private void runAction(String choice) {
            this.runSafely(switch (choice) {
                case "extract" -> this::extractOne;
                case "extract-all" -> this::extractAll;
                case "extract-commercial" -> this::extractCommercial;
                case "resume" -> this::resumeBatch;
                case "batches" -> this::showBatches;
                case "extracts" -> () -> {
                    printExtracts(context.extractResultService.findRecent(20));
                    return true;
                };
                case "companies" -> this::companies;
                case "login" -> this::testLogin;
                case "folder" -> this::openDataFolder;
                default -> () -> false;
            });
        }

        /**
         * Runs one action; it returns whether there is output to read before the menu is shown again.
         */
        private void runSafely(Supplier<Boolean> action) {
            try {
                if (action.get()) {
                    prompts.pause();
                }
            } catch (UserInterruptException e) {
                throw e;
            } catch (RuntimeException e) {
                SefazBaApplication.logError(e);
                prompts.pause();
            }
        }

        private void printHeader() {
            String currentUser = System.getProperty("user.name");
            System.out.println("Olá " + currentUser);
            System.out.println(SefazBaApplication.version());
            System.out.println("Dados em: " + context.config().baseDir());
            System.out.println(statusLine());
            System.out.println("Use as setas e Enter para escolher; Esc volta.");
            System.out.println();
        }

        /**
         * Re-queried on every redraw, so it reflects what the last action changed.
         */
        private String statusLine() {
            int companies = context.companyService.findAll().size();
            List<ExtractionBatchSummaryResponse> lastBatch = context.batchService.findRecent(1);
            String batch = lastBatch.isEmpty()
                    ? "nenhum lote ainda"
                    : "último lote #" + lastBatch.getFirst().id() + " " + lastBatch.getFirst().status()
                    + " (" + lastBatch.getFirst().success() + "/" + lastBatch.getFirst().total() + " ok)";
            return companies + " empresa(s) cadastrada(s) | " + batch;
        }

        /**
         * Runs once, before the first redraw clears the screen; the prompts below keep the notice on screen until
         * the user answers.
         */
        private void firstRunSetup() {
            if (!context.config().firstRun()) {
                return;
            }
            prompts.clearScreen();
            printHeader();
            System.out.println("Primeira execução: a pasta de dados foi criada.");
            System.out.println("Faça backup de " + Path.of(context.config().keyPath(), "secret-key.json")
                    + " - sem ela as senhas cadastradas não podem ser lidas.");
            System.out.println();
            if (context.companyService.findAll().isEmpty()
                    && prompts.confirm("Nenhuma empresa cadastrada. Importar de um arquivo JSON agora?", true)) {
                runSafely(this::importCompanies);
            } else {
                prompts.pause();
            }
        }

        private boolean extractOne() {
            CompanyResponse company = chooseCompany();
            if (company == null) {
                return false;
            }
            PeriodChoice period = choosePeriod(true);
            if (period == null) {
                return false;
            }
            ReportChoice report = chooseReport();
            if (report == null) {
                return false;
            }

            warnAboutBrowserDownload();

            ExtractionResult result = withLock(() -> period.custom()
                    ? context.extractionService.extractByDateRange(company.id(), period.start(), period.end(),
                            report.generate(), report.format(), report.outputPath())
                    : context.extractionService.extractByPeriod(company.id(), period.period(),
                            report.generate(), report.format(), report.outputPath()));
            printExtraction(result);
            return true;
        }

        private boolean extractAll() {
            PeriodChoice period = choosePeriod(false);
            if (period == null) {
                return false;
            }
            ReportChoice report = chooseReport();
            if (report == null) {
                return false;
            }
            int total = context.companyService.findAll().size();
            if (!prompts.confirm("Extrair " + total + " empresa(s) de " + period.label() + "?", false)) {
                return false;
            }

            warnAboutBrowserDownload();
            StartedBatch batch = withLock(() -> context.extractionService.startExtractAll(period.period(),
                    report.generate(), report.generate() ? report.format() : ReportFormat.PDF, report.outputPath()));
            printBatchDetail(context.batchService.findDetail(batch.batchId()));
            return true;
        }

        private boolean extractCommercial() {
            PeriodChoice period = choosePeriod(false);
            if (period == null) {
                return false;
            }
            int total = context.companyService.getAllEntitiesWithCommercialIntention().size();
            if (!prompts.confirm("Extrair " + total + " empresa(s) com intenção comercial de "
                    + period.label() + "?", false)) {
                return false;
            }

            warnAboutBrowserDownload();
            StartedBatch batch = withLock(() -> context.extractionService.startCommercialIntention(period.period()));
            printBatchDetail(context.batchService.findDetail(batch.batchId()));
            return true;
        }

        private boolean resumeBatch() {
            // Taking the lock first turns batches left RUNNING by a dead process into resumable ones
            try (ExtractionLock ignored = context.acquireExtractionLock()) {
                List<ExtractionBatchSummaryResponse> resumable = context.batchService.findRecent(50).stream()
                        .filter(batch -> batch.status().isResumable())
                        .toList();
                if (resumable.isEmpty()) {
                    System.out.println("Nenhum lote interrompido ou com erros para retomar.");
                    return true;
                }

                Map<String, String> options = new LinkedHashMap<>();
                resumable.forEach(batch -> options.put(String.valueOf(batch.id()), batchLabel(batch)));
                String choice = prompts.select("Qual lote retomar?", options);
                if (choice == null) {
                    return false;
                }

                warnAboutBrowserDownload();
                StartedBatch batch = context.extractionService.resumeBatch(Integer.valueOf(choice));
                printBatchDetail(context.batchService.findDetail(batch.batchId()));
                return true;
            }
        }

        private boolean showBatches() {
            List<ExtractionBatchSummaryResponse> batches = context.batchService.findRecent(20);
            if (batches.isEmpty()) {
                System.out.println("Nenhum lote de extração ainda.");
                return true;
            }
            printBatches(batches);
            System.out.println();

            Map<String, String> options = new LinkedHashMap<>();
            batches.forEach(batch -> options.put(String.valueOf(batch.id()), batchLabel(batch)));
            String choice = prompts.select("Ver os detalhes de qual lote? (Esc volta)", options);
            if (choice == null) {
                return false;
            }
            printBatchDetail(context.batchService.findDetail(Integer.valueOf(choice)));
            return true;
        }

        private boolean testLogin() {
            CompanyResponse company = chooseCompany();
            if (company == null) {
                return false;
            }
            warnAboutBrowserDownload();
            withLock(() -> {
                context.sessionService.login(company.id());
                return null;
            });
            System.out.println("Login realizado com sucesso.");
            return true;
        }

        private <T> T withLock(Supplier<T> action) {
            try (ExtractionLock ignored = context.acquireExtractionLock()) {
                return action.get();
            }
        }

        private boolean companies() {
            Map<String, String> options = new LinkedHashMap<>();
            options.put("list", "Listar empresas");
            options.put("add", "Cadastrar empresa");
            options.put("import", "Importar empresas de um arquivo JSON");
            options.put("edit", "Editar empresa");
            options.put("delete", "Remover empresa");
            String choice = prompts.select("Empresas", options);
            if (choice == null) {
                return false;
            }
            return switch (choice) {
                case "list" -> {
                    printCompanies(context.companyService.findAll());
                    yield true;
                }
                case "add" -> addCompany();
                case "import" -> importCompanies();
                case "edit" -> editCompany();
                case "delete" -> deleteCompany();
                default -> false;
            };
        }

        private boolean addCompany() {
            String legalName = askRequired("Razão social");
            if (legalName == null) return false;
            String tradeName = prompts.input("Nome fantasia (opcional)", null);
            if (tradeName == null) return false;
            String cnpj = askCnpj(null);
            if (cnpj == null) return false;
            Integer dominioId = askInt("Código no Domínio Sistemas", null);
            if (dominioId == null) return false;
            String login = askRequired("Login SEFAZ");
            if (login == null) return false;
            String password = prompts.password("Senha SEFAZ");
            if (password == null || password.isBlank()) return false;
            Integer responsible = askInt("ID do usuário responsável", 1);
            if (responsible == null) return false;
            boolean commercialIntention = prompts.confirm("Tem intenção comercial?", false);
            String outputPath = prompts.input("Pasta preferida para os relatórios (opcional)", null);
            if (outputPath == null) return false;

            List<CompanyResponse> created = context.companyService.create(List.of(new CreateCompanyRequest(
                    legalName, tradeName, cnpj, dominioId, login, password, responsible.longValue(),
                    commercialIntention, outputPath.isBlank() ? null : outputPath)));
            System.out.println("Empresa cadastrada.");
            printCompanies(created);
            return true;
        }

        private boolean importCompanies() {
            String path = prompts.input("Caminho do arquivo JSON (formato de main_company.json)", null);
            if (path == null || path.isBlank()) {
                return false;
            }
            File file = new File(path.replace("\"", ""));
            if (!file.isFile()) {
                System.out.println("Arquivo não encontrado: " + file.getAbsolutePath());
                return true;
            }
            List<CompanyResponse> created = context.companyService.create(CommandLine.readCompanies(file));
            System.out.println(created.size() + " empresa(s) cadastrada(s).");
            printCompanies(created);
            return true;
        }

        private boolean editCompany() {
            CompanyResponse company = chooseCompany();
            if (company == null) return false;
            System.out.println("Enter mantém o valor atual.");

            String legalName = prompts.input("Razão social", company.legalName());
            if (legalName == null) return false;
            String tradeName = prompts.input("Nome fantasia", company.tradeName());
            if (tradeName == null) return false;
            String cnpj = askCnpj(company.cnpj());
            if (cnpj == null) return false;
            Integer dominioId = askInt("Código no Domínio Sistemas", company.dominioSistemasId());
            if (dominioId == null) return false;
            String login = prompts.input("Novo login SEFAZ (vazio mantém)", null);
            if (login == null) return false;
            String password = prompts.password("Nova senha SEFAZ (vazio mantém)");
            if (password == null) return false;
            boolean commercialIntention = prompts.confirm("Tem intenção comercial?",
                    Boolean.TRUE.equals(company.commercialIntention()));

            CompanyResponse updated = context.companyService.update(company.id(), new UpdateCompanyRequest(
                    legalName, tradeName, cnpj, dominioId, login, password, null, commercialIntention));
            System.out.println("Empresa atualizada.");
            printCompanies(List.of(updated));
            return true;
        }

        private boolean deleteCompany() {
            CompanyResponse company = chooseCompany();
            if (company == null) return false;
            if (!prompts.confirm("Remover " + company.legalName() + "?", false)) {
                return false;
            }
            context.companyService.delete(company.id());
            System.out.println("Empresa removida.");
            return true;
        }

        // ==================== shared questions ====================

        /**
         * Type-to-filter search: with ~100 companies a plain list would be too long to scroll.
         */
        private CompanyResponse chooseCompany() {
            List<CompanyResponse> all = context.companyService.findAll();
            if (all.isEmpty()) {
                System.out.println("Nenhuma empresa cadastrada. Use Empresas > Cadastrar ou Importar.");
                return null;
            }
            String id = prompts.search("Empresa", "digite nome, CNPJ ou ID",
                    query -> matching(all, query),
                    c -> truncate(c.legalName(), 50) + "  ·  " + c.cnpj(),
                    c -> String.valueOf(c.id()));
            if (id == null) {
                return null;
            }
            return all.stream().filter(c -> String.valueOf(c.id()).equals(id)).findFirst().orElse(null);
        }

        private static List<CompanyResponse> matching(List<CompanyResponse> companies, String query) {
            String needle = normalize(query);
            String digits = needle.replaceAll("\\D", "");
            return companies.stream()
                    .filter(c -> needle.isEmpty()
                            || normalize(c.legalName()).contains(needle)
                            || normalize(c.tradeName()).contains(needle)
                            || (!digits.isEmpty() && c.cnpj().contains(digits))
                            || String.valueOf(c.id()).equals(needle))
                    .toList();
        }

        private PeriodChoice choosePeriod(boolean allowCustom) {
            LocalDate lastMonth = SearchPeriod.LAST_MONTH.startDate();
            Map<String, String> options = new LinkedHashMap<>();
            options.put("LAST_MONTH", "Mês passado (" + MONTH.format(lastMonth) + ")");
            options.put("CURRENT_MONTH", "Mês atual (" + MONTH.format(LocalDate.now()) + ", até ontem)");
            if (allowCustom) {
                options.put("CUSTOM", "Outro período…");
            }
            String choice = prompts.select("Período", options);
            if (choice == null) {
                return null;
            }
            if (!choice.equals("CUSTOM")) {
                SearchPeriod period = SearchPeriod.valueOf(choice);
                return new PeriodChoice(period, period.startDate(), period.endDate(), options.get(choice));
            }

            LocalDate start = askDate("Data inicial (dd/mm/aaaa)");
            if (start == null) return null;
            LocalDate end = askDate("Data final (dd/mm/aaaa)");
            if (end == null) return null;
            if (start.isAfter(end)) {
                System.out.println("A data inicial é depois da final.");
                return null;
            }
            return new PeriodChoice(SearchPeriod.CUSTOM, start, end,
                    INPUT_DATE.format(start) + " a " + INPUT_DATE.format(end));
        }

        private ReportChoice chooseReport() {
            Map<String, String> options = new LinkedHashMap<>();
            options.put("NONE", "Não gerar relatório");
            options.put("PDF", "PDF");
            options.put("XLSX", "Excel (XLSX)");
            options.put("BOTH", "PDF e Excel");
            String choice = prompts.select("Relatório", options);
            if (choice == null) {
                return null;
            }
            if (choice.equals("NONE")) {
                return new ReportChoice(false, null, null);
            }
            String outputPath = prompts.input("Pasta onde salvar", context.config().fallbackReportsDir());
            if (outputPath == null || outputPath.isBlank()) {
                return null;
            }
            return new ReportChoice(true, ReportFormat.valueOf(choice), outputPath);
        }

        private String askRequired(String message) {
            while (true) {
                String value = prompts.input(message, null);
                if (value == null || !value.isBlank()) {
                    return value;
                }
                System.out.println("Campo obrigatório.");
            }
        }

        private String askCnpj(String current) {
            while (true) {
                String value = prompts.input("CNPJ", current);
                if (value == null) {
                    return null;
                }
                try {
                    return new Cnpj(value).value();
                } catch (IllegalArgumentException e) {
                    System.out.println("CNPJ inválido: " + e.getMessage());
                }
            }
        }

        private Integer askInt(String message, Integer current) {
            while (true) {
                String value = prompts.input(message, current == null ? null : String.valueOf(current));
                if (value == null) {
                    return null;
                }
                try {
                    return Integer.valueOf(value);
                } catch (NumberFormatException e) {
                    System.out.println("Informe um número.");
                }
            }
        }

        private LocalDate askDate(String message) {
            while (true) {
                String value = prompts.input(message, null);
                if (value == null) {
                    return null;
                }
                try {
                    return LocalDate.parse(value, INPUT_DATE);
                } catch (Exception e) {
                    System.out.println("Data inválida; use dd/mm/aaaa.");
                }
            }
        }

        private boolean openDataFolder() {
            Path folder = context.config().baseDir();
            System.out.println("Dados:       " + folder);
            System.out.println("Relatórios:  " + context.config().fallbackReportsDir());
            System.out.println("Logs:        " + folder.resolve("logs"));
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(folder.toFile());
                    return false;
                }
            } catch (Exception e) {
                // No graphical session (e.g. over SSH): the paths printed above are enough
            }
            return true;
        }

        /**
         * Playwright downloads its browser the first time one is launched; without a warning that looks like a hang.
         */
        private void warnAboutBrowserDownload() {
            String custom = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
            Path cache = custom != null ? Path.of(custom)
                    : System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")
                    ? Path.of(System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")), "ms-playwright")
                    : Path.of(System.getProperty("user.home"), ".cache", "ms-playwright");
            if (!Files.isDirectory(cache)) {
                System.out.println("Primeiro uso: o navegador de automação será baixado (~100 MB). Aguarde...");
            }
        }

        private static String batchLabel(ExtractionBatchSummaryResponse batch) {
            return "#" + batch.id() + "  " + batch.periodLabel() + "  " + format(batch.startedAt()) + "  "
                    + batch.status() + "  (" + batch.success() + "/" + batch.total() + " ok)";
        }

        private static String normalize(String text) {
            if (text == null) {
                return "";
            }
            return Normalizer.normalize(text, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT)
                    .trim();
        }
    }

    private record PeriodChoice(SearchPeriod period, LocalDate start, LocalDate end, String label) {
        boolean custom() {
            return period == SearchPeriod.CUSTOM;
        }
    }

    private record ReportChoice(boolean generate, ReportFormat format, String outputPath) {
    }

    public void clear(Terminal terminal) {

        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.flush();
    }
}
