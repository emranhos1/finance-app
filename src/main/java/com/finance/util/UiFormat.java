package com.finance.util;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Display-only helpers: number format (24,500.00) and table cell factories (amounts, chips).
 * Cell factories only change how a cell looks; the column values, sorting and sums keep using the raw values.
 */
public final class UiFormat {

    public static final String CURRENCY = "৳";

    /** Colour of an amount: income/credit/given = POS, expense/debit/taken = NEG, anything else = NEUTRAL. */
    public enum Tone { POS, NEG, NEUTRAL }

    private UiFormat() { }

    /** 24,500.00 (grouped thousands, 2 decimals, same in every OS locale). */
    public static String number(BigDecimal value) {
        if (value == null) return "";
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(value);
    }

    public static String number(double value) {
        return number(BigDecimal.valueOf(value));
    }

    /** "৳ 24,500.00" */
    public static String money(BigDecimal value) {
        return value == null ? "" : CURRENCY + " " + number(value);
    }

    public static String money(double value) {
        return CURRENCY + " " + number(value);
    }

    /** Tone by sign: positive = POS, negative = NEG, zero = NEUTRAL. */
    public static Tone sign(BigDecimal v) {
        if (v == null) return Tone.NEUTRAL;
        int s = v.signum();
        return s > 0 ? Tone.POS : s < 0 ? Tone.NEG : Tone.NEUTRAL;
    }

    private static void applyTone(TableCell<?, ?> cell, Tone tone) {
        cell.getStyleClass().removeAll("amount", "amount-pos", "amount-neg");
        if (tone == null) return;
        cell.getStyleClass().add("amount");
        if (tone == Tone.POS) cell.getStyleClass().add("amount-pos");
        else if (tone == Tone.NEG) cell.getStyleClass().add("amount-neg");
    }

    /** Right-aligned BigDecimal column shown as 24,500.00 with the tone chosen per row. */
    public static <S> void amountColumn(TableColumn<S, BigDecimal> column, BiFunction<S, BigDecimal, Tone> tone) {
        column.setCellFactory(col -> new TableCell<S, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    applyTone(this, null);
                    return;
                }
                S row = getTableRow() == null ? null : getTableRow().getItem();
                setText(number(item));
                setAlignment(Pos.CENTER_RIGHT);
                applyTone(this, tone == null ? Tone.NEUTRAL : tone.apply(row, item));
            }
        });
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    /** Neutral (uncoloured) right-aligned amount column. */
    public static <S> void amountColumn(TableColumn<S, BigDecimal> column) {
        amountColumn(column, (row, v) -> Tone.NEUTRAL);
    }

    /** Right-aligned column whose text is already formatted (for example the Calculator). */
    public static <S> void amountTextColumn(TableColumn<S, String> column, Function<S, Tone> tone) {
        column.setCellFactory(col -> new TableCell<S, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    applyTone(this, null);
                    return;
                }
                S row = getTableRow() == null ? null : getTableRow().getItem();
                setText(item);
                setAlignment(Pos.CENTER_RIGHT);
                applyTone(this, tone == null || row == null ? Tone.NEUTRAL : tone.apply(row));
            }
        });
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    /** Right-aligned integer column (counts). */
    public static <S> void countColumn(TableColumn<S, Number> column) {
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    /**
     * Shows the cell value as a chip (a small rounded label).
     * label maps the raw value to the shown text, chipClass to the style class ("chip-income", ...), or null for a plain chip.
     */
    public static <S, T> void chipColumn(TableColumn<S, T> column,
                                         Function<T, String> label, Function<T, String> chipClass) {
        column.setCellFactory(col -> new TableCell<S, T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label chip = new Label(label == null ? String.valueOf(item) : label.apply(item));
                chip.getStyleClass().add("chip");
                BanglaFont.apply(chip);   // the chip is bold; an account type name can be Bangla
                String extra = chipClass == null ? null : chipClass.apply(item);
                if (extra != null) chip.getStyleClass().addAll(extra.trim().split("\s+"));
                setText(null);
                setGraphic(chip);
                setAlignment(Pos.CENTER_LEFT);
            }
        });
    }

    /** "INCOME" -> "Income" (first letter upper, rest lower). */
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ENGLISH) + s.substring(1).toLowerCase(Locale.ENGLISH);
    }

    /** Chip class for INCOME / EXPENSE / TRANSFER style words (any case); null for anything else. */
    public static String typeChipClass(String value) {
        if (value == null) return null;
        switch (value.toUpperCase(Locale.ENGLISH)) {
            case "INCOME": case "CREDIT": return "chip-income";
            case "EXPENSE": case "DEBIT": return "chip-expense";
            case "TRANSFER": return "chip-transfer";
            default: return null;
        }
    }

    private static String themeId() {
        try {
            return com.finance.theme.ThemeManager.getCurrent().getId();
        } catch (RuntimeException e) {
            return "emerald";
        }
    }

    /** Midnight and Bento show a Transfer type as an uppercase chip, the other boards as plain "Transfer". */
    public static String transferTypeLabel(String value) {
        String id = themeId();
        return "midnight".equals(id) || "bento".equals(id) ? (value == null ? null : value.toUpperCase(Locale.ENGLISH)) : capitalize(value);
    }

    /**
     * Loan status as the board of the current theme words it: Open, and Closed (Emerald, Paper) or Settled (the others).
     * Midnight also shows an open loan that is partly repaid as Partial. Display only: the stored status is not changed.
     */
    public static String loanStatusLabel(String status) {
        if ("SETTLED".equalsIgnoreCase(status)) {
            String id = themeId();
            return "emerald".equals(id) || "paper".equals(id) ? "Closed" : "Settled";
        }
        if ("PARTIAL".equalsIgnoreCase(status)) return "midnight".equals(themeId()) ? "Partial" : "Open";
        return capitalize(status);
    }

    public static String loanStatusChipClass(String status) {
        return "SETTLED".equalsIgnoreCase(status) ? "chip-closed" : "chip-open";
    }

    /**
     * Runs update now and again every time the items of the table change (also when the whole list is replaced).
     * Used for footers and stat tiles that are worked out from the rows the table already holds.
     */
    public static <S> void watchItems(javafx.scene.control.TableView<S> table, Runnable update) {
        if (table == null || update == null) return;
        javafx.collections.ListChangeListener<S> onChange = c -> update.run();
        if (table.getItems() != null) table.getItems().addListener(onChange);
        table.itemsProperty().addListener((obs, oldItems, newItems) -> {
            if (oldItems != null) oldItems.removeListener(onChange);
            if (newItems != null) newItems.addListener(onChange);
            update.run();
        });
        update.run();
    }

    /** Sets the text of a label that a layout may leave out. */
    public static void setText(Label label, String text) {
        if (label != null) label.setText(text);
    }

    /** Wraps a page in a transparent ScrollPane: it fills the window when roomy and scrolls when the window is short. */
    public static javafx.scene.control.ScrollPane scrollable(javafx.scene.Parent content) {
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("dash-scroll");
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return sp;
    }
}
