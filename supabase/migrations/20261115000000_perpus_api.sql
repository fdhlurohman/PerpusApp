CREATE TABLE IF NOT EXISTS public.perpus_login_attempts (
    login_key TEXT PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0)
);

ALTER TABLE public.perpus_login_attempts ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.perpus_user_by_username(p_username TEXT)
RETURNS SETOF public.pengguna
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT *
    FROM public.pengguna
    WHERE lower(username) = lower(p_username)
    LIMIT 1
$$;

CREATE OR REPLACE FUNCTION public.perpus_search_books(p_search TEXT)
RETURNS SETOF public.buku
LANGUAGE sql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT *
    FROM public.buku
    WHERE id_buku ILIKE '%' || p_search || '%'
       OR judul_buku ILIKE '%' || p_search || '%'
       OR isbn ILIKE '%' || p_search || '%'
       OR pengarang ILIKE '%' || p_search || '%'
    ORDER BY id_buku
$$;

CREATE OR REPLACE FUNCTION public.perpus_update_book(
    p_id_buku TEXT,
    p_judul_buku TEXT,
    p_isbn TEXT,
    p_pengarang TEXT,
    p_penerbit TEXT,
    p_tahun_terbit INTEGER,
    p_stok_buku INTEGER
)
RETURNS SETOF public.buku
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    requested_count BIGINT;
BEGIN
    IF p_id_buku IS NULL OR btrim(p_id_buku) = ''
       OR p_judul_buku IS NULL OR btrim(p_judul_buku) = ''
       OR p_tahun_terbit < 0 OR p_stok_buku < 0 THEN
        RAISE EXCEPTION 'Data buku tidak valid' USING ERRCODE = '22023';
    END IF;

    PERFORM 1 FROM public.buku WHERE id_buku = btrim(p_id_buku) FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Buku tidak ditemukan' USING ERRCODE = 'P0002';
    END IF;

    SELECT count(*) INTO requested_count
    FROM public.peminjaman
    WHERE id_buku = btrim(p_id_buku) AND status = 'REQUESTED';

    IF p_stok_buku < requested_count THEN
        RAISE EXCEPTION 'Stok tidak boleh kurang dari jumlah booking yang menunggu persetujuan'
            USING ERRCODE = 'P0001';
    END IF;

    RETURN QUERY
    UPDATE public.buku
    SET judul_buku = btrim(p_judul_buku),
        isbn = coalesce(nullif(btrim(p_isbn), ''), '-'),
        pengarang = coalesce(nullif(btrim(p_pengarang), ''), '-'),
        penerbit = coalesce(nullif(btrim(p_penerbit), ''), '-'),
        tahun_terbit = p_tahun_terbit,
        stok_buku = p_stok_buku
    WHERE id_buku = btrim(p_id_buku)
    RETURNING *;
END;
$$;

CREATE OR REPLACE FUNCTION public.perpus_check_login_rate_limit(p_login_key TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    current_attempts INTEGER;
BEGIN
    INSERT INTO public.perpus_login_attempts (login_key, window_started_at, attempts)
    VALUES (p_login_key, now(), 1)
    ON CONFLICT (login_key) DO UPDATE
    SET window_started_at = CASE
            WHEN public.perpus_login_attempts.window_started_at < now() - interval '10 minutes'
            THEN now()
            ELSE public.perpus_login_attempts.window_started_at
        END,
        attempts = CASE
            WHEN public.perpus_login_attempts.window_started_at < now() - interval '10 minutes'
            THEN 1
            ELSE public.perpus_login_attempts.attempts + 1
        END
    RETURNING attempts INTO current_attempts;

    RETURN current_attempts <= 10;
END;
$$;

CREATE OR REPLACE FUNCTION public.perpus_create_loan(p_member_id INTEGER, p_book_id TEXT)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    available_stock INTEGER;
    reserved_count BIGINT;
    new_loan_id INTEGER;
BEGIN
    IF p_member_id <= 0 OR p_book_id IS NULL OR btrim(p_book_id) = '' THEN
        RAISE EXCEPTION 'Data anggota atau buku tidak valid' USING ERRCODE = '22023';
    END IF;

    SELECT stok_buku INTO available_stock
    FROM public.buku
    WHERE id_buku = btrim(p_book_id)
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Buku tidak ditemukan di katalog' USING ERRCODE = 'P0002';
    END IF;
    IF available_stock <= 0 THEN
        RAISE EXCEPTION 'Stok buku sedang habis' USING ERRCODE = 'P0001';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.peminjaman
        WHERE id_anggota = p_member_id
          AND id_buku = btrim(p_book_id)
          AND status IN ('REQUESTED', 'DIPINJAM')
    ) THEN
        RAISE EXCEPTION 'Anda masih memiliki transaksi aktif untuk buku ini' USING ERRCODE = 'P0001';
    END IF;

    SELECT count(*) INTO reserved_count
    FROM public.peminjaman
    WHERE id_buku = btrim(p_book_id) AND status = 'REQUESTED';

    IF reserved_count >= available_stock THEN
        RAISE EXCEPTION 'Stok buku sedang dipesan anggota lain' USING ERRCODE = 'P0001';
    END IF;

    INSERT INTO public.peminjaman (id_anggota, id_buku, tanggal_booking, status, denda)
    VALUES (p_member_id, btrim(p_book_id), current_date, 'REQUESTED', 0)
    RETURNING id_peminjaman INTO new_loan_id;

    RETURN new_loan_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.perpus_approve_loan(p_loan_id INTEGER)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    loan_book_id TEXT;
    loan_status TEXT;
    loan_duration INTEGER;
