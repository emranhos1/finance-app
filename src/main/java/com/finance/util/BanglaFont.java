package com.finance.util;

import javafx.scene.control.Labeled;

/**
 * Bold Bangla text shows as empty boxes: JavaFX has no bold font with Bangla glyphs on Windows (the bold
 * Segoe UI / Vrinda / theme fonts lack them, and there is no bold Nirmala UI file), while the normal weight falls back to
 * Nirmala UI correctly. A label that can hold Bangla data and is bold (banners, account type names, chips, ...) is
 * passed to {@link #apply}: whenever its text contains Bangla it uses Nirmala UI (which shows the Bangla, without the
 * bold look), and goes back to the normal font when the text has none.
 */
public final class BanglaFont {
    private static final String BANGLA_STYLE = "-fx-font-family: \"Nirmala UI\";";

    private BanglaFont() {}

    /** True if the text has a character of the Bengali block (U+0980 to U+09FF). */
    public static boolean hasBangla(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0980 && c <= 0x09FF) return true;
        }
        return false;
    }

    /** Keeps the label's own inline style and adds the Bangla font on top while its text has Bangla. */
    public static void apply(Labeled label) {
        if (label == null) return;
        String base = label.getStyle() == null ? "" : label.getStyle();
        Runnable update = () -> label.setStyle(hasBangla(label.getText()) ? base + ";" + BANGLA_STYLE : base);
        label.textProperty().addListener((o, a, b) -> update.run());
        update.run();
    }
}
