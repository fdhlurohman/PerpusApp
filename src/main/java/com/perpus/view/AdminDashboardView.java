package com.perpus.view;

import com.perpus.dao.BukuDAO;
import com.perpus.dao.PeminjamanDAO;
import com.perpus.model.Buku;
import com.perpus.model.Pengguna;
import com.perpus.session.UserSession;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.List;

public class AdminDashboardView {
    private final BukuDAO bukuDAO = new BukuDAO();
    private final PeminjamanDAO peminjamanDAO = new PeminjamanDAO();
    private final Runnable onLogout;

    private final BorderPane mainLayout = new BorderPane();
    private final Label msgLabel = new Label();
    private PauseTransition feedbackTimer;

    private final TableView<Buku> bookTable = new TableView<>();
    private final TableView<String[]> approvalTable = new TableView<>();
    private final TableView<String[]> returnTable = new TableView<>();
    private final TableView<String[]> reportTable = new TableView<>();

    private Button navBooksBtn;
    private Button navApproveBtn;
    private Button navReportsBtn;
    private Button navSettingsBtn;

    public AdminDashboardView(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    public Parent getView() {
        VBox sidebar = buildSidebar();
        mainLayout.setLeft(sidebar);
        showManageBooksSection();
        return mainLayout;
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(16);
        sidebar.getStyleClass().add("sidebar");

        HBox brandRow = new HBox(8);
        brandRow.setAlignment(Pos.CENTER_LEFT);

        ImageView appLogo = UiIcons.image("perpus-icon.png", 32);

        Label brand = new Label("Perpustakaan");
        brand.getStyleClass().add("sidebar-brand-title");
        brandRow.getChildren().addAll(appLogo, brand);

        Pengguna user = UserSession.getInstance().getCurrentUser();
        String nama = user != null && user.getNama() != null ? user.getNama() : "Petugas";
        String initial = nama.isEmpty() ? "P" : nama.substring(0, 1).toUpperCase();

        HBox userBox = new HBox(10);
        userBox.getStyleClass().add("sidebar-user-box");
        userBox.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("sidebar-avatar");
        Label avText = new Label(initial);
        avText.getStyleClass().add("sidebar-avatar-text");
        avatar.getChildren().add(avText);

        VBox userMeta = new VBox(1);
        Label userName = new Label(user != null ? user.getUsername() : "Petugas");
        userName.getStyleClass().add("sidebar-username");
        Label userRole = new Label(user != null ? user.getRole().name() : "");
        userRole.getStyleClass().add("sidebar-role");
        userMeta.getChildren().addAll(userName, userRole);

        userBox.getChildren().addAll(avatar, userMeta);

        navBooksBtn = new Button("Data Buku");
        navBooksBtn.setGraphic(UiIcons.image("book-white.png", 24));
        navBooksBtn.getStyleClass().add("sidebar-nav-btn-active");
        navBooksBtn.setMaxWidth(Double.MAX_VALUE);
        navBooksBtn.setGraphicTextGap(12);

        navApproveBtn = new Button("Persetujuan");
        navApproveBtn.setGraphic(UiIcons.image("persetujuan-white.png", 20));
        navApproveBtn.getStyleClass().add("sidebar-nav-btn");
        navApproveBtn.setMaxWidth(Double.MAX_VALUE);
        navApproveBtn.setGraphicTextGap(12);

        navReportsBtn = new Button("Laporan");
        navReportsBtn.setGraphic(UiIcons.image("laporan-white.png", 20));
        navReportsBtn.getStyleClass().add("sidebar-nav-btn");
        navReportsBtn.setMaxWidth(Double.MAX_VALUE);
        navReportsBtn.setGraphicTextGap(12);
        navSettingsBtn = new Button("Pengaturan");
        navSettingsBtn.setGraphic(UiIcons.image("settings-white.png", 20));
        navSettingsBtn.getStyleClass().add("sidebar-nav-btn");
        navSettingsBtn.setMaxWidth(Double.MAX_VALUE);
        navSettingsBtn.setGraphicTextGap(12);

        navBooksBtn.setOnAction(e -> {
            setActiveButton(navBooksBtn);
            showManageBooksSection();
        });

        navApproveBtn.setOnAction(e -> {
            setActiveButton(navApproveBtn);
            showApprovalsSection();
        });

        navReportsBtn.setOnAction(e -> { setActiveButton(navReportsBtn); showReportsSection(); });
        navSettingsBtn.setOnAction(e -> { setActiveButton(navSettingsBtn); showSettingsSection(); });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logoutBtn = new Button("Keluar");
        logoutBtn.setGraphic(UiIcons.image("keluar-white.png", 20));
        logoutBtn.getStyleClass().add("sidebar-logout-btn");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setGraphicTextGap(12);
        logoutBtn.setOnAction(e -> {
            UserSession.getInstance().clear();
            onLogout.run();
        });

        sidebar.getChildren().addAll(brandRow, userBox, navBooksBtn, navApproveBtn, navReportsBtn, navSettingsBtn, spacer, logoutBtn);
        return sidebar;
    }

    private void setActiveButton(Button active) {
        for (Button button : List.of(navBooksBtn, navApproveBtn, navReportsBtn, navSettingsBtn)) {
            button.getStyleClass().setAll("sidebar-nav-btn");
        }
        active.getStyleClass().setAll("sidebar-nav-btn-active");
    }

    private void showManageBooksSection() {
        msgLabel.setText("");
        VBox content = new VBox(14);
        content.getStyleClass().add("content-area");

        Label pageTitle = new Label("Manajemen Data Buku");
        pageTitle.getStyleClass().add("page-title");

        HBox split = new HBox(16);
        VBox.setVgrow(split, Priority.ALWAYS);

        // Left Pane (65% Table)
        VBox leftPane = new VBox(10);
        leftPane.getStyleClass().add("white-panel");
        HBox.setHgrow(leftPane, Priority.ALWAYS);

        HBox searchRow = new HBox(8);
        TextField searchField = new TextField();
        searchField.setPromptText("Search");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("Cari");
        searchBtn.getStyleClass().add("btn-primary-blue");

        Button resetBtn = new Button("Semua");
        resetBtn.getStyleClass().add("btn-outline");

        searchBtn.setOnAction(e -> {
            String query = searchField.getText();
            UiTasks.submit(() -> bukuDAO.search(query),
                    books -> bookTable.setItems(FXCollections.observableArrayList(books)),
                    error -> showFeedback(UiTasks.errorMessage(error), false), searchBtn, resetBtn);
        });

        resetBtn.setOnAction(e -> {
            searchField.clear();
            refreshBookTable();
        });

        searchRow.getChildren().addAll(searchField, searchBtn, resetBtn);

        TextField idField = new TextField();
        idField.setPromptText("BK-001");

        TextField judulField = new TextField();
        TextField isbnField = new TextField();
        TextField pengarangField = new TextField();
        TextField penerbitField = new TextField();
        TextField tahunField = new TextField();
        TextField stokField = new TextField();

        setupAdminBookTable(idField, judulField, isbnField, pengarangField, penerbitField, tahunField, stokField);
        refreshBookTable();
        VBox.setVgrow(bookTable, Priority.ALWAYS);

        leftPane.getChildren().addAll(searchRow, bookTable);

        // Right Pane (35% Form Card matching reference labels)
        VBox rightCard = new VBox(7);
        rightCard.getStyleClass().add("white-panel");
        rightCard.setPrefWidth(320);

        Label lblId = new Label("Kode/ID Buku (tipe String/manual)");
        lblId.getStyleClass().add("form-label");

        Label lblJudul = new Label("Judul");
        lblJudul.getStyleClass().add("form-label");

        Label lblIsbn = new Label("ISBN:");
        lblIsbn.getStyleClass().add("form-label");

        Label lblPengarang = new Label("Pengarang:");
        lblPengarang.getStyleClass().add("form-label");

        Label lblPenerbit = new Label("Penerbit:");
        lblPenerbit.getStyleClass().add("form-label");

        Label lblTahun = new Label("Tahun:");
        lblTahun.getStyleClass().add("form-label");

        Label lblStok = new Label("Stok:");
        lblStok.getStyleClass().add("form-label");

        bookTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                populateForm(newV, idField, judulField, isbnField, pengarangField, penerbitField, tahunField, stokField);
            }
        });

        Button addBtn = new Button("Tambah Buku");
        addBtn.getStyleClass().add("btn-dark");

        Button updateBtn = new Button("Edit");
        updateBtn.getStyleClass().add("btn-primary-blue");

        Button delBtn = new Button("Hapus");
        delBtn.getStyleClass().add("btn-danger");

        Button resetBtnForm = new Button("Reset");
        resetBtnForm.getStyleClass().add("btn-outline");

        addBtn.setOnAction(e -> {
            try {
                int tahun = tahunField.getText().isBlank() ? 2024 : Integer.parseInt(tahunField.getText().trim());
                int stok = Integer.parseInt(stokField.getText().trim());
                Buku b = new Buku(idField.getText(), judulField.getText(), isbnField.getText(), pengarangField.getText(), penerbitField.getText(), tahun, stok);
                UiTasks.submit(() -> {
                    bukuDAO.insert(b);
                    return null;
                }, ignored -> {
                    showFeedback("Buku '" + b.getJudulBuku() + "' berhasil ditambahkan!", true);
                    clearForm(idField, judulField, isbnField, pengarangField, penerbitField, tahunField, stokField);
                    refreshBookTable();
                }, error -> showFeedback(UiTasks.errorMessage(error), false), addBtn, updateBtn, delBtn);
            } catch (Exception ex) {
                showFeedback(ex.getMessage(), false);
            }
        });

        updateBtn.setOnAction(e -> {
            try {
                int tahun = tahunField.getText().isBlank() ? 2024 : Integer.parseInt(tahunField.getText().trim());
                int stok = Integer.parseInt(stokField.getText().trim());
                Buku b = new Buku(idField.getText(), judulField.getText(), isbnField.getText(), pengarangField.getText(), penerbitField.getText(), tahun, stok);
                UiTasks.submit(() -> {
                    bukuDAO.update(b);
                    return null;
                }, ignored -> {
                    showFeedback("Buku '" + b.getIdBuku() + "' berhasil diedit!", true);
                    clearForm(idField, judulField, isbnField, pengarangField, penerbitField, tahunField, stokField);
                    refreshBookTable();
                }, error -> showFeedback(UiTasks.errorMessage(error), false), addBtn, updateBtn, delBtn);
            } catch (Exception ex) {
                showFeedback(ex.getMessage(), false);
            }
        });

        delBtn.setOnAction(e -> {
            Buku selected = bookTable.getSelectionModel().getSelectedItem();
            String id = selected != null ? selected.getIdBuku() : idField.getText().trim();
            if (id.isBlank()) {
                showFeedback("Pilih buku untuk dihapus", false);
                return;
            }
            UiTasks.submit(() -> {
                bukuDAO.delete(id);
                return null;
            }, ignored -> {
                showFeedback("Buku '" + id + "' berhasil dihapus", true);
                clearForm(idField, judulField, isbnField, pengarangField, penerbitField, tahunField, stokField);
                refreshBookTable();
            }, error -> showFeedback(UiTasks.errorMessage(error), false), addBtn, updateBtn, delBtn);
        });

        resetBtnForm.setOnAction(e -> clearForm(idField, judulField, isbnField, pengarangField, penerbitField, tahunField, stokField));

        addBtn.setMaxWidth(Double.MAX_VALUE);
        updateBtn.setMaxWidth(Double.MAX_VALUE);
        delBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtnForm.setMaxWidth(Double.MAX_VALUE);

        HBox btnRow1 = new HBox(8, addBtn, updateBtn);
        HBox.setHgrow(addBtn, Priority.ALWAYS);
        HBox.setHgrow(updateBtn, Priority.ALWAYS);

        HBox btnRow2 = new HBox(8, delBtn, resetBtnForm);
        HBox.setHgrow(delBtn, Priority.ALWAYS);
        HBox.setHgrow(resetBtnForm, Priority.ALWAYS);

        VBox btnContainer = new VBox(8, btnRow1, btnRow2);
        btnContainer.setPadding(new Insets(6, 0, 0, 0));

        rightCard.getChildren().addAll(
            lblId, idField,
            lblJudul, judulField,
            lblIsbn, isbnField,
            lblPengarang, pengarangField,
            lblPenerbit, penerbitField,
            lblTahun, tahunField,
            lblStok, stokField,
            btnContainer
        );

        ScrollPane rightScroll = new ScrollPane(rightCard);
        rightScroll.setFitToWidth(true);
        rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rightScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        split.getChildren().addAll(leftPane, rightScroll);
        content.getChildren().addAll(pageTitle, msgLabel, split);
        mainLayout.setCenter(content);
    }

    private void populateForm(Buku b, TextField id, TextField j, TextField isbn, TextField p, TextField pen, TextField th, TextField s) {
        id.setText(b.getIdBuku());
        id.setDisable(true);
        j.setText(b.getJudulBuku());
        isbn.setText(b.getIsbn());
        p.setText(b.getPengarang());
        pen.setText(b.getPenerbit());
        th.setText(String.valueOf(b.getTahunTerbit()));
        s.setText(String.valueOf(b.getStokBuku()));
    }

    private void clearForm(TextField id, TextField j, TextField isbn, TextField p, TextField pen, TextField th, TextField s) {
        id.clear();
        id.setDisable(false);
        j.clear();
        isbn.clear();
        p.clear();
        pen.clear();
        th.clear();
        s.clear();
        bookTable.getSelectionModel().clearSelection();
    }

    private void setupAdminBookTable(TextField id, TextField j, TextField isbn, TextField p, TextField pen, TextField th, TextField s) {
        bookTable.getColumns().clear();
        bookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Buku, String> idCol = new TableColumn<>("Kode Buku");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIdBuku()));
        idCol.setPrefWidth(95);

        TableColumn<Buku, String> titleCol = new TableColumn<>("Judul");
        titleCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getJudulBuku()));
        titleCol.setPrefWidth(200);

        TableColumn<Buku, String> isbnCol = new TableColumn<>("ISBN");
        isbnCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIsbn()));
        isbnCol.setPrefWidth(110);

        TableColumn<Buku, String> authorCol = new TableColumn<>("Pengarang");
        authorCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPengarang()));
        authorCol.setPrefWidth(130);

        TableColumn<Buku, String> pubCol = new TableColumn<>("Penerbit");
        pubCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPenerbit()));
        pubCol.setPrefWidth(120);

        TableColumn<Buku, String> yearCol = new TableColumn<>("Tahun Terbit");
        yearCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getTahunTerbit())));
        yearCol.setPrefWidth(85);

        TableColumn<Buku, String> stockCol = new TableColumn<>("Stok");
        stockCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getStokBuku())));
        stockCol.setPrefWidth(65);

        bookTable.getColumns().addAll(idCol, titleCol, isbnCol, authorCol, pubCol, yearCol, stockCol);
    }

    private void showApprovalsSection() {
        msgLabel.setText("");
        VBox content = new VBox(14);
        content.getStyleClass().add("content-area");

        HBox topHeader = new HBox(12);
        topHeader.setAlignment(Pos.CENTER_LEFT);

        Label pageTitle = new Label("Persetujuan & Pengembalian");
        pageTitle.getStyleClass().add("page-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshApproveBtn = new Button("Refresh");
        refreshApproveBtn.getStyleClass().add("btn-outline");
        refreshApproveBtn.setOnAction(e -> {
            refreshApprovalTable();
            refreshReturnTable();
        });

        topHeader.getChildren().addAll(pageTitle, spacer, refreshApproveBtn);

        // Top Card: REQUESTED
        VBox topCard = new VBox(10);
        topCard.getStyleClass().add("white-panel");
        Label reqTitle = new Label("REQUESTED");
        reqTitle.getStyleClass().add("section-title");

        setupApprovalTable();
        refreshApprovalTable();
        VBox.setVgrow(approvalTable, Priority.ALWAYS);
        topCard.getChildren().addAll(reqTitle, approvalTable);

        // Bottom Card: DIPINJAM
        VBox bottomCard = new VBox(10);
        bottomCard.getStyleClass().add("white-panel");
        Label dipinjamTitle = new Label("DIPINJAM");
        dipinjamTitle.getStyleClass().add("section-title");

        setupReturnTable();
        refreshReturnTable();
        VBox.setVgrow(returnTable, Priority.ALWAYS);

        Label returnNote = new Label("Denda dihitung otomatis berdasarkan tanggal jatuh tempo dan aturan perpustakaan.");

        bottomCard.getChildren().addAll(dipinjamTitle, returnTable, returnNote);

        content.getChildren().addAll(topHeader, msgLabel, topCard, bottomCard);
        mainLayout.setCenter(content);
    }

    private void setupApprovalTable() {
        approvalTable.getColumns().clear();
        approvalTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<String[], String> idCol = new TableColumn<>("ID Transaksi");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        idCol.setPrefWidth(90);

        TableColumn<String[], String> memberCol = new TableColumn<>("Peminjam");
        memberCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        memberCol.setPrefWidth(120);

        TableColumn<String[], String> titleCol = new TableColumn<>("Judul");
        titleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[4]));
        titleCol.setPrefWidth(220);

        TableColumn<String[], String> dateCol = new TableColumn<>("Tanggal");
        dateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[6]));
        dateCol.setPrefWidth(110);

        TableColumn<String[], String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[5]));
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> createStatusCell());

        TableColumn<String[], Void> actionCol = new TableColumn<>("Aksi");
        actionCol.setPrefWidth(130);
        actionCol.setCellFactory(col -> new TableCell<String[], Void>() {
            private final Button btn = new Button("Setujui Pinjam");
            {
                btn.getStyleClass().add("btn-table-action-blue");
                btn.setOnAction(e -> {
                    String[] row = getTableRow().getItem();
                    if (row == null) return;
                    int idBorrow = Integer.parseInt(row[0]);
                    String idBuku = row[2];
                    btn.setDisable(true);
                    UiTasks.submit(() -> {
                        peminjamanDAO.approve(idBorrow, idBuku);
                        return null;
                    }, ignored -> {
                        btn.setDisable(false);
                        showFeedback("Peminjaman " + idBorrow + " disetujui!", true);
                        refreshApprovalTable();
                        refreshReturnTable();
                    }, error -> {
                        btn.setDisable(false);
                        showFeedback(UiTasks.errorMessage(error), false);
                    });
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        approvalTable.getColumns().addAll(idCol, memberCol, titleCol, dateCol, statusCol, actionCol);
    }

    private void setupReturnTable() {
        returnTable.getColumns().clear();
        returnTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<String[], String> idCol = new TableColumn<>("ID Transaksi");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        idCol.setPrefWidth(90);

        TableColumn<String[], String> memberCol = new TableColumn<>("Peminjam");
        memberCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        memberCol.setPrefWidth(120);

        TableColumn<String[], String> titleCol = new TableColumn<>("Judul");
        titleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[4]));
        titleCol.setPrefWidth(220);

        TableColumn<String[], String> dateCol = new TableColumn<>("Tanggal");
        dateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[7]));
        dateCol.setPrefWidth(110);

        TableColumn<String[], String> dueDateCol = new TableColumn<>("Jatuh Tempo");
        dueDateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[8]));
        dueDateCol.setPrefWidth(110);

        TableColumn<String[], String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[5]));
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> createStatusCell());

        TableColumn<String[], Void> actionCol = new TableColumn<>("Aksi");
        actionCol.setPrefWidth(130);
        actionCol.setCellFactory(col -> new TableCell<String[], Void>() {
            private final Button btn = new Button("Proses Kembali");
            {
                btn.getStyleClass().add("btn-table-action-dark");
                btn.setOnAction(e -> {
                    String[] row = getTableRow().getItem();
                    if (row == null) return;
                    int idBorrow = Integer.parseInt(row[0]);
                    String idBuku = row[2];
                    btn.setDisable(true);
                    UiTasks.submit(() -> peminjamanDAO.returnBook(idBorrow, idBuku), denda -> {
                        btn.setDisable(false);
                        showFeedback("Buku ID " + idBorrow + " dikembalikan! Denda: Rp " + String.format("%,.0f", denda), true);
                        refreshReturnTable();
                        refreshApprovalTable();
                    }, error -> {
                        btn.setDisable(false);
                        showFeedback(UiTasks.errorMessage(error), false);
                    });
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        returnTable.getColumns().addAll(idCol, memberCol, titleCol, dateCol, dueDateCol, statusCol, actionCol);
    }

    private TableCell<String[], String> createStatusCell() {
        return new TableCell<String[], String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(item);
                    pill.getStyleClass().add("pill-badge");
                    if ("REQUESTED".equalsIgnoreCase(item)) pill.getStyleClass().add("badge-requested");
                    else if ("DIPINJAM".equalsIgnoreCase(item)) pill.getStyleClass().add("badge-dipinjam");
                    else pill.getStyleClass().add("badge-dikembalikan");
                    setGraphic(pill);
                    setText(null);
                }
            }
        };
    }

    private void showSettingsSection() {
        mainLayout.setCenter(new SettingsView(() -> showManageBooksSection()).getView());
    }

    private void showReportsSection() {
        msgLabel.setText("");
        VBox content = new VBox(14);
        content.getStyleClass().add("content-area");

        Label pageTitle = new Label("Laporan Seluruh Transaksi");
        pageTitle.getStyleClass().add("page-title");

        VBox card = new VBox(10);
        card.getStyleClass().add("white-panel");
        VBox.setVgrow(card, Priority.ALWAYS);

        reportTable.getColumns().clear();
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<String[], String> idCol = new TableColumn<>("ID Transaksi");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        idCol.setPrefWidth(90);

        TableColumn<String[], String> memberCol = new TableColumn<>("Anggota");
        memberCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        memberCol.setPrefWidth(120);

        TableColumn<String[], String> kodeCol = new TableColumn<>("Kode");
        kodeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        kodeCol.setPrefWidth(80);

        TableColumn<String[], String> isbnCol = new TableColumn<>("ISBN");
        isbnCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));
        isbnCol.setPrefWidth(110);

        TableColumn<String[], String> titleCol = new TableColumn<>("Judul");
        titleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[4]));
        titleCol.setPrefWidth(220);

        TableColumn<String[], String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[5]));
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> createStatusCell());

        TableColumn<String[], String> bookingCol = new TableColumn<>("Booking");
        bookingCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[6]));
        bookingCol.setPrefWidth(100);

        TableColumn<String[], String> dueCol = new TableColumn<>("Jatuh Tempo");
        dueCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[8]));
        dueCol.setPrefWidth(100);

        TableColumn<String[], String> dendaCol = new TableColumn<>("Denda");
        dendaCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[9]));
        dendaCol.setPrefWidth(100);

        reportTable.getColumns().addAll(idCol, memberCol, kodeCol, isbnCol, titleCol, statusCol, bookingCol, dueCol, dendaCol);

        refreshReportTable();
        VBox.setVgrow(reportTable, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-outline");
        refreshBtn.setOnAction(e -> refreshReportTable(refreshBtn));

        card.getChildren().addAll(reportTable, refreshBtn);
        content.getChildren().addAll(pageTitle, msgLabel, card);
        mainLayout.setCenter(content);
    }

    private void refreshBookTable() {
        UiTasks.submit(bukuDAO::findAll,
                books -> bookTable.setItems(FXCollections.observableArrayList(books)),
                error -> showFeedback(UiTasks.errorMessage(error), false), bookTable);
    }

    private void refreshApprovalTable() {
        UiTasks.submit(() -> peminjamanDAO.findRecords("REQUESTED", null),
                records -> approvalTable.setItems(FXCollections.observableArrayList(records)),
                error -> showFeedback(UiTasks.errorMessage(error), false), approvalTable);
    }

    private void refreshReturnTable() {
        UiTasks.submit(() -> peminjamanDAO.findRecords("DIPINJAM", null),
                records -> returnTable.setItems(FXCollections.observableArrayList(records)),
                error -> showFeedback(UiTasks.errorMessage(error), false), returnTable);
    }

    private void refreshReportTable() {
        refreshReportTable(null);
    }

    private void refreshReportTable(Button busyButton) {
        if (busyButton == null) {
            UiTasks.submit(() -> peminjamanDAO.findRecords(null, null),
                    records -> reportTable.setItems(FXCollections.observableArrayList(records)),
                    error -> showFeedback(UiTasks.errorMessage(error), false), reportTable);
        } else {
            UiTasks.submit(() -> peminjamanDAO.findRecords(null, null),
                    records -> reportTable.setItems(FXCollections.observableArrayList(records)),
                    error -> showFeedback(UiTasks.errorMessage(error), false), reportTable, busyButton);
        }
    }

    private void showFeedback(String text, boolean success) {
        if (feedbackTimer != null) feedbackTimer.stop();
        msgLabel.setStyle(success ? "-fx-text-fill: #16a34a; -fx-font-weight: bold;" : "-fx-text-fill: #dc2626; -fx-font-weight: bold;" );
        msgLabel.setText(text);
        feedbackTimer = new PauseTransition(Duration.seconds(3.5));
        feedbackTimer.setOnFinished(e -> msgLabel.setText(""));
        feedbackTimer.play();
    }
}
