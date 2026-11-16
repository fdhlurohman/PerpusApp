package com.perpus.model;

public class Buku {
    private String idBuku;
    private String judulBuku;
    private String isbn;
    private String pengarang;
    private String penerbit;
    private int tahunTerbit;
    private int stokBuku;

    public Buku() {}

    public Buku(String idBuku, String judulBuku, String pengarang, int stokBuku) {
        this(idBuku, judulBuku, "-", pengarang, "-", 2024, stokBuku);
    }

    public Buku(String idBuku, String judulBuku, String isbn, String pengarang, String penerbit, int tahunTerbit, int stokBuku) {
        if (idBuku == null || idBuku.isBlank()) throw new IllegalArgumentException("Kode buku wajib diisi");
        if (judulBuku == null || judulBuku.isBlank()) throw new IllegalArgumentException("Judul buku wajib diisi");
        if (stokBuku < 0) throw new IllegalArgumentException("Stok tidak boleh negatif");
        this.idBuku = idBuku.trim();
        this.judulBuku = judulBuku.trim();
        this.isbn = (isbn != null && !isbn.isBlank()) ? isbn.trim() : "-";
        this.pengarang = (pengarang != null && !pengarang.isBlank()) ? pengarang.trim() : "-";
        this.penerbit = (penerbit != null && !penerbit.isBlank()) ? penerbit.trim() : "-";
        this.tahunTerbit = tahunTerbit;
        this.stokBuku = stokBuku;
    }

    public String getIdBuku() { return idBuku; }
    public void setIdBuku(String idBuku) { this.idBuku = idBuku; }

    public String getJudulBuku() { return judulBuku; }
    public void setJudulBuku(String judulBuku) { this.judulBuku = judulBuku; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getPengarang() { return pengarang; }
    public void setPengarang(String pengarang) { this.pengarang = pengarang; }

    public String getPenerbit() { return penerbit; }
    public void setPenerbit(String penerbit) { this.penerbit = penerbit; }

    public int getTahunTerbit() { return tahunTerbit; }
    public void setTahunTerbit(int tahunTerbit) { this.tahunTerbit = tahunTerbit; }

    public int getStokBuku() { return stokBuku; }
    public void setStokBuku(int stokBuku) { this.stokBuku = stokBuku; }

    @Override
    public String toString() {
        return idBuku + " - " + judulBuku + " (" + pengarang + ") [Stok: " + stokBuku + "]";
    }
}
