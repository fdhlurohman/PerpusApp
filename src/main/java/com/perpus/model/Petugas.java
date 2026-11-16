package com.perpus.model;

public class Petugas extends Pengguna {
    public Petugas() {
        setRole(Role.ADMIN);
    }

    public Petugas(int id, String nama, String username, String password, Role role) {
        super(id, nama, username, password, role);
    }
}
