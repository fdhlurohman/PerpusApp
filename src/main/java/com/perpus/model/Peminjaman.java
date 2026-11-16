package com.perpus.model;

import java.time.LocalDate;

public class Peminjaman {
    private int idPeminjaman;
    private Anggota anggota;
    private Buku buku;
    private LocalDate tanggalBooking;
    private LocalDate tanggalPinjam;
    private LocalDate tanggalJatuhTempo;
    private LocalDate tanggalKembaliSebenarnya;
    private StatusPeminjaman status;
    private double denda;

    public Peminjaman() {}

    public Peminjaman(int idPeminjaman, Anggota anggota, Buku buku, LocalDate tanggalBooking,
                      LocalDate tanggalPinjam, LocalDate tanggalJatuhTempo,
                      LocalDate tanggalKembaliSebenarnya, StatusPeminjaman status, double denda) {
        this.idPeminjaman = idPeminjaman;
        this.anggota = anggota;
        this.buku = buku;
        this.tanggalBooking = tanggalBooking;
        this.tanggalPinjam = tanggalPinjam;
        this.tanggalJatuhTempo = tanggalJatuhTempo;
        this.tanggalKembaliSebenarnya = tanggalKembaliSebenarnya;
        this.status = status;
        this.denda = denda;
    }

    public int getIdPeminjaman() { return idPeminjaman; }
    public void setIdPeminjaman(int idPeminjaman) { this.idPeminjaman = idPeminjaman; }

    public Anggota getAnggota() { return anggota; }
    public void setAnggota(Anggota anggota) { this.anggota = anggota; }

    public Buku getBuku() { return buku; }
    public void setBuku(Buku buku) { this.buku = buku; }

    public LocalDate getTanggalBooking() { return tanggalBooking; }
    public void setTanggalBooking(LocalDate tanggalBooking) { this.tanggalBooking = tanggalBooking; }

    public LocalDate getTanggalPinjam() { return tanggalPinjam; }
    public void setTanggalPinjam(LocalDate tanggalPinjam) { this.tanggalPinjam = tanggalPinjam; }

    public LocalDate getTanggalJatuhTempo() { return tanggalJatuhTempo; }
    public void setTanggalJatuhTempo(LocalDate tanggalJatuhTempo) { this.tanggalJatuhTempo = tanggalJatuhTempo; }

    public LocalDate getTanggalKembaliSebenarnya() { return tanggalKembaliSebenarnya; }
    public void setTanggalKembaliSebenarnya(LocalDate tanggalKembaliSebenarnya) { this.tanggalKembaliSebenarnya = tanggalKembaliSebenarnya; }

    public StatusPeminjaman getStatus() { return status; }
    public void setStatus(StatusPeminjaman status) { this.status = status; }

    public double getDenda() { return denda; }
    public void setDenda(double denda) { this.denda = denda; }
}
