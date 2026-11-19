package com.finance.util;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.net.URL;

/**
 * The app's own title bar (metallic emerald, see ".app-titlebar" in main.css) for an UNDECORATED stage.
 * Wrap the screen with {@link #wrap}: the result is the scene root, bar on top, the screen below.
 * The bar shows the stage's logo and title, drags the window, and has minimize, maximize/restore and close.
 * Edge resizing is done here too, because an undecorated window has none.
 */
public final class WindowChrome {
    public static final double BAR_HEIGHT = 34;
    private static final double EDGE = 6;

    private final Stage stage;
    private final boolean canMaximize;
    private boolean maximized;
    private Rectangle2D restoreBounds;
    private SVGPath maxIcon;

    private double dragOffsetX, dragOffsetY;
    private int resizeZone; // bit flags: 1 left, 2 right, 4 top, 8 bottom
    private double startX, startY, startW, startH, startMouseX, startMouseY;

    private WindowChrome(Stage stage, boolean canMaximize) {
        this.stage = stage;
        this.canMaximize = canMaximize;
    }

    /** The stage must be created/initialised with StageStyle.UNDECORATED before it is first shown. */
    public static Parent wrap(Stage stage, Parent content, boolean canMaximize) {
        return new WindowChrome(stage, canMaximize).build(content);
    }

    private Parent build(Parent content) {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-titlebar");

        URL logoUrl = WindowChrome.class.getResource("/images/logo.png");
        if (logoUrl != null) {
            ImageView logo = new ImageView(new Image(logoUrl.toExternalForm(), 18, 18, true, true));
            bar.getChildren().add(logo);
        }
        Label title = new Label();
        title.textProperty().bind(stage.titleProperty());
        title.getStyleClass().add("app-titlebar-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(title, spacer);

        bar.getChildren().add(button(new SVGPath(), "M0,5 L10,5", e -> stage.setIconified(true), false));
        if (canMaximize) {
            maxIcon = new SVGPath();
            bar.getChildren().add(button(maxIcon, MAX_ICON, e -> toggleMaximize(), false));
        }
        bar.getChildren().add(button(new SVGPath(), "M0,0 L10,10 M10,0 L0,10", e -> stage.close(), true));

        bar.setOnMousePressed(e -> {
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
        });
        bar.setOnMouseDragged(e -> {
            if (maximized || e.getButton() != MouseButton.PRIMARY) return;
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });
        bar.setOnMouseClicked(e -> {
            if (canMaximize && e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) toggleMaximize();
        });

        // A resizable window (Dashboard): the content must be allowed to shrink with the window. Otherwise its computed
        // minimum height (the sidebar) is taller than a short window, the content area overflows below the window edge,
        // its scroll panes get a viewport taller than what is visible and the bottom of every form is cut with no scrolling.
        if (canMaximize && content instanceof Region) ((Region) content).setMinHeight(0);

        BorderPane root = new BorderPane(content);
        root.setTop(bar);
        root.getStyleClass().add("app-window");
        installEdgeResize(root);
        return root;
    }

    private static final String MAX_ICON = "M0,0 H10 V10 H0 Z";
    private static final String RESTORE_ICON = "M2,0 H10 V8 M0,2 H8 V10 H0 Z";

    static Button button(SVGPath icon, String path, javafx.event.EventHandler<javafx.event.ActionEvent> action, boolean close) {
        icon.setContent(path);
        icon.setFill(Color.TRANSPARENT);
        icon.setStroke(Color.WHITE);
        icon.setStrokeWidth(1.2);
        Button b = new Button();
        b.setGraphic(icon);
        b.setFocusTraversable(false);
        b.getStyleClass().add("tb-btn");
        if (close) b.getStyleClass().add("tb-close");
        b.setOnAction(action);
        return b;
    }

    /** Maximize to the usable screen area (above the taskbar), restore to the previous bounds. */
    private void toggleMaximize() {
        if (!canMaximize) return;
        if (maximized) {
            stage.setX(restoreBounds.getMinX());
            stage.setY(restoreBounds.getMinY());
            stage.setWidth(restoreBounds.getWidth());
            stage.setHeight(restoreBounds.getHeight());
            maximized = false;
            if (maxIcon != null) maxIcon.setContent(MAX_ICON);
        } else {
            restoreBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            Rectangle2D vb = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight())
                    .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
            stage.setX(vb.getMinX());
            stage.setY(vb.getMinY());
            stage.setWidth(vb.getWidth());
            stage.setHeight(vb.getHeight());
            maximized = true;
            if (maxIcon != null) maxIcon.setContent(RESTORE_ICON);
        }
    }

    private int zone(MouseEvent e, Region root) {
        if (!stage.isResizable() || maximized) return 0;
        int z = 0;
        if (e.getSceneX() < EDGE) z |= 1;
        if (e.getSceneX() > root.getWidth() - EDGE) z |= 2;
        if (e.getSceneY() < EDGE) z |= 4;
        if (e.getSceneY() > root.getHeight() - EDGE) z |= 8;
        return z;
    }

    private static Cursor cursorFor(int z) {
        switch (z) {
            case 1: case 2: return Cursor.H_RESIZE;
            case 4: case 8: return Cursor.V_RESIZE;
            case 5: case 10: return Cursor.NW_RESIZE;
            case 6: case 9: return Cursor.NE_RESIZE;
            default: return null;
        }
    }

    private void installEdgeResize(BorderPane root) {
        root.addEventFilter(MouseEvent.MOUSE_MOVED, e -> root.setCursor(cursorFor(zone(e, root))));
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            resizeZone = zone(e, root);
            if (resizeZone == 0) return;
            startX = stage.getX(); startY = stage.getY();
            startW = stage.getWidth(); startH = stage.getHeight();
            startMouseX = e.getScreenX(); startMouseY = e.getScreenY();
            e.consume();
        });
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (resizeZone == 0) return;
            double dx = e.getScreenX() - startMouseX, dy = e.getScreenY() - startMouseY;
            double minW = Math.max(stage.getMinWidth(), 200), minH = Math.max(stage.getMinHeight(), BAR_HEIGHT + 100);
            if ((resizeZone & 2) != 0) stage.setWidth(Math.max(minW, startW + dx));
            if ((resizeZone & 8) != 0) stage.setHeight(Math.max(minH, startH + dy));
            if ((resizeZone & 1) != 0) {
                double w = Math.max(minW, startW - dx);
                stage.setX(startX + startW - w);
                stage.setWidth(w);
            }
            if ((resizeZone & 4) != 0) {
                double h = Math.max(minH, startH - dy);
                stage.setY(startY + startH - h);
                stage.setHeight(h);
            }
            e.consume();
        });
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (resizeZone != 0) { resizeZone = 0; e.consume(); }
        });
    }
}
