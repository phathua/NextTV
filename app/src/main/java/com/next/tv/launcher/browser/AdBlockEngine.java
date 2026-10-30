package com.next.tv.launcher.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ultra-lightweight in-memory AdBlock engine for Android TV WebView.
 * Blocks known tracker and ad domains at the network layer with O(1) HashSet lookups.
 * Includes autonomous on-device rule compiler & deduplication engine:
 * dynamically fetches, compresses domain trees, and keeps filters fresh without GitHub Actions.
 */
public final class AdBlockEngine {
    private static final String TAG = "TvHome.AdBlock";
    private static final String COMPILED_FILENAME = "adblock_hosts_compiled.txt";
    private static final String PREF_NAME = "adblock_prefs";
    private static final String KEY_LAST_UPDATE = "last_adblock_update_time";
    private static final long UPDATE_INTERVAL_MS = 7 * 24 * 3600 * 1000L; // 7 days

    private static final Set<String> BLOCKED_HOSTS = new HashSet<>();
    private static volatile boolean initialized = false;
    private static volatile boolean compiling = false;

    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$");

    // Essential system whitelisted domains to prevent broken TV services
    private static final Set<String> SYSTEM_WHITELIST = new HashSet<>(Arrays.asList(
            "google.com",
            "googleapis.com",
            "gstatic.com",
            "android.com",
            "gvt1.com",
            "ggpht.com",
            "youtube.com",
            "ytimg.com",
            "googlevideo.com",
            "play.google.com",
            "time.android.com",
            "pool.ntp.org",
            "netflix.com",
            "nflxext.com",
            "nflximg.net",
            "nflxvideo.net",
            "spotify.com",
            "scdn.co"
    ));

    private static final String[] BLOCKLIST_SOURCES = {
            "https://raw.githubusercontent.com/abpvn/abpvn/master/filter/abpvn-hosts.txt",
            "https://raw.githubusercontent.com/bigdargon/hostsVN/master/hosts",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.txt",
            "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"
    };

    private AdBlockEngine() {}

    public static synchronized void init(Context context) {
        if (initialized || context == null) return;
        Context appCtx = context.getApplicationContext();

        try {
            File compiledFile = new File(appCtx.getFilesDir(), COMPILED_FILENAME);
            InputStream is;
            if (compiledFile.exists() && compiledFile.length() > 1024) {
                is = new FileInputStream(compiledFile);
                Log.d(TAG, "Loading AdBlock rules from compiled storage: " + compiledFile.length() + " bytes");
            } else {
                is = appCtx.getAssets().open("browser/adblock_hosts.txt");
                Log.d(TAG, "Loading initial AdBlock rules from bundled assets");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
                BLOCKED_HOSTS.add(line);
            }
            reader.close();
            initialized = true;
            Log.d(TAG, "AdBlock initialized with " + BLOCKED_HOSTS.size() + " domains.");
        } catch (Exception e) {
            Log.w(TAG, "Failed loading adblock hosts: " + e.getMessage());
        }

        // Trigger background compilation if expired
        SharedPreferences prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastTime = prefs.getLong(KEY_LAST_UPDATE, 0L);
        if (System.currentTimeMillis() - lastTime > UPDATE_INTERVAL_MS) {
            syncLatestRulesAsync(appCtx);
        }
    }

