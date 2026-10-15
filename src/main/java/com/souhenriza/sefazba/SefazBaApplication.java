package com.souhenriza.sefazba;

import com.souhenriza.sefazba.cli.CommandLine;
import com.souhenriza.sefazba.domain.exceptions.*;
import com.souhenriza.sefazba.domain.model.InvalidCompanyException;
import com.souhenriza.sefazba.infrastructure.config.AppConfig;
import com.souhenriza.sefazba.ui.InteractiveMenu;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Entry point. Without arguments it opens the interactive menu (what the installer's shortcut runs); with arguments
 * it runs a single subcommand and exits, for scripts and scheduled tasks.
 */
@Slf4j
public class SefazBaApplication {

    /* The errors the REST exception handlers answered with a 4xx/502: expected outcomes, no stack trace needed. */
    private static final Set<Class<? extends RuntimeException>> EXPECTED_ERRORS = Set.of(
            CompanyNotFoundException.class, CompanyAlreadyExists.class, InvalidCompanyException.class,
            SefazAuthenticationException.class, InvalidUserException.class, ExtractionInProgressException.class,
            CaptchaSolveException.class, BatchNotFoundException.class, BatchNotResumableException.class,
            NoDataFoundException.class, InputAccessKeyException.class, IllegalArgumentException.class);

    public static void main(String[] args) {
        if (args.length > 0 && Set.of("help", "-h", "--help").contains(args[0])) {
            System.out.println(CommandLine.USAGE);
            return;
        }
        if (args.length > 0 && Set.of("version", "-V", "--version").contains(args[0])) {
            System.out.println(version());
            return;
        }

        int exitCode;
        AppContext context = null;
        try {
            context = new AppContext(AppConfig.load());
            AppContext toClose = context;
            // Ctrl+C or a scheduler kill: close the browser; the batch is recovered as INTERRUPTED next time
            Runtime.getRuntime().addShutdownHook(new Thread(toClose::close, "shutdown"));

            if (args.length == 0) {
                new InteractiveMenu(context).run();
            } else {
                new CommandLine(context, args).run();
            }
            exitCode = 0;
        } catch (CommandLine.UsageException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println(CommandLine.USAGE);
            exitCode = 2;
        } catch (RuntimeException e) {
            logError(e);
            exitCode = 1;
        } finally {
            if (context != null) {
                context.close();
            }
        }
        // Playwright keeps non-daemon threads around
        System.exit(exitCode);
    }

    /**
     * Expected errors are logged as a one-line message; anything else with its stack trace.
     */
    public static void logError(RuntimeException e) {
        if (EXPECTED_ERRORS.contains(e.getClass())) {
            log.error(e.getMessage());
        } else {
            log.error("Unexpected error", e);
        }
    }

    public static String version() {
        String version = SefazBaApplication.class.getPackage().getImplementationVersion();
        return "sefazba " + (version != null ? version : "dev")
                + " (Java " + Runtime.version() + ", " + System.getProperty("os.name") + ")";
    }
}
