# Omnichannel Chat Demo – Hướng dẫn tổng hợp

Tài liệu này thay thế toàn bộ các hướng dẫn trước đó. Làm theo thứ tự từ trên xuống dưới để dựng và test hệ thống (Telegram focus).

---

## 1. Yêu cầu môi trường

| Thành phần | Phiên bản khuyến nghị |
|-----------|------------------------|
| Java      | 21 (MS OpenJDK 21)     |
| Gradle    | Wrapper đi kèm dự án   |
| ngrok     | 3.3+                   |
| Telegram  | Bot đã tạo với BotFather |

---

## 2. Chuẩn bị Bot Telegram

1. Chat với **@BotFather** → `/newbot` → đặt tên và username (kết thúc bằng `bot`).
2. Lưu **Bot Token** (ví dụ: `8276716956:AAFsZ...`).
3. (Tuỳ chọn) `/mybots` → bot → *Bot Settings* để bật các quyền cần thiết.

---

## 3. Cấu hình dự án

### 3.1. Clone & cài dependency
```powershell
git clone <repo>
cd chat-demo
./gradlew tasks
```

### 3.2. Đặt Bot Token (không commit token)
Chọn **một** trong các cách:

**A. Biến môi trường (khuyến nghị)**
```powershell
# PowerShell
$env:TELEGRAM_BOT_TOKEN="8276716956:AAFsZqvuvazxJcGB3j0oWlbi8DR3LO5jNmM"
```

**B. File `.env`** (đã có `.gitignore`)
```
TELEGRAM_BOT_TOKEN=8276716956:AAFsZqvuvazxJcGB3j0oWlbi8DR3LO5jNmM
```

**C. `application.yaml`** (chỉ local, đừng commit)
```yaml
platform:
  telegram:
    bot-token: "8276716956:AAFsZ..."
```

### 3.3. (Tuỳ chọn) Chuẩn bị Messenger
- Tạo Facebook App, gắn với một Facebook Page.
- Lấy **Page Access Token** và **Verify Token**.
- Xuất các biến môi trường (hoặc cập nhật `application.yaml`):
  ```powershell
  $env:MESSENGER_PAGE_TOKEN="EAAN..."
  $env:MESSENGER_VERIFY_TOKEN="your-verify-token"
  ```
- Sau khi backend chạy và ngrok có URL, vào Facebook Developer > App > Messenger để đăng ký webhook:
  - Callback URL: `https://<ngrok-url>/webhook/messenger`
  - Verify token: dùng đúng giá trị bạn đặt ở trên.
- Khi webhook Messenger đã verify thành công, Facebook sẽ gửi event tới backend giống Telegram.

---

## 4. Khởi động ứng dụng

1. Chạy backend (port đã cấu hình: `8081`):
   ```powershell
   ./gradlew bootRun
   ```
2. Log mong đợi:
   - `Tomcat started on port 8081`
   - `Initialized connectors for platforms: [...]`
3. Nếu máy hỗ trợ `Desktop.browse`, app sẽ tự mở Swagger `http://localhost:8081/swagger-ui.html`. Nếu log cảnh báo “Desktop browse action is not supported”, hãy mở bằng tay.

> Lỗi “Port 8081 already in use”: dừng process đang chạy (`taskkill /PID <pid> /F`) hoặc đổi port trong `application.yaml`.

---

## 5. Bật ngrok & thiết lập webhook

1. Mở terminal mới (để giữ app chạy ở terminal cũ):
   ```bash
   ngrok http 8081
   ```
2. Sao chép URL HTTPS ngrok cung cấp, ví dụ:
   ```
   https://abc123.ngrok-free.app
   ```
3. Đăng ký webhook cho Telegram:
   ```powershell
  $token   = "8276716956:AAFsZqvuvazxJcGB3j0oWlbi8DR3LO5jNmM"
$webhook = "https://exhaustingly-nonshrinkable-roosevelt.ngrok-free.dev/webhook/telegram"
Invoke-WebRequest -Uri "https://api.telegram.org/bot$token/setWebhook?url=$webhook" -Method Post
   ```
   Hoặc dùng endpoint helper:
   ```
   http://localhost:8081/test/telegram/setup-webhook?url=https://abc123.ngrok-free.app/webhook/telegram
   ```
4. Kiểm tra:
   ```
   http://localhost:8081/test/telegram/webhook-info
   ```
   hoặc trực tiếp Telegram API:
   ```
   https://api.telegram.org/bot<token>/getWebhookInfo
   ```

