# 🏗️ Tổng quan Hệ thống Backend: XAMPP + CodeIgniter 3 + MySQL

---

## 1. XAMPP là gì?

> **XAMPP = X + Apache + MySQL + PHP + Perl**

XAMPP là một **"gói phần mềm all-in-one"** giúp bạn biến máy tính cá nhân thành một **Web Server** để phát triển và test ứng dụng web mà không cần thuê server thật.

```
┌─────────────────────────────────────────────────┐
│                    XAMPP                        │
│                                                 │
│  ┌──────────┐   ┌──────────┐   ┌────────────┐  │
│  │  Apache  │   │  MySQL   │   │    PHP     │  │
│  │ (máy chủ │   │ (cơ sở  │   │  (ngôn    │  │
│  │  web)    │   │  dữ liệu)│   │   ngữ)    │  │
│  └──────────┘   └──────────┘   └────────────┘  │
└─────────────────────────────────────────────────┘
```

- **X** = Cross-platform (chạy được Windows, Mac, Linux)
- **A** = Apache (Web Server, xem mục 2)
- **M** = MySQL (Database, xem mục 3)
- **P** = PHP (Ngôn ngữ lập trình server-side)
- **P** = Perl (ít dùng, bỏ qua)

**Thư mục quan trọng của XAMPP:**
```
htdocs/          ← Đây là "gốc" của web server. Mọi project đặt ở đây.
    webapi/      ← Project của bạn
    ...
```

---

## 2. Apache là gì?

Apache là **Web Server** — một chương trình luôn chạy ngầm, **lắng nghe** các yêu cầu (request) từ Internet (hoặc mạng nội bộ).

### Hãy tưởng tượng:
```
Khách hàng (Browser/App)         Apache (Nhân viên lễ tân)
───────────────────────────────────────────────────────
"Tôi muốn xem file abc.png"  →   Nhận yêu cầu
                              →   Tìm file trong htdocs/
                              →   Trả file về
"Tôi muốn gọi API /upload"   →   Nhận yêu cầu
                              →   Chạy code PHP
                              →   Trả kết quả JSON về
```

### Port là gì?
Port là "số cửa" của server. Apache mặc định dùng **cổng 80**.

```
http://172.16.34.105/webapi/...
        ↑               ↑
     IP máy tính     Thư mục trong htdocs/
     (chạy XAMPP)
```

> Khi bạn không ghi port → mặc định là 80.
> `http://172.16.34.105` = `http://172.16.34.105:80`

---

## 3. MySQL là gì và kết nối ra sao?

MySQL là **hệ quản trị cơ sở dữ liệu** — nơi lưu trữ dữ liệu có cấu trúc dạng bảng (giống Excel nhưng mạnh hơn nhiều).

### Cấu trúc phân cấp:
```
MySQL Server
└── Database: upload_image          ← Bạn tạo cái này bằng phpMyAdmin
    └── Table: tbl_upload_images    ← Bảng chứa thông tin ảnh
        ├── id
        ├── user_id
        ├── image_url               ← Chỉ lưu ĐƯỜNG DẪN, không lưu file ảnh!
        ├── description
        └── created_at
```

### Kết nối qua `database.php`:
CodeIgniter kết nối MySQL thông qua file cấu hình:

```php
// application/config/database.php
$db['default'] = [
    'hostname' => 'localhost',   // MySQL chạy trên cùng máy với Apache
    'username' => 'root',        // User mặc định của XAMPP
    'password' => '',            // Mật khẩu mặc định: trống
    'database' => 'upload_image' // Tên database bạn đã tạo
];
```

> XAMPP chạy cả Apache và MySQL trên **cùng một máy**, nên chúng nói chuyện với nhau qua `localhost` nội bộ, rất nhanh.

### phpMyAdmin là gì?
Là giao diện web để quản lý MySQL bằng "click chuột" thay vì gõ lệnh SQL. Truy cập tại: `http://localhost/phpmyadmin`

---

## 4. CodeIgniter 3 (CI3) là gì?

CI3 là một **PHP Framework** — bộ khung đã được xây dựng sẵn, bạn chỉ cần điền code vào đúng chỗ thay vì viết mọi thứ từ đầu.

### CI3 theo mô hình MVC:

```
MVC = Model + View + Controller
```

