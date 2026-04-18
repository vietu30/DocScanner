# 🔥 Firebase – Hỏi & Đáp Chi Tiết (DocScanner)

> Ghi lại cuộc trò chuyện giải thích cơ chế Firebase trong project DocScanner.  
> Ngày: 2026-04-04

---

## ❓ Câu 1: Firebase kết nối kiểu gì?

### Trả lời:

Firebase kết nối **không phải qua một server riêng mà bạn tự cấu hình**, mà thông qua một file cấu hình đặc biệt do Google cấp:

**Phía Android (mobile):**
```
app/google-services.json
```
File này chứa: API key, project ID, app ID, sender ID, ... của Firebase project **`docscanner-546de`**.  
Khi build app, Gradle đọc file này và tự nhúng thông tin vào app.  
App sẽ tự biết "kết nối đến Firebase project nào" mà không cần bạn viết thêm gì.

**Phía Web (nếu có):**
```javascript
const firebaseConfig = {
  apiKey: "...",
  authDomain: "docscanner-546de.firebaseapp.com",
  projectId: "docscanner-546de",
  ...
}
```

Cả 2 đều trỏ vào **cùng 1 Firebase project** → người dùng tạo tài khoản trên mobile, login web cùng email/pass là vào được ngay.

```
[Android App]                     [Web App]
google-services.json    ←→    firebaseConfig
         │                          │
         └────────────┬─────────────┘
                      ▼
         Firebase Server (Google Cloud)
         → 1 bảng user duy nhất
```

---

## ❓ Câu 2: Firebase API có bảo mật cho mình không?

### Trả lời: **Có, Firebase tự lo phần bảo mật xác thực.**

Khi bạn gọi `signInWithEmailAndPassword(email, password)`, Firebase SDK sẽ:

1. **Mã hóa toàn bộ kết nối** bằng HTTPS (TLS) — không ai nghe lén được
2. **Không gửi password dạng plain text** — SDK hash trước khi truyền
3. Firebase tự so sánh với password đã được **bcrypt hash** trong database của Google
4. Nếu sai → trả về error ngay, **không bao giờ trả về password**
5. **API key trong `google-services.json`** là public (có thể bị lộ) nhưng:
   - API key chỉ định danh "Firebase project nào", không phải mật khẩu
   - Bảo mật thực sự nằm ở **Firebase Security Rules** và **Authentication state**

> **Lưu ý quan trọng:** API key Firebase ≠ secret key.  
> Firebase thiết kế API key để công khai. Bảo mật thực sự là: người dùng phải đăng nhập đúng mới nhận được UID hợp lệ.

---

## ❓ Câu 3: Flow khi đăng nhập ra sao?

### Trả lời — Flow đăng nhập (Login):

