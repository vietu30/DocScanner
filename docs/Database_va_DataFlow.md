# 🗄️ Database & Data Flow – DocScanner

> Giải thích: Database là gì, setup thế nào, dữ liệu đi đâu sau khi scan, từng bước hoạt động.
> Ngày: 2026-04-05

---

## 1. Stack công nghệ phía backend gồm những gì?

```
┌─────────────────────────────────────────────────────┐
│                  BACKEND STACK                      │
│                                                     │
│  XAMPP       → Bộ công cụ gộp sẵn (all-in-one)    │
│    ├── Apache  → Web Server (nhận HTTP request)     │
│    ├── MySQL   → Database (lưu dữ liệu)            │
│    └── PHP     → Ngôn ngữ xử lý logic server       │
│                                                     │
│  CodeIgniter 3 → PHP Framework (tổ chức code)      │
│  ngrok         → Tunnel (expose localhost ra ngoài) │
└─────────────────────────────────────────────────────┘
```

| Thành phần | Ngôn ngữ / Công nghệ | Vai trò |
|---|---|---|
| **XAMPP** | - | Bộ cài đặt gom Apache + MySQL + PHP vào 1 chỗ |
| **Apache** | - | Nhận HTTP request từ Android/ngrok, chuyển cho PHP |
| **PHP** | PHP 7/8 | Xử lý logic: nhận file, lưu file, query database |
| **CodeIgniter 3** | PHP Framework | Tổ chức code theo MVC, có sẵn DB helper |
| **MySQL** | SQL | Lưu metadata: tên file, userId, đường dẫn, ngày tạo |
| **ngrok** | - | Tạo URL công khai cho localhost (Android gọi được) |

---

## 2. Database được setup như thế nào?

### 2.1 — Thông tin kết nối (`database.php`)

```php
// File: /webapi/application/config/database.php

$db['default'] = array(
    'hostname' => 'localhost',     // MySQL chạy trên cùng máy
    'username' => 'root',          // User mặc định của XAMPP
    'password' => '',              // XAMPP mặc định không có password
    'database' => 'upload_image',  // Tên database
    'dbdriver' => 'mysqli',        // Driver MySQL
    'char_set' => 'utf8mb4',       // Hỗ trợ tiếng Việt + emoji
);
```

### 2.2 — Cấu trúc bảng trong MySQL

Chỉ có **1 bảng duy nhất**: `tbl_upload_images`

```sql
CREATE TABLE `tbl_upload_images` (
    `id`          INT AUTO_INCREMENT PRIMARY KEY,
    `user_id`     VARCHAR(128) NOT NULL,   -- Firebase UID
    `description` VARCHAR(255),            -- Tên file / mô tả
    `image_url`   TEXT NOT NULL,           -- Đường dẫn đầy đủ đến file
    `active`      TINYINT(1) DEFAULT 1,    -- 1 = tồn tại, 0 = đã xoá (soft delete)
    `created_at`  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Giải thích từng cột:**

| Cột | Kiểu dữ liệu | Ý nghĩa | Ví dụ |
|---|---|---|---|
| `id` | INT | Khoá chính, tự tăng | 1, 2, 3... |
| `user_id` | VARCHAR(128) | Firebase UID của người upload | `"xK9mPq2Rf8hT..."` |
| `description` | VARCHAR(255) | Tên file người dùng đặt | `"Hợp đồng tháng 4"` |
| `image_url` | TEXT | URL đầy đủ đến file trên server | `"https://xxxx.ngrok.io/uploads/abc123.pdf"` |
| `active` | TINYINT | 1 = còn, 0 = đã xoá | `1` |
| `created_at` | TIMESTAMP | Thời gian upload | `2026-04-05 15:00:00` |

### 2.3 — File thực tế lưu ở đâu?

**MySQL chỉ lưu thông tin (metadata)** — file PDF/ảnh thực tế lưu ở thư mục:

```
/Applications/XAMPP/xamppfiles/htdocs/webapi/uploads/
    ├── a1b2c3d4e5f6.pdf      ← tên được mã hoá ngẫu nhiên (encrypt_name = TRUE)
    ├── f7e8d9c0b1a2.jpg
    └── ...
```

> **Tại sao đổi tên file?**  
> `'encrypt_name' => TRUE` trong Upload.php → PHP tự đổi thành chuỗi ngẫu nhiên.  
> Tránh trùng tên, tránh path traversal attack, không đoán được tên file.

---

## 3. Hành trình dữ liệu: Từ lúc scan đến khi lưu vào DB

### Tổng quan (5 giai đoạn):

```
[Giai đoạn 1] Scan ảnh bằng camera
      ↓
