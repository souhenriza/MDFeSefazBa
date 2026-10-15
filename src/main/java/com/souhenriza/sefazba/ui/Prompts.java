package com.souhenriza.sefazba.ui;

import org.jline.prompt.ConfirmResult;
import org.jline.prompt.PromptBuilder;
import org.jline.prompt.PromptResult;
import org.jline.prompt.Prompter;
import org.jline.prompt.PrompterConfig;
import org.jline.prompt.PrompterFactory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One-question-at-a-time wrappers over JLine's jline-prompt. Every method returns {@code null} when the user
 * presses Esc, which the menu treats as "go back". Ctrl+C surfaces as JLine's UserInterruptException.
 */
public class Prompts implements AutoCloseable {

    private static final String KEY = "answer";
    private static final int PAGE_SIZE = 15;

    private final Terminal terminal;
    private final Prompter prompter;

    public Prompts() {
        try {
            this.terminal = TerminalBuilder.builder().system(true).build();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open the terminal", e);
        }
        this.prompter = PrompterFactory.create(terminal, PrompterConfig.defaults().withCancellableFirstPrompt(true));
    }

    /**
     * Arrow-key list. {@code options} maps the returned key to the label shown, in display order.
     */
    public String select(String message, Map<String, String> options) {
        PromptBuilder builder = prompter.newBuilder();
        var list = builder.createListPrompt().name(KEY).message(message).pageSize(PAGE_SIZE)
                // Echo the chosen label, not the internal key
                .transformer(key -> options.getOrDefault(key, key));
        options.forEach(list::add);
        list.addPrompt();
        return ask(builder);
    }

    /**
     * Filters as the user types; returns {@code value} of the chosen item.
     */
    public <T> String search(String message, String placeholder, Function<String, List<T>> search,
                             Function<T, String> display, Function<T, String> value) {
        PromptBuilder builder = prompter.newBuilder();
        builder.<T>createSearchPrompt().name(KEY).message(message)
                .placeholder(placeholder)
                .searchFunction(search)
                .displayFunction(display)
                .valueFunction(value)
                .minSearchLength(0)
                .maxResults(PAGE_SIZE)
                .addPrompt();
        return ask(builder);
    }

    public String input(String message, String defaultValue) {
        PromptBuilder builder = prompter.newBuilder();
        var input = builder.createInputPrompt().name(KEY).message(message);
        if (defaultValue != null && !defaultValue.isEmpty()) {
            input.defaultValue(defaultValue);
        }
        input.addPrompt();
        String answer = ask(builder);
        return answer == null ? null : answer.trim();
    }

    public String password(String message) {
        PromptBuilder builder = prompter.newBuilder();
        builder.createPasswordPrompt().name(KEY).message(message).mask('*').addPrompt();
        return ask(builder);
    }

    /**
     * Esc counts as "no".
     */
    public boolean confirm(String message, boolean defaultYes) {
        PromptBuilder builder = prompter.newBuilder();
        builder.createConfirmPrompt().name(KEY).message(message).defaultValue(defaultYes).addPrompt();
        return askRaw(builder) instanceof ConfirmResult confirm && confirm.isConfirmed();
    }

    public void pause() {
        System.out.println();
        input("Enter para voltar ao menu", null);
    }

    /**
     * Clears the screen through the terminal's own capability, so it also works on the Windows console.
     */
    public void clearScreen() {
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.flush();
    }

    private String ask(PromptBuilder builder) {
        PromptResult<?> result = askRaw(builder);
        return result == null ? null : result.getResult();
    }

    private PromptResult<?> askRaw(PromptBuilder builder) {
        try {
            Map<String, ? extends PromptResult<?>> answers = prompter.prompt(List.of(), builder.build());
            return answers == null ? null : answers.get(KEY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException ignored) {
        }
    }
}
