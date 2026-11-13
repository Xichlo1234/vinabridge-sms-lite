# Báo cáo Phân tích Tương thích & Hạn chế theo Phiên bản Android

Tài liệu này phân tích chi tiết khả năng tương thích kỹ thuật, sự khác biệt giữa các phiên bản hệ điều hành Android từ 5.0 (Lollipop) đến Android 14+ (Upside Down Cake).

---

## 1. Khả năng tương thích Android 5.0 - 5.1 (API 21 - 22)

### A. Điểm thuận lợi:
- **Quyền SMS (Permissions):** Trên Android 5, tất cả quyền (`RECEIVE_SMS`, `INTERNET`) được cấp vĩnh viễn lúc cài đặt (Install-time permissions). Ứng dụng không cần popup xin quyền động (Runtime Permission), tránh việc người dùng quên bấm Cho phép.
- **Broadcast nền:** `SMS_RECEIVED_ACTION` kích hoạt trực tiếp `BroadcastReceiver` ngay cả khi ứng dụng bị tắt hoàn toàn khỏi màn hình gần đây (Recents).
- **Bộ nhớ RAM:** Ứng dụng dùng XML layout thuần và Java SDK gốc, dung lượng RAM khi chạy nền chỉ khoảng 15MB - 25MB, chạy mượt trên các máy chip Mediatek/Snapdragon đời cũ có 1GB - 2GB RAM.

### B. Hạn chế & Giải pháp kỹ thuật:
- **TLS 1.2 / HTTPS:** Android 5.0 mặc định không bật TLS 1.2 cho các kết nối mạng cũ. Tuy nhiên, các máy đã cập nhật Google Play Services sẽ có Provider cập nhật. Với REST API của Firebase, Google hỗ trợ TLS 1.2 tiêu chuẩn.
- **Android Keystore:** API 21 chỉ hỗ trợ khóa bất đối xứng (RSA) trong Keystore, chưa hỗ trợ sinh trực tiếp khóa đối xứng AES-GCM (vốn chỉ có từ API 23 - Android 6.0).
  * *Giải pháp:* VinaBridge SMS Lite sử dụng cơ chế sinh khóa chuẩn **PBKDF2WithHmacSHA256** (10,000 vòng lặp) kết hợp Salt ngẫu nhiên 16-byte cho mỗi tin nhắn. Nhờ đó, khóa AES-256 được dẫn xuất độc lập an toàn tương đương mà không bị lỗi tương thích Keystore trên Android 5.0.
- **Firebase Android SDK chính thức:** Các bản Firebase SDK gần đây của Google đã nâng `minSdk` lên 21 hoặc 23. Do đó, việc ứng dụng sử dụng trực tiếp **Firebase REST API thuần** là giải pháp duy nhất đảm bảo 100% không bị crash trên Android 5.0.

---

## 2. Hạn chế trên Android 6.0 - 7.1 (API 23 - 25)

- **Doze Mode (Chế độ ngủ sâu):** Giới hạn mạng khi máy không cắm sạc và để yên.
  * *Giải pháp:* Người dùng máy Gateway tại Việt Nam phải tắt "Tối ưu hóa pin" (Battery Optimization) cho ứng dụng và luôn cắm sạc.
- **Runtime Permissions:** Người dùng phải cấp quyền `RECEIVE_SMS` tại lần mở đầu tiên.

---

## 3. Hạn chế trên Android 8.0 - 9.0 (API 26 - 28)

- **Background Execution Limits:** Android cấm các Service chạy nền vô hạn nếu không có Notification thường trực.
  * *Giải pháp:* Receiver sử dụng `ForegroundService` đi kèm một Notification cố định có độ ưu tiên thấp (`PRIORITY_MIN` / `IMPORTANCE_LOW`) để hệ thống không tự ngắt tiến trình.
- **Notification Channels:** Yêu cầu tạo Kênh thông báo (`NotificationChannel`). Ứng dụng đã chia 2 kênh:
  1. `vinabridge_service_channel`: Kênh cho Foreground Service duy trì kết nối.
  2. `vinabridge_sms_channel`: Kênh cho tin nhắn SMS mới đến (có âm thanh/rung).

---

## 4. Hạn chế trên Android 10 - 12 (API 29 - 32)

- **Quyền SMS trên Google Play:** Google áp dụng chính sách cực kỳ khắt khe đối với nhóm quyền `SMS` và `CALL_LOG` trên kho Play Store (chỉ cho phép ứng dụng SMS mặc định).
  * *Thực tế:* VinaBridge SMS Lite được phân phối dưới dạng **cài APK trực tiếp (Sideload)**, do đó hoàn toàn không bị ảnh hưởng bởi chính sách của Google Play Console.
- **Scraped SMS / Clipboard access:** Ứng dụng có nút "Sao chép" chủ động để người dùng bấm, tuân thủ hạn chế đọc clipboard nền của Android 10+.

---

## 5. Hạn chế trên Android 13 - 14+ (API 33 - 34)

- **Quyền Thông báo (POST_NOTIFICATIONS):** Android 13 yêu cầu người dùng phải chủ động cấp quyền thông báo. Nếu từ chối, máy Nhận vẫn nhận được dữ liệu khi mở app nhưng không thấy pop-up thông báo.
- **Foreground Service Type (Android 14):** Bắt buộc khai báo `android:foregroundServiceType="dataSync"` trong `AndroidManifest.xml` và xin quyền `FOREGROUND_SERVICE_DATA_SYNC`. Ứng dụng đã cấu hình đầy đủ.
- **Bảo vệ màn hình khóa (Lock Screen Privacy):** Ứng dụng đặt `NotificationCompat.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)`, đảm bảo nội dung tin nhắn và mã OTP không bị lộ ra ngoài màn hình khóa.

---

## 6. Tổng kết Ma trận Tương thích

| Tính năng | Android 5.0 - 5.1 (API 21-22) | Android 6.0 - 7.1 (API 23-25) | Android 8.0 - 12 (API 26-32) | Android 13 - 14+ (API 33-34) |
| :--- | :---: | :---: | :---: | :---: |
| Cài đặt qua APK | ✅ Hoạt động | ✅ Hoạt động | ✅ Hoạt động | ✅ Hoạt động |
| Quyền nhận SMS | Cấp khi cài | Cần bấm cho phép | Cần bấm cho phép | Cần bấm cho phép |
| Mã hóa AES-256-GCM | ✅ (PBKDF2) | ✅ (PBKDF2/Keystore) | ✅ | ✅ |
| Chạy nền Gateway | ✅ Tuyệt vời | Cần tắt tối ưu pin | Cần tắt tối ưu pin | Cần tắt tối ưu pin |
| Chạy nền Receiver | ✅ Service thường | ✅ Service thường | ✅ Foreground Service | ✅ Foreground Service DataSync |
| Thông báo SMS mới | ✅ | ✅ | ✅ (NotificationChannel) | ✅ (Yêu cầu POST_NOTIFICATIONS) |
