package com.sit.tracker.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Thin console input/output helper. All user interaction goes through here,
 * so the menus stay free of parsing noise and input is validated in one place.
 */
public class ConsoleIO {

    private static final DateTimeFormatter DATE_IN = DateTimeFormatter.ISO_LOCAL_DATE;
    private final Scanner in = new Scanner(System.in);

    // ---------- output ----------

    public void line(String text) {
        System.out.println(text);
    }

    public void blank() {
        System.out.println();
    }

    public void title(String text) {
        blank();
        line("=== " + text + " ===");
    }

    public void rule() {
        line("-".repeat(78));
    }

    public void error(String message) {
        line("  ! " + message);
    }

    public void ok(String message) {
        line("  > " + message);
    }

    // ---------- input ----------

    /**
     * Read one line (trimmed). Empty lines return "". Throws
     * {@link EndOfInputException} on end of input (piped stdin or Ctrl-D) so
     * the app can shut down cleanly instead of looping forever.
     */
    public String ask(String prompt) {
        System.out.print(prompt + " ");
        if (!in.hasNextLine()) {
            throw new EndOfInputException();
        }
        return in.nextLine().trim();
    }

    public String askRequired(String prompt) {
        while (true) {
            String value = ask(prompt);
            if (!value.isEmpty()) {
                return value;
            }
            line("  ! A value is required.");
        }
    }

    public int askInt(String prompt, int min, int max) {
        while (true) {
            String raw = askRequired(prompt);
            try {
                int value = Integer.parseInt(raw);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through to error message
            }
            line("  ! Please enter a number between " + min + " and " + max + ".");
        }
    }

    public double askDouble(String prompt) {
        while (true) {
            String raw = askRequired(prompt);
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                line("  ! Please enter a number, e.g. 2.5");
            }
        }
    }

    /** Ask for a date (yyyy-MM-dd); blank input returns today. */
    public LocalDate askDate(String prompt) {
        while (true) {
            String raw = ask(prompt + " [yyyy-MM-dd, empty = today]");
            if (raw.isEmpty()) {
                return LocalDate.now();
            }
            try {
                return LocalDate.parse(raw, DATE_IN);
            } catch (DateTimeParseException e) {
                line("  ! Invalid date format - use yyyy-MM-dd, e.g. 2026-11-23");
            }
        }
    }

    /** Ask for a date that may be left blank; blank input returns null. */
    public LocalDate askOptionalDate(String prompt) {
        while (true) {
            String raw = ask(prompt + " [yyyy-MM-dd, empty = unknown]");
            if (raw.isEmpty()) {
                return null;
            }
            try {
                return LocalDate.parse(raw, DATE_IN);
            } catch (DateTimeParseException e) {
                line("  ! Invalid date format - use yyyy-MM-dd, e.g. 2026-11-23");
            }
        }
    }

    /** Numbered choice from an enum; returns the selected constant. */
    public <E extends Enum<E>> E askEnum(String prompt, E[] values) {
        for (int i = 0; i < values.length; i++) {
            line("  " + (i + 1) + ". " + enumLabel(values[i]));
        }
        int choice = askInt(prompt, 1, values.length);
        return values[choice - 1];
    }

    private String enumLabel(Object value) {
        if (value instanceof com.sit.tracker.model.Urgency u) {
            return u.getLabel() + " (target response: " + u.getTargetResponseHours() + "h)";
        }
        try {
            return (String) value.getClass().getMethod("getLabel").invoke(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    public void pause() {
        System.out.print("\n[Press Enter to continue]");
        try {
            if (in.hasNextLine()) {
                in.nextLine();
            }
        } catch (NoSuchElementException ignored) {
            // end of input in scripted runs
        }
    }

    /** Signals clean shutdown when stdin closes (used by scripted runs). */
    public static class EndOfInputException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public EndOfInputException() {
            super("End of input");
        }
    }
}
