# 🎓 Câu hỏi Thuyết trình – DocScanner Q&A

---

## 1. Kiến trúc và Luồng dữ liệu

### ❓ Luồng xác thực (Authentication flow) diễn ra như thế nào?

Dự án này dùng **Firebase UID** thay cho token đầy đủ (simplified auth):

```
1. User nhập email + password → Firebase SDK xác thực
2. Firebase trả về FirebaseUser → lấy uid (ví dụ: "abc123XYZ")
3. App gửi uid đó kèm theo mỗi request lên CI3:
   GET /index.php/getupload/image?userId=abc123XYZ
4. CI3 dùng userId để WHERE trong MySQL, chỉ trả dữ liệu của đúng người đó
```

> **Lưu ý thành thật khi thuyết trình:** Hệ thống hiện tại chưa verify token phía server (CI3 tin user_id gửi lên). Đây là điểm có thể cải thiện bằng cách dùng Firebase ID Token và verify bằng Firebase Admin SDK.

---

### ❓ Tại sao tách riêng Backend API và Frontend Web?

**Lợi ích chính: Dùng chung 1 API cho cả Mobile lẫn Web**

```
Android App ──┐
              ├──► CI3 API ──► MySQL
Web Browser ──┘
```

- Không phải viết logic xử lý 2 lần
- Thay đổi logic nghiệp vụ chỉ cần sửa 1 chỗ (API)
- Web và Mobile luôn nhất quán về dữ liệu
- Dễ mở rộng thêm nền tảng khác (iOS, desktop...)

---

### ❓ Mô tả vòng đời của một Request Upload?

```
1. User nhấn "Upload" trên điện thoại

2. App đóng gói request (multipart/form-data):
   - userId  = "abc123XYZ"       (text)
   - description = "bangbeo"     (text)
   - image   = file.pdf          (binary)

3. Gửi qua HTTP POST đến:
   https://<ngrok>/webapi/index.php/upload/image

4. CI3 nhận request:
   ├── Kiểm tra định dạng file (chỉ cho jpg/png/pdf)
   ├── Hash tên file: md5("file.pdf") → "a1b2c3.pdf"
   ├── Lưu file vào /htdocs/webapi/uploads/a1b2c3.pdf
   └── INSERT vào tbl_upload_images (user_id, description, image_url...)

5. CI3 trả Response JSON:
   { "status": true, "message": "Upload Success" }

6. App hiện thông báo thành công
```

---

## 2. Bảo mật API

### ❓ Làm sao ngăn user A xem/xoá tài liệu của user B?

**GetUpload:** Query MySQL luôn kèm điều kiện `user_id`:
```sql
SELECT * FROM tbl_upload_images
WHERE user_id = 'userId_gửi_lên' AND active = 1
```
→ Dù biết ID file của người khác, cũng không truy vấn được vì `user_id` không khớp.

**Delete:** Cũng WHERE cả `id` lẫn `user_id`:
```sql
UPDATE tbl_upload_images SET active = 0
WHERE id = 5 AND user_id = 'userId_gửi_lên'
```
→ `affected_rows = 0` nếu file không thuộc về user đó → báo lỗi "Not found or unauthorized".

---

### ❓ Copy link API paste lên trình duyệt thì xảy ra gì?

- **GetUpload** (`GET ?userId=xxx`): Trả về JSON rỗng `{"status":false,"data":[]}` nếu không có userId đúng. Không lộ dữ liệu người khác.
- **Upload/Delete** (`POST`): Nếu gọi bằng `GET` từ browser → CI3 không nhận được param → trả `{"status":false,"message":"Missing userId"}`.
- Ngoài ra ngrok yêu cầu header `ngrok-skip-browser-warning` → browser thường sẽ thấy trang cảnh báo ngrok thay vì data.

> **Cải thiện có thể đề xuất:** Thêm middleware kiểm tra method (chỉ cho POST) và trả HTTP 405 nếu dùng GET cho endpoint POST.

---

### ❓ Validate dữ liệu đầu vào — chặn file độc hại không?

Có kiểm tra **phần mở rộng file** trong controller Upload:

```php
$allowed = ['jpg', 'jpeg', 'png', 'pdf'];
$ext = strtolower(pathinfo($filename, PATHINFO_EXTENSION));
if (!in_array($ext, $allowed)) → từ chối upload
```

> File `.exe`, `.php` sẽ bị từ chối ngay. Tuy nhiên kiểm tra extension chưa phải bảo mật tuyệt đối — cải thiện tốt hơn là kiểm tra MIME type thực của file bằng `finfo_file()`.

---

## 3. Xử lý Logic và Hiệu năng

### ❓ File vật lý lưu ở đâu?

```
/Applications/XAMPP/xamppfiles/htdocs/webapi/uploads/
├── a1b2c3d4.pdf
├── x9y8z7w6.jpg
└── ...
```

- Database (`tbl_upload_images`) chỉ lưu **đường dẫn URL** đến file (`image_url`)
- File thực tế nằm trên **ổ cứng server** (máy đang chạy XAMPP)
- Không dùng Cloud Storage — đây là lựa chọn phù hợp cho môi trường học tập, dễ kiểm soát

---

### ❓ File nặng, nhiều người upload cùng lúc thì sao?

**Thực tế trong dự án:** Dùng XAMPP local nên giới hạn upload mặc định là `upload_max_filesize = 2MB` trong `php.ini`. Có thể tăng lên nếu cần.

**Trong quá trình dev có gặp:** Timeout khi upload file lớn qua ngrok (tunnel chậm hơn local). Giải quyết bằng cách giảm resolution ảnh trước khi tạo PDF.

**Nếu scale lên production:** Nên dùng queue (hàng đợi) để xử lý upload bất đồng bộ, và chuyển lưu file sang S3/Cloud Storage.

---

### ❓ 1000 tài liệu — lấy hết 1 lần hay phân trang?

Hiện tại: **Lấy hết 1 lần** — query không có LIMIT.

```sql
SELECT * FROM tbl_upload_images WHERE user_id = ? AND active = 1
```

**Đây là điểm có thể cải thiện** — thêm Pagination:
```
GET /index.php/getupload/image?userId=x&page=1&limit=20
```
```sql
SELECT * FROM tbl_upload_images WHERE user_id = ?
LIMIT 20 OFFSET 0
```

Chưa implement vì phạm vi dự án nhỏ, lượng user test ít tài liệu.

---

## 4. Phối hợp và Tiêu chuẩn hóa RESTful

### ❓ API có theo chuẩn RESTful không?

**Gần đúng chuẩn nhưng có điểm chưa thuần RESTful:**

| Endpoint | Method hiện tại | Chuẩn REST |
|---|---|---|
| Lấy danh sách | GET ✅ | GET /images |
| Upload file | POST ✅ | POST /images |
| Xoá file | POST ⚠️ | Nên là DELETE /images/{id} |

Dùng POST cho Delete vì CI3 3 xử lý route đơn giản hơn, và `<form>` HTML không hỗ trợ method DELETE.

### ❓ Làm sao Mobile và Web biết format dữ liệu giống nhau?

Cả 2 đều gọi cùng 1 endpoint, Response JSON cố định:
```json
{ "status": true, "message": "...", "data": [...] }
```
Hai bên thống nhất format này từ đầu dự án và không thay đổi field name giữa chừng (tránh breaking change).
