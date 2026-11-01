package com.next.tv.launcher.clean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocalAdbBridgeResultTest {

    @Test
    public void parsesSuccessfulShellCommandAndOutput() {
        LocalAdbBridge.ShellCommandResult result = LocalAdbBridge.parseShellCommandResult(
                "activity_manager_constants=max_cached_processes=2\n__NEXTTV_EXIT_CODE__=0\r\n");

        assertNotNull(result);
        assertTrue(result.success);
        assertEquals(0, result.exitCode);
        assertEquals("activity_manager_constants=max_cached_processes=2", result.output);
    }

    @Test
    public void parsesRejectedShellCommandWithoutMarkingItSuccessful() {
        LocalAdbBridge.ShellCommandResult result = LocalAdbBridge.parseShellCommandResult(
                "Permission Denial: writing settings requires WRITE_SECURE_SETTINGS\n__NEXTTV_EXIT_CODE__=255\n");

        assertNotNull(result);
        assertFalse(result.success);
        assertEquals(255, result.exitCode);
        assertTrue(result.error.contains("Permission Denial"));
    }

    @Test
    public void ignoresOutputWithoutExitMarker() {
        assertNull(LocalAdbBridge.parseShellCommandResult("shell connection failed"));
    }

    @Test
    public void calculatesMemoryPercentageWithoutInventedFallback() {
        assertEquals(75, SmartRamGovernor.calculateUsedPercent(100, 25));
        assertEquals(0, SmartRamGovernor.calculateUsedPercent(100, 150));
        assertEquals(-1, SmartRamGovernor.calculateUsedPercent(0, 0));
    }
}
