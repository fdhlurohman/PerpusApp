package com.perpus.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.perpus.config.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PeminjamanDAO {

    public int create(int idAnggota, String idBuku) {
        if (idAnggota <= 0) throw new IllegalArgumentException("Anggota tidak valid");
        if (idBuku == null || idBuku.isBlank()) throw new IllegalArgumentException("Kode buku wajib dipilih");
        JsonNode result = ApiClient.post("loans", Map.of("id_buku", idBuku.trim()));
        return result.path("id_peminjaman").asInt();
    }

    public void approve(int idPeminjaman, String idBuku) {
        if (idPeminjaman <= 0) throw new IllegalArgumentException("ID transaksi tidak valid");
        if (idBuku == null || idBuku.isBlank()) throw new IllegalArgumentException("Kode buku wajib dipilih");
        ApiClient.post("loans/" + idPeminjaman + "/approve", Map.of());
    }

    public double returnBook(int idPeminjaman, String idBuku) {
        if (idPeminjaman <= 0) throw new IllegalArgumentException("ID transaksi tidak valid");
        if (idBuku == null || idBuku.isBlank()) throw new IllegalArgumentException("Kode buku wajib dipilih");
        JsonNode result = ApiClient.post("loans/" + idPeminjaman + "/return", Map.of());
        return result.path("fine").asDouble();
    }

    public List<String[]> findRecords(String statusFilter, Integer userIdFilter) {
        StringBuilder path = new StringBuilder("loans");
        List<String> query = new ArrayList<>();
        if (statusFilter != null && !statusFilter.isBlank()) {
            query.add("status=" + ApiClient.encodeQuery(statusFilter.trim()));
        }
        if (userIdFilter != null) {
            query.add("userId=" + ApiClient.encodeQuery(String.valueOf(userIdFilter)));
        }
        if (!query.isEmpty()) path.append('?').append(String.join("&", query));

        JsonNode rows = ApiClient.get(path.toString());
        if (!rows.isArray()) throw new IllegalStateException("Format riwayat peminjaman dari server tidak valid");
        List<String[]> result = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode user = relation(row.path("pengguna"));
            JsonNode book = relation(row.path("buku"));
            result.add(new String[] {
                    row.path("id_peminjaman").asText(),
                    text(user.path("nama"), "-"),
                    text(row.path("id_buku"), "-"),
                    text(book.path("isbn"), "-"),
                    text(book.path("judul_buku"), "-"),
                    text(row.path("status"), "-"),
                    text(row.path("tanggal_booking"), "-"),
                    text(row.path("tanggal_pinjam"), "-"),
                    text(row.path("tanggal_jatuh_tempo"), "-"),
                    "Rp " + String.format("%,.0f", row.path("denda").asDouble())
            });
        }
        return result;
    }

    private JsonNode relation(JsonNode value) {
        return value.isArray() ? value.path(0) : value;
    }

    private String text(JsonNode value, String fallback) {
        return value.isMissingNode() || value.isNull() ? fallback : value.asText(fallback);
    }
}
