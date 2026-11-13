# Hướng dẫn Cài đặt & Cấu hình VinaBridge SMS Lite

## 1. Cài đặt APK

Ứng dụng có thể cài đặt trực tiếp qua file `.apk` (Sideloading):
1. Chép file `app-debug.apk` vào cả 2 điện thoại (Máy ở Việt Nam và Máy mang đi nước ngoài).
2. Mở trình quản lý file trên điện thoại, bấm vào file `.apk` để cài đặt.
3. Nếu điện thoại yêu cầu bật "Cài đặt ứng dụng không rõ nguồn gốc" (Install unknown apps / Unknown sources), hãy chọn Cho phép (Allow).

---

## 2. Thiết lập trên Máy gửi SMS (Gateway - Đặt tại Việt Nam)

1. Lắp SIM VinaPhone vào khay SIM hoạt động tốt, đảm bảo máy có kết nối Internet (Wi-Fi ổn định hoặc 4G).
2. Mở ứng dụng **VinaBridge SMS Lite**.
3. Chọn **Cấu hình & Đăng nhập**:
   - Nhập Firebase DB URL, Web API Key, Email và Password.
   - Bấm **Đăng nhập**.
4. Vào mục **Bảo mật & Ghép cặp**:
   - Nhập **Mã khóa bảo mật (Passphrase)** bí mật của bạn.
   - Bấm **Lưu**.
5. Quay lại màn hình chính, chọn **Máy gửi SMS (Gateway)**:
   - Cấp quyền **Nhận tin nhắn SMS (RECEIVE_SMS)** khi hệ thống hỏi.
   - Bật công tắc **Bật chuyển tiếp SMS**.
   - Bấm nút **Gửi tin nhắn thử nghiệm** để kiểm tra đồng bộ lên Firebase. Khi thấy trạng thái báo "Đã kết nối" và "Đồng bộ gần nhất: Vừa xong", máy đã sẵn sàng hoạt động.
6. **Lưu ý pin và tối ưu nền:**
   - Vào *Cài đặt điện thoại* -> *Pin / Tối ưu hóa pin* (Battery Optimization) -> Chọn VinaBridge SMS Lite -> Chọn **Không tối ưu hóa** (Don't optimize / No restrictions) để hệ thống không tự ngắt ứng dụng khi tắt màn hình.
   - Cắm sạc liên tục ở nơi thoáng mát tại Việt Nam.

---

## 3. Thiết lập trên Máy nhận SMS (Receiver - Mang ra nước ngoài)

1. Mở ứng dụng **VinaBridge SMS Lite** trên điện thoại nước ngoài (kết nối Wi-Fi hoặc SIM địa phương/eSIM).
2. Chọn **Cấu hình & Đăng nhập**:
   - Nhập **chính xác cùng tài khoản Firebase** (Email, Password, DB URL, API Key) như máy ở Việt Nam.
3. Vào mục **Bảo mật & Ghép cặp**:
   - Nhập **chính xác cùng Mã khóa bảo mật (Passphrase)** đã đặt trên máy ở Việt Nam.
4. Quay lại màn hình chính, chọn **Máy nhận SMS (Receiver)**:
   - Nếu dùng Android 13+, cấp quyền **Gửi thông báo (POST_NOTIFICATIONS)**.
   - Ứng dụng sẽ hiển thị thông báo thường trực "VinaBridge SMS Đang lắng nghe".
   - Bấm **Đồng bộ thủ công** một lần để kiểm tra tải dữ liệu ban đầu.
5. Khi có SMS gửi đến máy Việt Nam:
   - Máy nhận sẽ nhận được ngay qua luồng Realtime SSE.
   - Ứng dụng tự động giải mã và hiển thị số người gửi, thời gian và nội dung SMS.
   - Có nút **Sao chép** (Copy) để bạn dễ dàng dán mã OTP vào ứng dụng ngân hàng.

---

## 4. Hướng dẫn Kiểm thử Thực tế

1. **Test gửi SMS nội địa:** Dùng điện thoại khác nhắn một tin nhắn (ví dụ: `OTP test 123456`) tới SIM VinaPhone tại Việt Nam.
2. **Kiểm tra trên máy Việt Nam:** Mở VinaBridge SMS Lite, thấy số lượng tin nhắn trong hàng đợi là 0 và "Lần đồng bộ gần nhất" cập nhật thời gian.
3. **Kiểm tra trên máy Nước ngoài:** Nhận thông báo "Tin nhắn SMS mới", danh sách tin nhắn xuất hiện tin nhắn giải mã nguyên vẹn.
4. **Test mất mạng và gửi lại:** Tắt Wi-Fi/4G trên máy Việt Nam, gửi tin nhắn đến SIM VinaPhone. Mở app thấy hàng đợi báo `1 tin`. Bật lại mạng, app tự động đồng bộ lên Firebase và giải phóng hàng đợi.
