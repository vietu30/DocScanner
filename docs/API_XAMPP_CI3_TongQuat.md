# 🌐 API, XAMPP & CodeIgniter 3 – Tổng Quan (DocScanner)

> Giải thích vai trò của từng thành phần backend và cách API giao tiếp với Mobile/Web.
> Ngày: 2026-04-05

---

## 1. Bức tranh toàn cảnh

```
┌─────────────┐        REST API         ┌────────────────────────┐
│ Android App │ ◄──────────────────────► │                        │
└─────────────┘   HTTP (qua ngrok)       │   XAMPP localhost      │
                                         │   ┌────────────────┐   │
┌─────────────┐        REST API          │   │  Apache        │   │
│  Web App    │ ◄──────────────────────► │   │  PHP + CI3     │   │
└─────────────┘   HTTP (qua ngrok)       │   │  MySQL         │   │
                                         │   └────────────────┘   │
┌─────────────┐   Firebase SDK           └────────────────────────┘
│ Android/Web │ ◄──────────────────────► Firebase Server (Google)
└─────────────┘   Xác thực / UID
```

Hệ thống có **2 luồng kết nối riêng biệt**:
- **Firebase** ← chỉ lo xác thực (login), trả về UID
- **REST API (XAMPP)** ← lo toàn bộ dữ liệu: upload, lấy danh sách, xoá

---

## 2. XAMPP là gì? Vai trò gì?

### XAMPP không phải 1 thứ — nó là bộ gộp 3 thứ:

```
XAMPP
 ├── Apache  → Web Server
 ├── MySQL   → Database
 └── PHP     → Ngôn ngữ backend
```

**Hãy tưởng tượng XAMPP như một toà nhà văn phòng:**

```
🏢 Toà nhà (XAMPP)
│
├── 🚪 Lễ tân (Apache)
│      - Đứng ở cửa, nhận mọi request đến
│      - Phân loại: request nào giao cho ai xử lý
│      - Nếu request file tĩnh (ảnh, PDF) → tự phục vụ luôn
│      - Nếu request cần xử lý logic → chuyển sang PHP
│
├── 👨‍💻 Nhân viên văn phòng (PHP + CI3)
│      - Nhận yêu cầu từ lễ tân
│      - Xử lý nghiệp vụ: lưu file, query DB, trả JSON
│      - Không nói chuyện trực tiếp với khách, chỉ qua lễ tân
│
└── 🗄️ Kho hồ sơ (MySQL)
       - Lưu toàn bộ thông tin: ai upload gì, khi nào, URL ở đâu
       - Chỉ nhận lệnh từ PHP, không ai khác vào trực tiếp được
```

### Tại sao dùng XAMPP mà không phải server thật?

XAMPP chạy **localhost** — tức là server nằm ngay trên máy tính của bạn.

| | XAMPP (đang dùng) | Server thật (production) |
|---|---|---|
| Chi phí | Miễn phí | Tốn tiền thuê |
| Tốc độ setup | Vài phút | Vài giờ/ngày |
| Truy cập ngoài | Cần ngrok | Có sẵn IP public |
| Phù hợp | Đồ án, phát triển | Sản phẩm thật |

---

## 3. Ngrok — Cầu nối giữa localhost và thế giới bên ngoài

**Vấn đề:** Android app chạy trên điện thoại, backend chạy trên laptop.  
Điện thoại không thể gọi `localhost` vì đó là địa chỉ riêng của laptop.

```
❌ Không làm được:
   Android → http://localhost/webapi/...   (localhost = điện thoại, không phải laptop)

✅ Ngrok giải quyết:
   Laptop chạy:  ngrok http 80
   Ngrok cấp:    https://a1b2c3d4.ngrok.io  (URL công khai, trỏ về localhost:80)
   Android gọi: https://a1b2c3d4.ngrok.io/webapi/...  ✓
```

```
📱 Android                ngrok server          💻 Laptop (localhost)
     │                   (cloud của ngrok)            │
     │──── HTTPS ────────────►│                       │
     │                        │──── HTTP ────────────►│
     │                        │◄─── response ─────────│
     │◄─── HTTPS ─────────────│                       │
```

> **Lưu ý:** ngrok free tạo URL ngẫu nhiên mỗi lần khởi động. Phải cập nhật `BASE_URL` trong `build.gradle` mỗi khi restart ngrok.

---

## 4. CodeIgniter 3 (CI3) là gì? Tại sao dùng?