[Giai đoạn 2] Crop + chỉnh ảnh
      ↓
[Giai đoạn 3] Convert sang PDF (hoặc giữ JPG)
      ↓
[Giai đoạn 4] Upload file lên server qua HTTP
      ↓
[Giai đoạn 5] Server lưu file + ghi vào MySQL
```

---

### Giai đoạn 1: Scan ảnh (`ScannerActivity.kt`)

```kotlin
// Dùng CameraX để chụp ảnh
imageCapture.takePicture(
    outputOptions,         // Lưu ra file tạm: IMG_<timestamp>.jpg
    ContextCompat.getMainExecutor(this),
    object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(...) {
            openCrop(photoFile.absolutePath)   // → Chuyển sang bước Crop
        }
    }
)
```

**Kết quả:** File JPG tạm lưu ở bộ nhớ ngoài máy Android.

---

### Giai đoạn 2: Crop ảnh (UCrop library)

```kotlin
// Dùng thư viện UCrop để crop theo tỉ lệ A4
UCrop.of(sourceUri, destinationUri)
    .withAspectRatio(1f, 1.414f)      // Tỉ lệ A4 (1:√2)
    .withMaxResultSize(1080, 1920)    // Giới hạn kích thước
    .getIntent(this)

// Sau khi crop xong:
imageList.add(croppedPath)   // Thêm vào danh sách ảnh đã crop
```

**Kết quả:** File JPG đã crop, tỉ lệ A4, thêm vào `imageList`.  
Người dùng có thể chụp nhiều trang → `imageList` có nhiều phần tử.

---

### Giai đoạn 3: Convert sang PDF (`PreviewActivity.kt` + `PdfUtils.kt`)

```kotlin
// Khi user bấm "Lưu PDF"
private fun savePdf(name: String) {
    val pdfFile = File(getExternalFilesDir(null), "$name.pdf")

    // createPdf: gom tất cả ảnh trong imageList → 1 file PDF
    createPdf(this, imageList, pdfFile.absolutePath)
    // Mỗi ảnh = 1 trang PDF, kích thước A4

    // Sau khi tạo PDF xong → upload lên server
    uploadImageToServer(pdfFile, name)
}
```

**Hoặc nếu chọn "Lưu ảnh":**
```kotlin
// Không convert PDF, lưu từng ảnh JPG rồi upload từng cái
imageList.forEachIndexed { index, path ->
    val destFile = File(dir, "${baseName}_${index+1}.jpg")
    // copy ảnh → upload
    uploadImageToServer(destFile, baseName)
}
```

**Kết quả:** File `.pdf` hoặc `.jpg` lưu trong bộ nhớ máy Android, sẵn sàng upload.

---

### Giai đoạn 4: Upload file lên server (`PreviewActivity.kt`)

```kotlin
private fun uploadImageToServer(file: File, description: String) {
    val uid = auth.currentUser?.uid ?: return  // Phải đăng nhập mới upload được

    lifecycleScope.launch {
        // Chuẩn bị dữ liệu dạng multipart/form-data
        val userIdBody = uid.toRequestBody("text/plain".toMediaTypeOrNull())
        val descBody   = description.toRequestBody("text/plain".toMediaTypeOrNull())
        val fileBody   = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val filePart   = MultipartBody.Part.createFormData("image", file.name, fileBody)

        // Gọi API upload
        val response = ApiClient.api.uploadImage(userIdBody, filePart, descBody)
        //                              ↑ POST multipart đến: /index.php/upload/image
    }
}
```

**HTTP request thực tế gửi đi:**
```
POST https://xxxx.ngrok.io/index.php/upload/image
Content-Type: multipart/form-data

