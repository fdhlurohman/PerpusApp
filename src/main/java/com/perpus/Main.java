package com.perpus;

import com.perpus.session.UserSession;
import com.perpus.view.AdminDashboardView;
import com.perpus.view.LoginView;
import com.perpus.view.MemberDashboardView;
import com.perpus.view.RegisterView;
import com.perpus.view.SplashScreenView;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;

public class Main extends Application {
    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        try (InputStream iconStream = getClass().getResourceAsStream("/icons/perpus-icon.png")) {
            if (iconStream == null) {
                throw new IllegalStateException("Ikon aplikasi tidak ditemukan: /icons/perpus-icon.png");
            }
            Image appIcon = new Image(iconStream);
            if (appIcon.isError()) {
                throw new IllegalStateException("Ikon aplikasi gagal dimuat", appIcon.getException());
            }
            primaryStage.getIcons().add(appIcon);
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal membaca ikon aplikasi", ex);
        }
        new SplashScreenView(stage, this::showLogin).show();
    }

    public void showLogin() {
        LoginView view = new LoginView(this::onLoginSuccess, this::showRegister);
        switchScene(view.getView(), "Login - Perpustakaan App", 920, 620);
    }

    public void showRegister() {
        RegisterView view = new RegisterView(this::showLogin, this::showLogin);
        switchScene(view.getView(), "Daftar Anggota - Perpustakaan App", 920, 640);
    }

    public void onLoginSuccess() {
        if (UserSession.getInstance().isAdmin()) {
            showAdminDashboard();
        } else {
            showMemberDashboard();
        }
    }

    public void showMemberDashboard() {
        MemberDashboardView view = new MemberDashboardView(this::showLogin);
        switchScene(view.getView(), "Dashboard Anggota - Perpustakaan", 1140, 740);
    }

    public void showAdminDashboard() {
        AdminDashboardView view = new AdminDashboardView(this::showLogin);
        switchScene(view.getView(), "Dashboard Admin - Perpustakaan", 1180, 740);
    }

    private void switchScene(Parent root, String title, double width, double height) {
        boolean wasMaximized = stage.isMaximized();
        Scene currentScene = stage.getScene();
        if (currentScene == null) {
            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            stage.setScene(scene);
        } else {
            currentScene.setRoot(root);
        }
        stage.setTitle(title);
        if (wasMaximized) {
            stage.setMaximized(true);
        }
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