```
┌──────────────────────────────────────────────────────────────────┐
│                        ANDROID APP                               │
│                                                                  │
│  1. User nhập:  email = "abc@gmail.com"                          │
│                 password = "123456"                              │
│                                                                  │
│  2. Code gọi:                                                    │
│     auth.signInWithEmailAndPassword(email, password)             │
│     (SettingsActivity.kt - dòng 56)                              │
│                                                                  │
│  3. Firebase SDK tự động:                                        │
│     → Gửi request HTTPS đến: identitytoolkit.googleapis.com     │
│     → Kèm theo email + password (đã mã hóa)                     │
└─────────────────────────┬────────────────────────────────────────┘
                          │ HTTPS (mã hóa)
                          ▼
┌──────────────────────────────────────────────────────────────────┐
│                   FIREBASE SERVER (Google Cloud)                 │
│                                                                  │
│  4. Firebase kiểm tra:                                           │
│     ✔ Email có tồn tại?                                         │
│     ✔ Password hash có khớp?                                     │
│                                                                  │
│  5. Nếu đúng → Tạo ra:                                         │
│     - UID: "xK9mPq2R..." (chuỗi định danh duy nhất, bất biến)   │
│     - ID Token (JWT): chuỗi dài, có hạn 1 giờ                   │
│     - Refresh Token: để xin ID Token mới khi hết hạn            │
└─────────────────────────┬────────────────────────────────────────┘
                          │ Trả về UID + Token
                          ▼
┌──────────────────────────────────────────────────────────────────┐
│                        ANDROID APP                               │
│                                                                  │
│  6. addOnSuccessListener { } chạy → đăng nhập thành công        │
│                                                                  │
│  7. Lấy UID:                                                     │
│     val uid = auth.currentUser?.uid                              │
│     (MainActivity.kt - dòng 56)                                  │
│                                                                  │
│  8. Gửi UID lên backend để lấy dữ liệu:                         │
│     ApiClient.api.getImages(uid)                                 │
│     → GET /index.php/getupload/image?userId=<UID>               │
│     (MainActivity.kt - dòng 69)                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## ❓ Câu 4: Code mobile ở đâu, viết như thế nào?

### Trả lời:

Code Firebase trong project của bạn nằm ở **2 file chính**:

---

### 📄 File 1: `SettingsActivity.kt` — Nơi xử lý đăng nhập / đăng ký

**Lấy instance Firebase Auth:**
```kotlin
// Dòng 23
auth = FirebaseAuth.getInstance()
```
→ `FirebaseAuth.getInstance()` tự đọc `google-services.json` để biết kết nối Firebase project nào.

**Đăng nhập:**
```kotlin
// Dòng 56
auth.signInWithEmailAndPassword(email, password)
    .addOnSuccessListener {
        // Đăng nhập thành công
        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
        updateUI()  // Cập nhật giao diện
    }
    .addOnFailureListener {
        // Sai email/pass hoặc lỗi mạng
        Toast.makeText(this, "Lỗi: ${it.message}", Toast.LENGTH_LONG).show()
    }
```

**Đăng ký tài khoản mới:**
```kotlin
// Dòng 46
auth.createUserWithEmailAndPassword(email, password)
    .addOnSuccessListener {
        // Firebase tự tạo UID mới + tự đăng nhập luôn sau khi tạo
        Toast.makeText(this, "Tạo tài khoản thành công!", Toast.LENGTH_SHORT).show()
        updateUI()
    }
```

**Đăng xuất:**
```kotlin
// Dòng 85
auth.signOut()
// → Firebase xóa session khỏi máy, currentUser = null
```

---

### 📄 File 2: `MainActivity.kt` — Nơi dùng UID để lấy dữ liệu

```kotlin
// Dòng 56-62: Kiểm tra đã đăng nhập chưa
val uid = auth.currentUser?.uid
if (uid != null) {
    loadFromServer(uid)   // Đã đăng nhập → lấy data từ server
} else {
    recyclerView.adapter = ScanAdapter(mutableListOf()) {}  // Chưa đăng nhập → list rỗng
}

// Dòng 69: Gửi UID như 1 query param lên backend
val response = ApiClient.api.getImages(userId)
// → GET https://<ngrok>/index.php/getupload/image?userId=xK9mPq2R...
```

---

## ❓ Câu 5: Mình nhập tài khoản mật khẩu rồi gửi lên server Firebase à?

### Trả lời: **Đúng, nhưng không phải gửi thẳng — mà qua Firebase SDK.**

```
Bạn NHẬP:  email + password (plain text trong EditText)
         ↓
Firebase SDK nhận, xử lý nội bộ:
  - Tạo HTTPS request đến Google API
  - Mã hóa password trước khi gửi
         ↓
Google Firebase Server:
  - Nhận request
  - So sánh với password đã lưu dạng bcrypt hash
  - KHÔNG bao giờ lưu plain text password