### CI3 là PHP Framework — tổ chức code theo chuẩn MVC

Không có CI3, code PHP sẽ rất lộn xộn:
```php
// ❌ PHP thuần, không framework — tất cả trong 1 file
<?php
$conn = mysqli_connect("localhost","root","","upload_image");
$userId = $_POST['userId'];
$sql = "SELECT * FROM tbl_upload_images WHERE user_id='$userId'";
// ... 100 dòng nộm vào nhau
?>
```

Với CI3, code được tổ chức rõ ràng theo **MVC (Model - View - Controller)**:

```
Model      → Làm việc với Database (SQL query)
View       → Giao diện trả về (project này không dùng vì chỉ trả JSON)
Controller → Nhận request, gọi Model, trả response
```

### Cấu trúc thư mục CI3 trong project:

```
webapi/
├── application/
│   ├── controllers/         ← CONTROLLER: nhận request, điều phối
│   │   ├── Upload.php       → Xử lý upload file
│   │   ├── GetUpload.php    → Lấy danh sách file
│   │   └── Delete.php       → Xoá file
│   │
│   ├── models/              ← MODEL: giao tiếp với MySQL
│   │   └── UploadImages_model.php
│   │
│   └── config/              ← CẤU HÌNH
│       ├── database.php     → Thông tin kết nối MySQL
│       ├── routes.php       → Mapping URL → Controller
│       └── autoload.php     → Tự động load những gì khi khởi động
│
├── uploads/                 ← Nơi lưu file PDF/ảnh thật
└── index.php                ← Cổng vào duy nhất của CI3
```

### URL mapping trong CI3 hoạt động thế nào?

CI3 dùng pattern: `index.php/controller/method`

```
URL: https://xxxx.ngrok.io/index.php/upload/image
                                     │       │
                                     │       └── Gọi method: image()
                                     └── Gọi class: Upload (Upload.php)

URL: https://xxxx.ngrok.io/index.php/getupload/image
                                     │          │
                                     │          └── method: image()
                                     └── class: GetUpload (GetUpload.php)

URL: https://xxxx.ngrok.io/index.php/delete/image
                                     │       │
                                     │       └── method: image()
                                     └── class: Delete (Delete.php)
```

### Những thứ CI3 lo giúp (không cần tự viết):

```
✅ Kết nối MySQL          → $this->db->...
✅ Query Builder          → where(), get(), insert(), update() không cần viết SQL thô
✅ File Upload            → $this->upload->do_upload()
✅ Input sanitize         → $this->input->get_post() (chống SQL injection cơ bản)
✅ Autoload               → Tự load model, library khi khởi động
✅ Route                  → Map URL → Controller tự động
```

---

## 5. API — Cách giao tiếp giữa Mobile, Web và Server

### API là gì trong project này?

API (Application Programming Interface) ở đây là **REST API** — tức là:
- Server expose một số **URL (endpoint)**
- Client (Android/Web) gọi đến URL đó kèm dữ liệu
- Server xử lý và trả về **JSON**

### 3 endpoint API trong project:

#### 📤 Upload ảnh/PDF
```
Method:  POST
URL:     /index.php/upload/image
Gửi:     multipart/form-data
  - userId      (text)   = Firebase UID
  - description (text)   = tên file
  - image       (file)   = binary data của PDF/ảnh
Nhận:    JSON
  { "status": true, "message": "Upload success",
    "data": { "imageUrl": "https://.../uploads/abc.pdf" } }
```

#### 📥 Lấy danh sách tài liệu
```
Method:  GET
URL:     /index.php/getupload/image?userId=<UID>
Gửi:     Query parameter: userId
Nhận:    JSON
  { "status": true, "data": [
      { "id": 1, "description": "HopDong", "imageUrl": "...", "uploadDate": 1743... },
      { "id": 2, "description": "Scan", "imageUrl": "...", "uploadDate": 1743... }
  ]}
```

#### 🗑️ Xoá tài liệu
```
Method:  POST
URL:     /index.php/delete/image
Gửi:     form fields
  - userId (text) = Firebase UID (để xác nhận chủ sở hữu)
  - id     (int)  = ID của record trong MySQL
Nhận:    JSON
  { "status": true, "message": "Deleted successfully" }
```

---

## 6. Mobile (Android) gọi API như thế nào?

Android dùng **Retrofit + OkHttp** — 2 thư viện lo việc gọi HTTP:

```
Retrofit  → Cấp cao: khai báo API bằng annotation (@GET, @POST, @Query...)
OkHttp    → Cấp thấp: thực sự gửi/nhận gói TCP, lo timeout, header, ...
```

