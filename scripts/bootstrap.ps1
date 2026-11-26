param(
    [switch]$InstallOnly,
    [switch]$NeedNode,
    [switch]$NeedJava,
    [switch]$NeedGit,
    [switch]$Rebuild,
    [switch]$NoBrowser,
    [switch]$CheckOnly,
    [switch]$BuildOnly,
    [switch]$Tunnel
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $ProjectRoot

function Write-Banner {
    Write-Host ''
    Write-Host '================================================================' -ForegroundColor Cyan
    Write-Host '  TBD.SUPPLY // Instalador y Lanzador Automatico' -ForegroundColor Cyan
    Write-Host '  Presentado por: Enrique, Edgarvis, Leonel y Anibal' -ForegroundColor DarkCyan
    Write-Host '================================================================' -ForegroundColor Cyan
    Write-Host ''
}

function Refresh-SessionPath {
    $machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $currentParts = $env:Path -split ';'
    $extra = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\nodejs'),
        'C:\Program Files\nodejs',
        'C:\Program Files\Git\cmd',
        (Join-Path $env:LOCALAPPDATA 'Programs\Git\cmd')
    ) | Where-Object { $_ -and (Test-Path $_) }
    $parts = @($extra + ($machinePath -split ';') + ($userPath -split ';') + $currentParts) |
        ForEach-Object { [Environment]::ExpandEnvironmentVariables($_) } |
        Where-Object { $_ -and $_.Trim() -ne '' } |
        Select-Object -Unique
    $env:Path = ($parts -join ';')
}

function Get-JavaMajorFromExe {
    param([string]$JavaExe)
    if (-not (Test-Path $JavaExe)) { return 0 }
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $JavaExe
        $psi.Arguments = '-version'
        $psi.RedirectStandardError = $true
        $psi.RedirectStandardOutput = $true
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $proc = [System.Diagnostics.Process]::Start($psi)
        $err = $proc.StandardError.ReadToEnd()
        $out = $proc.StandardOutput.ReadToEnd()
        $proc.WaitForExit()
        $text = "$err`n$out"
        if ($text -match '"(\d+)(?:\.\d+)?') {
            return [int]$Matches[1]
        }
    } catch { }
    return 0
}

function Find-JavaHome {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $candidates.Add($env:JAVA_HOME)
    }
    $cmdJava = Get-Command java -ErrorAction SilentlyContinue
    if ($cmdJava) {
        $candidates.Add((Split-Path (Split-Path $cmdJava.Source -Parent) -Parent))
    }
    $searchDirs = @(
        'C:\Program Files\Android\Android Studio\jbr',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Java',
        (Join-Path $env:USERPROFILE '.jdks')
    )
    foreach ($dir in $searchDirs) {
        if (-not (Test-Path $dir)) { continue }
        if (Test-Path (Join-Path $dir 'bin\java.exe')) {
            $candidates.Add($dir)
        } else {
            Get-ChildItem -Path $dir -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending |
                ForEach-Object {
                    if (Test-Path (Join-Path $_.FullName 'bin\java.exe')) {
                        $candidates.Add($_.FullName)
                    }
                }
        }
    }
    foreach ($homeDir in ($candidates | Select-Object -Unique)) {
        $ver = Get-JavaMajorFromExe (Join-Path $homeDir 'bin\java.exe')
        if ($ver -ge 21) {
            return @{ Home = $homeDir; Version = $ver }
        }
    }
    return $null
}

