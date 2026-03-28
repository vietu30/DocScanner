# 🔐 Firebase Auth — Cách hoạt động trong SettingsActivity

---

## 0. Firebase có phải là API không?

> **Đúng! Firebase là một tập hợp các API do Google cung cấp.**

---

### ❓ Câu hỏi: Mình đăng ký app trên Firebase, nó cấp Web Client ID, mình nhét ID đó vào code, khi chạy app báo lên Firebase qua file JSON, Firebase so sánh ID rồi cấp tài nguyên — đúng không?

### ✅ Bạn hiểu đúng **90%**! Chỉ cần chỉnh lại 1 điểm:

| Điểm bạn nói | Đúng/Sai | Giải thích |
|---|---|---|
| Firebase là API | ✅ Đúng | Firebase Auth, Firestore... đều là REST API do Google host |
| Đăng ký app → nhận Web Client ID | ✅ Đúng | ID này định danh app của bạn với hệ thống Google |
| Viết ID vào code | ✅ Đúng | `WEB_CLIENT_ID` trong `SettingsActivity.kt` |
| App báo lên Firebase **qua file JSON** | ⚠️ **Gần đúng** | `google-services.json` KHÔNG gửi lên server — nó được **đọc lúc build** (compile time) để nhúng API key vào bên trong file `.apk`. Khi chạy, app dùng thông tin đã nhúng sẵn đó để kết nối |
| Firebase so sánh ID rồi cấp tài nguyên | ✅ Đúng | Firebase nhận request kèm API key → xác minh → cho phép hoặc từ chối |

---

### 🔑 Điểm quan trọng: `google-services.json` hoạt động khi nào?

```
Lúc BUILD (Android Studio compile):
    google-services.json ──► Plugin đọc → nhúng API key vào trong file .apk

Lúc CHẠY app (runtime):
    App dùng API key đã nhúng sẵn → gửi request lên Firebase server
    Firebase kiểm tra key → cấp tài nguyên (Auth, Database...)
```

> Tưởng tượng `google-services.json` như tờ **đơn đăng ký** — bạn nộp 1 lần khi xây nhà (build).  
> Còn **chìa khóa** (API key) thì được đúc ra từ đơn đó và gắn vào nhà luôn để dùng hàng ngày.

---

### 🌐 Firebase hoạt động như thế nào về mặt kỹ thuật?

```
App Android
    │
    │  HTTPS request tới: https://identitytoolkit.googleapis.com/...
    │  Kèm theo: API key (từ google-services.json)
    ↓
Firebase Server (của Google, trên Internet)
    │
    ├── Kiểm tra API key có hợp lệ không
    ├── Xử lý đăng nhập Google (verify idToken)
    └── Trả về FirebaseUser (UID, email, tên...)
    ↓
App nhận được thông tin user
```

> Đây là lý do app **cần INTERNET permission** — vì Firebase Auth hoạt động hoàn toàn qua Internet.

---

---

## 0.5 Tại sao app biết đăng nhập qua Google? Có cần tài khoản/mật khẩu không?

### ❓ Câu hỏi: App mình biết đăng nhập qua Google là do đâu? Mình có phải định nghĩa user/password không? Phải request cái gì thì Firebase mới biết user này đang đăng nhập?

### ✅ Trả lời:

**Không cần định nghĩa tài khoản/mật khẩu** — vì bạn đang dùng **"đăng nhập ủy quyền"** (OAuth 2.0). Mật khẩu do Google giữ, app của bạn không bao giờ nhìn thấy.

---

### App "biết" dùng Google là do... bạn viết code nói ra điều đó!

```kotlin
// Dòng này = "Tôi muốn dùng Google Sign-In"
val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestIdToken(WEB_CLIENT_ID)
    .requestEmail()
    .build()
```

Bạn có thể thay bằng Facebook, GitHub, Apple... tùy thư viện bạn tích hợp.  
Firebase Auth hỗ trợ nhiều provider, bạn chọn cái nào thì cấu hình cái đó.

---

### Luồng request để Firebase "biết" user này đã đăng nhập:

```
Bước 1 — App gửi yêu cầu đến Google (KHÔNG phải Firebase):
    googleSignInClient.signInIntent
    → Mở popup Google → User chọn tài khoản → Google xác minh mật khẩu

Bước 2 — Google trả về idToken cho app:
    account.idToken  ← Bằng chứng "user này đã xác minh với Google"

Bước 3 — App gửi idToken lên Firebase (request thật sự):
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    auth.signInWithCredential(credential)
    ↑ Đây mới là lúc app "nói chuyện" với Firebase Auth

Bước 4 — Firebase kiểm tra idToken:
    ├── Chữ ký của Google có hợp lệ không?
    ├── Token có hết hạn chưa?
    └── Nếu OK → Tạo/cập nhật user trong Firebase → Trả về FirebaseUser

Bước 5 — App nhận FirebaseUser:
    auth.currentUser  ← Từ đây trở đi app biết ai đang đăng nhập
```

---

### So sánh: Đăng nhập truyền thống vs Google Sign-In

| | Email/Password truyền thống | Google Sign-In |
|---|---|---|
| Mật khẩu lưu ở đâu | Server của bạn (nguy hiểm nếu bị hack) | Google giữ (bạn không bao giờ thấy) |
| Bạn phải làm gì | Tạo form, hash password, lưu DB | Chỉ cần đọc idToken từ Google |
| Độ bảo mật | Phụ thuộc cách bạn code | Chuẩn bảo mật của Google (rất cao) |
| Firebase lưu gì | email + password hash | Chỉ lưu UID + email (không có password) |

---

### Firebase lưu user ở đâu?

Sau khi login lần đầu, Firebase **tự động tạo một user** trong hệ thống của họ:

```
Firebase Console → Authentication → Users
┌────────────────────────────────────────────────────┐
│ UID                │ Email             │ Provider   │
├────────────────────────────────────────────────────┤
│ abc123XYZ789...    │ user@gmail.com    │ google.com │
└────────────────────────────────────────────────────┘
```

Bạn **không cần tự tạo bảng users** cho Firebase Auth — Firebase tự quản lý.  
`user.uid` chính là cái UID đó, dùng để liên kết với database của mình (`tbl_upload_images.user_id`).

---



```
User nhấn "Đăng nhập với Google"
        ↓
GoogleSignInClient mở popup chọn tài khoản (của Google)
        ↓
Google xác thực tài khoản → trả về idToken
        ↓
App gửi idToken lên Firebase Auth
        ↓
Firebase xác nhận idToken hợp lệ → trả về FirebaseUser
        ↓
App lấy UID, email, tên, avatar từ FirebaseUser → cập nhật UI
```

---

## 2. idToken là gì? Tại sao lấy được key đăng nhập?

### Câu hỏi: Sao app lại có thể "biết" người dùng là ai?

Khi người dùng chọn tài khoản Google trong popup, Google làm 3 việc:

1. **Xác minh** người dùng thật sự sở hữu tài khoản đó (qua mật khẩu/session)
2. **Tạo ra** một `idToken` — một chuỗi mã hóa chứa thông tin người dùng
3. **Trả về** `idToken` đó cho app

```
idToken trông như thế này (JWT):
eyJhbGciOiJSUzI1NiIsImtpZCI6Ij...  (rất dài)

Bên trong mã hóa có:
{
  "sub": "10234567890",        ← UID duy nhất của user
  "email": "user@gmail.com",
  "name": "Nguyen Van A",
  "picture": "https://...",
  "exp": 1743000000           ← Thời gian hết hạn (1 giờ)
}
```

### Tại sao app TIN TƯỞNG idToken này?

Vì idToken được **Google ký bằng private key riêng của họ**. Firebase Auth nhận token này và **xác minh chữ ký** đó bằng public key của Google.

```
Google ký token  →  Firebase kiểm tra chữ ký  →  Nếu hợp lệ = tin tưởng
```

> Giống như tờ tiền có hình watermark — không ai giả mạo được vì chỉ Ngân hàng Nhà nước mới có máy in đó.

---

## 3. Web Client ID — Tại sao cần nó?

```kotlin
private val WEB_CLIENT_ID = "844235246700-kn0bh775r08a76pb6v3eqti5pi3phtcn.apps.googleusercontent.com"
```

Khi app yêu cầu Google đăng nhập, Google cần biết:
> *"App này là ai? Có được phép yêu cầu thông tin user không?"*

`WEB_CLIENT_ID` là **"thẻ căn cước"** của app, được tạo ra khi bạn đăng ký app trên Firebase Console. Google dùng ID này để:
- Xác minh request đến từ đúng app
- Biết phải trả idToken về cho project Firebase nào
- Hiển thị tên app đúng trong popup đăng nhập

---

## 4. Giải thích từng đoạn code trong `SettingsActivity.kt`

### 4.1 Cấu hình Google Sign-In
```kotlin
val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
    .requestIdToken(WEB_CLIENT_ID)  // ← Yêu cầu Google trả về idToken
    .requestEmail()                  // ← Yêu cầu lấy email
    .build()
googleSignInClient = GoogleSignIn.getClient(this, gso)
```
Đây là bước **cấu hình** — nói với Google SDK: "Khi đăng nhập, tôi cần idToken và email".

### 4.2 Mở popup chọn tài khoản
```kotlin
binding.btnLogin.setOnClickListener {
    signInLauncher.launch(googleSignInClient.signInIntent)
}
```
`signInIntent` = Intent mở màn hình chọn tài khoản của Google (do Google SDK xử lý, không phải bạn tự vẽ).

### 4.3 Nhận kết quả từ Google
```kotlin
private val signInLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
    val account = task.getResult(ApiException::class.java)
    firebaseAuthWithGoogle(account.idToken!!)  // ← idToken lấy ở đây
}
```
Sau khi user chọn tài khoản, Google gửi kết quả về đây. `account.idToken` chính là chuỗi JWT đã nói ở mục 2.

### 4.4 Xác thực với Firebase
```kotlin
private fun firebaseAuthWithGoogle(idToken: String) {
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    auth.signInWithCredential(credential)
        .addOnSuccessListener {
            updateUI()  // ← Login thành công
        }
}
```
`credential` = "Gói thông tin" gửi lên Firebase để xác thực.
Firebase nhận idToken → kiểm tra chữ ký Google → xác nhận thật → tạo `FirebaseUser`.

### 4.5 Lấy thông tin user sau login
```kotlin
val user = auth.currentUser
user.displayName   // Tên
user.email         // Email
user.photoUrl      // URL ảnh đại diện
user.uid           // ← Chuỗi ID duy nhất, dùng làm userId khi gọi API
```

> **`user.uid`** quan trọng nhất — đây là `userId` sẽ gửi kèm khi gọi API PHP để phân biệt ảnh của từng người.

---

## 5. Kết nối với API Backend (bước tiếp theo)

Sau khi login xong, `user.uid` sẽ được dùng như thế này khi gọi API:

```
GET /api/images?userId=<user.uid>
POST /api/images/upload + userId=<user.uid>
```

Database `tbl_upload_images` lưu `user_id = user.uid` → mỗi user chỉ thấy ảnh của mình.

```
Firebase UID:  "abc123XYZ789"
                    ↕
tbl_upload_images.user_id = "abc123XYZ789"
```
