package com.perpus.service;

import com.perpus.model.Buku;
import java.time.Duration;
import java.time.LocalDate;

public final class LibraryService {
    private LibraryService() {}

    public static LocalDate dueDate(LocalDate borrow) {
        return dueDate(borrow, 7);
    }

    public static LocalDate dueDate(LocalDate borrow, int loanDurationDays) {
        if (borrow == null) throw new IllegalArgumentException("Tanggal peminjaman wajib diisi");
        if (loanDurationDays < 1) throw new IllegalArgumentException("Durasi pinjam minimal 1 hari");
        return borrow.plusDays(loanDurationDays);
    }

    public static double fine(LocalDate due, LocalDate returned) {
        return fine(due, returned, 2000);
    }

    public static double fine(LocalDate due, LocalDate returned, int finePerDay) {
        if (due == null || returned == null) throw new IllegalArgumentException("Tanggal jatuh tempo dan pengembalian wajib diisi");
        if (finePerDay < 0) throw new IllegalArgumentException("Denda harian tidak boleh negatif");
        long days = Math.max(0, Duration.between(due.atStartOfDay(), returned.atStartOfDay()).toDays());
        return fine(days, finePerDay);
    }

    public static double fine(long lateDays, int finePerDay) {
        if (finePerDay < 0) throw new IllegalArgumentException("Denda harian tidak boleh negatif");
        return Math.max(0, lateDays) * (double) finePerDay;
    }

    public static void approve(Buku b) {
        if (b.getStokBuku() <= 0) throw new IllegalStateException("Stok buku habis");
        b.setStokBuku(b.getStokBuku() - 1);
    }

    public static void returned(Buku b) {
        b.setStokBuku(b.getStokBuku() + 1);
    }
}
