package com.finance.theme;

import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Applies the chosen colour theme to every window of the app.
 * main.css (shapes and the default colours) goes first, the theme file after it overrides the colour variables.
 * The choice is kept per computer in the user preferences.
 */
public final class ThemeManager {

    public enum Theme {
        EMERALD("emerald", "Emerald", "Clean white cards on a deep green sidebar.",
                "#14463a", "#216e59", "#ffffff", "#f3f8f6");

        private final String id;
        private final String displayName;
        private final String description;
        private final String sidebarColor;
        private final String accentColor;
        private final String surfaceColor;
        private final String pageColor;

        Theme(String id, String displayName, String description,
              String sidebarColor, String accentColor, String surfaceColor, String pageColor) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.sidebarColor = sidebarColor;
            this.accentColor = accentColor;
            this.surfaceColor = surfaceColor;
            this.pageColor = pageColor;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        /** Colours for the small preview on the Theme tab. */
        public String getSidebarColor() { return sidebarColor; }
        public String getAccentColor() { return accentColor; }
        public String getSurfaceColor() { return surfaceColor; }
        public String getPageColor() { return pageColor; }

        static Theme fromId(String id) {
            for (Theme t : values()) if (t.id.equals(id)) return t;
            return EMERALD;
        }
    }

    /** Scenes with this property (the splash screen) are left alone. */
    public static final String NO_THEME = "dfms.no-theme";

    private static final String PREF_KEY = "ui.theme";
    private static final String THEME_PATH = "/css/themes/";
    private static final String MAIN_CSS = "/css/main.css";

    private static Theme current;
    private static boolean installed;
    private static Runnable onThemeChanged;

    private ThemeManager() { }

    public static Theme getCurrent() {
        if (current == null) {
            current = Theme.fromId(prefs().get(PREF_KEY, Theme.EMERALD.getId()));
        }
        return current;
    }

    /** Call once on the JavaFX thread. Themes every window that is open now or opens later (dialogs, alerts). */
    public static void install() {
        if (installed) return;
        installed = true;
        loadBundledFonts();
        for (Window w : new ArrayList<>(Window.getWindows())) track(w);
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Window w : change.getAddedSubList()) track(w);
                }
            }
        });
    }

    /** Changes the theme, saves the choice and re-styles every open window. */
    public static void setTheme(Theme theme) {
        current = theme;
        try {
            prefs().put(PREF_KEY, theme.getId());
        } catch (RuntimeException e) {
            // the choice just is not remembered for the next start
            System.err.println("Could not save the theme choice: " + e.getMessage());
        }
        for (Window w : new ArrayList<>(Window.getWindows())) {
            if (w.getScene() != null) apply(w.getScene());
        }
        if (onThemeChanged != null) onThemeChanged.run();
    }

    /**
     * Picks the FXML for the current theme: /fxml/themes/{theme}/{File}.fxml when that file exists,
     * otherwise the standard /fxml/{File}.fxml. A theme only needs files for the screens it lays out differently.
     */
    public static String resolveFxml(String standardPath) {
        Theme theme = getCurrent();
        if (theme != Theme.EMERALD && standardPath.startsWith("/fxml/")) {
            String themed = "/fxml/themes/" + theme.getId() + "/" + standardPath.substring("/fxml/".length());
            if (ThemeManager.class.getResource(themed) != null) return themed;
        }
        return standardPath;
    }

    /** The app shell (navigation) differs per theme, so the Dashboard window rebuilds itself on a change. */
    public static void setOnThemeChanged(Runnable listener) {
        onThemeChanged = listener;
    }

    /** Adds main.css and the current theme to a scene. Safe to call more than once. */
    public static void apply(Scene scene) {
        if (scene == null || scene.getProperties().containsKey(NO_THEME)) return;
        List<String> sheets = scene.getStylesheets();
        String main = url(MAIN_CSS);
        sheets.removeIf(s -> s.contains(THEME_PATH));
        if (main != null && !sheets.contains(main)) sheets.add(0, main);
        String theme = url(THEME_PATH + getCurrent().getId() + ".css");
        if (theme != null) sheets.add(theme);
    }

    /**
     * Fonts shipped inside the app (Inter, SIL Open Font License, see fonts/inter/LICENSE.txt).
     * JavaFX needs one static file per weight: a variable font file loads as a single weight and loses bold.
     */
    private static void loadBundledFonts() {
        for (String file : new String[]{"Inter-Regular.ttf", "Inter-Bold.ttf"}) {
            try (java.io.InputStream in = ThemeManager.class.getResourceAsStream("/fonts/inter/" + file)) {
                if (in != null) javafx.scene.text.Font.loadFont(in, 12);
            } catch (java.io.IOException e) {
                System.err.println("Could not load font " + file + ": " + e.getMessage());
            }
        }
    }

    private static void track(Window window) {
        // popups (combo boxes, the date range popup) inherit the styles of their owner
        if (window instanceof PopupWindow) return;
        if (window.getScene() != null) apply(window.getScene());
        window.sceneProperty().addListener((obs, oldScene, newScene) -> apply(newScene));
    }

    private static String url(String path) {
        URL u = ThemeManager.class.getResource(path);
        return u != null ? u.toExternalForm() : null;
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(ThemeManager.class);
    }
}
