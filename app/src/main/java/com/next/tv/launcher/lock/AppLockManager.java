package com.next.tv.launcher.lock;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Flexible 2-tier app locking and hiding management.
 * Tier 1: Casual UI Hide (no PIN required).
 * Tier 2: PIN-Protected App Lock & Vault Hide (PIN required).
 * Supports complete PIN removal ("Gỡ mã khóa PIN").
 */
public final class AppLockManager {
    private static final String PREF_NAME = "app_lock_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_PIN_CONFIGURED = "pin_configured";
    private static final String KEY_LOCKED_PACKAGES = "locked_packages";
    private static final String KEY_CASUAL_HIDDEN_PACKAGES = "casual_hidden_packages";
    private static final String KEY_VAULT_HIDDEN_PACKAGES = "vault_hidden_packages";
    private static final String KEY_KID_WHITELIST_PACKAGES = "kid_whitelist_packages";
    private static final String KEY_KID_MODE_ACTIVE = "kid_mode_active";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until_epoch_ms";

    private static final String DEFAULT_SALT = "tv_lock_salt_v2";

    // Known restricted packages in Kid Mode (Browsers, TV Settings, File Managers, Installers)
    private static final Set<String> KID_MODE_RESTRICTED = new HashSet<>(Arrays.asList(
            "com.coccoc.trinhduyet_tv",
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.google.android.tv.settings",
            "com.android.tv.settings",
            "com.android.settings",
            "com.cvte.tv.filebrowser",
            "com.lonelycatgames.Xplore",
            "com.anExplorer",
            "com.google.android.documentsui",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
    ));

    private static AppLockManager instance;
    private final SharedPreferences prefs;

