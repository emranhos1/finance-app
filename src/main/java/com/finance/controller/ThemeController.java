package com.finance.controller;

import com.finance.theme.ThemeManager;
import com.finance.theme.ThemeManager.Theme;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Controller;

/** The Theme tab: one card per theme, a click applies it. */
@Controller
public class ThemeController {

    @FXML
    private FlowPane themeCardsPane;

    @FXML
    public void initialize() {
        showCards();
    }

    private void showCards() {
        themeCardsPane.getChildren().clear();
        Theme current = ThemeManager.getCurrent();
        for (Theme theme : Theme.values()) {
            themeCardsPane.getChildren().add(buildCard(theme, theme == current));
        }
    }

    private VBox buildCard(Theme theme, boolean selected) {
        Label name = new Label(theme.getDisplayName());
        name.getStyleClass().add("theme-card-name");
        Label desc = new Label(theme.getDescription());
        desc.getStyleClass().add("theme-card-desc");
        desc.setWrapText(true);
        Label state = new Label(selected ? "In use" : "Click to use");
        state.getStyleClass().add(selected ? "theme-card-check" : "theme-card-desc");

        VBox card = new VBox(8, preview(theme), name, desc, state);
        card.getStyleClass().add("theme-card");
        if (selected) card.getStyleClass().add("theme-card-selected");
        card.setPrefWidth(250);
        card.setOnMouseClicked(e -> {
            ThemeManager.setTheme(theme);
            showCards();
        });
        return card;
    }

    /** A small picture of the theme: sidebar strip, page with a card and an accent bar. */
    private HBox preview(Theme theme) {
        Region sidebar = block(theme.getSidebarColor(), 38, 112);
        sidebar.setStyle("-fx-background-color: " + theme.getSidebarColor() + ";");

        Region card = block(theme.getSurfaceColor(), 90, 38);
        Region accent = block(theme.getAccentColor(), 60, 10);
        Region card2 = block(theme.getSurfaceColor(), 90, 28);
        VBox pageContent = new VBox(8, accent, card, card2);
        pageContent.setAlignment(Pos.TOP_LEFT);
        pageContent.setStyle("-fx-padding: 10; -fx-background-color: " + theme.getPageColor() + ";");
        pageContent.setMinSize(180, 112);
        pageContent.setPrefSize(180, 112);
        pageContent.setMaxSize(180, 112);

        HBox box = new HBox(sidebar, pageContent);
        box.setStyle("-fx-border-color: #00000033;");
        box.setMinWidth(218);
        box.setMaxWidth(218);
        return box;
    }

    private Region block(String color, double width, double height) {
        Region r = new Region();
        r.setMinSize(width, height);
        r.setPrefSize(width, height);
        r.setMaxSize(width, height);
        r.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
        return r;
    }
}
