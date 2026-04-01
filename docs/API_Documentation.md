# 📋 Tài liệu API – DocScanner Backend (CodeIgniter 3)

## Tổng quan kiến trúc

```
[Android App / Web Browser]
        │
        │  HTTP (qua ngrok hoặc cùng WiFi)
        ▼
[CI3 Backend – XAMPP]
        │
        ├── /uploads/        ← File PDF/ảnh lưu tại đây
        │
        └── MySQL (tbl_upload_images)
```

| Thành phần | Vai trò |
|---|---|
| Android App | Chụp ảnh → tạo PDF → upload lên server |
| Web Frontend | Xem danh sách PDF đã upload, xoá |
| CI3 Backend | Nhận request, lưu file, đọc/ghi MySQL |
| MySQL | Lưu metadata: tên file, mô tả, userId, ngày tạo |
| `/uploads/` | Lưu file vật lý (PDF, JPG) |

---

## Luồng hoạt động khi chụp ảnh

```
1. Người dùng chụp ảnh bằng camera trong app
2. App xử lý ảnh (crop, enhance)
3. Người dùng nhấn "Lưu" → app tạo file PDF
4. File PDF được lưu tạm vào bộ nhớ điện thoại
5. App tự động gọi API Upload → gửi file lên server
6. Server lưu file vào /htdocs/webapi/uploads/<md5_hash>.pdf
7. Server insert 1 record vào tbl_upload_images
8. Lần sau mở app → gọi API GetUpload → hiện danh sách
```

---

## API 1: Upload

### Thông tin
| | |
|---|---|
| **URL** | `POST /index.php/upload/image` |
| **Content-Type** | `multipart/form-data` |
| **File** | `/application/controllers/Upload.php` |

### Tham số gửi lên
| Tham số | Kiểu | Mô tả |
|---|---|---|
| `userId` | String | Firebase UID của người dùng |
| `image` | File | File PDF hoặc ảnh JPG/PNG |
| `description` | String | Tên/mô tả tài liệu (ví dụ: "bangbeo") |

### Ví dụ request (Android – Retrofit)
```kotlin
val userId   = RequestBody.create("text/plain".toMediaType(), auth.uid)
val desc     = RequestBody.create("text/plain".toMediaType(), "bangbeo")
val filePart = MultipartBody.Part.createFormData("image", file.name,
                   file.asRequestBody("application/pdf".toMediaType()))

ApiClient.api.uploadImage(userId, filePart, desc)
```

### Server làm gì?
1. Nhận file, kiểm tra đuôi mở rộng (jpg/png/pdf)
2. Hash tên file theo MD5 để tránh trùng: `md5(original_name).pdf`
3. Lưu file vào `/htdocs/webapi/uploads/`
4. Insert vào `tbl_upload_images`:
   ```sql
   INSERT INTO tbl_upload_images
     (user_id, description, image_url, active, created_at)
   VALUES
     ('uid_firebase', 'bangbeo', 'http://localhost/webapi/uploads/a1b2c3.pdf', 1, NOW())
   ```

### Response trả về
```json
// Thành công
{ "status": true, "message": "Upload Success" }

// Thất bại
{ "status": false, "message": "Upload Failed" }
```

---

## API 2: GetUpload

### Thông tin
| | |
|---|---|
| **URL** | `GET /index.php/getupload/image?userId=<uid>` |
| **Content-Type** | `application/json` |
| **File** | `/application/controllers/GetUpload.php` |

### Tham số
| Tham số | Kiểu | Mô tả |
|---|---|---|
| `userId` | String (query param) | Firebase UID để lọc file của đúng người dùng |

### Ví dụ request
```
GET https://zelma-xxx.ngrok-free.dev/webapi/index.php/getupload/image?userId=abc123uid
Header: ngrok-skip-browser-warning: true
```

### Server làm gì?
1. Lấy `userId` từ query string
2. Query MySQL:
   ```sql
   SELECT id, description, image_url, created_at
   FROM tbl_upload_images
   WHERE user_id = 'abc123uid' AND active = 1
   ORDER BY id DESC
   ```
3. Trả về danh sách dạng JSON

### Response trả về
```json
{
  "status": true,
  "message": "Success",
  "data": [
    {
      "id": 5,
      "description": "bangbeo",
      "imageUrl": "http://localhost/webapi/uploads/a1b2c3.pdf",
      "uploadDate": 1743213215000
    },
    {
      "id": 4,
      "description": "test",
      "imageUrl": "http://localhost/webapi/uploads/x9y8z7.pdf",
      "uploadDate": 1743211993000
    }
  ]
}
```

> ⚠️ `imageUrl` trong DB lưu `localhost` → Android app tự dynamic-replace thành URL ngrok/IP thật khi hiển thị.

---

## API 3: Delete

### Thông tin
| | |
|---|---|
| **URL** | `POST /index.php/delete/image` |
| **Content-Type** | `application/x-www-form-urlencoded` |
| **File** | `/application/controllers/Delete.php` |

### Tham số
| Tham số | Kiểu | Mô tả |
|---|---|---|
| `userId` | String | Firebase UID – để xác minh quyền sở hữu |
| `id` | Int | ID của record cần xoá (lấy từ response GetUpload) |

### Ví dụ request (Android – Retrofit)
```kotlin
ApiClient.api.deleteImage(userId = auth.uid, id = 5)
```

### Ví dụ request (Web – Fetch)
```javascript
fetch(`${NGROK_BASE}/webapi/index.php/delete/image`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `userId=${uid}&id=5`
})
```

### Server làm gì?
**Soft delete** – không xoá vật lý, chỉ set `active = 0`:
```sql
UPDATE tbl_upload_images
SET active = 0
WHERE id = 5 AND user_id = 'abc123uid'
```
> Người dùng A không thể xoá file của người dùng B vì `WHERE user_id` phải khớp.

### Response trả về
```json
// Thành công
{ "status": true, "message": "Deleted successfully" }

// Không tìm thấy hoặc sai userId
{ "status": false, "message": "Not found or unauthorized" }
```

---

## Database Schema

### Bảng: `tbl_upload_images`

| Cột | Kiểu | Mô tả |
|---|---|---|
| `id` | INT AUTO_INCREMENT | Khoá chính |
| `user_id` | VARCHAR | Firebase UID |
| `description` | VARCHAR | Tên mô tả file |
| `image_url` | TEXT | URL đầy đủ đến file |
| `active` | TINYINT | 1 = tồn tại, 0 = đã xoá |
| `created_at` | DATETIME | Ngày tạo |

### File vật lý lưu ở đâu?
```
/Applications/XAMPP/xamppfiles/htdocs/webapi/uploads/
├── a1b2c3d4e5f6.pdf      ← file PDF scan
├── 9f8e7d6c5b4a.jpg      ← file ảnh scan
└── ...
```

---

## Bảng tóm tắt 3 API

| API | Method | Endpoint | Tác dụng |
|---|---|---|---|
| **Upload** | POST | `/index.php/upload/image` | Nhận file từ app, lưu vào `/uploads/` và MySQL |
| **GetUpload** | GET | `/index.php/getupload/image` | Trả danh sách file của user từ MySQL |
| **Delete** | POST | `/index.php/delete/image` | Soft-delete record (active=0), giấu file khỏi danh sách |
