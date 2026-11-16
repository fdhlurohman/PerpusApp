package com.perpus.view;

import com.perpus.dao.PenggunaDAO;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

public class RegisterView {
    private final PenggunaDAO penggunaDAO = new PenggunaDAO();
    private final Runnable onRegisterSuccess;
    private final Runnable onBackToLogin;

    public RegisterView(Runnable onRegisterSuccess, Runnable onBackToLogin) {
        this.onRegisterSuccess = onRegisterSuccess;
        this.onBackToLogin = onBackToLogin;
    }

    public Parent getView() {
        StackPane root = new StackPane();
        root.getStyleClass().add("auth-root");

        VBox card = new VBox(11);
        card.getStyleClass().add("auth-card");
        card.setAlignment(Pos.CENTER_LEFT);

        HBox emblemRow = new HBox(8);
        emblemRow.setAlignment(Pos.CENTER_LEFT);

        ImageView appLogo = UiIcons.image("perpus-icon.png", 32);

        Label appTitle = new Label("Perpustakaan");
        appTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
        emblemRow.getChildren().addAll(appLogo, appTitle);

        Label cardTitle = new Label("Daftar Anggota Baru");
        cardTitle.getStyleClass().add("auth-title");

        Label namaLbl = new Label("Nama");
        namaLbl.getStyleClass().add("form-label");
        TextField namaField = new TextField();

        Label alamatLbl = new Label("Alamat");
        alamatLbl.getStyleClass().add("form-label");
        TextField alamatField = new TextField();
        alamatField.setPromptText("Alamat");

        Label telpLbl = new Label("No Telepon");
        telpLbl.getStyleClass().add("form-label");
        TextField telpField = new TextField();

        Label userLbl = new Label("Username");
        userLbl.getStyleClass().add("form-label");
        TextField userField = new TextField();

        Label passLbl = new Label("Password");
        passLbl.getStyleClass().add("form-label");
        PasswordField passField = new PasswordField();

        Button regBtn = new Button("Daftar");
        regBtn.getStyleClass().add("auth-btn-dark");
        regBtn.setMaxWidth(Double.MAX_VALUE);

        HBox footerRow = new HBox(4);
        footerRow.setAlignment(Pos.CENTER);
        Label askLabel = new Label("Sudah punya akun?");
        askLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        Button loginLink = new Button("Masuk");
        loginLink.getStyleClass().add("auth-link-btn");
        loginLink.setOnAction(e -> onBackToLogin.run());
        footerRow.getChildren().addAll(askLabel, loginLink);

        Label msg = new Label();
        msg.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        regBtn.setOnAction(e -> {
            String nama = namaField.getText();
            String username = userField.getText();
            String password = passField.getText();
            String alamat = alamatField.getText();
            String telepon = telpField.getText();
            msg.setText("");
            UiTasks.submit(() -> {
                penggunaDAO.register(
                    nama,
                    username,
                    password,
                    alamat,
                    telepon
                );
                return null;
            }, ignored -> {
                onRegisterSuccess.run();
            }, error -> {
                msg.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                msg.setText(UiTasks.errorMessage(error));
            }, regBtn);
        });
        card.getChildren().addAll(
            emblemRow, cardTitle,
            namaLbl, namaField,
            alamatLbl, alamatField,
            telpLbl, telpField,
            userLbl, userField,
            passLbl, passField,
            regBtn, footerRow, msg
        );

        root.getChildren().add(card);
        return root;
    }
}
