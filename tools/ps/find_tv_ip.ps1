# NextTV - High-speed parallel Android TV LAN Scanner (Port 5555)
param()

$ErrorActionPreference = 'SilentlyContinue'

$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.InterfaceAlias -notmatch 'Loopback|vEthernet|WSL|Default' -and
    $_.IPAddress -notmatch '^169\.254' -and
    $_.IPAddress -notmatch '^127\.'
} | Select-Object -First 1).IPAddress

if (-not $ip) {
    Write-Host "[!] Could not determine local IPv4 subnet." -ForegroundColor Red
    exit 1
}

$base = $ip.Substring(0, $ip.LastIndexOf('.') + 1)
Write-Host "[*] Scanning subnet ${base}0/24 on port 5555..." -ForegroundColor Cyan

$tasks = 1..254 | ForEach-Object {
    $target = "$base$_"
    $tcp = New-Object System.Net.Sockets.TcpClient
    @{ IP = $target; Task = $tcp.ConnectAsync($target, 5555); Client = $tcp }
}

Start-Sleep -Milliseconds 1400

$found = $null
foreach ($t in $tasks) {
    if ($t.Client.Connected) {
        $found = $t.IP
        break
    }
}

foreach ($t in $tasks) {
    $t.Client.Dispose()
}

if ($found) {
    Write-Host "[+] Found Android TV at: $found" -ForegroundColor Green
    Write-Output $found
} else {
    Write-Host "[!] No active Android TV found on port 5555." -ForegroundColor Yellow
}
