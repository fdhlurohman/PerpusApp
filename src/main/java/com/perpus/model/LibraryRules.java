package com.perpus.model;

public record LibraryRules(int finePerDay, int loanDurationDays) {
    public LibraryRules {
        if (finePerDay < 0) throw new IllegalArgumentException("Denda harian tidak boleh negatif");
        if (loanDurationDays < 1) throw new IllegalArgumentException("Durasi pinjam minimal 1 hari");
    }
}
