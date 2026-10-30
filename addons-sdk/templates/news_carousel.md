# Template: `news_carousel`

Mẫu thẻ hiển thị tin tức báo chí, tự động xoay vòng tuần hoàn (carousel) theo chu kỳ định sẵn.

## 1. Trường Dữ liệu
- `template`: `"news_carousel"` (bắt buộc)
- `ttl`: Thời gian cache tính bằng giây (vd: `3600` = 1 giờ)
- `items`: Danh sách các bài viết:
  - `title`: Tiêu đề bài báo
  - `link`: Đường dẫn mở bài báo trong trình đọc tin
  - `imageUrl`: Đường dẫn ảnh minh họa bài báo
  - `pubDate`: Thời gian xuất bản
  - `source`: Nguồn báo chí (vd: `"VnExpress"`, `"Tuổi Trẻ"`)
  - `category`: Chuyên mục tin tức (vd: `"TIN NÓNG"`, `"Công nghệ"`)

## 2. Mock JSON Ví dụ
```json
{
  "template": "news_carousel",
  "ttl": 3600,
  "items": [
    {
      "title": "Việt Nam đẩy mạnh chuyển đổi số và công nghệ trí tuệ nhân tạo",
      "link": "https://vnexpress.net/cong-nghe-ai-viet-nam-12345.html",
      "imageUrl": "https://i1-vnexpress.vnecdn.net/2026/10/28/ai-tech.jpg",
      "pubDate": "2026-10-28 14:30:00",
      "source": "VnExpress",
      "category": "TIN NÓNG"
    }
  ]
}
```
