# Giải Thích Chi Tiết Code PHP — 3 API Controller + Model

> Tài liệu này đọc **từng dòng code PHP** thực tế trong project và giải thích ý nghĩa.

---

## Cấu trúc file PHP

```
htdocs/webapi/application/
├── controllers/
│   ├── GetUpload.php     ← API lấy danh sách ảnh
│   ├── Upload.php        ← API upload file
│   └── Delete.php        ← API xoá ảnh
├── models/
│   └── UploadImages_model.php   ← Layer nói chuyện với MySQL
└── config/
    └── database.php      ← Cấu hình kết nối MySQL
```

---

## 1. GetUpload.php — Lấy Danh Sách Ảnh

**URL**: `GET /index.php/getupload/image?userId=<uid>`

```php
<?php
defined('BASEPATH') OR exit('No direct script access allowed');
// ↑ Dòng bảo vệ: không cho ai truy cập trực tiếp file này bằng URL
//   Phải đi qua index.php của CI3 mới được

class GetUpload extends CI_Controller
//     ↑ Tên class = tên file = tên route
//       extends CI_Controller = kế thừa class cha của CI3
//       → tự động có: $this->input, $this->load, $this->db
{
    public function image()
    //                ↑ Tên hàm = phần URL sau tên controller
    //                  URL: /getupload/image → class GetUpload, function image
    {
        // ——— BƯỚC 1: Lấy tham số từ request ———
        $userId = $this->input->get_post('userId');
        //        ↑ $this->input = thư viện Input của CI3
        //        ↑ get_post()   = lấy param từ GET hoặc POST đều được
        //        ↑ 'userId'     = tên param (từ ?userId=xxx trong URL)
        //
        //  Ví dụ: URL là /getupload/image?userId=xK9mPq2R...
        //  → $userId = "xK9mPq2R..."

        // ——— BƯỚC 2: Validate ———
        if (empty($userId)) {
            return $this->response_json([
                'status'  => false,
                'message' => 'Missing userId',
                'data'    => []
            ]);
            // Nếu không có userId → trả lỗi ngay, không query DB
        }

        // ——— BƯỚC 3: Gọi Model để query Database ———
        $data = $this->UploadImages_model->getAllImages($userId);
        //      ↑ CI3 tự động load model UploadImages_model
        //        (nếu đã config autoload, hoặc gọi $this->load->model)
        //      ↑ getAllImages() = hàm mình tự viết trong Model
        //      ↑ Trả về: mảng các dòng dữ liệu, hoặc false nếu rỗng

        // ——— BƯỚC 4: Xử lý kết quả ———
        if ($data) {
            // Có dữ liệu → format lại cho đẹp trước khi trả về
            $result = array();
            foreach ($data as $row) {
                // Mỗi $row là 1 dòng trong bảng MySQL, ví dụ:
                // $row = ['id'=>1, 'description'=>'ABC', 'image_url'=>'http://...', 'created_at'=>'2026-04-05 15:00:00']

                $result[] = array(
                    'description' => $row['description'],
                    //  ↑ Tên file người dùng đặt, ví dụ "HopDong_thang4"

                    'imageUrl' => $row['image_url'],
                    //  ↑ URL đầy đủ đến file, ví dụ "http://xxx/uploads/abc123.pdf"
                    //  ↑ Đổi tên key từ 'image_url' (DB style) → 'imageUrl' (camelCase cho Android)

                    'uploadDate' => strtotime($row['created_at']) * 1000,
                    //  ↑ strtotime() = chuyển "2026-04-05 15:00:00" → 1775401200 (giây)
                    //  ↑ * 1000      = nhân thành milliseconds (Android dùng ms, PHP dùng seconds)
                );
            }

            return $this->response_json([
                'status'  => true,
                'message' => 'Success',
                'data'    => $result
            ]);
        } else {
            // Không có dữ liệu (user chưa upload gì, hoặc đã xoá hết)
            return $this->response_json([
                'status'  => false,
                'message' => 'Fail',
                'data'    => []
            ]);
        }
    }

    // ——— HÀM PHỤ: Trả JSON response ———
    private function response_json($data)
    {
        header('Content-Type: application/json');
        // ↑ Báo cho client biết: "response này là JSON, không phải HTML"

        echo json_encode($data);
        // ↑ Chuyển mảng PHP → chuỗi JSON
        //   ví dụ: ['status'=>true] → {"status":true}

        exit;
        // ↑ Dừng script ngay, không chạy gì thêm
    }
}
```

### Response mẫu:

```json
{
    "status": true,
    "message": "Success",
    "data": [
        {
            "description": "HopDong_thang4",
            "imageUrl": "http://xxx.ngrok.dev/webapi/uploads/a1b2c3.pdf",
            "uploadDate": 1712345678000
        }
    ]
}
```

