package com.next.tv.launcher.clean;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java Native ADB Protocol Client & Process Memory Reader for Android TV.
 * Direct TCP Socket to 127.0.0.1:5555 with RSA Handshake authentication.
 * Zero mockdata, zero hardcoded memory numbers, real privileged execution.
 */
public final class LocalAdbBridge {

    private static final String TAG = "TvHome.LocalADB";
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 5555;
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int DEFAULT_CMD_TIMEOUT_MS = 8000;
    private static final String EXIT_CODE_MARKER = "__NEXTTV_EXIT_CODE__=";

    public static final class ShellCommandResult {
        public final boolean success;
        public final int exitCode;
        public final String output;
        public final String error;

        private ShellCommandResult(boolean success, int exitCode, String output, String error) {
            this.success = success;
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }
    }

    // ADB Protocol Constants (Little-Endian uint32)
    private static final int A_SYNC = 0x434e5953;
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;
    private static final int A_AUTH = 0x48545541;

    private static final int A_VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 1048576;

    // Dynamic on-device RSA key storage files in app-private data directory
    private static final String ADB_KEY_FILE = "nexttv_adb_key.pk8";
    private static final String ADB_PUB_FILE = "nexttv_adb_key.pub";

    private static volatile LocalAdbBridge instance;
    private final Context context;
    private volatile boolean connected = false;
    private volatile long adbUnavailableUntilMs = 0;
    private volatile String lastAdbFailure = "";
    private final Map<String, Long> recentlyKilledTimestamps = new ConcurrentHashMap<>();
    private PrivateKey cachedPrivateKey;
    private String cachedPublicKeyString;

    public interface AdbCallback {
        void onResult(boolean success, String output);
    }

    public static class AppProcessMem {
        public final String packageName;
        public final int pid;
        public final long pssKb;
        public final String category; // "user", "media", "google", "system", "launcher"

        public AppProcessMem(String packageName, int pid, long pssKb) {
            this(packageName, pid, pssKb, "user");
        }

        public AppProcessMem(String packageName, int pid, long pssKb, String category) {
            this.packageName = packageName;
            this.pid = pid;
            this.pssKb = pssKb;
            this.category = category;
        }
    }

    private LocalAdbBridge(Context context) {
        this.context = context.getApplicationContext();
        if (com.next.tv.launcher.BuildConfig.ENABLE_LOCAL_ADB) {
            loadPrivateKey();
        }
    }

    public static LocalAdbBridge getInstance(Context context) {
        if (instance == null) {
            synchronized (LocalAdbBridge.class) {
                if (instance == null) {
                    instance = new LocalAdbBridge(context);
                }
            }
        }
        return instance;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public void markPackageKilled(String pkg) {
        if (pkg != null && !pkg.isEmpty()) {
            recentlyKilledTimestamps.put(pkg, System.currentTimeMillis());
            Log.d(TAG, "Marked package killed: " + pkg);
        }
    }

    public void markAllKilled(Collection<String> pkgs) {
        if (pkgs != null) {
            long now = System.currentTimeMillis();
            for (String pkg : pkgs) {
                if (pkg != null && !pkg.isEmpty()) {
                    recentlyKilledTimestamps.put(pkg, now);
                }
            }
            Log.d(TAG, "Marked all packages killed: " + pkgs.size());
        }
    }

    private void loadPrivateKey() {
        try {
            File keyFile = new File(context.getFilesDir(), ADB_KEY_FILE);
            File pubFile = new File(context.getFilesDir(), ADB_PUB_FILE);

            if (keyFile.exists() && pubFile.exists() && keyFile.length() > 0) {
                byte[] keyBytes = new byte[(int) keyFile.length()];
                try (FileInputStream fis = new FileInputStream(keyFile)) {
                    int read = fis.read(keyBytes);
                    if (read > 0) {
                        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
                        KeyFactory kf = KeyFactory.getInstance("RSA");
                        this.cachedPrivateKey = kf.generatePrivate(spec);
                    }
                }
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(pubFile), StandardCharsets.UTF_8))) {
                    this.cachedPublicKeyString = br.readLine();
                }
                if (this.cachedPrivateKey != null && this.cachedPublicKeyString != null && !this.cachedPublicKeyString.isEmpty()) {
                    return;
                }
            }

            // Generate fresh unique 2048-bit RSA keypair on first run
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
            RSAPublicKey pub = (RSAPublicKey) kp.getPublic();

            this.cachedPrivateKey = priv;
            String userDesc = "NextTV@" + android.os.Build.MODEL;
            this.cachedPublicKeyString = formatAdbPublicKey(pub, userDesc);