function Find-NodeAndNpm {
    $nodeCandidates = New-Object System.Collections.Generic.List[string]
    $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
    if ($nodeCmd) { $nodeCandidates.Add($nodeCmd.Source) }
    $nodeCandidates.Add((Join-Path $env:LOCALAPPDATA 'Programs\nodejs\node.exe'))
    $nodeCandidates.Add('C:\Program Files\nodejs\node.exe')

    $nodeExe = $nodeCandidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $nodeExe) { return $null }

    $npmDir = Split-Path $nodeExe -Parent
    $npmCandidates = @(
        (Join-Path $npmDir 'npm.cmd'),
        (Get-Command npm.cmd -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue),
        (Get-Command npm -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
    )
    $npmCmd = $npmCandidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $npmCmd) { return $null }

    try {
        $verRaw = (& $nodeExe -v).Trim().TrimStart('v')
        $major = [int]($verRaw.Split('.')[0])
        if ($major -ge 24) {
            return @{ Node = $nodeExe; Npm = $npmCmd; Version = $verRaw }
        }
    } catch { }
    return $null
}

function Find-GitExe {
    $g = Get-Command git -ErrorAction SilentlyContinue
    if ($g) { return $g.Source }
    if (Test-Path 'C:\Program Files\Git\cmd\git.exe') { return 'C:\Program Files\Git\cmd\git.exe' }
    $localGit = Join-Path $env:LOCALAPPDATA 'Programs\Git\cmd\git.exe'
    if (Test-Path $localGit) { return $localGit }
    return $null
}

function Find-VendorUiRoot {
    $vendored = Join-Path $ProjectRoot 'vendor\miuix'
    if (Test-Path (Join-Path $vendored 'settings.gradle.kts')) {
        return (Resolve-Path $vendored).Path
    }
    return $null
}

function Is-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Install-WithWinget {
    param([string]$PackageId, [string]$Label)
    $wg = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $wg) { return $false }
    Write-Host "[instalar] Instalando $Label ($PackageId) con winget..." -ForegroundColor Yellow
    & winget install --id $PackageId --exact --silent --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -eq 0 -or $LASTEXITCODE -eq -1978335189) { return $true }
    Write-Host "[instalar] Intentando actualizar $Label ($PackageId) con winget..." -ForegroundColor Yellow
    & winget upgrade --id $PackageId --exact --silent --accept-package-agreements --accept-source-agreements
    return ($LASTEXITCODE -eq 0 -or $LASTEXITCODE -eq -1978335189)
}

function Install-NodeFallbackMsi {
    Write-Host '[instalar] Descargando Node.js v26 oficial con verificacion SHA-256...' -ForegroundColor Yellow
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $baseUrl = 'https://nodejs.org/dist/latest-v26.x/'
    $shasums = (Invoke-WebRequest -Uri "${baseUrl}SHASUMS256.txt" -UseBasicParsing).Content
    $line = ($shasums -split "`n") | Where-Object { $_ -match 'node-v26\.\d+\.\d+-x64\.msi' } | Select-Object -First 1
    if (-not $line) { throw 'No se encontro el instalador MSI de Node.js v26 en nodejs.org' }
    $parts = $line.Trim() -split '\s+'
    $expectedHash = $parts[0].ToLowerInvariant()
    $fileName = $parts[1]
    $tmpMsi = Join-Path $env:TEMP $fileName
    Invoke-WebRequest -Uri "${baseUrl}${fileName}" -OutFile $tmpMsi -UseBasicParsing
    $actualHash = (Get-FileHash -Path $tmpMsi -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) {
        Remove-Item $tmpMsi -Force -ErrorAction SilentlyContinue
        throw "Checksum SHA-256 invalido para $fileName"
    }
    $proc = Start-Process msiexec.exe -ArgumentList "/i `"$tmpMsi`" /qn /norestart" -Wait -PassThru
    Remove-Item $tmpMsi -Force -ErrorAction SilentlyContinue
    if ($proc.ExitCode -ne 0) { throw "msiexec fallo para Node.js con codigo $($proc.ExitCode)" }
}

function Install-Temurin21FallbackMsi {
    Write-Host '[instalar] Descargando Eclipse Temurin JDK 21 oficial con verificacion SHA-256...' -ForegroundColor Yellow
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $apiUrl = 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $assets = Invoke-RestMethod -Uri $apiUrl -UseBasicParsing
    $pkg = ($assets | Select-Object -First 1).binary.installer
    if (-not $pkg) { throw 'No se encontro instalador JDK 21 en Adoptium' }
    $tmpMsi = Join-Path $env:TEMP $pkg.name
    Invoke-WebRequest -Uri $pkg.link -OutFile $tmpMsi -UseBasicParsing
    $actualHash = (Get-FileHash -Path $tmpMsi -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $pkg.checksum.ToLowerInvariant()) {
        Remove-Item $tmpMsi -Force -ErrorAction SilentlyContinue
        throw "Checksum SHA-256 invalido para $($pkg.name)"
    }
    $proc = Start-Process msiexec.exe -ArgumentList "/i `"$tmpMsi`" ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome /qn /norestart" -Wait -PassThru
    Remove-Item $tmpMsi -Force -ErrorAction SilentlyContinue
    if ($proc.ExitCode -ne 0) { throw "msiexec fallo para JDK 21 con codigo $($proc.ExitCode)" }
}