[form field] userId      = "xK9mPq2Rf8hT..."   ← Firebase UID
[form field] description = "HopDong_thang4"
[file field] image       = <binary data của file PDF/JPG>
```

---

### Giai đoạn 5: Server xử lý + lưu DB (`Upload.php`)

```php
public function image() {
    // 1. Nhận thông tin từ request
    $userId      = $this->input->get_post('userId');       // Firebase UID
    $description = $this->input->get_post('description');  // Tên file

    // 2. Lưu file binary lên ổ cứng server
    $config['upload_path']   = './uploads/';       // Thư mục lưu file
    $config['allowed_types'] = 'jpg|jpeg|png|pdf'; // Chỉ cho phép các loại này
    $config['max_size']      = 8 * 1024 * 1024;   // Giới hạn 8MB
    $config['encrypt_name']  = TRUE;               // Đổi tên file thành chuỗi ngẫu nhiên

    $this->load->library('upload', $config);
    $this->upload->do_upload('image');
    // → File ABC.pdf được lưu thành: uploads/a1b2c3d4e5f6.pdf

    // 3. Tạo URL đầy đủ đến file
    $data     = $this->upload->data();              // Lấy thông tin file vừa upload
    $filePath = base_url('uploads/' . $data['file_name']);
    // → "https://xxxx.ngrok.io/uploads/a1b2c3d4e5f6.pdf"

    // 4. Ghi metadata vào MySQL
    $this->UploadImages_model->insertUpload($userId, $description, $filePath);
    // → INSERT INTO tbl_upload_images (user_id, description, image_url, active)
    //   VALUES ('xK9mPq2R...', 'HopDong_thang4', 'https://...pdf', 1)
}
```

**Kết quả trong MySQL:**
```
id │ user_id          │ description      │ image_url                    │ active │ created_at
───┼──────────────────┼──────────────────┼──────────────────────────────┼────────┼───────────
1  │ xK9mPq2Rf8hT...  │ HopDong_thang4   │ https://.../uploads/abc.pdf  │ 1      │ 2026-04-05
```

---

## 4. Cách xoá dữ liệu — Soft Delete (`Delete.php`)

Khi người dùng long-press → xoá:

```php
// KHÔNG xoá thật khỏi DB, chỉ đặt active = 0
$this->db->where('id', $imageId);
$this->db->where('user_id', $userId);      // Chỉ xoá nếu đúng chủ sở hữu
$this->db->update('tbl_upload_images', ['active' => 0]);
```

```
Trước khi xoá:   active = 1 → Hiện trong danh sách
Sau khi xoá:     active = 0 → Ẩn khỏi danh sách
File vật lý trên server: VẪN CÒN (chưa bị xoá thật)
```

> **Tại sao dùng Soft Delete?**  
> - Dễ khôi phục nếu xoá nhầm  
> - Không lo lỗi khi xoá file  
> - Đủ đơn giản cho đồ án

---

## 5. Lấy danh sách tài liệu — GetUpload (Server + Mobile)

Sau khi upload xong, lần tới user mở app → `MainActivity` gọi API để load danh sách.

### 5.1 — Server side: `Getupload.php`

```php
// File: /webapi/application/controllers/Getupload.php

public function image() {
    // 1. Nhận userId từ query string
    $userId = $this->input->get_post('userId');

    if (empty($userId)) {
        return $this->response_json(['status' => false, 'message' => 'Missing userId', 'data' => []]);
    }

    // 2. Gọi Model → query DB
    $data = $this->UploadImages_model->getAllImages($userId);
    //  → SELECT id, description, image_url, created_at
    //    FROM tbl_upload_images
    //    WHERE user_id = $userId AND active = 1
    //    ORDER BY id DESC

    // 3. Format kết quả → trả JSON
    $result = [];
    foreach ($data as $row) {
        $result[] = [
            'id'          => $row['id'],
            'description' => $row['description'],
            'imageUrl'    => $row['image_url'],
            'uploadDate'  => strtotime($row['created_at']) * 1000  // seconds → milliseconds
        ];
    }

    $this->response_json(['status' => true, 'message' => 'Success', 'data' => $result]);
}
```

### 5.2 — Mobile side: `MainActivity.kt` (dòng 66–116)

```kotlin
// ① onResume() kiểm tra login → gọi loadFromServer
override fun onResume() {
    super.onResume()
    val uid = auth.currentUser?.uid       // dòng 56: lấy Firebase UID
    if (uid != null) {
        loadFromServer(uid)                // dòng 58: đã login → load từ server
    }
}

