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

### Cài đặt môi trường (Theo mentor)
- [ ] Cài XAMPP → bật Apache + MySQL
- [ ] Cài CodeIgniter 4 vào dự án API
- [ ] Cấu hình VirtualHost Apache ở port `8001` trỏ đến public folder của API để giả lập domain:
  ```apache
  Listen 8001
  <VirtualHost *:8001>
      DocumentRoot "Đường/dẫn/đến/thư/mục/api/public"
      ServerName  localhost:8001
      <Directory "Đường/dẫn/đến/thư/mục/api/public">
          Options FollowSymLinks
          AllowOverride All
          DirectoryIndex index.php index.html
          Require all granted
          RewriteEngine On
          RewriteCond %{REQUEST_FILENAME} !-f
          RewriteCond %{REQUEST_FILENAME} !-d
          RewriteRule ^(.*)$ index.php/$1 [L]
      </Directory>
  </VirtualHost>
  ```
- [ ] Cài thư viện verify Firebase token cho PHP:
  `kreait/firebase-php` (qua Composer)

### Database (MySQL qua DBeaver)
- [ ] Tạo database `docscanner`
- [ ] Tạo bảng `tbl_upload_images` (theo CSDL mentor cung cấp):
  ```sql
  CREATE TABLE tbl_upload_images (
    id int(11) NOT NULL AUTO_INCREMENT,
    active tinyint(1) DEFAULT 1,
    user_id varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
    description varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    image_url varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    created_at datetime DEFAULT current_timestamp(),
    updated_at datetime DEFAULT NULL,
    PRIMARY KEY (id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
  ```
- [ ] Tạo bảng `users`: `id, firebase_uid, email, display_name, photo_url, created_at` (Dùng để link với `user_id` ở bảng trên)

### API Endpoints
- [ ] **Auth**: `POST /api/auth/login` — verify Firebase idToken → tạo Token/Session → trả app
- [ ] **Images**: 
  - `GET  /api/images` — lấy danh sách ảnh của user từ `tbl_upload_images`
  - `POST /api/images/upload` — nhận file lưu vào `uploads/` trên server (trỏ từ VirtualHost), lưu URL vào `tbl_upload_images`
  - `DELETE /api/images/{id}` — update `active = 0` (soft delete) trong `tbl_upload_images`

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