if ($InstallOnly) {
    if ($NeedGit) {
        if (-not (Install-WithWinget -PackageId 'Git.Git' -Label 'Git')) {
            throw 'No se pudo instalar Git automaticamente. Instalalo desde https://git-scm.com/downloads'
        }
    }
    if ($NeedNode) {
        if (-not (Install-WithWinget -PackageId 'OpenJS.NodeJS' -Label 'Node.js + npm')) {
            Install-NodeFallbackMsi
        }
    }
    if ($NeedJava) {
        if (-not (Install-WithWinget -PackageId 'EclipseAdoptium.Temurin.21.JDK' -Label 'Eclipse Temurin JDK 21')) {
            Install-Temurin21FallbackMsi
        }
    }
    exit 0
}

Write-Banner

# 1. Detectar requisitos del sistema
Refresh-SessionPath
$nodeInfo = Find-NodeAndNpm
$javaInfo = Find-JavaHome
$vendorUiRoot = Find-VendorUiRoot
$gitExe = Find-GitExe

$missingNode = ($null -eq $nodeInfo)
$missingJava = ($null -eq $javaInfo)
$missingGit = ($null -eq $vendorUiRoot -and $null -eq $gitExe)

if ($missingNode -or $missingJava -or $missingGit) {
    $missingLabels = @()
    if ($missingNode) { $missingLabels += 'Node.js (>= 24) + npm' }
    if ($missingJava) { $missingLabels += 'Java JDK 21+' }
    if ($missingGit) { $missingLabels += 'Git' }

    Write-Host "[check] Faltan dependencias: $($missingLabels -join ', ')" -ForegroundColor Yellow

    $flags = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"", '-InstallOnly')
    if ($missingNode) { $flags += '-NeedNode' }
    if ($missingJava) { $flags += '-NeedJava' }
    if ($missingGit) { $flags += '-NeedGit' }

    if (Is-Administrator) {
        & powershell.exe @flags
        if ($LASTEXITCODE -ne 0) { throw 'La instalacion de dependencias fallo.' }
    } else {
        Write-Host '[uac] Solicitando permiso de Administrador para instalar las dependencias faltantes...' -ForegroundColor Yellow
        try {
            $proc = Start-Process powershell.exe -ArgumentList ($flags -join ' ') -Verb RunAs -Wait -PassThru
            if ($proc.ExitCode -ne 0) {
                throw "El instalador elevado cancelo o fallo (codigo $($proc.ExitCode))."
            }
        } catch {
            throw "No se pudo elevar permisos UAC automaticamente ($($_.Exception.Message)). Ejecuta iniciar.bat como Administrador o instala: $($missingLabels -join ', ')."
        }
    }

    Refresh-SessionPath
    $nodeInfo = Find-NodeAndNpm
    $javaInfo = Find-JavaHome
    $gitExe = Find-GitExe

    if (-not $nodeInfo) { throw 'Node.js >= 24 o npm no quedaron disponibles tras instalar.' }
    if (-not $javaInfo) { throw 'JDK 21+ no quedo disponible tras instalar.' }
}

