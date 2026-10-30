package com.next.tv.launcher;

import java.util.Calendar;

/**
 * Astronomical Vietnamese Lunar Calendar conversion (Thuật toán âm lịch Hồ Ngọc Đức).
 * Operates purely offline using integer arithmetic and astronomical approximations for Timezone UTC+7.
 * Execution time is less than 0.1ms with zero memory allocations after calculation.
 */
public final class VietnameseLunarCalendar {
    private static final double PI = Math.PI;
    private static final int TIME_ZONE = 7; // Vietnam Standard Time (GMT+7)

    private static final String[] CAN = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
    private static final String[] CHI = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};

    private VietnameseLunarCalendar() {}

    public static final class LunarDate {
        public final int day;
        public final int month;
        public final int year;
        public final boolean isLeap;

        public LunarDate(int day, int month, int year, boolean isLeap) {
            this.day = day;
            this.month = month;
            this.year = year;
            this.isLeap = isLeap;
        }

        public String formatConcise() {
            return day + "/" + month + (isLeap ? " Nhuận" : "") + " ÂL";
        }

        public String formatWithCanChi() {
            int canIndex = (year + 6) % 10;
            if (canIndex < 0) canIndex += 10;
            int chiIndex = (year + 8) % 12;
            if (chiIndex < 0) chiIndex += 12;
            return day + "/" + month + (isLeap ? " Nhuận" : "") + " ÂL · Năm " + CAN[canIndex] + " " + CHI[chiIndex];
        }

        public String formatFull() {
            int canIndex = (year + 6) % 10;
            int chiIndex = (year + 8) % 12;
            String canChi = CAN[canIndex] + " " + CHI[chiIndex];
            return day + "/" + month + (isLeap ? " (Nhuận)" : "") + " Âm lịch (" + canChi + ")";
        }
    }

    public static LunarDate today() {
        Calendar c = Calendar.getInstance();
        return convert(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    public static LunarDate convert(int dd, int mm, int yy) {
        int dayNumber = jdFromDate(dd, mm, yy);
        int k = (int) Math.floor((dayNumber - 2415021.076998695) / 29.530588853);
        int monthStart = getNewMoonDay(k + 1, TIME_ZONE);
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k, TIME_ZONE);
        }
        int a11 = getLunarMonth11(yy, TIME_ZONE);
        int b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = yy;
            a11 = getLunarMonth11(yy - 1, TIME_ZONE);
        } else {
            lunarYear = yy + 1;
            b11 = getLunarMonth11(yy + 1, TIME_ZONE);
        }
        int lunarDay = dayNumber - monthStart + 1;
        int diff = (int) Math.floor((monthStart - a11) / 29.0);
        boolean isLeap = false;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapMonthDiff = getLeapMonthOffset(a11, TIME_ZONE);
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10;
                if (diff == leapMonthDiff) {
                    isLeap = true;
                }
            }
        }
        if (lunarMonth > 12) {
            lunarMonth -= 12;
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear -= 1;
        }
        return new LunarDate(lunarDay, lunarMonth, lunarYear, isLeap);
    }

    private static int jdFromDate(int dd, int mm, int yy) {
        int a = (14 - mm) / 12;
        int y = yy + 4800 - a;
        int m = mm + 12 * a - 3;
        return dd + ((153 * m + 2) / 5) + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
    }

    private static int getNewMoonDay(int k, int timeZone) {
        double T = k / 1236.85;
        double T2 = T * T;
        double T3 = T2 * T;
        double dr = PI / 180.0;
        double Jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * T2 - 0.000000155 * T3;
        Jd1 += 0.00033 * Math.sin((166.56 + 132.87 * T - 0.009173 * T2) * dr);
        double M = 359.2242 + 29.10535608 * k - 0.0000333 * T2 - 0.00000347 * T3;
        double Mpr = 306.0253 + 385.81691806 * k + 0.0107306 * T2 + 0.00001236 * T3;
        double F = 21.2964 + 390.67050646 * k - 0.0016528 * T2 - 0.00000239 * T3;
        double C1 = (0.1734 - 0.000393 * T) * Math.sin(M * dr) + 0.0021 * Math.sin(2.0 * dr * M);
        C1 = C1 - 0.4068 * Math.sin(Mpr * dr) + 0.0161 * Math.sin(2.0 * dr * Mpr);
        C1 = C1 - 0.0004 * Math.sin(3.0 * dr * Mpr);
        C1 = C1 + 0.0104 * Math.sin(2.0 * dr * F) - 0.0051 * Math.sin((M + Mpr) * dr);
        C1 = C1 - 0.0074 * Math.sin((M - Mpr) * dr) + 0.0004 * Math.sin((2.0 * F + M) * dr);
        C1 = C1 - 0.0004 * Math.sin((2.0 * F - M) * dr) - 0.0006 * Math.sin((2.0 * F + Mpr) * dr);
        C1 = C1 + 0.0010 * Math.sin((2.0 * F - Mpr) * dr) + 0.0005 * Math.sin((M + 2.0 * Mpr) * dr);
        double deltat;
        if (T < -11.0) {
            deltat = 0.001 + 0.000839 * T + 0.0002261 * T2 - 0.00000845 * T3 - 0.00000061 * T * T3;
        } else {
            deltat = -0.000078 + 0.0000799 * T + 0.0001815 * T2;
        }
        double JdNew = Jd1 + C1 - deltat;
        return (int) Math.floor(JdNew + 0.5 + timeZone / 24.0);
    }

    private static int getSunLongitude(int jdn, int timeZone) {
        double T = (jdn - 2451545.0 + 0.5 - timeZone / 24.0) / 36525.0;
        double T2 = T * T;
        double dr = PI / 180.0;
        double M = 357.52910 + 35999.05030 * T - 0.0001559 * T2 - 0.00000048 * T * T2;
        double L0 = 280.46645 + 36000.76983 * T + 0.0003032 * T2;
        double DL = (1.914600 - 0.004817 * T - 0.000014 * T2) * Math.sin(dr * M);
        DL += (0.019993 - 0.000101 * T) * Math.sin(dr * 2.0 * M) + 0.000290 * Math.sin(dr * 3.0 * M);
        double L = (L0 + DL) * dr;
        L = L - 2.0 * PI * Math.floor(L / (2.0 * PI));
        return (int) Math.floor(L / (PI / 6.0));
    }

    private static int getLunarMonth11(int yy, int timeZone) {
        int off = jdFromDate(31, 12, yy) - 2415021;
        int k = (int) Math.floor(off / 29.530588853);
        int nm = getNewMoonDay(k, timeZone);
        int sunLong = getSunLongitude(nm, timeZone);
        if (sunLong >= 9) {
            nm = getNewMoonDay(k - 1, timeZone);
        }
        return nm;
    }

    private static int getLeapMonthOffset(int a11, int timeZone) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int last = 0;
        int i = 1;
        int arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
        while (true) {
            last = arc;
            i++;
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
            if (arc == last || i > 14) {
                break;
            }
        }
        return i - 1;
    }
}
