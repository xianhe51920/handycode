#Requires -Version 5.1
<#
.SYNOPSIS
    HandyCode iOS 自动续签服务 — 在 Windows 上保持 AltServer 24/7 运行

.DESCRIPTION
    AltServer 在同一局域网下，每7天自动帮 iPhone 上的 HandyCode 续签。
    本脚本可：
      1. 下载安装 AltServer（如果尚未安装）
      2. 将 AltServer 注册为 Windows 开机启动任务
      3. 检测 AltServer 崩溃后自动重启

.USAGE
    以管理员身份运行 PowerShell，执行：
    .\setup-altserver-service.ps1 [-Install] [-Uninstall] [-Status] [-Restart]

.NOTES
    需求：Windows 10/11，iTunes 或 Apple Devices 应用（Microsoft Store 版）
    手机端：iPhone 和 PC 在同一 Wi-Fi 下，AltStore 已通过 AltServer 安装到 iPhone
#>

param(
    [switch]$Install,    # 安装并注册开机自启
    [switch]$Uninstall,  # 卸载开机自启任务
    [switch]$Status,     # 查看状态
    [switch]$Restart,    # 重启 AltServer
    [switch]$WatchOnly   # 仅守护（不注册），供 Task Scheduler 调用
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── 配置 ─────────────────────────────────────────────────────────────────────
$TASK_NAME      = "HandyCode_AltServer_Keeper"
$ALTSERVER_URL  = "https://cdn.altstore.io/file/altstore/altserver/altinstaller.zip"
$ALTSERVER_DIR  = "$env:LOCALAPPDATA\AltServer"
$ALTSERVER_EXE  = "$ALTSERVER_DIR\AltServer.exe"
$LOG_DIR        = "$env:LOCALAPPDATA\HandyCode\logs"
$LOG_FILE       = "$LOG_DIR\altserver-keeper.log"
$CHECK_INTERVAL = 60   # 每60秒检查一次 AltServer 是否在运行

# ── 工具函数 ─────────────────────────────────────────────────────────────────
function Write-Log {
    param([string]$Msg, [string]$Level = "INFO")
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $line = "[$timestamp] [$Level] $Msg"
    Write-Host $line
    if (-not (Test-Path $LOG_DIR)) { New-Item -ItemType Directory -Path $LOG_DIR -Force | Out-Null }
    Add-Content -Path $LOG_FILE -Value $line -Encoding UTF8
}

function Get-AltServerProcess {
    return Get-Process -Name "AltServer" -ErrorAction SilentlyContinue
}

function Start-AltServer {
    if (-not (Test-Path $ALTSERVER_EXE)) {
        Write-Log "AltServer.exe 不存在，请先运行 -Install" "ERROR"
        return $false
    }
    Write-Log "正在启动 AltServer..."
    Start-Process -FilePath $ALTSERVER_EXE -WindowStyle Hidden
    Start-Sleep -Seconds 3
    $proc = Get-AltServerProcess
    if ($proc) {
        Write-Log "AltServer 已启动 (PID: $($proc.Id))"
        return $true
    } else {
        Write-Log "AltServer 启动失败" "ERROR"
        return $false
    }
}

function Stop-AltServer {
    $proc = Get-AltServerProcess
    if ($proc) {
        Write-Log "正在停止 AltServer (PID: $($proc.Id))..."
        $proc | Stop-Process -Force
        Start-Sleep -Seconds 2
        Write-Log "AltServer 已停止"
    } else {
        Write-Log "AltServer 未运行"
    }
}

# ── 安装 AltServer ───────────────────────────────────────────────────────────
function Install-AltServer {
    Write-Log "=== 安装 AltServer ==="

    # 检查是否已安装
    if (Test-Path $ALTSERVER_EXE) {
        Write-Log "AltServer 已安装于 $ALTSERVER_EXE"
    } else {
        Write-Log "下载 AltServer 安装包..."
        $zipPath = "$env:TEMP\altinstaller.zip"
        try {
            [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $ALTSERVER_URL -OutFile $zipPath -UseBasicParsing
            Write-Log "下载完成，解压中..."
            if (-not (Test-Path $ALTSERVER_DIR)) {
                New-Item -ItemType Directory -Path $ALTSERVER_DIR -Force | Out-Null
            }
            Expand-Archive -Path $zipPath -DestinationPath $ALTSERVER_DIR -Force
            Remove-Item $zipPath -Force
        } catch {
            Write-Log "下载失败: $_" "ERROR"
            Write-Log "请手动从 https://altstore.io 下载 AltServer for Windows" "ERROR"
            Write-Log "解压后将 AltServer.exe 放到: $ALTSERVER_EXE" "ERROR"
            return
        }
    }

    # 检查 iTunes / Apple Devices
    $iTunesInstalled = (Get-Command "iTunes.exe" -ErrorAction SilentlyContinue) -or
                       (Get-AppxPackage -Name "AppleInc.iTunes" -ErrorAction SilentlyContinue) -or
                       (Get-AppxPackage -Name "AppleInc.AppleDevices" -ErrorAction SilentlyContinue)
    if (-not $iTunesInstalled) {
        Write-Log "⚠️  未检测到 iTunes 或 Apple Devices！" "WARN"
        Write-Log "   AltServer 需要 iTunes 或 Apple Devices 才能工作" "WARN"
        Write-Log "   请从 Microsoft Store 安装 Apple Devices（推荐）" "WARN"
        Write-Log "   Store 链接: ms-windows-store://pdp/?productid=9NP83LWLPZ9N" "WARN"
    } else {
        Write-Log "✓ iTunes / Apple Devices 已安装"
    }

    # 注册开机启动 Task
    Register-StartupTask

    # 立即启动
    Start-AltServer
    Write-Log "=== 安装完成 ==="
    Write-Log "iPhone 和 PC 保持同一局域网，AltServer 会每7天自动为 HandyCode 续签"
}

# ── 注册 Task Scheduler 任务 ─────────────────────────────────────────────────
function Register-StartupTask {
    Write-Log "注册开机自启任务..."

    # 守护脚本路径（本脚本自身）
    $scriptPath = $MyInvocation.ScriptName
    if (-not $scriptPath -or -not (Test-Path $scriptPath)) {
        $scriptPath = $PSCommandPath
    }

    $action = New-ScheduledTaskAction `
        -Execute "powershell.exe" `
        -Argument "-NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$scriptPath`" -WatchOnly"

    # 登录时触发 + 每小时检查一次（双保险）
    $triggerLogon  = New-ScheduledTaskTrigger -AtLogOn
    $triggerRepeat = New-ScheduledTaskTrigger -RepetitionInterval (New-TimeSpan -Hours 1) -Once -At (Get-Date)

    $settings = New-ScheduledTaskSettingsSet `
        -ExecutionTimeLimit (New-TimeSpan -Hours 0) `
        -RestartCount 10 `
        -RestartInterval (New-TimeSpan -Minutes 1) `
        -StartWhenAvailable `
        -RunOnlyIfNetworkAvailable

    $principal = New-ScheduledTaskPrincipal `
        -UserId "$env:USERDOMAIN\$env:USERNAME" `
        -LogonType Interactive `
        -RunLevel Limited

    $task = New-ScheduledTask `
        -Action $action `
        -Trigger $triggerLogon `
        -Settings $settings `
        -Principal $principal `
        -Description "保持 AltServer 运行，每7天自动为 iPhone 上的 HandyCode 续签"

    # 注册（如已存在则覆盖）
    Register-ScheduledTask -TaskName $TASK_NAME -InputObject $task -Force | Out-Null
    Write-Log "✓ 任务已注册: $TASK_NAME"
    Write-Log "  查看: 任务计划程序 → 任务计划程序库 → $TASK_NAME"
}

# ── 卸载 ─────────────────────────────────────────────────────────────────────
function Uninstall-StartupTask {
    Stop-AltServer
    if (Get-ScheduledTask -TaskName $TASK_NAME -ErrorAction SilentlyContinue) {
        Unregister-ScheduledTask -TaskName $TASK_NAME -Confirm:$false
        Write-Log "✓ 已卸载任务: $TASK_NAME"
    } else {
        Write-Log "任务不存在: $TASK_NAME"
    }
}

# ── 状态 ─────────────────────────────────────────────────────────────────────
function Show-Status {
    Write-Host ""
    Write-Host "=== HandyCode iOS 自动续签状态 ===" -ForegroundColor Cyan
    Write-Host ""

    # AltServer 进程
    $proc = Get-AltServerProcess
    if ($proc) {
        Write-Host "  AltServer:  " -NoNewline
        Write-Host "✓ 运行中 (PID: $($proc.Id))" -ForegroundColor Green
    } else {
        Write-Host "  AltServer:  " -NoNewline
        Write-Host "✗ 未运行" -ForegroundColor Red
    }

    # Task Scheduler
    $task = Get-ScheduledTask -TaskName $TASK_NAME -ErrorAction SilentlyContinue
    if ($task) {
        Write-Host "  开机自启:   " -NoNewline
        $state = $task.State
        $color = if ($state -eq "Ready" -or $state -eq "Running") { "Green" } else { "Yellow" }
        Write-Host "✓ 已注册 ($state)" -ForegroundColor $color
    } else {
        Write-Host "  开机自启:   " -NoNewline
        Write-Host "✗ 未注册（运行 -Install 安装）" -ForegroundColor Red
    }

    # AltServer 路径
    Write-Host "  安装路径:   $ALTSERVER_EXE"
    Write-Host "  日志文件:   $LOG_FILE"
    Write-Host ""
    Write-Host "提示: 续签时 iPhone 和 PC 需在同一 Wi-Fi 下" -ForegroundColor Yellow
    Write-Host ""
}

# ── 守护模式（Task Scheduler 调用）─────────────────────────────────────────
function Start-WatchMode {
    Write-Log "=== AltServer Keeper 守护模式启动 ==="
    Write-Log "检查间隔: ${CHECK_INTERVAL}秒"

    while ($true) {
        $proc = Get-AltServerProcess
        if (-not $proc) {
            Write-Log "AltServer 未运行，尝试重启..." "WARN"
            $ok = Start-AltServer
            if (-not $ok) {
                Write-Log "重启失败，${CHECK_INTERVAL}秒后重试" "ERROR"
            }
        }
        Start-Sleep -Seconds $CHECK_INTERVAL
    }
}

# ── 主入口 ───────────────────────────────────────────────────────────────────
if (-not (Test-Path $LOG_DIR)) {
    New-Item -ItemType Directory -Path $LOG_DIR -Force | Out-Null
}

if ($Install) {
    Install-AltServer
} elseif ($Uninstall) {
    Uninstall-StartupTask
} elseif ($Status) {
    Show-Status
} elseif ($Restart) {
    Stop-AltServer
    Start-Sleep -Seconds 2
    Start-AltServer
} elseif ($WatchOnly) {
    Start-WatchMode
} else {
    Write-Host @"
HandyCode iOS 自动续签服务

用法:
  .\setup-altserver-service.ps1 -Install    # 安装 AltServer + 注册开机自启（推荐首次运行）
  .\setup-altserver-service.ps1 -Status     # 查看运行状态
  .\setup-altserver-service.ps1 -Restart    # 重启 AltServer
  .\setup-altserver-service.ps1 -Uninstall  # 卸载开机自启任务

工作原理:
  1. AltServer 在后台持续运行（开机自启）
  2. iPhone 和 PC 在同一局域网时，AltServer 自动帮 HandyCode 续签
  3. GitHub Actions 每6天发布新签名 IPA（双重保障）
"@
}
