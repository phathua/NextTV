package com.next.tv.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated Live TV channel provider for Vietnamese national & regional broadcast stations.
 * Dispatches deep links with 3-tier fallback (XemTV -> VTVgo / MyTV / TV360 / SCTV -> Play Store).
 */
public final class LiveTvChannelProvider {
    private static final String TAG = "TvHome.LiveTv";

    public static final String PKG_XEMTV = "com.kt.apps.media.xemtv.beta";
    public static final String PKG_VTVGO = "vn.vtv.vtvgotv";
    public static final String PKG_MYTV = "vn.mytvnet.b2cott";
    public static final String PKG_TV360 = "com.viettel.tv360.tv";
    public static final String PKG_SCTV = "io.vimai.sctvonline.androidtv";
    public static final String PKG_FPT = "net.fptplay.ottbox";

    public static final class ChannelDef {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String posterUrl;
        public final String category; // "VTV", "HTV", "THVL", "SCTV", "SPORTS", "NEWS"

        public ChannelDef(String id, String title, String subtitle, String posterUrl, String category) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.posterUrl = posterUrl;
            this.category = category;
        }
    }

    private static final List<ChannelDef> CHANNELS = new ArrayList<>();

    static {
        CHANNELS.add(new ChannelDef("vtv1-hd", "VTV1 HD", "Thời sự - Chính luận", "https://i.postimg.cc/F1FvzstX/V1.png", "VTV"));
        CHANNELS.add(new ChannelDef("vtv2-hd", "VTV2 HD", "Khoa giáo", "https://i.postimg.cc/yDR4tDRm/V2.png", "VTV"));
        CHANNELS.add(new ChannelDef("vtv3-hd", "VTV3 HD", "Giải trí - Thể thao", "https://i.postimg.cc/B8cWZRM0/V3.png", "VTV"));
        CHANNELS.add(new ChannelDef("vtv4-hd", "VTV4 HD", "Đối ngoại", "https://i.postimg.cc/Wqzxnzj0/V4.png", "VTV"));
        CHANNELS.add(new ChannelDef("vtv5-hd", "VTV5 HD", "Tiếng dân tộc", "https://i.postimg.cc/ZC91vWVM/V5.png", "VTV"));
        CHANNELS.add(new ChannelDef("vtv7-hd", "VTV7 HD", "Giáo dục", "https://i.postimg.cc/ykSbYwN8/V7.png", "VTV"));
        CHANNELS.add(new ChannelDef("vtv8-hd", "VTV8 HD", "Miền Trung", "https://i.postimg.cc/k2QYWK8w/V8.png", "VTV"));
        CHANNELS.add(new ChannelDef("vtv9-hd", "VTV9 HD", "Khu vực phía Nam", "https://i.postimg.cc/phT0NSS6/V9.png", "VTV"));
        CHANNELS.add(new ChannelDef("htv7-hd", "HTV7 HD", "Giải trí TP.HCM", "https://i.imgur.com/n2PEK28.png", "HTV"));
        CHANNELS.add(new ChannelDef("htv9-hd", "HTV9 HD", "Tin tức TP.HCM", "https://i.imgur.com/6GSN524.png", "HTV"));
        CHANNELS.add(new ChannelDef("vie-channel-htv2-hd", "HTV2 - Vie Channel", "Phim truyện", "https://i.imgur.com/2npoF3Q.png", "HTV"));
        CHANNELS.add(new ChannelDef("vinh-long-1-hd", "THVL1 HD", "Phim truyện Vĩnh Long", "https://i.imgur.com/q3fjpYc.png", "THVL"));
        CHANNELS.add(new ChannelDef("vinh-long-2-hd", "THVL2 HD", "Giải trí tổng hợp", "https://i.imgur.com/zGv54Ed.png", "THVL"));
        CHANNELS.add(new ChannelDef("an-ninh-hd", "ANTV HD", "An ninh TV", "https://static.wikia.nocookie.net/logos/images/1/1a/ANTV_2023.png/revision/latest/scale-to-width-down/512?path-prefix=vi", "NEWS"));
        CHANNELS.add(new ChannelDef("sctvhdpth", "SCTV Phim", "Phim truyện tổng hợp", "https://i.imgur.com/R8EBxJj.png", "SCTV"));
        CHANNELS.add(new ChannelDef("onsports", "On Sports", "Thể thao trực tiếp", "https://raw.githubusercontent.com/HaNoiIPTV/HaNoiIPTV.m3u/refs/heads/master/ON%20Sports.png", "SPORTS"));
        CHANNELS.add(new ChannelDef("onfootball", "On Football", "Bóng đá đỉnh cao", "https://raw.githubusercontent.com/HaNoiIPTV/HaNoiIPTV.m3u/refs/heads/master/ON%20Football.png", "SPORTS"));
    }

    public static List<ChannelDef> getChannels() {
        return java.util.Collections.unmodifiableList(CHANNELS);
    }

    public static TvRecommendationReader.MovieSection getLiveTvSection(Context context) {
        if (context == null) return null;

        boolean xemTvInstalled = isPackageInstalled(context, PKG_XEMTV);
        boolean vtvGoInstalled = isPackageInstalled(context, PKG_VTVGO);
        boolean myTvInstalled = isPackageInstalled(context, PKG_MYTV);
        boolean tv360Installed = isPackageInstalled(context, PKG_TV360);
        boolean sctvInstalled = isPackageInstalled(context, PKG_SCTV);

        List<TvRecommendationReader.MovieItem> items = new ArrayList<>();
        long idGen = 900000L;

        for (ChannelDef def : CHANNELS) {
            String targetPkg = null;
            String intentUri = null;

            if (xemTvInstalled) {
                // Tier 1 Priority: Televizo / XemTV direct playback
                targetPkg = PKG_XEMTV;
                intentUri = "xemtv://tv/channel/" + def.id;
            } else if ("VTV".equals(def.category) && vtvGoInstalled) {
                // Tier 1 Priority for VTV channels: VTVgo TV
                targetPkg = PKG_VTVGO;
                intentUri = "vtvgo://play?channel_id=" + getVtvGoChannelId(def.id);
            } else if (myTvInstalled) {
                targetPkg = PKG_MYTV;
                intentUri = null;
            } else if (tv360Installed) {
                targetPkg = PKG_TV360;
                intentUri = null;
            } else if ("SCTV".equals(def.category) && sctvInstalled) {
                targetPkg = PKG_SCTV;
                intentUri = null;
            } else {
                targetPkg = "VTV".equals(def.category) ? PKG_VTVGO : PKG_XEMTV;
                intentUri = "xemtv://tv/channel/" + def.id;
            }

            Uri posterUri = def.posterUrl != null ? Uri.parse(def.posterUrl) : null;
            items.add(new TvRecommendationReader.MovieItem(
                    idGen++,
                    def.title,
                    def.subtitle,
                    0,
                    0,
                    posterUri,
                    intentUri,
                    0,
                    0,
                    0,
                    false, // isMovie = false
                    TvRecommendationReader.Contract.ASPECT_RATIO_16_9,
                    targetPkg
            ));
        }

        return new TvRecommendationReader.MovieSection(
                "live_channels",
                "Truyền hình trực tuyến",
                items,
                false // 16:9 landscape cards
        );
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        if (context == null || pkg == null || pkg.isEmpty()) return false;
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static String getVtvGoChannelId(String id) {
        if ("vtv1-hd".equals(id)) return "1";
        if ("vtv2-hd".equals(id)) return "2";
        if ("vtv3-hd".equals(id)) return "3";
        if ("vtv4-hd".equals(id)) return "4";
        if ("vtv5-hd".equals(id)) return "5";
        if ("vtv7-hd".equals(id)) return "6";
        if ("vtv8-hd".equals(id)) return "7";
        if ("vtv9-hd".equals(id)) return "8";
        return "1";
    }

    private LiveTvChannelProvider() {}
}
