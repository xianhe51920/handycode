param([Parameter(Mandatory)][string]$AndroidSdk,[Parameter(Mandatory)][string]$JavaHome)
$ErrorActionPreference='Stop'
$projectSiteLibrary=Split-Path $PSScriptRoot -Parent
$vendorSiteLibrary=Join-Path $projectSiteLibrary 'third_party\androidx-webkit-1.13.0'
$workSiteLibrary=Join-Path $projectSiteLibrary 'build\site-data-library'
$androidSiteJar=Join-Path $AndroidSdk 'platforms\android-35\android.jar'
$r8SiteJar=Join-Path $AndroidSdk 'build-tools\36.0.0\lib\d8.jar'
$javaSite=Join-Path $JavaHome 'bin\java.exe'
foreach($requiredSite in @($androidSiteJar,$r8SiteJar,$javaSite)){if(-not(Test-Path -LiteralPath $requiredSite)){throw ('Missing: '+$requiredSite)}}
New-Item -ItemType Directory -Path $workSiteLibrary -Force | Out-Null
$inputSiteHashes=@{
 'webkit-1.13.0.aar'='001c836ea612e835991288a7a66877f25288e4cf5ec89729ee7ae066e3491a35'
 'core-1.1.0.aar'='76c7cfbe596fe3c09a6983bf1c89e889299c08ac9a3b52ce5182a088d056647e'
}
foreach($inputSiteName in $inputSiteHashes.Keys){
 if((Get-FileHash -LiteralPath (Join-Path $vendorSiteLibrary $inputSiteName) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $inputSiteHashes[$inputSiteName]){throw ('Input checksum mismatch: '+$inputSiteName)}
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($sourceSite in @(@('webkit-1.13.0.aar','webkit.jar'),@('core-1.1.0.aar','core-compile.jar'))){
 $zipSite=[IO.Compression.ZipFile]::OpenRead((Join-Path $vendorSiteLibrary $sourceSite[0]))
 try{[IO.Compression.ZipFileExtensions]::ExtractToFile($zipSite.GetEntry('classes.jar'),(Join-Path $workSiteLibrary $sourceSite[1]),$true)}finally{$zipSite.Dispose()}
}
$outputSiteJar=Join-Path $vendorSiteLibrary 'site-data-compat.jar'
& $javaSite -cp $r8SiteJar com.android.tools.r8.R8 --classfile --release --lib $androidSiteJar --lib (Join-Path $workSiteLibrary 'core-compile.jar') --pg-conf (Join-Path $vendorSiteLibrary 'site-data.pro') --output $outputSiteJar (Join-Path $workSiteLibrary 'webkit.jar')
if($LASTEXITCODE -ne 0){throw 'WebKit API preparation failed'}
$unexpectedSiteRefs=@(& (Join-Path $JavaHome 'bin\jdeps.exe') --ignore-missing-deps -verbose:class $outputSiteJar | Select-String 'androidx.core|org.jspecify|kotlin|LambdaMetafactory')
if($unexpectedSiteRefs.Count -gt 0){$unexpectedSiteRefs;throw 'Unexpected runtime dependency in reduced WebKit library'}
$shaSite=(Get-FileHash -LiteralPath $outputSiteJar -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText(($outputSiteJar+'.sha256'),($shaSite+'  site-data-compat.jar'+[Environment]::NewLine),[Text.UTF8Encoding]::new($false))
Write-Output ('Prepared site-data-compat.jar: '+(Get-Item -LiteralPath $outputSiteJar).Length+' bytes, SHA256 '+$shaSite)
