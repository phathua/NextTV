# NextTV Widget Protocol SDK (NWP-1)

Bộ công cụ phát triển mở rộng (Addon/Plugin SDK) dành cho các lập trình viên cộng đồng muốn xây dựng và tích hợp tiện ích mới vào **NextTV Launcher**.

---

## 1. Tổng quan Kiến trúc

NextTV sử dụng giao thức **Widget Protocol NWP-1** lấy cảm hứng từ Stremio Addon Manifest và Cloudstream Extensions. Phân hệ bao gồm:

1. **Manifest File (`manifest.json`)**: Định nghĩa ID, phiên bản, tên, mẫu thẻ hiển thị (template) và endpoint lấy dữ liệu.
2. **Feed Response**: Trả về dữ liệu chuẩn JSON tuân thủ đúng 1 trong 4 mẫu thẻ Apple tvOS.
3. **Zero Android Code Needed**: Lập trình viên không cần biết Java/Kotlin hay kiến trúc Android TV. Chỉ cần triển khai một Cloudflare Worker (hoặc Node.js / Python API) trả về đúng định dạng JSON.

---

## 2. 4 Mẫu Thẻ Giao diện TV (Card UI Templates)

| Template | Tên mẫu | Mục đích sử dụng | Ví dụ thực tế |
|---|---|---|---|
| `hero_metric` | Số liệu trọng tâm | Hiển thị 1 chỉ số lớn, đơn vị, trạng thái, và tối đa 3 mục phụ dạng pill | Thời tiết, Giá Vàng SJC, Giá Bitcoin/Crypto, Tỉ giá ngoại tệ, Nhiệt độ phòng Home Assistant |
| `scoreboard` | Bảng điểm thi đấu | Hiển thị đối đầu trực tiếp (Home vs Away), logo 2 đội, tỉ số, chấm đỏ LIVE | Bóng đá (Ngoại hạng Anh, C1), Bóng rổ (NBA), Quần vợt (ATP), Đua xe F1, Esports |
| `media_banner` | Biểu ngữ truyền thông | Thumbnail 16:9 sắc nét, thời lượng, tiêu đề, tên kênh/tác giả, lượt xem | YouTube Trending, Twitch Streams, Trailer phim mới, Podcast nổi bật |
| `news_carousel` | Tin tức tuần hoàn | Tiêu đề tin, ảnh đại diện, nguồn báo, tự động xoay vòng sau mỗi chu kỳ | VnExpress, Tuổi Trẻ, Báo Quốc tế, Tin tức công nghệ, Cảnh báo khẩn cấp |

Xem chi tiết quy cách từng mẫu trong thư mục `templates/`.

---

## 3. Hướng dẫn Tạo Widget Mới trong 10 Phút

### Bước 1: Khai báo `manifest.json`
Tạo file manifest đại diện cho widget của bạn (ví dụ: `widgets/gold/manifest.json`):

```json
{
  "id": "com.community.gold-sjc",
  "name": "Giá Vàng SJC",
  "version": "1.0.0",
  "template": "hero_metric",
  "description": "Cập nhật giá vàng miếng SJC và vàng nhẫn 9999 theo thời gian thực",
  "refreshIntervalSec": 1800,
  "endpoint": "https://your-worker.workers.dev/api/feed"
}
```

### Bước 2: Viết Worker Endpoint
Triển khai hàm xử lý trả về dữ liệu tuân thủ schema `hero_metric`:

```typescript
export default {
  async fetch(request) {
    // Cào hoặc truy vấn API giá vàng
    return new Response(JSON.stringify({
      template: "hero_metric",
      ttl: 1800,
      headerLeft: "Vàng SJC Hà Nội",
      headerRight: "+0.5%",
      metricValue: "89.50",
      pillText: "Mua 87.50 • Bán 89.50",
      subItems: [
        { label: "Nhẫn 9999", iconCode: 1, value: "88.20" },
        { label: "Thế giới", iconCode: 1, value: "$2,740" }
      ]
    }), {
      headers: { "Content-Type": "application/json; charset=utf-8" }
    });
  }
};
```

### Bước 3: Nạp vào NextTV Launcher
1. Mở NextTV trên Android TV.
2. Điều hướng tới: **Cài đặt tiện ích** -> **Thêm kho Repo tiện ích**.
3. Nhập đường dẫn manifest hoặc repo index của bạn. NextTV sẽ tự động xác thực và hiển thị thẻ lên màn hình chính.
