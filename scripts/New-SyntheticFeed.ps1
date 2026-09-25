<#
.SYNOPSIS
  Generates a SYNTHETIC World-Check-shaped feed for local testing. Every name is invented.

.DESCRIPTION
  The real feed is licensed and personal data: it never enters this repository and no agent may
  read it (CLAUDE.md section 3). This script produces a stand-in that is shaped like the fixture
  the WorldCheckAdapter is tested against AND whose population statistics sit inside the adapter's
  capability gate (CLAUDE.md section 6): names/type/country 100%, date of birth 27%, passport 1%.
  A hand-made 20-record file cannot pass that gate; 100 records with exactly 27 dates of birth
  and 1 passport can.

  Output is deterministic (fixed seed): the same parameters always give the same bytes, so the
  ingest's checksum-based idempotency can be demonstrated. Write the file OUTSIDE the repository.

.PARAMETER OutFile
  Where to write the XML. Must be outside the repository working tree.

.PARAMETER OmitUid
  Optional uid to leave out, to demonstrate a tombstone on a second load (e.g. wc-demo-3).

.PARAMETER Count
  Total records. Default 100. Keep it at 100 for the capability gate (27 DOB, 1 passport).

.EXAMPLE
  .\scripts\New-SyntheticFeed.ps1 -OutFile $env:TEMP\horus-synthetic\feed-v1.xml
  .\scripts\New-SyntheticFeed.ps1 -OutFile $env:TEMP\horus-synthetic\feed-v2.xml -OmitUid wc-demo-3
#>
param(
    [Parameter(Mandatory = $true)][string]$OutFile,
    [string]$OmitUid = "",
    [int]$Count = 100
)

$ErrorActionPreference = "Stop"

# Refuse to write inside the repository: a feed file must never be committable.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$target = [System.IO.Path]::GetFullPath($OutFile)
if ($target.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to write a feed file inside the repository ($repoRoot). Choose a path outside it."
}
New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($target)) | Out-Null

$random = New-Object System.Random 20260924   # fixed seed: deterministic output
$prefixes = @("Zor","Tal","Quil","Ost","Hark","Bel","Dro","Fen","Gar","Hal","Kes","Lor","Mev","Nyr","Pell","Wex","Yar","Cor","Dun","Eld")
$suffixes = @("van","mesk","lon","brek","rel","wood","feld","mere","dath","kor","lin","sund","thar","vex","mond")
$countries = @("AE","EG","KE","NG","ZA","GH","SD","TZ","UG","MA")
$demoNames = @("zorvantalmesk","quillonmarbrek","ostrelvandermere")

function New-Word { $prefixes[$random.Next($prefixes.Length)] + $suffixes[$random.Next($suffixes.Length)] }
function Esc([string]$s) { [System.Security.SecurityElement]::Escape($s) }

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
[void]$sb.AppendLine('<!-- SYNTHETIC feed for local testing. Every name is invented. -->')
[void]$sb.AppendLine('<records xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">')

function Add-Record($uid, $category, $ei, $first, $last, $dob, $country, $passport) {
    if ($uid -eq $OmitUid) { return }
    [void]$sb.AppendLine("  <record uid=`"$uid`" category=`"$category`">")
    [void]$sb.AppendLine("    <person e-i=`"$ei`">")
    [void]$sb.AppendLine("      <first_name>$(Esc $first)</first_name>")
    [void]$sb.AppendLine("      <last_name>$(Esc $last)</last_name>")
    [void]$sb.AppendLine("    </person>")
    if ($dob) {
        [void]$sb.AppendLine("    <date_of_birth>")
        foreach ($part in @("year","month","day")) {
            if ($dob.ContainsKey($part)) { [void]$sb.AppendLine("      <$part>$($dob[$part])</$part>") }
        }
        [void]$sb.AppendLine("    </date_of_birth>")
    }
    [void]$sb.AppendLine("    <countries><country>$country</country></countries>")
    if ($passport) {
        [void]$sb.AppendLine("    <identification><passport>$passport</passport></identification>")
    }
    [void]$sb.AppendLine("    <details><further_information></further_information></details>")
    [void]$sb.AppendLine("  </record>")
}

# Four fixed demo entities the testing guide screens against. Demo 1 carries the single passport;
# demos 1 and 3 carry two of the 27 dates of birth.
Add-Record "wc-demo-1" "CAT-IND-SAN" "M" "Zorvan"   "Talmesk"  @{year=1980;month=5;day=17} "EG" "ZT1234567"
Add-Record "wc-demo-2" "CAT-ORG-SAN" "E" ""         "Sahara Nile Trading Company" $null "SD" $null
Add-Record "wc-demo-3" "CAT-IND-SAN" "F" "Quillon"  "Marbrek"  @{year=1972} "KE" $null
Add-Record "wc-demo-4" "CAT-IND-SAN" "M" "Ostrel"   "Vandermere" $null "NG" $null

# Remaining records: 4 already written above (counted whether or not one is omitted, so the
# population statistics stay put).
$remaining = $Count - 4
$dobLeft = 27 - 2                       # 27 dates of birth in total, two already used by demos
for ($i = 1; $i -le $remaining; $i++) {
    $uid = "wc-{0:D4}" -f $i
    $country = $countries[$random.Next($countries.Length)]
    do { $first = New-Word; $last = New-Word } while ($demoNames -contains (($first + $last).ToLower()))

    if ($i -le 5) {
        # A few non-individuals: organisation (name wholly in last_name) and a vessel.
        $category = if ($i -eq 5) { "CAT-VESSEL" } else { "CAT-ORG-SAN" }
        $orgName = if ($i -eq 5) { "MV $first" } else { "$first $last Holdings Ltd" }
        Add-Record $uid $category "E" "" $orgName $null $country $null
        continue
    }
    $dob = $null
    if ($dobLeft -gt 0 -and ($i % 3 -eq 0)) {
        $dob = @{ year = 1950 + $random.Next(50); month = 1 + $random.Next(12); day = 1 + $random.Next(28) }
        $dobLeft--
    }
    $gender = if ($random.Next(2) -eq 0) { "M" } else { "F" }
    Add-Record $uid "CAT-IND-SAN" $gender $first $last $dob $country $null
}
# If the modulo pass left dates of birth unassigned, that is a generator bug, not something to hide.
if ($dobLeft -gt 0) { throw "generator error: $dobLeft dates of birth were not placed" }

[void]$sb.AppendLine('</records>')
[System.IO.File]::WriteAllText($target, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
$written = ([regex]::Matches($sb.ToString(), "<record ")).Count
Write-Host "wrote $written records to $target"