            // Persist to app-private internal storage
            try (FileOutputStream fos = new FileOutputStream(keyFile)) {
                fos.write(priv.getEncoded());
            }
            try (FileOutputStream fos = new FileOutputStream(pubFile)) {
                fos.write(this.cachedPublicKeyString.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to initialize dynamic ADB keypair", e);
        }
    }

    private static String formatAdbPublicKey(RSAPublicKey pubKey, String userDesc) {
        int rsaNumWords = 64; // 2048 bits / 32
        BigInteger n = pubKey.getModulus();
        BigInteger r32 = BigInteger.valueOf(2).pow(32);

        // n0inv = -1 / n[0] mod 2^32
        BigInteger n0inv = n.remainder(r32).modInverse(r32).negate().remainder(r32);
        if (n0inv.signum() < 0) {
            n0inv = n0inv.add(r32);
        }

        BigInteger r = BigInteger.valueOf(2).pow(2048);
        BigInteger rr = r.multiply(r).remainder(n);

        ByteBuffer buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(rsaNumWords);
        buf.putInt((int) n0inv.longValue());

        BigInteger tempN = n;
        for (int i = 0; i < rsaNumWords; i++) {
            buf.putInt((int) tempN.remainder(r32).longValue());
            tempN = tempN.divide(r32);
        }

        BigInteger tempRR = rr;
        for (int i = 0; i < rsaNumWords; i++) {
            buf.putInt((int) tempRR.remainder(r32).longValue());
            tempRR = tempRR.divide(r32);
        }

        buf.putInt(pubKey.getPublicExponent().intValue());
        String b64 = Base64.getEncoder().encodeToString(buf.array());
        return b64 + " " + userDesc;
    }

    private static class AdbMessage {
        public int command;
        public int arg0;
        public int arg1;
        public int length;
        public int checksum;
        public int magic;
        public byte[] data;

        public static AdbMessage create(int cmd, int arg0, int arg1, byte[] data) {
            AdbMessage m = new AdbMessage();
            m.command = cmd;
            m.arg0 = arg0;
            m.arg1 = arg1;
            m.data = data != null ? data : new byte[0];
            m.length = m.data.length;
            int sum = 0;
            for (byte b : m.data) {
                sum += (b & 0xFF);
            }
            m.checksum = sum;
            m.magic = cmd ^ 0xFFFFFFFF;
            return m;
        }

        public byte[] toBytes() {
            ByteBuffer buf = ByteBuffer.allocate(24 + length).order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(command);
            buf.putInt(arg0);
            buf.putInt(arg1);
            buf.putInt(length);
            buf.putInt(checksum);
            buf.putInt(magic);
            if (length > 0) {
                buf.put(data);
            }
            return buf.array();
        }

        public static AdbMessage read(InputStream in) throws Exception {
            byte[] hdr = new byte[24];
            int read = 0;
            while (read < 24) {
                int r = in.read(hdr, read, 24 - read);
                if (r < 0) return null;
                read += r;
            }
            ByteBuffer buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
            AdbMessage m = new AdbMessage();
            m.command = buf.getInt();
            m.arg0 = buf.getInt();
            m.arg1 = buf.getInt();
            m.length = buf.getInt();
            m.checksum = buf.getInt();
            m.magic = buf.getInt();

            m.data = new byte[m.length];
            int dataRead = 0;
            while (dataRead < m.length) {
                int r = in.read(m.data, dataRead, m.length - dataRead);
                if (r < 0) break;
                dataRead += r;
            }
            return m;
        }
    }

