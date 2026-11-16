package com.perpus.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.perpus.config.ApiClient;
import com.perpus.model.Buku;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class BukuDAO {

    public List<Buku> findAll() {
        return mapBooks(ApiClient.get("books"));
    }

    public List<Buku> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return findAll();
        return mapBooks(ApiClient.get("books?search=" + ApiClient.encodeQuery(keyword.trim())));
    }

    public Buku findById(String idBuku) {
        if (idBuku == null || idBuku.isBlank()) return null;
        return findAll().stream()
                .filter(book -> book.getIdBuku().equals(idBuku.trim()))
                .findFirst()
                .orElse(null);
    }

    public void insert(Buku book) {
        validateBuku(book);
        try {
            ApiClient.post("books", toPayload(book, true));
        } catch (ApiClient.ApiException ex) {
            if (ex.getStatusCode() == 409) {
                throw new IllegalArgumentException("Kode buku '" + book.getIdBuku() + "' sudah terdaftar", ex);
            }
            throw new IllegalStateException("Gagal menambah buku: " + ex.getMessage(), ex);
        }
    }

    public void update(Buku book) {
        validateBuku(book);
        try {
            ApiClient.put("books/" + ApiClient.encodePathSegment(book.getIdBuku().trim()), toPayload(book, false));
        } catch (ApiClient.ApiException ex) {
            throw new IllegalStateException("Gagal mengubah buku: " + ex.getMessage(), ex);
        }
    }

    public void delete(String idBuku) {
        if (idBuku == null || idBuku.isBlank()) throw new IllegalArgumentException("Kode buku wajib dipilih");
        try {
            ApiClient.delete("books/" + ApiClient.encodePathSegment(idBuku.trim()));
        } catch (ApiClient.ApiException ex) {
            if (ex.getStatusCode() == 409) {
                throw new IllegalStateException("Buku tidak dapat dihapus karena tercatat pada transaksi peminjaman", ex);
            }
            if (ex.getStatusCode() == 404) throw new NoSuchElementException(ex.getMessage());
            throw new IllegalStateException("Gagal menghapus buku: " + ex.getMessage(), ex);
        }
    }

    private List<Buku> mapBooks(JsonNode rows) {
        List<Buku> books = new ArrayList<>();
        if (!rows.isArray()) throw new IllegalStateException("Format daftar buku dari server tidak valid");
        for (JsonNode row : rows) {
            books.add(new Buku(
                    row.path("id_buku").asText(),
                    row.path("judul_buku").asText(),
                    row.path("isbn").asText("-"),
                    row.path("pengarang").asText("-"),
                    row.path("penerbit").asText("-"),
                    row.path("tahun_terbit").asInt(),
                    row.path("stok_buku").asInt()));
        }
        return books;
    }

    private Map<String, Object> toPayload(Buku book, boolean includeId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (includeId) payload.put("id_buku", book.getIdBuku().trim());
        payload.put("judul_buku", book.getJudulBuku().trim());
        payload.put("isbn", book.getIsbn());
        payload.put("pengarang", book.getPengarang());
        payload.put("penerbit", book.getPenerbit());
        payload.put("tahun_terbit", book.getTahunTerbit());
        payload.put("stok_buku", book.getStokBuku());
        return payload;
    }

    private void validateBuku(Buku book) {
        if (book == null) throw new IllegalArgumentException("Data buku wajib diisi");
        if (book.getIdBuku() == null || book.getIdBuku().isBlank()) throw new IllegalArgumentException("Kode buku wajib diisi");
        if (book.getJudulBuku() == null || book.getJudulBuku().isBlank()) throw new IllegalArgumentException("Judul buku wajib diisi");
        if (book.getIdBuku().trim().length() > 50) throw new IllegalArgumentException("Kode buku maksimal 50 karakter");
        if (book.getJudulBuku().trim().length() > 200) throw new IllegalArgumentException("Judul buku maksimal 200 karakter");
        if (book.getIsbn() != null && book.getIsbn().length() > 30) throw new IllegalArgumentException("ISBN maksimal 30 karakter");
        if (book.getPengarang() != null && book.getPengarang().length() > 100) throw new IllegalArgumentException("Nama pengarang maksimal 100 karakter");
        if (book.getPenerbit() != null && book.getPenerbit().length() > 100) throw new IllegalArgumentException("Nama penerbit maksimal 100 karakter");
        if (book.getTahunTerbit() < 0) throw new IllegalArgumentException("Tahun terbit tidak boleh negatif");
        if (book.getStokBuku() < 0) throw new IllegalArgumentException("Stok tidak boleh negatif");
    }
}