    public static void syncLatestRulesAsync(Context context) {
        if (compiling || context == null) return;
        compiling = true;
        Context appCtx = context.getApplicationContext();

        new Thread(() -> {
            try {
                Log.d(TAG, "Starting autonomous AdBlock compile & deduplication...");
                Set<String> rawDomains = new HashSet<>();
                Set<String> allowlist = new HashSet<>(SYSTEM_WHITELIST);

                for (String sourceUrl : BLOCKLIST_SOURCES) {
                    try {
                        fetchAndParseSource(sourceUrl, rawDomains, allowlist);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed fetching " + sourceUrl + ": " + e.getMessage());
                    }
                }

                if (rawDomains.size() < 1000) {
                    Log.w(TAG, "Fetched domain count too low (" + rawDomains.size() + "), aborting update.");
                    compiling = false;
                    return;
                }

                // Domain Tree Compression & Deduplication
                Set<String> compressed = compressDomains(rawDomains, allowlist);
                Log.d(TAG, "Compressed from " + rawDomains.size() + " to " + compressed.size() + " unique root domains.");

                // Save to local storage file
                File outFile = new File(appCtx.getFilesDir(), COMPILED_FILENAME);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8));
                writer.write("# CleanYourTV Autonomous AdBlock Rules\n");
                writer.write("# Updated: " + System.currentTimeMillis() + "\n");
                for (String domain : compressed) {
                    writer.write(domain);
                    writer.newLine();
                }
                writer.flush();
                writer.close();

                // Update active in-memory set atomically
                synchronized (BLOCKED_HOSTS) {
                    BLOCKED_HOSTS.clear();
                    BLOCKED_HOSTS.addAll(compressed);
                }

                appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                        .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                        .apply();

                Log.i(TAG, "Autonomous AdBlock update completed with " + compressed.size() + " rules.");
            } catch (Exception e) {
                Log.e(TAG, "AdBlock compilation failed: " + e.getMessage(), e);
            } finally {
                compiling = false;
            }
        }).start();
    }

    private static void fetchAndParseSource(String sourceUrl, Set<String> targetDomains, Set<String> targetAllowlist) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(sourceUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "CleanYourTV-AdBlockCompiler/1.0");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    parseLine(line, targetDomains, targetAllowlist);
                }
                reader.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error fetching " + sourceUrl + ": " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void parseLine(String line, Set<String> targetDomains, Set<String> targetAllowlist) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return;

        // Exception rules (@@||domain^)
        if (line.startsWith("@@")) {
            String clean = line.substring(2);
            if (clean.startsWith("||")) clean = clean.substring(2);
            clean = clean.split("\\^")[0].split("/")[0].split("\\$")[0].trim().toLowerCase();
            if (DOMAIN_PATTERN.matcher(clean).matches()) {
                targetAllowlist.add(clean);
            }
            return;
        }

        // Adblock syntax (||domain.com^)
        if (line.startsWith("||")) {
            String domain = line.substring(2).split("\\^")[0].split("/")[0].split("\\$")[0].trim().toLowerCase();
            if (DOMAIN_PATTERN.matcher(domain).matches()) {
                targetDomains.add(domain);
            }
            return;
        }

        // /etc/hosts syntax (0.0.0.0 domain or 127.0.0.1 domain)
        String[] parts = line.split("\\s+");
        if (parts.length >= 2 && ("0.0.0.0".equals(parts[0]) || "127.0.0.1".equals(parts[0]))) {
            String domain = parts[1].trim().toLowerCase();
            if (DOMAIN_PATTERN.matcher(domain).matches() && !"localhost".equals(domain) && !"broadcasthost".equals(domain)) {
                targetDomains.add(domain);
            }
            return;
        }

        // Plain domain line
        if (DOMAIN_PATTERN.matcher(line).matches()) {
            targetDomains.add(line.toLowerCase());
        }
    }

    private static Set<String> compressDomains(Set<String> rawDomains, Set<String> allowlist) {
        Set<String> compressed = new HashSet<>();

        for (String domain : rawDomains) {
            if (allowlist.contains(domain)) continue;
            boolean whitelistedParent = false;
            for (String allowed : allowlist) {
                if (domain.endsWith("." + allowed)) {
                    whitelistedParent = true;
                    break;
                }
            }
            if (whitelistedParent) continue;
            compressed.add(domain);
        }

        // Tree compression: drop subdomains if parent domain is already in the blocked set
        Set<String> finalDomains = new HashSet<>();
        for (String domain : compressed) {
            boolean hasParent = false;
            int dotIdx = domain.indexOf('.');
            while (dotIdx > 0 && dotIdx < domain.length() - 1) {
                String parent = domain.substring(dotIdx + 1);
                if (compressed.contains(parent)) {
                    hasParent = true;
                    break;
                }
                dotIdx = domain.indexOf('.', dotIdx + 1);
            }
            if (!hasParent) {
                finalDomains.add(domain);
            }
        }

        return finalDomains;
    }

    public static boolean isAdUrl(String url) {
        if (!initialized || url == null || url.isEmpty()) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase();

            if (BLOCKED_HOSTS.contains(host)) return true;

            int dotIdx = host.indexOf('.');
            while (dotIdx > 0 && dotIdx < host.length() - 1) {
                String parent = host.substring(dotIdx + 1);
                if (BLOCKED_HOSTS.contains(parent)) return true;
                dotIdx = host.indexOf('.', dotIdx + 1);
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static WebResourceResponse intercept(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return null;
        String url = request.getUrl().toString();
        if (isAdUrl(url)) {
            return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        }
        return null;
    }
}
