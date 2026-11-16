package com.perpus.session;

import com.perpus.config.ApiClient;
import com.perpus.model.Pengguna;

public final class UserSession {
    private static UserSession instance;
    private Pengguna currentUser;

    private UserSession() {}

    public static synchronized UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public Pengguna getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(Pengguna user) {
        this.currentUser = user;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isAdmin() {
        return currentUser != null && (currentUser.getRole() == com.perpus.model.Role.ADMIN || currentUser.getRole() == com.perpus.model.Role.SUPER_ADMIN);
    }

    public void clear() {
        this.currentUser = null;
        ApiClient.clearAuth();
    }
}