Write-Host "[ok] Node.js v$($nodeInfo.Version) + npm listos" -ForegroundColor Green
Write-Host "[ok] Java $($javaInfo.Version) en $($javaInfo.Home)" -ForegroundColor Green

$env:JAVA_HOME = $javaInfo.Home
$env:Path = "$(Join-Path $javaInfo.Home 'bin');$(Split-Path $nodeInfo.Node -Parent);$env:Path"

# 2. Verificar o descargar dependencias locales de interfaz dentro de vendor/
if (-not $vendorUiRoot) {
    if (-not $gitExe) { throw 'Se requiere Git para inicializar el motor de interfaz local.' }
    $vendorDir = Join-Path $ProjectRoot 'vendor\miuix'
    New-Item -ItemType Directory -Force -Path (Split-Path $vendorDir -Parent) | Out-Null
    Write-Host '[setup] Descargando motor de interfaz dentro de vendor/...' -ForegroundColor Yellow
    & $gitExe clone --depth 50 https://github.com/compose-miuix-ui/miuix.git $vendorDir
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo descargar el motor de interfaz.' }
    & $gitExe -C $vendorDir checkout d8efe187
    $vendorUiRoot = (Resolve-Path $vendorDir).Path
}
Write-Host '[ok] Motor de interfaz listo dentro del proyecto' -ForegroundColor Green

if ($CheckOnly) {
    Write-Host '[check-only] Verificacion de entorno completada con exito.' -ForegroundColor Cyan
    exit 0
}

# 3. Generar .env seguro si no existe
$envFile = Join-Path $ProjectRoot '.env'
if (-not (Test-Path $envFile)) {
    $envExample = Join-Path $ProjectRoot '.env.example'
    $content = Get-Content $envExample -Raw
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes1 = New-Object byte[] 32
    $bytes2 = New-Object byte[] 32
    $rng.GetBytes($bytes1)
    $rng.GetBytes($bytes2)
    $pepper = ([BitConverter]::ToString($bytes1)).Replace('-', '').ToLowerInvariant()
    $salt = ([BitConverter]::ToString($bytes2)).Replace('-', '').ToLowerInvariant()
    $content = $content -replace '(?m)^SESSION_PEPPER=.*$', "SESSION_PEPPER=$pepper"
    $content = $content -replace '(?m)^IP_HASH_SALT=.*$', "IP_HASH_SALT=$salt"
    Set-Content -Path $envFile -Value $content -Encoding ASCII
    Write-Host '[ok] Archivo .env creado con secretos criptograficos unicos.' -ForegroundColor Green
}

# 4. Instalar paquetes npm si existe lockfile o node_modules requerido
if (Test-Path (Join-Path $ProjectRoot 'package-lock.json')) {
    Write-Host '[npm] Verificando dependencias...' -ForegroundColor Cyan
    & $nodeInfo.Npm install --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'npm install fallo.' }
}

# 5. Compilar interfaz WebAssembly si hace falta o hubo cambios
$distWebDir = Join-Path $ProjectRoot 'server\dist-web'
$stampPath = Join-Path $distWebDir '.build-stamp'
$watchFiles = @()
$watchFiles += Get-ChildItem -Path (Join-Path $ProjectRoot 'webApp\src') -Recurse -File -ErrorAction SilentlyContinue
$watchFiles += Get-Item -Path (Join-Path $ProjectRoot 'webApp\build.gradle.kts'), (Join-Path $ProjectRoot 'settings.gradle.kts'), (Join-Path $ProjectRoot 'gradle\libs.versions.toml') -ErrorAction SilentlyContinue
$fingerprintRaw = ($watchFiles | Sort-Object FullName | ForEach-Object { "$($_.LastWriteTimeUtc.Ticks):$($_.FullName)" }) -join "`n"
$sha = [System.Security.Cryptography.SHA256]::Create()
$sourceHash = ([BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($fingerprintRaw)))).Replace('-', '').ToLowerInvariant()

