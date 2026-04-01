# 🗺️ Toàn cảnh hệ thống DocScanner – Đọc xong là hiểu hết

---

## BỨC TRANH TỔNG THỂ

```
┌─────────────────────────────────────────────────────────┐
│                     NGƯỜI DÙNG                          │
│          (có thể dùng mobile HOẶC web)                  │
└──────────────┬──────────────────────┬───────────────────┘
               │                      │
     ┌─────────▼──────┐    ┌──────────▼─────────┐
     │  Android App   │    │   Web (login.html   │
     │ (Kotlin)       │    │    + index.html)    │
     └─────────┬──────┘    └──────────┬──────────┘
               │                      │
               │   HTTP / JSON        │
               └──────────┬───────────┘
                          │
              ┌───────────▼────────────┐
              │    NGROK (tunnel)      │
              │  public URL → localhost│
              └───────────┬────────────┘
                          │
              ┌───────────▼────────────┐
              │   CI3 Backend (XAMPP)  │◄── Đây là "bộ não" xử lý
              │   /webapi/             │
              └────────┬──────┬────────┘
                       │      │
            ┌──────────▼┐  ┌──▼──────────────────┐
            │   MySQL   │  │  /uploads/ (ổ cứng)  │
            │ (metadata)│  │  file PDF/ảnh thật   │
            └───────────┘  └─────────────────────-┘

              ┌───────────────────────┐
              │  Firebase (Google)    │◄── Chỉ lo việc xác thực user
              │  Authentication       │
              └───────────────────────┘
```

---

## PHẦN 1 – Firebase làm gì trong hệ thống này?

**Firebase KHÔNG lưu file. Firebase CHỈ làm 1 việc: xác minh danh tính user.**

### Cách hoạt động:

```
User nhập email + password
        │
        ▼
Firebase SDK (thư viện của Google được nhúng vào app/web)
        │ gửi request lên server Google
        ▼
Firebase Server kiểm tra email & password
        │ nếu đúng
        ▼
Trả về UID  (ví dụ: "XyZ9abc123kQ...")
```

**UID là gì?**
- Chuỗi ký tự duy nhất, đại diện cho 1 tài khoản
- Mobile và Web cùng dùng chung UID vì cùng project Firebase
- UID này sẽ được gửi kèm trong mọi request đến CI3

**Cả Mobile lẫn Web đều kết nối Firebase qua:**
- Android → file `google-services.json` + Firebase SDK (`build.gradle`)
- Web → đoạn `firebaseConfig` trong `login.html` + Firebase JS SDK

---

## PHẦN 2 – CI3 làm gì trong hệ thống này?

**CI3 là backend API** — nhận request từ app/web, xử lý, trả về JSON.

CI3 dùng mô hình **MVC nhưng không dùng View** vì đây là API thuần:
```
Controller ← nhận request, điều phối
Model      ← đọc/ghi MySQL
View       ← KHÔNG DÙNG (thay bằng JSON response)
```

### Cấu trúc thư mục CI3 của dự án:

```
/htdocs/webapi/
├── application/
│   ├── controllers/
│   │   ├── Upload.php      ← API upload file
│   │   ├── GetUpload.php   ← API lấy danh sách
│   │   └── Delete.php      ← API xoá file
│   ├── models/
│   │   └── UploadImages_model.php   ← tương tác MySQL
│   └── views/              ← BỎ TRỐNG (không dùng)
├── uploads/                ← file PDF/ảnh lưu ở đây
└── web/
    ├── login.html          ← Web frontend (không phải CI3 view)
    └── index.html
```

> `login.html` và `index.html` đặt trong `/web/` không phải trong `/views/`
> vì chúng là **frontend độc lập**, tự gọi API bằng JavaScript `fetch()`.

---

## PHẦN 3 – 3 API và cách chúng hoạt động

### 🔵 API Upload

**Khi nào gọi?** Sau khi chụp ảnh và tạo PDF xong.

```
Mobile gửi:
  POST /index.php/upload/image
  Body (multipart/form-data):
    userId      = "XyZ9abc123..."   ← lấy từ Firebase
    description = "bangbeo"          ← tên file user đặt
    image       = [binary file.pdf]  ← file thực

CI3 xử lý:
  1. Nhận file → kiểm tra đuôi (chỉ cho pdf/jpg/png)
  2. Đổi tên: md5("file.pdf") → "a1b2c3.pdf"
  3. Lưu vào /uploads/a1b2c3.pdf
  4. INSERT vào MySQL:
     user_id="XyZ9...", description="bangbeo",
     image_url="http://localhost/webapi/uploads/a1b2c3.pdf"

Trả về:
  { "status": true, "message": "Upload Success" }
```

