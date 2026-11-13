# Kiến trúc Hệ thống VinaBridge SMS Lite

## 1. Tổng quan
**VinaBridge SMS Lite** là giải pháp chuyển tiếp tin nhắn SMS một chiều (Gateway -> Cloud -> Receiver) dành cho người dùng di chuyển ra nước ngoài nhưng vẫn cần nhận SMS biến động số dư, mã OTP từ SIM VinaPhone đặt tại Việt Nam.

Ứng dụng được thiết kế theo tiêu chí:
- Siêu nhẹ, tiết kiệm RAM.
- Chạy từ Android 5.0 (Lollipop - API 21) đến Android 14+ (API 34).
- Không phụ thuộc Firebase Android SDK cồng kềnh, dùng thuần HTTPS REST & SSE.
- Mã hóa đầu cuối (E2EE) chuẩn quân đội AES-256-GCM.

---

## 2. Luồng hoạt động (Data Flow)

```
[Mạng viễn thông VinaPhone]
             │
             ▼ (SMS nhận trên điện thoại VN)
[Gateway - SmsReceiver]
             │
             ▼ (Stitch tin nhắn dài & Deduplicate)
[Gateway - SQLite Queue: pending_sms]
             │
             ▼ (Mã hóa AES-256-GCM bằng Key chia sẻ)
[Gateway - HttpsURLConnection PUT]
             │
             ▼
[Firebase Realtime Database: users/{uid}/sms/{id}]
             │
             ▼ (SSE Streaming qua text/event-stream)
[Receiver - Foreground Service (Điện thoại nước ngoài)]
             │
             ▼ (Giải mã AES-256-GCM)
[Receiver - Danh sách tin nhắn & Thông báo an toàn]
```

---

## 3. Các thành phần chính

### 3.1. Máy gửi (Gateway - Điện thoại tại Việt Nam)
1. **SmsReceiver**: Lắng nghe `SMS_RECEIVED_ACTION`. Khi có tin nhắn đến, trích xuất số người gửi, thời gian, và ghép các phần (multipart SMS) thành một chuỗi tin nhắn trọn vẹn.
2. **Deduplication Engine**: Sử dụng thuật toán SHA-256 trên tổ hợp `(sender + body + rounded_time)` để tạo ra ID ổn định cho mỗi tin nhắn, chống gửi lặp khi hệ điều hành phát lại broadcast.
3. **SQLite Local Queue (`pending_sms`)**: Lưu lại tin nhắn cục bộ ngay lập tức. Nếu mất mạng, tin nhắn vẫn được lưu an toàn trong SQLite và tự động đồng bộ khi kết nối mạng phục hồi.
4. **GatewaySyncService**: Kích hoạt việc tải tin nhắn lên Firebase REST API. Nếu thành công (HTTP 200), tin nhắn được đánh dấu gửi thành công và xóa khỏi hàng đợi.

### 3.2. Máy nhận (Receiver - Điện thoại ở nước ngoài)
1. **FirebaseAuthRestClient**: Đăng nhập qua Firebase Authentication REST API, quản lý `idToken`, `refreshToken` và `uid` mà không cần Google Play Services.
2. **FirebaseDbRestClient (SSE Streaming)**: Mở luồng kết nối HTTP streaming dài hạn (`Accept: text/event-stream`). Khi Firebase có bản ghi mới, server đẩy ngay lập tức về máy nhận với độ trễ dưới 1 giây.
3. **ReceiverForegroundService**: Duy trì kết nối ở chế độ nền với notification cố định, đúng quy định của Android 8.0+ và Android 14 (`FOREGROUND_SERVICE_DATA_SYNC`).
4. **Security Notification**: Khi có tin mới, hệ thống bắn Notification nhưng che nội dung trên màn hình khóa (`NotificationCompat.VISIBILITY_PRIVATE`) để phòng ngừa kẻ xấu nhìn trộm OTP.

---

## 4. Cơ chế Mã hóa & Bảo mật (E2EE)
- **Thuật toán:** AES-256-GCM (`AES/GCM/NoPadding`).
- **Khóa bí mật:** Được sinh ra từ mật mã ghép cặp (passphrase do người dùng tự đặt) kết hợp với Salt ngẫu nhiên 16 bytes bằng hàm chuẩn **PBKDF2WithHmacSHA256** (10,000 vòng lặp).
- **IV (Initialization Vector):** Mỗi tin nhắn được sinh một IV ngẫu nhiên 12 bytes độc lập. Không bao giờ tái sử dụng IV.
- **Tính toàn vẹn (Integrity):** GCM cung cấp 128-bit Authentication Tag. Bất kỳ sự can thiệp, chỉnh sửa dữ liệu trên Firebase đều bị phát hiện ngay lập tức và hủy bỏ khi giải mã.
- **Firebase Security Rules:** Đảm bảo chỉ tài khoản đã đăng nhập đúng `auth.uid` mới có quyền đọc và ghi dữ liệu tại nhánh `users/$uid`.
