package com.perpus.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.perpus.config.ApiClient;
import com.perpus.dao.LibraryRulesDAO;
import com.perpus.model.Anggota;
import com.perpus.model.LibraryRules;
import com.perpus.model.Pengguna;
import com.perpus.session.UserSession;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsView {
    private final Runnable back;
    private final LibraryRulesDAO rulesDAO = new LibraryRulesDAO();

    public SettingsView(Runnable back) {
        this.back = back;
    }

    public Parent getView() {
        VBox root = new VBox(16);
        root.getStyleClass().add("content-area");

        Label title = new Label("Pengaturan");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Kelola profil, pengguna, dan aturan perpustakaan");
        subtitle.getStyleClass().add("page-subtitle");

        TabPane tabs = new TabPane();
        tabs.getTabs().add(tab("Profil Saya", profile()));
        if (UserSession.getInstance().isAdmin()) tabs.getTabs().add(tab("Kelola Anggota", usersPanel("MEMBER")));
        Pengguna currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getRole().name().equals("SUPER_ADMIN")) {
            tabs.getTabs().add(tab("Kelola Petugas", usersPanel("ADMIN")));
        }
        if (UserSession.getInstance().isAdmin()) {
            tabs.getTabs().add(tab("Aturan Perpustakaan", rules()));
        }

        Button backButton = new Button("Kembali ke Dashboard");
        backButton.getStyleClass().add("btn-outline");
        backButton.setOnAction(event -> back.run());
        root.getChildren().addAll(title, subtitle, tabs, backButton);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return root;
    }

    private Tab tab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private VBox profile() {
        Pengguna user = UserSession.getInstance().getCurrentUser();
        TextField name = new TextField(user == null ? "" : user.getNama());
        TextField username = new TextField(user == null ? "" : user.getUsername());
        TextField address = new TextField();
        TextField phone = new TextField();
        if (user instanceof Anggota member) {
            address.setText(member.getAlamat() == null ? "" : member.getAlamat());
            phone.setText(member.getNoTelepon() == null ? "" : member.getNoTelepon());
        }

        Button save = new Button("Simpan Profil");
        Label message = new Label();
        save.setOnAction(event -> {
            if (user == null) {
                showError(message, "Tidak ada pengguna yang sedang masuk");
                return;
            }
            String normalizedName = name.getText().trim();
            String normalizedUsername = username.getText().trim();
            String normalizedAddress = address.getText().trim();
            String normalizedPhone = phone.getText().trim();
            if (normalizedName.isBlank() || normalizedUsername.isBlank()) {
                showError(message, "Nama dan username wajib diisi");
                return;
            }
            if (normalizedName.length() > 100 || normalizedUsername.length() > 50
                    || normalizedAddress.length() > 255 || normalizedPhone.length() > 30) {
                showError(message, "Panjang nama, username, alamat, atau nomor telepon melebihi batas");
                return;
            }
            UiTasks.submit(() -> {
                ApiClient.put("profile", Map.of(
                        "nama", normalizedName,
                        "username", normalizedUsername,
                        "alamat", normalizedAddress,
                        "noTelepon", normalizedPhone));
                return null;
            }, ignored -> {
                user.setNama(normalizedName);
                user.setUsername(normalizedUsername);
                if (user instanceof Anggota member) {
                    member.setAlamat(normalizedAddress);
                    member.setNoTelepon(normalizedPhone);
                }
                showSuccess(message, "Profil berhasil disimpan");
            }, error -> showApiError(message, "Gagal menyimpan profil", error), save);
        });

        return form(
                new Label("Nama"), name,
                new Label("Username"), username,
                new Label("Alamat"), address,
                new Label("No Telepon"), phone,
                save, message);
    }

    private VBox usersPanel(String role) {
        TableView<UserDetails> table = usersTable();
        Label message = new Label();
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("btn-outline");
        Button edit = new Button("Edit Biodata / Password");
        edit.getStyleClass().add("btn-primary-blue");
        Button add = new Button(role.equals("MEMBER") ? "Tambah Anggota" : "Tambah Petugas");
        add.getStyleClass().add("btn-primary-blue");
        Button delete = new Button(role.equals("MEMBER") ? "Hapus Anggota" : "Hapus Petugas");
        delete.getStyleClass().add("btn-danger");
        boolean canEdit = UserSession.getInstance().getCurrentUser() != null
                && UserSession.getInstance().getCurrentUser().getRole().name().equals("SUPER_ADMIN");

        refresh.setOnAction(event -> loadUsers(role, table, message));
        add.setOnAction(event -> addUser(role, table, message));
        edit.setOnAction(event -> {
            UserDetails selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError(message, "Pilih pengguna yang akan diedit");
                return;
            }
            editUser(selected, role, table, message);
        });
        delete.setOnAction(event -> {
            UserDetails selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError(message, "Pilih pengguna yang akan dihapus");
                return;
            }
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Konfirmasi Penghapusan");
            confirmation.setHeaderText("Hapus " + selected.nama() + "?");
            confirmation.setContentText("Tindakan ini tidak dapat dibatalkan.");
            if (table.getScene() != null) {
                confirmation.initOwner(table.getScene().getWindow());
            }
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            UiTasks.submit(() -> {
                ApiClient.delete("users/" + selected.id());
                return null;
            }, ignored -> {
                loadUsers(role, table, message);
                showSuccess(message, "Pengguna berhasil dihapus");
            }, error -> showApiError(message, "Gagal menghapus pengguna", error), delete);
        });

        loadUsers(role, table, message);
        VBox actions = new VBox(8);
        actions.getChildren().add(refresh);
        if (canEdit) actions.getChildren().addAll(add, edit);
        actions.getChildren().add(delete);
        VBox panel = form(table, actions, message);
        VBox.setVgrow(table, Priority.ALWAYS);
        return panel;
    }

    private TableView<UserDetails> usersTable() {
        TableView<UserDetails> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Belum ada data untuk ditampilkan"));
        table.getColumns().add(userColumn("ID", user -> String.valueOf(user.id())));
        table.getColumns().add(userColumn("Nama", UserDetails::nama));
        table.getColumns().add(userColumn("Username", UserDetails::username));
        table.getColumns().add(userColumn("Peran", UserDetails::role));
        table.getColumns().add(userColumn("Alamat", UserDetails::alamat));
        table.getColumns().add(userColumn("No. Telepon", UserDetails::noTelepon));
        return table;
    }

    private TableColumn<UserDetails, String> userColumn(String title,
                                                         java.util.function.Function<UserDetails, String> value) {
        TableColumn<UserDetails, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private void loadUsers(String role, TableView<UserDetails> table, Label message) {
        UiTasks.submit(() -> rows(role), users -> {
            table.setItems(users);
            message.setText("");
        }, error -> showError(message, "Gagal memuat daftar pengguna: " + UiTasks.errorMessage(error)), table);
    }

    private ObservableList<UserDetails> rows(String role) {
        ObservableList<UserDetails> users = FXCollections.observableArrayList();
        JsonNode rows = ApiClient.get("users?role=" + ApiClient.encodeQuery(role));
        if (!rows.isArray()) throw new IllegalStateException("Format daftar pengguna dari server tidak valid");
        for (JsonNode user : rows) {
            users.add(new UserDetails(
                    user.path("id").asInt(),
                    user.path("nama").asText(),
                    user.path("username").asText(),
                    user.path("role").asText(),
                    user.path("alamat").asText(""),
                    user.path("no_telepon").asText("")));
        }
        return users;
    }

    private void editUser(UserDetails user, String role, TableView<UserDetails> table, Label message) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Data Pengguna");
        dialog.setHeaderText("Perbarui biodata " + user.nama());

        TextField name = new TextField(user.nama());
        TextField username = new TextField(user.username());
        TextField address = new TextField(user.alamat() == null ? "" : user.alamat());
        TextField phone = new TextField(user.noTelepon() == null ? "" : user.noTelepon());
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("Kosongkan jika tidak ingin mengganti");

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        fields.setPadding(new Insets(12));
        fields.addRow(0, new Label("Nama"), name);
        fields.addRow(1, new Label("Username"), username);
        fields.addRow(2, new Label("Alamat"), address);
        fields.addRow(3, new Label("No. Telepon"), phone);
        fields.addRow(4, new Label("Password baru"), newPassword);
        fields.add(new Label("Password lama tidak dapat dilihat. Isi hanya jika ingin menggantinya."),
                0, 5, 2, 1);
        GridPane.setHgrow(name, Priority.ALWAYS);
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setHgrow(address, Priority.ALWAYS);
        GridPane.setHgrow(phone, Priority.ALWAYS);
        GridPane.setHgrow(newPassword, Priority.ALWAYS);
        dialog.getDialogPane().setContent(fields);

        ButtonType save = new ButtonType("Simpan", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        if (table.getScene() != null) dialog.initOwner(table.getScene().getWindow());

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;

        String normalizedName = name.getText().trim();
        String normalizedUsername = username.getText().trim();
        String normalizedAddress = address.getText().trim();
        String normalizedPhone = phone.getText().trim();
        String password = newPassword.getText();
        if (normalizedName.isBlank() || normalizedUsername.isBlank()) {
            showError(message, "Nama dan username wajib diisi");
            return;
        }
        if (normalizedName.length() > 100 || normalizedUsername.length() > 50
                || normalizedAddress.length() > 255 || normalizedPhone.length() > 30) {
            showError(message, "Panjang biodata melebihi batas yang diizinkan");
            return;
        }
        if (!password.isBlank() && password.length() < 4) {
            showError(message, "Password baru minimal 4 karakter");
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nama", normalizedName);
        payload.put("username", normalizedUsername);
        payload.put("alamat", normalizedAddress);
        payload.put("noTelepon", normalizedPhone);
        if (!password.isBlank()) payload.put("password", password);
        UiTasks.submit(() -> {
            ApiClient.put("users/" + user.id(), payload);
            return null;
        }, ignored -> {
            loadUsers(role, table, message);
            showSuccess(message, "Biodata pengguna berhasil diperbarui");
        }, error -> showApiError(message, "Gagal memperbarui biodata", error), table);
    }

    private void addUser(String role, TableView<UserDetails> table, Label message) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(role.equals("MEMBER") ? "Tambah Anggota" : "Tambah Petugas");
        dialog.setHeaderText("Masukkan biodata dan password awal");

        TextField name = new TextField();
        TextField username = new TextField();
        TextField address = new TextField();
        TextField phone = new TextField();
        PasswordField password = new PasswordField();
        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        fields.setPadding(new Insets(12));
        fields.addRow(0, new Label("Nama"), name);
        fields.addRow(1, new Label("Username"), username);
        fields.addRow(2, new Label("Alamat"), address);
        fields.addRow(3, new Label("No. Telepon"), phone);
        fields.addRow(4, new Label("Password awal"), password);
        GridPane.setHgrow(name, Priority.ALWAYS);
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setHgrow(address, Priority.ALWAYS);
        GridPane.setHgrow(phone, Priority.ALWAYS);
        GridPane.setHgrow(password, Priority.ALWAYS);
        dialog.getDialogPane().setContent(fields);

        ButtonType save = new ButtonType("Simpan", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        if (table.getScene() != null) dialog.initOwner(table.getScene().getWindow());
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;

        String normalizedName = name.getText().trim();
        String normalizedUsername = username.getText().trim();
        String normalizedAddress = address.getText().trim();
        String normalizedPhone = phone.getText().trim();
        String rawPassword = password.getText();
        if (normalizedName.isBlank() || normalizedUsername.isBlank()) {
            showError(message, "Nama dan username wajib diisi");
            return;
        }
        if (normalizedName.length() > 100 || normalizedUsername.length() > 50
                || normalizedAddress.length() > 255 || normalizedPhone.length() > 30) {
            showError(message, "Panjang biodata melebihi batas yang diizinkan");
            return;
        }
        if (rawPassword.isBlank() || rawPassword.length() < 4) {
            showError(message, "Password awal minimal 4 karakter");
            return;
        }

        UiTasks.submit(() -> {
            ApiClient.post("users", Map.of(
                    "nama", normalizedName,
                    "username", normalizedUsername,
                    "password", rawPassword,
                    "role", role,
                    "alamat", normalizedAddress,
                    "noTelepon", normalizedPhone));
            return null;
        }, ignored -> {
            loadUsers(role, table, message);
            showSuccess(message, role.equals("MEMBER") ? "Anggota berhasil ditambahkan" : "Petugas berhasil ditambahkan");
        }, error -> showApiError(message, "Gagal menambahkan pengguna", error), table);
    }

    private record UserDetails(int id, String nama, String username, String role, String alamat, String noTelepon) {}

    private VBox rules() {
        TextField fine = new TextField();
        TextField duration = new TextField();
        Label message = new Label();
        loadRules(fine, duration, message);

        Button save = new Button("Simpan Aturan");
        save.setOnAction(event -> {
            try {
                LibraryRules rules = new LibraryRules(
                        Integer.parseInt(fine.getText().trim()),
                        Integer.parseInt(duration.getText().trim()));
                UiTasks.submit(() -> {
                    rulesDAO.save(rules);
                    return null;
                }, ignored -> showSuccess(message, "Aturan perpustakaan berhasil disimpan"),
                        error -> showError(message, "Gagal menyimpan aturan: " + UiTasks.errorMessage(error)), save);
            } catch (NumberFormatException ex) {
                showError(message, "Denda dan durasi harus berupa angka bulat");
            } catch (IllegalArgumentException ex) {
                showError(message, ex.getMessage());
            } catch (RuntimeException ex) {
                showError(message, "Gagal menyimpan aturan: " + ex.getMessage());
            }
        });

        return form(
                new Label("Denda per hari (Rp)"), fine,
                new Label("Durasi pinjam (hari)"), duration,
                save, message);
    }

    private void loadRules(TextField fine, TextField duration, Label message) {
        UiTasks.submit(rulesDAO::find, rules -> {
            fine.setText(String.valueOf(rules.finePerDay()));
            duration.setText(String.valueOf(rules.loanDurationDays()));
        }, error -> showError(message, "Gagal memuat aturan perpustakaan: " + UiTasks.errorMessage(error)),
                fine, duration);
    }

    private VBox form(Node... nodes) {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("white-panel");
        panel.setPadding(new Insets(16));
        panel.getChildren().addAll(nodes);
        return panel;
    }

    private void showError(Label message, String text) {
        message.setStyle("-fx-text-fill: #dc2626;");
        message.setText(text);
    }

    private void showSuccess(Label message, String text) {
        message.setStyle("-fx-text-fill: #16a34a;");
        message.setText(text);
    }

    private void showApiError(Label message, String operation, Throwable error) {
        if (error instanceof ApiClient.ApiException apiError && apiError.getStatusCode() == 409) {
            if (operation.contains("menghapus")) {
                showError(message, "Pengguna tidak dapat dihapus karena masih memiliki riwayat peminjaman");
            } else {
                showError(message, "Username sudah digunakan oleh akun lain");
            }
        } else {
            showError(message, operation + ": " + UiTasks.errorMessage(error));
        }
    }
}
