# 🔐 Firebase Authentication – Cách hoạt động

## Firebase là gì?

Firebase Authentication là **dịch vụ đăng nhập của Google** — thay vì tự xây hệ thống login, mình dùng Firebase để xác thực người dùng.

---

## Kết nối như thế nào?

```
Mobile (Android)           Web (Browser)
google-services.json  ←→  firebaseConfig trong login.html
        │                         │
        └──────────┬──────────────┘
                   ▼
         Firebase Server (Google Cloud)
         → 1 bảng user duy nhất cho cả 2 nền tảng
```

> Cả mobile lẫn web đều trỏ vào **cùng 1 project Firebase** `docscanner-546de`  
> → Tạo tài khoản trên mobile, đăng nhập web cùng email/password là vào được ngay.

---

## Flow đăng nhập

```
1. Người dùng nhập email + password

2. App gửi lên Firebase:
   signInWithEmailAndPassword(email, password)

3. Firebase kiểm tra:
   ✔ Email có tồn tại không?
   ✔ Password có khớp không? (Firebase tự so sánh hash)

4. Nếu đúng → Firebase trả về UID (chuỗi định danh duy nhất)

5. App dùng UID đó để lấy dữ liệu từ server:
   GET /api/images?userId=<UID>
```

---

## Flow đăng ký

```
1. Người dùng nhập email + password

2. App gọi:
   createUserWithEmailAndPassword(email, password)

3. Firebase tự động:
   → Kiểm tra email chưa bị trùng
   → Hash password và lưu (không ai xem được plain text)
   → Sinh ra UID mới
   → Tự đăng nhập luôn sau đó
```

---

## Firebase lưu gì?

| Firebase lưu ✅ | Firebase KHÔNG lưu ❌ |
|---|---|
| Email | File PDF / ảnh scan |
| UID (định danh duy nhất) | Metadata tài liệu |
| Password (đã hash, không xem được) | Bất kỳ dữ liệu app nào |
| Ngày tạo, lần đăng nhập cuối | |

> File PDF và metadata lưu trong **MySQL (XAMPP)**, liên kết với Firebase qua `user_id = UID`

---

## Vai trò của Firebase trong toàn bộ hệ thống

```
Firebase = "Cổng bảo mật"

   Firebase Auth ──► "Người này là ai?" ──► Trả về UID
                                                │
                                                ▼
                               Backend PHP (CI3) + MySQL
                               "Dữ liệu của người đó là gì?"
```

Firebase **chỉ làm 1 việc**: xác định đúng người dùng và cấp UID.  
Mọi thao tác với dữ liệu (upload, xem, xoá) đều do backend CI3 + MySQL xử lý.

---

## Tóm tắt 1 câu

> Firebase Auth = **"Chìa khoá"** để mở cửa vào hệ thống.  
> Còn bên trong nhà (dữ liệu, file) thì do **XAMPP + MySQL** quản lý.