$currentStamp = if (Test-Path $stampPath) { (Get-Content $stampPath -Raw).Trim() } else { '' }
$hasComposeApp = Test-Path (Join-Path $distWebDir 'composeApp.js')

if ($Rebuild -or (-not $hasComposeApp) -or ($currentStamp -ne $sourceHash)) {
    Write-Host '[build] Compilando interfaz web...' -ForegroundColor Cyan
    $env:GIT_DIR = Join-Path $vendorUiRoot '.git'
    $env:GIT_WORK_TREE = $vendorUiRoot
    try {
        & (Join-Path $ProjectRoot 'gradlew.bat') :webApp:wasmJsBrowserDistribution -Prelease --no-configure-on-demand --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Gradle fallo con codigo $LASTEXITCODE" }
    } finally {
        Remove-Item Env:\GIT_DIR -ErrorAction SilentlyContinue
        Remove-Item Env:\GIT_WORK_TREE -ErrorAction SilentlyContinue
    }

    $gradleDist = Join-Path $ProjectRoot 'webApp\build\dist\wasmJs\productionExecutable'
    if (-not (Test-Path (Join-Path $gradleDist 'composeApp.js'))) {
        throw 'No se encontro el bundle compilado en webApp/build/dist/wasmJs/productionExecutable'
    }
    if (Test-Path $distWebDir) { Remove-Item $distWebDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $distWebDir | Out-Null
    Copy-Item -Path (Join-Path $gradleDist '*') -Destination $distWebDir -Recurse -Force
    Set-Content -Path $stampPath -Value $sourceHash -Encoding ASCII
    Write-Host '[ok] Interfaz web compilada y lista en server/dist-web/' -ForegroundColor Green
} else {
    Write-Host '[ok] Interfaz web al dia (arranque rapido).' -ForegroundColor Green
}

if ($BuildOnly) {
    Write-Host '[build-only] Compilacion finalizada con exito.' -ForegroundColor Cyan
    exit 0
}

# 6. Leer PORT del .env y lanzar servidor + abrir navegador tras /api/health
$port = 3000
$envLines = Get-Content $envFile
foreach ($l in $envLines) {
    if ($l -match '^\s*PORT\s*=\s*(\d+)\s*$') {
        $port = [int]$Matches[1]
    }
}
$targetUrl = "http://127.0.0.1:$port"

function Ensure-Cloudflared {
    $cmdCf = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($cmdCf) { return $cmdCf.Source }
    $toolsDir = Join-Path $ProjectRoot '.tools'
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $localExe = Join-Path $toolsDir 'cloudflared.exe'
    if ((Test-Path $localExe) -and ((Get-Item $localExe).Length -gt 1000000)) {
        return $localExe
    }
    Write-Host '[tunnel] Descargando cloudflared.exe oficial en .tools\cloudflared.exe...' -ForegroundColor Yellow
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $tmpExe = "$localExe.tmp"
    $dlUrl = 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe'
    Invoke-WebRequest -Uri $dlUrl -OutFile $tmpExe -UseBasicParsing
    Move-Item -Path $tmpExe -Destination $localExe -Force
    Write-Host '[ok] cloudflared.exe descargado.' -ForegroundColor Green
    return $localExe
}

Write-Host ''
Write-Host "[run] Iniciando tienda en $targetUrl (Ctrl+C para detener)..." -ForegroundColor Cyan
Write-Host ''

# Limpiar instancias previas que hayan quedado abiertas en el mismo puerto o tunel
try {
    $oldConns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($oldConns) {
        $oldPids = $oldConns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($opid in $oldPids) {
            if ($opid -gt 4) {
                Write-Host "[clean] Cerrando servidor anterior en puerto $port (PID $opid)..." -ForegroundColor Yellow
                Stop-Process -Id $opid -Force -ErrorAction SilentlyContinue
            }
        }
        Start-Sleep -Milliseconds 500
    }
} catch { }

if ($Tunnel) {
    Get-Process cloudflared -ErrorAction SilentlyContinue | ForEach-Object {
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    }
}

$serverEntry = Join-Path $ProjectRoot 'server\src\index.js'
$nodeProc = $null
$cfProc = $null

try {
    $nodeProc = Start-Process -FilePath $nodeInfo.Node -ArgumentList "`"$serverEntry`"" -NoNewWindow -PassThru
    Start-Sleep -Milliseconds 500

    $healthOk = $false
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    for ($i = 0; $i -lt 60; $i++) {
        if ($nodeProc.HasExited) {
            throw "El servidor Node.js termino inesperadamente (codigo $($nodeProc.ExitCode))."
        }
        try {
            $resp = Invoke-WebRequest -Uri "$targetUrl/api/health" -UseBasicParsing -TimeoutSec 2
            if ($resp.StatusCode -eq 200 -and (-not $nodeProc.HasExited)) {
                $healthOk = $true
                break
            }
        } catch {
            Start-Sleep -Milliseconds 300
        }
    }

    if ($healthOk -and (-not $NoBrowser)) {
        Start-Process $targetUrl
    }

    function Test-PublicTunnelUrl {
        param([string]$Url)
        if (-not $Url) { return $false }
        $js = "fetch('$Url/api/health',{signal:AbortSignal.timeout(5000)}).then(r=>r.status===200?r.text():'').then(t=>process.exit(t.includes('true')&&!t.includes('1033')?0:1)).catch(()=>process.exit(1));"
        & $nodeInfo.Node -e $js 2>$null
        return ($LASTEXITCODE -eq 0)
    }

    function Start-VerifiedCloudflareTunnel {
        param([string]$CfExePath)
        $cfLog = Join-Path $ProjectRoot '.tools\cloudflared.log'

        for ($attempt = 1; $attempt -le 4; $attempt++) {
            Get-Process cloudflared -ErrorAction SilentlyContinue | ForEach-Object {
                Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
            }
            Start-Sleep -Milliseconds 300
            if (Test-Path $cfLog) { Remove-Item $cfLog -Force -ErrorAction SilentlyContinue }

            Write-Host "[tunnel] Abriendo Cloudflare Quick Tunnel (intento $attempt/4)..." -ForegroundColor Yellow
            $proc = Start-Process -FilePath $CfExePath -ArgumentList @('tunnel', '--url', $targetUrl, '--no-autoupdate', '--protocol', 'http2') -RedirectStandardError $cfLog -WindowStyle Hidden -PassThru

            $pubUrl = $null
            $edgeRegistered = $false
            for ($i = 0; $i -lt 75; $i++) {
                if ($proc.HasExited) { break }
                if (Test-Path $cfLog) {
                    $raw = Get-Content -Path $cfLog -Raw -ErrorAction SilentlyContinue
                    if ((-not $pubUrl) -and ($raw -match 'https://[a-zA-Z0-9-]+\.trycloudflare\.com')) {
                        $pubUrl = $Matches[0]
                    }
                    if ($pubUrl -and ($raw -match 'Registered tunnel connection')) {
                        $edgeRegistered = $true
                        break
                    }
                }
                Start-Sleep -Milliseconds 400
            }

            if ($pubUrl -and $edgeRegistered -and (-not $proc.HasExited)) {
                Write-Host "[tunnel] Enlace creado ($pubUrl). Esperando propagacion de Cloudflare (3-10s)..." -ForegroundColor Yellow
                $verified = $false
                for ($v = 0; $v -lt 14; $v++) {
                    if ($proc.HasExited) { break }
                    Start-Sleep -Milliseconds 1200
                    if (Test-PublicTunnelUrl -Url $pubUrl) {
                        $verified = $true
                        break
                    }
                }

                if ($verified -and (-not $proc.HasExited)) {
                    try { Set-Clipboard -Value $pubUrl } catch { }
                    Write-Host ''
                    Write-Host '================================================================' -ForegroundColor Green
                    Write-Host '  ENLACE PUBLICO CLOUDFLARE 100% VERIFICADO Y ACTIVO:' -ForegroundColor Green
                    Write-Host "  $pubUrl" -ForegroundColor White
                    Write-Host '  -> Ya esta COPIADO al portapapeles (pegalo con Ctrl+V).' -ForegroundColor Green
                    Write-Host '  -> En tu PC: http://127.0.0.1:3000 entra directo con Admin.' -ForegroundColor Cyan
                    Write-Host '  -> NO pulses Ctrl+C en esta ventana a menos que quieras apagar la tienda.' -ForegroundColor Yellow
                    Write-Host '================================================================' -ForegroundColor Green
                    Write-Host ''
                    return @{ Proc = $proc; Url = $pubUrl }
                }
            }

            Write-Host '[tunnel] Ese subdominio de Cloudflare dio Error 1033 o tardo en propagar. Pidiendo uno nuevo...' -ForegroundColor Yellow
            if ($proc -and (-not $proc.HasExited)) {
                Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            }
        }

        Write-Host "[tunnel] No se pudo verificar el tunel tras 4 intentos; revisa $cfLog" -ForegroundColor Red
        return @{ Proc = $null; Url = $null }
    }

    $activeTunnelUrl = $null
    $cfExe = $null
    if ($Tunnel) {
        $cfExe = Ensure-Cloudflared
        $tInfo = Start-VerifiedCloudflareTunnel -CfExePath $cfExe
        $cfProc = $tInfo.Proc
        $activeTunnelUrl = $tInfo.Url
    }

    $watchdogTicks = 0
    $tunnelFails = 0
    while ($nodeProc -and (-not $nodeProc.HasExited)) {
        if ($Tunnel -and $cfExe) {
            $watchdogTicks++
            if (($cfProc -and $cfProc.HasExited) -or (-not $cfProc)) {
                Write-Host '[tunnel] cloudflared se detuvo; reconectando automaticamente...' -ForegroundColor Yellow
                $tInfo = Start-VerifiedCloudflareTunnel -CfExePath $cfExe
                $cfProc = $tInfo.Proc
                $activeTunnelUrl = $tInfo.Url
                $watchdogTicks = 0
                $tunnelFails = 0
            } elseif ($watchdogTicks -ge 40 -and $activeTunnelUrl) {
                $watchdogTicks = 0
                if (Test-PublicTunnelUrl -Url $activeTunnelUrl) {
                    $tunnelFails = 0
                } else {
                    $tunnelFails++
                    if ($tunnelFails -ge 3) {
                        Write-Host '[tunnel] El enlace de Cloudflare dejo de responder. Generando nuevo enlace...' -ForegroundColor Yellow
                        $tInfo = Start-VerifiedCloudflareTunnel -CfExePath $cfExe
                        $cfProc = $tInfo.Proc
                        $activeTunnelUrl = $tInfo.Url
                        $tunnelFails = 0
                    }
                }
            }
        }
        Start-Sleep -Milliseconds 500
    }
    if ($nodeProc) {
        exit $nodeProc.ExitCode
    }
} finally {
    if ($cfProc -and (-not $cfProc.HasExited)) {
        Stop-Process -Id $cfProc.Id -Force -ErrorAction SilentlyContinue
    }
    if ($nodeProc -and (-not $nodeProc.HasExited)) {
        Stop-Process -Id $nodeProc.Id -Force -ErrorAction SilentlyContinue
    }
}
