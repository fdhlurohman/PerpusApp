package com.perpus.view;

import com.perpus.config.ApiClient;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;

public final class SplashScreenView {
    private final Stage owner;
    private final Runnable onReady;
    private final Stage splashStage = new Stage(StageStyle.TRANSPARENT);

    private final Label status = new Label("Memulai aplikasi...");
    private final Label percentage = new Label("0%");
    private final Label errorMessage = new Label();
    private final Button retryButton = new Button("Coba Lagi");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final StackPane bookLogo = new StackPane();
    private final StackPane pixelLogo = new StackPane();
    private final Circle orbit = new Circle(61);

    public SplashScreenView(Stage owner, Runnable onReady) {
        this.owner = owner;
        this.onReady = onReady;
    }

    public void show() {
        StackPane shell = new StackPane();
        shell.setPadding(new Insets(18));
        shell.setStyle("-fx-background-color: transparent;");

        VBox card = new VBox(14);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(28, 38, 24, 38));
        card.setPrefSize(480, 420);
        card.setStyle("-fx-background-color: #0B192C; -fx-background-radius: 22;");
        card.setEffect(new DropShadow(28, Color.rgb(0, 0, 0, 0.55)));

        StackPane logo = createLogo();
        Label title = new Label("Perpustakaan");
        title.getStyleClass().add("splash-title");
        Label subtitle = new Label("Sistem Informasi Perpustakaan");
        subtitle.getStyleClass().add("splash-subtitle");

        status.getStyleClass().add("splash-status");
        status.setWrapText(true);
        status.setMaxWidth(360);
        errorMessage.getStyleClass().add("splash-error");
        errorMessage.setWrapText(true);
        errorMessage.setMaxWidth(360);
        errorMessage.setVisible(false);
        errorMessage.setManaged(false);

        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("splash-progress");
        percentage.getStyleClass().add("splash-percentage");
        HBox progressInfo = new HBox(8, status, percentage);
        progressInfo.setAlignment(Pos.CENTER);

        retryButton.getStyleClass().add("splash-retry-button");
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        retryButton.setOnAction(event -> startConnectionCheck());

        card.getChildren().addAll(logo, title, subtitle, progressBar, progressInfo, errorMessage, retryButton);
        shell.getChildren().add(card);

        Scene scene = new Scene(shell, 516, 456, Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        splashStage.initOwner(owner);
        splashStage.setScene(scene);
        splashStage.setResizable(false);
        splashStage.setTitle("Perpustakaan");
        try (InputStream iconStream = getClass().getResourceAsStream("/icons/perpus-icon.png")) {
            if (iconStream == null) {
                throw new IllegalStateException("Ikon aplikasi tidak ditemukan: /icons/perpus-icon.png");
            }
            Image appIcon = new Image(iconStream);
            if (appIcon.isError()) {
                throw new IllegalStateException("Ikon aplikasi gagal dimuat", appIcon.getException());
            }
            splashStage.getIcons().add(appIcon);
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal membaca ikon aplikasi", ex);
        }
        splashStage.show();
        centerOnOwner();
        animateLogo();
        startConnectionCheck();
    }

    private StackPane createLogo() {
        StackPane logo = new StackPane();
        logo.setMinSize(136, 136);
        logo.setPrefSize(136, 136);
        logo.setMaxSize(136, 136);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        spinner.setPrefSize(98, 98);
        spinner.setMaxSize(98, 98);
        spinner.getStyleClass().add("splash-spinner");

        orbit.setFill(Color.TRANSPARENT);
        orbit.setStroke(Color.web("#00ADB5"));
        orbit.setStrokeWidth(2.2);
        orbit.getStrokeDashArray().setAll(2.5, 7.5);
        orbit.setEffect(new DropShadow(10, Color.rgb(0, 173, 181, 0.8)));

        SVGPath book = new SVGPath();
        book.setContent("M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z");
        book.setFill(Color.web("#E6FCFF"));
        book.setEffect(new DropShadow(16, Color.rgb(0, 173, 181, 0.85)));
        bookLogo.getChildren().add(book);
        bookLogo.setMaxSize(42, 42);

        pixelLogo.setMaxSize(48, 48);
        pixelLogo.setOpacity(0);
        Pane pixels = new Pane();
        int[][] points = {
                {8, 8}, {20, 8}, {32, 8},
                {8, 20}, {20, 20}, {32, 20},
                {8, 32}, {20, 32}, {32, 32}
        };
        for (int index = 0; index < points.length; index++) {
            Rectangle pixel = new Rectangle(8, 8, Color.web(index % 2 == 0 ? "#E6FCFF" : "#00ADB5"));
            pixel.setArcWidth(2);
            pixel.setArcHeight(2);
            pixel.setLayoutX(points[index][0]);
            pixel.setLayoutY(points[index][1]);
            pixel.setEffect(new DropShadow(5, Color.rgb(0, 173, 181, 0.6)));
            pixels.getChildren().add(pixel);
        }
        pixelLogo.getChildren().add(pixels);

        logo.getChildren().addAll(spinner, orbit, bookLogo, pixelLogo);
        return logo;
    }