```kotlin
// ApiService.kt — Khai báo interface
interface ApiService {
    @GET("index.php/getupload/image")
    suspend fun getImages(@Query("userId") userId: String): Response<ImageResponse>

    @Multipart
    @POST("index.php/upload/image")
    suspend fun uploadImage(
        @Part("userId") userId: RequestBody,
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody
    ): Response<UploadResponse>

    @FormUrlEncoded
    @POST("index.php/delete/image")
    suspend fun deleteImage(
        @Field("userId") userId: String,
        @Field("id") id: Int
    ): Response<DeleteResponse>
}
```

```kotlin
// ApiClient.kt — Khởi tạo Retrofit một lần duy nhất
val api: ApiService by lazy {
    Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)           // https://xxxx.ngrok.io/
        .client(okHttpClient)                    // OkHttp với timeout + header ngrok
        .addConverterFactory(GsonConverterFactory.create())  // JSON ↔ Kotlin object
        .build()
        .create(ApiService::class.java)
}
```

**Cách dùng trong Activity:**
```kotlin
// Gọi API từ coroutine (bất đồng bộ)
lifecycleScope.launch {
    val response = ApiClient.api.getImages(uid)      // Gọi API
    if (response.isSuccessful) {
        val list = response.body()!!.data            // Parse JSON tự động
        // Hiển thị lên UI
    }
}
```

---

## 7. Web gọi API như thế nào?

Web dùng **Fetch API** của JavaScript (tương tự Retrofit nhưng cho JS):

```javascript
// Lấy danh sách tài liệu
const uid = firebase.auth().currentUser.uid;
const response = await fetch(
    `https://xxxx.ngrok.io/index.php/getupload/image?userId=${uid}`,
    { headers: { "ngrok-skip-browser-warning": "true" } }
);
const data = await response.json();
// data.data = mảng tài liệu

// Upload file
const formData = new FormData();
formData.append("userId", uid);
formData.append("description", "TenFile");
formData.append("image", fileObject);   // File object từ <input type="file">

await fetch("https://xxxx.ngrok.io/index.php/upload/image", {
    method: "POST",
    body: formData
});
```

---

## 8. Tại sao Mobile và Web cùng dùng được 1 API?

Vì API trả về **JSON** — định dạng trung lập, ai cũng đọc được:

```
Server PHP trả về:
{
    "status": true,
    "data": [{ "id": 1, "imageUrl": "..." }]
}

Android nhận:
→ Gson parse JSON → Kotlin data class ImageResponse

Web nhận:
→ response.json() → JavaScript object
→ data.data[0].imageUrl
```

> **JSON là ngôn ngữ chung** giữa mọi client (Android, iOS, Web, Desktop...) và server.  
> Server không cần biết client là gì — cứ trả JSON, ai nhận thì tự xử lý.

---

## 9. Header CORS — Tại sao cần?

Mỗi controller PHP đều có:
```php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');
```

**CORS (Cross-Origin Resource Sharing)** là cơ chế bảo mật của browser:
- Browser **chặn** mọi request gọi sang domain khác theo mặc định
- Thêm header `Access-Control-Allow-Origin: *` → cho phép mọi domain gọi vào
- Android không bị CORS (chỉ browser mới có), nhưng thêm vào để Web gọi được

---

## 10. Tổng kết: Mỗi thứ làm 1 việc

```
XAMPP
 ├── Apache   → "Bưu tá": nhận và chuyển request
 ├── MySQL    → "Kho hồ sơ": lưu metadata
 └── PHP      → "Nhân viên": xử lý nghiệp vụ

CodeIgniter 3
 ├── Controller → Nhận request từ Apache, gọi Model, trả JSON
 ├── Model      → Viết SQL, query MySQL
 └── Config     → Khai báo DB, routes, autoload

ngrok
 └── Tunnel: localhost → URL công khai (cho Mobile + Web gọi được)

REST API (3 endpoints)
 ├── POST /upload/image     → Upload file
 ├── GET  /getupload/image  → Lấy danh sách
 └── POST /delete/image     → Xoá (soft delete)

Android (Retrofit + OkHttp)
 └── Gọi API → parse JSON → hiển thị UI

Web (Fetch API)
 └── Gọi API → parse JSON → render HTML
```

---

*File này được tạo từ cuộc trò chuyện giải thích API, XAMPP, CI3 trong project DocScanner.*