---

## 2. Upload.php — Upload File Lên Server

**URL**: `POST /index.php/upload/image` (multipart/form-data)

```php
<?php
defined('BASEPATH') OR exit('No direct script access allowed');

class Upload extends CI_Controller
{
    public function image()
    {
        // ——— BƯỚC 1: Lấy tham số text từ request ———
        $userId = $this->input->get_post('userId');
        //  ↑ Firebase UID, gửi từ app Android trong phần multipart text

        $description = $this->input->get_post('description');
        //  ↑ Tên file / mô tả, do user nhập

        // ——— BƯỚC 2: Validate userId ———
        if (empty($userId)) {
            return $this->response_json([
                'status'  => false,
                'message' => 'Missing userId'
            ]);
        }

        // ——— BƯỚC 3: Kiểm tra có file đính kèm không ———
        if (!isset($_FILES['image'])) {
        //  ↑ $_FILES = biến PHP toàn cục chứa thông tin file upload
        //  ↑ ['image'] = tên field file (app Android gửi: name="image")
        //
        //  $_FILES['image'] chứa:
        //  [
        //    'name'     => 'scan_001.jpg',     ← tên file gốc
        //    'type'     => 'image/jpeg',        ← MIME type
        //    'tmp_name' => '/tmp/phpXXXXXX',    ← file tạm trên server
        //    'error'    => 0,                   ← mã lỗi (0 = OK)
        //    'size'     => 245760               ← kích thước (bytes)
        //  ]

            return $this->response_json([
                'status'  => false,
                'message' => 'No image uploaded'
            ]);
        }

        // ——— BƯỚC 4: Cấu hình upload ———
        $config['upload_path']   = './uploads/';
        //  ↑ Thư mục lưu file: /htdocs/webapi/uploads/
        //  ↑ './' = thư mục hiện tại (webapi/)

        $config['allowed_types'] = 'jpg|jpeg|png|webp';
        //  ↑ Chỉ cho phép upload các loại file này
        //  ↑ Nếu gửi file .exe hoặc .php → bị từ chối (bảo mật)

        $config['max_size']      = 8 * 1024 * 1024;
        //  ↑ Giới hạn kích thước file: 8MB
        //  ↑ 8 * 1024 * 1024 = 8,388,608 bytes

        $config['encrypt_name']  = TRUE;
        //  ↑ Đổi tên file thành chuỗi ngẫu nhiên
        //  ↑ "scan_001.jpg" → "a7f3e9b2c1d4.jpg"
        //  ↑ Lý do: tránh trùng tên, tránh tấn công path traversal

        // ——— BƯỚC 5: Load thư viện Upload của CI3 ———
        $this->load->library('upload', $config);
        //  ↑ $this->load = bộ loader của CI3
        //  ↑ library('upload') = nạp thư viện Upload có sẵn của CI3
        //  ↑ $config = truyền cấu hình vừa tạo ở trên

        // ——— BƯỚC 6: Thực hiện upload ———
        if (!$this->upload->do_upload('image')) {
        //   ↑ $this->upload = thư viện Upload vừa load
        //   ↑ do_upload('image') = lấy file từ $_FILES['image']
        //     → validate (type, size)
        //     → move từ /tmp/ sang ./uploads/
        //     → đổi tên (encrypt)
        //
        //   Trả về: true nếu thành công, false nếu lỗi

            return $this->response_json([
                'status'  => false,
                'message' => $this->upload->display_errors()
                //  ↑ Lấy thông báo lỗi chi tiết
                //    ví dụ: "The filetype you are attempting to upload is not allowed."
            ]);
        }

        // ——— BƯỚC 7: Upload thành công → lấy thông tin file ———
        $data = $this->upload->data();
        //  ↑ Trả về mảng thông tin file đã upload:
        //  [
        //    'file_name'     => 'a7f3e9b2c1d4.jpg',   ← tên mới (đã encrypt)
        //    'file_type'     => 'image/jpeg',
        //    'file_path'     => '/htdocs/webapi/uploads/',
        //    'full_path'     => '/htdocs/webapi/uploads/a7f3e9b2c1d4.jpg',
        //    'file_size'     => 245.76,                ← KB
        //    'image_width'   => 1080,
        //    'image_height'  => 1920,
        //    ...
        //  ]

        $filePath = base_url('uploads/' . $data['file_name']);
        //  ↑ base_url() = hàm CI3 trả về URL gốc của website
        //    ví dụ: base_url() = "http://xxx.ngrok.dev/webapi/"
        //  ↑ Ghép lại: "http://xxx.ngrok.dev/webapi/uploads/a7f3e9b2c1d4.jpg"
        //  ↑ Đây là URL để sau này app Android tải file về

        // ——— BƯỚC 8: Ghi metadata vào MySQL ———
        $this->UploadImages_model->insertUpload($userId, $description, $filePath);
        //  ↑ Gọi Model → INSERT INTO tbl_upload_images
        //    (user_id, description, image_url, active)
        //    VALUES ('xK9mPq2R...', 'HopDong', 'http://xxx/uploads/abc.jpg', 1)

        // ——— BƯỚC 9: Trả response thành công ———
        return $this->response_json([
            'status'  => true,
            'message' => 'Upload success',
            'data'    => [
                'imageUrl'    => $filePath,      // URL file đã upload
                'description' => $description,   // Mô tả
            ]
        ]);
    }

    private function response_json($data)
    {
        header('Content-Type: application/json');
        echo json_encode($data);
        exit;
    }
}
```

