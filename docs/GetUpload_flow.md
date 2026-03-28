# 📡 Luồng hoạt động: GET Ảnh (`GetUpload`)

## Tổng quan

```
Android App  ──►  XAMPP (Apache)  ──►  CodeIgniter 3  ──►  MySQL DB
                                                        ◄──  Trả JSON về App
```

---

## 🔢 Từng bước chi tiết

### Bước 1 — App Android gửi Request

App gọi một HTTP **GET** request tới server:

```
GET http://172.16.34.105/webapi/index.php/getupload/image?userId=123
```

| Thành phần URL | Ý nghĩa |
|---|---|
| `172.16.34.105` | IP máy tính đang chạy XAMPP (cùng WiFi) |
| `/webapi/` | Tên thư mục project trong `htdocs/` |
| `index.php` | Entry point duy nhất của CodeIgniter |
| `/getupload` | Tên class Controller: `GetUpload.php` |
| `/image` | Tên method trong controller: `public function image()` |
| `?userId=123` | Tham số truyền vào (query string) |

---

### Bước 2 — XAMPP (Apache) nhận Request

XAMPP đang lắng nghe trên cổng **80** (mặc định).

Khi nhận được request, Apache chuyển ngay vào `index.php` của CodeIgniter.

---

### Bước 3 — CodeIgniter điều phối (Routing)

`index.php` khởi động framework CI3.

CI3 đọc URL và tự động map:

```
/getupload/image  →  application/controllers/GetUpload.php  →  function image()
```

> CI3 dùng **Convention over Configuration**: tên file = tên class = tên route, không cần config thêm.

---

### Bước 4 — Controller `GetUpload.php` xử lý

```php
public function image() {
    // 1. Lấy tham số userId từ request (GET hoặc POST đều được)
    $userId = $this->input->get_post('userId');

    // 2. Kiểm tra: nếu không có userId → trả lỗi ngay
    if (empty($userId)) {
        return $this->response_json(['status' => false, 'message' => 'Missing userId', 'data' => []]);
    }

    // 3. Gọi Model để truy vấn Database
    $data = $this->UploadImages_model->getAllImages($userId);

    // 4. Nếu có kết quả → format lại → trả về
    // 5. Nếu không có → trả về mảng rỗng
}
```

---

### Bước 5 — Model truy vấn MySQL

Controller không tự nói chuyện với DB. Nó **ủy thác** cho Model:

```
UploadImages_model->getAllImages($userId)
```

Model thực thi câu SQL tương đương:

```sql
SELECT description, image_url, created_at
FROM tbl_upload_images
WHERE user_id = '123' AND active = 1;
```

> CI3 kết nối MySQL thông qua cấu hình trong `application/config/database.php`.

---

### Bước 6 — Format dữ liệu & trả về JSON

Controller nhận kết quả từ Model, format lại:

```php
$result[] = [
    'description' => $row['description'],
    'imageUrl'    => $row['image_url'],       // URL ảnh đầy đủ, ví dụ: http://172.16.34.105/webapi/uploads/abc.png
    'uploadDate'  => strtotime($row['created_at']) * 1000,  // Đổi sang milliseconds cho Android
];
```

> `* 1000` vì Android/Java dùng **milliseconds**, còn PHP `strtotime()` trả về **seconds**.

Cuối cùng trả về JSON:

```json
{
    "status": true,
    "message": "Success",
    "data": [
        {
            "description": "ddd",
            "imageUrl": "http://172.16.34.105/webapi/uploads/6201f1ac5e1391...png",
            "uploadDate": 1742989319000
        }
    ]
}
```

---

## 🗺️ Sơ đồ tổng hợp

```
┌──────────────────────────────────────────────────────────────┐
│                        Android App                           │
│  GET /webapi/index.php/getupload/image?userId=123            │
└────────────────────────┬─────────────────────────────────────┘
                         │ HTTP Request
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                   XAMPP Apache (port 80)                     │
│  Nhận request → chuyển vào index.php                        │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                  CodeIgniter 3 Framework                     │
│                                                              │
│  index.php → Router → GetUpload.php → function image()       │
│                                │                             │
│                                ▼                             │
│                    UploadImages_model                        │
│                    getAllImages($userId)                      │
│                                │                             │
└────────────────────────────────┼─────────────────────────────┘
                                 │ SQL Query
                                 ▼
┌──────────────────────────────────────────────────────────────┐
│                MySQL DB (qua phpMyAdmin)                     │
│  Database: upload_image                                      │
│  Table:    tbl_upload_images                                 │
│  WHERE user_id = '123' AND active = 1                       │
└────────────────────────┬─────────────────────────────────────┘
                         │ Rows trả về
                         ▼
┌──────────────────────────────────────────────────────────────┐
│             Controller format → JSON response                │
│  { "status": true, "data": [ { "imageUrl": "..." } ] }      │
└────────────────────────┬─────────────────────────────────────┘
                         │ HTTP Response (JSON)
                         ▼
                   Android App
              (Hiển thị ảnh lên UI)
```

---

## 📁 Vị trí các file liên quan

```
htdocs/webapi/
├── index.php                              ← Entry point CI3
└── application/
    ├── controllers/
    │   └── GetUpload.php                  ← FILE BẠN ĐANG XEM
    ├── models/
    │   └── UploadImages_model.php         ← Tầng truy vấn DB
    ├── config/
    │   └── database.php                   ← Cấu hình kết nối MySQL
    └── uploads/
        └── abc123.png                     ← File ảnh thật lưu trên server
```
