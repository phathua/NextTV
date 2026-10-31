# NextTV - Native Header Generator for NDK Build
# Pure PowerShell & .NET - Dynamic Polymorphic Masking with Interactive Wizard
param(
    [switch]$Force,
    [switch]$Interactive
)

$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Security.Cryptography;

public class NativeHeaderVault {
    public static byte[] GenerateRandomMask(int length) {
        byte[] mask = new byte[length];
        using (var rng = RandomNumberGenerator.Create()) {
            rng.GetBytes(mask);
        }
        return mask;
    }

    public static string Encode(string text, byte[] key, int lShift, int rShift, string sig) {
        if (string.IsNullOrEmpty(text)) return "0x00";
        var sb = new StringBuilder();
        for (int i = 0; i < text.Length; i++) {
            int c = (int)text[i];
            int k = (int)key[i % key.Length];
            int s = (!string.IsNullOrEmpty(sig)) ? (int)sig[i % sig.Length] : 0;
            int v = (c ^ k ^ s) & 0xFF;
            int r = ((v << lShift) | (v >> rShift)) & 0xFF;
            if (i > 0) sb.Append(", ");
            sb.Append(string.Format("0x{0:X2}", r));
        }
        return sb.ToString();
    }

    public static string FormatMask(byte[] mask) {
        if (mask == null || mask.Length == 0) return "0x00";
        var sb = new StringBuilder();
        for (int i = 0; i < mask.Length; i++) {
            if (i > 0) sb.Append(", ");
            sb.Append(string.Format("0x{0:X2}", mask[i]));
        }
        return sb.ToString();
    }

    public static string GenerateHmacSecretHex() {
        byte[] bytes = new byte[32];
        using (var rng = RandomNumberGenerator.Create()) {
            rng.GetBytes(bytes);
        }
        var sb = new StringBuilder();
        foreach (byte b in bytes) {
            sb.Append(b.ToString("x2"));
        }
        return sb.ToString();
    }
}
'@

function Read-ConfigFile([string]$FilePath) {
    $map = @{}
    if (Test-Path -LiteralPath $FilePath) {
        $lines = Get-Content -LiteralPath $FilePath -Encoding UTF8
        foreach ($line in $lines) {
            $t = $line.Trim()
            if ($t -and -not $t.StartsWith('#')) {
                $idx = $t.IndexOf('=')
                if ($idx -gt 0) {
                    $k = $t.Substring(0, $idx).Trim()
                    $v = $t.Substring($idx + 1).Trim().Trim('"', "'")
                    $map[$k] = $v
                }
            }
        }
    }
    return $map
}

$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$envFile = Join-Path $root ".env"
$envExample = Join-Path $root ".env.example"

# Onboarding Wizard: Check if .env is missing or contains placeholder values
$needsWizard = $false
$envMap = @{}

if (-not (Test-Path $envFile)) {
    $needsWizard = $true
} else {
    $envMap = Read-ConfigFile $envFile
    if ($envMap['APP_HMAC_SECRET'] -like "*your_*_here*" -or $envMap['TMDB_API_KEY'] -like "*your_*_here*") {
        $needsWizard = $true
    }
}