// ② Gọi API getImages
private fun loadFromServer(userId: String) {
    lifecycleScope.launch {
        try {
            // ★ DÒNG GỌI API — dòng 69 ★
            val response = ApiClient.api.getImages(userId)
            //  → GET /index.php/getupload/image?userId=xK9mPq2R...

            if (response.isSuccessful && response.body()?.status == true) {
                // ③ Parse response → tạo danh sách hiển thị
                val items = response.body()!!.data.map { img ->
                    val fileName = img.imageUrl.substringAfterLast("/")
                    val fixedUrl = "${BuildConfig.BASE_URL}uploads/$fileName"

                    ScanItem(
                        fileName  = img.description ?: "Ảnh scan",
                        fileInfo  = SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date(img.uploadDate)),
                        imageUrl  = fixedUrl,       // URL để download + mở file
                        serverId  = img.id           // ID trên server (dùng cho delete sau này)
                    )
                }
                // ④ Gắn adapter → hiển thị lên RecyclerView
                recyclerView.adapter = ScanAdapter(items.toMutableList()) { ... }
            }
        } catch (e: Exception) {
            // Server không kết nối được → hiện danh sách rỗng
        }
    }
}
```

**Request → Response:**

```
GET https://xxx.ngrok.dev/webapi/index.php/getupload/image?userId=xK9mPq2R...
                              ↓
Server query: SELECT * FROM tbl_upload_images WHERE user_id='xK9mPq2R...' AND active=1
                              ↓
JSON: { "status": true, "data": [
    { "id": 1, "description": "HopDong", "imageUrl": "uploads/abc.pdf", "uploadDate": 1712345678000 },
    { "id": 2, "description": "BienLai", "imageUrl": "uploads/def.jpg", "uploadDate": 1712345679000 }
]}
                              ↓
App hiển thị danh sách trên RecyclerView
```

---

## 6. Xoá tài liệu — Delete (Server + Mobile)

### 6.1 — Server side: `Delete.php`

```php
// File: /webapi/application/controllers/Delete.php