    private AppLockManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        migrateLegacyKeys();
    }

    public static synchronized AppLockManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppLockManager(context);
        }
        return instance;
    }

    private void migrateLegacyKeys() {
        // Migrate legacy hidden_packages into casual_hidden_packages if needed
        if (prefs.contains("hidden_packages") && !prefs.contains(KEY_CASUAL_HIDDEN_PACKAGES)) {
            Set<String> oldHidden = prefs.getStringSet("hidden_packages", null);
            if (oldHidden != null) {
                prefs.edit().putStringSet(KEY_CASUAL_HIDDEN_PACKAGES, oldHidden).remove("hidden_packages").apply();
            }
        }
    }

    public boolean hasPin() {
        return prefs.getBoolean(KEY_PIN_CONFIGURED, false) && prefs.contains(KEY_PIN_HASH);
    }

    public void setupInitialPin(String pin) {
        if (pin == null || pin.length() != 4) return;
        byte[] saltBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(saltBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : saltBytes) sb.append(String.format("%02x", b));
        String salt = sb.toString();
        String hash = hashPin(pin, salt);
        prefs.edit()
                .putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, salt)
                .putBoolean(KEY_PIN_CONFIGURED, true)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply();
    }

    public boolean removePin(String currentPin) {
        if (!verifyPin(currentPin)) return false;
        prefs.edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .putBoolean(KEY_PIN_CONFIGURED, false)
                .remove(KEY_LOCKED_PACKAGES)
                .remove(KEY_VAULT_HIDDEN_PACKAGES)
                .putBoolean(KEY_KID_MODE_ACTIVE, false)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply();
        return true;
    }

    public void forceRemovePin() {
        prefs.edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .putBoolean(KEY_PIN_CONFIGURED, false)
                .remove(KEY_LOCKED_PACKAGES)
                .remove(KEY_VAULT_HIDDEN_PACKAGES)
                .putBoolean(KEY_KID_MODE_ACTIVE, false)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply();
    }

    private static String hashPin(String pin, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = (salt + pin).getBytes(StandardCharsets.UTF_8);
            int rounds = DEFAULT_SALT.equals(salt) ? 1 : 5000;
            for (int i = 0; i < rounds; i++) {
                encoded = digest.digest(encoded);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : encoded) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return pin;
        }
    }

    public long getRemainingLockoutSeconds() {
        long lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L);
        long now = System.currentTimeMillis();
        if (lockoutUntil > now) {
            return (lockoutUntil - now + 999) / 1000L;
        }
        return 0L;
    }

    public boolean verifyPin(String pin) {
        if (!hasPin()) return true;
        if (getRemainingLockoutSeconds() > 0) {
            return false;
        }
        if (pin == null || pin.length() != 4) return false;

        String salt = prefs.getString(KEY_PIN_SALT, DEFAULT_SALT);
        String expectedHash = prefs.getString(KEY_PIN_HASH, "");
        String actualHash = hashPin(pin, salt);

        if (expectedHash.equals(actualHash)) {
            recordSuccessfulAttempt();
            if (DEFAULT_SALT.equals(salt)) {
                setupInitialPin(pin);
            }
            return true;
        } else {
            recordFailedAttempt();
            return false;
        }
    }

    public void recordSuccessfulAttempt() {
        prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply();
    }

    public void recordFailedAttempt() {
        int failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        long lockoutSec = 0;
        if (failed == 3 || failed == 4) {
            lockoutSec = 30;
        } else if (failed == 5 || failed == 6) {
            lockoutSec = 60;
        } else if (failed >= 7) {
            lockoutSec = Math.min(3600, (long) (30 * Math.pow(2, failed - 3)));
        }

        long lockoutUntil = lockoutSec > 0 ? System.currentTimeMillis() + (lockoutSec * 1000L) : 0L;
        prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, failed)
                .putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
                .apply();
    }

    public boolean setPin(String oldPin, String newPin) {
        if (hasPin() && !verifyPin(oldPin)) return false;
        if (newPin == null || newPin.length() != 4) return false;
        setupInitialPin(newPin);
        return true;
    }

    // Tier 1: Casual UI Hide (No PIN required)
    public boolean isCasualHidden(String packageName) {
        if (packageName == null) return false;
        Set<String> set = prefs.getStringSet(KEY_CASUAL_HIDDEN_PACKAGES, null);
        return set != null && set.contains(packageName);
    }

    public void setCasualHidden(String packageName, boolean hidden) {
        if (packageName == null) return;
        Set<String> current = new HashSet<>(prefs.getStringSet(KEY_CASUAL_HIDDEN_PACKAGES, new HashSet<String>()));
        if (hidden) current.add(packageName);
        else current.remove(packageName);
        prefs.edit().putStringSet(KEY_CASUAL_HIDDEN_PACKAGES, current).apply();
    }

    public Set<String> getCasualHiddenPackages() {
        Set<String> set = prefs.getStringSet(KEY_CASUAL_HIDDEN_PACKAGES, null);
        return set != null ? new HashSet<>(set) : new HashSet<String>();
    }

    // Tier 2: Vault Hidden (PIN required)
    public boolean isVaultHidden(String packageName) {
        if (packageName == null) return false;
        Set<String> set = prefs.getStringSet(KEY_VAULT_HIDDEN_PACKAGES, null);
        return set != null && set.contains(packageName);
    }

    public void setVaultHidden(String packageName, boolean hidden) {
        if (packageName == null) return;
        Set<String> current = new HashSet<>(prefs.getStringSet(KEY_VAULT_HIDDEN_PACKAGES, new HashSet<String>()));
        if (hidden) current.add(packageName);
        else current.remove(packageName);
        prefs.edit().putStringSet(KEY_VAULT_HIDDEN_PACKAGES, current).apply();
    }

    public Set<String> getVaultHiddenPackages() {
        Set<String> set = prefs.getStringSet(KEY_VAULT_HIDDEN_PACKAGES, null);
        return set != null ? new HashSet<>(set) : new HashSet<String>();
    }

    // Universal hide check: hidden if in either casual or vault
    public boolean isAppHidden(String packageName) {
        return isCasualHidden(packageName) || isVaultHidden(packageName);
    }

    public Set<String> getHiddenPackages() {
        Set<String> all = getCasualHiddenPackages();
        all.addAll(getVaultHiddenPackages());
        return all;
    }

    // Locked apps
    public boolean isAppLocked(String packageName) {
        if (packageName == null || !hasPin()) return false;
        Set<String> locked = prefs.getStringSet(KEY_LOCKED_PACKAGES, null);
        return locked != null && locked.contains(packageName);
    }

    public void setAppLocked(String packageName, boolean locked) {
        if (packageName == null) return;
        Set<String> current = new HashSet<>(prefs.getStringSet(KEY_LOCKED_PACKAGES, new HashSet<String>()));
        if (locked) current.add(packageName);
        else current.remove(packageName);
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply();
    }

    public Set<String> getLockedPackages() {
        Set<String> locked = prefs.getStringSet(KEY_LOCKED_PACKAGES, null);
        return locked != null ? new HashSet<>(locked) : new HashSet<String>();
    }

    // Kid mode
    public boolean isKidModeActive() {
        return prefs.getBoolean(KEY_KID_MODE_ACTIVE, false);
    }

    public void setKidModeActive(boolean active) {
        prefs.edit().putBoolean(KEY_KID_MODE_ACTIVE, active).apply();
    }

    public boolean isWhitelistedInKidMode(String packageName) {
        if (packageName == null) return false;
        Set<String> wl = prefs.getStringSet(KEY_KID_WHITELIST_PACKAGES, null);
        if (wl == null || wl.isEmpty()) {
            return "com.google.android.youtube.tv".equals(packageName)
                    || "com.google.android.youtube.tvkids".equals(packageName)
                    || "com.nuvio".equals(packageName);
        }
        return wl.contains(packageName);
    }

    public void setAppWhitelistedInKidMode(String packageName, boolean whitelisted) {
        if (packageName == null) return;
        Set<String> current = new HashSet<>(prefs.getStringSet(KEY_KID_WHITELIST_PACKAGES, new HashSet<String>()));
        if (whitelisted) current.add(packageName);
        else current.remove(packageName);
        prefs.edit().putStringSet(KEY_KID_WHITELIST_PACKAGES, current).apply();
    }

    public Set<String> getKidWhitelistPackages() {
        Set<String> set = prefs.getStringSet(KEY_KID_WHITELIST_PACKAGES, null);
        return set != null ? new HashSet<>(set) : new HashSet<String>();
    }

    public boolean isRestrictedInKidMode(String packageName) {
        if (!isKidModeActive() || packageName == null) return false;
        if (KID_MODE_RESTRICTED.contains(packageName) || isAppLocked(packageName) || isVaultHidden(packageName)) {
            return true;
        }
        return !isWhitelistedInKidMode(packageName);
    }
}
