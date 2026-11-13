# VinaBridge SMS Lite 📱⚡

Ứng dụng Android độc lập, siêu nhẹ dùng để chuyển tiếp tin nhắn SMS (OTP, biến động số dư ngân hàng) từ điện thoại lắp SIM VinaPhone đặt tại Việt Nam sang điện thoại ở nước ngoài thông qua Firebase cá nhân.

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![minSdk](https://img.shields.io/badge/minSdk-21%20(Android%205.0)-green.svg)
![targetSdk](https://img.shields.io/badge/targetSdk-34-blue.svg)
![Language](https://img.shields.io/badge/language-Pure%20Java-orange.svg)

---

## 🌟 Tính năng nổi bật

- **Tối giản & Siêu nhẹ:** Viết bằng Java thuần và Android SDK gốc, không Jetpack Compose, không Flutter, không dùng Firebase SDK nặng. Tương thích hoàn hảo với máy cấu hình thấp, RAM 1GB - 2GB.
- **Hoạt động từ Android 5.0 (API 21):** Hỗ trợ tốt các điện thoại Android đời cũ tận dụng làm trạm phát SMS tại Việt Nam.
- **Không cần VPS / Máy chủ trung gian:** Toàn bộ dữ liệu đi thẳng từ Gateway lên Firebase Project của chính bạn qua HTTPS REST API.
- **Mã hóa đầu cuối (E2EE):** Toàn bộ nội dung tin nhắn và số điện thoại người gửi được mã hóa bằng thuật toán **AES-256-GCM** với khóa dẫn xuất **PBKDF2WithHmacSHA256**. Dữ liệu lưu trên Firebase Console ở dạng mã hóa không thể đọc trộm.
- **Offline-First & Hàng đợi SQLite:** Nếu mất kết nối Internet, máy Gateway lưu SMS vào hàng đợi SQLite và tự động đồng bộ lại khi mạng phục hồi.
- **Chống trùng lặp (Deduplication):** Tự động phát hiện và ghép nối tin nhắn SMS nhiều phần (multipart SMS), tính hash để không bao giờ gửi trùng một tin nhắn lên Firebase.
- **Bảo mật OTP:** Tuyệt đối không in mã OTP ra Logcat, không hiển thị nội dung tin nhắn ra ngoài màn hình khóa (`VISIBILITY_PRIVATE`).

---

## 🏗️ Cấu trúc Kho lưu trữ

```text
vinabridge-sms-lite/
├── app/                        # Toàn bộ mã nguồn Android (Java native)
│   ├── src/main/java/com/vinabridge/smslite/
│   │   ├── gateway/            # SmsReceiver, SQLite Queue, GatewaySyncService
│   │   ├── receiver/           # ReceiverForegroundService, SSE Stream Listener
│   │   ├── firebase/           # FirebaseAuthRestClient, FirebaseDbRestClient
│   │   ├── security/           # CryptoManager (AES-256-GCM)
│   │   ├── model/              # SmsPayload, FirebaseSmsRecord
│   │   └── ui/                 # MainActivity, GatewayActivity, ReceiverActivity
│   └── src/main/res/           # XML Layouts, Styles, Drawables
├── firebase/
│   └── database.rules.json     # Quy tắc bảo mật Firebase Realtime Database
├── docs/
│   ├── ARCHITECTURE.md         # Tài liệu kiến trúc chi tiết & Luồng dữ liệu
│   ├── FIREBASE_SETUP.md       # Hướng dẫn tạo Firebase Project từng bước
│   ├── INSTALLATION_GUIDE.md   # Hướng dẫn cài đặt APK & Cấu hình 2 máy
│   └── ANDROID_LIMITATIONS.md  # Phân tích hạn chế từ Android 5.0 đến Android 14
├── .github/workflows/
│   └── build-apk.yml           # CI/CD tự động build APK debug & chạy Unit Test
├── AGENTS.md                   # Quy tắc điều phối các AI Agents
└── README.md                   # Tài liệu hướng dẫn sử dụng tiếng Việt
```

---

## 🚀 Hướng dẫn Bắt đầu Nhanh

1. **Thiết lập Firebase:** Xem chi tiết tại [docs/FIREBASE_SETUP.md](file:///C:/Users/HOANG%20ANH/.t3/scratch/2026-11-13-i-u-ph-i-c-0c501fe6/docs/FIREBASE_SETUP.md) để lấy **Database URL**, **Web API Key** và tạo tài khoản đăng nhập.
2. **Cài đặt ứng dụng:** Xem chi tiết tại [docs/INSTALLATION_GUIDE.md](file:///C:/Users/HOANG%20ANH/.t3/scratch/2026-11-13-i-u-ph-i-c-0c501fe6/docs/INSTALLATION_GUIDE.md).
3. **Biên dịch APK:**
   - Mở dự án trong **Android Studio** (Hedgehog hoặc mới hơn) và bấm **Build APK**.
   - Hoặc đẩy code lên GitHub, **GitHub Actions** sẽ tự động build file `app-debug.apk` trong tab Actions.

---

## 🛡️ Báo cáo Kiểm thử & Trạng thái Thực tế

- **Unit Tests đã viết:**
  - `CryptoTest.java`: Kiểm tra mã hóa và giải mã tiếng Việt có dấu, IV ngẫu nhiên, phát hiện chỉnh sửa dữ liệu trái phép (tamper-proof).
  - `SmsPayloadTest.java`: Kiểm tra sinh mã hash ID tin nhắn chống trùng, serialization JSON.
  - `GatewayQueueTest.java`: Kiểm tra hàng đợi SQLite offline.
- **Trạng thái Môi trường Hiện tại:**
  - Môi trường máy tính hiện tại không có sẵn Android SDK / Java CLI để chạy lệnh `gradlew` trực tiếp. Do đó, toàn bộ mã nguồn đã được chuẩn hóa để biên dịch qua **GitHub Actions** (`.github/workflows/build-apk.yml`) hoặc mở trực tiếp bằng **Android Studio**.
  - Việc kiểm thử trên sóng viễn thông VinaPhone thực tế cần được thực hiện trên điện thoại thật theo các bước tại [docs/INSTALLATION_GUIDE.md](file:///C:/Users/HOANG%20ANH/.t3/scratch/2026-11-13-i-u-ph-i-c-0c501fe6/docs/INSTALLATION_GUIDE.md).
