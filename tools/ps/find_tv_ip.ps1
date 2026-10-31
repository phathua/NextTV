# NextTV - High-speed parallel Android TV LAN Scanner (Port 5555)
# Zero external dependencies. Detects default internet gateway & scans active subnet.
param(
    [string]$Subnet,       # Optional override, e.g. "192.168.1"
    [int]$TimeoutMs = 1500,
    [switch]$SkipCache
)

$ErrorActionPreference = 'SilentlyContinue'

$toolsDir = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $toolsDir
$cacheFile = Join-Path $repoRoot ".last_tv_ip"

function Test-TcpPort([string]$TargetIp, [int]$Port = 5555, [int]$Timeout = 800) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($TargetIp, $Port, $null, $null)
        $wh = $iar.AsyncWaitHandle
        if (-not $wh.WaitOne($Timeout, $false)) {
            $client.Close()
            return $false
        }
        $client.EndConnect($iar)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

# 1. Fast path: Check previously cached TV IP
if (-not $SkipCache -and (Test-Path $cacheFile)) {
    $cachedIp = (Get-Content $cacheFile -Encoding UTF8 | Select-Object -First 1).Trim()
    if ($cachedIp -match '^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$') {
        if (Test-TcpPort -TargetIp $cachedIp -Timeout 600) {
            Write-Host "[+] Reused cached TV Box IP: $cachedIp" -ForegroundColor Green
            Write-Output "__FOUND_IP__:$cachedIp"
            exit 0
        }
    }
}

# 2. Fast path: Check ADB devices already connected via IP:5555
try {
    $adbDevices = adb devices 2>$null
    foreach ($line in $adbDevices) {
        if ($line -match '^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}):5555\s+device') {
            $connIp = $Matches[1]
            Write-Host "[+] Found already connected Android TV at: $connIp" -ForegroundColor Green
            [System.IO.File]::WriteAllText($cacheFile, $connIp)
            Write-Output "__FOUND_IP__:$connIp"
            exit 0
        }
    }
} catch {}

# 3. Determine local IPv4 subnet via Default Gateway
$base = $null
if ($Subnet) {
    $base = if ($Subnet.EndsWith('.')) { $Subnet } else { "$Subnet." }
} else {
    try {
        $defaultRoute = Get-NetRoute -DestinationPrefix '0.0.0.0/0' | Sort-Object RouteMetric | Select-Object -First 1
        if ($defaultRoute) {
            $ipObj = Get-NetIPAddress -InterfaceIndex $defaultRoute.InterfaceIndex -AddressFamily IPv4 |
                     Where-Object { $_.IPAddress -notmatch '^169\.254' -and $_.IPAddress -notmatch '^127\.' } |
                     Select-Object -First 1
            if ($ipObj) {
                $rawIp = $ipObj.IPAddress
                $base = $rawIp.Substring(0, $rawIp.LastIndexOf('.') + 1)
            }
        }
    } catch {}

    # Fallback to any valid non-virtual adapter
    if (-not $base) {
        $fallback = Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
            $_.InterfaceAlias -notmatch 'Loopback|vEthernet|WSL|Default|VMware|VirtualBox|Tailscale|ZeroTier' -and
            $_.IPAddress -notmatch '^169\.254' -and
            $_.IPAddress -notmatch '^127\.'
        } | Select-Object -First 1
        if ($fallback) {
            $rawIp = $fallback.IPAddress
            $base = $rawIp.Substring(0, $rawIp.LastIndexOf('.') + 1)
        }
    }
}

if (-not $base) {
    Write-Host "[!] Could not determine local IPv4 subnet." -ForegroundColor Red
    exit 1
}

Write-Host "[*] Scanning subnet ${base}0/24 on ADB port 5555..." -ForegroundColor Cyan

$tasks = 1..254 | ForEach-Object {
    $target = "$base$_"
    $tcp = New-Object System.Net.Sockets.TcpClient
    @{ IP = $target; Task = $tcp.ConnectAsync($target, 5555); Client = $tcp }
}

Start-Sleep -Milliseconds $TimeoutMs

$foundList = [System.Collections.Generic.List[string]]::new()
foreach ($t in $tasks) {
    if ($t.Client.Connected) {
        $foundList.Add($t.IP)
    }
    $t.Client.Dispose()
}

if ($foundList.Count -eq 1) {
    $selected = $foundList[0]
    Write-Host "[+] Found Android TV at: $selected" -ForegroundColor Green
    [System.IO.File]::WriteAllText($cacheFile, $selected)
    Write-Output "__FOUND_IP__:$selected"
} elseif ($foundList.Count -gt 1) {
    Write-Host "[+] Found $($foundList.Count) Android TV devices:" -ForegroundColor Green
    for ($i = 0; $i -lt $foundList.Count; $i++) {
        Write-Host "  [$($i+1)] $($foundList[$i])" -ForegroundColor Cyan
    }
    # Auto-pick the first one as primary output for non-interactive pipelines
    $selected = $foundList[0]
    Write-Host "[*] Auto-selecting primary device: $selected" -ForegroundColor Yellow
    [System.IO.File]::WriteAllText($cacheFile, $selected)
    Write-Output "__FOUND_IP__:$selected"
} else {
    Write-Host "[!] No active Android TV found on port 5555 in subnet ${base}0/24." -ForegroundColor Yellow
}
