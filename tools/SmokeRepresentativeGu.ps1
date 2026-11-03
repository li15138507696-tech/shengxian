[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Classes)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$fixture=Join-Path $projectRoot 'build/ascension-runtime-mixin-smoke-20261029-1e3d7320bee0454fa369e8de3a6283a7'
$smokeRoot=Join-Path $projectRoot ('build/representative-gu-smoke-'+[Guid]::NewGuid().ToString('N'))
$utf8=[Text.UTF8Encoding]::new($false)
$java='C:\Users\ADMIN\.jdks\temurin-21\bin\java.exe'
$jar='C:\Users\ADMIN\.jdks\temurin-21\bin\jar.exe'
$process=$null
function Invoke-IsolatedCommand([string]$command){
    $marker='SHENGXIAN_SMOKE_'+[Guid]::NewGuid().ToString('N')
    $process.StandardInput.WriteLine($command)
    $process.StandardInput.WriteLine('say '+$marker)
    $process.StandardInput.Flush()
    $deadline=(Get-Date).AddSeconds(12)
    while((Get-Date) -lt $deadline -and -not $process.HasExited){
        if((Test-Path -LiteralPath $latest) -and (Get-Content -LiteralPath $latest -Raw).Contains($marker)){return Get-Content -LiteralPath $latest -Raw}
        Start-Sleep -Milliseconds 100
    }
    throw "Isolated server did not process command: $command"
}
try {
    if(@(Get-NetTCPConnection -LocalPort 33593 -State Listen -ErrorAction SilentlyContinue).Count -gt 0){throw 'Isolated test port is occupied'}
    [void][IO.Directory]::CreateDirectory((Join-Path $smokeRoot 'mods'))
    [void](New-Item -ItemType Junction -Path (Join-Path $smokeRoot 'libraries') -Target (Join-Path $fixture 'libraries'))
    foreach($mod in @('guzhenren-outer.jar')){
        Copy-Item -LiteralPath (Join-Path $fixture ('mods/'+$mod)) -Destination (Join-Path $smokeRoot 'mods')
    }
    [IO.File]::WriteAllText((Join-Path $smokeRoot 'eula.txt'),'eula=true',$utf8)
    [IO.File]::WriteAllText((Join-Path $smokeRoot 'user_jvm_args.txt'),'-Xmx3G -Dfile.encoding=UTF-8 -Dmixin.debug.verbose=true',$utf8)
    $properties=@"
server-ip=127.0.0.1
server-port=33593
enable-rcon=false
online-mode=false
level-name=immortal-gu-test-world
level-type=minecraft:flat
generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}
view-distance=2
simulation-distance=2
spawn-protection=0
max-players=2
sync-chunk-writes=true
"@
    [IO.File]::WriteAllText((Join-Path $smokeRoot 'server.properties'),$properties,$utf8)
    $candidate=Join-Path $smokeRoot 'mods/shengxian-development-smoke.jar'
    & $jar --create --file $candidate -C $Classes net -C (Join-Path $projectRoot 'src/main/resources') .
    if($LASTEXITCODE -ne 0){throw 'Could not create isolated development candidate'}
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=$java; $start.Arguments='@user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.263/win_args.txt nogui'
    $start.WorkingDirectory=$smokeRoot; $start.UseShellExecute=$false; $start.CreateNoWindow=$true
    $start.RedirectStandardInput=$true; $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
    $process=[Diagnostics.Process]::new(); $process.StartInfo=$start
    [void]$process.Start()
    $stdout=[IO.File]::Open((Join-Path $smokeRoot 'stdout.log'),[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::ReadWrite)
    $stderr=[IO.File]::Open((Join-Path $smokeRoot 'stderr.log'),[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::ReadWrite)
    $stdoutTask=$process.StandardOutput.BaseStream.CopyToAsync($stdout)
    $stderrTask=$process.StandardError.BaseStream.CopyToAsync($stderr)
    $latest=Join-Path $smokeRoot 'logs/latest.log'
    $deadline=(Get-Date).AddMinutes(3)
    $ready=$false
    while((Get-Date) -lt $deadline -and -not $process.HasExited){
        if((Test-Path -LiteralPath $latest) -and (Get-Content -LiteralPath $latest -Raw) -match 'Done \('){$ready=$true;break}
        Start-Sleep -Milliseconds 500
    }
    if(-not $ready){throw "Isolated server did not reach Done; inspect $smokeRoot"}
    $log=Get-Content -LiteralPath (Join-Path $smokeRoot 'logs/debug.log') -Raw
    foreach($name in @('LandscapeExplosionMixin','LandscapeBlockChangeMixin')){
        if($log -notmatch ('Mixing '+$name+' from shengxian.common.mixins.json')){throw "Mixin application missing: $name"}
    }
    $results=[Collections.Generic.List[string]]::new()
    $results.Add('SERVER_READY=YES')
    $results.Add('LANDSCAPE_MIXINS=APPLIED')
    $catalog=Invoke-IsolatedCommand 'shengxiangu list'
    foreach($id in @('ding_xian_you','ren_ru_gu_immortal','jiang_shan_ru_gu')){
        if(-not $catalog.Contains($id)){throw "Catalog entry missing: $id"}
        $reply=Invoke-IsolatedCommand ('give @a shengxian:'+$id)
        if($reply -match ('Unknown item|Invalid item|not a valid item')){throw "Physical item argument rejected: $id"}
        $results.Add('REGISTERED_ITEM='+$id)
    }
    [void](Invoke-IsolatedCommand 'forceload add 0 0')
    $results.Add((Invoke-IsolatedCommand 'setblock 0 70 0 minecraft:stone'))
    $results.Add((Invoke-IsolatedCommand 'summon minecraft:tnt 0.5 71 0.5 {fuse:1s}'))
    Start-Sleep -Seconds 5
    $explosion=Invoke-IsolatedCommand 'execute if block 0 70 0 minecraft:air run say IMMORTAL_GU_EXPLOSION_FALLTHROUGH_OK'
    $results.Add('EXPLOSION_RESULT='+$explosion.Contains('IMMORTAL_GU_EXPLOSION_FALLTHROUGH_OK'))
    if(-not $explosion.Contains('IMMORTAL_GU_EXPLOSION_FALLTHROUGH_OK')){throw 'Explosion fallthrough did not destroy the test stone'}
    [void](Invoke-IsolatedCommand 'forceload remove 0 0')
    $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush()
    if(-not $process.WaitForExit(45000)){throw 'Isolated server accepted stop but did not exit cleanly'}
    $results.Add('SERVER_EXIT_CODE='+$process.ExitCode)
    if($process.ExitCode -ne 0){throw 'Isolated server exited with error'}
    $results.Add('SERVER_CLEAN_STOP=YES')
    $results.Add('SMOKE_ROOT='+$smokeRoot)
    [IO.File]::WriteAllLines((Join-Path $smokeRoot 'verification.txt'),$results,$utf8)
    $results
} finally {
    if($null -ne $process -and -not $process.HasExited){Stop-Process -Id $process.Id -Force}
}
