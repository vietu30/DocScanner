# Chi Tiết Các Lệnh Gọi API Từ App Đến CI3 Backend

> Tài liệu này trình bày **từng dòng code** trong app Android gọi đến server CI3,
> giải thích chúng làm gì, gửi gì, nhận gì, và giao tiếp với nhau ra sao.

---

## 1. Tổng Quan Kiến Trúc

```
┌──────────────────────────────────────────────────────┐
│                   Android App                        │
│                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │ MainActivity  │  │PreviewActivity│  │SettingsAct.│ │
│  │              │  │              │  │            │ │
│  │• loadFromSer │  │• uploadImage │  │            │ │
│  │• deleteServer│  │  ToServer()  │  │            │ │
│  │  Item()      │  │              │  │            │ │
│  └──────┬───────┘  └──────┬───────┘  └────────────┘ │
│         │                 │                          │
│         ▼                 ▼                          │
│  ┌────────────────────────────────────────────────┐  │
│  │           ApiClient (Retrofit + OkHttp)        │  │
│  │  BASE_URL = https://xxx.ngrok-free.dev/webapi/ │  │
│  └──────────────────────┬─────────────────────────┘  │
│                         │                            │
│  ┌──────────────────────┴─────────────────────────┐  │
│  │              ApiService (interface)             │  │
│  │                                                 │  │
│  │  GET  /index.php/getupload/image  → getImages() │  │
│  │  POST /index.php/upload/image     → uploadImage()│  │
│  │  POST /index.php/delete/image     → deleteImage()│  │
│  └─────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
                          │
                    HTTP (qua ngrok)
                          │
                          ▼
┌──────────────────────────────────────────────────────┐
│              XAMPP Server (CI3 Backend)               │
│                                                      │
│  Apache → PHP (CodeIgniter 3)                        │
│                                                      │
│  Controller: Getupload  → truy vấn DB → trả JSON    │
│  Controller: Upload     → nhận file → lưu + insert DB│
│  Controller: Delete     → soft delete (active=0)     │
│                                                      │
│  MySQL DB: bảng images                               │
│  Folder: /uploads/   (lưu file thực tế)              │
└──────────────────────────────────────────────────────┘
```

---

## 2. Các File Liên Quan Trong App

| File | Đường dẫn | Vai trò |
|------|-----------|---------|
| **ApiClient.kt** | `network/ApiClient.kt` | Cấu hình Retrofit + OkHttp, tạo instance API |
| **ApiService.kt** | `network/ApiService.kt` | Khai báo 3 endpoint (interface Retrofit) |
| **ImageItem.kt** | `model/ImageItem.kt` | Model cho response của getImages |
| **UploadResponse.kt** | `network/UploadResponse.kt` | Model cho response của uploadImage |
| **DeleteResponse.kt** | `network/DeleteResponse.kt` | Model cho response của deleteImage |
| **MainActivity.kt** | `MainActivity.kt` | Gọi `getImages()` và `deleteImage()` |
| **PreviewActivity.kt** | `PreviewActivity.kt` | Gọi `uploadImage()` |
| **ScanItem.kt** | `ScanItem.kt` | Model hiển thị trên RecyclerView |

---

## 3. ApiClient — Cầu Nối HTTP