> Mỗi lần ngrok đổi URL, cần set webhook lại.

---

## 6. Luồng test end-to-end

### 6.1. User → Webhook
1. Mở Telegram, tìm bot (username đã tạo).
2. Gửi tin nhắn “Hello”.
3. Quan sát log:
   - `Routing message from platform TELEGRAM user ...`
   - `Registered new user ...` (nếu lần đầu)
   - `Saved inbound message ...`
   - `Welcome message dispatched ...`

### 6.2. Kiểm tra DB (PostgreSQL)
1. Kết nối PostgreSQL (psql, pgAdmin, DBeaver) tới DB `chatdemo` với user `postgres`.
2. Chạy:
   ```sql
   SELECT * FROM users;
   SELECT * FROM conversations;
   SELECT * FROM messages;
   ```
3. Bạn sẽ thấy user/tin nhắn đã được lưu (không còn dùng H2 console).

### 6.3. Staff Dashboard APIs (Swagger UI)
Mở `http://localhost:8081/swagger-ui.html` → các nhóm:

- **Webhook API**: `/webhook/telegram`, `/webhook/messenger`, `/webhook/zalo`.
- **Telegram Test API**: các endpoint helper (webhook-info, setup, delete, send-message).
- **Staff Chat API**:
  - `GET /api/conversations` – danh sách hội thoại.
  - `GET /api/conversations/{id}` – chi tiết hội thoại.
  - `GET /api/conversations/{id}/messages` – lịch sử tin nhắn (đặt `page`, `size`).
  - `POST /api/conversations/{id}/messages` – staff trả lời người dùng.

### 6.4. Staff trả lời người dùng
1. Từ `GET /api/conversations`, chọn `id`.
2. Gọi `POST /api/conversations/{id}/messages` với JSON:
   ```json
   {
     "content": "Xin chào, chúng tôi đã nhận được yêu cầu của bạn!"
   }
   ```
3. Log hiển thị:
   - `Sent outbound message ...`
   - `TelegramConnector` log URL gửi.
4. Người dùng thấy tin nhắn trả lời trực tiếp trên Telegram.

---

## 7. Logging quan trọng

| Vị trí | Log chính |
|-------|-----------|
| `UserRegistryService` | Kiểm tra user mới, đăng ký user |
| `OmnichannelRouter` | Route message, lưu inbound, welcome message |
| `OmnichannelMessageBus` | Lưu inbound/outbound messages |
| `ChatApiController` | Lấy conversations/messages, gửi reply |
| Connectors | URL gửi, kết quả gửi message |

Sử dụng log để debug nhanh mỗi bước.

---

## 8. Troubleshooting nhanh

| Lỗi | Cách xử lý |
|-----|-----------|
| Port 8081 bận | `netstat -ano | findstr 8081` → `taskkill /PID <pid> /F` |
| Không mở được swagger tự động | Hệ thống không hỗ trợ `Desktop.browse`. Mở thủ công `http://localhost:8081/swagger-ui.html` |
| ngrok đổi URL | Chạy lại lệnh `setWebhook` với URL mới |
| Bot không phản hồi | Kiểm tra webhook info, log app, đảm bảo token đúng |
| H2 không truy cập được | Đảm bảo app đang chạy; check `application.yaml` |

---

## 9. Thay đổi cấu hình nhanh

```yaml
server:
  port: 8081          # đổi port nếu cần

app:
  open-browser:
    enabled: true     # tắt nếu không muốn tự mở swagger
    url: http://localhost:8081/swagger-ui.html

platform:
  telegram:
    bot-token: ${TELEGRAM_BOT_TOKEN:dummy}
    api-url: https://api.telegram.org/bot
    webhook-url: ${TELEGRAM_WEBHOOK_URL:http://localhost:8081/webhook/telegram}
```

---

## 10. Checklist nhanh

1. ✅ Set `TELEGRAM_BOT_TOKEN`
2. ✅ `./gradlew bootRun`
3. ✅ `ngrok http 8081`
4. ✅ `setWebhook` với URL ngrok
5. ✅ Gửi tin nhắn Telegram → xem log
6. ✅ Mở Swagger/H2 để kiểm tra dữ liệu
7. ✅ Staff reply qua API → người dùng nhận tin

Hoàn tất! Dự án hiện tập trung vào luồng Omnichannel cơ bản cho Telegram. Khi cần, bạn có thể mở rộng thêm các nền tảng khác trong tương lai.

