param([ValidateSet('Release','Debug')][string]$Configuration='Release')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb='D:\developer\Unity\6000.3.26f1\Editor\Data\PlaybackEngines\AndroidPlayer\SDK\platform-tools\adb.exe'
[xml]$manifest=Get-Content -LiteralPath (Join-Path $projectRoot 'app\src\main\AndroidManifest.xml') -Raw
$version=$manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
$apkName='HandyCode-'+$version+'-'+$Configuration.ToLowerInvariant()+'.apk'
$apk=Join-Path $projectRoot $apkName
if(-not (Test-Path -LiteralPath $apk)){ $apk=Join-Path $projectRoot ('dist\'+$apkName) }
foreach($required in @($adb,$apk)){if(-not (Test-Path -LiteralPath $required)){throw ('Not found: '+$required)}}
function Invoke-Adb {
    param([string[]]$AdbArguments)
    & $adb @AdbArguments
    if($LASTEXITCODE -ne 0){throw ('ADB failed: '+$AdbArguments[0])}
}
function Read-Endpoint {
    param([string]$Prompt)
    $value=(Read-Host $Prompt).Trim()
    $endpointUri=$null
    $address=$null
    if(-not [Uri]::TryCreate(('tcp://'+$value),[UriKind]::Absolute,[ref]$endpointUri) -or
       $endpointUri.Port -lt 1 -or $endpointUri.Port -gt 65535 -or
       $endpointUri.UserInfo -or $endpointUri.Query -or $endpointUri.Fragment -or
       $endpointUri.AbsolutePath -notin @('','/') -or
       -not [Net.IPAddress]::TryParse($endpointUri.DnsSafeHost,[ref]$address)){
        throw 'Enter a numeric IP address and port exactly as shown on your phone.'
    }
    return [PSCustomObject]@{Endpoint=$value;Address=$address}
}
Write-Output 'handyCode wireless debugging'
Write-Output 'Keep your phone and PC on the same Wi-Fi.'
Write-Output 'On the phone: Developer options > Wireless debugging > Pair device with pairing code.'
Write-Output 'Starting the local ADB service...'
Invoke-Adb @('start-server')
$pair=Read-Endpoint 'Pairing dialog IP:port'
Write-Output 'Enter the temporary pairing code at the ADB prompt below.'
Invoke-Adb @('pair',$pair.Endpoint)
Write-Output 'Return to the MAIN Wireless debugging page on your phone.'
Write-Output 'The connection port there is usually different from the pairing port.'
$connection=Read-Endpoint 'Main Wireless debugging page IP:port'
if(-not $pair.Address.Equals($connection.Address)){throw 'The connection address is not the phone IP used for pairing.'}
Invoke-Adb @('connect',$connection.Endpoint)
$state=(& $adb -s $connection.Endpoint get-state) -join ''
if($LASTEXITCODE -ne 0 -or $state.Trim() -ne 'device'){throw 'The paired phone is not connected and ready.'}
Write-Output ('Installing the current HandyCode '+$Configuration+' APK on the selected phone...')
Invoke-Adb @('-s',$connection.Endpoint,'install','-r',$apk)
Invoke-Adb @('-s',$connection.Endpoint,'shell','am','start','-W','-n','com.fairycrane.handycode/.MainActivity')
Write-Output 'Ready. The app has been installed and launched on your phone.'
Write-Output ('Connection endpoint for later updates: '+$connection.Endpoint)