```

**Bạn không cần tự viết code gửi HTTP** — Firebase SDK làm hết. Bạn chỉ cần gọi hàm `signInWithEmailAndPassword()` và xử lý callback `onSuccess`/`onFailure`.

> ✅ Đây chính là lý do dùng Firebase Auth: **Không phải tự xây hệ thống bảo mật login phức tạp**.

---

## ❓ Câu 6: Firebase gen ra UID — UID là chìa khoá kết nối DB đúng không?

### Trả lời: **Đúng hoàn toàn. Đây là cơ chế cốt lõi của hệ thống.**

### UID là gì?

```
UID = "xK9mPq2Rf8hTzLm7GdNwQvBk3..."
```
- Một chuỗi ký tự **duy nhất, bất biến** cho mỗi user
- Firebase sinh ra **1 lần duy nhất** khi tạo tài khoản
- Dù user đổi email/password, UID **không đổi**
- Dù user login trên điện thoại khác, UID **vẫn giữ nguyên**

### UID làm chìa khoá như thế nào trong project của bạn?

```
MySQL Database (XAMPP)
─────────────────────────────────────────────────────
Bảng: images

id │ user_id              │ image_url          │ active
───┼──────────────────────┼────────────────────┼───────
1  │ xK9mPq2Rf8hTzLm7... │ uploads/scan1.jpg  │ 1
2  │ xK9mPq2Rf8hTzLm7... │ uploads/scan2.jpg  │ 1
3  │ aB5cDe9F...          │ uploads/scan3.jpg  │ 1
   └─── user A's data ──┘  └─── user B's data ─┘
```

- `user_id` trong MySQL **chính là Firebase UID**
- Khi `getImages(uid)` → PHP query: `WHERE user_id = '<uid>' AND active = 1`
- Mỗi user chỉ thấy đúng dữ liệu của mình

### Luồng đầy đủ: Từ Login đến xem tài liệu

```
1. [App] User đăng nhập → Firebase xác thực → Trả UID
                │
                ▼
2. [App] Lưu UID trong Firebase SDK (auth.currentUser?.uid)
                │
                ▼
3. [App] Gọi API: GET /getupload/image?userId=<UID>
                │
                ▼
4. [Backend PHP] Nhận userId, query MySQL:
   SELECT * FROM images WHERE user_id = '<UID>' AND active = 1
                │
                ▼
5. [Backend PHP] Trả về JSON danh sách ảnh của USER đó
                │
                ▼
6. [App] Hiển thị danh sách tài liệu lên RecyclerView
```

---

## ❓ Câu 7: JWT và Refresh Token là để làm gì?

### 7.1 — JWT (JSON Web Token) là gì?

Sau khi đăng nhập thành công, Firebase không chỉ trả về UID — nó còn trả về một **ID Token** (chính là JWT).

```
ID Token (JWT) trông như thế này:
eyJhbGciOiJSUzI1NiIsImtpZCI6Ii...  (chuỗi rất dài, ~900 ký tự)
```

JWT thực chất là **3 phần ghép lại bằng dấu chấm**, mỗi phần được Base64 encode:

```
Header . Payload . Signature
  │          │          │
  │          │          └── Chữ ký số của Google (để verify)
  │          └── Thông tin user (không mã hoá, ai cũng đọc được)
  └── Thuật toán ký (RS256)
```

**Phần Payload (giải mã ra) trông như thế này:**
```json
{
  "iss": "https://securetoken.google.com/docscanner-546de",
  "aud": "docscanner-546de",
  "auth_time": 1743799200,
  "user_id": "xK9mPq2Rf8hTzLm7...",
  "sub": "xK9mPq2Rf8hTzLm7...",      ← chính là UID
  "iat": 1743799200,                  ← thời điểm cấp token
  "exp": 1743802800,                  ← thời điểm hết hạn (iat + 1 giờ)
  "email": "abc@gmail.com",
  "firebase": { "sign_in_provider": "password" }
}
```

> ⚠️ **Quan trọng:** JWT Payload không được mã hoá — ai có token đều đọc được.  
> Bảo mật của JWT nằm ở **chữ ký (Signature)**: chỉ Google mới có private key để ký, nên không ai giả mạo được nội dung.

---

### 7.2 — JWT dùng để làm gì?

**Mục đích chính: Chứng minh "tôi là user hợp lệ" mà không cần gửi password lại mỗi lần.**

```
Không có JWT:
  Mỗi request phải: gửi email + password → Firebase xác thực → mới lấy data
  → Chậm, không an toàn

