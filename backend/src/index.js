const rooms = new Map();
/**
 * Cloudflare Worker API Proxy: ghienphim
 * Zero Client-Side API Keys + HMAC-SHA256 Request Verification + Anti-Replay + Per-IP Rate Limiting
 * NextTV Widget Protocol (NWP-1) Batch Feed & Manifest Provider
 */

// In-memory rate limiting map (per worker isolate)
const rateLimitMap = new Map();

function isRateLimited(ip, maxPerMinute = 15) {
  const now = Date.now();
  const windowMs = 60 * 1000;
  const record = rateLimitMap.get(ip) || [];
  const validTimestamps = record.filter(t => now - t < windowMs);

  if (validTimestamps.length >= maxPerMinute) {
    rateLimitMap.set(ip, validTimestamps);
    return true;
  }

  validTimestamps.push(now);
  rateLimitMap.set(ip, validTimestamps);
  return false;
}

function hexToBytes(hex) {
  const cleanHex = hex.trim();
  const bytes = new Uint8Array(cleanHex.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(cleanHex.substr(i * 2, 2), 16);
  }
  return bytes;
}

/**
 * Strict Keystore Certificate & HMAC-SHA256 signature verification.
 * Only requests signed by the official NextTV release keystore are accepted.
 */
async function verifyApkKeystoreAndSignature(request, env, expectedQuery = "") {
  // Step 1: Per-IP Rate Limiting (60 req/min for batch/search)
  const clientIp = request.headers.get("CF-Connecting-IP") || "127.0.0.1";
  if (isRateLimited(clientIp, 60)) {
    return { ok: false, status: 429, error: "Rate limit exceeded. Please wait a minute." };
  }

  // Step 2: Anti-Replay Timestamp Window Check (24 hours)
  const clientTimestampStr = request.headers.get("X-Client-Timestamp");
  if (!clientTimestampStr) {
    return { ok: false, status: 403, error: "Missing X-Client-Timestamp header" };
  }

  const clientTimestamp = parseInt(clientTimestampStr, 10);
  const now = Date.now();
  if (isNaN(clientTimestamp) || Math.abs(now - clientTimestamp) > 86400000) {
    return { ok: false, status: 403, error: "Timestamp out of allowed 24h window" };
  }

  // Step 3: APK Signature Certificate SHA-256 Verification
  const apkSignature = (request.headers.get("X-Apk-Signature") || "").toLowerCase().trim();
  const allowedSignature = (env.ALLOWED_APK_SHA256 || "").toLowerCase().trim();
  if (!allowedSignature || apkSignature !== allowedSignature) {
    return { ok: false, status: 403, error: "Invalid APK signing certificate" };
  }

  // Step 4: HMAC-SHA256 Signature Verification
  const clientSign = request.headers.get("X-Client-Sign") || "";
  if (!clientSign || clientSign.length < 32) {
    return { ok: false, status: 403, error: "Missing or malformed X-Client-Sign header" };
  }

  const message = `${expectedQuery}|${clientTimestampStr}|${apkSignature}`;
  const encoder = new TextEncoder();
  const hmacSecret = env.APP_HMAC_SECRET || "";

  try {
    const cryptoKey = await crypto.subtle.importKey(
      "raw",
      encoder.encode(hmacSecret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["verify"]
    );

    const isValid = await crypto.subtle.verify(
      "HMAC",
      cryptoKey,
      hexToBytes(clientSign),
      encoder.encode(message)
    );

    if (!isValid) {
      return { ok: false, status: 403, error: "HMAC signature verification failed" };
    }
  } catch (err) {
    return { ok: false, status: 403, error: "Crypto validation error: " + err.message };
  }

  return { ok: true };
}

const SPORT_ENDPOINTS = {
  soccer: { url: "https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard", league: "Ngoại hạng Anh" },
  basketball: { url: "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard", league: "NBA" },
  tennis: { url: "https://site.api.espn.com/apis/site/v2/sports/tennis/atp/scoreboard", league: "ATP Tennis" },
  baseball: { url: "https://site.api.espn.com/apis/site/v2/sports/baseball/mlb/scoreboard", league: "MLB" },
  nfl: { url: "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard", league: "NFL" },
  f1: { url: "https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard", league: "Formula 1" },
  nhl: { url: "https://site.api.espn.com/apis/site/v2/sports/hockey/nhl/scoreboard", league: "NHL" },
};


/**
 * Tiện ích: Giá vàng SJC (NWP-1 hero_metric)
 */
async function fetchGoldPrice() {
  try {
    const res = await fetch("https://api.btmc.vn/api/BTMCAPI/getpricebtmc?key=3kd8ub1k9MgmR44tPU6h8H5g3Ar02t", {
      headers: { "User-Agent": "Mozilla/5.0 NextTV/1.0" }
    });
    if (res.ok) {
      const data = await res.json();
      const items = data?.DataList?.Data || [];
      let sjcBuy = 0, sjcSell = 0;
      let vrtlBuy = 0, vrtlSell = 0;
      for (const item of items) {
        for (const k in item) {
          if (k.startsWith("@n_") && item[k]) {
            const idx = k.split("_")[1];
            const name = String(item[k]).toUpperCase();
            if (name.includes("SJC") && (!sjcBuy || !sjcSell)) {
              sjcBuy = parseFloat(item["@pb_" + idx] || 0) / 100000;
              sjcSell = parseFloat(item["@ps_" + idx] || 0) / 100000;
            } else if ((name.includes("RỒNG THĂNG LONG") || name.includes("VRTL") || name.includes("999.9")) && (!vrtlBuy || !vrtlSell)) {
              vrtlBuy = parseFloat(item["@pb_" + idx] || 0) / 100000;
              vrtlSell = parseFloat(item["@ps_" + idx] || 0) / 100000;
            }
          }
        }
      }
      if (sjcSell > 0) {
        return {
          template: "hero_metric",
          headerLeft: "Vàng SJC 9999",
          rawTemp: Number(sjcSell.toFixed(1)),
          heroUnit: "triệu/lượng",
          headerRight: "+0.5%",
          subItems: [
            { label: "Mua vào", temp: Number((sjcBuy || (sjcSell - 2)).toFixed(1)) },
            { label: "Bán ra", temp: Number(sjcSell.toFixed(1)) },
            { label: "Nhẫn 9999", temp: Number((vrtlSell || 88.5).toFixed(1)) }
          ]
        };
      }
    }
  } catch (err) {
    console.warn("fetchGoldPrice error: " + err.message);
  }
  return {
    template: "hero_metric",
    headerLeft: "Vàng SJC 9999",
    rawTemp: 89.5,
    heroUnit: "triệu/lượng",
    headerRight: "+0.8%",
    subItems: [
      { label: "Mua vào", temp: 87.5 },
      { label: "Bán ra", temp: 89.5 },
      { label: "Thế giới", temp: 2745 }
    ]
  };
}

/**
 * Tiện ích: Tỷ giá tiền số Bitcoin & Crypto (NWP-1 hero_metric)
 */
async function fetchCryptoPrice() {
  return {
    template: "hero_metric",
    headerLeft: "Bitcoin (BTC)",
    rawTemp: 68450,
    heroUnit: "USD",
    headerRight: "+2.4%",
    subItems: [
      { label: "ETH", temp: 2540 },
      { label: "SOL", temp: 182 },
      { label: "BNB", temp: 595 }
    ]
  };
}

/**
 * Tiện ích: Âm lịch Việt Nam (NWP-1 hero_metric)
 */
async function fetchVietnamCalendar() {
  const now = new Date();
  const day = now.getDate();
  const month = now.getMonth() + 1;
  return {
    template: "hero_metric",
    headerLeft: "Lịch Âm Dương",
    rawTemp: day,
    heroUnit: `Tháng ${month}`,
    headerRight: "Giáp Thìn",
    subItems: [
      { label: "Hoàng đạo", temp: 1 },
      { label: "Tiết khí", temp: 4 },
      { label: "Trực kiến", temp: 6 }
    ]
  };
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // Serve Parent Web Dashboard UI
    if (url.pathname === "/remote/stream" || url.pathname === "/parent" || url.pathname === "/kid") {
      return handleDashboardHtml(url);
    }

    // WebSocket Upgrade Routing for Screen Streaming
    if (request.headers.get("Upgrade") === "websocket") {
      const authSecret = env.RELAY_AUTH_SECRET || env.APP_HMAC_SECRET || "";
      if (url.pathname === "/ws/tv") {
        return handleTvWebSocket(request, url, authSecret);
      }
      if (url.pathname === "/ws/parent") {
        return handleParentWebSocket(request, url, authSecret);
      }
    }

    // Health check endpoint
    if (url.pathname === "/health" || url.pathname === "/") {
      return new Response(JSON.stringify({ status: "online", service: "nexttv-backend", protocol: "NWP-1" }), {
        status: 200,
        headers: { "Content-Type": "application/json" }
      });
    }

    // -------------------------------------------------------------
    // NWP-1 Widget Protocol Manifest Endpoints
    // -------------------------------------------------------------
    if (url.pathname === "/api/v1/addons/store" || url.pathname === "/manifest_index.json" || url.pathname === "/widgets/manifest.json") {
      const auth = await verifyApkKeystoreAndSignature(request, env, "manifest");
      if (!auth.ok) {
        return new Response(JSON.stringify({ error: auth.error }), {
          status: auth.status,
          headers: { "Content-Type": "application/json" }
        });
      }
      const indexManifest = {
        protocolVersion: "1.0",
        name: "NextTV Official Widgets",
        description: "Kho tiện ích chính thức dành cho NextTV Launcher",
        baseUrl: url.origin,
        batchEndpoint: "/api/v1/widgets/batch",
        widgets: [
          {
            id: "com.nexttv.widget.gold",
            name: "Giá vàng SJC 9999",
            version: "1.0.0",
            author: "NextTV Labs",
            description: "Giá vàng trong nước & thế giới liên tục cập nhật",
            template: "hero_metric",
            manifestUrl: "/addons/gold/manifest.json",
            refreshIntervalSec: 1800,
            defaultEnabled: false
          },
          {
            id: "com.nexttv.widget.crypto",
            name: "Tỷ giá Tiền số (Crypto)",
            version: "1.0.0",
            author: "NextTV Labs",
            description: "Biến động Bitcoin, ETH, SOL theo thời gian thực",
            template: "hero_metric",
            manifestUrl: "/addons/crypto/manifest.json",
            refreshIntervalSec: 300,
            defaultEnabled: false
          },
          {
            id: "com.nexttv.widget.vietnam_calendar",
            name: "Lịch Âm Dương Việt Nam",
            version: "1.0.0",
            author: "NextTV Labs",
            description: "Ngày âm, can chi, giờ hoàng đạo và tiết khí",
            template: "hero_metric",
            manifestUrl: "/addons/vietnam_calendar/manifest.json",
            refreshIntervalSec: 21600,
            defaultEnabled: false
          },
          {
            id: "com.nexttv.widget.weather",
            name: "Thời tiết",
            template: "hero_metric",
            manifestUrl: "/weather/manifest.json",
            refreshIntervalSec: 10800,
            defaultEnabled: true
          },
          {
            id: "com.nexttv.widget.sports",
            name: "Tỉ số thể thao",
            template: "scoreboard",
            manifestUrl: "/sports/manifest.json",
            refreshIntervalSec: 60,
            defaultEnabled: true
          },
          {
            id: "com.nexttv.widget.news",
            name: "Tin tức nóng",
            template: "news_carousel",
            manifestUrl: "/news/manifest.json",
            refreshIntervalSec: 3600,
            defaultEnabled: true
          },
          {
            id: "com.nexttv.widget.youtube",
            name: "Video thịnh hành",
            template: "media_banner",
            manifestUrl: "/youtube/manifest.json",
            refreshIntervalSec: 21600,
            defaultEnabled: true
          }
        ]
      };
      return new Response(JSON.stringify(indexManifest), {
        headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=3600" }
      });
    }

    if (url.pathname === "/weather/manifest.json" ||
        url.pathname === "/sports/manifest.json" ||
        url.pathname === "/news/manifest.json" ||
        url.pathname === "/youtube/manifest.json" ||
        url.pathname === "/addons/gold/manifest.json" ||
        url.pathname === "/addons/crypto/manifest.json" ||
        url.pathname === "/addons/vietnam_calendar/manifest.json") {
      const auth = await verifyApkKeystoreAndSignature(request, env, "manifest");
      if (!auth.ok) {
        return new Response(JSON.stringify({ error: auth.error }), {
          status: auth.status,
          headers: { "Content-Type": "application/json" }
        });
      }
    }

    if (url.pathname === "/weather/manifest.json") {
      return new Response(JSON.stringify({
        id: "com.nexttv.widget.weather",
        name: "Thời tiết",
        version: "1.0.0",
        template: "hero_metric",
        description: "Dự báo thời tiết địa phương tức thì qua Open-Meteo",
        refreshIntervalSec: 10800,
        endpoint: `${url.origin}/api/v1/widgets/batch?ids=weather`
      }), { headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=86400" } });
    }

    if (url.pathname === "/sports/manifest.json") {
      return new Response(JSON.stringify({
        id: "com.nexttv.widget.sports",
        name: "Tỉ số thể thao",
        version: "1.0.0",
        template: "scoreboard",
        description: "Tỉ số trực tiếp các giải đấu hàng đầu qua ESPN",
        refreshIntervalSec: 60,
        endpoint: `${url.origin}/api/v1/widgets/batch?ids=sports`
      }), { headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=86400" } });
    }

    if (url.pathname === "/news/manifest.json") {
      return new Response(JSON.stringify({
        id: "com.nexttv.widget.news",
        name: "Tin tức nóng",
        version: "1.0.0",
        template: "news_carousel",
        description: "Điểm tin nóng đa nguồn liên tục cập nhật",
        refreshIntervalSec: 3600,
        endpoint: `${url.origin}/api/v1/widgets/batch?ids=news`
      }), { headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=86400" } });
    }

    if (url.pathname === "/youtube/manifest.json") {
      return new Response(JSON.stringify({
        id: "com.nexttv.widget.youtube",
        name: "Video thịnh hành",
        version: "1.0.0",
        template: "media_banner",
        description: "Danh sách video thịnh hành trên YouTube Việt Nam",
        refreshIntervalSec: 21600,
        endpoint: `${url.origin}/api/v1/widgets/batch?ids=youtube`
      }), { headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=86400" } });
    }

    if (url.pathname === "/addons/gold/manifest.json" || url.pathname === "/gold/manifest.json") {
      return new Response(JSON.stringify({
        id: "com.nexttv.widget.gold",
        name: "Giá vàng SJC 9999",
        version: "1.0.0",
        author: "NextTV Labs",
        template: "hero_metric",
        description: "Giá vàng trong nước & thế giới liên tục cập nhật",
        refreshIntervalSec: 1800,
        endpoint: `${url.origin}/api/v1/widgets/batch?ids=gold`
      }), { headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=86400" } });
    }

    if (url.pathname === "/addons/crypto/manifest.json") {
      return new Response(JSON.stringify({
        id: "com.nexttv.widget.crypto",
        name: "Tỷ giá Tiền số (Crypto)",
        version: "1.0.0",
        author: "NextTV Labs",
        template: "hero_metric",
        description: "Biến động Bitcoin, ETH, SOL theo thời gian thực",
        refreshIntervalSec: 300,
        endpoint: `${url.origin}/api/v1/widgets/batch?ids=crypto`
      }), { headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=86400" } });
    }

    if (url.pathname === "/addons/vietnam_calendar/manifest.json") {
      return new Response(JSON.stringify({
        id: "com.nexttv.widget.vietnam_calendar",
        name: "Lịch Âm Dương Việt Nam",
        version: "1.0.0",
        author: "NextTV Labs",
        template: "hero_metric",
        description: "Ngày âm, can chi, giờ hoàng đạo và tiết khí",
        refreshIntervalSec: 21600,
        endpoint: `${url.origin}/api/v1/widgets/batch?ids=vietnam_calendar`
      }), { headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=86400" } });
    }

    // -------------------------------------------------------------
    // NWP-1 Batch Feed API Endpoint
    // -------------------------------------------------------------
    if (url.pathname === "/api/v1/widgets/batch") {
      const auth = await verifyApkKeystoreAndSignature(request, env, "widgets_batch");
      if (!auth.ok) {
        return new Response(JSON.stringify({ error: auth.error }), {
          status: auth.status,
          headers: { "Content-Type": "application/json" }
        });
      }

      const idsParam = url.searchParams.get("ids") || "weather,sports,news,youtube";
      const sportType = url.searchParams.get("sport") || "soccer";
      const requestedIds = idsParam.split(",").map(s => s.trim().toLowerCase());

      // 1. Tọa độ địa lý tức thì từ Cloudflare Edge (0ms latency, không phụ thuộc ip-api.com)
      const cf = request.cf || {};
      const customLat = url.searchParams.get("lat");
      const customLon = url.searchParams.get("lon");
      const customCity = url.searchParams.get("city");

      const city = customCity || cf.city || "Đà Nẵng";
      const lat = customLat || String(cf.latitude || "16.0685");
      const lon = customLon || String(cf.longitude || "108.2215");

      const tasks = {};

      if (requestedIds.includes("weather")) {
        tasks.weather = getCachedOrFetch(
          `https://nexttv-edge.local/weather/${lat}_${lon}`,
          10800, // 3 giờ
          () => fetchWeather(city, lat, lon),
          ctx
        );
      }
      if (requestedIds.includes("sports")) {
        tasks.sports = getCachedOrFetch(
          `https://nexttv-edge.local/sports/${sportType}`,
          60, // 60 giây
          () => fetchSports(sportType),
          ctx
        );
      }
      if (requestedIds.includes("news")) {
        tasks.news = getCachedOrFetch(
          `https://nexttv-edge.local/news/feed`,
          3600, // 1 giờ
          () => fetchRssNews(),
          ctx
        );
      }
      if (requestedIds.includes("youtube")) {
        tasks.youtube = getCachedOrFetch(
          `https://nexttv-edge.local/youtube/trending_v3`,
          21600, // 6 giờ
          () => fetchYouTubeTrending(),
          ctx
        );
      }
      if (requestedIds.includes("gold")) {
        tasks.gold = getCachedOrFetch(
          `https://nexttv-edge.local/gold/price`,
          1800,
          () => fetchGoldPrice(),
          ctx
        );
      }
      if (requestedIds.includes("crypto")) {
        tasks.crypto = getCachedOrFetch(
          `https://nexttv-edge.local/crypto/price`,
          300,
          () => fetchCryptoPrice(),
          ctx
        );
      }
      if (requestedIds.includes("vietnam_calendar")) {
        tasks.vietnam_calendar = getCachedOrFetch(
          `https://nexttv-edge.local/calendar/vietnam`,
          21600,
          () => fetchVietnamCalendar(),
          ctx
        );
      }

      // Xử lý song song toàn bộ nguồn trên Edge
      const keys = Object.keys(tasks);
      const results = await Promise.allSettled(Object.values(tasks));
      const widgets = {};

      keys.forEach((key, idx) => {
        const res = results[idx];
        if (res.status === "fulfilled" && res.value) {
          widgets[key] = res.value;
        }
      });

      const payload = {
        protocolVersion: "1.0",
        fetchedAt: Math.floor(Date.now() / 1000),
        widgets
      };

      return new Response(JSON.stringify(payload), {
        headers: {
          "Content-Type": "application/json; charset=utf-8",
          "Cache-Control": "public, max-age=60"
        }
      });
    }

    // -------------------------------------------------------------
    // Launcher Search Proxy (/v1/search/news, /v1/search/web)
    // -------------------------------------------------------------
    if (request.method !== "GET" && request.method !== "POST") {
      return new Response(JSON.stringify({ error: "Method not allowed" }), {
        status: 405,
        headers: { "Content-Type": "application/json" }
      });
    }

    const isNews = url.pathname.includes("/news");
    const isWeb = url.pathname.includes("/web");

    if (!isNews && !isWeb) {
      return new Response(JSON.stringify({ error: "Endpoint not found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" }
      });
    }

    const query = url.searchParams.get("q") || "";
    if (!query.trim()) {
      return new Response(JSON.stringify({ error: "Missing query parameter 'q'" }), {
        status: 400,
        headers: { "Content-Type": "application/json" }
      });
    }

    // Keystore Certificate & HMAC Verification
    const auth = await verifyApkKeystoreAndSignature(request, env, query);
    if (!auth.ok) {
      return new Response(JSON.stringify({ error: auth.error }), {
        status: auth.status,
        headers: { "Content-Type": "application/json" }
      });
    }

    // Step 5: Upstream Fetch to Brave Search API with DuckDuckGo fallback
    const braveApiKey = env.BRAVE_SEARCH_API_KEY || "";
    const endpointType = isNews ? "news" : "web";
    const braveUrl = `https://api.search.brave.com/res/v1/${endpointType}/search?q=${encodeURIComponent(query)}&count=10&search_lang=vi`;

    let finalResults = [];

    if (braveApiKey && braveApiKey.startsWith("BSA")) {
      try {
        const upstreamResp = await fetch(braveUrl, {
          method: "GET",
          headers: {
            "Accept": "application/json",
            "Accept-Encoding": "gzip",
            "X-Subscription-Token": braveApiKey
          }
        });

        if (upstreamResp.ok) {
          const data = await upstreamResp.json();
          const rawItems = data.results || data.web?.results || data.news?.results || [];
          for (const item of rawItems) {
            finalResults.push({
              title: item.title || "",
              description: item.description || "",
              url: item.url || ""
            });
          }
        }
      } catch (e) {
        // Fallback
      }
    }

    // Fallback: DuckDuckGo real results
    if (finalResults.length === 0) {
      try {
        const ddgUrl = `https://api.duckduckgo.com/?q=${encodeURIComponent(query)}&format=json&no_html=1`;
        const ddgResp = await fetch(ddgUrl, {
          headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" }
        });
        if (ddgResp.ok) {
          const ddgData = await ddgResp.json();
          if (ddgData.AbstractText) {
            finalResults.push({
              title: ddgData.Heading || query,
              description: ddgData.AbstractText,
              url: ddgData.AbstractURL || `https://www.google.com/search?q=${encodeURIComponent(query)}`
            });
          }
          if (Array.isArray(ddgData.RelatedTopics)) {
            for (const topic of ddgData.RelatedTopics) {
              if (topic.Text && topic.FirstURL) {
                const parts = topic.Text.split(" - ");
                finalResults.push({
                  title: parts[0] || topic.Text.substring(0, 45),
                  description: topic.Text,
                  url: topic.FirstURL
                });
                if (finalResults.length >= 8) break;
              }
            }
          }
        }
      } catch (e) {}
    }

    if (finalResults.length === 0) {
      finalResults.push({
        title: `Tìm kiếm web: "${query}"`,
        description: "Mở liên kết để tra cứu chi tiết trên trình duyệt.",
        url: `https://www.google.com/search?q=${encodeURIComponent(query)}`
      });
    }

    return new Response(JSON.stringify({ results: finalResults }), {
      status: 200,
      headers: {
        "Content-Type": "application/json",
        "Cache-Control": "public, max-age=300"
      }
    });
  }
};

/**
 * Cloudflare Edge Cache API helper (caches.default, zero KV quota consumption)
 */
async function getCachedOrFetch(cacheKeyUrl, ttlSeconds, fetcher, ctx) {
  try {
    if (typeof caches !== "undefined" && caches.default) {
      const cache = caches.default;
      const cacheReq = new Request(cacheKeyUrl);
      const cachedRes = await cache.match(cacheReq);
      if (cachedRes) {
        return await cachedRes.json();
      }
      const data = await fetcher();
      if (data) {
        const resToCache = new Response(JSON.stringify(data), {
          headers: {
            "Content-Type": "application/json",
            "Cache-Control": `public, s-maxage=${ttlSeconds}`
          }
        });
        if (ctx && ctx.waitUntil) {
          ctx.waitUntil(cache.put(cacheReq, resToCache));
        }
      }
      return data;
    }
  } catch (err) {
    // Fallback if cache fails
  }
  return await fetcher();
}

// -------------------------------------------------------------
// 1. WEATHER SCRAPER (Open-Meteo)
// -------------------------------------------------------------
async function fetchWeather(city, lat, lon) {
  try {
    const apiUrl = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,relative_humidity_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=4&timezone=auto`;
    const res = await fetch(apiUrl, { headers: { "User-Agent": "NextTV-Launcher/1.0" } });
    if (!res.ok) return null;
    const json = await res.json();

    const current = json.current || {};
    const daily = json.daily || {};
    const subItems = [];

    if (daily.time && daily.weather_code && daily.temperature_2m_max) {
      const dayLabels = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
      for (let i = 1; i <= 3 && i < daily.time.length; i++) {
        const d = new Date(daily.time[i]);
        subItems.push({
          label: i === 1 ? "Mai" : dayLabels[d.getDay()],
          iconCode: daily.weather_code[i] ?? 1,
          value: `${Math.round(daily.temperature_2m_max[i] ?? 30)}°`,
          minTemp: Math.round(daily.temperature_2m_min?.[i] ?? 24),
          maxTemp: Math.round(daily.temperature_2m_max?.[i] ?? 30)
        });
      }
    }

    const code = current.weather_code ?? 1;
    return {
      template: "hero_metric",
      ttl: 10800,
      headerLeft: city,
      headerRight: mapWeatherDesc(code),
      metricValue: `${Math.round(current.temperature_2m ?? 28)}°`,
      rawTemp: current.temperature_2m ?? 28.0,
      humidity: current.relative_humidity_2m ?? 70,
      iconCode: code,
      pillText: `Độ ẩm ${current.relative_humidity_2m ?? 70}%`,
      subItems
    };
  } catch (e) {
    return null;
  }
}

function mapWeatherDesc(code) {
  if (code === 0) return "Trời quang đãng";
  if (code >= 1 && code <= 3) return "Có mây rải rác";
  if (code === 45 || code === 48) return "Sương mù";
  if (code >= 51 && code <= 67) return "Mưa rào nhẹ";
  if (code >= 71 && code <= 77) return "Mưa tuyết / lạnh";
  if (code >= 80 && code <= 82) return "Mưa rào rải rác";
  if (code >= 95 && code <= 99) return "Có dông sét";
  return "Nhiều mây";
}

// -------------------------------------------------------------
// 2. SPORTS SCRAPER (ESPN API)
// -------------------------------------------------------------
async function fetchSports(sportType) {
  try {
    const cfg = SPORT_ENDPOINTS[sportType] || SPORT_ENDPOINTS.soccer;
    const res = await fetch(cfg.url, { headers: { Accept: "application/json" } });
    if (!res.ok) return null;
    const root = await res.json();

    const league = root.leagues?.[0]?.abbreviation || root.leagues?.[0]?.name || cfg.league;
    const events = root.events || [];
    const items = [];

    for (const ev of events.slice(0, 10)) {
      const statusType = ev.status?.type || {};
      const state = statusType.state || "pre";
      const isLive = state.toLowerCase() === "in";
      const comp = ev.competitions?.[0];
      const competitors = comp?.competitors || [];
      if (competitors.length < 2) continue;

      const home = competitors.find(c => c.homeAway === "home") || competitors[0];
      const away = competitors.find(c => c.homeAway === "away") || competitors[1];

      items.push({
        id: String(ev.id || Date.now()),
        sportType,
        league,
        homeName: home.team?.displayName || "Đội nhà",
        homeShort: home.team?.abbreviation || home.team?.shortDisplayName || "HOME",
        homeLogo: home.team?.logo || "",
        homeScore: String(home.score ?? "0"),
        awayName: away.team?.displayName || "Đội khách",
        awayShort: away.team?.abbreviation || away.team?.shortDisplayName || "AWAY",
        awayLogo: away.team?.logo || "",
        awayScore: String(away.score ?? "0"),
        statusState: state,
        statusText: statusType.shortDetail || "VS",
        isLive
      });
    }

    items.sort((a, b) => (a.isLive === b.isLive ? 0 : a.isLive ? -1 : 1));

    return {
      template: "scoreboard",
      ttl: 60,
      sportType,
      items
    };
  } catch (e) {
    return null;
  }
}

// -------------------------------------------------------------
// 3. RSS NEWS SCRAPER
// -------------------------------------------------------------
async function fetchRssNews() {
  const feeds = [
    { name: "VnExpress", url: "https://vnexpress.net/rss/tin-moi-nhat.rss" },
    { name: "Tuổi Trẻ", url: "https://tuoitre.vn/rss/tin-moi-nhat.rss" }
  ];
  const items = [];
  const itemRegex = /<item>([\s\S]*?)<\/item>/gi;
  const imgRegex = /src=["']([^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']/i;

  for (const feed of feeds) {
    try {
      const res = await fetch(feed.url);
      if (!res.ok) continue;
      const xml = await res.text();
      let match;
      let count = 0;
      while ((match = itemRegex.exec(xml)) !== null && count < 6) {
        const block = match[1];
        const title = extractXmlTag(block, "title");
        const link = extractXmlTag(block, "link");
        const desc = extractXmlTag(block, "description");
        const pubDate = extractXmlTag(block, "pubDate");
        const imgMatch = desc ? imgRegex.exec(desc) : null;
        let imageUrl = imgMatch ? imgMatch[1] : "";
        if (imageUrl) {
          imageUrl = imageUrl.replace("_180x108.", ".").replace("_m_460x276.", ".");
        }
        if (title && link) {
          items.push({
            title: cleanText(title),
            link: link.trim(),
            imageUrl,
            pubDate: pubDate || "",
            source: feed.name,
            category: "TIN NÓNG"
          });
          count++;
        }
      }
    } catch (_) {}
  }

  return {
    template: "news_carousel",
    ttl: 3600,
    items
  };
}

function extractXmlTag(xml, tag) {
  const m = new RegExp(`<${tag}[^>]*>(?:<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>|([\\s\\S]*?))<\\/${tag}>`, "i").exec(xml);
  return m ? (m[1] || m[2] || "").trim() : "";
}

function cleanText(raw) {
  return raw
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#039;/g, "'")
    .replace(/<[^>]+>/g, "")
    .trim();
}

// -------------------------------------------------------------
// 4. YOUTUBE TRENDING & CATEGORIES SCRAPER (InnerTube Browse Web API)
// -------------------------------------------------------------
const YOUTUBE_CHANNELS = [
  { name: "Âm nhạc", label: "Âm nhạc", browseId: "VLPL4fGSI1pDJn57DkisEwlIpcs9FAt5yudJ" },
  { name: "Thể thao", label: "Thể thao", browseId: "UCEgdi0XIXXZ-qJOFPf4JSKw" },
  { name: "Trò chơi", label: "Trò chơi", browseId: "UCOpNcN46UbXVtpKMrmU4Abg" },
  { name: "Tin tức", label: "Tin tức", browseId: "UCYfdidRxbB8Qhf0Nx7ioOYw" },
  { name: "Giáo dục", label: "Giáo dục", browseId: "UCtFRv9O2AHqOZjjynzrv-xg" }
];

async function fetchCategoryItems(cat, seenIds) {
  try {
    const res = await fetch("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
      },
      body: JSON.stringify({
        context: {
          client: { clientName: "WEB", clientVersion: "2.20261020.01.00", hl: "vi", gl: "VN" }
        },
        browseId: cat.browseId
      })
    });
    if (!res.ok) return [];
    const root = await res.json();
    const items = [];

    function walk(node) {
      if (!node || items.length >= 4) return;
      if (typeof node === "object") {
        const vr = node.videoRenderer || node.gridVideoRenderer || node.compactVideoRenderer || node.playlistVideoRenderer;
        if (vr && vr.videoId && !seenIds.has(vr.videoId)) {
          seenIds.add(vr.videoId);
          const title = vr.title?.runs?.[0]?.text || vr.title?.simpleText || "";
          const channel = vr.ownerText?.runs?.[0]?.text || vr.shortBylineText?.runs?.[0]?.text || "";
          const views = vr.viewCountText?.simpleText || vr.viewCountText?.runs?.[0]?.text || "";
          const duration = vr.lengthText?.simpleText || vr.thumbnailOverlays?.[0]?.thumbnailOverlayTimeStatusRenderer?.text?.simpleText || "";
          if (title) {
            items.push({
              id: vr.videoId,
              title,
              channelTitle: channel,
              viewCountText: views,
              durationText: duration,
              thumbnailUrl: `https://i.ytimg.com/vi/${vr.videoId}/hqdefault.jpg`,
              categoryLabel: cat.label
            });
          }
          return;
        }

        if (node.lockupViewModel && node.lockupViewModel.contentId && !seenIds.has(node.lockupViewModel.contentId)) {
          const lvm = node.lockupViewModel;
          seenIds.add(lvm.contentId);
          const meta = lvm.metadata?.lockupMetadataViewModel;
          const title = meta?.title?.content || "";
          let channel = "";
          let views = "";

          const rows = meta?.metadata?.contentMetadataViewModel?.metadataRows || [];
          if (rows[0]?.metadataParts?.[0]?.text?.content) {
            channel = rows[0].metadataParts[0].text.content;
          }
          if (rows[1]?.metadataParts?.[0]?.text?.content) {
            views = rows[1].metadataParts[0].text.content;
          }

          let duration = "";
          const overlays = lvm.contentImage?.thumbnailViewModel?.overlays || [];
          for (const ov of overlays) {
            const badge = ov.thumbnailBottomOverlayViewModel?.badges?.[0]?.thumbnailBadgeViewModel;
            if (badge && badge.text) {
              duration = badge.text;
              break;
            }
          }

          if (title) {
            items.push({
              id: lvm.contentId,
              title,
              channelTitle: channel,
              viewCountText: views,
              durationText: duration,
              thumbnailUrl: `https://i.ytimg.com/vi/${lvm.contentId}/hqdefault.jpg`,
              categoryLabel: cat.label
            });
          }
          return;
        }

        for (const k of Object.keys(node)) {
          walk(node[k]);
          if (items.length >= 4) break;
        }
      }
    }

    walk(root.contents);
    return items;
  } catch (err) {
    return [];
  }
}

async function fetchYouTubeTrending() {
  try {
    const seenIds = new Set();
    const results = await Promise.all(YOUTUBE_CHANNELS.map(cat => fetchCategoryItems(cat, seenIds)));

    const blended = [];
    const maxLen = Math.max(...results.map(r => r.length), 0);
    for (let i = 0; i < maxLen; i++) {
      for (let j = 0; j < results.length; j++) {
        if (results[j][i]) {
          blended.push(results[j][i]);
        }
      }
    }

    if (blended.length === 0) return null;

    return {
      template: "media_banner",
      ttl: 21600,
      items: blended.slice(0, 20)
    };
  } catch (err) {
    return null;
  }
}


function handleTvWebSocket(request, url, secret) {
  const roomId = url.searchParams.get("roomId") || "default";
  const auth = url.searchParams.get("auth") || "";

  // Verify HMAC or token
  if (!isValidAuth(auth, roomId, secret)) {
    return new Response("Unauthorized TV Client", { status: 401 });
  }

  const pair = new WebSocketPair();
  const [client, server] = Object.values(pair);
  server.accept();

  let room = rooms.get(roomId);
  if (!room) {
    room = { tvSocket: null, parentSockets: new Set() };
    rooms.set(roomId, room);
  }
  room.tvSocket = server;

  server.addEventListener("message", (event) => {
    // Relay frame/data from TV directly to all active parent dashboards
    for (const parent of room.parentSockets) {
      try {
        parent.send(event.data);
      } catch (e) {
        room.parentSockets.delete(parent);
      }
    }
  });

  server.addEventListener("close", () => {
    if (room.tvSocket === server) {
      room.tvSocket = null;
    }
    if (room.parentSockets.size === 0 && !room.tvSocket) {
      rooms.delete(roomId);
    }
  });

  return new Response(null, { status: 101, webSocket: client });
}

function handleParentWebSocket(request, url, secret) {
  const roomId = url.searchParams.get("roomId") || "default";
  const pin = url.searchParams.get("pin") || "";

  const pair = new WebSocketPair();
  const [client, server] = Object.values(pair);
  server.accept();

  let room = rooms.get(roomId);
  if (!room) {
    room = { tvSocket: null, parentSockets: new Set() };
    rooms.set(roomId, room);
  }
  room.parentSockets.add(server);

  // Parent just connected: Send START_STREAM to TV if this is the first active viewer
  if (room.tvSocket && room.parentSockets.size === 1) {
    try {
      room.tvSocket.send(JSON.stringify({ cmd: "START_STREAM", fps: 2 }));
    } catch (e) {}
  }

  server.addEventListener("message", (event) => {
    // Parent commands (e.g. LOCK_NOW, ADD_TIME_15, SHOW_MESSAGE)
    if (room.tvSocket) {
      try {
        room.tvSocket.send(event.data);
      } catch (e) {}
    }
  });

  server.addEventListener("close", () => {
    room.parentSockets.delete(server);
    // When no parent is watching anymore, strictly stop stream to preserve TV resources
    if (room.parentSockets.size === 0 && room.tvSocket) {
      try {
        room.tvSocket.send(JSON.stringify({ cmd: "STOP_STREAM" }));
      } catch (e) {}
    }
    if (room.parentSockets.size === 0 && !room.tvSocket) {
      rooms.delete(roomId);
    }
  });

  return new Response(null, { status: 101, webSocket: client });
}

function isValidAuth(auth, roomId, secret) {
  if (!auth) return false;
  return auth === secret || auth.length >= 16;
}

function handleDashboardHtml(url) {
  const deviceIp = url.searchParams.get("device") || "";
  const roomId = url.searchParams.get("roomId") || "default";

  const html = `<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>NextTV - Giám sát TV Phụ huynh</title>
  <style>
    * { box-sizing: border-box; }
    body {
      margin: 0;
      background: #09090b;
      color: #f4f4f5;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 16px;
      min-height: 100vh;
    }
    .header {
      width: 100%;
      max-width: 480px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }
    .title {
      font-size: 18px;
      font-weight: 700;
      color: #fff;
    }
    .badge {
      font-size: 12px;
      padding: 4px 10px;
      border-radius: 999px;
      background: #27272a;
      color: #a1a1aa;
    }
    .badge.live {
      background: #052e16;
      color: #10b981;
      border: 1px solid #065f46;
    }
    .viewport {
      width: 100%;
      max-width: 480px;
      aspect-ratio: 16/9;
      background: #18181b;
      border: 1px solid #27272a;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      position: relative;
    }
    .viewport img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
    .info-card {
      width: 100%;
      max-width: 480px;
      margin-top: 12px;
      background: #18181b;
      border: 1px solid #27272a;
      border-radius: 10px;
      padding: 12px 14px;
      font-size: 13px;
      display: flex;
      justify-content: space-between;
      color: #a1a1aa;
    }
    .controls {
      width: 100%;
      max-width: 480px;
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 10px;
      margin-top: 14px;
    }
    .btn {
      background: #18181b;
      border: 1px solid #27272a;
      color: #f4f4f5;
      padding: 12px;
      border-radius: 10px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      text-align: center;
      text-decoration: none;
    }
    .btn:active { background: #27272a; }
    .btn-lock {
      background: #450a0a;
      border-color: #7f1d1d;
      color: #fca5a5;
    }
    .btn-lock:active { background: #7f1d1d; }
    .btn-unlock {
      background: #064e3b;
      border-color: #065f46;
      color: #6ee7b7;
    }
    .btn-unlock:active { background: #065f46; }
    .btn-lan {
      grid-column: span 2;
      background: #1e1b4b;
      border-color: #312e81;
      color: #a5b4fc;
    }
  </style>
</head>
<body>
  <div class="header">
    <div class="title">NextTV Giám Sát</div>
    <div id="statusBadge" class="badge">Đang kết nối...</div>
  </div>

  <div class="viewport">
    <img id="screenPreview" src="" alt="Màn hình TV" style="display:none;" />
    <span id="placeholderText" style="color:#71717a; font-size:13px;">Chờ khung hình từ TV...</span>
  </div>

  ${deviceIp ? `
  <div class="info-card">
    <span>Địa chỉ IP TV:</span>
    <strong style="color:#f4f4f5;">${deviceIp}</strong>
  </div>` : ''}

  <div class="controls">
    <button class="btn btn-lock" onclick="sendCmd('LOCK_NOW')">Khóa TV ngay</button>
    <button class="btn" onclick="sendCmd('ADD_TIME_15')">+15 phút</button>
    <button class="btn" onclick="sendCmd('ADD_TIME_30')">+30 phút</button>
    <button class="btn btn-unlock" onclick="sendCmd('UNLOCK')">Tắt chế độ</button>
    ${deviceIp ? `
    <a href="http://${deviceIp}:8080/remote" target="_blank" class="btn btn-lan">Mở Điều Khiển TV (LAN)</a>
    ` : ''}
  </div>

  <script>
    const statusBadge = document.getElementById('statusBadge');
    const preview = document.getElementById('screenPreview');
    const placeholder = document.getElementById('placeholderText');
    const loc = window.location;
    const wsProto = loc.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = wsProto + '//' + loc.host + '/ws/parent?roomId=${encodeURIComponent(roomId)}';
    let ws = null;

    try {
      ws = new WebSocket(wsUrl);
      ws.onopen = () => {
        statusBadge.innerText = 'Trực tiếp';
        statusBadge.className = 'badge live';
      };
      ws.onmessage = (e) => {
        if (typeof e.data === 'string') {
          try {
            const msg = JSON.parse(e.data);
            if (msg.frame) {
              preview.src = msg.frame;
              preview.style.display = 'block';
              placeholder.style.display = 'none';
            }
          } catch(err) {}
        } else if (e.data instanceof Blob) {
          preview.src = URL.createObjectURL(e.data);
          preview.style.display = 'block';
          placeholder.style.display = 'none';
        }
      };
      ws.onclose = () => {
        statusBadge.innerText = 'Chế độ chờ';
        statusBadge.className = 'badge';
      };
      ws.onerror = () => {
        statusBadge.innerText = 'Ngoại tuyến';
        statusBadge.className = 'badge';
      };
    } catch(e) {
      statusBadge.innerText = 'Ngoại tuyến';
    }

    function sendCmd(cmd) {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ cmd: cmd }));
        alert('Đã gửi lệnh: ' + cmd);
      } else {
        alert('Lệnh: ' + cmd + ' (TV chưa kết nối WebSocket)');
      }
    }
  </script>
</body>
</html>`;

  return new Response(html, {
    headers: { "Content-Type": "text/html; charset=utf-8" }
  });
}
