# DocScanner – Implementation Plan (Cloud Integration)

## Stack tổng thể

| Phần | Công nghệ |
|------|-----------|
| Mobile | Android (Kotlin), Firebase Auth SDK |
| Backend | XAMPP + CodeIgniter 4 (PHP) |
| Database | MySQL (trong XAMPP) |
| File storage | Server local (`htdocs/uploads/`) |
| Auth | Firebase Auth (Google) → verify trên server → JWT riêng |

---

## Phase 1 — Mobile: Settings + Google Login

> Làm trước, không cần server. Chỉ cần Firebase project.

### Bước chuẩn bị (1 lần, thủ công)
- [ ] Tạo Firebase project tại [console.firebase.google.com](https://console.firebase.google.com)
- [ ] Thêm Android app với package: `com.example.docscanner`
- [ ] Bật **Authentication → Google Sign-In**
- [ ] Tải `google-services.json` → đặt vào `app/`
- [ ] Lấy **Web Client ID** từ Firebase Console (dùng trong code)

### Gradle
- [ ] Thêm Firebase plugin vào `build.gradle.kts` (root)
- [ ] Thêm dependencies vào `build.gradle.kts` (app):
  - `firebase-auth`
  - `play-services-auth`
  - `glide` (load ảnh avatar)
  - `INTERNET` permission vào `AndroidManifest.xml`

### UI Settings
- [ ] Tạo `activity_settings.xml`:
  - Avatar (ImageView tròn)
  - Tên + Email (TextView)
  - Nút "Đăng nhập với Google" (ẩn khi đã login)
  - Nút "Đăng xuất" (ẩn khi chưa login)
- [ ] Tạo `SettingsActivity.kt`:
  - Khởi tạo `FirebaseAuth` + `GoogleSignInClient`
  - Kiểm tra trạng thái login → cập nhật UI
  - Xử lý đăng nhập Google
  - Xử lý đăng xuất
- [ ] Khai báo `SettingsActivity` trong `AndroidManifest.xml`

### Navigation
- [ ] Wire `BottomNavigationView` trong `MainActivity.kt`:
  - `nav_home` → ở lại home
  - `nav_settings` → mở `SettingsActivity`

---

## Phase 2 — Backend: CodeIgniter 4 REST API

### Cài đặt môi trường
- [ ] Cài XAMPP → bật Apache + MySQL
- [ ] Cài CodeIgniter 4 vào `htdocs/docscanner-api/`
- [ ] Cài thư viện verify Firebase token cho PHP:
  `kreait/firebase-php` (qua Composer)

### Database (MySQL)
- [ ] Tạo database `docscanner`
- [ ] Tạo bảng `users`: `id, firebase_uid, email, display_name, photo_url, created_at`
- [ ] Tạo bảng `documents`: `id, user_id, file_name, file_path, file_size, share_token, created_at`

### API Endpoints
- [ ] `POST /api/auth/login` — verify Firebase idToken → tạo JWT → trả app
- [ ] `GET  /api/documents` — list PDF của user (cần JWT)
- [ ] `POST /api/documents/upload` — nhận file PDF, lưu vào `uploads/{uid}/`
- [ ] `DELETE /api/documents/{id}` — xóa PDF
- [ ] `GET  /api/documents/{id}/share` — trả về public share link
- [ ] `GET  /files/{share_token}` — endpoint công khai, trả file PDF (không cần JWT)

### Bảo mật
- [ ] Middleware kiểm tra JWT cho tất cả endpoint (trừ `/files/`)
- [ ] Mỗi user chỉ thao tác được file của mình

---

## Phase 3 — Mobile: Kết nối API

> Làm sau khi Phase 2 xong và test API bằng Postman thành công.

### Setup
- [ ] Thêm Retrofit + OkHttp vào `build.gradle.kts`
- [ ] Tạo `ApiService.kt` (interface Retrofit)
- [ ] Tạo `ApiClient.kt` (singleton Retrofit instance, tự gắn JWT vào header)

### Tính năng
- [ ] Sau login: gửi idToken lên `POST /api/auth/login` → lưu JWT vào `SharedPreferences`
- [ ] Trang chủ: load danh sách PDF từ `GET /api/documents` (thay thế load local)
- [ ] Sau lưu PDF: tự động upload lên `POST /api/documents/upload`
- [ ] Nút Share trên item: gọi `GET /api/documents/{id}/share` → copy link ra clipboard
- [ ] Nút Delete: gọi `DELETE /api/documents/{id}`

---

## Thứ tự làm khuyến nghị

```
[1] Firebase Console setup (thủ công, ~15 phút)
 ↓
[2] Phase 1: Settings UI + Google Login (mobile)
 ↓
[3] Phase 2: Cài XAMPP + CodeIgniter, viết API
 ↓
[4] Test API bằng Postman
 ↓
[5] Phase 3: Retrofit trong app, kết nối API
 ↓
[6] Test end-to-end toàn bộ flow
```

> [!IMPORTANT]
> Khi test trên điện thoại thật + XAMPP:
> Điện thoại và máy tính phải **cùng WiFi**.
> URL trong app: `http://192.168.x.x/docscanner-api/api/...`
> Dùng **ngrok** nếu muốn test ngoài mạng nội bộ.
