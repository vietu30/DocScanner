# DocScanner – Roadmap

## ✅ Phase 1: Mobile App (Done)

**Stack:** Android (Kotlin), CameraX, UCrop, iText7

| Tính năng | Trạng thái |
|-----------|-----------|
| Chụp ảnh bằng camera | ✅ |
| Crop ảnh (tỉ lệ A4) | ✅ |
| Preview nhiều ảnh (ViewPager2) | ✅ |
| Xuất PDF nhiều trang | ✅ |
| Đặt tên file PDF | ✅ |
| Danh sách PDF tại trang chủ | ✅ |
| Mở PDF bằng app ngoài | ✅ |
| Quay về trang chủ sau khi lưu | ✅ |

---

## 🔵 Phase 2: Web API

**Stack đề xuất:** ASP.NET Core / Node.js / FastAPI + Database (PostgreSQL / MySQL)

### Endpoints cần xây dựng

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| `POST` | `/api/documents/upload` | Upload file PDF từ app |
| `GET` | `/api/documents` | Lấy danh sách tài liệu của user |
| `GET` | `/api/documents/{id}` | Lấy chi tiết 1 tài liệu |
| `DELETE` | `/api/documents/{id}` | Xóa tài liệu |
| `POST` | `/api/auth/login` | Đăng nhập |
| `POST` | `/api/auth/register` | Đăng ký tài khoản |

### Database schema (tối thiểu)

```
Users: id, email, password_hash, created_at
Documents: id, user_id, file_name, file_url, file_size, created_at
```

### Storage
- Lưu file PDF trên server local hoặc cloud (Azure Blob / AWS S3 / Firebase Storage)

---

## 🟣 Phase 3: Tích hợp API vào Mobile App

Sau khi API hoàn thành, thêm vào app:

| Việc cần làm | Ghi chú |
|-------------|---------|
| Thêm `INTERNET` permission vào Manifest | |
| Thêm **Retrofit + OkHttp** vào `build.gradle` | HTTP client |
| Màn hình **Đăng nhập / Đăng ký** | Mới hoàn toàn |
| Nút **Upload** trên từng item PDF | Icon đã có sẵn trong layout, đang comment out |
| Trạng thái upload (chưa / đang / đã upload) | Badge trên item |
| Xem PDF từ server (remote) | Nếu cần |

---

## 🟤 Phase 4: Tính năng nâng cao (Optional)

- OCR (nhận dạng chữ từ ảnh scan) — ML Kit hoặc Tesseract
- Tìm kiếm nội dung trong PDF
- Chia sẻ PDF qua link
- Đa ngôn ngữ (i18n)