Có JWT:
  Đăng nhập 1 lần lấy JWT → Mỗi request gửi kèm JWT → Server tự verify
  → Nhanh, không cần Firebase mỗi lần
```

**Flow chuẩn khi dùng JWT đúng cách:**

```
[Login lần đầu]
App → Firebase: email + password
Firebase → App: UID + JWT (hạn 1 giờ) + Refresh Token

[Mỗi lần gọi API sau đó]
App → Backend: Authorization: Bearer <JWT>
Backend → Google: Verify JWT này có hợp lệ không?
Google → Backend: Hợp lệ, user_id = "xK9mPq2R..."
Backend → App: Trả data của user đó
```

---

### 7.3 — Refresh Token là gì? Tại sao cần?

**Vấn đề:** JWT chỉ có hạn **1 giờ**. Sau 1 giờ thì sao?

Nếu không có Refresh Token:
```
→ User phải đăng nhập lại mỗi giờ → Trải nghiệm rất tệ
```

**Refresh Token** giải quyết vấn đề này:
```
Refresh Token:
  - Chuỗi dài ngẫu nhiên (không phải JWT)
  - Hạn dùng: RẤT LÂU (mặc định Firebase: không hết hạn, trừ khi user đổi pass/logout)
  - Lưu an toàn trong bộ nhớ máy
  - Chỉ dùng để "đổi" lấy JWT mới khi JWT cũ hết hạn
```

**Flow tự động làm mới (Firebase SDK làm hết):**
```
┌─────────────────────────────────────────────────────┐
│  JWT còn hạn (< 1 giờ từ lúc cấp)                  │
│  → Dùng bình thường, không cần làm gì              │
└─────────────────────────────────────────────────────┘
          ↓ (sau ~55 phút, SDK tự detect JWT sắp hết hạn)
┌─────────────────────────────────────────────────────┐
│  Firebase SDK tự động:                              │
│  Gửi Refresh Token → Firebase Server               │
│  Firebase Server xác thực Refresh Token            │
│  → Cấp JWT mới (hạn tiếp 1 giờ)                   │
│  → App nhận JWT mới, dùng tiếp                     │
│  → User KHÔNG biết gì, KHÔNG cần làm gì           │
└─────────────────────────────────────────────────────┘
          ↓ (chỉ hết Refresh Token khi)
┌─────────────────────────────────────────────────────┐
│  - User bấm Đăng xuất (auth.signOut())             │
│  - User đổi password                              │
│  - Admin Firebase thu hồi token                   │
│  → Lúc này mới cần đăng nhập lại                  │
└─────────────────────────────────────────────────────┘
```

---

### 7.4 — Trong project DocScanner này, JWT/Refresh Token ở đâu?

**Câu trả lời thực tế: Firebase SDK tự xử lý hết, bạn không cần đụng vào.**

```kotlin
// Bạn chỉ viết:
val uid = auth.currentUser?.uid