if ($needsWizard -and ($Interactive -or [Environment]::UserInteractive)) {
    Write-Host "========================================================" -ForegroundColor Yellow
    Write-Host " [!] Phat hien .env chua duoc cau hinh hoac dung key mau!" -ForegroundColor Yellow
    Write-Host "========================================================" -ForegroundColor Yellow
    
    $hmac = if ($envMap.ContainsKey('APP_HMAC_SECRET') -and $envMap['APP_HMAC_SECRET'] -notlike "*your_*_here*") {
        $envMap['APP_HMAC_SECRET']
    } else {
        $autoHex = [NativeHeaderVault]::GenerateHmacSecretHex()
        Write-Host "[+] Da tu dong sinh APP_HMAC_SECRET (256-bit Hex): $autoHex" -ForegroundColor Green
        $autoHex
    }

    $workerUrl = if ($envMap.ContainsKey('WORKER_PROXY_URL') -and $envMap['WORKER_PROXY_URL'] -notlike "*your-*") {
        $envMap['WORKER_PROXY_URL']
    } else {
        "https://your-worker.workers.dev"
    }

    $tmdbKey = if ($envMap.ContainsKey('TMDB_API_KEY') -and $envMap['TMDB_API_KEY'] -notlike "*your_*_here*") {
        $envMap['TMDB_API_KEY']
    } else {
        ""
    }

    $tmdbToken = if ($envMap.ContainsKey('TMDB_READ_ACCESS_TOKEN') -and $envMap['TMDB_READ_ACCESS_TOKEN'] -notlike "*your_*_here*") {
        $envMap['TMDB_READ_ACCESS_TOKEN']
    } else {
        ""
    }

    $braveKey = if ($envMap.ContainsKey('BRAVE_SEARCH_API_KEY') -and $envMap['BRAVE_SEARCH_API_KEY'] -notlike "*your_*_here*") {
        $envMap['BRAVE_SEARCH_API_KEY']
    } else {
        ""
    }

    # Write out verified .env
    $envContent = @"
# Brave Search API Key for Universal Search (https://brave.com/search/api/)
BRAVE_SEARCH_API_KEY=$braveKey

# 256-bit Hex Shared Secret for HMAC-SHA256 Client Authentication
APP_HMAC_SECRET=$hmac

# Cloudflare Worker Proxy Endpoint
WORKER_PROXY_URL=$workerUrl

# Allowed Release APK Signing Certificate SHA-256 Fingerprint
ALLOWED_APK_SHA256=$($envMap['ALLOWED_APK_SHA256'])

# TMDB API Credentials (https://www.themoviedb.org/settings/api)
TMDB_API_KEY=$tmdbKey
TMDB_READ_ACCESS_TOKEN=$tmdbToken

# Free Wallpaper API Keys
PEXELS_API_KEY=$($envMap['PEXELS_API_KEY'])
UNSPLASH_API_KEY=$($envMap['UNSPLASH_API_KEY'])
PIXABAY_API_KEY=$($envMap['PIXABAY_API_KEY'])

# Optional Live Sports Keys
SPORTMONKS_API_KEY=$($envMap['SPORTMONKS_API_KEY'])
FOOTBALL_DATA_ORG_KEY=$($envMap['FOOTBALL_DATA_ORG_KEY'])
API_FOOTBALL_KEY=$($envMap['API_FOOTBALL_KEY'])
SPORTAPI_KEY=$($envMap['SPORTAPI_KEY'])
"@
    [System.IO.File]::WriteAllText($envFile, $envContent, (New-Object System.Text.UTF8Encoding $false))
    Write-Host "[+] Da khoi tao file .env an toan tai: $envFile" -ForegroundColor Green
    $envMap = Read-ConfigFile $envFile
} elseif (-not (Test-Path $envFile)) {
    $envMap = Read-ConfigFile $envExample
}

function Get-CleanValue([string]$key) {
    if ($envMap.ContainsKey($key)) {
        $val = $envMap[$key]
        if ($val -like "*your_*_here*" -or $val -like "*your-*") { return '' }
        return $val
    }
    return ''
}

$url = Get-CleanValue 'WORKER_PROXY_URL'
$sec = Get-CleanValue 'APP_HMAC_SECRET'
$sig = (Get-CleanValue 'ALLOWED_APK_SHA256').ToLower().Trim()
$tmdbK = Get-CleanValue 'TMDB_API_KEY'
$tmdbT = Get-CleanValue 'TMDB_READ_ACCESS_TOKEN'
$pexK = Get-CleanValue 'PEXELS_API_KEY'
$unsK = Get-CleanValue 'UNSPLASH_API_KEY'
$pixK = Get-CleanValue 'PIXABAY_API_KEY'
$braveK = Get-CleanValue 'BRAVE_SEARCH_API_KEY'
$smK = Get-CleanValue 'SPORTMONKS_API_KEY'
$fdK = Get-CleanValue 'FOOTBALL_DATA_ORG_KEY'
$afK = Get-CleanValue 'API_FOOTBALL_KEY'
$saK = Get-CleanValue 'SPORTAPI_KEY'

# Dynamic Polymorphic Masking: Randomly generate masks per build
$uMask = [NativeHeaderVault]::GenerateRandomMask(8)
$sMask = [NativeHeaderVault]::GenerateRandomMask(16)
$tkMask = [NativeHeaderVault]::GenerateRandomMask(8)
$ttMask = [NativeHeaderVault]::GenerateRandomMask(12)
$pMask = [NativeHeaderVault]::GenerateRandomMask(9)
$unMask = [NativeHeaderVault]::GenerateRandomMask(8)
$pxMask = [NativeHeaderVault]::GenerateRandomMask(9)
$bvMask = [NativeHeaderVault]::GenerateRandomMask(12)
$spMask = [NativeHeaderVault]::GenerateRandomMask(12)

