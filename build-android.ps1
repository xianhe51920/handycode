param([string]$AndroidSdk=$env:ANDROID_SDK_ROOT,[string]$JavaHome=$env:JAVA_HOME,
    [ValidateSet('Release','Debug')][string]$Configuration='Release')
$ErrorActionPreference='Stop'
$projectRoot=$PSScriptRoot
if (-not $AndroidSdk) { $AndroidSdk=$env:ANDROID_HOME }
if (-not $AndroidSdk) {
    $unityBase='D:\developer\Unity'
    if (Test-Path -LiteralPath $unityBase) {
        $AndroidSdk=Get-ChildItem -LiteralPath $unityBase -Directory | Sort-Object Name -Descending |
            ForEach-Object {Join-Path $_.FullName 'Editor\Data\PlaybackEngines\AndroidPlayer\SDK'} |
            Where-Object {Test-Path -LiteralPath $_} | Select-Object -First 1
    }
}
if (-not $AndroidSdk) {throw 'Pass -AndroidSdk with an Android SDK directory.'}
if (-not $JavaHome) {$JavaHome=Split-Path (Split-Path (Get-Command javac -ErrorAction Stop).Source -Parent) -Parent}
$java=Join-Path $JavaHome 'bin\java.exe'
$javac=Join-Path $JavaHome 'bin\javac.exe'
$keytool=Join-Path $JavaHome 'bin\keytool.exe'
$androidJar=Join-Path $AndroidSdk 'platforms\android-35\android.jar'
$siteDataJar=Join-Path $projectRoot 'third_party\androidx-webkit-1.13.0\site-data-compat.jar'
$siteDataHash=((Get-Content -LiteralPath ($siteDataJar+'.sha256') -Raw).Trim() -split '\s+')[0]
if ((Get-FileHash -LiteralPath $siteDataJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $siteDataHash) {throw 'Site-data library checksum mismatch'}
$toolRoot=Join-Path $AndroidSdk 'build-tools\36.0.0'
foreach($required in @($java,$javac,$keytool,$androidJar,(Join-Path $toolRoot 'aapt2.exe'),
    (Join-Path $toolRoot 'lib\d8.jar'),(Join-Path $toolRoot 'lib\apksigner.jar'))) {
    if (-not (Test-Path -LiteralPath $required)) {throw ('Missing: '+$required)}
}
function Invoke-Checked {
    param([string]$Executable,[string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {throw ('Command failed ('+$LASTEXITCODE+'): '+$Executable)}
}
$buildRoot=Join-Path $projectRoot ('build\run-'+[Guid]::NewGuid().ToString('N'))
$classes=Join-Path $buildRoot 'classes'
$generated=Join-Path $buildRoot 'generated'
$dex=Join-Path $buildRoot 'dex'
$testClasses=Join-Path $buildRoot 'test-classes'
$dist=Join-Path $projectRoot 'dist'
$keyRoot=Join-Path $projectRoot '.local'
foreach($path in @($classes,$generated,$dex,$testClasses,$dist,$keyRoot)) {
    New-Item -ItemType Directory -Path $path -Force | Out-Null
}
$sourceRoot=Join-Path $projectRoot 'app\src\main'
$resZip=Join-Path $buildRoot 'resources.zip'
$baseApk=Join-Path $buildRoot 'base.apk'
$alignedApk=Join-Path $buildRoot 'aligned.apk'
[xml]$manifest=Get-Content -LiteralPath (Join-Path $sourceRoot 'AndroidManifest.xml') -Raw
$version=$manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
$artifactKind=$Configuration.ToLowerInvariant()
$apkName='HandyCode-'+$version+'-'+$artifactKind+'.apk'
$versionCode=[int]$manifest.manifest.GetAttribute('versionCode','http://schemas.android.com/apk/res/android')
if($Configuration -eq 'Release' -and $manifest.manifest.application.GetAttribute('debuggable','http://schemas.android.com/apk/res/android') -ne 'false') {
    throw 'Release requires android:debuggable="false" in the manifest.'
}
$finalApk=Join-Path $dist $apkName
Write-Output '1/6 Compile Android resources'
Invoke-Checked (Join-Path $toolRoot 'aapt2.exe') @('compile','--dir',(Join-Path $sourceRoot 'res'),'-o',$resZip)
Invoke-Checked (Join-Path $toolRoot 'aapt2.exe') @('link','-o',$baseApk,'-I',$androidJar,
    '--manifest',(Join-Path $sourceRoot 'AndroidManifest.xml'),'-A',(Join-Path $sourceRoot 'assets'),$resZip,
    '--java',$generated,'--min-sdk-version','26','--target-sdk-version','35')
Write-Output '2/6 Compile Java sources'
$sourceFiles=@(Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'java') -Recurse -Filter '*.java' -File | ForEach-Object {$_.FullName})
$sourceFiles+=@(Get-ChildItem -LiteralPath $generated -Recurse -Filter '*.java' -File | ForEach-Object {$_.FullName})
$sourceList=Join-Path $buildRoot 'sources.txt'
$sourceLines=($sourceFiles | ForEach-Object {'"'+$_.Replace('\','/')+'"'}) -join [Environment]::NewLine
[IO.File]::WriteAllText($sourceList,$sourceLines,[Text.UTF8Encoding]::new($false))
Invoke-Checked $javac @('-encoding','UTF-8','--release','8','-classpath',($androidJar+[IO.Path]::PathSeparator+$siteDataJar),'-d',$classes,('@'+$sourceList))
Write-Output '3/6 Test URL navigation rules'
Invoke-Checked $javac @('-encoding','UTF-8','--release','8','-d',$testClasses,
    (Join-Path $sourceRoot 'java\com\fairycrane\handycode\UrlPolicy.java'),
    (Join-Path $sourceRoot 'java\com\fairycrane\handycode\PddBundleAdapter.java'),
    (Join-Path $sourceRoot 'java\com\fairycrane\handycode\SwitchMotion.java'),
    (Join-Path $sourceRoot 'java\com\fairycrane\handycode\LogoutSites.java'),
    (Join-Path $sourceRoot 'java\com\fairycrane\handycode\SiteClearQueue.java'),
    (Join-Path $projectRoot 'tests\SiteClearQueueTest.java'),
    (Join-Path $projectRoot 'tests\UrlPolicyTest.java'),
    (Join-Path $projectRoot 'tests\PddUrlPolicyTest.java'),
    (Join-Path $projectRoot 'tests\PddBundleAdapterTest.java'),
    (Join-Path $projectRoot 'tests\SwitchMotionTest.java'))
Invoke-Checked $java @('-cp',$testClasses,'UrlPolicyTest')
Invoke-Checked $java @('-cp',$testClasses,'PddUrlPolicyTest')
Invoke-Checked $java @('-cp',$testClasses,'PddBundleAdapterTest')
Invoke-Checked $java @('-cp',$testClasses,'SwitchMotionTest')
Invoke-Checked $java @('-cp',$testClasses,'SiteClearQueueTest')
Invoke-Checked 'node' @((Join-Path $projectRoot 'tests\pdd-same-origin.test.mjs'))
Invoke-Checked 'node' @((Join-Path $projectRoot 'tests\pdd-section-refresh.test.mjs'))
Invoke-Checked 'node' @((Join-Path $projectRoot 'tests\pdd-login-client.test.mjs'))
Invoke-Checked 'node' @((Join-Path $projectRoot 'tests\pdd-layout.test.mjs'))
Invoke-Checked 'node' @('--check',(Join-Path $sourceRoot 'assets\login-ready.js'))
Invoke-Checked 'node' @((Join-Path $projectRoot 'tests\login-ready.test.mjs'))
Invoke-Checked 'node' @((Join-Path $projectRoot 'tests\official-identity.test.mjs'))
Invoke-Checked 'node' @((Join-Path $projectRoot 'tests\refresh-controller.test.mjs'))
Invoke-Checked 'node' @((Join-Path $projectRoot 'tests\taobao-transition.test.mjs'))
Invoke-Checked 'node' @((Join-Path $projectRoot 'tests\taobao-login-chrome.test.mjs'))
Write-Output '4/6 Produce DEX'
$classFiles=@(Get-ChildItem -LiteralPath $classes -Recurse -Filter '*.class' -File | ForEach-Object {$_.FullName})
$d8Args=@('-cp',(Join-Path $toolRoot 'lib\d8.jar'),'com.android.tools.r8.D8',
    '--lib',$androidJar,'--min-api','26','--output',$dex,$siteDataJar)+$classFiles
Invoke-Checked $java $d8Args
foreach($compressionAssembly in @('System.IO.Compression','System.IO.Compression.FileSystem')) {
    try {Add-Type -AssemblyName $compressionAssembly -ErrorAction Stop} catch {}
}
foreach($zipTypeName in @('System.IO.Compression.ZipArchiveMode','System.IO.Compression.ZipFile')) {
    if(-not ($zipTypeName -as [type])) {throw ('Zip type unavailable: '+$zipTypeName)}
}
$archive=[IO.Compression.ZipFile]::Open($baseApk,[IO.Compression.ZipArchiveMode]::Update)
try {
    foreach($file in @(Get-ChildItem -LiteralPath $dex -Filter '*.dex' -File)) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$file.Name) | Out-Null
    }
} finally {$archive.Dispose()}
Write-Output ('5/6 Align and sign '+$Configuration+' APK')
Invoke-Checked (Join-Path $toolRoot 'zipalign.exe') @('-f','4',$baseApk,$alignedApk)
$keystore=Join-Path $keyRoot 'debug.keystore'
if (-not (Test-Path -LiteralPath $keystore)) {
    if($Configuration -eq 'Release'){throw 'Release signing key missing: preserve the existing .local/debug.keystore for compatible updates.'}
    Invoke-Checked $keytool @('-genkeypair','-keystore',$keystore,'-storepass','android',
        '-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048',
        '-validity','10000','-dname','CN=Android Debug,O=handyCode,C=CN','-noprompt')
}
Invoke-Checked $java @('-jar',(Join-Path $toolRoot 'lib\apksigner.jar'),'sign','--ks',$keystore,
    '--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',$finalApk,$alignedApk)
Write-Output '6/6 Verify signature and package'
Invoke-Checked $java @('-jar',(Join-Path $toolRoot 'lib\apksigner.jar'),'verify','--verbose',$finalApk)
$badging=& (Join-Path $toolRoot 'aapt.exe') dump badging $finalApk
if ($LASTEXITCODE -ne 0 -or -not ($badging -match "package: name='com.fairycrane.handycode'")) {
    throw 'APK package check failed.'
}
$badging | Select-Object -First 6 | Write-Output
$hash=(Get-FileHash -LiteralPath $finalApk -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText(($finalApk+'.sha256'),($hash+'  '+(Split-Path $finalApk -Leaf)+[Environment]::NewLine),[Text.UTF8Encoding]::new($false))
Write-Output ('APK: '+$finalApk)
Copy-Item -LiteralPath $finalApk -Destination (Join-Path $projectRoot $apkName) -Force
Copy-Item -LiteralPath ($finalApk+'.sha256') -Destination (Join-Path $projectRoot ($apkName+'.sha256')) -Force
if($Configuration -eq 'Release') {
    $signerInfo=& $java -jar (Join-Path $toolRoot 'lib\apksigner.jar') verify --print-certs $finalApk
    if($LASTEXITCODE -ne 0){throw 'Release certificate verification failed'}
    $signerMatch=[regex]::Match(($signerInfo -join "`n"),'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)')
    if(-not $signerMatch.Success){throw 'Release certificate digest missing'}
    if($badging -match '^application-debuggable'){throw 'Release APK must not be debuggable'}
    $releaseRecord=[ordered]@{name='HandyCode';version=$version;versionCode=$versionCode;configuration='Release';tag=('v'+$version);package='com.fairycrane.handycode';file=$apkName;sha256=$hash;bytes=(Get-Item -LiteralPath $finalApk).Length;minSdk=26;targetSdk=35;signerCertificateSha256=$signerMatch.Groups[1].Value.ToLowerInvariant()}
    [IO.File]::WriteAllText((Join-Path $projectRoot 'release.json'),($releaseRecord|ConvertTo-Json)+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
}
Write-Output ('SHA256: '+$hash)
