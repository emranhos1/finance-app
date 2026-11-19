package com.finance.util;

import javafx.scene.control.TextInputDialog;

/** A TextInputDialog with the app's own title bar, see {@link DialogChrome}. */
public class AppTextInputDialog extends TextInputDialog {
    public AppTextInputDialog(String defaultValue) {
        super(defaultValue);
        DialogChrome.apply(this);
    }
}