### Response mẫu:

```json
{
    "status": true,
    "message": "Upload success",
    "data": {
        "imageUrl": "http://xxx.ngrok.dev/webapi/uploads/a7f3e9b2c1d4.jpg",
        "description": "HopDong_thang4"
    }
}
```

---

## 3. Delete.php — Xoá Ảnh (Soft Delete)

**URL**: `POST /index.php/delete/image` (form-urlencoded)

```php
<?php
defined('BASEPATH') OR exit('No direct script access allowed');

class Delete extends CI_Controller
{
    public function image()
    {
        // ——— BƯỚC 0: CORS headers ———
        header('Access-Control-Allow-Origin: *');
        header('Access-Control-Allow-Methods: POST, DELETE, OPTIONS');
        header('Access-Control-Allow-Headers: Content-Type');
        //  ↑ Cho phép mọi domain gọi API này (cần cho ngrok/web browser)
        //  ↑ App Android gọi trực tiếp thì không cần, nhưng không hại gì

        if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { exit(0); }
        //  ↑ Nếu browser gửi preflight request (OPTIONS) → trả OK rỗng
        //  ↑ Đây là cơ chế CORS, browser gửi OPTIONS trước POST thật

        // ——— BƯỚC 1: Lấy tham số ———
        $userId = $this->input->get_post('userId');
        //  ↑ Firebase UID — để xác nhận quyền xoá

        $imageId = $this->input->get_post('id');
        //  ↑ ID của record trong bảng tbl_upload_images
        //  ↑ Giá trị này app lấy từ getImages() trước đó (img.id → ScanItem.serverId)

        // ——— BƯỚC 2: Validate ———
        if (empty($userId) || empty($imageId)) {
            return $this->response_json([
                'status'  => false,
                'message' => 'Missing userId or id'
            ]);
        }

        // ——— BƯỚC 3: Soft delete ———
        $this->db->where('id', $imageId);
        //  ↑ WHERE id = 3  (chỉ định dòng cần xoá)

        $this->db->where('user_id', $userId);
        //  ↑ AND user_id = 'xK9mPq2R...'
        //  ↑ QUAN TRỌNG: kiểm tra quyền sở hữu
        //    → User A không thể xoá ảnh của User B
        //    → Vì phải khớp cả id VÀ user_id mới update được

        $updated = $this->db->update('tbl_upload_images', ['active' => 0]);
        //  ↑ UPDATE tbl_upload_images SET active = 0
        //    WHERE id = 3 AND user_id = 'xK9mPq2R...'
        //
        //  ↑ active = 0 nghĩa là "đã xoá" (soft delete)
        //    File vật lý VẪN CÒN trên ổ cứng server
        //    Nhưng getImages() query WHERE active=1 → sẽ không thấy nữa
        //
        //  ↑ $updated = true nếu SQL chạy không lỗi

        // ——— BƯỚC 4: Kiểm tra kết quả ———
        if ($updated && $this->db->affected_rows() > 0) {
        //               ↑ affected_rows() = số dòng thực sự bị thay đổi
        //               ↑ > 0 nghĩa là có ít nhất 1 dòng bị update
        //               ↑ = 0 nếu id không tồn tại hoặc userId sai

            return $this->response_json([
                'status'  => true,
                'message' => 'Deleted successfully'
            ]);
        } else {
            return $this->response_json([
                'status'  => false,
                'message' => 'Not found or unauthorized'
                //  ↑ 2 trường hợp: id không tồn tại, HOẶC userId không khớp
            ]);
        }
    }

    private function response_json($data)
    {
        header('Content-Type: application/json');
        echo json_encode($data);
        exit;
    }
}
```

### Response mẫu:

```json
{
    "status": true,
    "message": "Deleted successfully"
}
```

---

## 4. UploadImages_model.php — Layer Nói Chuyện Với MySQL