📁 **File**: [ApiClient.kt](file:///Users/viettung/AndroidStudioProjects/DocScanner/app/src/main/java/com/example/docscanner/network/ApiClient.kt)

```kotlin
object ApiClient {

    // Interceptor ghi log mọi request/response (debug)
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // OkHttp client có thêm header bypass ngrok warning
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("ngrok-skip-browser-warning", "true")  // ← bypass ngrok
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Retrofit instance → tạo ra ApiService
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)  // ← "https://xxx.ngrok-free.dev/webapi/"
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())  // ← JSON ↔ Kotlin object
            .build()
            .create(ApiService::class.java)
    }
}
```

### Giải thích:
- **BASE_URL** được cấu hình trong `build.gradle.kts`:
  ```
  buildConfigField("String", "BASE_URL", "\"https://zelma-undamageable-odis.ngrok-free.dev/webapi/\"")
  ```
- Mỗi khi gọi `ApiClient.api.xxx()`, Retrofit sẽ ghép `BASE_URL + endpoint` → tạo URL đầy đủ.
- Header `ngrok-skip-browser-warning` được tự động thêm vào **mọi request** để tránh trang cảnh báo của ngrok free.

---

## 4. ApiService — Khai Báo 3 Endpoint

📁 **File**: [ApiService.kt](file:///Users/viettung/AndroidStudioProjects/DocScanner/app/src/main/java/com/example/docscanner/network/ApiService.kt)

```kotlin
interface ApiService {

    // ① GET danh sách ảnh
    @GET("index.php/getupload/image")
    suspend fun getImages(
        @Query("userId") userId: String
    ): Response<ImageResponse>

    // ② POST upload ảnh (multipart)
    @Multipart
    @POST("index.php/upload/image")
    suspend fun uploadImage(
        @Part("userId") userId: RequestBody,
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody
    ): Response<UploadResponse>

    // ③ POST xoá ảnh (soft delete)
    @FormUrlEncoded
    @POST("index.php/delete/image")
    suspend fun deleteImage(
        @Field("userId") userId: String,
        @Field("id") id: Int
    ): Response<DeleteResponse>
}
```

---

## 5. Chi Tiết Từng API Call

---

### 5.1 `getImages()` — Lấy Danh Sách Tài Liệu

> **Ai gọi?** → `MainActivity.loadFromServer()`
> **Khi nào?** → Mỗi khi `onResume()` chạy (mở app, quay lại từ màn hình khác)

#### Request

| Thuộc tính | Giá trị |
|-----------|---------|
| **Method** | `GET` |
| **URL** | `https://xxx.ngrok-free.dev/webapi/index.php/getupload/image?userId=<uid>` |
| **Header** | `ngrok-skip-browser-warning: true` (tự động thêm) |
| **Params** | `userId` = Firebase UID (ví dụ: `"abc123XYZ..."`) |

#### Response JSON (thành công)

```json
{
    "status": true,
    "message": "success",
    "data": [
        {
            "id": 1,
            "description": "tên file",
            "imageUrl": "uploads/filename.jpg",
            "uploadDate": 1712345678000
        },
        {
            "id": 2,
            "description": "tên file 2",
            "imageUrl": "uploads/filename2.pdf",
            "uploadDate": 1712345679000
        }
    ]
}
```

#### Code gọi trong app (dòng-by-dòng)

📁 **File**: [MainActivity.kt](file:///Users/viettung/AndroidStudioProjects/DocScanner/app/src/main/java/com/example/docscanner/MainActivity.kt) — dòng 54–116

```kotlin
// ① Khi màn hình hiện lên → kiểm tra đã login chưa
override fun onResume() {
    super.onResume()
    val uid = auth.currentUser?.uid          // Lấy Firebase UID
    if (uid != null) {
        loadFromServer(uid)                   // Đã login → load từ server
    } else {
        recyclerView.adapter = ScanAdapter(mutableListOf()) {}  // Chưa login → rỗng
    }
}

// ② Gọi API getImages
private fun loadFromServer(userId: String) {
    lifecycleScope.launch {                   // Chạy trong Coroutine (không block UI)
        try {
            // ★ DÒNG GỌI API ★
            val response = ApiClient.api.getImages(userId)
            //       ↑               ↑
            //   Retrofit instance   Gọi GET /index.php/getupload/image?userId=xxx

            if (response.isSuccessful && response.body()?.status == true) {
                // ③ Parse response → tạo danh sách ScanItem để hiển thị
                val items = response.body()!!.data.map { img ->
                    val date = SimpleDateFormat("dd/MM/yyyy HH:mm")
                        .format(Date(img.uploadDate))  // timestamp → chuỗi ngày

                    val fileName = img.imageUrl.substringAfterLast("/")
                    val fixedUrl = "${BuildConfig.BASE_URL}uploads/$fileName"
                    //  ↑ Ghép lại URL đầy đủ, ví dụ:
                    //  "https://xxx.ngrok-free.dev/webapi/uploads/filename.jpg"

                    ScanItem(
                        fileName  = img.description ?: "Ảnh scan",
                        fileInfo  = date,
                        file      = null,       // Không có file local
                        imageUrl  = fixedUrl,    // URL để download + mở
                        serverId  = img.id       // ID trên server (dùng cho delete)
                    )
                }
                // ④ Gắn adapter → hiển thị lên RecyclerView
                val adapter = ScanAdapter(items.toMutableList()) { item ->
                    item.imageUrl?.let { url -> downloadAndOpen(url) }
                }
                recyclerView.adapter = adapter
            }
        } catch (e: Exception) {
            // Không kết nối được → hiện danh sách rỗng
            Toast.makeText(this, "Không kết nối được server", ...)
        }
    }
}
```

#### Sơ đồ luồng

```
onResume()
    │
    ▼
auth.currentUser?.uid  ──(null)──▶  Hiện danh sách rỗng
    │
    │ (có uid)
    ▼
ApiClient.api.getImages(uid)
    │
    │  GET /index.php/getupload/image?userId=uid
    │
    ▼
Server trả JSON { status, data: [...] }
    │
    ▼
Parse ImageItem → ScanItem
    │
    ▼
Hiển thị lên RecyclerView
```

---

### 5.2 `uploadImage()` — Upload File Lên Server

> **Ai gọi?** → `PreviewActivity.uploadImageToServer()`
> **Khi nào?** → Sau khi user nhấn "Lưu PDF" hoặc "Lưu ảnh" trong dialog

#### Request

| Thuộc tính | Giá trị |
|-----------|---------|
| **Method** | `POST` |
| **URL** | `https://xxx.ngrok-free.dev/webapi/index.php/upload/image` |
| **Content-Type** | `multipart/form-data` |
| **Fields** | `userId` (text), `image` (file binary), `description` (text) |

#### Ví dụ request body (multipart)

```
------boundary
Content-Disposition: form-data; name="userId"

abc123XYZ...
------boundary
Content-Disposition: form-data; name="image"; filename="scan_001.jpg"
Content-Type: image/jpeg

<binary data của file>
------boundary
Content-Disposition: form-data; name="description"

scan_001
------boundary--
```

#### Response JSON (thành công)

```json
{
    "status": true,
    "message": "Upload thành công",
    "data": {
        "imageUrl": "uploads/1712345678_scan_001.jpg",
        "description": "scan_001"
    }
}
```

#### Code gọi trong app (dòng-by-dòng)

📁 **File**: [PreviewActivity.kt](file:///Users/viettung/AndroidStudioProjects/DocScanner/app/src/main/java/com/example/docscanner/PreviewActivity.kt) — dòng 111–135

```kotlin
private fun uploadImageToServer(file: File, description: String) {
    // ① Kiểm tra đăng nhập — chưa login thì bỏ qua
    val uid = auth.currentUser?.uid ?: return

    lifecycleScope.launch {
        try {
            // ② Xác định MIME type (PDF hay ảnh)
            val mimeType = if (file.extension == "pdf") "application/pdf" else "image/jpeg"

            // ③ Chuẩn bị các phần của multipart request
            val userIdBody = uid.toRequestBody("text/plain".toMediaTypeOrNull())
            //  ↑ userId gửi dưới dạng text/plain

            val descBody = description.toRequestBody("text/plain".toMediaTypeOrNull())
            //  ↑ mô tả file (tên file)

            val fileBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            //  ↑ nội dung binary của file

            val filePart = MultipartBody.Part.createFormData("image", file.name, fileBody)
            //  ↑ đóng gói thành form-data với field name "image"

            // ④ ★ DÒNG GỌI API ★
            val response = ApiClient.api.uploadImage(userIdBody, filePart, descBody)
            //       ↑               ↑
            //   Retrofit instance   POST /index.php/upload/image (multipart)

            if (response.isSuccessful && response.body()?.status == true) {
                // Upload thành công — file đã có trên server
            } else {
                Toast.makeText(this, "Upload thất bại: ${response.body()?.message}", ...)
            }
        } catch (e: Exception) {
            // Server không khả dụng → chỉ lưu local, im lặng
            Log.w("Upload", "Server không khả dụng: ${e.message}")
        }
    }
}
```

#### Ai gọi `uploadImageToServer()`?

```kotlin
// ① Khi lưu PDF (dòng 80–88)
private fun savePdf(name: String) {
    val pdfFile = File(getExternalFilesDir(null), "$name.pdf")
    createPdf(this, imageList, pdfFile.absolutePath)  // Tạo file PDF
    uploadImageToServer(pdfFile, name)                 // ★ Upload lên server
    goHome()
}

// ② Khi lưu ảnh (dòng 91–109)
private fun saveImages(baseName: String) {
    imageList.forEachIndexed { index, path ->
        val destFile = File(dir, "${baseName}_${index + 1}.jpg")
        // ... lưu bitmap ra file ...
        uploadImageToServer(destFile, baseName)         // ★ Upload từng ảnh
    }
    goHome()
}
```

#### Sơ đồ luồng

```
User nhấn "Lưu PDF" / "Lưu ảnh"
    │
    ▼
savePdf(name) / saveImages(baseName)
    │
    ├── Tạo file local (PDF hoặc JPG)
    │
    ▼
uploadImageToServer(file, description)
    │
    ├── Kiểm tra đăng nhập (uid)
    ├── Chuẩn bị multipart: userId + image + description
    │
    ▼
ApiClient.api.uploadImage(userIdBody, filePart, descBody)
    │
    │  POST /index.php/upload/image
    │  Content-Type: multipart/form-data
    │
    ▼
Server nhận file → lưu vào /uploads/ → insert DB → trả JSON
    │
    ▼
App nhận response → kiểm tra status → goHome()
```

---

### 5.3 `deleteImage()` — Xoá Tài Liệu (Soft Delete)

> **Ai gọi?** → `MainActivity.deleteServerItem()`
> **Khi nào?** → User nhấn giữ (long press) vào item → chọn "Xoá"

#### Request

| Thuộc tính | Giá trị |
|-----------|---------|
| **Method** | `POST` |
| **URL** | `https://xxx.ngrok-free.dev/webapi/index.php/delete/image` |
| **Content-Type** | `application/x-www-form-urlencoded` |
| **Fields** | `userId=<uid>&id=<serverId>` |

#### Response JSON (thành công)

```json
{
    "status": true,
    "message": "Xoá thành công"
}
```

#### Code gọi trong app (dòng-by-dòng)

📁 **File**: [MainActivity.kt](file:///Users/viettung/AndroidStudioProjects/DocScanner/app/src/main/java/com/example/docscanner/MainActivity.kt) — dòng 151–166

```kotlin
private fun deleteServerItem(id: Int, items: MutableList<ScanItem>,
                              adapter: ScanAdapter, position: Int) {
    // ① Lấy userId
    val userId = auth.currentUser?.uid ?: return

    lifecycleScope.launch {
        try {
            // ② ★ DÒNG GỌI API ★
            val response = ApiClient.api.deleteImage(userId, id)
            //       ↑               ↑
            //   Retrofit instance   POST /index.php/delete/image
            //                       body: userId=xxx&id=123

            if (response.isSuccessful && response.body()?.status == true) {
                // ③ Xoá thành công → xoá item khỏi RecyclerView
                adapter.removeAt(position)
                Toast.makeText(this, "Đã xoá thành công", ...)
            } else {
                Toast.makeText(this, "Xoá thất bại", ...)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi kết nối: ${e.message}", ...)
        }
    }
}
```

#### Ai gọi `deleteServerItem()`?

```kotlin
// Long press handler (dòng 90–101 trong loadFromServer)
adapter.setOnLongClickListener { item, position ->
    if (item.serverId != null) {
        AlertDialog.Builder(this)
            .setTitle("Xoá tài liệu")
            .setMessage("Bạn có chắc muốn xoá \"${item.fileName}\"?")
            .setPositiveButton("Xoá") { _, _ ->
                deleteServerItem(item.serverId, mutableItems, adapter, position)
                //                ↑ serverId = img.id lấy từ getImages() lúc đầu
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }
}
```

#### Sơ đồ luồng

```
User nhấn giữ item trên RecyclerView
    │
    ▼
AlertDialog hiện: "Bạn có chắc muốn xoá?"
    │
    │  User nhấn "Xoá"
    ▼
deleteServerItem(serverId, items, adapter, position)
    │
    ▼
ApiClient.api.deleteImage(userId, id)
    │
    │  POST /index.php/delete/image
    │  body: userId=xxx&id=123
    │
    ▼
Server set active=0 trong DB → trả JSON
    │
    ▼
App nhận response → adapter.removeAt(position) → UI cập nhật
```

---

## 6. Mối Quan Hệ Giữa 3 API

```
                    ┌─────────────────────────────────────────┐
                    │           VÒNG ĐỜI CỦA 1 TÀI LIỆU    │
                    └─────────────────────────────────────────┘

    ┌───────────┐         ┌───────────┐         ┌───────────┐
    │  UPLOAD   │  ────▶  │ GETUPLOAD │  ────▶  │  DELETE   │
    │           │         │           │         │           │
    │ Tạo mới   │         │ Hiển thị  │         │ Xoá bỏ    │
    └───────────┘         └───────────┘         └───────────┘
         │                     │                     │
         ▼                     ▼                     ▼
    PreviewActivity       MainActivity           MainActivity
    uploadImageToServer   loadFromServer         deleteServerItem
         │                     │                     │
         ▼                     ▼                     ▼
    POST /upload/image    GET /getupload/image   POST /delete/image
         │                     │                     │
         ▼                     ▼                     ▼
    Server lưu file +     Server query DB        Server set active=0
    insert vào DB         WHERE active=1         WHERE id=? AND userId=?
         │                     │                     │
         ▼                     ▼                     ▼
    Trả imageUrl +        Trả list ImageItem     Trả status + message
    description           (id, url, date, desc)
```

### Luồng hoàn chỉnh:

1. **User scan tài liệu** → vào `PreviewActivity` → nhấn "Lưu"
2. **`uploadImage()`** gửi file lên server → server lưu file + insert DB row (active=1)
3. **User quay về `MainActivity`** → `onResume()` chạy
4. **`getImages()`** query server → server trả về tất cả row có `active=1` của user đó
5. **User muốn xoá** → long press → confirm → **`deleteImage()`** gửi id
6. **Server set `active=0`** (soft delete) → file vẫn còn trên disk nhưng không hiện nữa
7. **Lần tới `getImages()`** sẽ không trả về row đã bị soft delete

---

## 7. Bảng Tóm Tắt

| API | Method | URL CI3 | Gọi từ | Hàm trong app | Gửi gì | Nhận gì |
|-----|--------|---------|--------|----------------|---------|---------|
| **getImages** | `GET` | `/index.php/getupload/image` | `MainActivity.loadFromServer()` | `ApiClient.api.getImages(userId)` | `?userId=<uid>` | `ImageResponse { status, data: [ImageItem] }` |
| **uploadImage** | `POST` | `/index.php/upload/image` | `PreviewActivity.uploadImageToServer()` | `ApiClient.api.uploadImage(userId, filePart, desc)` | `multipart: userId + image + description` | `UploadResponse { status, message, data: {imageUrl, description} }` |
| **deleteImage** | `POST` | `/index.php/delete/image` | `MainActivity.deleteServerItem()` | `ApiClient.api.deleteImage(userId, id)` | `form: userId=xxx&id=123` | `DeleteResponse { status, message }` |

---

## 8. Model Classes (Cấu Trúc Dữ Liệu)

### Request side (gửi đi)

```kotlin
// getImages: không cần model, chỉ truyền userId (String) qua @Query

// uploadImage: 3 phần multipart
userId: RequestBody         // "text/plain" — Firebase UID
image: MultipartBody.Part   // file binary (image/jpeg hoặc application/pdf)
description: RequestBody    // "text/plain" — tên file

// deleteImage: 2 field form-urlencoded
userId: String              // Firebase UID
id: Int                     // ID của record trên server (lấy từ getImages)
```

### Response side (nhận về)

```kotlin
// ① GET getImages → ImageResponse
data class ImageResponse(
    val status: Boolean,     // true = thành công
    val message: String,     // "success"
    val data: List<ImageItem>
)
data class ImageItem(
    val id: Int,             // ID trên server (dùng cho delete)
    val description: String?,// tên file
    val imageUrl: String,    // đường dẫn file trên server
    val uploadDate: Long     // timestamp (milliseconds)
)

// ② POST uploadImage → UploadResponse
data class UploadResponse(
    val status: Boolean,
    val message: String,
    val data: UploadData?
)
data class UploadData(
    val imageUrl: String,    // URL file đã upload
    val description: String  // mô tả
)

// ③ POST deleteImage → DeleteResponse
data class DeleteResponse(
    val status: Boolean,
    val message: String
)
```

---

## 9. Ghi Chú Quan Trọng

> [!NOTE]
> **Soft Delete**: API `deleteImage` không xoá thực sự file trên server, mà chỉ set `active=0` trong DB.
> Lần tới `getImages()` query `WHERE active=1` sẽ không trả về row đó nữa.

> [!NOTE]
> **Offline Graceful**: Nếu server không khả dụng lúc upload, app chỉ log warning và vẫn lưu file local bình thường. Không crash, không báo lỗi ầm ĩ.

> [!NOTE]
> **serverId liên kết upload → delete**: Khi `getImages()` trả về `ImageItem.id`, giá trị này được lưu vào `ScanItem.serverId`. Khi user xoá, chính `serverId` này được gửi lên `deleteImage(userId, id)`.

> [!IMPORTANT]
> **Tất cả API đều cần Firebase UID**: App lấy `auth.currentUser?.uid`. Nếu chưa đăng nhập → không gọi API nào cả.
