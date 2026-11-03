param([string]$JdkPath='C:/Users/ADMIN/.jdks/temurin-21')
$ErrorActionPreference='Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
 $paths=@(Get-Content build/offline-classpath.txt | Where-Object { $_.Replace([char]92,[char]47) -match '/org/lwjgl/(lwjgl|lwjgl-glfw|lwjgl-opengl)/' -and $_ -notmatch 'arm64|x86' })
 if($paths.Count -lt 6){throw 'Missing OpenGL jars'}
 $cp=$paths -join ';'
 $out='build/grand-preview'
 [void][IO.Directory]::CreateDirectory($out)
 $core=@(Get-ChildItem src/main/java/net/guzhenren/shengxian/core -Filter '*.java' | ForEach-Object {$_.FullName})
 & (Join-Path $JdkPath 'bin/javac.exe') -encoding UTF-8 -cp $cp -d $out tools/VerifyFxShaders.java tools/PreviewAscensionWorld.java tools/VerifyCloudVolume.java tools/PreviewGrandRitual.java tools/VerifyGrandRitualMesh.java @core
 if($LASTEXITCODE -ne 0){throw 'Preview compilation failed'}
 & (Join-Path $JdkPath 'bin/java.exe') -cp ($out+';'+$cp) VerifyGrandRitualMesh
 if($LASTEXITCODE -ne 0){throw 'Solid mesh checks failed'}
 & (Join-Path $JdkPath 'bin/java.exe') '-Djava.awt.headless=true' -cp ($out+';'+$cp) PreviewGrandRitual
 if($LASTEXITCODE -ne 0){throw 'GPU preview failed'}
} finally { Pop-Location }
