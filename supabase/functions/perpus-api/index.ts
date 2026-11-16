import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

type Role = "SUPER_ADMIN" | "ADMIN" | "MEMBER";
type JsonRecord = Record<string, unknown>;

interface UserRecord {
  id: number;
  nama: string;
  username: string;
  password: string;
  role: Role;
  alamat: string | null;
  no_telepon: string | null;
}

interface Claims {
  sub: number;
  username: string;
  role: Role;
  exp: number;
}

class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

const encoder = new TextEncoder();
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
};

function respond(status: number, payload: JsonRecord): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}

function fail(status: number, code: string, message: string): never {
  throw new ApiError(status, code, message);
}

function record(value: unknown): JsonRecord {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    fail(400, "INVALID_BODY", "Format data tidak valid");
  }
  return value as JsonRecord;
}

function requiredText(body: JsonRecord, name: string, maxLength: number): string {
  const value = body[name];
  if (typeof value !== "string" || value.trim().length === 0 || value.length > maxLength) {
    fail(400, "INVALID_FIELD", `Data ${name} wajib diisi dan maksimal ${maxLength} karakter`);
  }
  return value.trim();
}

function requiredSecret(body: JsonRecord, name: string, maxLength: number): string {
  const value = body[name];
  if (typeof value !== "string" || value.trim().length === 0 || value.length > maxLength) {
    fail(400, "INVALID_FIELD", `${name} wajib diisi`);
  }
  return value;
}

function optionalText(body: JsonRecord, name: string, maxLength: number): string {
  const value = body[name];
  if (value === undefined || value === null) return "";
  if (typeof value !== "string" || value.length > maxLength) {
    fail(400, "INVALID_FIELD", `Data ${name} maksimal ${maxLength} karakter`);
  }
  return value.trim();
}

function requiredInteger(body: JsonRecord, name: string, minimum: number, maximum: number): number {
  const value = body[name];
  if (typeof value !== "number" || !Number.isInteger(value) || value < minimum || value > maximum) {
    fail(400, "INVALID_FIELD", `Data ${name} harus berupa angka bulat yang valid`);
  }
  return value;
}

async function readBody(request: Request): Promise<JsonRecord> {
  const text = await request.text();
  if (text.length > 32_768) fail(413, "BODY_TOO_LARGE", "Ukuran data terlalu besar");
  try {
    return record(JSON.parse(text));
  } catch (error) {
    if (error instanceof ApiError) throw error;
    fail(400, "INVALID_JSON", "Format JSON tidak valid");
  }
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/u, "");
}

function fromBase64Url(value: string): Uint8Array {
  const base64 = value.replaceAll("-", "+").replaceAll("_", "/");
  const binary = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="));
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

async function signingKey(): Promise<CryptoKey> {
  const secret = Deno.env.get("APP_JWT_SECRET");
  if (!secret || secret.length < 32) {
    throw new Error("APP_JWT_SECRET belum disetel atau terlalu pendek");
  }
  return crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign", "verify"],
  );
}

async function createToken(user: Pick<UserRecord, "id" | "username" | "role">): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(encoder.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payload = base64Url(encoder.encode(JSON.stringify({
    sub: user.id,
    username: user.username,
    role: user.role,
    iat: now,
    exp: now + 60 * 60 * 8,
  })));
  const input = `${header}.${payload}`;
  const signature = await crypto.subtle.sign("HMAC", await signingKey(), encoder.encode(input));
  return `${input}.${base64Url(new Uint8Array(signature))}`;
}

async function parseToken(request: Request, supabase: SupabaseClient): Promise<Claims> {
  const authorization = request.headers.get("Authorization");
  const match = authorization?.match(/^Bearer\s+([A-Za-z0-9._-]+)$/u);
  if (!match) fail(401, "UNAUTHORIZED", "Silakan login kembali");

  const parts = match[1].split(".");
  if (parts.length !== 3) fail(401, "UNAUTHORIZED", "Token login tidak valid");
  const input = `${parts[0]}.${parts[1]}`;
  let signatureValid = false;
  let payload: unknown;
  try {
    signatureValid = await crypto.subtle.verify(
      "HMAC",
      await signingKey(),
      fromBase64Url(parts[2]),
      encoder.encode(input),
    );
    payload = JSON.parse(new TextDecoder().decode(fromBase64Url(parts[1])));
  } catch {
    fail(401, "UNAUTHORIZED", "Token login tidak valid");
  }
  if (!signatureValid) fail(401, "UNAUTHORIZED", "Token login tidak valid");

  const claims = record(payload) as unknown as Claims;
  if (!Number.isInteger(claims.sub) || typeof claims.exp !== "number" || claims.exp <= Date.now() / 1000) {
    fail(401, "SESSION_EXPIRED", "Sesi login berakhir, silakan login kembali");
  }

  const { data, error } = await supabase
    .from("pengguna")
    .select("id, username, role")
    .eq("id", claims.sub)
    .maybeSingle();
  if (error) throw new Error("Gagal memvalidasi sesi pengguna");
  if (!data || data.username !== claims.username || data.role !== claims.role) {
    fail(401, "SESSION_REVOKED", "Sesi tidak lagi berlaku, silakan login kembali");
  }
  return claims;
}

