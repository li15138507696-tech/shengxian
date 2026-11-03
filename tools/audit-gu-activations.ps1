param([string]$JdkPath='C:/Users/ADMIN/.jdks/temurin-21')
$ErrorActionPreference='Stop'
Push-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
try {
    $cp=((Get-Content build/offline-classpath.txt)-join ';')
    $classes=Join-Path (Get-Location) 'build/gu-activation-audit/classes'
    [void][IO.Directory]::CreateDirectory($classes)
    & (Join-Path $JdkPath 'bin/javac.exe') -proc:none -encoding UTF-8 -cp $cp -d $classes tools/AuditGuActivations.java
    if($LASTEXITCODE -ne 0){throw 'Gu audit compilation failed'}
    & (Join-Path $JdkPath 'bin/java.exe') '-Dfile.encoding=UTF-8' -cp ($classes+';'+$cp) AuditGuActivations
    if($LASTEXITCODE -ne 0){throw 'Gu audit failed'}
} finally {Pop-Location}