// Firebase SDK nội bộ đã:
// 1. Lưu JWT + Refresh Token vào SharedPreferences (encrypted)
// 2. Tự refresh JWT khi gần hết hạn
// 3. Trả uid từ JWT đã verify rồi
```

**Trong project này, app KHÔNG dùng JWT khi gọi backend** — chỉ gửi UID dạng plain text:

```kotlin
// ApiService.kt - dòng 17
suspend fun getImages(
    @Query("userId") userId: String   // ← chỉ gửi UID, không gửi JWT
): Response<ImageResponse>
```

> **Đây là sự khác biệt so với hệ thống production:**
>
> | | Project DocScanner | Hệ thống production chuẩn |
> |---|---|---|
> | Xác thực UID | Backend tin tưởng UID từ app | Backend verify JWT với Google |
> | Độ bảo mật | ⭐⭐ (đủ cho đồ án) | ⭐⭐⭐⭐⭐ |
> | Phức tạp | Đơn giản | Phức tạp hơn |
> | Nguy cơ | Ai biết UID có thể giả mạo | Không thể giả mạo vì cần JWT hợp lệ |

Nếu muốn bảo mật chuẩn production, backend PHP cần thêm bước:
```php
// Verify JWT với Firebase Admin SDK
$verifiedIdToken = $auth->verifyIdToken($idToken);
$uid = $verifiedIdToken->claims()->get('sub');
```
Nhưng với đồ án sinh viên thì **không cần thiết**.

---

### 7.5 — Sơ đồ JWT + Refresh Token tổng hợp

```
                    ┌──────────────────────────────────┐
                    │         FIREBASE SERVER          │
                    │                                  │
                    │  Khi login thành công:           │
   email + pass ───►│  → Sinh JWT (hạn 1h)            │
                    │  → Sinh Refresh Token (lâu dài)  │
                    │                                  │
                    │  Khi nhận Refresh Token:         │
   Refresh Token ──►│  → Cấp JWT mới                  │
                    └──────────┬──────────────┬────────┘
                               │              │
                        JWT (1h)        Refresh Token
                               │              │
                               ▼              ▼
                    ┌──────────────────────────────────┐
                    │         FIREBASE SDK             │
                    │    (trong Android App)           │
                    │                                  │
                    │  - Lưu cả 2 vào bộ nhớ máy      │
                    │  - Tự động refresh JWT khi cần  │
                    │  - Expose: auth.currentUser?.uid │
                    └──────────────────────────────────┘
                               │
                          uid  │
                               ▼
                    ┌──────────────────────────────────┐
                    │     Code của bạn (Kotlin)        │
                    │                                  │
                    │  val uid = auth.currentUser?.uid │
                    │  ApiClient.api.getImages(uid)    │
                    └──────────────────────────────────┘
```

---

## 📊 Tổng kết: Ai làm gì?

| Thành phần | Vai trò | Dữ liệu lưu |
|---|---|---|
| **Firebase Auth** | Xác thực user, cấp UID | Email, UID, password (hash) |
| **Firebase SDK** | Thư viện trong app, giao tiếp với Firebase Server | Không lưu gì |
| **`google-services.json`** | File cấu hình kết nối Firebase | Chỉ là config, không phải secret |
| **`ApiService.kt`** | Định nghĩa các API call lên backend | - |
| **`ApiClient.kt`** | Cấu hình Retrofit + OkHttp để gọi API | - |
| **Backend PHP (CI3)** | Xử lý logic app (upload, lấy, xoá tài liệu) | Không lưu gì |
| **MySQL (XAMPP)** | Database chính của app | File metadata, `user_id` = Firebase UID |

---

## 🔄 Sơ đồ toàn bộ hệ thống

```
┌─────────────────────────────────────────────────────────────────┐
│                      ANDROID APP                                │
│                                                                 │
│   SettingsActivity.kt          MainActivity.kt                  │
│   ┌──────────────────┐         ┌─────────────────────────────┐  │
│   │ 1. Nhập email/pw │         │ 4. auth.currentUser?.uid    │  │
│   │ 2. signIn()      │─────┐   │ 5. ApiClient.api.getImages()│  │
│   │ 3. Nhận UID      │     │   │    → Retrofit → OkHttp      │  │
│   └──────────────────┘     │   └─────────────────────────────┘  │
└────────────────────────────│────────────────────┬───────────────┘
                             │                    │
                             ▼                    ▼
              ┌──────────────────┐    ┌───────────────────────────┐
              │  FIREBASE SERVER │    │   BACKEND (Ngrok → XAMPP) │
              │  (Google Cloud)  │    │                           │
              │                  │    │  PHP CodeIgniter 3        │
              │  - Xác thực user │    │  - GetUpload.php          │
              │  - Sinh UID      │    │  - Upload.php             │
              │  - Lưu: email,   │    │  - Delete.php             │
              │    UID, pw-hash  │    │         │                 │
              └──────────────────┘    │         ▼                 │
                                      │    MySQL Database          │
                                      │    images table           │
                                      │    user_id = Firebase UID  │
                                      └───────────────────────────┘
