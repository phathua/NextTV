package com.next.tv.launcher.update;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AppUpdateManagerTest {

    @Test
    public void testVersionComparison() {
        // Newer versions
        assertTrue(AppUpdateManager.compareVersions("1.0.1", "1.0.0") > 0);
        assertTrue(AppUpdateManager.compareVersions("v1.0.1", "1.0.0") > 0);
        assertTrue(AppUpdateManager.compareVersions("v1.1.0", "1.0.5") > 0);
        assertTrue(AppUpdateManager.compareVersions("2.0.0", "1.9.9") > 0);
        assertTrue(AppUpdateManager.compareVersions("1.0.0.1", "1.0.0") > 0);

        // Equal versions
        assertEquals(0, AppUpdateManager.compareVersions("1.0.0", "1.0.0"));
        assertEquals(0, AppUpdateManager.compareVersions("v1.0.0", "1.0.0"));
        assertEquals(0, AppUpdateManager.compareVersions("1.0", "1.0.0"));

        // Older versions
        assertTrue(AppUpdateManager.compareVersions("1.0.0", "1.0.1") < 0);
        assertTrue(AppUpdateManager.compareVersions("0.9.9", "1.0.0") < 0);
    }

    @Test
    public void testCleanCommitMessageRemovesHashes() {
        // Line with commit hash at start
        String raw1 = "* b72c018fdf9964e2780df8d256dd311a1898a6cb: Tối ưu giao diện Terminal";
        String clean1 = AppUpdateManager.cleanCommitMessage(raw1);
        assertEquals("Tối ưu giao diện Terminal", clean1);
        assertFalse(clean1.contains("b72c018"));

        // Line with short hash in parentheses
        String raw2 = "* Nâng cấp kết nối TV (6a9fc4a)";
        String clean2 = AppUpdateManager.cleanCommitMessage(raw2);
        assertEquals("Nâng cấp kết nối TV", clean2);
        assertFalse(clean2.contains("6a9fc4a"));

        // Line with GitHub PR and commit link
        String raw3 = "* feat: nâng chuẩn targetSdk 35 by @phathua in https://github.com/phathua/NextTV/commit/2f5979ffd5f4a73838a1feb3e42efe72ea704c34";
        String clean3 = AppUpdateManager.cleanCommitMessage(raw3);
        assertEquals("feat: nâng chuẩn targetSdk 35", clean3);
        assertFalse(clean3.contains("2f5979f"));

        // Clean accented Vietnamese message remains clean
        String raw4 = "Hỗ trợ chạy trên macOS: sửa lỗi tạo mã bảo mật";
        String clean4 = AppUpdateManager.cleanCommitMessage(raw4);
        assertEquals("Hỗ trợ chạy trên macOS: sửa lỗi tạo mã bảo mật", clean4);
    }

    @Test
    public void testParseChangelogLines() {
        String body = "## What's Changed\n" +
                "* b72c018 Tối ưu giao diện Terminal\n" +
                "* [6a9fc4a](https://github.com/phathua/NextTV/commit/6a9fc4a) Nâng cấp kết nối TV\n" +
                "* Full Changelog: https://github.com/phathua/NextTV/compare/v1.0.0...v1.0.1";

        List<String> lines = AppUpdateManager.parseChangelogLines(body);
        assertEquals(2, lines.size());
        assertEquals("Tối ưu giao diện Terminal", lines.get(0));
        assertEquals("Nâng cấp kết nối TV", lines.get(1));
        assertFalse(lines.get(0).contains("b72c018"));
        assertFalse(lines.get(1).contains("6a9fc4a"));
    }
}
