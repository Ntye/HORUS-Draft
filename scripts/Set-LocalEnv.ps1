<#
.SYNOPSIS
  Sets the HORUS environment variables for the CURRENT PowerShell session (local testing only).

.DESCRIPTION
  DOT-SOURCE it so the variables stay in your session:   . .\scripts\Set-LocalEnv.ps1

  The database passwords are random and generated ONCE, then kept in your user profile
  ($HOME\.horus-local\env.json) -- never in the repository (CLAUDE.md section 3: secrets come from
  the environment only). The PostgreSQL container bakes these passwords into its roles the first
  time its volume is created, so later sessions must reuse the same values; this file guarantees
  that. If you delete it, also reset the database volume:  docker compose down -v

  This is for a LOCAL machine. Production secrets come from the secrets manager, not from here.
#>
param([switch]$Quiet)

# Deliberately no $ErrorActionPreference change: this file is dot-sourced, so it would leak into the
# caller's session and turn native-command stderr (Docker prints progress there) into errors.
$dir  = Join-Path $HOME ".horus-local"
$file = Join-Path $dir "env.json"

function New-LocalSecret {
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    # Alphanumeric only: safe inside the SQL and shell quoting the compose init script uses.
    ([Convert]::ToBase64String($bytes) -replace '[^A-Za-z0-9]', '').Substring(0, 24)
}

if (-not (Test-Path $file)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    [ordered]@{
        HORUS_DB_ADMIN_PASSWORD = New-LocalSecret
        HORUS_INGEST_PASSWORD   = New-LocalSecret
        HORUS_SCREEN_PASSWORD   = New-LocalSecret
        HORUS_AUDIT_PASSWORD    = New-LocalSecret
        PGADMIN_DEFAULT_PASSWORD = New-LocalSecret
    } | ConvertTo-Json | Set-Content -Path $file -Encoding UTF8
    Write-Host "Created local secrets in $file (outside the repository)."
}

$values = Get-Content $file -Raw | ConvertFrom-Json
foreach ($property in $values.PSObject.Properties) {
    Set-Item -Path "Env:$($property.Name)" -Value $property.Value
}

if (-not $env:HORUS_OPERATOR)          { $env:HORUS_OPERATOR = $env:USERNAME }
if (-not $env:PGADMIN_DEFAULT_EMAIL)   { $env:PGADMIN_DEFAULT_EMAIL = "horus@example.com" }
$env:HORUS_SYNTHETIC_DIR = Join-Path $env:TEMP "horus-synthetic"

if (-not $Quiet) {
Write-Host "HORUS local environment set for this session:"
Write-Host "  operator        : $env:HORUS_OPERATOR"
Write-Host "  synthetic feeds : $env:HORUS_SYNTHETIC_DIR   (outside the repository)"
Write-Host "  pgAdmin login   : $env:PGADMIN_DEFAULT_EMAIL  /  (password in $file)"
}
