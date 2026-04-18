# Giải Thích ApiClient.kt — Cầu Nối App Mobile ↔ Server CI3

---

## Vai trò của ApiClient

```
MainActivity / PreviewActivity
        │
        │  gọi: ApiClient.api.getImages(...)
        │
        ▼
┌──────────────────────────────────┐
│          ApiClient.kt            │
│                                  │
│  Gồm 2 thứ:                     │
│  ① OkHttpClient → lo gửi/nhận   │
│  ② Retrofit     → lo chuyển đổi │
│                                  │
│  Ghép BASE_URL + endpoint        │
│  Gắn header, timeout             │
│  Chuyển JSON ↔ Kotlin object     │
└──────────────┬───────────────────┘
               │
               │  HTTP request
               ▼
        Server CI3 (XAMPP)
```

Nói ngắn: **ApiClient = người đưa thư** giữa app và server.  
App đưa dữ liệu cho ApiClient → ApiClient đóng gói thành HTTP request → gửi đi → nhận response → chuyển JSON thành Kotlin object → trả lại cho app.

---

## Code từng dòng

```kotlin
package com.example.docscanner.network

import com.example.docscanner.BuildConfig
// ↑ Chứa BASE_URL, được config trong build.gradle.kts
//   BuildConfig.BASE_URL = "https://zelma-undamageable-odis.ngrok-free.dev/webapi/"

import okhttp3.OkHttpClient
// ↑ Thư viện gửi/nhận HTTP (giống postman nhưng trong code)

import okhttp3.logging.HttpLoggingInterceptor
// ↑ Công cụ ghi log request/response (để debug trong Logcat)

import retrofit2.Retrofit
// ↑ Thư viện "biến interface thành HTTP client"
//   Bạn khai báo hàm trong ApiService → Retrofit tự tạo code gọi HTTP

import retrofit2.converter.gson.GsonConverterFactory
// ↑ Bộ chuyển đổi JSON ↔ Kotlin object
//   JSON: {"status": true} → Kotlin: UploadResponse(status = true)

import java.util.concurrent.TimeUnit
// ↑ Đơn vị thời gian (SECONDS, MINUTES...)
```

---

### `object ApiClient` — Singleton

```kotlin
object ApiClient {
// ↑ "object" = singleton — chỉ có DUY NHẤT 1 instance trong toàn app
//   Dù gọi ApiClient.api từ MainActivity hay PreviewActivity
//   → đều dùng CÙNG 1 kết nối, không tạo lại
```

---

### Phần 1: OkHttpClient — "Người gửi thư"

```kotlin
    // ——— Interceptor ghi log ———
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    // ↑ Ghi log MỌI request/response vào Logcat
    // ↑ Level.BODY = log cả body (nội dung gửi/nhận)
    // ↑ Mở Logcat trong Android Studio → tìm "OkHttp" → thấy hết
    //
    // Ví dụ log:
    // --> POST http://xxx/index.php/upload/image
    // Content-Type: multipart/form-data
    // userId=xK9mPq2R...
    // <-- 200 OK {"status":true,"message":"Upload success"}

    // ——— OkHttp Client ———
    val okHttpClient = OkHttpClient.Builder()

        .addInterceptor(loggingInterceptor)
        // ↑ Gắn bộ ghi log vào (interceptor = "chặn giữa đường để xem")

        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("ngrok-skip-browser-warning", "true")
                .build()
            chain.proceed(request)
        }
        // ↑ Interceptor thứ 2: TỰ ĐỘNG thêm header vào MỌI request
        //   Tại sao? Ngrok free hiện trang cảnh báo khi gọi API
        //   Header này bảo ngrok: "skip cái trang đó đi, tôi biết rồi"
        //
        //   Mọi request gửi đi sẽ có:
        //   ngrok-skip-browser-warning: true
        //
        //   Nếu không có header này → server trả HTML cảnh báo thay vì JSON

        .connectTimeout(30, TimeUnit.SECONDS)
        // ↑ Chờ tối đa 30 giây để KẾT NỐI đến server
        //   Nếu quá 30s mà chưa kết nối được → báo lỗi timeout

        .readTimeout(30, TimeUnit.SECONDS)
        // ↑ Chờ tối đa 30 giây để ĐỌC response từ server
        //   Server xử lý lâu quá 30s → báo lỗi

        .writeTimeout(60, TimeUnit.SECONDS)
        // ↑ Chờ tối đa 60 giây để GỬI dữ liệu lên server
        //   60s vì upload file có thể lâu (file lớn, mạng chậm)

        .build()
        // ↑ Gom tất cả config → tạo ra OkHttpClient hoàn chỉnh
```

**OkHttpClient tóm gọn:**

```
Mỗi request gửi đi, OkHttpClient tự động:
  1. Thêm header "ngrok-skip-browser-warning: true"
  2. Ghi log request vào Logcat
  3. Gửi request, chờ tối đa 30s kết nối / 60s gửi
  4. Nhận response, ghi log response
  5. Trả response về cho Retrofit xử lý
```

---

### Phần 2: Retrofit — "Người dịch"