function requireRole(claims: Claims, ...roles: Role[]): void {
  if (!roles.includes(claims.role)) fail(403, "FORBIDDEN", "Kamu tidak memiliki izin untuk tindakan ini");
}

function publicUser(user: UserRecord): JsonRecord {
  return {
    id: user.id,
    nama: user.nama,
    username: user.username,
    role: user.role,
    alamat: user.alamat ?? "",
    no_telepon: user.no_telepon ?? "",
  };
}

function decodeBase64(value: string): Uint8Array {
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

function encodeBase64(value: Uint8Array): string {
  let binary = "";
  for (const byte of value) binary += String.fromCharCode(byte);
  return btoa(binary);
}

async function hashPassword(password: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey("raw", encoder.encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations: 120_000, hash: "SHA-256" },
    key,
    256,
  );
  return `pbkdf2$${encodeBase64(salt)}$${encodeBase64(new Uint8Array(bits))}`;
}

function constantTimeEqual(left: Uint8Array, right: Uint8Array): boolean {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index++) difference |= left[index] ^ right[index];
  return difference === 0;
}

async function verifyPassword(password: string, stored: string): Promise<boolean> {
  if (!stored.startsWith("pbkdf2$")) return constantTimeEqual(encoder.encode(password), encoder.encode(stored));
  const parts = stored.split("$");
  if (parts.length !== 3) return false;
  try {
    const salt = decodeBase64(parts[1]);
    const expected = decodeBase64(parts[2]);
    if (salt.length !== 16 || expected.length !== 32) return false;
    const key = await crypto.subtle.importKey("raw", encoder.encode(password), "PBKDF2", false, ["deriveBits"]);
    const bits = await crypto.subtle.deriveBits(
      { name: "PBKDF2", salt, iterations: 120_000, hash: "SHA-256" },
      key,
      256,
    );
    return constantTimeEqual(new Uint8Array(bits), expected);
  } catch {
    return false;
  }
}

async function loginRateKey(username: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    encoder.encode(username.toLowerCase()),
  );
  return base64Url(new Uint8Array(digest));
}

async function login(supabase: SupabaseClient, body: JsonRecord): Promise<JsonRecord> {
  const username = requiredText(body, "username", 50);
  const password = requiredSecret(body, "password", 1024);
  const key = await loginRateKey(username);
  const { data: allowed, error: rateError } = await supabase.rpc("perpus_check_login_rate_limit", {
    p_login_key: key,
  });
  if (rateError) throw new Error("Gagal memeriksa batas percobaan login");
  if (allowed !== true) fail(429, "RATE_LIMITED", "Terlalu banyak percobaan login. Coba lagi setelah 10 menit");

  const { data, error } = await supabase
    .rpc("perpus_user_by_username", { p_username: username })
    .maybeSingle();
  if (error) throw new Error("Gagal memeriksa akun");
  const user = data as UserRecord | null;
  if (!user || !(await verifyPassword(password, user.password))) {
    fail(401, "INVALID_CREDENTIALS", "Username atau password salah");
  }

  if (!user.password.startsWith("pbkdf2$")) {
    const upgradedHash = await hashPassword(password);
    const { error: updateError } = await supabase
      .from("pengguna")
      .update({ password: upgradedHash })
      .eq("id", user.id)
      .eq("password", user.password);
    if (updateError) throw new Error("Gagal mengamankan password akun lama");
  }

  return {
    token: await createToken(user),
    user: publicUser(user),
  };
}

async function register(supabase: SupabaseClient, body: JsonRecord): Promise<JsonRecord> {
  const nama = requiredText(body, "nama", 100);
  const username = requiredText(body, "username", 50);
  const password = requiredSecret(body, "password", 1024);
  if (password.length < 4) fail(400, "WEAK_PASSWORD", "Password minimal 4 karakter");

  const { data, error } = await supabase
    .from("pengguna")
    .insert({
      nama,
      username,
      password: await hashPassword(password),
      role: "MEMBER",
      alamat: optionalText(body, "alamat", 255),
      no_telepon: optionalText(body, "noTelepon", 30),
    })
    .select("id, nama, username, role, alamat, no_telepon")
    .single();
  if (error?.code === "23505") fail(409, "USERNAME_TAKEN", "Username sudah digunakan");
  if (error) throw new Error("Gagal mendaftarkan akun");
  return { data };
}

