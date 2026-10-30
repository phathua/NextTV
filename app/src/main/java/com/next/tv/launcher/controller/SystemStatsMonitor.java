package com.next.tv.launcher.controller;

import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import com.next.tv.launcher.R;
import com.next.tv.launcher.StatsText;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Monitors CPU, RAM, and Storage stats in the background.
 * Active only when showStats is true and the activity is resumed.
 */
public final class SystemStatsMonitor {

    private static final long STATS_MS = 10_000L;
    private static final int CPU_UNREADABLE = -2;

    private final Context context;
    private final Handler mainHandler;
    private TextView statsView;
    private boolean showStats;

    private HandlerThread statsThread;
    private volatile Handler statsBg;

    private long cpuTotalPrev = -1;
    private long cpuIdlePrev = -1;
    private boolean procStatDead;
    private long idlePrev = -1;
    private long wallPrev = -1;

    public SystemStatsMonitor(Context context, Handler mainHandler, TextView statsView, boolean showStats) {
        this.context = context.getApplicationContext();
        this.mainHandler = mainHandler;
        this.statsView = statsView;
        this.showStats = showStats;
    }

    public void setStatsView(TextView statsView) {
        this.statsView = statsView;
    }

    public void setShowStats(boolean showStats) {
        this.showStats = showStats;
        if (!showStats && statsView != null) {
            statsView.setVisibility(View.GONE);
        }
    }

    public boolean isShowStats() {
        return showStats;
    }

    public void start() {
        if (!showStats || statsThread != null) return;
        if (statsView != null) {
            statsView.setVisibility(View.VISIBLE);
        }
        cpuTotalPrev = -1;
        idlePrev = -1;
        procStatDead = false;

        statsThread = new HandlerThread("stats");
        statsThread.start();

        Handler bg = new Handler(statsThread.getLooper());
        statsBg = bg;

        bg.post(new Runnable() {
            @Override
            public void run() {
                if (statsBg != bg) return;
                String line = readStatsLine();
                mainHandler.post(() -> {
                    if (showStats && statsView != null && statsBg == bg) {
                        statsView.setText(line);
                    }
                });
                if (statsBg == bg) {
                    bg.postDelayed(this, STATS_MS);
                }
            }
        });
    }

    public void stop() {
        if (statsThread == null) return;
        if (statsBg != null) {
            statsBg.removeCallbacksAndMessages(null);
            statsBg = null;
        }
        statsThread.quit();
        statsThread = null;

        if (!showStats && statsView != null) {
            statsView.setVisibility(View.GONE);
        }
    }

    private static void appendStat(StringBuilder line, String part) {
        if (line.length() > 0) line.append("  ·  ");
        line.append(part);
    }

    private String readStatsLine() {
        int cpu = cpuPercent();
        if (cpu < 0) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            }
            cpu = cpuPercent();
        }

        StringBuilder line = new StringBuilder();
        if (cpu >= 0) {
            line.append(context.getString(R.string.stats_cpu, cpu));
        }
        long[] mem = memUsedOfTotalMb();
        if (mem != null) {
            appendStat(line, context.getString(R.string.stats_memory, mem[0], mem[1]));
        }
        String free = formatGb(context.getResources(), Environment.getDataDirectory().getFreeSpace());
        if (!free.isEmpty()) {
            appendStat(line, context.getString(R.string.stats_storage, free));
        }
        return StatsText.forLocale(line.toString(), context.getResources().getConfiguration().getLocales().get(0));
    }

    private int cpuPercent() {
        if (!procStatDead) {
            int fromStat = cpuFromProcStat();
            if (fromStat != CPU_UNREADABLE) return fromStat;
            procStatDead = true;
            idlePrev = -1;
        }
        return cpuFromIdle();
    }

    private int cpuFromProcStat() {
        String s = readFirstLine("/proc/stat");
        if (s == null || !s.startsWith("cpu")) return CPU_UNREADABLE;
        String[] t = s.trim().split("\\s+");
        if (t.length < 5) return CPU_UNREADABLE;
        try {
            long user = Long.parseLong(t[1]);
            long nice = Long.parseLong(t[2]);
            long sys = Long.parseLong(t[3]);
            long idle = Long.parseLong(t[4]);
            long iowait = t.length > 5 ? Long.parseLong(t[5]) : 0;
            long irq = t.length > 6 ? Long.parseLong(t[6]) : 0;
            long soft = t.length > 7 ? Long.parseLong(t[7]) : 0;
            long total = user + nice + sys + idle + iowait + irq + soft;
            long idleAll = idle + iowait;
            if (cpuTotalPrev < 0) {
                cpuTotalPrev = total;
                cpuIdlePrev = idleAll;
                return -1;
            }
            long dt = total - cpuTotalPrev;
            long di = idleAll - cpuIdlePrev;
            cpuTotalPrev = total;
            cpuIdlePrev = idleAll;
            if (dt <= 0) return -1;
            int busy = (int) Math.round((dt - di) * 100.0 / dt);
            return Math.max(0, Math.min(100, busy));
        } catch (NumberFormatException e) {
            return CPU_UNREADABLE;
        }
    }

    private int cpuFromIdle() {
        long idle = 0;
        int cores = 0;
        for (int i = 0; i < 8; i++) {
            boolean any = false;
            for (int state = 0; state < 8; state++) {
                String t = readFirstLine("/sys/devices/system/cpu/cpu" + i + "/cpuidle/state" + state + "/time");
                if (t == null) break;
                try {
                    idle += Long.parseLong(t.trim());
                    any = true;
                } catch (NumberFormatException e) {
                    break;
                }
            }
            if (!any) break;
            cores++;
        }
        if (cores == 0) return -1;
        long wall = SystemClock.elapsedRealtime() * 1000L;
        if (idlePrev < 0) {
            idlePrev = idle;
            wallPrev = wall;
            return -1;
        }
        long di = idle - idlePrev;
        long dw = (wall - wallPrev) * cores;
        idlePrev = idle;
        wallPrev = wall;
        if (dw <= 0) return -1;
        int busy = (int) Math.round((dw - di) * 100.0 / dw);
        return Math.max(0, Math.min(100, busy));
    }

    private static long[] memUsedOfTotalMb() {
        long available = -1, total = -1;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            String[] t;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    t = line.split("\\s+");
                    if (t.length >= 2) available = Long.parseLong(t[1]) / 1024;
                } else if (line.startsWith("MemTotal:")) {
                    t = line.split("\\s+");
                    if (t.length >= 2) total = Long.parseLong(t[1]) / 1024;
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        if (available < 0 || total <= 0) return null;
        return new long[]{Math.max(0, total - available), total};
    }

    private static String readFirstLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private static String formatGb(Resources res, long bytes) {
        if (bytes <= 0) return "";
        double gb = bytes / (1024.0 * 1024 * 1024);
        return gb >= 10 ? res.getString(R.string.storage_gb_whole, gb)
                : res.getString(R.string.storage_gb_decimal, gb);
    }
}