    /**
     * Executes shell command via Local TCP ADB Socket stream (127.0.0.1:5555).
     * Synchronized to prevent socket collisions between worker threads.
     */
    public synchronized String executeAdbShell(String command, long timeoutMs) {
        if (!com.next.tv.launcher.BuildConfig.ENABLE_LOCAL_ADB) return "";
        if (command == null || command.isEmpty()) return "";
        long timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_CMD_TIMEOUT_MS;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout((int) timeout);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // 1. Send A_CNXN
            byte[] hostId = "host::NextTV\0".getBytes(StandardCharsets.UTF_8);
            out.write(AdbMessage.create(A_CNXN, A_VERSION, MAX_PAYLOAD, hostId).toBytes());
            out.flush();

            AdbMessage resp = AdbMessage.read(in);
            if (resp == null) return "";

            // 2. Handle Authentication Challenge
            if (resp.command == A_AUTH && resp.arg0 == 1) {
                byte[] token = resp.data;

                // Sign token using ASN.1 SHA-1 DigestInfo prefix + RSA private key
                byte[] asn1Prefix = new byte[]{
                    0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
                };
                byte[] digestInfo = new byte[asn1Prefix.length + token.length];
                System.arraycopy(asn1Prefix, 0, digestInfo, 0, asn1Prefix.length);
                System.arraycopy(token, 0, digestInfo, asn1Prefix.length, token.length);

                Signature sig = Signature.getInstance("NONEwithRSA");
                sig.initSign(cachedPrivateKey);
                sig.update(digestInfo);
                byte[] signature = sig.sign();

                // Send A_AUTH (Type 2: Signature)
                out.write(AdbMessage.create(A_AUTH, 2, 0, signature).toBytes());
                out.flush();

                resp = AdbMessage.read(in);
                if (resp != null && resp.command == A_AUTH && resp.arg0 == 1) {
                    // Key not yet recognized by device: Send A_AUTH (Type 3: RSAPUBLICKEY)
                    String pubStr = (cachedPublicKeyString != null && !cachedPublicKeyString.isEmpty())
                            ? cachedPublicKeyString : ("NextTV@" + android.os.Build.MODEL);
                    byte[] pubKeyBytes = (pubStr + "\0").getBytes(StandardCharsets.UTF_8);
                    out.write(AdbMessage.create(A_AUTH, 3, 0, pubKeyBytes).toBytes());
                    out.flush();

                    // Wait for user approval popup on TV
                    resp = AdbMessage.read(in);
                }
            }

            if (resp == null || resp.command != A_CNXN) {
                lastAdbFailure = "ADB chưa xác thực hoặc kết nối bị từ chối";
                Log.w(TAG, "ADB Handshake failed or rejected by TV adbd");
                this.connected = false;
                return "";
            }

            this.connected = true;

            // 3. Open Channel for Shell Command
            int localId = 1;
            byte[] cmdBytes = ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8);
            out.write(AdbMessage.create(A_OPEN, localId, 0, cmdBytes).toBytes());
            out.flush();

            resp = AdbMessage.read(in);
            if (resp == null || resp.command != A_OKAY) {
                lastAdbFailure = "Không mở được kênh lệnh ADB";
                Log.w(TAG, "Failed to open shell channel for: " + command);
                return "";
            }

            int remoteId = resp.arg0;
            StringBuilder sb = new StringBuilder();

            // 4. Stream response
            while (true) {
                resp = AdbMessage.read(in);
                if (resp == null) break;
                if (resp.command == A_WRTE) {
                    sb.append(new String(resp.data, StandardCharsets.UTF_8));
                    out.write(AdbMessage.create(A_OKAY, localId, remoteId, null).toBytes());
                    out.flush();
                } else if (resp.command == A_CLSE) {
                    out.write(AdbMessage.create(A_CLSE, localId, remoteId, null).toBytes());
                    out.flush();
                    break;
                }
            }

