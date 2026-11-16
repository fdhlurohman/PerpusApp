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
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MemberDashboardView {
    private final BukuDAO bukuDAO = new BukuDAO();
    private final PeminjamanDAO peminjamanDAO = new PeminjamanDAO();
    private final Runnable onLogout;

    private final BorderPane mainLayout = new BorderPane();
    private final HBox bookCardsRow = new HBox(12);
    private final TableView<String[]> recentLoansTable = new TableView<>();
    private final Label msgLabel = new Label();
    private PauseTransition feedbackTimer;

    private final Label statTotal = new Label("0");
    private final Label statTersedia = new Label("0");
    private final Label statAktif = new Label("0");
    private final AtomicInteger bookRequest = new AtomicInteger();
    private final AtomicInteger loanRequest = new AtomicInteger();
    private final AtomicInteger statsRequest = new AtomicInteger();

    private Button navCatalogBtn;
    private Button navLoansBtn;

    public MemberDashboardView(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    public Parent getView() {
        VBox sidebar = buildSidebar();
        mainLayout.setLeft(sidebar);
        showCatalogSection();
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
        String nama = user != null && user.getNama() != null ? user.getNama() : "Anggota";
        String initial = nama.isEmpty() ? "U" : nama.substring(0, 1).toUpperCase();

        HBox userBox = new HBox(10);
        userBox.getStyleClass().add("sidebar-user-box");
        userBox.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("sidebar-avatar");
        Label avText = new Label(initial);
        avText.getStyleClass().add("sidebar-avatar-text");
        avatar.getChildren().add(avText);

        VBox userMeta = new VBox(1);
        Label userName = new Label(user != null ? user.getUsername() : "Anggota");
        userName.getStyleClass().add("sidebar-username");
        Label userRole = new Label(user != null ? user.getRole().name() : "");
        userRole.getStyleClass().add("sidebar-role");
        userMeta.getChildren().addAll(userName, userRole);

        userBox.getChildren().addAll(avatar, userMeta);

        navCatalogBtn = new Button("Katalog Buku");
        navCatalogBtn.setGraphic(UiIcons.image("book-white.png", 24));
        navCatalogBtn.getStyleClass().add("sidebar-nav-btn-active");
        navCatalogBtn.setMaxWidth(Double.MAX_VALUE);
        navCatalogBtn.setGraphicTextGap(12);

        navLoansBtn = new Button("Peminjaman Saya");
        navLoansBtn.setGraphic(UiIcons.image("peminjamansaya-white.png", 24));
        navLoansBtn.getStyleClass().add("sidebar-nav-btn");
        navLoansBtn.setMaxWidth(Double.MAX_VALUE);
        navLoansBtn.setGraphicTextGap(12);

        navCatalogBtn.setOnAction(e -> {
            setActiveButton(navCatalogBtn, navLoansBtn);
            showCatalogSection();
        });

        navLoansBtn.setOnAction(e -> {
            setActiveButton(navLoansBtn, navCatalogBtn);
            showLoansSection();
        });

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

        sidebar.getChildren().addAll(brandRow, userBox, navCatalogBtn, navLoansBtn, spacer, logoutBtn);
        return sidebar;
    }

    private void setActiveButton(Button active, Button inactive) {
        active.getStyleClass().setAll("sidebar-nav-btn-active");
        inactive.getStyleClass().setAll("sidebar-nav-btn");
    }

    private void showCatalogSection() {
        msgLabel.setText("");
        VBox content = new VBox(14);
        content.getStyleClass().add("content-area");

        HBox topHeader = new HBox(12);
        topHeader.setAlignment(Pos.CENTER_LEFT);

        Label pageTitle = new Label("Katalog & Booking Buku");
        pageTitle.getStyleClass().add("page-title");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        TextField searchField = new TextField();
        searchField.setPromptText("Cari kode, judul, ISBN, atau pengarang...");
        searchField.setPrefWidth(280);

        Button searchBtn = new Button("Cari");
        searchBtn.getStyleClass().add("btn-primary-blue");

        Button filterButton = new Button();
        filterButton.setGraphic(UiIcons.image("filter.png", 20));
        filterButton.setAccessibleText("Filter dan urutkan buku");
        filterButton.setTooltip(new Tooltip("Filter dan urutkan buku"));
        filterButton.getStyleClass().addAll("btn-outline", "filter-icon-button");

        ContextMenu filterMenu = new ContextMenu();

        MenuItem miAll = new MenuItem("Semua Koleksi");
        MenuItem miTersedia = new MenuItem("Hanya Tersedia (Stok > 0)");
        MenuItem miHabis = new MenuItem("Stok Habis (0)");
        MenuItem miSortTitle = new MenuItem("Urutkan: Judul (A-Z)");
        MenuItem miSortYear = new MenuItem("Urutkan: Tahun Terbaru");
        MenuItem miSortStock = new MenuItem("Urutkan: Stok Terbanyak");

        miAll.setOnAction(e -> {
            searchField.clear();
            loadBooksFiltered("", "ALL");
        });
        miTersedia.setOnAction(e -> loadBooksFiltered(searchField.getText(), "TERSEDIA"));
        miHabis.setOnAction(e -> loadBooksFiltered(searchField.getText(), "HABIS"));
        miSortTitle.setOnAction(e -> loadBooksFiltered(searchField.getText(), "SORT_TITLE"));
        miSortYear.setOnAction(e -> loadBooksFiltered(searchField.getText(), "SORT_YEAR"));
        miSortStock.setOnAction(e -> loadBooksFiltered(searchField.getText(), "SORT_STOCK"));

        filterMenu.getItems().addAll(miAll, miTersedia, miHabis, new SeparatorMenuItem(), miSortTitle, miSortYear, miSortStock);
        filterButton.setOnAction(e -> {
            if (filterMenu.isShowing()) {
                filterMenu.hide();
            } else {
                filterMenu.show(filterButton, Side.BOTTOM, 0, 0);
            }
        });

        searchBtn.setOnAction(e -> loadBooksFiltered(searchField.getText(), "ALL"));

        topHeader.getChildren().addAll(pageTitle, headerSpacer, searchField, searchBtn, filterButton);

        HBox statRow = new HBox(14);
        statRow.getChildren().addAll(
            createStatCard("Total Buku:", statTotal),
            createStatCard("Tersedia:", statTersedia),
            createStatCard("Transaksi Aktif:", statAktif)
        );

        bookCardsRow.setAlignment(Pos.CENTER_LEFT);
        ScrollPane cardScroll = new ScrollPane(bookCardsRow);
        cardScroll.setFitToHeight(true);
        cardScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        cardScroll.setPrefHeight(245);
        cardScroll.setMinHeight(245);

        VBox tableBox = new VBox(10);
        tableBox.getStyleClass().add("white-panel");
        VBox.setVgrow(tableBox, Priority.ALWAYS);

        Label tableHeader = new Label("Booking & Peminjaman Terbaru");
        tableHeader.getStyleClass().add("section-title");

        setupRecentLoansTable();
        refreshRecentLoansTable(null);
        VBox.setVgrow(recentLoansTable, Priority.ALWAYS);

        tableBox.getChildren().addAll(tableHeader, recentLoansTable);

        content.getChildren().addAll(topHeader, statRow, msgLabel, cardScroll, tableBox);
        mainLayout.setCenter(content);

        refreshCatalogStats();
        loadBooks("");
    }

    private void showLoansSection() {
        msgLabel.setText("");
        VBox content = new VBox(14);
        content.getStyleClass().add("content-area");

        Label pageTitle = new Label("Peminjaman Saya");
        pageTitle.getStyleClass().add("page-title");
        Label pageSub = new Label("Riwayat lengkap transaksi peminjaman, batas pengembalian, dan rincian denda");
        pageSub.getStyleClass().add("page-subtitle");

        VBox card = new VBox(12);
        card.getStyleClass().add("white-panel");
        VBox.setVgrow(card, Priority.ALWAYS);

        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("Semua Status", "REQUESTED", "DIPINJAM", "DIKEMBALIKAN");
        filterCombo.setValue("Semua Status");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-outline");

        filterCombo.setOnAction(e -> refreshRecentLoansTable(filterCombo.getValue()));
        refreshBtn.setOnAction(e -> refreshRecentLoansTable(filterCombo.getValue()));

        filterBar.getChildren().addAll(new Label("Filter Status:"), filterCombo, refreshBtn);

        setupRecentLoansTable();
        refreshRecentLoansTable("Semua Status");
        VBox.setVgrow(recentLoansTable, Priority.ALWAYS);

        card.getChildren().addAll(filterBar, recentLoansTable);
        content.getChildren().addAll(new VBox(2, pageTitle, pageSub), msgLabel, card);
        mainLayout.setCenter(content);
    }

    private VBox createStatCard(String label, Label valueLabel) {
        VBox card = new VBox(4);
        card.getStyleClass().add("metric-card");
        Label lbl = new Label(label);
        lbl.getStyleClass().add("metric-label");
        valueLabel.getStyleClass().add("metric-value");
        card.getChildren().addAll(lbl, valueLabel);
        return card;
    }

    private void loadBooks(String keyword) {
        loadBooksFiltered(keyword, "ALL");
    }

    private void loadBooksFiltered(String keyword, String filterType) {
        int requestId = bookRequest.incrementAndGet();
        bookCardsRow.getChildren().setAll(new Label("Memuat katalog..."));
        UiTasks.submit(() -> {
            List<Buku> books = (keyword == null || keyword.isBlank()) ? bukuDAO.findAll() : bukuDAO.search(keyword);
            if ("TERSEDIA".equals(filterType)) {
                books = books.stream().filter(b -> b.getStokBuku() > 0).toList();
            } else if ("HABIS".equals(filterType)) {
                books = books.stream().filter(b -> b.getStokBuku() == 0).toList();
            } else if ("SORT_TITLE".equals(filterType)) {
                books = books.stream().sorted((a, b) -> a.getJudulBuku().compareToIgnoreCase(b.getJudulBuku())).toList();
            } else if ("SORT_YEAR".equals(filterType)) {
                books = books.stream().sorted((a, b) -> Integer.compare(b.getTahunTerbit(), a.getTahunTerbit())).toList();
            } else if ("SORT_STOCK".equals(filterType)) {
                books = books.stream().sorted((a, b) -> Integer.compare(b.getStokBuku(), a.getStokBuku())).toList();
            }
            return books;
        }, books -> {
            if (requestId != bookRequest.get()) return;
            bookCardsRow.getChildren().clear();
            if (books.isEmpty()) {
                Label empty = new Label("Tidak ada buku yang cocok");
                empty.setStyle("-fx-text-fill: #64748b; -fx-padding: 30; -fx-font-size: 13px;");
                bookCardsRow.getChildren().add(empty);
            } else {
                int idx = 0;
                for (Buku b : books) {
                    bookCardsRow.getChildren().add(createBookCard(b, idx++));
                }
            }
        }, error -> {
            if (requestId == bookRequest.get()) {
                bookCardsRow.getChildren().clear();
                showFeedback(UiTasks.errorMessage(error), false);
            }
        });
    }

    private VBox createBookCard(Buku b, int index) {
        VBox card = new VBox(5);
        card.getStyleClass().add("book-card");

        StackPane cover = new StackPane();
        if (index % 3 == 0) cover.getStyleClass().add("book-cover");
        else if (index % 3 == 1) cover.getStyleClass().add("book-cover-alt1");
        else cover.getStyleClass().add("book-cover-alt2");

        Label coverText = new Label(b.getJudulBuku());
        coverText.getStyleClass().add("book-cover-text");
        cover.getChildren().add(coverText);

        HBox badgeRow = new HBox();
        boolean available = b.getStokBuku() > 0;
        Label badge = new Label(available ? "Tersedia (" + b.getStokBuku() + ")" : "Habis (0)");
        badge.getStyleClass().addAll("pill-badge", available ? "badge-tersedia" : "badge-habis");
        badgeRow.getChildren().add(badge);

        Label title = new Label(b.getJudulBuku());
        title.getStyleClass().add("book-title");
        title.setWrapText(false);

        Label author = new Label("Pengarang: " + b.getPengarang());
        author.getStyleClass().add("book-meta");

        Label year = new Label("Tahun: " + b.getTahunTerbit());
        year.getStyleClass().add("book-meta");

        Label isbn = new Label("ISBN: " + b.getIsbn());
        isbn.getStyleClass().add("book-isbn-text");

        Button bookBtn = new Button(available ? "Booking Buku" : "Habis");
        bookBtn.getStyleClass().add(available ? "btn-primary-blue" : "btn-outline");
        bookBtn.setDisable(!available);
        bookBtn.setMaxWidth(Double.MAX_VALUE);

        bookBtn.setOnAction(e -> {
            Pengguna u = UserSession.getInstance().getCurrentUser();
            if (u == null) return;
            String originalText = bookBtn.getText();
            bookBtn.setText("Mengirim...");
            UiTasks.submit(() -> {
                peminjamanDAO.create(u.getId(), b.getIdBuku());
                return null;
            }, ignored -> {
                bookBtn.setText(originalText);
                showFeedback("Booking '" + b.getJudulBuku() + "' berhasil diajukan!", true);
                refreshCatalogStats();
                loadBooks("");
                refreshRecentLoansTable(null);
            }, error -> {
                bookBtn.setText(originalText);
                showFeedback(UiTasks.errorMessage(error), false);
            }, bookBtn);
        });

        card.getChildren().addAll(cover, badgeRow, title, author, year, isbn, bookBtn);
        return card;
    }

    private void setupRecentLoansTable() {
        recentLoansTable.getColumns().clear();
        recentLoansTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<String[], String> idCol = new TableColumn<>("ID Transaksi");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        idCol.setPrefWidth(90);

        TableColumn<String[], String> kodeCol = new TableColumn<>("Kode");
        kodeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        kodeCol.setPrefWidth(80);

        TableColumn<String[], String> isbnCol = new TableColumn<>("ISBN");
        isbnCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[3]));
        isbnCol.setPrefWidth(110);

        TableColumn<String[], String> titleCol = new TableColumn<>("Judul");
        titleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[4]));
        titleCol.setPrefWidth(240);

        TableColumn<String[], String> bookingDateCol = new TableColumn<>("Booking");
        bookingDateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[6]));
        bookingDateCol.setPrefWidth(100);

        TableColumn<String[], String> borrowDateCol = new TableColumn<>("Dipinjam");
        borrowDateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[7]));
        borrowDateCol.setPrefWidth(100);

        TableColumn<String[], String> dueDateCol = new TableColumn<>("Jatuh Tempo");
        dueDateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[8]));
        dueDateCol.setPrefWidth(105);

        TableColumn<String[], String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[5]));
        statusCol.setPrefWidth(120);

        statusCol.setCellFactory(col -> new TableCell<String[], String>() {
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
        });

        TableColumn<String[], String> fineCol = new TableColumn<>("Denda");
        fineCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[9]));
        fineCol.setPrefWidth(100);

        recentLoansTable.getColumns().addAll(
                idCol, kodeCol, isbnCol, titleCol, bookingDateCol, borrowDateCol, dueDateCol, statusCol, fineCol);
    }

    private void refreshRecentLoansTable(String filter) {
        Pengguna u = UserSession.getInstance().getCurrentUser();
        if (u == null) return;
        int requestId = loanRequest.incrementAndGet();
        String statusParam = (filter == null || "Semua Status".equals(filter)) ? null : filter;
        UiTasks.submit(() -> peminjamanDAO.findRecords(statusParam, u.getId()), records -> {
            if (requestId != loanRequest.get()) return;
            recentLoansTable.setItems(FXCollections.observableArrayList(records));
        }, error -> {
            if (requestId == loanRequest.get()) showFeedback(UiTasks.errorMessage(error), false);
        }, recentLoansTable);
    }

    private void refreshCatalogStats() {
        int requestId = statsRequest.incrementAndGet();
        UiTasks.submit(() -> {
            List<Buku> all = bukuDAO.findAll();
            Pengguna u = UserSession.getInstance().getCurrentUser();
            List<String[]> myLoans = u == null ? List.of() : peminjamanDAO.findRecords(null, u.getId());
            long active = myLoans.stream()
                    .filter(row -> "DIPINJAM".equals(row[5]) || "REQUESTED".equals(row[5]))
                    .count();
            return new int[] { all.size(), all.stream().mapToInt(Buku::getStokBuku).sum(), (int) active };
        }, stats -> {
            if (requestId != statsRequest.get()) return;
            statTotal.setText(String.valueOf(stats[0]));
            statTersedia.setText(String.valueOf(stats[1]));
            statAktif.setText(String.valueOf(stats[2]));
        }, error -> {
            if (requestId == statsRequest.get()) {
                showFeedback("Gagal memuat statistik katalog: " + UiTasks.errorMessage(error), false);
            }
        });
    }

    private void showFeedback(String text, boolean success) {
        if (feedbackTimer != null) feedbackTimer.stop();
        msgLabel.setStyle(success ? "-fx-text-fill: #16a34a; -fx-font-weight: bold;" : "-fx-text-fill: #dc2626; -fx-font-weight: bold;");
        msgLabel.setText(text);
        feedbackTimer = new PauseTransition(Duration.seconds(3.5));
        feedbackTimer.setOnFinished(e -> msgLabel.setText(""));
        feedbackTimer.play();
    }
}
