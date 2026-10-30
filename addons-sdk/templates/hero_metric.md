# Template: `hero_metric`

Mẫu thẻ hiển thị 1 số liệu chính (Hero Metric) kèm tiêu đề, huy hiệu phụ và danh sách 3 mục con dạng pill.

## 1. Trường Dữ liệu
- `template`: `"hero_metric"` (bắt buộc)
- `ttl`: Thời gian cache tính bằng giây (vd: `10800` = 3 giờ)
- `headerLeft`: Tiêu đề góc trái trên (vd: `"Hà Nội"`, `"Vàng SJC"`)
- `headerRight`: Trạng thái góc phải trên (vd: `"Nhiều mây"`, `"+1.2%"`)
- `metricValue`: Chuỗi số liệu chính cỡ chữ lớn (vd: `"28°"`, `"89.50"`, `"$68,400"`)
- `pillText`: Chuỗi phụ nổi bật (vd: `"Độ ẩm 72%"`, `"Mua 87.50 • Bán 89.50"`)
- `iconCode`: Mã icon tương ứng (vd: WMO weather code)
- `subItems`: Mảng tối đa 3 phần tử:
  - `label`: Nhãn (vd: `"Mai"`, `"T3"`, `"Nhẫn 9999"`)
  - `iconCode`: Mã icon
  - `value`: Giá trị (vd: `"30°"`, `"88.20"`)

## 2. Mock JSON Ví dụ
```json
{
  "template": "hero_metric",
  "ttl": 10800,
  "headerLeft": "TP. Hồ Chí Minh",
  "headerRight": "Trời quang đãng",
  "metricValue": "31°",
  "rawTemp": 31.0,
  "humidity": 65,
  "iconCode": 0,
  "pillText": "Độ ẩm 65%",
  "subItems": [
    { "label": "Mai", "iconCode": 1, "value": "32°" },
    { "label": "T5", "iconCode": 2, "value": "31°" },
    { "label": "T6", "iconCode": 61, "value": "29°" }
  ]
}
```