async function handleApi(request: Request, supabase: SupabaseClient): Promise<JsonRecord> {
  const url = new URL(request.url);
  const segments = url.pathname.split("/").filter(Boolean);
  const functionIndex = segments.lastIndexOf("perpus-api");
  const path = functionIndex < 0 ? segments : segments.slice(functionIndex + 1);
  const method = request.method.toUpperCase();

  if (method === "POST" && path.join("/") === "auth/login") {
    return { data: await login(supabase, await readBody(request)) };
  }
  if (method === "POST" && path.join("/") === "auth/register") {
    return await register(supabase, await readBody(request));
  }
  if (method === "GET" && path.join("/") === "health") {
    const { error } = await supabase.from("aturan_perpustakaan").select("id").eq("id", 1).maybeSingle();
    if (error) throw new Error("Database belum dapat diakses");
    return { data: { status: "ok" } };
  }

  const claims = await parseToken(request, supabase);

  if (path[0] === "profile" && path.length === 1) {
    if (method === "GET") {
      const { data, error } = await supabase
        .from("pengguna")
        .select("id, nama, username, role, alamat, no_telepon")
        .eq("id", claims.sub)
        .single();
      if (error) throw new Error("Gagal memuat profil");
      return { data };
    }
    if (method === "PUT") {
      const body = await readBody(request);
      const updates = {
        nama: requiredText(body, "nama", 100),
        username: requiredText(body, "username", 50),
        alamat: optionalText(body, "alamat", 255),
        no_telepon: optionalText(body, "noTelepon", 30),
      };
      const { data, error } = await supabase
        .from("pengguna")
        .update(updates)
        .eq("id", claims.sub)
        .select("id, nama, username, role, alamat, no_telepon")
        .single();
      if (error?.code === "23505") fail(409, "USERNAME_TAKEN", "Username sudah digunakan");
      if (error) throw new Error("Gagal menyimpan profil");
      return { data };
    }
  }

  if (path[0] === "books" && path.length === 1) {
    if (method === "GET") {
      const search = url.searchParams.get("search")?.trim();
      const query = supabase
        .from("buku")
        .select("id_buku, judul_buku, isbn, pengarang, penerbit, tahun_terbit, stok_buku")
        .order("id_buku");
      if (search) {
        const { data, error } = await supabase.rpc("perpus_search_books", { p_search: search });
        if (error) throw new Error("Gagal mencari katalog buku");
        return { data };
      }
      const { data, error } = await query;
      if (error) throw new Error("Gagal memuat katalog buku");
      return { data };
    }
    if (method === "POST") {
      requireRole(claims, "ADMIN", "SUPER_ADMIN");
      const body = await readBody(request);
      const book = {
        id_buku: requiredText(body, "id_buku", 50),
        judul_buku: requiredText(body, "judul_buku", 200),
        isbn: optionalText(body, "isbn", 30) || "-",
        pengarang: optionalText(body, "pengarang", 100) || "-",
        penerbit: optionalText(body, "penerbit", 100) || "-",
        tahun_terbit: requiredInteger(body, "tahun_terbit", 0, 9999),
        stok_buku: requiredInteger(body, "stok_buku", 0, 1_000_000),
      };
      const { data, error } = await supabase.from("buku").insert(book).select().single();
      if (error?.code === "23505") fail(409, "BOOK_EXISTS", "Kode buku sudah terdaftar");
      if (error) throw new Error("Gagal menambahkan buku");
      return { data };
    }
  }

  if (path[0] === "books" && path.length === 2) {
    requireRole(claims, "ADMIN", "SUPER_ADMIN");
    const id = decodeURIComponent(path[1]);
    if (method === "PUT") {
      const body = await readBody(request);
      const { data, error } = await supabase.rpc("perpus_update_book", {
        p_id_buku: id,
        p_judul_buku: requiredText(body, "judul_buku", 200),
        p_isbn: optionalText(body, "isbn", 30) || "-",
        p_pengarang: optionalText(body, "pengarang", 100) || "-",
        p_penerbit: optionalText(body, "penerbit", 100) || "-",
        p_tahun_terbit: requiredInteger(body, "tahun_terbit", 0, 9999),
        p_stok_buku: requiredInteger(body, "stok_buku", 0, 1_000_000),
      });
      if (error?.code === "P0001") fail(409, "BOOK_UPDATE_REJECTED", error.message);
      if (error?.code === "P0002") fail(404, "BOOK_NOT_FOUND", "Buku tidak ditemukan");
      if (error) throw new Error("Gagal mengubah buku");
      if (!Array.isArray(data) || data.length === 0) fail(404, "BOOK_NOT_FOUND", "Buku tidak ditemukan");
      return { data: data[0] };
    }
    if (method === "DELETE") {
      const { error } = await supabase.from("buku").delete().eq("id_buku", id);
      if (error?.code === "23503") fail(409, "BOOK_HAS_LOANS", "Buku tidak dapat dihapus karena memiliki riwayat peminjaman");
      if (error) throw new Error("Gagal menghapus buku");
      return { data: { deleted: true } };
    }
  }

  if (path[0] === "loans" && path.length === 1) {
    if (method === "GET") {
      const status = url.searchParams.get("status");
      let query = supabase
        .from("peminjaman")
        .select("id_peminjaman, pengguna!peminjaman_id_anggota_fkey(nama), id_buku, buku!peminjaman_id_buku_fkey(isbn, judul_buku), status, tanggal_booking, tanggal_pinjam, tanggal_jatuh_tempo, denda")
        .order("id_peminjaman", { ascending: false });
      if (claims.role === "MEMBER") query = query.eq("id_anggota", claims.sub);
      else if (url.searchParams.has("userId")) {
        query = query.eq("id_anggota", requiredInteger(
          { userId: Number(url.searchParams.get("userId")) },
          "userId",
          1,
          Number.MAX_SAFE_INTEGER,
        ));
      }
      if (status && status !== "Semua Status") query = query.eq("status", status);
      const { data, error } = await query;
      if (error) throw new Error("Gagal memuat transaksi peminjaman");
      return { data };
    }
    if (method === "POST") {
      requireRole(claims, "MEMBER");
      const body = await readBody(request);
      const bookId = requiredText(body, "id_buku", 50);
      const { data, error } = await supabase.rpc("perpus_create_loan", {
        p_member_id: claims.sub,
        p_book_id: bookId,
      });
      if (error) fail(409, "LOAN_NOT_CREATED", error.message);
      return { data: { id_peminjaman: data } };
    }
  }

  if (path[0] === "loans" && path.length === 3 && /^\d+$/u.test(path[1])) {
    requireRole(claims, "ADMIN", "SUPER_ADMIN");
    const loanId = Number(path[1]);
    if (!Number.isSafeInteger(loanId) || loanId <= 0) fail(400, "INVALID_ID", "ID peminjaman tidak valid");
    if (method === "POST" && path[2] === "approve") {
      const { error } = await supabase.rpc("perpus_approve_loan", { p_loan_id: loanId });
      if (error) fail(409, "LOAN_NOT_APPROVED", error.message);
      return { data: { approved: true } };
    }
    if (method === "POST" && path[2] === "return") {
      const { data, error } = await supabase.rpc("perpus_return_loan", { p_loan_id: loanId });
      if (error) fail(409, "LOAN_NOT_RETURNED", error.message);
      return { data: { fine: data } };
    }
  }

  if (path[0] === "users" && path.length === 1) {
    if (method === "GET") {
      requireRole(claims, "ADMIN", "SUPER_ADMIN");
      const role = url.searchParams.get("role");
      if (role !== "MEMBER" && role !== "ADMIN") fail(400, "INVALID_ROLE", "Role pengguna tidak valid");
      if (role === "ADMIN") requireRole(claims, "SUPER_ADMIN");
      const { data, error } = await supabase
        .from("pengguna")
        .select("id, nama, username, role, alamat, no_telepon")
        .eq("role", role)
        .order("nama");
      if (error) throw new Error("Gagal memuat daftar pengguna");
      return { data };
    }
    if (method === "POST") {
      requireRole(claims, "SUPER_ADMIN");
      const body = await readBody(request);
      const role = body.role;
      if (role !== "MEMBER" && role !== "ADMIN") fail(400, "INVALID_ROLE", "Role pengguna tidak valid");
      const nama = requiredText(body, "nama", 100);
      const username = requiredText(body, "username", 50);
      const password = requiredSecret(body, "password", 1024);
      if (password.length < 4) fail(400, "WEAK_PASSWORD", "Password minimal 4 karakter");
      const { data, error } = await supabase
        .from("pengguna")
        .insert({
          nama,
          username,
          password: await hashPassword(password),
          role,
          alamat: optionalText(body, "alamat", 255),
          no_telepon: optionalText(body, "noTelepon", 30),
        })
        .select("id, nama, username, role, alamat, no_telepon")
        .single();
      if (error?.code === "23505") fail(409, "USERNAME_TAKEN", "Username sudah digunakan");
      if (error) throw new Error("Gagal menambahkan pengguna");
      return { data };
    }
  }

  if (path[0] === "users" && path.length === 2 && /^\d+$/u.test(path[1])) {
    requireRole(claims, "ADMIN", "SUPER_ADMIN");
    const userId = Number(path[1]);
    if (!Number.isSafeInteger(userId) || userId <= 0) fail(400, "INVALID_ID", "ID pengguna tidak valid");
    const { data: target, error: targetError } = await supabase
      .from("pengguna")
      .select("id, role")
      .eq("id", userId)
      .maybeSingle();
    if (targetError) throw new Error("Gagal memeriksa akun pengguna");
    if (!target || target.role === "SUPER_ADMIN") fail(404, "USER_NOT_FOUND", "Pengguna tidak ditemukan");
    if (target.role === "ADMIN") requireRole(claims, "SUPER_ADMIN");
    if (userId === claims.sub) fail(403, "SELF_MANAGEMENT_DENIED", "Akun yang sedang digunakan tidak dapat diubah dari menu ini");

    if (method === "PUT") {
      requireRole(claims, "SUPER_ADMIN");
      const body = await readBody(request);
      const updates: JsonRecord = {
        nama: requiredText(body, "nama", 100),
        username: requiredText(body, "username", 50),
        alamat: optionalText(body, "alamat", 255),
        no_telepon: optionalText(body, "noTelepon", 30),
      };
      if (typeof body.password === "string" && body.password.length > 0) {
        if (body.password.length < 4 || body.password.length > 1024) {
          fail(400, "WEAK_PASSWORD", "Password baru minimal 4 karakter");
        }
        updates.password = await hashPassword(body.password);
      }
      const { data, error } = await supabase
        .from("pengguna")
        .update(updates)
        .eq("id", userId)
        .select("id, nama, username, role, alamat, no_telepon")
        .single();
      if (error?.code === "23505") fail(409, "USERNAME_TAKEN", "Username sudah digunakan");
      if (error) throw new Error("Gagal memperbarui akun");
      return { data };
    }
    if (method === "DELETE") {
      const { error } = await supabase.from("pengguna").delete().eq("id", userId);
      if (error?.code === "23503") fail(409, "USER_HAS_LOANS", "Pengguna tidak dapat dihapus karena memiliki riwayat peminjaman");
      if (error) throw new Error("Gagal menghapus akun");
      return { data: { deleted: true } };
    }
  }

  if (path[0] === "rules" && path.length === 1) {
    requireRole(claims, "ADMIN", "SUPER_ADMIN");
    if (method === "GET") {
      const { data, error } = await supabase
        .from("aturan_perpustakaan")
        .select("denda_per_hari, durasi_pinjam_hari")
        .eq("id", 1)
        .single();
      if (error) throw new Error("Gagal memuat aturan perpustakaan");
      return { data };
    }
    if (method === "PUT") {
      const body = await readBody(request);
      const updates = {
        denda_per_hari: requiredInteger(body, "finePerDay", 0, 2_000_000_000),
        durasi_pinjam_hari: requiredInteger(body, "loanDurationDays", 1, 3650),
      };
      const { data, error } = await supabase
        .from("aturan_perpustakaan")
        .update(updates)
        .eq("id", 1)
        .select("denda_per_hari, durasi_pinjam_hari")
        .single();
      if (error) throw new Error("Gagal menyimpan aturan perpustakaan");
      return { data };
    }
  }

  fail(404, "NOT_FOUND", "Endpoint API tidak ditemukan");
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const secretKeys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") ?? "{}") as Record<string, string>;
    const serviceRoleKey = secretKeys.default ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !serviceRoleKey) {
      throw new Error("Konfigurasi Supabase Edge Function belum lengkap");
    }
    const supabase = createClient(supabaseUrl, serviceRoleKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const data = await handleApi(request, supabase);
    return respond(200, data);
  } catch (error) {
    if (error instanceof ApiError) {
      return respond(error.status, { error: { code: error.code, message: error.message } });
    }
    console.error("Perpus API request failed", error);
    return respond(500, {
      error: { code: "INTERNAL_ERROR", message: "Terjadi kesalahan pada server. Coba lagi nanti." },
    });
  }
});