PS C:\Users\minhf> $token = "8276716956:AAGEGKI3nFVxhvvEyyaEl2D4vU5NBYGGqlI"
PS C:\Users\minhf> $webhook = "https://exhaustingly-nonshrinkable-roosevelt.ngrok-free.dev/webhook/telegram"
PS C:\Users\minhf> Invoke-WebRequest -Uri "https://api.telegram.org/bot$token/setWebhook?url=$webhook" -Method Post

---

## 11. Kiến trúc backend (Model → Controller)

1. `core.model.User | Conversation | Message` nắm toàn bộ dữ liệu (user info, hội thoại, tin nhắn).
2. `core.repository.*` (ConversationRepository, MessageRepository, UserRepository) thao tác PostgreSQL.
3. `omnichannel.bus.OmnichannelMessageBus` lưu inbound/outbound, cập nhật `Conversation.lastMessageAt` và `channelId`.
4. `omnichannel.router.OmnichannelRouter` nhận `UnifiedMessage`, tìm/khởi tạo `User` + `Conversation`, gọi MessageBus, xử lý auto-reply.
5. `api.controller.ChatApiController` cung cấp REST cho staff, trả DTO chuẩn cho frontend:
   - `GET /api/conversations` → `List<ConversationDto>`
   - `GET /api/conversations/{id}` → `ConversationDetailDto` (gồm `conversation` + `MessageListDto`)
   - `POST /api/conversations/{id}/messages` → `MessageDto` cho outbound mới tạo
6. `webhook.controller.*` là điểm vào inbound từng kênh (`WebhookController` cho Telegram/Messenger, `DiscordTestController` cho test Discord,...).

---

## 12. Luồng Telegram end-to-end

1. User chat với bot → Telegram gửi webhook tới `/webhook/telegram`.
2. `WebhookController` parse payload thông qua `TelegramParser` thành `UnifiedMessage`.
3. `OmnichannelRouter` tìm/khởi tạo `User` + `Conversation`, lưu inbound qua `OmnichannelMessageBus` → `MessageRepository`.
4. Nếu là lần đầu, router có thể gửi welcome/auto-reply theo cấu hình.
5. Staff UI gọi `GET /api/conversations` nhận `ConversationDto` (trường chính: `id`, `userName`, `channelType`, `lastMessagePreview`, `lastMessageAt`, `unreadCount`...), rồi `GET /api/conversations/{id}` để lấy `ConversationDetailDto` (bao gồm block `conversation` + `messages` với `MessageDto` và metadata `MessageListDto`).
6. Staff trả lời bằng `POST /api/conversations/{id}/messages`: controller chọn `TelegramConnector`, dùng `user.platformUserId` gọi Telegram Bot API. API trả về `MessageDto` của outbound.
7. `OmnichannelMessageBus.saveOutboundMessage` lưu bản ghi outbound để đồng bộ UI và cập nhật `ConversationDto` khi frontend reload.

API liên quan:
- `/webhook/telegram`
- `/test/telegram/setup-webhook`, `/test/telegram/webhook-info`, `/test/telegram/send-message`
- `/api/conversations`, `/api/conversations/{id}`

---

## 13. Luồng Discord end-to-end

1. `DiscordGatewayService` đăng nhập JDA với intents `GUILD_MESSAGES`, `MESSAGE_CONTENT`, `DIRECT_MESSAGES`.
2. `onMessageReceived` chuyển event thành `UnifiedMessage` (gồm `platformUserId`, `channelId`, `content`) rồi chuyển cho `OmnichannelRouter`.
3. `Conversation.channelId` được lưu để staff reply đúng channel công khai.
4. Staff vẫn dùng chung API `GET /api/conversations`, `GET /api/conversations/{id}` để xem dữ liệu (`ConversationDto`, `ConversationDetailDto` như Telegram).
5. Khi staff gửi `POST /api/conversations/{id}/messages`, controller kiểm tra `channelType=DISCORD` và dùng `conversation.channelId` gọi `DiscordConnector` → REST `POST /channels/{channelId}/messages` với header `Bot <token>`. Kết quả trả về `MessageDto`.
6. Các API kiểm thử:
   - `POST /test/discord/send-message`
   - `GET /test/discord/channel-info`
   - `POST /test/discord/create-invite`
   - `GET /test/discord/channels`

Ghi chú:
- Luồng text dùng channel support có sẵn, không tạo thread/channel động.
- Voice call: chỉ gửi invite tới voice channel native Discord, không đi qua Omnichannel.