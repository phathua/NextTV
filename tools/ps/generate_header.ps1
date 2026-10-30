# NextTV - Native Header Generator for NDK Build
# Pure PowerShell & .NET - zero external dependencies.
param()

$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @'
using System;
using System.Text;

public class NativeHeaderVault {
    public static string Encode(string text, byte[] key, int lShift, int rShift, string sig) {
        if (string.IsNullOrEmpty(text)) return "";
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
        if (mask == null || mask.Length == 0) return "";
        var sb = new StringBuilder();
        for (int i = 0; i < mask.Length; i++) {
            if (i > 0) sb.Append(", ");
            sb.Append(string.Format("0x{0:X2}", mask[i]));
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
                    $v = $t.Substring($idx + 1).Trim()
                    $map[$k] = $v
                }
            }
        }
    }
    return $map
}

$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$envMap = Read-ConfigFile (Join-Path $root ".env")
if ($envMap.Count -eq 0) {
    $envMap = Read-ConfigFile (Join-Path $root ".env.example")
}

$url = if ($envMap.ContainsKey('WORKER_PROXY_URL')) { $envMap['WORKER_PROXY_URL'] } else { '' }
$sec = if ($envMap.ContainsKey('APP_HMAC_SECRET')) { $envMap['APP_HMAC_SECRET'] } else { '' }
$sig = if ($envMap.ContainsKey('ALLOWED_APK_SHA256')) { $envMap['ALLOWED_APK_SHA256'].ToLower().Trim() } else { '' }
$tmdbK = if ($envMap.ContainsKey('TMDB_API_KEY')) { $envMap['TMDB_API_KEY'] } else { '' }
$tmdbT = if ($envMap.ContainsKey('TMDB_READ_ACCESS_TOKEN')) { $envMap['TMDB_READ_ACCESS_TOKEN'] } else { '' }
$pexK = if ($envMap.ContainsKey('PEXELS_API_KEY')) { $envMap['PEXELS_API_KEY'] } else { '' }
$unsK = if ($envMap.ContainsKey('UNSPLASH_API_KEY')) { $envMap['UNSPLASH_API_KEY'] } else { '' }
$pixK = if ($envMap.ContainsKey('PIXABAY_API_KEY')) { $envMap['PIXABAY_API_KEY'] } else { '' }

$uMask = [byte[]]@(0x5A, 0x3C, 0xA5, 0xF0, 0x1F, 0x8E, 0x27, 0x4D)
$sMask = [byte[]]@(0x73, 0x19, 0x42, 0xFE, 0x8A, 0x31, 0x95, 0xC6, 0x0B, 0x5D, 0x2E, 0x64, 0x88, 0xAF, 0x13, 0xD7)
$tkMask = [byte[]]@(0x4A, 0x9B, 0x33, 0xD1, 0x7E, 0x24, 0x8C, 0x15)
$ttMask = [byte[]]@(0x62, 0xE7, 0x1A, 0x88, 0x3F, 0x5C, 0xB4, 0x09, 0x77, 0x2D, 0x91, 0x43)
$pMask = [byte[]]@(0x3B, 0x82, 0xF6, 0x14, 0x99, 0x4D, 0x7E, 0xC3, 0x2A)
$unMask = [byte[]]@(0x8A, 0x29, 0xD4, 0x55, 0x0F, 0x6C, 0xB7, 0x3E)
$pxMask = [byte[]]@(0x5C, 0x11, 0xA9, 0x6E, 0x84, 0x2F, 0xD7, 0x48, 0x93)

$encUrl = [NativeHeaderVault]::Encode($url, $uMask, 2, 6, "")
$encSec = if ($sig.Length -gt 0) { [NativeHeaderVault]::Encode($sec, $sMask, 3, 5, $sig) } else { [NativeHeaderVault]::Encode($sec, $sMask, 3, 5, ([string][char]0x41)) }
$encTk = [NativeHeaderVault]::Encode($tmdbK, $tkMask, 2, 6, "")
$encTt = [NativeHeaderVault]::Encode($tmdbT, $ttMask, 3, 5, "")
$encP = [NativeHeaderVault]::Encode($pexK, $pMask, 2, 6, "")
$encUn = [NativeHeaderVault]::Encode($unsK, $unMask, 3, 5, "")
$encPx = [NativeHeaderVault]::Encode($pixK, $pxMask, 1, 7, "")

$headerDir = Join-Path $root "app\src\main\cpp"
if (-not (Test-Path $headerDir)) {
    New-Item -ItemType Directory -Path $headerDir -Force | Out-Null
}
$headerPath = Join-Path $headerDir "generated_secrets.h"

