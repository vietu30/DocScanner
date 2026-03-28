# 🔐 Firebase Auth — Email/Password trong SettingsActivity

---

## 0. Firebase có phải là API không?

> **Đúng! Firebase là một tập hợp các API do Google cung cấp.**

---

### ❓ Câu hỏi: Mình đăng ký app trên Firebase, nó cấp cấu hình, mình nhét vào file JSON, khi chạy app báo lên Firebase — đúng không?

### ✅ Bạn hiểu đúng **90%**! Chỉ cần chỉnh lại 1 điểm:

| Điểm bạn nói | Đúng/Sai | Giải thích |
|---|---|---|
| Firebase là API | ✅ Đúng | Firebase Auth, Firestore... đều là REST API do Google host |
| Đăng ký app → nhận config | ✅ Đúng | `google-services.json` chứa `project_id`, `api_key`... |
| App báo lên Firebase **qua file JSON** | ⚠️ **Gần đúng** | `google-services.json` KHÔNG gửi lên server — nó được **đọc lúc build** (compile time) để nhúng API key vào bên trong file `.apk`. Khi chạy, app dùng thông tin đã nhúng sẵn đó để kết nối |
| Firebase so sánh key rồi cấp tài nguyên | ✅ Đúng | Firebase nhận request kèm API key → xác minh → cho phép hoặc từ chối |

---

### 🔑 `google-services.json` hoạt động khi nào?

```
Lúc BUILD (Android Studio compile):
    google-services.json ──► Plugin đọc → nhúng API key vào trong file .apk

Lúc CHẠY app (runtime):
    App dùng API key đã nhúng sẵn → gửi request lên Firebase server
    Firebase kiểm tra key → cấp tài nguyên (Auth, Database...)
```

> `google-services.json` như tờ **đơn đăng ký** — nộp 1 lần khi xây nhà (build).
> Còn **chìa khóa** (API key) thì được đúc ra từ đơn đó và gắn vào nhà luôn.

---

## 1. Firebase lưu gì khi dùng Email/Password?

**Đúng! Firebase Authentication lưu thông tin người dùng.** Nhưng chỉ lưu thông tin xác thực, không lưu dữ liệu ảnh/document.

| Firebase lưu | Firebase KHÔNG lưu |
|---|---|
| UID (chuỗi duy nhất định danh user) | Ảnh scan của bạn |
| Email | Document PDF |
| Password hash (mã hóa, không xem được) | Metadata file |
| Ngày tạo tài khoản | Bất kỳ dữ liệu app nào |

Dữ liệu ảnh/document được lưu trong MySQL (XAMPP) của bạn, liên kết qua `user_id = UID`.

---

## 2. Luồng đăng nhập Email/Password

```
Bước 1 — User nhập email + password → bấm "Đăng nhập"

Bước 2 — App gọi Firebase Auth API:
    auth.signInWithEmailAndPassword(email, password)

Bước 3 — Firebase kiểm tra:
    ├── Email có tồn tại trong hệ thống không?
    ├── Password có khớp với hash đã lưu không?
    └── Nếu OK → Trả về FirebaseUser

Bước 4 — App nhận FirebaseUser:
    auth.currentUser?.uid  ← UID dùng để gọi API backend
```

---

## 3. Luồng đăng ký tài khoản mới

```
Bước 1 — User nhập email + password + confirm password → bấm "Tạo tài khoản"

Bước 2 — App kiểm tra local:
    ├── Password == confirmPassword?  (nếu không → báo lỗi ngay, không gọi Firebase)
    └── Password >= 6 ký tự?

Bước 3 — App gọi Firebase Auth API:
    auth.createUserWithEmailAndPassword(email, password)

Bước 4 — Firebase tạo user mới:
    ├── Kiểm tra email chưa được đăng ký
    ├── Hash password và lưu vào hệ thống
    ├── Tạo UID duy nhất cho user
    └── Trả về FirebaseUser (tự động đăng nhập luôn)
```

---

## 4. Toggle UI: Login ↔ Register

App có 2 mode, bấm nút để chuyển:

