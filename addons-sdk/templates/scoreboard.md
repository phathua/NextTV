# Template: `scoreboard`

Mẫu thẻ hiển thị bảng điểm trực tiếp giữa 2 đội (Home vs Away) cho thể thao và giải đấu.

## 1. Trường Dữ liệu
- `template`: `"scoreboard"` (bắt buộc)
- `ttl`: Thời gian cache tính bằng giây (vd: `60`)
- `sportType`: Định danh môn thể thao (`soccer`, `basketball`, `tennis`, `f1`, v.v.)
- `items`: Danh sách các trận đấu:
  - `id`: Mã trận đấu duy nhất
  - `league`: Tên giải đấu (vd: `"Ngoại hạng Anh"`, `"NBA"`)
  - `homeName`: Tên đội nhà (vd: `"Arsenal"`)
  - `homeShort`: Tên viết tắt đội nhà (vd: `"ARS"`)
  - `homeLogo`: URL ảnh logo đội nhà
  - `homeScore`: Điểm/bàn thắng đội nhà
  - `awayName`: Tên đội khách (vd: `"Chelsea"`)
  - `awayShort`: Tên viết tắt đội khách (vd: `"CHE"`)
  - `awayLogo`: URL ảnh logo đội khách
  - `awayScore`: Điểm/bàn thắng đội khách
  - `statusState`: Trạng thái (`"in"`: Đang diễn ra, `"pre"`: Sắp diễn ra, `"post"`: Đã kết thúc)
  - `statusText`: Chi tiết trạng thái (vd: `"68'"`, `"FT"`, `"19:30"`, `"VS"`)
  - `isLive`: `true` nếu trận đấu đang trực tiếp

## 2. Mock JSON Ví dụ
```json
{
  "template": "scoreboard",
  "ttl": 60,
  "sportType": "soccer",
  "items": [
    {
      "id": "401547890",
      "sportType": "soccer",
      "league": "Ngoại hạng Anh",
      "homeName": "Arsenal",
      "homeShort": "ARS",
      "homeLogo": "https://a.espncdn.com/i/teamlogos/soccer/500/359.png",
      "homeScore": "2",
      "awayName": "Chelsea",
      "awayShort": "CHE",
      "awayLogo": "https://a.espncdn.com/i/teamlogos/soccer/500/363.png",
      "awayScore": "1",
      "statusState": "in",
      "statusText": "72'",
      "isLive": true
    }
  ]
}
```
