# PerpusApp

PerpusApp adalah aplikasi desktop perpustakaan berbasis JavaFX. Aplikasi menyediakan
katalog buku, pengajuan peminjaman, pengelolaan transaksi, dan pengaturan akun.
Data disimpan di Supabase PostgreSQL dan diakses melalui Supabase Edge Function API
menggunakan HTTPS.

## Daftar Isi

- [Fitur](#fitur)
- [Teknologi dan arsitektur](#teknologi-dan-arsitektur)
- [Struktur proyek](#struktur-proyek)
- [Persiapan Supabase](#persiapan-supabase)
- [Menjalankan aplikasi untuk pengembangan](#menjalankan-aplikasi-untuk-pengembangan)
- [Membuat installer Windows](#membuat-installer-windows)
- [Keamanan](#keamanan)
- [Pemecahan masalah](#pemecahan-masalah)

## Fitur

### Anggota

- Mendaftar dan masuk ke aplikasi.
- Melihat, mencari, memfilter, dan mengurutkan katalog buku.
- Mengajukan peminjaman dan melihat status serta riwayat transaksi.
- Melihat dan memperbarui profil.

### Petugas dan administrator

- Mengelola data buku (tambah, cari, ubah, dan hapus).
- Menyetujui pengajuan peminjaman dan memproses pengembalian.
- Melihat laporan transaksi.
- Memperbarui profil dan aturan denda/durasi peminjaman.

### Super admin

- Mengelola data anggota dan petugas, termasuk biodata dan pengaturan ulang password.
- Membuat atau menghapus akun sesuai hak akses yang berlaku.

Operasi jaringan di layar katalog, peminjaman, administrasi, pengaturan, dan registrasi
dijalankan di background agar antarmuka JavaFX tetap dapat merespons.

## Teknologi dan arsitektur

- Java 17 dan JavaFX 17
- Maven
- Supabase PostgreSQL
- Supabase Edge Functions (Deno/TypeScript)
- HTTPS/JSON antara aplikasi desktop dan backend

Aplikasi desktop **tidak** terhubung langsung ke PostgreSQL. Alur data:

```text
Aplikasi JavaFX --HTTPS--> Supabase Edge Function --server-side--> Supabase PostgreSQL
```

Dengan demikian, password database, `service_role` key, dan secret backend hanya berada
di sisi server. Aplikasi memakai publishable/anon key yang memang bersifat publik.

## Struktur proyek

```text
.
├── src/
│   ├── main/java/com/perpus/       # Kode aplikasi JavaFX
│   ├── main/resources/             # Konfigurasi publik, ikon, dan stylesheet
│   └── test/java/                  # Unit test
├── supabase/
│   ├── functions/perpus-api/       # HTTPS API
│   └── migrations/                 # Fungsi dan perubahan skema database
├── scripts/
│   └── build-installer.ps1         # Build installer Windows
├── schema.sql                      # Skema awal database
├── pom.xml                         # Konfigurasi Maven
└── perpus-icon.ico                 # Ikon installer Windows
```

## Persiapan Supabase

Langkah berikut dilakukan satu kali untuk menyiapkan backend. Jangan masukkan kredensial
database atau secret server ke aplikasi desktop atau GitHub.

### 1. Siapkan skema database

1. Buat atau buka proyek Supabase.
2. Buka **SQL Editor** di Supabase Dashboard.
3. Jalankan [`schema.sql`](./schema.sql) untuk membuat tabel awal.
4. Jalankan migrasi [`20261115000000_perpus_api.sql`](./supabase/migrations/20261115000000_perpus_api.sql)
   untuk menyiapkan fungsi transaksi dan pembatasan percobaan login.

`schema.sql` hanya membuat struktur awal; file tersebut tidak mengimpor data dari TiDB/MySQL.

### 2. Atur dan deploy Edge Function

Pasang Supabase CLI, buka PowerShell di folder proyek, lalu jalankan:

```powershell
supabase login
supabase link --project-ref PROJECT_REF
```

Ganti `PROJECT_REF` dengan Project ID yang terlihat pada URL/API settings Supabase.
Di **Project Settings → Edge Functions → Secrets**, buat `APP_JWT_SECRET` dengan nilai
acak yang panjang (minimal 32 karakter). Simpan nilai tersebut hanya di Supabase.

Deploy API:

```powershell
supabase functions deploy perpus-api --no-verify-jwt
```

Opsi `--no-verify-jwt` digunakan karena fungsi memverifikasi token aplikasi sendiri.
Uji endpoint status dengan publishable/anon key dari Supabase:

```powershell
$headers = @{ apikey = "PUBLISHABLE_KEY_ANDA" }
Invoke-RestMethod `
  -Uri "https://PROJECT_REF.supabase.co/functions/v1/perpus-api/health" `
  -Headers $headers
```

Ganti `PUBLISHABLE_KEY_ANDA` dan `PROJECT_REF` sesuai proyek. Respons yang berhasil
menunjukkan status `ok`. Jangan gunakan `service_role` key untuk pengujian dari desktop.

### 3. Atur konfigurasi aplikasi

Konfigurasi publik aplikasi ada di
[`src/main/resources/app.properties`](./src/main/resources/app.properties):

```properties
api.url=https://PROJECT_REF.supabase.co/functions/v1/perpus-api
api.anon-key=PUBLISHABLE_KEY_ANDA
```

Gunakan URL proyek dan publishable/anon key milikmu. Publishable/anon key boleh ada di
aplikasi karena bukan secret; keamanan data harus ditegakkan oleh Edge Function.

### 4. Buat akun super admin pertama

Daftarkan akun anggota melalui aplikasi. Kemudian jalankan SQL berikut di SQL Editor,
dengan mengganti username:

```sql
UPDATE public.pengguna
SET role = 'SUPER_ADMIN'
WHERE lower(username) = lower('USERNAME_ANDA')
  AND role = 'MEMBER';
```

Pastikan hanya akun yang dimaksud yang berubah, lalu masuk dengan akun itu untuk mengelola
petugas dan anggota.

## Menjalankan aplikasi untuk pengembangan

Persyaratan:

- JDK 17
- Maven 3.8 atau lebih baru
- Backend Supabase sudah disiapkan dan Edge Function sudah aktif

Dari folder proyek:

```powershell
mvn test package
mvn javafx:run
```

Splash screen akan memeriksa koneksi API. Untuk memakai konfigurasi key melalui environment
variable di PowerShell saat sesi berjalan:

```powershell
$env:SUPABASE_ANON_KEY = "PUBLISHABLE_KEY_ANDA"
mvn javafx:run
```

Jangan pernah memakai password database, `APP_JWT_SECRET`, atau `service_role` key sebagai
nilai `SUPABASE_ANON_KEY`.

## Membuat installer Windows

Installer Windows `.exe` menyertakan runtime Java, sehingga komputer pengguna tidak perlu
memasang Java atau Maven. Diperlukan komputer pengembang Windows dengan:

- JDK 17 yang menyertakan `jpackage`
- Maven
- WiX Toolset 3.x, dengan `candle.exe` dan `light.exe` tersedia di `PATH`
- Ikon [`perpus-icon.ico`](./perpus-icon.ico)

Pastikan backend dan konfigurasi publik pada `app.properties` sudah benar, kemudian jalankan
PowerShell dari folder proyek:

```powershell
.\scripts\build-installer.ps1
```

Installer akan dibuat di folder `dist`. Bagikan file installer tersebut kepada pengguna;
komputer pengguna memerlukan Windows dan koneksi internet untuk memakai backend Supabase.
Installer ini dibangun untuk Windows pada arsitektur komputer pengembang (umumnya x64).

## Keamanan

- Jangan commit file `.env`, password database, `APP_JWT_SECRET`, atau `service_role` key.
- Jangan menaruh secret server dalam `app.properties`, source code desktop, atau installer.
- Publishable/anon key bukan secret dan tidak memberi izin administratif dengan sendirinya.
- Atur autentikasi, validasi input, dan hak akses di Edge Function; jangan mengandalkan
  penyembunyian konfigurasi pada aplikasi desktop.
- Jika kredensial database pernah dibagikan, rotasi password tersebut di Supabase.

## Pemecahan masalah

| Masalah | Yang perlu diperiksa |
|---|---|
| Maven mengatakan tidak ada `pom.xml` | Pindah ke folder proyek `ProjekAplikasiPerpus` sebelum menjalankan Maven. |
| Splash screen gagal terhubung | Periksa koneksi internet, URL API, publishable/anon key, dan status Edge Function. |
| API mengembalikan 404 | Pastikan fungsi `perpus-api` sudah di-deploy ke proyek Supabase yang benar. |
| Login atau fitur data gagal | Periksa deploy migrasi database, role akun, dan log Edge Function di Supabase. |
| `jpackage` tidak menemukan WiX | Tambahkan folder `bin` WiX Toolset ke `PATH`, lalu buka ulang PowerShell. |
| Installer gagal dibuat | Pastikan JDK 17, Maven, WiX, serta `candle.exe` dan `light.exe` tersedia di terminal. |

Setelah membangun installer, uji pemasangan di komputer Windows lain tanpa source code atau
VS Code. Periksa login untuk setiap role, pencarian buku, transaksi, pengaturan, dan pesan
yang muncul saat internet tidak tersedia.
