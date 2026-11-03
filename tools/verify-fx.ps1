param([string]$JdkPath='C:/Users/ADMIN/.jdks/temurin-21')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $index=Join-Path $projectRoot 'build/offline-classpath.txt'
    if(-not (Test-Path -LiteralPath $index)){throw 'Run build.ps1 -Action Prepare first'}
    $paths=@(Get-Content -LiteralPath $index | Where-Object {
        $_ -match 'org[\\/]lwjgl[\\/](lwjgl|lwjgl-glfw|lwjgl-opengl)[\\/]' -and $_ -notmatch 'arm64|x86'
    })
    if($paths.Count -lt 6){throw 'LWJGL OpenGL/GLFW jars and Windows native jars are required'}
    $cp=$paths -join ';'
    $classes=Join-Path $projectRoot 'build/fx-offline/classes'
    [void][IO.Directory]::CreateDirectory($classes)
    $core=@(Get-ChildItem -LiteralPath 'src/main/java/net/guzhenren/shengxian/core' -Filter '*.java' | ForEach-Object { $_.FullName })
    & (Join-Path $JdkPath 'bin/javac.exe') -encoding UTF-8 -cp $cp -d $classes tools/VerifyFxShaders.java tools/PreviewAscensionWorld.java tools/VerifyCloudVolume.java @core
    if($LASTEXITCODE -ne 0){throw 'Offline shader probe compilation failed'}
    & (Join-Path $JdkPath 'bin/java.exe') '-Djava.awt.headless=true' -cp ($classes+';'+$cp) VerifyFxShaders
    if($LASTEXITCODE -ne 0){throw 'Offline shader verification failed'}
} finally { Pop-Location }