```

---

## 💡 Điểm quan trọng nhất cần nhớ khi bảo vệ

1. **Firebase KHÔNG lưu tài liệu** — chỉ lo xác thực người dùng
2. **UID là cầu nối** giữa Firebase Auth (xác thực) và MySQL (dữ liệu)
3. **App không tự gửi HTTP login** — Firebase SDK làm hết, bạn chỉ dùng callback
4. **Backend PHP không tự xác thực** — tin tưởng userId từ app gửi lên (đây là điểm yếu nhỏ, chấp nhận được cho đồ án)
5. **`google-services.json`** = địa chỉ Firebase, không phải mật khẩu

---

---

## ❓ Câu 8: UID được sinh ra ở đâu, gửi về thế nào, và mobile/web dùng nó ra sao?

### 8.1 — UID sinh ra ở đâu và gửi về khi nào?

**Đúng — Firebase Server sinh ra UID, và gửi về mỗi lần đăng nhập thành công.**

```
┌────────────────────────────────────────────────────────────────┐
│  LẦN ĐẦU ĐĂNG KÝ (createUserWithEmailAndPassword)             │
│                                                                │
│  App ──────► Firebase Server                                  │
│               → Kiểm tra email chưa tồn tại                  │
│               → Hash password                                  │
│               → Sinh UID mới: "xK9mPq2Rf8hT..."              │  ← chỉ sinh 1 lần duy nhất
│               → Lưu vào DB của Firebase                       │
│  App ◄──────  Trả về: UID + JWT + Refresh Token               │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│  MỖI LẦN ĐĂNG NHẬP SAU (signInWithEmailAndPassword)           │
│                                                                │
│  App ──────► Firebase Server                                  │
│               → Tìm user theo email                           │
│               → Kiểm tra password hash                        │
│               → UID đã có sẵn, KHÔNG sinh mới                │  ← UID không đổi
│  App ◄──────  Trả về: UID (giống hệt lần trước) + JWT mới    │
└────────────────────────────────────────────────────────────────┘
```

> **UID không bao giờ thay đổi** dù bạn đăng nhập lần 1, lần 100, hay đổi password.  
> Đây là điểm mấu chốt — vì UID dùng làm khoá trong MySQL, nếu UID đổi thì mất hết data.

---

### 8.2 — Mobile và Web dùng UID như thế nào?

```
                Firebase Server
                     │
          ┌──────────┴──────────┐
          │                     │
          ▼                     ▼
   [Android App]           [Web App]
   auth.currentUser?.uid   firebase.auth().currentUser.uid
          │                     │
          │   Đều là cùng 1 UID │
          └──────────┬──────────┘
                     │
                     ▼
            GET /api/images?userId=<UID>
                     │
                     ▼
              Backend PHP + MySQL
              SELECT * FROM images
              WHERE user_id = '<UID>'
