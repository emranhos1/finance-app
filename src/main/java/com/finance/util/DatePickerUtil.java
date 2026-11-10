package com.finance.util;

import javafx.scene.control.DatePicker;

import java.time.LocalDate;

public final class DatePickerUtil {

    private DatePickerUtil() {
    }

    /** The picker keeps its old value when the text is cleared, so a blank text field counts as no date. */
    public static LocalDate readDate(DatePicker picker) {
        String text = picker.getEditor().getText();
        return text == null || text.isBlank() ? null : picker.getValue();
    }
}