```
┌────────────────────────────────────────────────────┐
│                  Request từ App/Browser            │
└───────────────────────┬────────────────────────────┘
                        ▼
              ┌─────────────────┐
              │   Controller    │  ← Điều phối, nhận request
              │  GetUpload.php  │    quyết định làm gì
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │      Model      │  ← Nói chuyện với Database
              │ UploadImages_   │    (SELECT, INSERT, UPDATE)
              │    model.php    │
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │     MySQL DB    │  ← Lưu và truy xuất dữ liệu
              │  tbl_upload_    │
              │    images       │
              └─────────────────┘
```

> **View** (HTML) ít được dùng trong API thuần, vì API chỉ trả JSON, không trả giao diện.

### Cấu trúc thư mục CI3:
```
webapi/
├── index.php                    ← Cổng vào duy nhất của CI3
├── system/                      ← Framework core, KHÔNG SỬA
└── application/                 ← Nơi BẠN code
    ├── config/
    │   ├── database.php         ← Cấu hình kết nối MySQL
    │   └── config.php           ← URL gốc của app
    ├── controllers/             ← Nhận request, xử lý logic
    │   ├── GetUpload.php
    │   └── Upload.php
    ├── models/                  ← Truy vấn database
    │   └── UploadImages_model.php
    └── views/                   ← HTML (ít dùng cho API)
```

---

## 5. Ảnh được lưu như thế nào?

Đây là điểm hay gây nhầm lẫn: **Database KHÔNG lưu file ảnh** — nó chỉ lưu **đường dẫn (URL)** tới file.

### Luồng Upload ảnh:

```
App Android gửi file ảnh
         │
         ▼
   Upload.php (Controller)
         │
         ├─── Lưu FILE thật vào:  htdocs/webapi/uploads/abc123.png
         │                                    (ổ cứng máy tính)
         │
         └─── Lưu ĐƯỜNG DẪN vào DB:  image_url = "http://172.16.34.105/webapi/uploads/abc123.png"
```

### Tại sao làm vậy?
Vì database lưu text rất hiệu quả nhưng không phù hợp để lưu file nhị phân lớn (ảnh, video). File được lưu trên ổ cứng, database chỉ ghi nhớ "file đó ở đâu".

### Khi App muốn xem ảnh:
```
App lấy URL từ API:  "http://172.16.34.105/webapi/uploads/abc123.png"
         │
         ▼
App gọi thẳng URL đó tới Apache
         │
         ▼
Apache tìm file tại:  htdocs/webapi/uploads/abc123.png
         │
         ▼
Apache trả file ảnh về (không cần qua PHP/CI3 nữa)
```

---

## 6. Bức tranh toàn cảnh

```
┌──────────────────────────────────────────────────────────┐
│                   Máy tính của bạn                       │
│                                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │                   XAMPP                            │  │
│  │                                                    │  │
│  │   ┌──────────────┐      ┌──────────────────────┐  │  │
│  │   │    Apache    │      │         MySQL         │  │  │
│  │   │  (port 80)   │      │   DB: upload_image   │  │  │
│  │   │              │      │   Tbl: tbl_upload_   │  │  │
│  │   │  htdocs/     │      │        images        │  │  │
│  │   │   webapi/    │      └──────────┬───────────┘  │  │
│  │   │    ├ index.php│                │              │  │
│  │   │    ├ system/ ◄────── CI3 ──────┘              │  │
│  │   │    ├ application/   Framework                 │  │
│  │   │    └ uploads/       (PHP Code)                │  │
│  │   │      └ abc.png                                │  │
│  │   └──────────────┘                                │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
          ▲                    ▲
          │ WiFi nội bộ        │ WiFi nội bộ
          │                    │
   ┌──────┴──────┐      ┌──────┴──────┐
   │ Android App │      │   Browser   │
   │  DocScanner │      │ (chrome...) │
   └─────────────┘      └─────────────┘
```

---

## 7. Tóm tắt 1 dòng cho mỗi thứ

| Thứ | Vai trò |
|-----|---------|
| **XAMPP** | Phần mềm biến máy tính thành server |
| **Apache** | Nhân viên lễ tân — nhận và trả lời mọi request HTTP |
| **MySQL** | Kho lưu dữ liệu dạng bảng |
| **phpMyAdmin** | Giao diện web để quản lý MySQL |
| **PHP** | Ngôn ngữ lập trình chạy trên server |
| **CodeIgniter 3** | Bộ khung PHP giúp code có cấu trúc (MVC) |
| **Controller** | Nhận request, điều phối xử lý |
| **Model** | Truy vấn database |
| **`uploads/`** | Thư mục lưu file ảnh thật trên ổ cứng |
| **`image_url` trong DB** | Chỉ là đường dẫn tới file, không phải file ảnh |
