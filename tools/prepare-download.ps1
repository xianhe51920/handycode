param([Parameter(Mandatory=$true)][string[]]$Changes,
    [ValidateSet('Release','Debug')][string]$Configuration='Release',[switch]$OnlyCurrent)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$siteRoot=Join-Path $projectRoot 'apk-download-site'
[xml]$manifest=Get-Content -LiteralPath (Join-Path $projectRoot 'app\src\main\AndroidManifest.xml') -Raw
$androidNamespace='http://schemas.android.com/apk/res/android'
$version=$manifest.manifest.GetAttribute('versionName',$androidNamespace)
$versionCode=[int]$manifest.manifest.GetAttribute('versionCode',$androidNamespace)
if($version -notmatch '^\d+\.\d+\.\d+$'){throw 'Unexpected app version.'}
$apkName='HandyCode-'+$version+'-'+$Configuration.ToLowerInvariant()+'.apk'
$apk=Join-Path $projectRoot $apkName
$builtApk=Join-Path $projectRoot ('dist\'+$apkName)
$hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if($hash -ne (Get-FileHash -LiteralPath $builtApk -Algorithm SHA256).Hash.ToLowerInvariant()){
    throw 'Root APK and built APK differ. Rebuild before preparing a download.'
}
if(-not (Test-Path -LiteralPath (Join-Path $siteRoot '.openai\hosting.json'))){throw 'Download Site is not registered.'}
$siteDist=Join-Path $siteRoot 'dist'
$releaseRoot=Join-Path $siteDist 'releases'
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $releaseRoot $apkName) -Force
$encoding=[Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $releaseRoot ($apkName+'.sha256')),($hash+'  '+$apkName+"`n"),$encoding)
$beijing=[TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow,'China Standard Time')
$bytes=(Get-Item -LiteralPath $apk).Length
$record=[ordered]@{version=$version;versionCode=$versionCode;file=('releases/'+$apkName);bytes=$bytes;sha256=$hash;releasedAt=$beijing.ToString('yyyy-MM-ddTHH:mm:sszzz');changes=@($Changes)}
$historyFile=Join-Path $siteDist 'releases.json'
$previous=@()
if(-not $OnlyCurrent -and (Test-Path -LiteralPath $historyFile)){
    $old=Get-Content -LiteralPath $historyFile -Raw | ConvertFrom-Json
    $previous=@($old.releases | Where-Object version -ne $version)
}
$history=[ordered]@{latest=$version;releases=@($record)+$previous}
[IO.File]::WriteAllText($historyFile,($history | ConvertTo-Json -Depth 8),$encoding)
$template=Get-Content -LiteralPath (Join-Path $siteRoot 'index.template.html') -Raw
$changeHtml=($Changes | ForEach-Object {'<li>'+[Net.WebUtility]::HtmlEncode($_)+'</li>'}) -join ''
$html=$template.Replace('@@VERSION@@',$version).Replace('@@APK_NAME@@',$apkName).Replace('@@CONFIGURATION@@',$Configuration).Replace('@@SIZE@@',([Math]::Round($bytes/1KB,1).ToString([Globalization.CultureInfo]::InvariantCulture))).Replace('@@DATE@@',$beijing.ToString('yyyy-MM-dd')).Replace('@@CHANGES@@',$changeHtml).Replace('@@SHA256@@',$hash)
[IO.File]::WriteAllText((Join-Path $siteDist 'index.html'),$html,$encoding)
$headers="/releases/*.apk`n  Content-Type: application/vnd.android.package-archive`n  Content-Disposition: attachment`n  Cache-Control: no-cache`n`n/index.html`n  Cache-Control: no-cache`n`n/releases.json`n  Cache-Control: no-cache`n"
[IO.File]::WriteAllText((Join-Path $siteDist '_headers'),$headers,$encoding)
Write-Output ('Prepared '+$apkName+' ('+$bytes+' bytes). Publishing via Sites is still required.')
