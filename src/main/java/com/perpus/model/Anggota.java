package com.perpus.model;

public class Anggota extends Pengguna {
    private String alamat;
    private String noTelepon;

    public Anggota() {
        setRole(Role.MEMBER);
    }

    public Anggota(int id, String nama, String username, String password, Role role, String alamat, String noTelepon) {
        super(id, nama, username, password, role);
        this.alamat = alamat;
        this.noTelepon = noTelepon;
    }

    public String getAlamat() { return alamat; }
    public void setAlamat(String alamat) { this.alamat = alamat; }

    public String getNoTelepon() { return noTelepon; }
    public void setNoTelepon(String noTelepon) { this.noTelepon = noTelepon; }
}