$p = "OB" + "F_"
$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine('// Auto-generated security header. Do not commit or edit directly.')
[void]$sb.AppendLine('#ifndef GENERATED_SECRETS_H')
[void]$sb.AppendLine('#define GENERATED_SECRETS_H')
[void]$sb.AppendLine()
[void]$sb.AppendLine('#include <cstdint>')
[void]$sb.AppendLine('#include <cstddef>')
[void]$sb.AppendLine()

[void]$sb.AppendLine("static const size_t ${p}URL_LEN = $($url.Length);")
[void]$sb.AppendLine("static const size_t ${p}URL_MASK_LEN = $($uMask.Length);")
[void]$sb.AppendLine("static const uint8_t ${p}URL_BYTES[] = {$encUrl};")
[void]$sb.AppendLine("static const uint8_t ${p}URL_MASK[] = {$([NativeHeaderVault]::FormatMask($uMask))};")
[void]$sb.AppendLine()

[void]$sb.AppendLine("static const size_t ${p}SECRET_LEN = $($sec.Length);")
[void]$sb.AppendLine("static const size_t ${p}MASK_LEN = $($sMask.Length);")
[void]$sb.AppendLine("static const uint8_t ${p}SECRET_BYTES[] = {$encSec};")
[void]$sb.AppendLine("static const uint8_t ${p}XOR_MASK[] = {$([NativeHeaderVault]::FormatMask($sMask))};")
[void]$sb.AppendLine()

[void]$sb.AppendLine("static const char* const EXPECTED_APK_SHA256 = `"$sig`";")
[void]$sb.AppendLine()

[void]$sb.AppendLine("static const size_t ${p}TMDB_KEY_LEN = $($tmdbK.Length);")
[void]$sb.AppendLine("static const size_t ${p}TMDB_KEY_MASK_LEN = $($tkMask.Length);")
[void]$sb.AppendLine("static const uint8_t ${p}TMDB_KEY_BYTES[] = {$encTk};")
[void]$sb.AppendLine("static const uint8_t ${p}TMDB_KEY_MASK[] = {$([NativeHeaderVault]::FormatMask($tkMask))};")
[void]$sb.AppendLine()

[void]$sb.AppendLine("static const size_t ${p}TMDB_TOK_LEN = $($tmdbT.Length);")
[void]$sb.AppendLine("static const size_t ${p}TMDB_TOK_MASK_LEN = $($ttMask.Length);")
[void]$sb.AppendLine("static const uint8_t ${p}TMDB_TOK_BYTES[] = {$encTt};")
[void]$sb.AppendLine("static const uint8_t ${p}TMDB_TOK_MASK[] = {$([NativeHeaderVault]::FormatMask($ttMask))};")
[void]$sb.AppendLine()

[void]$sb.AppendLine("static const size_t ${p}PEXELS_LEN = $($pexK.Length);")
[void]$sb.AppendLine("static const size_t ${p}PEXELS_MASK_LEN = $($pMask.Length);")
[void]$sb.AppendLine("static const uint8_t ${p}PEXELS_BYTES[] = {$encP};")
[void]$sb.AppendLine("static const uint8_t ${p}PEXELS_MASK[] = {$([NativeHeaderVault]::FormatMask($pMask))};")
[void]$sb.AppendLine()

[void]$sb.AppendLine("static const size_t ${p}UNSPLASH_LEN = $($unsK.Length);")
[void]$sb.AppendLine("static const size_t ${p}UNSPLASH_MASK_LEN = $($unMask.Length);")
[void]$sb.AppendLine("static const uint8_t ${p}UNSPLASH_BYTES[] = {$encUn};")
[void]$sb.AppendLine("static const uint8_t ${p}UNSPLASH_MASK[] = {$([NativeHeaderVault]::FormatMask($unMask))};")
[void]$sb.AppendLine()

[void]$sb.AppendLine("static const size_t ${p}PIXABAY_LEN = $($pixK.Length);")
[void]$sb.AppendLine("static const size_t ${p}PIXABAY_MASK_LEN = $($pxMask.Length);")
[void]$sb.AppendLine("static const uint8_t ${p}PIXABAY_BYTES[] = {$encPx};")
[void]$sb.AppendLine("static const uint8_t ${p}PIXABAY_MASK[] = {$([NativeHeaderVault]::FormatMask($pxMask))};")
[void]$sb.AppendLine()

[void]$sb.AppendLine('#endif // GENERATED_SECRETS_H')

$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($headerPath, $sb.ToString(), $utf8NoBom)
Write-Host "Generated native secrets header at: $headerPath"
