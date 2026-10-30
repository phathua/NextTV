package com.next.tv.launcher.util;

/**
 * Centralized network constants to eliminate scattered hardcoded user-agents and timeout thresholds.
 */
public final class NetworkConstants {
    private NetworkConstants() {}

    /** Modern Desktop Chrome User-Agent string ensuring compatibility with web services. */
    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /** Default connect timeout in milliseconds for network requests. */
    public static final int CONNECT_TIMEOUT_MS = 8000;

    /** Default read timeout in milliseconds for network requests. */
    public static final int READ_TIMEOUT_MS = 8000;
}