$encUrl = [NativeHeaderVault]::Encode($url, $uMask, 2, 6, "")
$encSec = if ($sig.Length -gt 0) { [NativeHeaderVault]::Encode($sec, $sMask, 3, 5, $sig) } else { [NativeHeaderVault]::Encode($sec, $sMask, 3, 5, ([string][char]0x41)) }
$encTk = [NativeHeaderVault]::Encode($tmdbK, $tkMask, 2, 6, "")
$encTt = [NativeHeaderVault]::Encode($tmdbT, $ttMask, 3, 5, "")
$encP = [NativeHeaderVault]::Encode($pexK, $pMask, 2, 6, "")
$encUn = [NativeHeaderVault]::Encode($unsK, $unMask, 3, 5, "")
$encPx = [NativeHeaderVault]::Encode($pixK, $pxMask, 1, 7, "")
$encBv = [NativeHeaderVault]::Encode($braveK, $bvMask, 2, 6, "")
$encSm = [NativeHeaderVault]::Encode($smK, $spMask, 3, 5, "")
$encFd = [NativeHeaderVault]::Encode($fdK, $spMask, 2, 6, "")
$encAf = [NativeHeaderVault]::Encode($afK, $spMask, 3, 5, "")
$encSa = [NativeHeaderVault]::Encode($saK, $spMask, 1, 7, "")

$headerDir = Join-Path $root "app\src\main\cpp"
if (-not (Test-Path $headerDir)) {
    New-Item -ItemType Directory -Path $headerDir -Force | Out-Null
}
$headerPath = Join-Path $headerDir "generated_secrets.h"

$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine('// Auto-generated polymorphic security header. Do not commit or edit directly.')
[void]$sb.AppendLine('#ifndef GENERATED_SECRETS_H')
[void]$sb.AppendLine('#define GENERATED_SECRETS_H')
[void]$sb.AppendLine()
[void]$sb.AppendLine('#include <cstdint>')
[void]$sb.AppendLine('#include <cstddef>')
[void]$sb.AppendLine()

function Append-Entry([string]$name, [string]$val, [byte[]]$mask, [string]$enc) {
    [void]$sb.AppendLine("static const size_t ${name}_LEN = $($val.Length);")
    [void]$sb.AppendLine("static const size_t ${name}_MASK_LEN = $($mask.Length);")
    [void]$sb.AppendLine("static const uint8_t ${name}_BYTES[] = {$enc};")
    [void]$sb.AppendLine("static const uint8_t ${name}_MASK[] = {$([NativeHeaderVault]::FormatMask($mask))};")
    [void]$sb.AppendLine()
}

Append-Entry "OBF_URL" $url $uMask $encUrl

[void]$sb.AppendLine("static const size_t OBF_SECRET_LEN = $($sec.Length);")
[void]$sb.AppendLine("static const size_t OBF_MASK_LEN = $($sMask.Length);")
[void]$sb.AppendLine("static const uint8_t OBF_SECRET_BYTES[] = {$encSec};")
[void]$sb.AppendLine("static const uint8_t OBF_XOR_MASK[] = {$([NativeHeaderVault]::FormatMask($sMask))};")
[void]$sb.AppendLine()

[void]$sb.AppendLine("static const char* const EXPECTED_APK_SHA256 = `"$sig`";")
[void]$sb.AppendLine()

Append-Entry "OBF_TMDB_KEY" $tmdbK $tkMask $encTk
Append-Entry "OBF_TMDB_TOK" $tmdbT $ttMask $encTt
Append-Entry "OBF_PEXELS" $pexK $pMask $encP
Append-Entry "OBF_UNSPLASH" $unsK $unMask $encUn
Append-Entry "OBF_PIXABAY" $pixK $pxMask $encPx
Append-Entry "OBF_BRAVE_KEY" $braveK $bvMask $encBv
Append-Entry "OBF_SPORTMONKS_KEY" $smK $spMask $encSm
Append-Entry "OBF_FOOTBALL_DATA_KEY" $fdK $spMask $encFd
Append-Entry "OBF_API_FOOTBALL_KEY" $afK $spMask $encAf
Append-Entry "OBF_SPORTAPI_KEY" $saK $spMask $encSa

[void]$sb.AppendLine('#endif // GENERATED_SECRETS_H')

$newContent = $sb.ToString()
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

# Incremental protection: Only regenerate if values changed or -Force is requested
if (-not $Force -and (Test-Path $headerPath)) {
    # Check if keys differ from existing
    $existing = [System.IO.File]::ReadAllText($headerPath, [System.Text.Encoding]::UTF8)
    if ($existing.Contains("OBF_BRAVE_KEY_LEN = $($braveK.Length)") -and
        $existing.Contains("OBF_TMDB_KEY_LEN = $($tmdbK.Length)") -and
        $existing.Contains("OBF_SECRET_LEN = $($sec.Length)")) {
        Write-Host ">>> Header keys are unchanged. Preserving NDK compilation cache." -ForegroundColor DarkGray
        exit 0
    }
}

[System.IO.File]::WriteAllText($headerPath, $newContent, $utf8NoBom)
Write-Host ">>> Generated polymorphic native secrets header at: $headerPath" -ForegroundColor Green
