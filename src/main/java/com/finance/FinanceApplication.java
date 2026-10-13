package com.finance;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class FinanceApplication extends Application {
    @Getter
    private static ApplicationContext applicationContext;
    private static final CountDownLatch springInitialized = new CountDownLatch(1);

    @Getter
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        applyAppIcon(primaryStage);
        Stage splashStage = new Stage(StageStyle.TRANSPARENT);
        applyAppIcon(splashStage);
        showSplashScreen(splashStage);

        Thread springWaitThread = new Thread(() -> {
            try {
                if (!springInitialized.await(90, TimeUnit.SECONDS)) {
                    System.err.println("ERROR: Spring Boot initialization timeout");
                    Platform.exit();
                    return;
                }
                if (applicationContext == null) {
                    System.err.println("ERROR: Spring application context is null");
                    Platform.exit();
                    return;
                }
                Platform.runLater(() -> {
                    try {
                        splashStage.close();
                        showLoginScreen();
                    } catch (Exception e) {
                        System.err.println("ERROR: Failed to start application");
                        e.printStackTrace();
                        System.exit(1);
                    }
                });
            } catch (Exception e) {
                System.err.println("ERROR: Failed to start application");
                e.printStackTrace();
                System.exit(1);
            }
        });
        springWaitThread.setDaemon(true);
        springWaitThread.start();
    }

    private static void applyAppIcon(Stage stage) {
        if (FinanceApplication.class.getResource("/images/logo.png") != null) {
            stage.getIcons().add(new Image(FinanceApplication.class.getResourceAsStream("/images/logo.png")));
        }
    }

    private static void showSplashScreen(Stage splashStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(FinanceApplication.class.getResource("/fxml/Splash.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 500, 350);
        scene.setFill(Color.TRANSPARENT);
        applyCSS(scene);
        splashStage.setScene(scene);
        splashStage.setResizable(false);
        splashStage.centerOnScreen();
        splashStage.show();
    }

    public static void showLoginScreen() throws Exception {
        FXMLLoader loader = new FXMLLoader(FinanceApplication.class.getResource("/fxml/Login.fxml"));
        loader.setControllerFactory(applicationContext::getBean);
        Parent root = loader.load();

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double width = screenBounds.getWidth() * 0.30;
        double height = screenBounds.getHeight() * 0.40;

        Scene scene = new Scene(root, width, height);
        applyCSS(scene);
        // Hiding before reconfiguring forces Windows to tear down and repaint the native window
        // fresh, instead of resizing/re-showing the already-visible Dashboard window in place -
        // the latter can leave stale Dashboard pixels ghosted behind the new Login content.
        primaryStage.hide();
        primaryStage.setTitle("Daily Finance Management System");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        // A Stage does NOT auto-resize to a new Scene's dimensions once it has already been shown
        // once with an explicit size - it keeps whatever width/height it last had (e.g. still the
        // Dashboard's size here) unless told to resize explicitly.
        primaryStage.setWidth(width);
        primaryStage.setHeight(height);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void showDashboard() throws Exception {
        FXMLLoader loader = new FXMLLoader(FinanceApplication.class.getResource("/fxml/Dashboard.fxml"));
        loader.setControllerFactory(applicationContext::getBean);
        Parent root = loader.load();

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double width = screenBounds.getWidth() * 0.75;
        double height = screenBounds.getHeight() * 0.88;

        Scene scene = new Scene(root, width, height);
        applyCSS(scene);
        // See showLoginScreen() - hide before reconfiguring avoids ghosted stale pixels from the
        // previous (Login) window bleeding through the resized Dashboard window.
        primaryStage.hide();
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(screenBounds.getWidth() * 0.45);
        primaryStage.setMinHeight(screenBounds.getHeight() * 0.45);
        // See showLoginScreen() - a Stage keeps its previous width/height across a setScene() call,
        // so without this the Dashboard would render at the small Login window's size, squeezing
        // and truncating every card on the page.
        primaryStage.setWidth(width);
        primaryStage.setHeight(height);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    private static void applyCSS(Scene scene) {
        String css = FinanceApplication.class.getResource("/css/main.css") != null
                ? FinanceApplication.class.getResource("/css/main.css").toExternalForm()
                : null;
        if (css != null) scene.getStylesheets().add(css);
    }

    public static void main(String[] args) {
        Thread springThread = new Thread(() -> {
            try {
                applicationContext = SpringApplication.run(FinanceApplication.class, args);
                springInitialized.countDown();
            } catch (Exception e) {
                System.err.println("ERROR: Spring Boot failed to initialize");
                e.printStackTrace();
                System.exit(1);
            }
        });
        springThread.setDaemon(true);
        springThread.start();
        launch(args);
    }
}