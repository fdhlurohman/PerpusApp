package com.perpus;

import com.perpus.model.Buku;
import com.perpus.model.LibraryRules;
import com.perpus.security.PasswordUtil;
import com.perpus.service.LibraryService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

public class LibraryServiceTest {

    @Test
    void testDueDateSevenDays() {
        LocalDate borrow = LocalDate.of(2026, 11, 8);
        LocalDate due = LibraryService.dueDate(borrow);
        assertEquals(LocalDate.of(2026, 11, 15), due);
    }

    @Test
    void testCustomLoanDurationAndFine() {
        LocalDate borrow = LocalDate.of(2026, 11, 8);
        LocalDate due = LibraryService.dueDate(borrow, 14);
        assertEquals(LocalDate.of(2026, 11, 22), due);
        assertEquals(9000.0, LibraryService.fine(due, due.plusDays(3), 3000));
    }

    @Test
    void testLibraryRulesValidation() {
        assertThrows(IllegalArgumentException.class, () -> new LibraryRules(-1, 7));
        assertThrows(IllegalArgumentException.class, () -> new LibraryRules(2000, 0));
    }

    @Test
    void testFineCalculationLateAndOnTime() {
        LocalDate due = LocalDate.of(2026, 11, 10);
        assertEquals(0.0, LibraryService.fine(due, LocalDate.of(2026, 11, 10)));
        assertEquals(0.0, LibraryService.fine(due, LocalDate.of(2026, 11, 8)));
        assertEquals(6000.0, LibraryService.fine(due, LocalDate.of(2026, 11, 13)));
    }

    @Test
    void testStockApproveAndReturn() {
        Buku b = new Buku("BK-01", "Java", "Author", 2);
        LibraryService.approve(b);
        assertEquals(1, b.getStokBuku());
        LibraryService.returned(b);
        assertEquals(2, b.getStokBuku());
    }

    @Test
    void testApproveEmptyStockThrows() {
        Buku b = new Buku("BK-02", "Kotlin", "Author", 0);
        assertThrows(IllegalStateException.class, () -> LibraryService.approve(b));
    }

    @Test
    void testPasswordHashingAndVerification() {
        String raw = "secret123";
        String hashed = PasswordUtil.hash(raw);
        assertNotEquals(raw, hashed);
        assertTrue(PasswordUtil.isHashed(hashed));
        assertTrue(PasswordUtil.verify(raw, hashed));
        assertFalse(PasswordUtil.verify("wrong", hashed));
    }

    @Test
    void testLegacyPasswordCanBeVerifiedAndMalformedHashFailsClosed() {
        assertFalse(PasswordUtil.isHashed("legacy-password"));
        assertTrue(PasswordUtil.verify("legacy-password", "legacy-password"));
        assertFalse(PasswordUtil.verify("wrong", "legacy-password"));
        assertFalse(PasswordUtil.verify("secret", "pbkdf2$invalid"));
        assertFalse(PasswordUtil.verify(null, "legacy-password"));
    }
}