public function image() {
    $userId  = $this->input->get_post('userId');
    $imageId = $this->input->get_post('id');

    // Soft delete: chỉ set active = 0, KHÔNG xoá thật
    $this->db->where('id', $imageId);
    $this->db->where('user_id', $userId);     // Chỉ xoá nếu đúng chủ sở hữu
    $this->db->update('tbl_upload_images', ['active' => 0]);

    $this->response_json(['status' => true, 'message' => 'Xoá thành công']);
}
```

### 6.2 — Mobile side: `MainActivity.kt` (dòng 88–166)

```kotlin
// ① Trong loadFromServer(), sau khi tạo adapter → gắn long-press handler
adapter.setOnLongClickListener { item, position ->
    if (item.serverId != null) {
        AlertDialog.Builder(this)
            .setTitle("Xoá tài liệu")
            .setMessage("Bạn có chắc muốn xoá \"${item.fileName}\"?")
            .setPositiveButton("Xoá") { _, _ ->
                // dòng 96: gọi hàm xoá, truyền serverId lấy từ getImages()
                deleteServerItem(item.serverId, mutableItems, adapter, position)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }
}

// ② Gọi API deleteImage
private fun deleteServerItem(id: Int, items: MutableList<ScanItem>,
                              adapter: ScanAdapter, position: Int) {
    val userId = auth.currentUser?.uid ?: return   // dòng 152

    lifecycleScope.launch {
        try {
            // ★ DÒNG GỌI API — dòng 155 ★
            val response = ApiClient.api.deleteImage(userId, id)
            //  → POST /index.php/delete/image
            //    body: userId=xK9mPq2R...&id=3

            if (response.isSuccessful && response.body()?.status == true) {
                adapter.removeAt(position)    // Xoá item khỏi RecyclerView
                Toast.makeText(this, "Đã xoá thành công", ...).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi kết nối", ...).show()
        }
    }
}
```

**Request → Response:**

```
POST https://xxx.ngrok.dev/webapi/index.php/delete/image
body: userId=xK9mPq2R...&id=3
                              ↓
Server: UPDATE tbl_upload_images SET active=0 WHERE id=3 AND user_id='xK9mPq2R...'
                              ↓
JSON: { "status": true, "message": "Xoá thành công" }
                              ↓
App xoá item khỏi RecyclerView (UI cập nhật ngay)
```

> **Lưu ý:** `serverId` mà delete gửi lên chính là `img.id` trả về từ `getImages()`. Đây là cầu nối giữa 2 API.

---

## 7. Model layer — Lớp giao tiếp với MySQL (`UploadImages_model.php`)

```php
class UploadImages_model extends CI_Model {

    private $table = 'tbl_upload_images';

    // LẤY danh sách ảnh của 1 user
    public function getAllImages($userId) {
        $this->db->select('id, description, image_url, created_at');
        $this->db->where('active', 1);          // Chỉ lấy chưa bị xoá
        $this->db->where('user_id', $userId);   // Chỉ lấy của user này
        $this->db->order_by('id', 'DESC');      // Mới nhất lên trước
        $query = $this->db->get($this->table);
        // SQL: SELECT id, description, image_url, created_at
        //      FROM tbl_upload_images
        //      WHERE active = 1 AND user_id = 'xK9mPq2R...'
        //      ORDER BY id DESC
        return $query->result_array();
    }

    // THÊM record mới sau khi upload
    public function insertUpload($userId, $desc, $imageUrl) {
        return $this->db->insert($this->table, [
            'active'      => 1,
            'user_id'     => $userId,
            'description' => $desc,
            'image_url'   => $imageUrl
        ]);
        // SQL: INSERT INTO tbl_upload_images (active, user_id, description, image_url)
        //      VALUES (1, 'xK9mPq2R...', 'HopDong', 'https://...')
    }
}
```

---

## 8. Sơ đồ tổng thể toàn bộ data flow

```
📱 ANDROID APP
─────────────────────────────────────────────────────────────
[ScannerActivity]
  Camera chụp ảnh
      │ IMG_xxxx.jpg (file tạm)
      ▼
  UCrop cắt theo tỉ lệ A4
      │ cropped_xxxx.jpg (file đã crop)
      ▼
[PreviewActivity]
  User bấm "Lưu PDF"
      │
      ▼
  PdfUtils.createPdf()
  → Gom nhiều ảnh → 1 file PDF
      │ scan.pdf (lưu bộ nhớ máy)
      ▼
  uploadImageToServer(pdfFile, name)
  → Retrofit tạo multipart request
      │
      │ POST multipart/form-data
      │ - userId = Firebase UID
      │ - description = "scan"
      │ - image = <binary PDF>
      │
      │ (qua ngrok tunnel)
      │
─────────────────────────────────────────────────────────────
🌐 BACKEND SERVER (XAMPP)
─────────────────────────────────────────────────────────────
      ▼
Apache nhận request
      ▼
PHP CodeIgniter → Upload.php::image()
      │
      ├── Validate: có userId không? Có file không?
      │
      ├── Lưu file vào: /htdocs/webapi/uploads/abc123.pdf
      │   (tên file được mã hoá ngẫu nhiên)
      │
      └── Gọi UploadImages_model::insertUpload()
              │
              ▼
─────────────────────────────────────────────────────────────
🗄️ MySQL DATABASE
─────────────────────────────────────────────────────────────
  INSERT INTO tbl_upload_images
  (user_id,        description, image_url,              active)
  ('xK9mPq2R...', 'scan',      'https://.../abc123.pdf', 1)

─────────────────────────────────────────────────────────────
📱 KHI MỞ LẠI APP (MainActivity)
─────────────────────────────────────────────────────────────
  auth.currentUser?.uid → "xK9mPq2R..."
      │
      ▼
  GET /getupload/image?userId=xK9mPq2R...
      │
      ▼
  GetUpload.php → SELECT * WHERE user_id = 'xK9mPq2R...' AND active = 1
      │
      ▼
  JSON [{ id, description, imageUrl, uploadDate }, ...]
      │
      ▼
  RecyclerView hiển thị danh sách tài liệu
```

---

## 9. Tóm tắt: Ai lưu gì ở đâu?

| Dữ liệu | Lưu ở đâu | Ai quản lý |
|---|---|---|
| Email, password (hash), UID | Firebase Server (Google Cloud) | Firebase tự lo |
| File PDF/ảnh thực tế | `/htdocs/webapi/uploads/` (ổ cứng máy bạn) | Apache + PHP |
| Metadata (tên file, ai upload, URL, ngày) | MySQL `tbl_upload_images` | PHP + CodeIgniter |
| Session đăng nhập, JWT, Refresh Token | Bộ nhớ máy Android (encrypted) | Firebase SDK |

---

## 10. Setup từ đầu cần làm gì?

```
1. Cài XAMPP → Start Apache + MySQL

2. Tạo database trong phpMyAdmin:
   - Tên DB: upload_image
   - Tạo bảng: tbl_upload_images (cấu trúc như mục 2.2)

3. Copy thư mục webapi vào: /htdocs/webapi/

4. Tạo thư mục uploads:
   /htdocs/webapi/uploads/        ← PHP sẽ lưu file vào đây
   (cần cấp quyền write)

5. Chạy ngrok:
   ngrok http 80
   → Lấy URL: https://xxxx.ngrok.io
   → Điền vào buildConfigField BASE_URL trong build.gradle

6. Build + chạy Android app
```

---

*File này được tạo từ cuộc trò chuyện giải thích database và data flow trong project DocScanner.*
