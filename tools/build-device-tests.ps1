param([Parameter(Mandatory)][string]$AndroidSdk,[Parameter(Mandatory)][string]$JavaHome)
$ErrorActionPreference='Stop'
$probeRoot=Split-Path $PSScriptRoot -Parent
$probeWork=Join-Path $probeRoot 'build\device-tests'
$probeClasses=Join-Path $probeWork 'classes'
$probeDex=Join-Path $probeWork 'dex'
$probeAndroid=Join-Path $AndroidSdk 'platforms\android-35\android.jar'
$probeTools=Join-Path $AndroidSdk 'build-tools\36.0.0'
$probeJava=Join-Path $JavaHome 'bin\java.exe'
$probeLibrary=Join-Path $probeRoot 'third_party\androidx-webkit-1.13.0\site-data-compat.jar'
$probeApplication=Get-ChildItem -LiteralPath (Join-Path $probeRoot 'build') -Directory -Filter 'run-*' |
 Sort-Object LastWriteTime -Descending | Where-Object {Test-Path -LiteralPath (Join-Path $_.FullName 'classes\com\fairycrane\handycode\SiteDataClearer.class')} | Select-Object -First 1
if(-not $probeApplication){throw 'Build the application first'}
New-Item -ItemType Directory -Path $probeClasses,$probeDex -Force | Out-Null
function Invoke-Probe([string]$Executable,[string[]]$Arguments){& $Executable @Arguments;if($LASTEXITCODE -ne 0){throw ('Probe build failed: '+$Executable)}}
$probeAppJar=Join-Path $probeWork 'application-compile.jar'
Invoke-Probe (Join-Path $JavaHome 'bin\jar.exe') @('cf',$probeAppJar,'-C',(Join-Path $probeApplication.FullName 'classes'),'.')
$probeSources=@(Get-ChildItem -LiteralPath (Join-Path $probeRoot 'tests\android') -Filter '*.java' -File | ForEach-Object {$_.FullName})
Invoke-Probe (Join-Path $JavaHome 'bin\javac.exe') (@('-encoding','UTF-8','--release','8','-classpath',($probeAndroid+';'+$probeAppJar+';'+$probeLibrary),'-d',$probeClasses)+$probeSources)
$probeClassFiles=@(Get-ChildItem -LiteralPath $probeClasses -Recurse -Filter '*.class' -File | ForEach-Object {$_.FullName})
Invoke-Probe $probeJava (@('-cp',(Join-Path $probeTools 'lib\d8.jar'),'com.android.tools.r8.D8','--lib',$probeAndroid,'--classpath',$probeAppJar,'--classpath',$probeLibrary,'--min-api','26','--output',$probeDex)+$probeClassFiles)
$probeBase=Join-Path $probeWork 'base.apk'
Invoke-Probe (Join-Path $probeTools 'aapt2.exe') @('link','-I',$probeAndroid,'--manifest',(Join-Path $probeRoot 'tests\android\AndroidManifest.xml'),'--min-sdk-version','26','--target-sdk-version','35','-o',$probeBase)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$probeZip=[IO.Compression.ZipFile]::Open($probeBase,[IO.Compression.ZipArchiveMode]::Update)
try{foreach($probeFile in @(Get-ChildItem -LiteralPath $probeDex -Filter '*.dex' -File)){[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($probeZip,$probeFile.FullName,$probeFile.Name)|Out-Null}}finally{$probeZip.Dispose()}
$probeAligned=Join-Path $probeWork 'aligned.apk'
$probeFinal=Join-Path $probeWork 'fixture-tests.apk'
Invoke-Probe (Join-Path $probeTools 'zipalign.exe') @('-f','4',$probeBase,$probeAligned)
Invoke-Probe $probeJava @('-jar',(Join-Path $probeTools 'lib\apksigner.jar'),'sign','--ks',(Join-Path $probeRoot '.local\debug.keystore'),'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',$probeFinal,$probeAligned)
Invoke-Probe $probeJava @('-jar',(Join-Path $probeTools 'lib\apksigner.jar'),'verify',$probeFinal)
Write-Output ('Synthetic fixture test APK: '+$probeFinal)
