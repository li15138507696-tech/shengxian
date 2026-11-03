param([string]$JdkPath='C:/Users/ADMIN/.jdks/temurin-21')
$ErrorActionPreference='Stop'
Push-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
try {
    $compiled=(Get-ChildItem build/runs -Directory | Sort-Object LastWriteTime -Descending | Where-Object {
        Test-Path -LiteralPath (Join-Path $_.FullName 'classes/net/guzhenren/shengxian/compat/SavedRankProjection.class')
    } | Select-Object -First 1)
    if($null -eq $compiled){throw 'Run build.ps1 -Action SourceCheck or Build first'}
    $cp=(Join-Path $compiled.FullName 'classes')+';'+((Get-Content build/offline-classpath.txt)-join ';')
    $out=Join-Path (Get-Location) 'build/fx-offline/protocol'
    [void][IO.Directory]::CreateDirectory($out)
    & (Join-Path $JdkPath 'bin/javac.exe') -proc:none -encoding UTF-8 -cp $cp -d $out tools/VerifyVisualProtocol.java tools/OutcomeVerification.java tools/VerifyRankPersistence.java tools/VerifyFailureReceipt.java
    if($LASTEXITCODE -ne 0){throw 'Protocol verification compilation failed'}
    foreach($name in @('VerifyVisualProtocol','OutcomeVerification','VerifyRankPersistence','VerifyFailureReceipt')){
        & (Join-Path $JdkPath 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp ($out+';'+$cp) $name
        if($LASTEXITCODE -ne 0){throw ($name+' failed')}
    }
} finally { Pop-Location }