    private void animateLogo() {
        FadeTransition fade = new FadeTransition(Duration.millis(850), bookLogo);
        fade.setFromValue(0);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(850), bookLogo);
        scale.setFromX(0.65);
        scale.setFromY(0.65);
        scale.setToX(1);
        scale.setToY(1);
        new ParallelTransition(fade, scale).play();

        Timeline orbitAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(orbit.rotateProperty(), 0)),
                new KeyFrame(Duration.seconds(5), new KeyValue(orbit.rotateProperty(), 360)));
        orbitAnimation.setCycleCount(Timeline.INDEFINITE);
        orbitAnimation.play();

        Timeline pixelTransition = new Timeline(
                new KeyFrame(Duration.seconds(1.8),
                        new KeyValue(bookLogo.opacityProperty(), 1),
                        new KeyValue(pixelLogo.opacityProperty(), 0)),
                new KeyFrame(Duration.seconds(2.2),
                        new KeyValue(bookLogo.opacityProperty(), 0.05),
                        new KeyValue(pixelLogo.opacityProperty(), 1)),
                new KeyFrame(Duration.seconds(3.2),
                        new KeyValue(bookLogo.opacityProperty(), 1),
                        new KeyValue(pixelLogo.opacityProperty(), 0)));
        pixelTransition.setCycleCount(Timeline.INDEFINITE);
        pixelTransition.play();
    }

    private void startConnectionCheck() {
        retryButton.setDisable(true);
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        errorMessage.setVisible(false);
        errorMessage.setManaged(false);
        errorMessage.setText("");
        status.setStyle("");

        Task<Void> connectionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Menyiapkan komponen...");
                updateProgress(25, 100);
                Thread.sleep(500);

                updateMessage("Menghubungkan ke layanan perpustakaan...");
                updateProgress(65, 100);
                ApiClient.checkConnection();

                updateMessage("Berhasil terhubung!");
                updateProgress(100, 100);
                Thread.sleep(300);
                return null;
            }
        };

        status.textProperty().unbind();
        percentage.textProperty().unbind();
        progressBar.progressProperty().unbind();
        status.textProperty().bind(connectionTask.messageProperty());
        percentage.textProperty().bind(connectionTask.progressProperty().multiply(100).asString("%.0f%%"));
        progressBar.progressProperty().bind(connectionTask.progressProperty());

        connectionTask.setOnSucceeded(event -> {
            splashStage.close();
            onReady.run();
        });
        connectionTask.setOnFailed(event -> {
            status.textProperty().unbind();
            percentage.textProperty().unbind();
            progressBar.progressProperty().unbind();
            status.setText("Koneksi server gagal");
            status.setStyle("-fx-text-fill: #fca5a5;");
            percentage.setText("");
            progressBar.setProgress(connectionTask.getProgress());
            errorMessage.setText("Gagal terhubung ke server perpustakaan. Periksa internet atau coba lagi.");
            errorMessage.setVisible(true);
            errorMessage.setManaged(true);
            retryButton.setDisable(false);
            retryButton.setVisible(true);
            retryButton.setManaged(true);
        });

        Thread worker = new Thread(connectionTask, "database-connection-check");
        worker.setDaemon(true);
        worker.start();
    }

    private void centerOnOwner() {
        if (owner.isShowing()) {
            splashStage.setX(owner.getX() + (owner.getWidth() - splashStage.getWidth()) / 2);
            splashStage.setY(owner.getY() + (owner.getHeight() - splashStage.getHeight()) / 2);
        } else {
            splashStage.centerOnScreen();
        }
    }
}
