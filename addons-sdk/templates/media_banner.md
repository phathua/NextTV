# Template: `media_banner`

Mẫu thẻ hiển thị nội dung truyền thông đa phương tiện dạng banner 16:9 kèm tiêu đề, thời lượng, số lượt xem và tên kênh.

## 1. Trường Dữ liệu
- `template`: `"media_banner"` (bắt buộc)
- `ttl`: Thời gian cache tính bằng giây (vd: `21600` = 6 giờ)
- `items`: Danh sách các video:
  - `id`: Mã video (vd: YouTube video ID)
  - `title`: Tiêu đề video
  - `channelTitle`: Tên kênh hoặc tác giả
  - `viewCountText`: Chuỗi lượt xem (vd: `"1.2 Tr lượt xem"`)
  - `durationText`: Chuỗi thời lượng (vd: `"14:25"`)
  - `thumbnailUrl`: Đường dẫn ảnh đại diện 16:9
  - `categoryLabel`: Nhãn chuyên mục (vd: `"Thịnh hành"`)

## 2. Mock JSON Ví dụ
```json
{
  "template": "media_banner",
  "ttl": 21600,
  "items": [
    {
      "id": "dQw4w9WgXcQ",
      "title": "Highlight Trận Chung Kết Cúp C1 Châu Âu 2026",
      "channelTitle": "Bóng Đá TV",
      "viewCountText": "850 N lượt xem",
      "durationText": "12:45",
      "thumbnailUrl": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
      "categoryLabel": "Thịnh hành"
    }
  ]
}
```