```php
<?php

class UploadImages_model extends CI_Model
//  ↑ extends CI_Model = kế thừa class Model của CI3
//    → tự động có $this->db (kết nối MySQL từ database.php config)
{
    private $table = 'tbl_upload_images';
    //  ↑ Biến lưu tên bảng, dùng lại nhiều lần cho gọn

    public function __construct()
    {
        parent::__construct();
        //  ↑ Gọi constructor của CI_Model cha
        //    → CI3 tự động setup $this->db dựa trên database.php
    }

    // ——— HÀM 1: Lấy tất cả ảnh của 1 user ———
    public function getAllImages($userId = "")
    {
        $this->db->where('active', 1);
        //  ↑ WHERE active = 1  (chỉ lấy ảnh chưa bị xoá)

        $this->db->where('user_id', $userId);
        //  ↑ AND user_id = 'xK9mPq2R...'  (chỉ ảnh của user này)

        $this->db->order_by('id', 'DESC');
        //  ↑ ORDER BY id DESC  (mới nhất hiện trước)

        $query = $this->db->get($this->table);
        //  ↑ get('tbl_upload_images') = FROM tbl_upload_images
        //  ↑ CI3 ghép tất cả lại thành câu SQL hoàn chỉnh:
        //
        //    SELECT *
        //    FROM tbl_upload_images
        //    WHERE active = 1 AND user_id = 'xK9mPq2R...'
        //    ORDER BY id DESC
        //
        //  ↑ Chạy câu SQL → trả kết quả vào $query

        if ($query->num_rows() > 0) {
        //  ↑ num_rows() = đếm số dòng kết quả
        //  ↑ > 0 = có dữ liệu

            return $query->result_array();
            //  ↑ Chuyển kết quả SQL → mảng PHP
            //  ↑ Mỗi phần tử = 1 dòng trong bảng, ví dụ:
            //  [
            //    ['id'=>1, 'user_id'=>'xK9...', 'description'=>'ABC',
            //     'image_url'=>'http://...', 'active'=>1, 'created_at'=>'2026-04-05']
            //  ]
        }

        return false;
        //  ↑ Không có dòng nào → trả false
        //  ↑ Controller nhận false → trả JSON { status: false, data: [] }
    }

    // ——— HÀM 2: Thêm 1 record mới sau khi upload file ———
    public function insertUpload($userId = "", $desc = "", $imageUrl = "")
    {
        return $this->db->insert($this->table, array(
        //  ↑ insert('tbl_upload_images', [...]) = INSERT INTO tbl_upload_images

            'active'      => 1,
            //  ↑ Mặc định là 1 (hiện) — khi xoá sẽ set về 0

            'user_id'     => $userId,
            //  ↑ Firebase UID của người upload

            'description' => $desc,
            //  ↑ Tên file người dùng đặt

            'image_url'   => $imageUrl
            //  ↑ URL đầy đủ đến file trên server
        ));

        //  SQL hoàn chỉnh:
        //  INSERT INTO tbl_upload_images (active, user_id, description, image_url)
        //  VALUES (1, 'xK9mPq2R...', 'HopDong', 'http://xxx/uploads/abc.jpg')
        //
        //  Cột 'id' tự tăng (AUTO_INCREMENT)
        //  Cột 'created_at' tự điền thời gian hiện tại (DEFAULT CURRENT_TIMESTAMP)
    }
}
?>
```

---

## 5. Tóm Tắt: Ai Gọi Ai?

```
App gọi GET /getupload/image
    → GetUpload.php::image()
        → UploadImages_model::getAllImages()
            → MySQL: SELECT ... WHERE active=1 AND user_id=?
        ← Trả mảng dữ liệu
    ← Trả JSON { status, data }
← App nhận JSON → hiển thị RecyclerView

App gọi POST /upload/image (multipart)
    → Upload.php::image()
        → CI3 Upload library: lưu file vào /uploads/
        → UploadImages_model::insertUpload()
            → MySQL: INSERT INTO tbl_upload_images (...)
        ← Trả true
    ← Trả JSON { status, imageUrl }
← App nhận JSON → biết upload OK

App gọi POST /delete/image (form)
    → Delete.php::image()
        → $this->db->update() trực tiếp (không qua Model)
            → MySQL: UPDATE ... SET active=0 WHERE id=? AND user_id=?
        ← Trả affected_rows
    ← Trả JSON { status, message }
← App nhận JSON → xoá item khỏi RecyclerView
```

> **Lưu ý**: `Delete.php` gọi `$this->db` trực tiếp trong Controller thay vì qua Model. Điều này vẫn hoạt động được vì `CI_Controller` cũng có `$this->db`. Nhưng về mặt "sạch code" thì nên viết 1 hàm `deleteImage()` trong Model giống 2 cái kia.
