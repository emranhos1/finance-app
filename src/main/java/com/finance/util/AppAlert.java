package com.finance.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/** An Alert with the app's own title bar, see {@link DialogChrome}. */
public class AppAlert extends Alert {
    public AppAlert(AlertType type) {
        super(type);
        DialogChrome.apply(this);
    }

    public AppAlert(AlertType type, String contentText, ButtonType... buttons) {
        super(type, contentText, buttons);
        DialogChrome.apply(this);
    }
}
