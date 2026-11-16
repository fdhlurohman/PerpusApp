package com.perpus.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.perpus.config.ApiClient;
import com.perpus.model.Anggota;
import com.perpus.model.Pengguna;
import com.perpus.model.Petugas;
import com.perpus.model.Role;

public class PenggunaDAO {

    public Pengguna login(String username, String password) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username tidak boleh kosong");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password tidak boleh kosong");

        JsonNode user;
        try {
            user = ApiClient.login(username.trim(), password);
        } catch (ApiClient.ApiException ex) {
            if (ex.getStatusCode() == 401) return null;
            throw ex;
        }

        int id = user.path("id").asInt();
        String nama = user.path("nama").asText();
        String resolvedUsername = user.path("username").asText();
        Role role = Role.valueOf(user.path("role").asText());
        if (role == Role.MEMBER) {
            return new Anggota(
                    id,
                    nama,
                    resolvedUsername,
                    "",
                    role,
                    user.path("alamat").asText(""),
                    user.path("no_telepon").asText(""));
        }
        return new Petugas(id, nama, resolvedUsername, "", role);
    }

    public void register(String nama, String username, String password, String alamat, String noTelepon) {
        if (nama == null || nama.isBlank()) throw new IllegalArgumentException("Nama lengkap wajib diisi");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username tidak boleh kosong");
        if (password == null || password.length() < 4) throw new IllegalArgumentException("Password minimal 4 karakter");
        if (nama.trim().length() > 100) throw new IllegalArgumentException("Nama maksimal 100 karakter");
        if (username.trim().length() > 50) throw new IllegalArgumentException("Username maksimal 50 karakter");
        if (alamat != null && alamat.length() > 255) throw new IllegalArgumentException("Alamat maksimal 255 karakter");
        if (noTelepon != null && noTelepon.length() > 30) throw new IllegalArgumentException("Nomor telepon maksimal 30 karakter");

        try {
            ApiClient.register(nama.trim(), username.trim(), password, alamat, noTelepon);
        } catch (ApiClient.ApiException ex) {
            if (ex.getStatusCode() == 409) {
                throw new IllegalArgumentException("Username '" + username.trim() + "' sudah digunakan", ex);
            }
            throw ex;
        }
    }
}
