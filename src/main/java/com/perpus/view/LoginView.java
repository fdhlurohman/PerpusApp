package com.perpus.view;

import com.perpus.dao.PenggunaDAO;
import com.perpus.model.Pengguna;
import com.perpus.session.UserSession;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

public class LoginView {
    private final PenggunaDAO penggunaDAO = new PenggunaDAO();
    private final Runnable onLoginSuccess;
    private final Runnable onOpenRegister;

    public LoginView(Runnable onLoginSuccess, Runnable onOpenRegister) {
        this.onLoginSuccess = onLoginSuccess;
        this.onOpenRegister = onOpenRegister;
    }

    public Parent getView() {
        StackPane root = new StackPane();
        root.getStyleClass().add("auth-root");

        ImageView bookshelf = UiIcons.image("bookshelf.png", 900);
        bookshelf.fitWidthProperty().bind(root.widthProperty());
        bookshelf.setMouseTransparent(true);
        bookshelf.setOpacity(0.28);
        StackPane.setAlignment(bookshelf, Pos.BOTTOM_CENTER);

        // Center Login Card
        VBox card = new VBox(14);
        card.getStyleClass().add("auth-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxSize(440, Region.USE_PREF_SIZE);

        HBox emblemRow = new HBox(8);
        emblemRow.setAlignment(Pos.CENTER_LEFT);

        ImageView appLogo = UiIcons.image("perpus-icon.png", 32);

        Label appTitle = new Label("Perpustakaan");
        appTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
        emblemRow.getChildren().addAll(appLogo, appTitle);

        Label cardTitle = new Label("Login");
        cardTitle.getStyleClass().add("auth-title");

        Label userLabel = new Label("Username");
        userLabel.getStyleClass().add("form-label");
        TextField userField = new TextField();
        userField.setPromptText("Username");

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("form-label");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Password");

        Button loginBtn = new Button("Masuk");
        loginBtn.getStyleClass().add("auth-btn-dark");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setDefaultButton(true);
        userField.setOnAction(event -> loginBtn.fire());
        passField.setOnAction(event -> loginBtn.fire());

        HBox footerRow = new HBox(4);
        footerRow.setAlignment(Pos.CENTER);
        Label askLabel = new Label("Belum punya akun?");
        askLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        Button registerLink = new Button("Daftar");
        registerLink.getStyleClass().add("auth-link-btn");
        registerLink.setOnAction(e -> onOpenRegister.run());
        footerRow.getChildren().addAll(askLabel, registerLink);

        Label msg = new Label();
        msg.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        msg.setWrapText(true);
        msg.setMaxWidth(360);

        loginBtn.setOnAction(e -> {
            msg.setText("");
            String username = userField.getText();
            String password = passField.getText();
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                showLoginError(msg, "Username dan password wajib diisi");
                return;
            }

            loginBtn.setDisable(true);
            loginBtn.setText("Memeriksa...");
            registerLink.setDisable(true);
            userField.setDisable(true);
            passField.setDisable(true);

            Task<Pengguna> loginTask = new Task<>() {
                @Override
                protected Pengguna call() throws Exception {
                    return penggunaDAO.login(username, password);
                }
            };
            loginTask.setOnSucceeded(event -> {
                restoreLoginControls(loginBtn, registerLink, userField, passField);
                Pengguna user = loginTask.getValue();
                if (user == null) {
                    showLoginError(msg, "Username atau password salah");
                    return;
                }
                UserSession.getInstance().setCurrentUser(user);
                onLoginSuccess.run();
            });
            loginTask.setOnFailed(event -> {
                restoreLoginControls(loginBtn, registerLink, userField, passField);
                Throwable error = loginTask.getException();
                String detail = error == null || error.getMessage() == null
                        ? "Kesalahan tidak diketahui"
                        : error.getMessage();
                showLoginError(msg, "Gagal memproses login: " + detail);
            });

            Thread worker = new Thread(loginTask, "user-login");
            worker.setDaemon(true);
            worker.start();
        });

        card.getChildren().addAll(emblemRow, cardTitle, userLabel, userField, passLabel, passField, loginBtn, footerRow, msg);

        root.getChildren().addAll(bookshelf, card);
        return root;
    }

    private void restoreLoginControls(Button loginButton, Button registerButton,
                                      TextField usernameField, PasswordField passwordField) {
        loginButton.setDisable(false);
        loginButton.setText("Masuk");
        registerButton.setDisable(false);
        usernameField.setDisable(false);
        passwordField.setDisable(false);
    }

    private void showLoginError(Label message, String text) {
        message.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
        message.setText(text);
    }
}