```
MODE LOGIN (mặc định):          MODE REGISTER:
┌─────────────────────┐         ┌─────────────────────┐
│ Email               │         │ Email               │
│ Mật khẩu           │  ──►    │ Mật khẩu           │
│ [ Đăng nhập ]       │         │ Xác nhận mật khẩu  │
│ [ Tạo TK mới ]      │         │ [ Tạo tài khoản ]   │
└─────────────────────┘         │ [ ← Quay lại ]      │
                                └─────────────────────┘
```

Logic trong `SettingsActivity.kt`:
- `isRegisterMode = false` → Login mode
- `isRegisterMode = true` → Register mode
- `layoutConfirmPassword.visibility` = GONE/VISIBLE để ẩn/hiện ô xác nhận

---

## 5. Giải thích code `SettingsActivity.kt`

### 5.1 Đăng nhập
```kotlin
auth.signInWithEmailAndPassword(email, password)
    .addOnSuccessListener { updateUI() }
    .addOnFailureListener { Toast lỗi }
```

### 5.2 Đăng ký
```kotlin
// Kiểm tra local trước
if (password != confirm) { → Toast lỗi; return }
if (password.length < 6) { → Toast lỗi; return }

// Gọi Firebase
auth.createUserWithEmailAndPassword(email, password)
    .addOnSuccessListener { updateUI() }  // tự đăng nhập luôn sau khi tạo
```

### 5.3 Lấy UID sau login
```kotlin
val uid = auth.currentUser?.uid   // ← LocalcAche, không cần internet
```

---

## 6. Khi app yêu cầu dữ liệu, Firebase xử lý user ID như thế nào?

### ❓ Câu hỏi: Mình sẽ yêu cầu về `user_id` đúng không? Vậy Firebase xử lý ra sao?

**Đúng! Và đây là điều quan trọng cần hiểu rõ:**

**Firebase KHÔNG phải là nơi lưu dữ liệu ảnh của bạn** — Firebase chỉ làm nhiệm vụ **xác định "người dùng đó là ai"** rồi trả lại `uid`. Còn việc lấy dữ liệu (ảnh, documents...) là công việc của **backend PHP (XAMPP) + MySQL**.

---

### Luồng hoàn chỉnh khi app yêu cầu dữ liệu:

```
1. App đã login xong → Firebase đang giữ session
           ↓
2. App lấy uid từ Firebase (LOCAL, không cần request lên mạng):
   val uid = FirebaseAuth.getInstance().currentUser?.uid
           ↓
3. App gửi uid đó lên backend PHP của bạn:
   GET /api/images?user_id=abc123XYZ789
           ↓
4. Backend PHP truy vấn MySQL:
   SELECT * FROM tbl_upload_images WHERE user_id = 'abc123XYZ789'
           ↓
5. Backend trả về danh sách ảnh của đúng user đó
```

---

### Firebase có tham gia vào bước lấy dữ liệu không?

| Bước | Firebase tham gia? | Giải thích |
|---|---|---|
| Đăng nhập / Đăng ký | ✅ Có | Firebase xác thực, cấp `uid` |
| Lấy `uid` sau login | ✅ Có (local) | Đọc từ cache, **không cần internet** |
| Gọi API lấy danh sách ảnh | ❌ Không | App tự gửi `uid` lên backend PHP |
| Lưu ảnh vào MySQL | ❌ Không | Backend PHP tự xử lý |

> **Tóm lại:** Firebase đóng vai "cổng bảo mật" — chỉ để biết *ai đang dùng app*. Sau khi biết rồi (`uid`), mọi tương tác với dữ liệu đều đi qua backend PHP của bạn.

```
Firebase UID:  "abc123XYZ789"
                    ↕
tbl_upload_images.user_id = "abc123XYZ789"
```

---

### Tại sao không dùng Firebase để lưu dữ liệu luôn?

Bạn **có thể** dùng Firestore/Firebase Storage thay cho MySQL + PHP, nhưng dự án này chọn XAMPP vì:
- Kiểm soát hoàn toàn cấu trúc database
- Dễ debug hơn khi học
- Không phụ thuộc quota/pricing của Firebase
