package com.finance.util;

import javafx.scene.control.Dialog;

/** A Dialog with the app's own title bar, see {@link DialogChrome}. */
public class AppDialog<R> extends Dialog<R> {
    public AppDialog() {
        super();
        DialogChrome.apply(this);
    }
}
