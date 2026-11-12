$ErrorActionPreference = "Stop"

# Bu betik yedegi ayri bir veritabanina geri yukler, kayit sayilarini ve cari toplamini
# uretim veritabaniyla karsilastirir. Uretim veritabanina yazmaz.
# Kullanim: powershell -ExecutionPolicy Bypass -File .\scripts\restore-drill.ps1
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$backupFile = "restore-drill-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".dump"
$testDatabase = "servisdefteri_restore_test"

function DbShell($command) {
    docker compose exec -T db sh -lc $command
    if ($LASTEXITCODE -ne 0) {
        throw "Veritabani komutu basarisiz: $command"
    }
}

function CountRows($database, $table) {
    $value = (docker compose exec -T db sh -lc `
        "PGPASSWORD=`$POSTGRES_PASSWORD psql -U `$POSTGRES_USER -d $database -t -A -c 'select count(*) from $table'").Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "$table sayisi okunamadi."
    }
    return $value
}

function LedgerTotal($database) {
    $value = (docker compose exec -T db sh -lc `
        "PGPASSWORD=`$POSTGRES_PASSWORD psql -U `$POSTGRES_USER -d $database -t -A -c 'select coalesce(sum(amount),0) from account_transactions'").Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Cari toplami okunamadi."
    }
    return $value
}

try {
    Write-Host "1/6 Veritabani hazirlaniyor ve yedek aliniyor..."
    docker compose up -d db | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Veritabani baslatilamadi." }
    DbShell "PGPASSWORD=`$POSTGRES_PASSWORD pg_dump -h 127.0.0.1 -p 5432 -U `$POSTGRES_USER -Fc -f /tmp/$backupFile `$POSTGRES_DB"

    Write-Host "2/6 Uretim sayilari okunuyor..."
    $tables = @("customers", "vehicles", "work_orders", "payments", "account_transactions")
    $before = @{}
    foreach ($table in $tables) {
        $before[$table] = CountRows '$POSTGRES_DB' $table
    }
    $beforeLedger = LedgerTotal '$POSTGRES_DB'

    Write-Host "3/6 Test veritabani hazirlaniyor..."
    DbShell "PGPASSWORD=`$POSTGRES_PASSWORD psql -U `$POSTGRES_USER -d postgres -c 'DROP DATABASE IF EXISTS $testDatabase'"
    DbShell "PGPASSWORD=`$POSTGRES_PASSWORD psql -U `$POSTGRES_USER -d postgres -c 'CREATE DATABASE $testDatabase'"

    Write-Host "4/6 Yedek geri yukleniyor..."
    DbShell "PGPASSWORD=`$POSTGRES_PASSWORD pg_restore -h 127.0.0.1 -p 5432 -U `$POSTGRES_USER -d $testDatabase /tmp/$backupFile"

    Write-Host "5/6 Dogrulama yapiliyor..."
    foreach ($table in $tables) {
        $after = CountRows $testDatabase $table
        Write-Host ("  {0}: uretim={1}, geri yuklenen={2}" -f $table, $before[$table], $after)
        if ($after -ne $before[$table]) {
            throw "Geri yukleme sonrasi $table sayisi uretimle ayni degil."
        }
    }
    $afterLedger = LedgerTotal $testDatabase
    Write-Host ("  cari toplam: uretim={0}, geri yuklenen={1}" -f $beforeLedger, $afterLedger)
    if ($afterLedger -ne $beforeLedger) {
        throw "Geri yukleme sonrasi cari toplam uretimle ayni degil."
    }
    if ([int]$before["customers"] -eq 0) {
        Write-Host "UYARI: Uretim veritabani bos; tatbikat bos veriyle dogrulandi."
    }

    Write-Host "6/6 Temizlik yapiliyor..."
    Write-Host "Yedek geri yukleme tatbikati BASARILI."
} finally {
    docker compose exec -T db sh -lc "rm -f /tmp/$backupFile" 2>$null | Out-Null
    docker compose exec -T db sh -lc "PGPASSWORD=`$POSTGRES_PASSWORD psql -U `$POSTGRES_USER -d postgres -c 'DROP DATABASE IF EXISTS $testDatabase'" 2>$null | Out-Null
}
