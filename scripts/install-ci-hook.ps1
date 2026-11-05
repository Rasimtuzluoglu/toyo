$ErrorActionPreference = "Stop"

# Push öncesi yerel CI'ı çalıştıran git hook'unu kurar.
# Kullanım: powershell -ExecutionPolicy Bypass -File .\scripts\install-ci-hook.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot
$hooksDir = Join-Path $projectRoot ".git\hooks"
$hookFile = Join-Path $hooksDir "pre-push"

if (-not (Test-Path $hooksDir)) {
    Write-Error "Git hook klasörü bulunamadı: $hooksDir"
    exit 1
}

$content = @"
#!/bin/sh
ROOT="`$(git rev-parse --show-toplevel)"
SCRIPT="`$(cygpath -w "`$ROOT/scripts/ci-local.ps1")"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "`$SCRIPT"
exit `$?
"@

# Git Bash shebang'inin bozulmaması için LF satır sonu ve BOM'suz UTF-8.
$content = $content.Replace("`r`n", "`n")
[System.IO.File]::WriteAllText($hookFile, $content, [System.Text.UTF8Encoding]::new($false))

Write-Host "Pre-push CI hook'u kuruldu:"
Write-Host $hookFile
Write-Host ""
Write-Host "Bir sonraki git push öncesinde testler ve Docker build çalışacak."
Write-Host "Kaldırmak için: Remove-Item `"$hookFile`""
