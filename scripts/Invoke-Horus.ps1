<#
.SYNOPSIS
  Runs one HORUS CLI command against the local database and returns its EXIT CODE.

.DESCRIPTION
  Loads the local environment (Set-LocalEnv.ps1), builds the application jar if it is out of date,
  and runs it. Pass the command and its options exactly as you would type them:

    .\scripts\Invoke-Horus.ps1 ingest --source=worldcheck
    .\scripts\Invoke-Horus.ps1 screen --name "Zorvan Talmesc" --type INDIVIDUAL --dob 1980
    .\scripts\Invoke-Horus.ps1 --help

  Why a wrapper rather than `gradlew bootRun --args=...`:
   * Quoting an argument that contains a space through Gradle is fragile on this repository's path
     (it contains spaces), and differs between shells.
   * Gradle reports any non-zero exit as a BUILD FAILURE, which would bury the CLI's meaningful
     exit codes (screen: 0 NO_MATCH, 1 POSSIBLE_MATCH, 2 STRONG_MATCH, 3 ERROR, 64 bad input).
   Here the exit code is the CLI's own; read it with  $LASTEXITCODE  or  the printed exit= line.

  Set HORUS_FEED_PATH yourself for `ingest` (see docs/runbook/testing-guide.md).
#>
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$CliArgs)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "Set-LocalEnv.ps1") -Quiet

$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $repo
try {
    & .\gradlew.bat :bootstrap:bootJar --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "the build failed (exit $LASTEXITCODE)" }

    # The build directory is not always inside the repository: OneDrive dehydrates build outputs
    # into cloud placeholders that Gradle cannot snapshot, so a local init script may redirect
    # every project's buildDir to C:\horus-build (see docs/runbook/testing-guide.md). Look in both
    # places and take the newest jar, so the wrapper works either way.
    $jar = @("$repo\bootstrap\build\libs", "C:\horus-build\bootstrap\libs") |
        Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem "$_\bootstrap-*.jar" -ErrorAction SilentlyContinue } |
        Where-Object { $_.Name -notlike "*-plain.jar" } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw "no executable jar found in bootstrap\build\libs or C:\horus-build\bootstrap\libs" }
    Write-Host "using $($jar.FullName)" -ForegroundColor DarkGray

    # Quieter output: no banner, WARN-level Spring logging. Environment variables (not --spring.*
    # arguments) because every argument is handed to the picocli parser.
    $env:SPRING_MAIN_BANNERMODE = "off"
    $env:LOGGING_LEVEL_ROOT = "WARN"

    # The CLI reports errors on stderr by design ("ERROR: screening unavailable ..."). Under "Stop",
    # PowerShell would turn that text into a terminating error and lose the exit code this wrapper
    # exists to return, so relax it for exactly this call.
    $ErrorActionPreference = "Continue"
    & java -jar $jar.FullName @CliArgs
    $code = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
} finally {
    Pop-Location
}
Write-Host "exit=$code"
exit $code
