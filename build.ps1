[CmdletBinding()]
param(
    [ValidateSet('Prepare','Test','Build','Compile','SourceCheck','NbtCheck','Verify','Clean')][string]$Action='Build',
    [string]$JdkPath='C:\Users\ADMIN\.jdks\temurin-21',
    [string]$MinecraftDir='D:\game\mcmod\1\.minecraft',
    [string]$UpstreamShell='',
    # Default follows the newest released artifact; bump this line on every release.
    # The old fixed default (0.3.18) made a forgotten -ModVersion collide with an existing jar.
    # 0.3.27 was itself stale until 0.3.92, which is the same footgun one release later.
    [string]$ModVersion='0.3.94'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if ($ModVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'ModVersion must be semantic x.y.z' }
$projectRoot=$PSScriptRoot
if([string]::IsNullOrWhiteSpace($UpstreamShell)){
    # ASCII-only default: Windows PowerShell 5.1 would misread a UTF-8 Chinese literal
    # when this file has no BOM. The escapes spell the four Han characters in the path.
    $UpstreamShell='D:\cxdownload\guzhenrn'+(-join ([char[]](0x86CA,0x771F,0x4EBA,0x6A21,0x7EC4,0x672C,0x4F53)))+'10.20.jar'
}
$utf8=[System.Text.UTF8Encoding]::new($false)
$buildRoot=Join-Path $projectRoot 'build'
[void][System.IO.Directory]::CreateDirectory($buildRoot)
$javac=Join-Path $JdkPath 'bin\javac.exe'
$java=Join-Path $JdkPath 'bin\java.exe'
$jar=Join-Path $JdkPath 'bin\jar.exe'
foreach($executable in @($javac,$java,$jar)){if(-not [System.IO.File]::Exists($executable)){throw "Missing JDK 21 tool: $executable"}}
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Write-Utf8([string]$path,[string]$value){[System.IO.File]::WriteAllText($path,$value,$utf8)}
function Quote-Javac([string]$value){'"'+$value.Replace('\','/').Replace('"','\"')+'"'}
function Invoke-Javac([string[]]$sources,[string]$output,[string]$classpath){
    [void][System.IO.Directory]::CreateDirectory($output)
    $arguments=@('-encoding','UTF-8','--release','21','-proc:none','-nowarn','-d',(Quote-Javac $output))
    if($classpath){$arguments+=@('-classpath',(Quote-Javac $classpath))}
    $arguments+=@($sources | ForEach-Object {Quote-Javac $_})
    $argumentFile=Join-Path $output 'javac.args'
    Write-Utf8 $argumentFile ($arguments -join "`n")
    # Windows PowerShell 5.1 turns native stderr into a terminating error record under
    # $ErrorActionPreference='Stop', and 2>$null does not suppress it. javac writes deprecation
    # notes to stderr even with -nowarn, which silently aborted whole builds. Judge success only
    # by the exit code, and keep the notes visible in verbose output.
    $previousPreference=$ErrorActionPreference
    $ErrorActionPreference='Continue'
    try{
        & $javac ('@'+$argumentFile)
    } finally {
        $ErrorActionPreference=$previousPreference
    }
    if($LASTEXITCODE -ne 0){throw "javac failed ($LASTEXITCODE)"}
}

<#
 Per-invocation staging under build\runs is a build byproduct: it holds compiled classes, test
 scratch and the jar candidate that the installed immutable artifact already supersedes. Nothing
 there is an input, so a successful run purges it. Before this existed the directory had grown to
 170 orphaned runs (~580 MB). A FAILED run deliberately keeps its staging for diagnosis.
#>
function Clear-RunStaging(){
    $runsRoot=Join-Path $buildRoot 'runs'
    if(-not [System.IO.Directory]::Exists($runsRoot)){return}
    $removed=0
    foreach($dir in @(Get-ChildItem -LiteralPath $runsRoot -Directory -Force -ErrorAction SilentlyContinue)){
        try{Remove-Item -LiteralPath $dir.FullName -Recurse -Force; $removed++}
        catch{Write-Warning "Could not remove staging directory $($dir.FullName): $($_.Exception.Message)"}
    }
    if($removed -gt 0){Write-Host "Cleaned $removed build staging director$(if($removed -eq 1){'y'}else{'ies'}) under build\runs."}
}

<#
 Fixed-name directories that are pure re-derivable copies of the pinned upstream jar in .cache.
 They are audit byproducts, not build inputs: audit scripts regenerate them from .cache on demand.
#>
function Clear-DerivedUpstream(){
    foreach($name in @('upstream-extract','upstream-classes')){
        $path=Join-Path $buildRoot $name
        if([System.IO.Directory]::Exists($path)){
            try{
                Remove-Item -LiteralPath $path -Recurse -Force
                Write-Host "Removed derived upstream copy: build\$name"
            } catch {
                Write-Warning "Could not remove build\${name}: $($_.Exception.Message)"
            }
        }
    }
}

<#
 Released jars are NOT rebuildable — the source is not versioned — so the policy is a rolling
 window instead of "keep everything" (which had reached 92 files / 360 MB) or "delete everything"
 (which would throw away the only copy of every past release). Only shengxian-<x.y.z>.jar and its
 .sha256 sidecar are considered; the embedded Veil jar and metadata.json in build\libs are inputs.
#>
function Prune-Artifacts([int]$KeepArtifacts=3){
    $libsRoot=Join-Path $buildRoot 'libs'
    if(-not [System.IO.Directory]::Exists($libsRoot)){return}
    $releases=@(Get-ChildItem -LiteralPath $libsRoot -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^shengxian-\d+\.\d+\.\d+\.jar$' })
    if($releases.Count -le $KeepArtifacts){return}
    $keep=@($releases | Sort-Object { [version]($_.Name -replace '^shengxian-','' -replace '\.jar$','') } -Descending |
        Select-Object -First $KeepArtifacts | ForEach-Object {$_.FullName})
    $removed=0
    foreach($release in $releases){
        if($keep -contains $release.FullName){continue}
        try{
            Remove-Item -LiteralPath $release.FullName -Force
            $sidecar=$release.FullName+'.sha256'
            if([System.IO.File]::Exists($sidecar)){Remove-Item -LiteralPath $sidecar -Force}
            $removed++
        } catch {
            Write-Warning "Could not prune $($release.Name): $($_.Exception.Message)"
        }
    }
    if($removed -gt 0){Write-Host "Pruned $removed superseded release artifact(s); kept the newest $KeepArtifacts."}
}

function Test-Core([string]$workRoot){
    $coreFiles=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main\java\net\guzhenren\shengxian\core') -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})
    $testFiles=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\test\java') -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})
    $testOutput=Join-Path $workRoot 'tests'
    $testTemp=Join-Path $workRoot 'test-tmp'
    [void][System.IO.Directory]::CreateDirectory($testTemp)
    Invoke-Javac ($coreFiles+$testFiles) $testOutput ''
    & $java '-Dfile.encoding=UTF-8' ('-Djava.io.tmpdir='+$testTemp) '-cp' $testOutput 'net.guzhenren.shengxian.FrameworkTest' 2>$null
    if($LASTEXITCODE -ne 0){throw "Framework tests failed ($LASTEXITCODE)"}
}
function Test-PlayerProjection([string]$workRoot,[string]$compiled,[string]$classpath){
    $output=Join-Path $workRoot 'player-projection-tests'
    [void][System.IO.Directory]::CreateDirectory((Join-Path $workRoot 'test-tmp'))
    $sources=@((Join-Path $projectRoot 'tools/VerifyRankPersistence.java'),
        (Join-Path $projectRoot 'tools/VerifyFailureReceipt.java'),
        (Join-Path $projectRoot 'tools/VerifyTribulationImpactReceipt.java'),
        (Join-Path $projectRoot 'tools/VerifyGuBacklashReceipt.java'),
        (Join-Path $projectRoot 'tools/VerifyShatterEssenceReceipt.java'),
        (Join-Path $projectRoot 'tools/VerifyXianGuUseReceipt.java'))
    $runtime=$compiled+';'+$classpath
    Invoke-Javac $sources $output $runtime
    foreach($verifier in @('VerifyRankPersistence','VerifyFailureReceipt','VerifyTribulationImpactReceipt','VerifyGuBacklashReceipt','VerifyShatterEssenceReceipt','VerifyXianGuUseReceipt')){
        & $java '-Dfile.encoding=UTF-8' ('-Djava.io.tmpdir='+(Join-Path $workRoot 'test-tmp')) '-cp' ($output+';'+$runtime) $verifier
        if($LASTEXITCODE -ne 0){throw "$verifier failed"}
    }
}
function Test-GuActivationHooks([string]$workRoot,[string]$compiled,[string]$classpath){
    $output=Join-Path $workRoot 'gu-activation-hook-tests'
    $source=Join-Path $projectRoot 'tools/VerifyGuActivationHooks.java'
    $runtime=$compiled+';'+$classpath
    $upstream=Join-Path $projectRoot '.cache\guzhenren-0.1-neoforge-1.21.1.jar'
    $config=Join-Path $projectRoot 'src\main\resources\shengxian.common.mixins.json'
    $metadata=Join-Path $projectRoot 'src\main\resources\META-INF\neoforge.mods.toml'
    foreach($required in @($source,$upstream,$config,$metadata)){
        if(-not [System.IO.File]::Exists($required)){throw "Missing Gu activation hook verification input: $required"}
    }
    Invoke-Javac @($source) $output $runtime
    & $java '-Dfile.encoding=UTF-8' '-cp' ($output+';'+$runtime) 'VerifyGuActivationHooks' $upstream $config $metadata $compiled
    if($LASTEXITCODE -ne 0){throw 'Exact Gu activation hook verification failed'}
}
function Test-RepresentativeGu([string]$workRoot,[string]$compiled,[string]$classpath){
    $output=Join-Path $workRoot 'representative-gu-tests'
    $runtime=$compiled+';'+$classpath
    Invoke-Javac @((Join-Path $projectRoot 'tools/VerifyRepresentativeGu.java')) $output $runtime
    $minecraftJar=($classpath -split ';' | Where-Object {$_ -match 'neoforge-[0-9.]+-client.jar$'} | Select-Object -First 1)
    & $java '-Dfile.encoding=UTF-8' '-cp' ($output+';'+$runtime) 'VerifyRepresentativeGu' $minecraftJar $compiled (Join-Path $projectRoot 'src/main/resources')
    if($LASTEXITCODE -ne 0){throw 'Representative Gu hook/resource verification failed'}
}
function Prepare-Classpath {
    $expectedOuter='3733c20ffbd16d9068bb8188d45b24a0d61b279d7cb6fdc8ea341938a7f4cf04'
    $expectedInner='b6d314d3810b8bc5fcfdbc4f588db8721f0efdfc14d27a0a06d88143a01fe2bc'
    if((Get-FileHash -LiteralPath $UpstreamShell -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedOuter){throw 'Unsupported upstream shell fingerprint'}
    $cacheRoot=Join-Path $projectRoot '.cache'
    [void][System.IO.Directory]::CreateDirectory($cacheRoot)
    $upstream=Join-Path $cacheRoot 'guzhenren-0.1-neoforge-1.21.1.jar'
    if(-not [System.IO.File]::Exists($upstream)){
        $archive=[System.IO.Compression.ZipFile]::OpenRead($UpstreamShell)
        try {
            $entry=$archive.GetEntry('META-INF/jarjar/guzhenren-0.1-neoforge-1.21.1.jar')
            if($null -eq $entry){throw 'Upstream nested jar missing'}
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$upstream)
        } finally {$archive.Dispose()}
    }
    if((Get-FileHash -LiteralPath $upstream -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedInner){throw 'Unsupported upstream inner fingerprint'}
    # Launcher profiles are directly inside each version directory. Never recurse
    # through instance saves, resource packs, or configuration trees.
    $profileFiles=@(Get-ChildItem -LiteralPath (Join-Path $MinecraftDir 'versions') -Directory | ForEach-Object {
        Get-ChildItem -LiteralPath $_.FullName -Filter '*.json' -File
    })
    $profiles=@($profileFiles | ForEach-Object {
        try {
            $profile=Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
            if($profile.PSObject.Properties['arguments'] -and $profile.arguments.PSObject.Properties['game']){
                $gameArgs=@($profile.arguments.game | Where-Object {$_ -is [string]})
                $mcIndex=[Array]::IndexOf($gameArgs,'--fml.mcVersion')
                if($mcIndex -ge 0 -and $gameArgs[$mcIndex+1] -eq '1.21.1' -and $gameArgs -contains '--fml.neoForgeVersion'){
                    [pscustomobject]@{Profile=$profile;Args=$gameArgs;Count=@($profile.libraries).Count;Source=$_.FullName}
                }
            }
        } catch {Write-Verbose "Ignored profile $($_.Exception.Message)"}
    } | Sort-Object Count -Descending)
    if($profiles.Count -eq 0){throw 'No local NeoForge 1.21.1 profile found'}
    $selected=$profiles[0]
    $neo=$selected.Args[[Array]::IndexOf($selected.Args,'--fml.neoForgeVersion')+1]
    $neoform=$selected.Args[[Array]::IndexOf($selected.Args,'--fml.neoFormVersion')+1]
    $libraries=Join-Path $MinecraftDir 'libraries'
    $classpath=[System.Collections.Generic.List[string]]::new()
    foreach($relative in @("net/neoforged/neoforge/$neo/neoforge-$neo-client.jar","net/minecraft/client/1.21.1-$neoform/client-1.21.1-$neoform-srg.jar","net/neoforged/neoforge/$neo/neoforge-$neo-universal.jar")){
        $dependency=Join-Path $libraries $relative
        if(-not [System.IO.File]::Exists($dependency)){throw "Missing core library: $dependency"}
        $classpath.Add($dependency)
    }
    foreach($library in $selected.Profile.libraries){
        if($library.PSObject.Properties['downloads'] -and $library.downloads.PSObject.Properties['artifact'] -and $library.downloads.artifact.PSObject.Properties['path']){
            $dependency=Join-Path $libraries $library.downloads.artifact.path
            if([System.IO.File]::Exists($dependency)){$classpath.Add($dependency)}
        }
    }
    $classpath.Add($upstream)
    # Optional compile-time-only dependencies (never required at runtime): the Photon VFX
    # workshop is reached through reflection, so these jars only need to exist while compiling.
    $optionalDeps=Join-Path $buildRoot 'optional-deps'
    if([System.IO.Directory]::Exists($optionalDeps)){
        foreach($jar in (Get-ChildItem $optionalDeps -Filter '*.jar' | Sort-Object Name)){
            $classpath.Add($jar.FullName)
        }
    }
    $unique=@($classpath | Select-Object -Unique)
    Write-Utf8 (Join-Path $buildRoot 'offline-classpath.txt') ($unique -join "`n")
    Write-Utf8 (Join-Path $buildRoot 'dependency-evidence.txt') "minecraft=1.21.1`nneoforge=$neo`nprofile=$($selected.Source)`nupstream.outer.sha256=$expectedOuter`nupstream.inner.sha256=$expectedInner`n"
    Write-Host "Dependencies: Minecraft 1.21.1 / NeoForge $neo / $($unique.Count) local jars"
    return ($unique -join [System.IO.Path]::PathSeparator)
}
function Read-ZipText($archive,[string]$name){
    $entry=$archive.GetEntry($name)
    if($null -eq $entry){throw "Missing archive entry: $name"}
    $reader=[System.IO.StreamReader]::new($entry.Open())
    try {$reader.ReadToEnd()} finally {$reader.Dispose()}
}
function Verify-Artifact([string]$artifact){
    $veilPath='META-INF/jarjar/veil-neoforge-1.21.1-4.5.2.jar'
    $expectedVeilHash='e372d963f6b9162b68a5e4caeabde7e7ffbdf9ddaf03ebdf7ec47997e9718d66'
    $archive=[System.IO.Compression.ZipFile]::OpenRead($artifact)
    try {
        foreach($name in @('META-INF/neoforge.mods.toml','META-INF/jarjar/metadata.json','META-INF/THIRD-PARTY-NOTICES.txt','net/guzhenren/shengxian/ShengxianMod.class','net/guzhenren/shengxian/core/SessionFlow.class','assets/shengxian/pinwheel/post/aperture_transit.json','assets/shengxian/pinwheel/shaders/program/aperture_transit.json','assets/shengxian/pinwheel/shaders/program/aperture_transit.fsh',$veilPath)){
            if($null -eq $archive.GetEntry($name)){throw "Missing artifact entry: $name"}
        }
        $illegal=@($archive.Entries | Where-Object {($_.FullName.EndsWith('.jar') -and $_.FullName -ne $veilPath) -or ($_.FullName.EndsWith('.class') -and -not $_.FullName.StartsWith('net/guzhenren/shengxian/'))})
        if($illegal.Count -gt 0){throw 'Unexpected bundled jar or foreign class found'}
        $pipeline=Read-ZipText $archive 'assets/shengxian/pinwheel/post/aperture_transit.json' | ConvertFrom-Json
        $stage=@($pipeline.stages)[0]
        if($pipeline.renderStage -ne 'after_level' -or @($pipeline.stages).Count -ne 1 -or $stage.type -ne 'veil:blit' -or $stage.shader -ne 'shengxian:aperture_transit' -or $stage.in -ne 'minecraft:main'){throw 'Aperture Veil pipeline mismatch'}
        $program=Read-ZipText $archive 'assets/shengxian/pinwheel/shaders/program/aperture_transit.json' | ConvertFrom-Json
        if($program.vertex -ne 'veil:blit_screen' -or $program.fragment -ne 'shengxian:aperture_transit'){throw 'Aperture shader program mismatch'}
        $shader=Read-ZipText $archive 'assets/shengxian/pinwheel/shaders/program/aperture_transit.fsh'
        if($shader -notmatch 'uniform sampler2D DiffuseSampler' -or $shader -notmatch 'texture\(DiffuseSampler, texCoord\)'){throw 'Aperture shader does not composite the game scene'}
        if ([version]$ModVersion -ge [version]'0.3.8') {
            foreach($entry in @(
                'net/guzhenren/shengxian/core/AscensionVisuals.class',
                'net/guzhenren/shengxian/client/ClientAscensionEffects.class',
                'net/guzhenren/shengxian/client/VeilAscensionPostRenderer.class',
                'net/guzhenren/shengxian/server/AscensionVisualService.class',
                'net/guzhenren/shengxian/network/AscensionVisualPayload.class',
                'assets/shengxian/pinwheel/post/ascension_flow.json',
                'assets/shengxian/pinwheel/shaders/program/ascension_flow_post.json',
                'assets/shengxian/pinwheel/shaders/program/ascension_flow_post.fsh')) {
                if($null -eq $archive.GetEntry($entry)){throw ("Missing ascension VFX entry: "+$entry)}
            }
            $flow=Read-ZipText $archive 'assets/shengxian/pinwheel/post/ascension_flow.json' | ConvertFrom-Json
            if($flow.renderStage -ne 'after_level' -or @($flow.stages).Count -ne 1 -or $flow.PSObject.Properties['uniforms']) {
                throw 'Ascension flow pipeline mismatch'
            }
            $flowStage=@($flow.stages)[0]
            if($flowStage.type -ne 'veil:blit' -or $flowStage.shader -ne 'shengxian:ascension_flow_post' -or $flowStage.in -ne 'minecraft:main' -or $flowStage.out -ne 'veil:post') {
                throw 'Ascension flow stage mismatch'
            }
            $flowProgram=Read-ZipText $archive 'assets/shengxian/pinwheel/shaders/program/ascension_flow_post.json' | ConvertFrom-Json
            if($flowProgram.vertex -ne 'veil:blit_screen' -or $flowProgram.fragment -ne 'shengxian:ascension_flow_post') {
                throw 'Ascension flow program mismatch'
            }
            $flowShader=Read-ZipText $archive 'assets/shengxian/pinwheel/shaders/program/ascension_flow_post.fsh'
            foreach($uniform in @('Strength','Stage','Progress','Age','Time','Instability','Insight','Resolution','Weights','HeavenColor','EarthColor','HumanColor','JadeColor')) {
                if($flowShader -notmatch ('uniform (float|vec2|vec3) '+$uniform+';')) {throw ('Missing flow uniform '+$uniform)}
            }
            if($flowShader -notmatch 'depth<=0.0008' -or $flowShader -notmatch 'Strength<=0.001') {
                throw 'Ascension shader zero-strength/depth guard missing'
            }
        }

        if ([version]$ModVersion -ge [version]'0.3.9') {
            foreach($entry in @(
                'net/guzhenren/shengxian/core/AscensionAuraMesh.class',
                'net/guzhenren/shengxian/core/ApertureVisionMesh.class',
                'net/guzhenren/shengxian/core/AscensionCloudMesh.class',
                'net/guzhenren/shengxian/client/VeilApertureVisionRenderer.class',
                'net/guzhenren/shengxian/client/VeilAscensionCloudRenderer.class',
                'net/guzhenren/shengxian/client/AscensionOutcomeClient.class',
                'net/guzhenren/shengxian/client/AscensionResultScreen.class',
                'net/guzhenren/shengxian/client/RitualSoundscape.class',
                'net/guzhenren/shengxian/network/AscensionOutcomePayload.class',
                'net/guzhenren/shengxian/server/AscensionOutcomeService.class')) {
                if($null -eq $archive.GetEntry($entry)){throw ("Missing ritual entry: "+$entry)}
            }
            foreach($name in @('ascension_aperture_vision','ascension_cloud_volume')) {
                foreach($suffix in @('json','vsh','fsh')) {
                    $entry='assets/shengxian/pinwheel/shaders/program/'+$name+'.'+$suffix
                    if($null -eq $archive.GetEntry($entry)){throw ("Missing ritual shader "+$entry)}
                }
                $program=Read-ZipText $archive ('assets/shengxian/pinwheel/shaders/program/'+$name+'.json') | ConvertFrom-Json
                if($program.vertex -ne ('shengxian:'+$name) -or $program.fragment -ne ('shengxian:'+$name)) {throw 'Ritual shader reference mismatch'}
                $type=Read-ZipText $archive ('assets/shengxian/pinwheel/rendertypes/'+$name+'.json') | ConvertFrom-Json
                if($type.format -ne 'NEW_ENTITY' -or $type.mode -ne 'QUADS') {throw 'Ritual vertex format mismatch'}
                $mask=@($type.layers | Where-Object {$_.type -eq 'minecraft:write_mask'})
                if($mask.Count -ne 1 -or $mask[0].depth -ne $false) {throw 'Ritual must not write scene depth'}
            }
            $commonCount=0
            foreach($entry in $archive.Entries) {
                if($entry.FullName -notmatch '^net/guzhenren/shengxian/.+\.class$' -or $entry.FullName -match '/client/') {continue}
                $stream=$entry.Open(); $memory=[IO.MemoryStream]::new()
                try {
                    $stream.CopyTo($memory)
                    $classText=[Text.Encoding]::ASCII.GetString($memory.ToArray())
                } finally {$stream.Dispose();$memory.Dispose()}
                if($classText -match 'net/minecraft/client/|com/mojang/blaze3d/|org/lwjgl/|foundry/veil/|net\.minecraft\.client\.|foundry\.veil\.') {
                    throw ('Client rendering dependency in common class: '+$entry.FullName)
                }
                $commonCount++
            }
            Write-Host ("Static common-side dependency boundary verified: "+$commonCount+" class files")
            foreach($name in @('Calamity','Impact')) {
                if($flowShader -notmatch ('uniform float '+$name+';')){throw ('Missing ritual event uniform '+$name)}
            }
            if($flowShader -notmatch 'depthGuard' -or $flowShader -notmatch 'abs\(depth-sampledDepth\)') {
                throw 'Ritual refraction silhouette depth guard missing'
            }
        }
        if ([version]$ModVersion -ge [version]'0.3.10') {
            $cloud=Read-ZipText $archive 'assets/shengxian/pinwheel/rendertypes/ascension_cloud_volume.json' | ConvertFrom-Json
            $depth=@($cloud.layers | Where-Object {$_.type -eq 'minecraft:depth_test'})
            if($depth.Count -ne 1 -or $depth[0].mode -ne 'always') {throw 'Volume exit-face depth test would erase foreground cloud'}
            $cloudShader=Read-ZipText $archive 'assets/shengxian/pinwheel/shaders/program/ascension_cloud_volume.fsh'
            foreach($uniform in @('SceneDepthSampler','FrameSize','InverseProjection')) {
                if($cloudShader -notmatch ('uniform (sampler2D|vec2|mat4) '+$uniform+';')) {throw ('Missing cloud occlusion uniform '+$uniform)}
            }
            $depthClip = if ([version]$ModVersion -ge [version]'0.3.56') {
                'finish=min\(finish,sceneDistance / max\(VolumeScale,1\.0\)\)'
            } else { 'finish=min\(finish,sceneDistance\)' }
            if($cloudShader -notmatch $depthClip -or $cloudShader -notmatch 'if \(finish <= start\) discard') {
                throw 'Volume scene-depth integration clip is missing'
            }
        }
        if ([version]$ModVersion -ge [version]'0.3.14') {
            $hookEntries=@('shengxian.common.mixins.json',
                'net/guzhenren/shengxian/server/GuActivationObserver.class',
                'net/guzhenren/shengxian/mixin/common/YueGuangGuProcedureMixin.class',
                'net/guzhenren/shengxian/mixin/common/BaiXiangSheGuProcedureMixin.class',
                'net/guzhenren/shengxian/mixin/common/AiBieChiProcedureMixin.class',
                'net/guzhenren/shengxian/mixin/common/DingGu2ProcedureMixin.class')
            $expectedMixins=@('YueGuangGuProcedureMixin','BaiXiangSheGuProcedureMixin','AiBieChiProcedureMixin','DingGu2ProcedureMixin')
            # 0.3.22 adds the four Sanzhao keybind hooks and the two landscape hooks.
            if ([version]$ModVersion -ge [version]'0.3.22') {
                $hookEntries += @(
                    'net/guzhenren/shengxian/mixin/common/SanZhaoAnJianCProcedureMixin.class',
                    'net/guzhenren/shengxian/mixin/common/SanZhaoAnJianVProcedureMixin.class',
                    'net/guzhenren/shengxian/mixin/common/SanZhaoAnJianXProcedureMixin.class',
                    'net/guzhenren/shengxian/mixin/common/SanZhaoAnJianZProcedureMixin.class',
                    'net/guzhenren/shengxian/mixin/common/LandscapeExplosionMixin.class',
                    'net/guzhenren/shengxian/mixin/common/LandscapeBlockChangeMixin.class')
                $expectedMixins += @('SanZhaoAnJianCProcedureMixin','SanZhaoAnJianVProcedureMixin',
                    'SanZhaoAnJianXProcedureMixin','SanZhaoAnJianZProcedureMixin',
                    'LandscapeExplosionMixin','LandscapeBlockChangeMixin')
            }
            foreach($entry in $hookEntries) {
                if($null -eq $archive.GetEntry($entry)){throw ('Missing Gu activation hook entry: '+$entry)}
            }
            $hookConfig=Read-ZipText $archive 'shengxian.common.mixins.json' | ConvertFrom-Json
            if($hookConfig.required -ne $true -or $hookConfig.compatibilityLevel -ne 'JAVA_21' -or @($hookConfig.mixins).Count -ne $expectedMixins.Count) {
                throw 'Gu activation mixin config mismatch'
            }
            if(@(Compare-Object -ReferenceObject $expectedMixins -DifferenceObject @($hookConfig.mixins)).Count -ne 0) {
                throw 'Gu activation mixin target list mismatch'
            }
        }
        if ([version]$ModVersion -ge [version]'0.3.22') {
            foreach($entry in @(
                'net/guzhenren/shengxian/core/XianGuRuntime.class',
                'net/guzhenren/shengxian/core/XianGuStateCodec.class',
                'net/guzhenren/shengxian/core/RecentHealthHistory.class',
                'net/guzhenren/shengxian/core/TravelChannel.class',
                'net/guzhenren/shengxian/core/LandscapeHistory.class',
                'net/guzhenren/shengxian/compat/SavedXianGuUseReceipt.class',
                'net/guzhenren/shengxian/item/ImmortalGuItem.class',
                'net/guzhenren/shengxian/item/ShengxianItems.class',
                'net/guzhenren/shengxian/server/XianGuUseService.class',
                'net/guzhenren/shengxian/server/XianGuItemBinding.class',
                'net/guzhenren/shengxian/server/RenRuGuService.class',
                'net/guzhenren/shengxian/server/DingXianYouService.class',
                'net/guzhenren/shengxian/server/JiangShanRuGuService.class',
                'net/guzhenren/shengxian/server/XianGuEffects.class',
                'net/guzhenren/shengxian/server/XianGuEvents.class',
                'assets/shengxian/models/item/ding_xian_you.json',
                'assets/shengxian/models/item/ren_ru_gu_immortal.json',
                'assets/shengxian/models/item/jiang_shan_ru_gu.json')) {
                if($null -eq $archive.GetEntry($entry)){throw ('Missing representative Immortal Gu artifact entry: '+$entry)}
            }
            foreach($locale in @('zh_cn','en_us')) {
                $language=Read-ZipText $archive ('assets/shengxian/lang/'+$locale+'.json') | ConvertFrom-Json
                foreach($id in @('ding_xian_you','ren_ru_gu_immortal','jiang_shan_ru_gu')) {
                    $itemKey = 'item.shengxian.'+$id
                    $tooltipKey = 'tooltip.shengxian.'+$id
                    if (($null -eq $language.PSObject.Properties[$itemKey]) -or ($null -eq $language.PSObject.Properties[$tooltipKey]) -or ($null -eq $language.PSObject.Properties['tooltip.shengxian.immortal_gu_binding'])) {
                        throw ('Missing representative Immortal Gu language key in '+$locale+': '+$id)
                    }
                }
            }
        }
        if ([version]$ModVersion -ge [version]'0.3.11') {
            foreach($entry in @('net/guzhenren/shengxian/compat/SavedRankProjection.class',
                'net/guzhenren/shengxian/core/RankSixSyncDecision.class',
                'net/guzhenren/shengxian/core/RankSixSyncDecision$CheckedAction.class')) {
                if($null -eq $archive.GetEntry($entry)){throw ('Missing player persistence entry: '+$entry)}
            }
        }
        $metadata=Read-ZipText $archive 'META-INF/neoforge.mods.toml'
        $expectedVersionPattern=[regex]::Escape($ModVersion)
        if($metadata -notmatch 'modId = "shengxian"' -or $metadata -notmatch ('version = "'+$expectedVersionPattern+'"') -or $metadata -notmatch 'versionRange = "\[4\.5\.2,4\.6\.0\)"'){throw 'Artifact mod metadata mismatch'}
        if ([version]$ModVersion -ge [version]'0.3.14') {
            $hookRegistration=[regex]::Matches($metadata,'(?m)^\[\[mixins\]\]\s*config = "shengxian\.common\.mixins\.json"\s*$')
            if($hookRegistration.Count -ne 1){throw 'Gu activation mixin config must be registered exactly once'}
        }
        $jarjar=Read-ZipText $archive 'META-INF/jarjar/metadata.json' | ConvertFrom-Json
        $records=@($jarjar.jars | Where-Object {$_.path -eq $veilPath})
        if($records.Count -ne 1){throw 'Jar-in-Jar metadata must declare exactly one Veil artifact'}
        $record=$records[0]
        if($record.identifier.group -ne 'foundry.veil' -or $record.identifier.artifact -ne 'veil-neoforge-1.21.1' -or $record.version.artifactVersion -ne '4.5.2' -or $record.version.range -ne '[4.5.2,4.6.0)' -or $record.isObfuscated -ne $false){throw 'Veil Jar-in-Jar metadata mismatch'}

        $embedded=$archive.GetEntry($veilPath).Open()
        $buffer=[System.IO.MemoryStream]::new()
        try {$embedded.CopyTo($buffer)} finally {$embedded.Dispose()}
        $veilBytes=$buffer.ToArray()
        $sha=[System.Security.Cryptography.SHA256]::Create()
        try {$veilHash=[BitConverter]::ToString($sha.ComputeHash($veilBytes)).Replace('-','').ToLowerInvariant()} finally {$sha.Dispose()}
        if($veilHash -ne $expectedVeilHash){throw "Unexpected Veil 4.5.2 binary hash: $veilHash"}

        $veilStream=[System.IO.MemoryStream]::new($veilBytes)
        $veilArchive=[System.IO.Compression.ZipArchive]::new($veilStream,[System.IO.Compression.ZipArchiveMode]::Read,$false)
        try {
            $veilMetadata=Read-ZipText $veilArchive 'META-INF/neoforge.mods.toml'
            if($veilMetadata -notmatch 'modId="veil"' -or $veilMetadata -notmatch 'version="4\.5\.2"'){throw 'Embedded Veil mod metadata mismatch'}
            if($null -eq $veilArchive.GetEntry('LICENSE_Veil') -or $null -eq $veilArchive.GetEntry('META-INF/jarjar/metadata.json')){throw 'Embedded Veil license or nested dependency metadata missing'}
        } finally {$veilArchive.Dispose()}
    } finally {$archive.Dispose()}
    $hash=(Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Utf8 ($artifact+'.sha256') ($hash+'  '+[System.IO.Path]::GetFileName($artifact)+"`n")
    Write-Host "Artifact verified: $artifact"
    Write-Host "Embedded Veil: 4.5.2 (SHA256 $expectedVeilHash)"
    Write-Host "SHA256: $hash"
}

if($Action -eq 'Prepare'){[void](Prepare-Classpath);exit 0}
if($Action -eq 'Clean'){
    Clear-RunStaging
    Clear-DerivedUpstream
    Prune-Artifacts
    Write-Host 'Clean complete. The newest 3 release artifacts and the pinned .cache upstream jar are kept on purpose.'
    exit 0
}
$artifact=Join-Path $buildRoot ('libs\shengxian-'+$ModVersion+'.jar')
if($Action -eq 'Verify'){Verify-Artifact $artifact;Clear-RunStaging;exit 0}
$workRoot=Join-Path $buildRoot ('runs\'+[Guid]::NewGuid().ToString('N'))
[void][System.IO.Directory]::CreateDirectory($workRoot)
if($Action -eq 'Test'){Test-Core $workRoot;Clear-RunStaging;exit 0}
if($Action -eq 'Build'){Test-Core $workRoot}
if($Action -in @('Build','Compile') -and [System.IO.File]::Exists($artifact)){
    throw "Refusing to overwrite existing immutable artifact: $artifact. Increase -ModVersion."
}
$classpath=Prepare-Classpath
$classes=Join-Path $workRoot 'classes'
$sources=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main\java') -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})
Invoke-Javac $sources $classes $classpath
Test-GuActivationHooks $workRoot $classes $classpath
Test-RepresentativeGu $workRoot $classes $classpath
if($Action -in @('Build','SourceCheck','NbtCheck') -and [version]$ModVersion -ge [version]'0.3.11'){
    Test-PlayerProjection $workRoot $classes $classpath
}
# javac.args is an input file, not a distributable resource.
Remove-Item -LiteralPath (Join-Path $classes 'javac.args')
if($Action -eq 'SourceCheck'){Write-Host "Source compile verified.";Clear-RunStaging;exit 0}
if($Action -eq 'NbtCheck'){Write-Host "Saved-player NBT checks verified.";Clear-RunStaging;exit 0}
[void][System.IO.Directory]::CreateDirectory((Split-Path -Parent $artifact))
$candidate=Join-Path $workRoot ('shengxian-'+$ModVersion+'.jar')
$resourceSource=Join-Path $projectRoot 'src\main\resources'
$resourceStage=Join-Path $workRoot 'resources'
[void][System.IO.Directory]::CreateDirectory($resourceStage)
Get-ChildItem -LiteralPath $resourceSource -Force | Copy-Item -Destination $resourceStage -Recurse -Force
$modsToml=Join-Path $resourceStage 'META-INF\neoforge.mods.toml'
$metadataText=[System.IO.File]::ReadAllText($modsToml)
$versionedMetadata=[regex]::Replace($metadataText,'(?m)^version = ".*"$','version = "'+$ModVersion+'"')
if([regex]::Matches($versionedMetadata,'(?m)^version = "'+[regex]::Escape($ModVersion)+'"\r?$').Count -ne 1){throw 'Staged mod metadata must declare the requested artifact version exactly once'}
[System.IO.File]::WriteAllText($modsToml,$versionedMetadata,$utf8)
& $jar '--create' '--date=2026-01-01T00:00:00Z' '--file' $candidate '-C' $classes '.' '-C' $resourceStage '.'
if($LASTEXITCODE -ne 0){throw "Jar creation failed ($LASTEXITCODE)"}
Verify-Artifact $candidate
Move-Item -LiteralPath $candidate -Destination $artifact
Move-Item -LiteralPath ($candidate+'.sha256') -Destination ($artifact+'.sha256')
Write-Host "Installed immutable artifact: $artifact"
Clear-RunStaging
Prune-Artifacts