```

Cả Mobile lẫn Web đều:
1. Đăng nhập → nhận UID từ Firebase
2. Dùng UID đó gửi lên **backend của bạn** (PHP + MySQL) để lấy data
3. **Firebase không liên quan đến bước lấy data** — nó chỉ lo xác thực

---

### 8.3 — Chi tiết code lấy dữ liệu qua UID trên Android (từng lớp)

Flow trong app có **4 lớp** phối hợp với nhau:

```
[1] MainActivity.kt       → Điều phối: lấy UID, gọi loadFromServer()
[2] ApiService.kt         → Khai báo endpoint GET
[3] ApiClient.kt          → Cấu hình HTTP client (Retrofit + OkHttp)
[4] ImageItem.kt          → Định nghĩa cấu trúc JSON response
```

---

#### 🔷 Lớp 1: `MainActivity.kt` — Điều phối chính

```kotlin
// Bước 1: Kiểm tra đã đăng nhập chưa (onResume chạy mỗi khi màn hình hiện)
override fun onResume() {
    super.onResume()
    val uid = auth.currentUser?.uid      // Lấy UID từ Firebase SDK
    //        └── auth = FirebaseAuth.getInstance()
    //            currentUser = null nếu chưa đăng nhập
    //            .uid = chuỗi UID nếu đã đăng nhập

    if (uid != null) {
        loadFromServer(uid)              // Có UID → lấy data
    } else {
        recyclerView.adapter = ScanAdapter(mutableListOf()) {}  // Chưa login → list rỗng
    }
}

// Bước 2: Gọi API, xử lý response
private fun loadFromServer(userId: String) {
    lifecycleScope.launch {             // Chạy bất đồng bộ (không block UI)
        try {
            // ★ Đây là dòng gọi API thực sự 
            val response = ApiClient.api.getImages(userId)
            //             └── ApiClient.api = Retrofit instance
            //                 .getImages(userId) = gọi GET /index.php/getupload/image?userId=<uid>

            if (response.isSuccessful && response.body()?.status == true) {
                val items = response.body()!!.data.map { img ->
                    // img là 1 ImageItem (id, description, imageUrl, uploadDate)
                    // Map sang ScanItem để hiển thị lên RecyclerView
                    val date = SimpleDateFormat("dd/MM/yyyy HH:mm")
                        .format(Date(img.uploadDate))
                    val fileName = img.imageUrl.substringAfterLast("/")
                    val fixedUrl = "${BuildConfig.BASE_URL}uploads/$fileName"

                    ScanItem(
                        fileName  = img.description?.ifEmpty { "Ảnh scan" } ?: "Ảnh scan",
                        fileInfo  = date,
                        imageUrl  = fixedUrl,
                        serverId  = img.id
                    )
                }
                recyclerView.adapter = ScanAdapter(items.toMutableList()) { ... }

            } else {
                // Server trả status = false → không có data hoặc lỗi
                recyclerView.adapter = ScanAdapter(mutableListOf()) {}
            }
        } catch (e: Exception) {
            // Không kết nối được server (ngrok tắt, mất mạng, ...)
            Toast.makeText(this@MainActivity, "Không kết nối được server", Toast.LENGTH_SHORT).show()
        }
    }
}
```

---

#### 🔷 Lớp 2: `ApiService.kt` — Khai báo endpoint

```kotlin
interface ApiService {

    // Khai báo: đây là 1 GET request
    @GET("index.php/getupload/image")
    suspend fun getImages(
        @Query("userId") userId: String   // userId được gắn vào URL như query param
    ): Response<ImageResponse>
    //          └── ImageResponse là data class chứa kết quả parse từ JSON
}

// URL thực tế được tạo ra:
// https://xxxx.ngrok.io/index.php/getupload/image?userId=xK9mPq2Rf8hT...
//                       └────── base URL ──────┘└── path ─┘└── query ──┘
```

---

#### 🔷 Lớp 3: `ApiClient.kt` — Cầu nối HTTP

```kotlin
object ApiClient {