BEGIN
    SELECT id_buku, status INTO loan_book_id, loan_status
    FROM public.peminjaman
    WHERE id_peminjaman = p_loan_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Transaksi peminjaman tidak ditemukan' USING ERRCODE = 'P0002';
    END IF;
    IF loan_status <> 'REQUESTED' THEN
        RAISE EXCEPTION 'Hanya permohonan REQUESTED yang dapat disetujui' USING ERRCODE = 'P0001';
    END IF;

    UPDATE public.buku
    SET stok_buku = stok_buku - 1
    WHERE id_buku = loan_book_id AND stok_buku > 0;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Stok buku habis atau tidak mencukupi' USING ERRCODE = 'P0001';
    END IF;

    SELECT durasi_pinjam_hari INTO loan_duration
    FROM public.aturan_perpustakaan WHERE id = 1;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Aturan perpustakaan belum tersedia' USING ERRCODE = 'P0001';
    END IF;

    UPDATE public.peminjaman
    SET status = 'DIPINJAM',
        tanggal_pinjam = current_date,
        tanggal_jatuh_tempo = current_date + loan_duration
    WHERE id_peminjaman = p_loan_id AND status = 'REQUESTED';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Status peminjaman gagal diperbarui' USING ERRCODE = 'P0001';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.perpus_return_loan(p_loan_id INTEGER)
RETURNS NUMERIC
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    loan_book_id TEXT;
    loan_status TEXT;
    due_date DATE;
    daily_fine INTEGER;
    total_fine NUMERIC(12, 2);
BEGIN
    SELECT id_buku, status, tanggal_jatuh_tempo
    INTO loan_book_id, loan_status, due_date
    FROM public.peminjaman
    WHERE id_peminjaman = p_loan_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Transaksi tidak ditemukan' USING ERRCODE = 'P0002';
    END IF;
    IF loan_status <> 'DIPINJAM' THEN
        RAISE EXCEPTION 'Hanya transaksi DIPINJAM yang dapat dikembalikan' USING ERRCODE = 'P0001';
    END IF;
    IF due_date IS NULL THEN
        RAISE EXCEPTION 'Tanggal jatuh tempo belum tercatat' USING ERRCODE = 'P0001';
    END IF;

    SELECT denda_per_hari INTO daily_fine
    FROM public.aturan_perpustakaan WHERE id = 1;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Aturan perpustakaan belum tersedia' USING ERRCODE = 'P0001';
    END IF;

    total_fine := greatest(0, current_date - due_date) * daily_fine;

    UPDATE public.buku SET stok_buku = stok_buku + 1 WHERE id_buku = loan_book_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Buku tidak ditemukan saat memperbarui stok' USING ERRCODE = 'P0001';
    END IF;

    UPDATE public.peminjaman
    SET status = 'DIKEMBALIKAN',
        tanggal_kembali_sebenarnya = current_date,
        denda = total_fine
    WHERE id_peminjaman = p_loan_id AND status = 'DIPINJAM';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Status pengembalian gagal diperbarui' USING ERRCODE = 'P0001';
    END IF;

    RETURN total_fine;
END;
$$;

REVOKE ALL ON FUNCTION public.perpus_user_by_username(TEXT) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.perpus_search_books(TEXT) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.perpus_update_book(TEXT, TEXT, TEXT, TEXT, TEXT, INTEGER, INTEGER) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.perpus_check_login_rate_limit(TEXT) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.perpus_create_loan(INTEGER, TEXT) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.perpus_approve_loan(INTEGER) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.perpus_return_loan(INTEGER) FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.perpus_user_by_username(TEXT) TO service_role;
GRANT EXECUTE ON FUNCTION public.perpus_search_books(TEXT) TO service_role;
GRANT EXECUTE ON FUNCTION public.perpus_update_book(TEXT, TEXT, TEXT, TEXT, TEXT, INTEGER, INTEGER) TO service_role;
GRANT EXECUTE ON FUNCTION public.perpus_check_login_rate_limit(TEXT) TO service_role;
GRANT EXECUTE ON FUNCTION public.perpus_create_loan(INTEGER, TEXT) TO service_role;
GRANT EXECUTE ON FUNCTION public.perpus_approve_loan(INTEGER) TO service_role;
GRANT EXECUTE ON FUNCTION public.perpus_return_loan(INTEGER) TO service_role;