---

### 🟢 API GetUpload

**Khi nào gọi?** Mỗi lần vào trang chủ (mobile/web) để hiện danh sách file.

```
Mobile/Web gửi:
  GET /index.php/getupload/image?userId=XyZ9abc123...

CI3 xử lý:
  SELECT id, description, image_url, created_at
  FROM tbl_upload_images
  WHERE user_id = "XyZ9abc123..." AND active = 1
  ORDER BY id DESC

Trả về:
  {
    "status": true,
    "data": [
      { "id": 5, "description": "bangbeo",
        "imageUrl": "http://localhost/webapi/uploads/a1b2c3.pdf",
        "uploadDate": 1743213215000 },
      ...
    ]
  }

Mobile nhận:
  → Replace "http://localhost" → ngrok URL (vì DB lưu localhost)
  → Hiện list file

Web nhận:
  → Render card HTML cho từng file
```

---

### 🔴 API Delete

**Khi nào gọi?** User long-press file trên mobile hoặc nhấn 🗑️ trên web.

```
Mobile/Web gửi:
  POST /index.php/delete/image
  Body: userId=XyZ9abc123...&id=5

CI3 xử lý:
  UPDATE tbl_upload_images
  SET active = 0
  WHERE id = 5 AND user_id = "XyZ9abc123..."
  ← Nếu user_id không khớp → không xoá được file của người khác

Trả về:
  { "status": true, "message": "Deleted successfully" }

Mobile: xoá item khỏi RecyclerView
Web: xoá card khỏi DOM
```

> **Soft delete:** File vật lý vẫn còn trong `/uploads/`, chỉ ẩn trong DB (`active=0`).

---

## PHẦN 4 – Ngrok là gì và tại sao cần?

**Vấn đề:** XAMPP chạy trên máy tính ở `localhost`. Điện thoại và internet không vào được `localhost`.

**Giải pháp:** Ngrok tạo một đường hầm (tunnel):

```
Internet → https://zelma-xxx.ngrok-free.dev → localhost:80 (XAMPP)
```

- Điện thoại gọi `https://zelma-xxx.ngrok-free.dev/webapi/...`
- Ngrok chuyển tiếp về `http://localhost/webapi/...`
- XAMPP xử lý và trả kết quả ngược lại

**Lưu ý:** Ngrok free thêm trang cảnh báo khi mở bằng browser. Fix bằng header:
```
ngrok-skip-browser-warning: true
```
Header này được thêm vào mọi request từ cả Android (ApiClient.kt) lẫn Web (index.html).

---

## PHẦN 5 – Luồng hoàn chỉnh từ đầu đến cuối

### Kịch bản: User mở app, chụp ảnh, xem lại, xoá

```
BƯỚC 1 – Đăng nhập
  User nhập email/password
  → Firebase xác thực → trả UID
  → App lưu UID vào session

BƯỚC 2 – Vào trang chủ
  App gọi: GET /getupload/image?userId=<UID>
  → CI3 query MySQL theo UID
  → Trả JSON danh sách file
  → App hiện list (RecyclerView / cards HTML)

BƯỚC 3 – Chụp ảnh
  Camera → xử lý ảnh → tạo PDF
  App gọi: POST /upload/image (userId, file, description)
  → CI3 lưu file vào /uploads/
  → CI3 INSERT record vào MySQL
  → App refresh danh sách

BƯỚC 4 – Xem file
  User tap vào file
  → App download file về cache (qua OkHttp kèm ngrok header)
  → Mở file PDF bằng FileProvider
  (Web: nhấn "Mở PDF" → browser tự mở)

BƯỚC 5 – Xoá file
  User long-press → confirm dialog
  App gọi: POST /delete/image (userId, id)
  → CI3 SET active=0
  → App xoá item khỏi list
```

---

## TÓM TẮT VAI TRÒ TỪNG THÀNH PHẦN

| Thành phần | Vai trò | Nằm ở đâu |
|---|---|---|
| **Firebase Auth** | Xác minh danh tính, cấp UID | Google Cloud |
| **Android App** | Giao diện mobile, gọi API | Điện thoại |
| **Web (HTML/JS)** | Giao diện trình duyệt, gọi API | `/webapi/web/` |
| **CI3 (PHP)** | Xử lý logic API, đọc/ghi data | XAMPP localhost |
| **MySQL** | Lưu metadata (tên, URL, userId) | XAMPP localhost |
| **`/uploads/`** | Lưu file PDF/ảnh vật lý | Ổ cứng máy chủ |
| **Ngrok** | Tạo public URL cho localhost | Terminal |