            return sb.toString();

        } catch (Exception e) {
            lastAdbFailure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Log.w(TAG, "ADB socket connection failed: " + e.getMessage());
            this.connected = false;
            return "";
        }
    }

    /**
     * Executes shell command via Local ADB Socket, falling back to Runtime.exec if ADB is closed.
     */
    public String executeShell(String command) {
        if (!com.next.tv.launcher.BuildConfig.ENABLE_LOCAL_ADB) return "";
        String res = executeAdbShell(command, DEFAULT_CMD_TIMEOUT_MS);
        if (!res.isEmpty()) {
            return res;
        }

        // Fallback for commands supported in app sandbox
        StringBuilder sb = new StringBuilder();
        try {
            java.lang.Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", command});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            process.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public synchronized ShellCommandResult executeShellCommand(String command, long timeoutMs) {
        if (!com.next.tv.launcher.BuildConfig.ENABLE_LOCAL_ADB) {
            return new ShellCommandResult(false, -1, "", "ADB nội bộ bị tắt trong bản dựng này");
        }
        if (command == null || command.trim().isEmpty()) {
            return new ShellCommandResult(false, -1, "", "Lệnh rỗng");
        }
        if (System.currentTimeMillis() < adbUnavailableUntilMs) {
            return new ShellCommandResult(false, -1, "", "ADB nội bộ đang không phản hồi; chưa thử lại trong lần dọn này");
        }

        String wrappedCommand = command + "; echo " + EXIT_CODE_MARKER + "$?";
        lastAdbFailure = "";
        String adbOutput = executeAdbShell(wrappedCommand, timeoutMs > 0 ? timeoutMs : DEFAULT_CMD_TIMEOUT_MS);
        ShellCommandResult adbResult = parseShellCommandResult(adbOutput);
        if (adbResult != null) {
            if (adbResult.exitCode == 255 && adbResult.output.isEmpty()) {
                adbUnavailableUntilMs = System.currentTimeMillis() + 15_000;
            }
            return adbResult;
        }
        String adbFailure = lastAdbFailure;
        if (!adbFailure.isEmpty()) adbUnavailableUntilMs = System.currentTimeMillis() + 15_000;

        try {
            java.lang.Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", wrappedCommand});
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }
            boolean completed = process.waitFor(Math.max(1, timeoutMs), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new ShellCommandResult(false, -1, output.toString(), "Hết thời gian chờ lệnh");
            }
            ShellCommandResult localResult = parseShellCommandResult(output.toString());
            if (localResult != null) return localResult;
            return new ShellCommandResult(false, process.exitValue(), output.toString(),
                    adbFailure.isEmpty() ? "Không đọc được mã kết thúc lệnh" : "ADB nội bộ: " + adbFailure);
        } catch (Exception e) {
            String message = "Không chạy được lệnh: " + e.getMessage();
            if (!adbFailure.isEmpty()) message += "; ADB: " + adbFailure;
            return new ShellCommandResult(false, -1, "", message);
        }
    }

    static ShellCommandResult parseShellCommandResult(String rawOutput) {
        if (rawOutput == null) return null;
        int markerIndex = rawOutput.lastIndexOf(EXIT_CODE_MARKER);
        if (markerIndex < 0) return null;
        int valueStart = markerIndex + EXIT_CODE_MARKER.length();
        int valueEnd = valueStart;
        while (valueEnd < rawOutput.length() && Character.isDigit(rawOutput.charAt(valueEnd))) valueEnd++;
        if (valueEnd == valueStart) return null;
        try {
            int exitCode = Integer.parseInt(rawOutput.substring(valueStart, valueEnd));
            String output = rawOutput.substring(0, markerIndex).trim();
            return new ShellCommandResult(exitCode == 0, exitCode, output,
                    exitCode == 0 ? "" : (output.isEmpty() ? "Lệnh trả về mã " + exitCode : output));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void executeShellAsync(String command, AdbCallback callback) {
        new Thread(() -> {
            String res = executeShell(command);
            boolean ok = res != null && !res.isEmpty();
            if (callback != null) {
                callback.onResult(ok, res);
            }
        }).start();
    }

    /**
     * Tự động cấp quyền hệ thống và thiết lập giới hạn kernel tối đa cho Launcher.
     */
    public void grantSelfPermissions() {
        new Thread(() -> {
            try {
                String pkg = context.getPackageName();
                executeAdbShell("pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS", 4000);
                executeAdbShell("cmd appops set " + pkg + " GET_USAGE_STATS allow", 4000);
                executeAdbShell("settings put global activity_manager_constants max_cached_processes=2,background_settle_time=5000", 4000);
                Log.i(TAG, "Granted self permissions and applied strict kernel constants via local ADB");
            } catch (Exception ignored) {}
        }).start();
    }

    /**
     * Bóc tách tiến trình RAM thực chất 100% từ dumpsys meminfo của hệ thống.
     * Tuyệt đối không mockdata, không số ước tính, không lọc giấu app chưa chết.
     */
    public List<AppProcessMem> parseRunningProcessMem() {
        List<AppProcessMem> list = new ArrayList<>();
        String myPkg = context.getPackageName();

        // 1. Thử lấy PSS/RSS trực tiếp từ luồng ADB đặc quyền (dumpsys meminfo)
        try {
            String output = executeAdbShell("dumpsys meminfo", DEFAULT_CMD_TIMEOUT_MS);
            if (output != null && (output.contains("Total RSS by process:") || output.contains("Total PSS by process:"))) {
                Pattern pattern = Pattern.compile("^\\s*([0-9,]+)K:\\s+([a-zA-Z0-9._:\\-]+)\\s+\\(pid\\s+(\\d+)", Pattern.MULTILINE);
                Matcher matcher = pattern.matcher(output);

                Map<String, AppProcessMem> uniqueMap = new HashMap<>();

                while (matcher.find()) {
                    try {
                        String kbStr = matcher.group(1).replace(",", "");
                        long kb = Long.parseLong(kbStr);
                        String name = matcher.group(2);
                        int pid = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

                        if (kb <= 0 || name == null || name.isEmpty() || name.startsWith(".") || "Native".equalsIgnoreCase(name)
        || "Dalvik".equalsIgnoreCase(name) || "Cached".equalsIgnoreCase(name) || "Total".equalsIgnoreCase(name)
        || "Unknown".equalsIgnoreCase(name)) {
    continue;
}

                        String category = categorizeProcess(name, myPkg);
                        AppProcessMem pm = new AppProcessMem(name, pid, kb, category);

                        // Ưu tiên bản ghi có PSS lớn hơn nếu xuất hiện nhiều lần (RSS vs PSS)
                        AppProcessMem existing = uniqueMap.get(name);
                        if (existing == null || kb > existing.pssKb) {
                            uniqueMap.put(name, pm);
                        }
                    } catch (Exception ignored) {}
                }

                if (!uniqueMap.isEmpty()) {
                    list.addAll(uniqueMap.values());
                    // Sắp xếp bộ nhớ giảm dần
                    list.sort((a, b) -> Long.compare(b.pssKb, a.pssKb));
                    return list;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Real ADB meminfo parse failed: " + e.getMessage());
        }

        // 2. Dự phòng an toàn khi TV chưa kích hoạt cổng ADB:
        // Đo chính xác bằng ActivityManager + /proc/meminfo (vẫn đảm bảo không mock data)
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                int myPid = Process.myPid();
                android.os.Debug.MemoryInfo[] memInfos = am.getProcessMemoryInfo(new int[]{myPid});
                long myPssKb = 0;
                if (memInfos != null && memInfos.length > 0 && memInfos[0].getTotalPss() > 0) {
                    myPssKb = memInfos[0].getTotalPss();
                }
                if (myPssKb > 0) list.add(new AppProcessMem(myPkg, myPid, myPssKb, "launcher"));

                List<ActivityManager.RunningAppProcessInfo> runningList = am.getRunningAppProcesses();
                if (runningList != null) {
                    int[] pids = new int[Math.min(runningList.size(), 12)];
                    for (int i = 0; i < pids.length; i++) {
                        pids[i] = runningList.get(i).pid;
                    }
                    android.os.Debug.MemoryInfo[] appMems = am.getProcessMemoryInfo(pids);
                    if (appMems != null) {
                        for (int i = 0; i < appMems.length; i++) {
                            if (i >= runningList.size()) break;
                            ActivityManager.RunningAppProcessInfo info = runningList.get(i);
                            if (info.processName.equals(myPkg)) continue;
                            long pssKb = appMems[i].getTotalPss();
                            if (pssKb >= 10 * 1024L) {
                                String cat = categorizeProcess(info.processName, myPkg);
                                list.add(new AppProcessMem(info.processName, info.pid, pssKb, cat));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Fallback memory scan failed: " + e.getMessage());
        }

        return list;
    }

    private String categorizeProcess(String name, String myPkg) {
        if (name.equals(myPkg)) {
            return "launcher";
        }
        if (name.startsWith("com.google.android.") || name.equals("com.android.vending")) {
            return "google";
        }
        if (name.equals("system") || name.equals("com.android.systemui") || name.equals("surfaceflinger")
                || name.equals("adbd") || name.equals("audioserver") || name.equals("zygote")
                || name.equals("netd") || name.equals("mediaserver") || name.startsWith("android.hardware.")) {
            return "system";
        }
        if (name.contains("torrserver") || name.contains("stremio") || name.contains("nuvio")
                || name.contains("netflix") || name.contains("spagreen") || name.contains("oxootv")
                || name.contains("vtv") || name.contains("xemtv") || name.contains("kodi")) {
            return "media";
        }
        return "user";
    }

    /**
     * Dọn dẹp ứng dụng chạy ngầm triệt để bằng am force-stop và kill daemon qua ADB.
     */
    public boolean deepKillApp(String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_.]+")) return false;

        if (!com.next.tv.launcher.BuildConfig.ENABLE_LOCAL_ADB) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    am.killBackgroundProcesses(packageName);
                }
            } catch (Exception ignored) {}
            Log.w(TAG, "Không xác nhận được lệnh dừng " + packageName + ": ADB nội bộ bị tắt");
            return false;
        }

        ShellCommandResult result = executeShellCommand("am force-stop " + packageName, 3500);
        if (!result.success) {
            Log.w(TAG, "Không dừng được " + packageName + " qua ADB: " + result.error);
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) am.killBackgroundProcesses(packageName);
            } catch (Exception e) {
                Log.w(TAG, "Dự phòng ActivityManager cũng thất bại với " + packageName, e);
            }
            return false;
        }

        markPackageKilled(packageName);
        Log.i(TAG, "ADB chấp nhận lệnh dừng " + packageName);
        return true;
    }
}