```kotlin
    val api: ApiService by lazy {
    // ↑ "by lazy" = chỉ tạo khi lần đầu tiên ApiClient.api được gọi
    //   Lần sau gọi lại → dùng cái đã tạo, không tạo mới
    //   Tiết kiệm bộ nhớ + tốc độ

        Retrofit.Builder()

            .baseUrl(BuildConfig.BASE_URL)
            // ↑ URL gốc: "https://zelma-undamageable-odis.ngrok-free.dev/webapi/"
            //   Mọi endpoint sẽ nối vào sau URL này:
            //   baseUrl + "index.php/getupload/image"
            //   = "https://xxx.ngrok.dev/webapi/index.php/getupload/image"

            .client(okHttpClient)
            // ↑ Dùng OkHttpClient đã config ở trên (có log, header, timeout)

            .addConverterFactory(GsonConverterFactory.create())
            // ↑ Gson = thư viện Google chuyển JSON ↔ Kotlin object
            //
            //   NHẬN response:
            //   JSON: {"status":true,"message":"Success","data":[...]}
            //       → tự động chuyển thành: ImageResponse(status=true, message="Success", data=[...])
            //
            //   GỬI request:
            //   Kotlin object → tự động chuyển thành JSON/form-data

            .build()
            // ↑ Gom config → tạo Retrofit instance

            .create(ApiService::class.java)
            // ↑ ĐÂY LÀ PHÉP MÀU CỦA RETROFIT
            //   Đưa vào: ApiService interface (chỉ là khai báo, không có code thật)
            //   Trả ra:  1 object thực sự biết gọi HTTP
            //
            //   Retrofit đọc các annotation (@GET, @POST, @Query, @Field...)
            //   → tự sinh code gọi HTTP tương ứng
            //
            //   Bạn viết:
            //     @GET("index.php/getupload/image")
            //     suspend fun getImages(@Query("userId") userId: String)
            //
            //   Retrofit tự tạo code tương đương:
            //     "OK, khi ai gọi getImages("abc123"), tôi sẽ:
            //      1. Tạo GET request đến baseUrl + index.php/getupload/image?userId=abc123
            //      2. Gửi qua OkHttpClient
            //      3. Nhận JSON response
            //      4. Dùng Gson chuyển JSON → ImageResponse
            //      5. Trả về Response<ImageResponse>"
    }
```

---

## Sơ đồ hoạt động khi gọi `ApiClient.api.getImages("abc123")`

```
MainActivity gọi:
ApiClient.api.getImages("abc123")
      │
      ▼
Retrofit (api) nhận lệnh:
  "getImages có @GET("index.php/getupload/image") và @Query("userId")"
  → Tạo URL: https://xxx.ngrok.dev/webapi/index.php/getupload/image?userId=abc123
  → Tạo HTTP GET request
      │
      ▼
OkHttpClient (okHttpClient) xử lý:
  → Interceptor 1: Ghi log request vào Logcat
  → Interceptor 2: Thêm header ngrok-skip-browser-warning: true
  → Gửi request qua internet
      │
      ▼
Server CI3 nhận → xử lý → trả JSON:
  {"status":true,"data":[{"id":1,"description":"ABC",...}]}
      │
      ▼
OkHttpClient nhận response:
  → Ghi log response vào Logcat
  → Trả cho Retrofit
      │
      ▼
Retrofit + Gson chuyển đổi:
  JSON string → ImageResponse(status=true, data=[ImageItem(id=1,...)])
      │
      ▼
MainActivity nhận được:
  response.body() = ImageResponse(status=true, data=[...])
  → Parse → hiển thị lên RecyclerView
```

---

## Tại sao cần cả OkHttp lẫn Retrofit?

| | OkHttp | Retrofit |
|---|---|---|
| **Làm gì** | Gửi/nhận HTTP thô | Chuyển interface → HTTP client |
| **Ví von** | Xe tải chở hàng | Người đóng gói + dán nhãn |
| **Không có nó** | Phải tự viết URL, header, timeout | Phải tự parse JSON, tự ghép URL |
| **Trong project** | Lo: header ngrok, log, timeout | Lo: ghép URL, parse JSON ↔ Kotlin |

```
Retrofit = "Tôi biết GỌI CÁI GÌ" (endpoint, params, response type)
OkHttp   = "Tôi biết GỌI NHƯ NÀO" (gửi bytes qua mạng, header, timeout)
```

---

## Tóm tắt 1 hình

```
┌─ build.gradle.kts ──────────────────────────────┐
│  BASE_URL = "https://xxx.ngrok.dev/webapi/"      │
└──────────────────────┬───────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────┐
│              ApiClient.kt (object)                │
│                                                   │
│  ┌─────────────────────────────────────────────┐ │
│  │  OkHttpClient                                │ │
│  │  • Header: ngrok-skip-browser-warning        │ │
│  │  • Log: ghi mọi request/response             │ │
│  │  • Timeout: 30s connect, 30s read, 60s write │ │
│  └──────────────────┬──────────────────────────┘ │
│                     │                             │
│  ┌──────────────────▼──────────────────────────┐ │
│  │  Retrofit                                    │ │
│  │  • baseUrl = BASE_URL                        │ │
│  │  • client = OkHttpClient ở trên              │ │
│  │  • converter = Gson (JSON ↔ Kotlin)          │ │
│  │  • .create(ApiService) → tạo api instance    │ │
│  └──────────────────┬──────────────────────────┘ │
│                     │                             │
│  val api: ApiService ← kết quả cuối cùng         │
└──────────────────────┬───────────────────────────┘
                       │
        ApiClient.api.getImages()
        ApiClient.api.uploadImage()
        ApiClient.api.deleteImage()
```
