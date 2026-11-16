$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$inputDirectory = Join-Path $projectRoot "target\jpackage-input"
$outputDirectory = Join-Path $projectRoot "dist"
$installerPath = Join-Path $outputDirectory "Perpustakaan-1.0.exe"
$iconPath = Join-Path $projectRoot "perpus-icon1.ico"

foreach ($toolName in @("mvn", "jpackage", "candle.exe", "light.exe")) {
    if (-not (Get-Command $toolName -ErrorAction SilentlyContinue)) {
        throw "Perangkat '$toolName' tidak ditemukan. Pastikan JDK 17, Maven, dan WiX Toolset tersedia di PATH."
    }
}

if (-not (Test-Path -LiteralPath $iconPath -PathType Leaf)) {
    throw "Ikon installer tidak ditemukan: $iconPath"
}

Push-Location $projectRoot
try {
    Write-Host "Menguji proyek dan menyiapkan dependency runtime..."
    & mvn verify dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=$inputDirectory"
    if ($LASTEXITCODE -ne 0) {
        throw "Build Maven gagal dengan exit code $LASTEXITCODE."
    }

    Copy-Item -LiteralPath (Join-Path $projectRoot "target\perpus-1.0.jar") `
        -Destination $inputDirectory -Force
    Get-ChildItem -LiteralPath $inputDirectory -Filter "javafx-*.jar" -File |
        Where-Object { $_.Name -notmatch "-win\.jar$" } |
        Remove-Item -Force

    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    if (Test-Path -LiteralPath $installerPath -PathType Leaf) {
        Remove-Item -LiteralPath $installerPath -Force
    }

    Write-Host "Membuat installer Windows..."
    & jpackage `
        --type exe `
        --input $inputDirectory `
        --dest $outputDirectory `
        --name Perpustakaan `
        --app-version 1.0 `
        --vendor "PerpusApp" `
        --description "Aplikasi desktop manajemen perpustakaan" `
        --main-jar "perpus-1.0.jar" `
        --main-class "com.perpus.Main" `
        --icon $iconPath `
        --java-options '--module-path=$APPDIR' `
        --java-options "--add-modules=javafx.controls" `
        --win-menu `
        --win-menu-group "Perpustakaan" `
        --win-shortcut `
        --win-dir-chooser `
        --win-upgrade-uuid "9D6EC7A2-8A62-4A9A-9C71-8C5CB5C55C10"
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage gagal dengan exit code $LASTEXITCODE."
    }

    if (-not (Test-Path -LiteralPath $installerPath -PathType Leaf)) {
        throw "jpackage selesai tetapi file installer tidak ditemukan: $installerPath"
    }

    Write-Host "Installer berhasil dibuat: $installerPath"
}
finally {
    Pop-Location
}
