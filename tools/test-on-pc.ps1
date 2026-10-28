param([switch]$Setup,[ValidateSet('Release','Debug')][string]$Configuration='Release')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$sdkRoot=Join-Path $projectRoot 'android-test-sdk'
$dataRoot=Join-Path $projectRoot 'android-test-data'
$avdName='HandyCode_API35'
$serial='emulator-5580'
[xml]$manifest=Get-Content -LiteralPath (Join-Path $projectRoot 'app\src\main\AndroidManifest.xml') -Raw
$version=$manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
$apk=Join-Path $projectRoot ('dist\HandyCode-'+$version+'-'+$Configuration.ToLowerInvariant()+'.apk')
$env:ANDROID_HOME=$sdkRoot
$env:ANDROID_SDK_ROOT=$sdkRoot
$env:ANDROID_USER_HOME=$dataRoot
$env:ANDROID_AVD_HOME=Join-Path $dataRoot 'avd'
function Run-Checked {
    param([string]$Executable,[string[]]$Arguments)
    & $Executable @Arguments
    if($LASTEXITCODE -ne 0){throw ('Command failed: '+$Executable)}
}
if($Setup){
    $toolsRoot=Join-Path $sdkRoot 'cmdline-tools\16.0'
    $sdkManager=Join-Path $toolsRoot 'bin\sdkmanager.bat'
    if(-not (Test-Path -LiteralPath $sdkManager)){
        $unityRoot='D:\developer\Unity'
        $sourceTools=Get-ChildItem -LiteralPath $unityRoot -Directory |
            Sort-Object Name -Descending |
            ForEach-Object {Join-Path $_.FullName 'Editor\Data\PlaybackEngines\AndroidPlayer\SDK\cmdline-tools\16.0'} |
            Where-Object {Test-Path -LiteralPath (Join-Path $_ 'bin\sdkmanager.bat')} |
            Select-Object -First 1
        if(-not $sourceTools){throw 'Android command-line tools not found. Install the official tools first.'}
        New-Item -ItemType Directory -Path (Split-Path $toolsRoot -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $sourceTools -Destination $toolsRoot -Recurse
    }
    New-Item -ItemType Directory -Path $dataRoot,$env:ANDROID_AVD_HOME -Force | Out-Null
    Write-Output 'Download official emulator and Android 15 image. Review any license prompts yourself; this script does not auto-accept terms.'
    Run-Checked $sdkManager @("--sdk_root=$sdkRoot",'emulator','platform-tools','system-images;android-35;google_apis;x86_64')
    $avdFolder=Join-Path $env:ANDROID_AVD_HOME ($avdName+'.avd')
    if(-not (Test-Path -LiteralPath (Join-Path $avdFolder 'config.ini'))){
        $avdManager=Join-Path $toolsRoot 'bin\avdmanager.bat'
        'no' | & $avdManager create avd --name $avdName --package 'system-images;android-35;google_apis;x86_64' --path $avdFolder --device pixel_7
        if($LASTEXITCODE -ne 0){throw 'Virtual device creation failed.'}
    }
}
$emulator=Join-Path $sdkRoot 'emulator\emulator.exe'
$adb=Join-Path $sdkRoot 'platform-tools\adb.exe'
foreach($required in @($emulator,$adb,$apk,(Join-Path $env:ANDROID_AVD_HOME ($avdName+'.avd\config.ini')))){
    if(-not (Test-Path -LiteralPath $required)){throw 'Test environment is incomplete. Run this script with -Setup first.'}
}
Run-Checked $emulator @('-accel-check')
Run-Checked $adb @('start-server')
$devices=& $adb devices
if($LASTEXITCODE -ne 0){throw 'Cannot query ADB devices.'}
if($devices -match $serial){
    $runningName=(& $adb -s $serial emu avd name) -join ' '
    if($LASTEXITCODE -ne 0 -or $runningName -notmatch $avdName){
        throw 'Port 5580 is occupied by another virtual device. Close that device yourself, then retry.'
    }
}else{
    Write-Output 'Starting the dedicated handyCode virtual phone.'
    Start-Process -FilePath $emulator -ArgumentList @('-avd',$avdName,'-port','5580','-gpu','auto','-no-snapshot-save') -WindowStyle Hidden | Out-Null
}
$deadline=(Get-Date).AddMinutes(3)
$booted=$false
while((Get-Date) -lt $deadline){
    $deviceState=(& $adb -s $serial get-state 2>$null) -join ''
    if($deviceState -eq 'device'){
        $bootState=(& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join ''
        if($bootState.Trim() -eq '1'){$booted=$true;break}
    }
    Start-Sleep -Seconds 2
}
if(-not $booted){throw 'Android did not finish booting within 3 minutes. Inspect the emulator window and retry.'}
Run-Checked $adb @('-s',$serial,'install','-r',$apk)
Run-Checked $adb @('-s',$serial,'shell','am','start','-W','-n','com.fairycrane.handycode/.MainActivity')
Write-Output 'App launched. Check platform switching, settings, refresh and brightness. Complete official web login yourself.'
Write-Output 'A successful launch is not proof that either platform supports identity-code display in WebView.'