    // OkHttp: thư viện HTTP cấp thấp, lo việc gửi/nhận gói tin
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("ngrok-skip-browser-warning", "true")  // Bỏ qua trang warning của ngrok
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)   // Tối đa 30s để kết nối
        .readTimeout(30, TimeUnit.SECONDS)       // Tối đa 30s để đọc response
        .build()

    // Retrofit: thư viện cấp cao hơn, tự convert JSON → Kotlin object
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)       // VD: "https://xxxx.ngrok.io/"
            .client(okHttpClient)                // Dùng OkHttp ở trên
            .addConverterFactory(GsonConverterFactory.create())  // Tự parse JSON
            .build()
            .create(ApiService::class.java)      // Tạo implementation của ApiService
    }
}
```

---

#### 🔷 Lớp 4: `ImageItem.kt` — Định nghĩa cấu trúc dữ liệu

```kotlin
// Retrofit + Gson dùng class này để parse JSON response tự động

data class ImageResponse(
    val status: Boolean,       // true/false
    val message: String,       // "Success" / "Fail"
    val data: List<ImageItem>  // Danh sách ảnh
)

data class ImageItem(
    val id: Int,               // ID trong MySQL
    val description: String?,  // Tên file (nullable)
    val imageUrl: String,      // Đường dẫn file trên server
    val uploadDate: Long       // Timestamp milliseconds
)

// JSON từ server trông như thế này:
// {
//   "status": true,
//   "message": "Success",
//   "data": [
//     { "id": 1, "description": "Scan tài liệu", "imageUrl": "uploads/abc.jpg", "uploadDate": 1743799200000 },
//     { "id": 2, "description": "", "imageUrl": "uploads/xyz.jpg", "uploadDate": 1743802800000 }
//   ]
// }
```

---

#### 🔷 Backend nhận UID và query MySQL như thế nào? (`GetUpload.php`)

```php
public function image() {
    // Nhận userId từ query param (?userId=xxx)
    $userId = $this->input->get_post('userId');    // = Firebase UID từ app gửi lên

    if (empty($userId)) {
        // Không có UID → từ chối
        return $this->response_json(['status' => false, 'message' => 'Missing userId']);
    }

    // Query MySQL: lấy tất cả ảnh của userId đó
    $data = $this->UploadImages_model->getAllImages($userId);
    // SQL thực tế: SELECT * FROM images WHERE user_id = '$userId' AND active = 1

    // Trả về JSON
    foreach ($data as $row) {
        $result[] = [
            'id'          => $row['id'],
            'description' => $row['description'],
            'imageUrl'    => $row['image_url'],
            'uploadDate'  => strtotime($row['created_at']) * 1000,  // → milliseconds cho Android
        ];
    }
    return $this->response_json(['status' => true, 'data' => $result]);
}
```

---

### 8.4 — Sơ đồ hoàn chỉnh: Từ UID đến RecyclerView

```
Firebase SDK
auth.currentUser?.uid = "xK9mPq2R..."
        │
        │ uid (String)
        ▼
MainActivity.loadFromServer(uid)
        │
        │ gọi suspend fun
        ▼
ApiClient.api.getImages("xK9mPq2R...")     ← Retrofit + OkHttp
        │
        │ HTTP GET (qua ngrok tunnel)
        │ URL: https://xxxx.ngrok.io/index.php/getupload/image?userId=xK9mPq2R...
        ▼
GetUpload.php (CodeIgniter 3)
        │
        │ SELECT * FROM images WHERE user_id = 'xK9mPq2R...' AND active = 1
        ▼
MySQL Database
        │
        │ Trả về rows: [{id, description, image_url, created_at}, ...]
        ▼
GetUpload.php → json_encode() → gửi về
        │
        │ JSON string
        ▼
Gson parse → ImageResponse { data: List<ImageItem> }
        │
        │ List<ImageItem>
        ▼
MainActivity map → List<ScanItem>
        │
        ▼
ScanAdapter → RecyclerView → Hiển thị trên màn hình
```

---

*File này được tạo từ cuộc trò chuyện giải thích Firebase trong project DocScanner.*
