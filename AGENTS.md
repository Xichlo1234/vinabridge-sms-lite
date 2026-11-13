# AGENTS.md - Quy tắc và Hướng dẫn cho AI Agents (Codex, Claude, Antigravity)

## 1. Mục tiêu Dự án
Dự án **VinaBridge SMS Lite** là ứng dụng Android tối giản (minSdk 21 - Android 5.0 Lollipop), viết hoàn toàn bằng **Java thuần** và **Android SDK gốc** (không Jetpack Compose, không Flutter, không thư viện Firebase SDK nặng).

## 2. Quy tắc Lập trình Cốt lõi
- **Ngôn ngữ:** Java 8 / 17 compatible (chạy mượt trên Android 5.0+).
- **Mã hóa:** Toàn bộ nội dung SMS (sender, body) phải được mã hóa bằng AES-256-GCM với Key được sinh qua PBKDF2WithHmacSHA256 (hoặc Android Keystore khi hỗ trợ) TRƯỚC KHI gửi lên Firebase.
- **REST HTTPS:** Không dùng Firebase Android SDK vì Android 5.0 không tương thích tốt với bản SDK mới. Sử dụng thuần `HttpURLConnection` / `HttpsURLConnection` với Firebase Auth REST API và Firebase Realtime Database REST API.
- **Bảo mật OTP:** Tuyệt đối không log nội dung SMS/OTP ra `Logcat`, Crashlytics hoặc bất kỳ analytics nào.
- **Xử lý SMS:**
  - Nhận Broadcast `android.provider.Telephony.SMS_RECEIVED`.
  - Hỗ trợ ghép tin nhắn dài (multipart SMS `PduParser` / `SmsMessage.createFromPdu`).
  - Chống trùng tin nhắn bằng hash (`SHA-256(sender + timestamp + body)`).
  - Lưu vào SQLite offline queue (`pending_sms`). Khi có mạng thì đẩy tuần tự và xóa khỏi queue khi thành công.
- **Giao diện:** XML Layout tối giản, nhẹ cho máy cấu hình yếu/RAM thấp.

## 3. Phân công vai trò giữa các AI
- **Antigravity (Orchestrator):** Cấu trúc dự án, Security Rules, Data Model, CryptoManager (AES-GCM), GitHub CI, Docs.
- **Codex:** Phụ trách Gateway Engine (SmsReceiver, GatewayDbHelper SQLite queue, GatewaySyncService, retry mechanism).
- **Claude:** Phụ trách Receiver Engine & UI (Firebase REST Client, Realtime DB SSE Streaming, ReceiverForegroundService, Message UI Adapter, MainActivity, Settings).
