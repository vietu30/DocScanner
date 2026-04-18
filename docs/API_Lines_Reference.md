# Vị Trí Gọi API Trong Code

---

## A. Firebase Authentication API

Tất cả gọi qua SDK `com.google.firebase.auth.FirebaseAuth`

### SettingsActivity.kt

| Dòng | Code | Mô tả |
|------|------|-------|
| **23** | `auth = FirebaseAuth.getInstance()` | Khởi tạo Firebase Auth instance |
| **46** | `auth.createUserWithEmailAndPassword(email, password)` | **Đăng ký** tài khoản mới → gửi lên Firebase Server |
| **56** | `auth.signInWithEmailAndPassword(email, password)` | **Đăng nhập** → xác thực với Firebase Server |
| **85** | `auth.signOut()` | **Đăng xuất** → xoá session local |
| **94** | `auth.currentUser` | **Kiểm tra** trạng thái đăng nhập (đọc local, không gọi network) |

### MainActivity.kt

| Dòng | Code | Mô tả |
|------|------|-------|
| **56** | `auth.currentUser?.uid` | Lấy UID để gọi CI3 API `getImages` |
| **152** | `auth.currentUser?.uid` | Lấy UID để gọi CI3 API `deleteImage` |

### PreviewActivity.kt

| Dòng | Code | Mô tả |
|------|------|-------|
| **113** | `auth.currentUser?.uid ?: return` | Lấy UID để gọi CI3 API `uploadImage`, bỏ qua nếu chưa login |

---

## B. CI3 Backend API (Retrofit)

Tất cả gọi qua `ApiClient.api` (Retrofit → OkHttp → HTTP)

### Khai báo endpoint — `network/ApiService.kt`

| Dòng | Code | Mô tả |
|------|------|-------|
| **15** | `@GET("index.php/getupload/image")` | Endpoint GET danh sách ảnh |
| **24** | `@POST("index.php/upload/image")` | Endpoint POST upload file |
| **33** | `@POST("index.php/delete/image")` | Endpoint POST xoá ảnh |

### Gọi thực tế

| Dòng | File | Code | Mô tả |
|------|------|------|-------|
| **69** | `MainActivity.kt` | `ApiClient.api.getImages(userId)` | GET danh sách tài liệu của user |
| **124** | `PreviewActivity.kt` | `ApiClient.api.uploadImage(userIdBody, filePart, descBody)` | POST upload file lên server |
| **155** | `MainActivity.kt` | `ApiClient.api.deleteImage(userId, id)` | POST xoá tài liệu (soft delete) |

---

## C. Tổng hợp — Thứ tự gọi

```
SettingsActivity                 PreviewActivity              MainActivity
      │                                │                           │
      ▼                                │                           │
 ① auth.createUser...()               │                           │
    hoặc                               │                           │
 ② auth.signIn...()                    │                           │
      │                                │                           │
      │  ┌─ Firebase Server ──┐        │                           │
      │  │ Xác thực, trả UID  │        │                           │
      │  └─────────────────────┘        │                           │
      │                                │                           │
      ▼                                │                           ▼
 (Đăng nhập xong, quay về Main)        │                    ③ api.getImages(uid)
                                       │                       ↓ GET /getupload/image
                                       │                       ↓ Server trả danh sách
                                       │                           │
                                       │                           │ (User scan ảnh mới)
                                       ▼                           │
                                ④ api.uploadImage(uid, file, desc) │
                                   ↓ POST /upload/image            │
                                   ↓ Server lưu file + insert DB   │
                                       │                           │
                                       │  (Quay về Main)           ▼
                                       │                    ③ api.getImages(uid)
                                       │                       (load lại danh sách)
                                       │                           │
                                       │                           │ (User long-press → Xoá)
                                       │                           ▼
                                       │                    ⑤ api.deleteImage(uid, id)
                                       │                       ↓ POST /delete/image
                                       │                       ↓ Server set active=0
                                       │                           │
 ⑥ auth.signOut()                      │                           │
    (Đăng xuất)                        │                           │
```
