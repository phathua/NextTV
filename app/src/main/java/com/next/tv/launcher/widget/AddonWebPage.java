package com.next.tv.launcher.widget;

/**
 * Generates responsive, anti-blue dark mode HTML for mobile browser configuration.
 * Strictly adheres to Zero-Emoji and Pure Vietnamese rules.
 */
public final class AddonWebPage {
    private AddonWebPage() {}

    public static String getHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\">\n" +
                "  <title>Cấu hình Tiện ích NextTV</title>\n" +
                "  <style>\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "    body {\n" +
                "      background-color: #121215;\n" +
                "      color: #F4F4F5;\n" +
                "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n" +
                "      padding: 16px;\n" +
                "      max-width: 600px;\n" +
                "      margin: 0 auto;\n" +
                "      -webkit-font-smoothing: antialiased;\n" +
                "    }\n" +
                "    header {\n" +
                "      padding: 16px 0 20px 0;\n" +
                "      border-bottom: 1px solid #27272A;\n" +
                "      margin-bottom: 18px;\n" +
                "    }\n" +
                "    h1 {\n" +
                "      font-size: 20px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: 0.5px;\n" +
                "      color: #FFFFFF;\n" +
                "    }\n" +
                "    .sub {\n" +
                "      font-size: 13px;\n" +
                "      color: #A1A1AA;\n" +
                "      margin-top: 4px;\n" +
                "    }\n" +
                "    .tabs {\n" +
                "      display: flex;\n" +
                "      background: #18181B;\n" +
                "      border-radius: 10px;\n" +
                "      padding: 4px;\n" +
                "      margin-bottom: 20px;\n" +
                "      border: 1px solid #27272A;\n" +
                "    }\n" +
                "    .tab {\n" +
                "      flex: 1;\n" +
                "      text-align: center;\n" +
                "      padding: 10px 4px;\n" +
                "      font-size: 13px;\n" +
                "      font-weight: 600;\n" +
                "      color: #A1A1AA;\n" +
                "      border-radius: 7px;\n" +
                "      cursor: pointer;\n" +
                "      transition: all 0.2s;\n" +
                "      border: none;\n" +
                "      background: none;\n" +
                "    }\n" +
                "    .tab.active {\n" +
                "      background: #27272A;\n" +
                "      color: #FFFFFF;\n" +
                "      box-shadow: 0 1px 3px rgba(0,0,0,0.4);\n" +
                "    }\n" +
                "    .card {\n" +
                "      background: #18181B;\n" +
                "      border: 1px solid #27272A;\n" +
                "      border-radius: 12px;\n" +
                "      padding: 14px 16px;\n" +
                "      margin-bottom: 12px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: space-between;\n" +
                "    }\n" +
                "    .card-info { flex: 1; padding-right: 12px; }\n" +
                "    .card-title {\n" +
                "      font-size: 15px;\n" +
                "      font-weight: 600;\n" +
                "      color: #FFFFFF;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 8px;\n" +
                "    }\n" +
                "    .badge {\n" +
                "      font-size: 10px;\n" +
                "      font-weight: 600;\n" +
                "      padding: 2px 6px;\n" +
                "      border-radius: 4px;\n" +
                "      text-transform: uppercase;\n" +
                "      background: #27272A;\n" +
                "      color: #A1A1AA;\n" +
                "    }\n" +
                "    .badge.addon { background: #0284C7; color: #FFFFFF; }\n" +
                "    .card-desc {\n" +
                "      font-size: 12px;\n" +
                "      color: #71717A;\n" +
                "      margin-top: 4px;\n" +
                "      line-height: 1.4;\n" +
                "    }\n" +
                "    .btn {\n" +
                "      padding: 8px 14px;\n" +
                "      font-size: 13px;\n" +
                "      font-weight: 600;\n" +
                "      border-radius: 8px;\n" +
                "      border: none;\n" +
                "      cursor: pointer;\n" +
                "      transition: background 0.2s;\n" +
                "    }\n" +
                "    .btn-primary { background: #38BDF8; color: #09090B; }\n" +
                "    .btn-secondary { background: #27272A; color: #F4F4F5; }\n" +
                "    .btn-danger { background: #EF4444; color: #FFFFFF; }\n" +
                "    .btn-toggle-on { background: #22C55E; color: #FFFFFF; min-width: 60px; }\n" +
                "    .btn-toggle-off { background: #3F3F46; color: #A1A1AA; min-width: 60px; }\n" +
                "    .reorder-btns { display: flex; gap: 4px; margin-right: 8px; }\n" +
                "    .reorder-btn {\n" +
                "      width: 32px;\n" +
                "      height: 32px;\n" +
                "      background: #27272A;\n" +
                "      color: #F4F4F5;\n" +
                "      border: none;\n" +
                "      border-radius: 6px;\n" +
                "      font-size: 14px;\n" +
                "      cursor: pointer;\n" +
                "    }\n" +
                "    .input-group {\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      gap: 8px;\n" +
                "      margin-bottom: 16px;\n" +
                "    }\n" +
                "    input[type=\"url\"] {\n" +
                "      width: 100%;\n" +
                "      padding: 12px 14px;\n" +
                "      background: #18181B;\n" +
                "      border: 1px solid #27272A;\n" +
                "      border-radius: 8px;\n" +
                "      color: #FFFFFF;\n" +
                "      font-size: 14px;\n" +
                "      outline: none;\n" +
                "    }\n" +
                "    input[type=\"url\"]:focus { border-color: #38BDF8; }\n" +
                "    .toast {\n" +
                "      position: fixed;\n" +
                "      bottom: 24px;\n" +
                "      left: 50%;\n" +
                "      transform: translateX(-50%);\n" +
                "      background: #27272A;\n" +
                "      border: 1px solid #38BDF8;\n" +
                "      color: #FFFFFF;\n" +
                "      padding: 10px 18px;\n" +
                "      border-radius: 20px;\n" +
                "      font-size: 13px;\n" +
                "      font-weight: 500;\n" +
                "      opacity: 0;\n" +
                "      transition: opacity 0.3s;\n" +
                "      pointer-events: none;\n" +
                "      z-index: 1000;\n" +
                "    }\n" +
                "    .toast.show { opacity: 1; }\n" +
                "    .empty-state { text-align: center; color: #71717A; padding: 40px 16px; font-size: 14px; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <header>\n" +
                "    <h1>CẤU HÌNH TIỆN ÍCH NEXTTV</h1>\n" +
                "    <div class=\"sub\">Đồng bộ trực tiếp với màn hình TV</div>\n" +
                "  </header>\n" +
                "\n" +
                "  <div class=\"tabs\">\n" +
                "    <button class=\"tab active\" onclick=\"showTab('installed')\">Tiện ích đã cài</button>\n" +
                "    <button class=\"tab\" onclick=\"showTab('store')\">Cửa hàng tiện ích</button>\n" +
                "    <button class=\"tab\" onclick=\"showTab('url')\">Thêm bằng liên kết</button>\n" +
                "  </div>\n" +
                "\n" +
                "  <div id=\"tab-installed\"></div>\n" +
                "  <div id=\"tab-store\" style=\"display:none;\"></div>\n" +
                "  <div id=\"tab-url\" style=\"display:none;\">\n" +
                "    <div class=\"card\" style=\"flex-direction:column; align-items:stretch;\">\n" +
                "      <div class=\"input-group\">\n" +
                "        <label style=\"font-size:13px; font-weight:600; color:#E4E4E7;\">Địa chỉ liên kết tệp cấu hình</label>\n" +
                "        <input type=\"url\" id=\"manifestUrl\" placeholder=\"https://example.com/cau-hinh.json\" autocomplete=\"off\">\n" +
                "      </div>\n" +
                "      <button class=\"btn btn-primary\" style=\"width:100%;\" onclick=\"installUrl()\">Cài đặt tiện ích</button>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div id=\"toast\" class=\"toast\"></div>\n" +
                "\n" +
                "  <script>\n" +
                "    const sessionToken = new URLSearchParams(window.location.search).get('token') || '';\n" +
                "    function apiUrl(path) {\n" +
                "      return sessionToken ? (path + (path.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(sessionToken)) : path;\n" +
                "    }\n" +
                "    function escapeHtml(str) {\n" +
                "      if (str === null || str === undefined) return '';\n" +
                "      return String(str)\n" +
                "        .replace(/&/g, '&amp;')\n" +
                "        .replace(/</g, '&lt;')\n" +
                "        .replace(/>/g, '&gt;')\n" +
                "        .replace(/\"/g, '&quot;')\n" +
                "        .replace(/'/g, '&#39;');\n" +
                "    }\n" +
                "    let state = { installed: [], store: [] };\n" +
                "    function showToast(msg) {\n" +
                "      const t = document.getElementById('toast');\n" +
                "      t.innerText = msg;\n" +
                "      t.classList.add('show');\n" +
                "      setTimeout(() => t.classList.remove('show'), 2000);\n" +
                "    }\n" +
                "    function showTab(name) {\n" +
                "      document.querySelectorAll('.tab').forEach((b, i) => {\n" +
                "        b.classList.toggle('active', (name === 'installed' && i === 0) || (name === 'store' && i === 1) || (name === 'url' && i === 2));\n" +
                "      });\n" +
                "      document.getElementById('tab-installed').style.display = name === 'installed' ? 'block' : 'none';\n" +
                "      document.getElementById('tab-store').style.display = name === 'store' ? 'block' : 'none';\n" +
                "      document.getElementById('tab-url').style.display = name === 'url' ? 'block' : 'none';\n" +
                "    }\n" +
                "    async function loadState() {\n" +
                "      try {\n" +
                "        const res = await fetch(apiUrl('/api/state'));\n" +
                "        if (!res.ok) {\n" +
                "          showToast('Mã phiên không hợp lệ hoặc đã hết hạn');\n" +
                "          return;\n" +
                "        }\n" +
                "        state = await res.json();\n" +
                "        renderInstalled();\n" +
                "        renderStore();\n" +
                "      } catch (e) { showToast('Lỗi nạp dữ liệu từ TV'); }\n" +
                "    }\n" +
                "    function renderInstalled() {\n" +
                "      const c = document.getElementById('tab-installed');\n" +
                "      if (!state.installed || state.installed.length === 0) {\n" +
                "        c.innerHTML = '<div class=\"empty-state\">Chưa có tiện ích nào được kích hoạt</div>';\n" +
                "        return;\n" +
                "      }\n" +
                "      c.innerHTML = state.installed.map((item, idx) => `\n" +
                "        <div class=\"card\">\n" +
                "          <div class=\"card-info\">\n" +
                "            <div class=\"card-title\">\n" +
                "              ${escapeHtml(item.name)}\n" +
                "              <span class=\"badge ${item.isBuiltIn ? '' : 'addon'}\">${item.isBuiltIn ? 'Mặc định' : 'Mở rộng'}</span>\n" +
                "            </div>\n" +
                "            <div class=\"card-desc\">${escapeHtml(item.description || item.author || '')}</div>\n" +
                "          </div>\n" +
                "          <div style=\"display:flex; align-items:center;\">\n" +
                "            <div class=\"reorder-btns\">\n" +
                "              <button class=\"reorder-btn\" onclick=\"moveItem(${idx}, -1)\">▲</button>\n" +
                "              <button class=\"reorder-btn\" onclick=\"moveItem(${idx}, 1)\">▼</button>\n" +
                "            </div>\n" +
                "            <button class=\"btn ${item.isEnabled ? 'btn-toggle-on' : 'btn-toggle-off'}\" data-id=\"${escapeHtml(item.id)}\" onclick=\"toggleItem(this.getAttribute('data-id'), ${!item.isEnabled})\">\n" +
                "              ${item.isEnabled ? 'Bật' : 'Tắt'}\n" +
                "            </button>\n" +
                "            ${!item.isBuiltIn ? `<button class=\"btn btn-danger\" style=\"margin-left:6px;\" data-id=\"${escapeHtml(item.id)}\" onclick=\"uninstallItem(this.getAttribute('data-id'))\">Gỡ</button>` : ''}\n" +
                "          </div>\n" +
                "        </div>\n" +
                "      `).join('');\n" +
                "    }\n" +
                "    function renderStore() {\n" +
                "      const c = document.getElementById('tab-store');\n" +
                "      if (!state.store || state.store.length === 0) {\n" +
                "        c.innerHTML = '<div class=\"empty-state\">Đang nạp danh mục cửa hàng...</div>';\n" +
                "        return;\n" +
                "      }\n" +
                "      c.innerHTML = state.store.map(item => {\n" +
                "        const isInst = state.installed.some(a => a.id === item.id);\n" +
                "        return `\n" +
                "          <div class=\"card\">\n" +
                "            <div class=\"card-info\">\n" +
                "              <div class=\"card-title\">\n" +
                "                ${escapeHtml(item.name)}\n" +
                "                <span class=\"badge\">${escapeHtml(item.version)}</span>\n" +
                "              </div>\n" +
                "              <div class=\"card-desc\">${escapeHtml(item.description || item.author || '')}</div>\n" +
                "            </div>\n" +
                "            <div>\n" +
                "              ${isInst ?\n" +
                "                '<button class=\"btn btn-secondary\" disabled>Đã cài</button>' :\n" +
                "                `<button class=\"btn btn-primary\" data-id=\"${escapeHtml(item.id)}\" onclick=\"installStoreItem(this.getAttribute('data-id'))\">Cài đặt</button>`\n" +
                "              }\n" +
                "            </div>\n" +
                "          </div>\n" +
                "        `;\n" +
                "      }).join('');\n" +
                "    }\n" +
                "    async function toggleItem(id, enabled) {\n" +
                "      await fetch(apiUrl('/api/addons/toggle'), { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ id, enabled }) });\n" +
                "      showToast(enabled ? 'Đã bật trên TV' : 'Đã tắt trên TV');\n" +
                "      loadState();\n" +
                "    }\n" +
                "    async function moveItem(index, dir) {\n" +
                "      const target = index + dir;\n" +
                "      if (target < 0 || target >= state.installed.length) return;\n" +
                "      const item = state.installed.splice(index, 1)[0];\n" +
                "      state.installed.splice(target, 0, item);\n" +
                "      const order = state.installed.map(a => a.id);\n" +
                "      await fetch(apiUrl('/api/addons/reorder'), { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ order }) });\n" +
                "      showToast('Đã lưu thứ tự trên TV');\n" +
                "      renderInstalled();\n" +
                "    }\n" +
                "    async function uninstallItem(id) {\n" +
                "      if (!confirm('Gỡ tiện ích này khỏi TV?')) return;\n" +
                "      await fetch(apiUrl('/api/addons/uninstall'), { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ id }) });\n" +
                "      showToast('Đã gỡ tiện ích');\n" +
                "      loadState();\n" +
                "    }\n" +
                "    async function installStoreItem(id) {\n" +
                "      const item = state.store.find(a => a.id === id);\n" +
                "      if (!item) return;\n" +
                "      await fetch(apiUrl('/api/addons/install'), { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(item) });\n" +
                "      showToast('Đã cài đặt lên TV');\n" +
                "      loadState();\n" +
                "    }\n" +
                "    async function installUrl() {\n" +
                "      const url = document.getElementById('manifestUrl').value.trim();\n" +
                "      if (!url) { alert('Vui lòng nhập đường dẫn liên kết'); return; }\n" +
                "      const res = await fetch(apiUrl('/api/addons/url'), { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ url }) });\n" +
                "      const data = await res.json();\n" +
                "      if (data.success) {\n" +
                "        showToast('Đã cài đặt thành công');\n" +
                "        document.getElementById('manifestUrl').value = '';\n" +
                "        showTab('installed');\n" +
                "        loadState();\n" +
                "      } else {\n" +
                "        alert(data.message || 'Cài đặt thất bại');\n" +
                "      }\n" +
                "    }\n" +
                "    loadState();\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
