# Hướng dẫn Thiết lập Firebase cho VinaBridge SMS Lite

## 1. Tạo Firebase Project
1. Truy cập [Firebase Console](https://console.firebase.google.com/).
2. Bấm **Add project** (Thêm dự án), đặt tên (ví dụ: `vinabridge-sms`).
3. Tắt Google Analytics (để tối giản và bảo mật) rồi bấm **Create project**.

---

## 2. Kích hoạt Firebase Authentication
1. Tại menu bên trái, vào **Build** -> **Authentication**.
2. Chọn thẻ **Sign-in method**.
3. Bật phương thức **Email/Password** (chỉ cần bật Email/Password, không cần Email link).
4. Chuyển sang thẻ **Users**, bấm **Add user**:
   - Nhập Email của bạn (ví dụ: `myphone@vinabridge.local`).
   - Nhập Mật khẩu bảo mật.
   - Bấm **Add user**. Lưu lại email và mật khẩu này để đăng nhập trên cả 2 điện thoại.

---

## 3. Tạo Firebase Realtime Database
1. Tại menu bên trái, vào **Build** -> **Realtime Database**.
2. Bấm **Create Database**:
   - Chọn vị trí cơ sở dữ liệu (ví dụ: `Singapore (asia-southeast1)` hoặc `United States`).
   - Chọn **Start in locked mode** rồi bấm **Enable**.
3. Chép lại URL của cơ sở dữ liệu (dạng: `https://vinabridge-sms-default-rtdb.asia-southeast1.firebasedatabase.app`).

---

## 4. Cập nhật Security Rules
1. Tại giao diện Realtime Database, chọn tab **Rules**.
2. Sao chép toàn bộ nội dung từ file [firebase/database.rules.json](file:///C:/Users/HOANG%20ANH/.t3/scratch/2026-11-13-i-u-ph-i-c-0c501fe6/firebase/database.rules.json) vào ô nhập rules:

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid === $uid",
        ".write": "auth != null && auth.uid === $uid",
        "sms": {
          "$messageId": {
            ".validate": "newData.hasChildren(['id', 'encryptedPayload', 'timestamp', 'deviceId', 'version'])",
            "id": {
              ".validate": "newData.isString() && newData.val().length > 0"
            },
            "encryptedPayload": {
              ".validate": "newData.isString() && newData.val().length > 0"
            },
            "timestamp": {
              ".validate": "newData.isNumber()"
            },
            "deviceId": {
              ".validate": "newData.isString() && newData.val().length > 0"
            },
            "version": {
              ".validate": "newData.isNumber() && newData.val() >= 1"
            },
            "$other": {
              ".validate": false
            }
          }
        }
      }
    }
  }
}
```

3. Bấm **Publish** để áp dụng. Quy tắc này đảm bảo:
   - Chỉ người dùng đăng nhập đúng tài khoản mới có quyền đọc/ghi.
   - Tuyệt đối không ai bên ngoài (kể cả quét mạng công khai) truy cập được dữ liệu.

---

## 5. Lấy Firebase Web API Key
1. Bấm vào biểu tượng bánh răng ⚙️ ở góc trên bên trái -> **Project settings**.
2. Trong tab **General**, cuộn xuống tìm **Web API Key** (dạng `AIzaSy...`).
3. Chép Web API Key này lại.

---

## 6. Tổng kết thông tin cần nhập vào ứng dụng
Khi mở VinaBridge SMS Lite lần đầu, bạn chỉ cần điền 4 thông số:
1. **Database URL:** `https://your-project.firebaseio.com` (hoặc domain asia-southeast1).
2. **Web API Key:** `AIzaSy...`
3. **Email đăng nhập:** Email đã tạo ở Bước 2.
4. **Mật khẩu:** Mật khẩu đã tạo ở Bước 2.
5. **Mã khóa bảo mật (Passphrase):** Do bạn tự nghĩ ra (phải nhập **GIỐNG HỆT NHAU** trên cả 2 điện thoại).
