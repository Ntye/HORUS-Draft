<#
.SYNOPSIS
  Reports the STRUCTURE of a watchlist feed: every element path and attribute the reader can see,
  how often each is populated, and whether the World-Check adapter currently asks for it.

.DESCRIPTION
  CLAUDE.md section 6 establishes which SIGNALS matter in the feed (entity type from person/@e-i,
  the composed primary name, DOB coverage, and so on) but never the element PATHS that carry them.
  The adapter's paths were therefore invented alongside the synthetic fixture -- the fixture file
  says so itself -- and a path that does not exist yields an absent slot or a rejected record, at
  scale, hours into a load.

  This script closes that gap without ever exposing feed content. It prints:
    * each element path, and the number and percentage of records where it holds a non-empty value
    * each attribute key, the same way
    * for the three CODE attributes only (@e-i, @category, @sub-category), the distinct values

  WHY THE CODE VALUES ARE SAFE TO PRINT: they are classification vocabularies, not data about a
  person -- section 6 already quotes @e-i's values and states that the feed has 28 categories. They
  are also the one thing the adapter cannot be corrected without: E_CATEGORY_TYPES in
  WorldCheckAdapter is a set of invented placeholders. Everything else is counted, never shown.

  PATH SEMANTICS MIRROR THE PRODUCTION READER (core XmlFormatReader): paths are the element stack
  joined with "/", relative to and EXCLUDING the record element; attributes are "<path>@<name>";
  xsi:nil="true" counts as an asserted absence, not a value; and a parent's text is discarded when
  a child element opens. So a path reported here is a path the adapter can ask for, exactly.

.PARAMETER SourceFile
  The feed, or an extract of it. Read only, streamed, never modified.

.PARAMETER Records
  How many records to inspect. 0 (the default) means the whole file -- the only way to get coverage
  percentages comparable with section 6, at roughly the cost of one structural pass (~4 minutes on
  the real feed).

.EXAMPLE
  .\scripts\New-FeedPathInventory.ps1 -SourceFile $env:HORUS_FEED_PATH -Records 5000
#>
param(
    [Parameter(Mandatory = $true)][string]$SourceFile,
    [int]$Records = 0,
    [string]$RecordElement = "record"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $SourceFile)) { throw "Source file not found: $SourceFile" }

# Exactly what WorldCheckAdapter.toCanonical asks for today. Kept here so the report can say MISS
# against the real file, instead of leaving a reader to diff two lists by eye.
$adapterPaths = @(
    "person/first_name", "person/last_name",
    "date_of_birth/year", "date_of_birth/month", "date_of_birth/day",
    "date_of_birth/age", "date_of_birth/as_of_date", "date_of_birth/deceased",
    "countries/country", "identification/passport", "details/further_information"
)
$adapterAttrs = @(
    "@uid", "@category", "person@e-i",
    "locations/location@city", "locations/location@country", "locations/location@state"
)
$codeAttrNames = @("e-i", "category", "sub-category")

# Element paths whose text is also a vocabulary rather than data about a person. Countries settle
# an open question the adapter cannot answer from path names alone -- whether the feed writes ISO
# codes or country names, which is what CountryComparator ends up comparing a query against -- and
# keywords are list-source codes (OFAC, UKHMT). Both are capped below, so a path that turns out to
# hold free text reports a count and nothing else.
$codeElementPaths = @("details/countries/country", "details/keywords/keyword")
$maxDistinctValues = 400
$xsi = "http://www.w3.org/2001/XMLSchema-instance"

$settings = New-Object System.Xml.XmlReaderSettings
$settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit   # XXE off, as the ingester does
$settings.XmlResolver = $null
$settings.IgnoreWhitespace = $true
$settings.IgnoreComments = $true

$pathCounts = @{}   # element path -> records holding a non-empty value
$attrCounts = @{}   # attribute key -> records holding a non-empty value
$nilCounts = @{}    # element path -> records where xsi:nil="true"
$codeValues = @{}   # code attribute key -> (value -> count)
$recordCount = 0

$reader = [System.Xml.XmlReader]::Create($SourceFile, $settings)
try {
    $inRecord = $false
    $stack = New-Object System.Collections.Generic.List[string]
    $recPaths = New-Object System.Collections.Generic.HashSet[string]
    $recAttrs = New-Object System.Collections.Generic.HashSet[string]
    $recNils = New-Object System.Collections.Generic.HashSet[string]
    $hasText = $false
    $nilHere = $false
    $stop = $false
    # Held only so an allow-listed vocabulary path can report its distinct values; cleared on every
    # element boundary, exactly as the production reader clears its own text buffer.
    $elementText = New-Object System.Text.StringBuilder

    while ((-not $stop) -and $reader.Read()) {
        $type = $reader.NodeType

        if ($type -eq [System.Xml.XmlNodeType]::Element) {
            $local = $reader.LocalName
            $empty = $reader.IsEmptyElement

            if (-not $inRecord) {
                if ($local -ne $RecordElement) { continue }
                $inRecord = $true
                $stack.Clear()
                [void]$recPaths.Clear(); [void]$recAttrs.Clear(); [void]$recNils.Clear()
            }
            else {
                $stack.Add($local)
            }

            $path = [string]::Join("/", $stack)
            $nilHere = $false

            if ($reader.HasAttributes) {
                for ($i = 0; $i -lt $reader.AttributeCount; $i++) {
                    [void]$reader.MoveToAttribute($i)
                    $an = $reader.LocalName
                    $av = $reader.Value
                    if ($reader.NamespaceURI -eq $xsi) {
                        if ($an -eq "nil" -and $av -eq "true") { $nilHere = $true }
                        continue
                    }
                    if (-not [string]::IsNullOrWhiteSpace($av)) {
                        $key = "$path@$an"
                        [void]$recAttrs.Add($key)
                        # Code vocabularies only -- see the safety note in the header.
                        if (($codeAttrNames -contains $an) -and ($av.Length -le 48)) {
                            if (-not $codeValues.ContainsKey($key)) { $codeValues[$key] = @{} }
                            $codeValues[$key][$av] = 1 + [int]$codeValues[$key][$av]
                        }
                    }
                }
                [void]$reader.MoveToElement()
            }

            $hasText = $false
            [void]$elementText.Clear()

            # XmlReader raises no EndElement for <x/>, but the production StAX reader does, so
            # self-closing elements are closed here to keep the two in step.
            if ($empty) {
                if ($nilHere) { [void]$recNils.Add($path) }
                if ($stack.Count -gt 0) { $stack.RemoveAt($stack.Count - 1) } else { $inRecord = $false }
                $nilHere = $false
            }
        }
        elseif ($type -eq [System.Xml.XmlNodeType]::Text -or $type -eq [System.Xml.XmlNodeType]::CDATA) {
            if ($inRecord -and -not [string]::IsNullOrWhiteSpace($reader.Value)) {
                $hasText = $true
                if ($elementText.Length -le 64) { [void]$elementText.Append($reader.Value) }
            }
        }
        elseif ($type -eq [System.Xml.XmlNodeType]::EndElement) {
            if (-not $inRecord) { continue }

            $path = [string]::Join("/", $stack)
            if ($nilHere) { [void]$recNils.Add($path) }
            elseif ($hasText) {
                [void]$recPaths.Add($path)
                if (($codeElementPaths -contains $path) -and $elementText.Length -le 48) {
                    $v = $elementText.ToString().Trim()
                    if ($v.Length -gt 0) {
                        if (-not $codeValues.ContainsKey($path)) { $codeValues[$path] = @{} }
                        if ($codeValues[$path].Count -lt $maxDistinctValues -or $codeValues[$path].ContainsKey($v)) {
                            $codeValues[$path][$v] = 1 + [int]$codeValues[$path][$v]
                        }
                    }
                }
            }
            $hasText = $false
            $nilHere = $false
            [void]$elementText.Clear()

            if ($stack.Count -eq 0) {
                # End of a record: fold its sets into the totals, so a path that repeats inside one
                # record still counts once -- these are record coverage figures, not element counts.
                $recordCount++
                foreach ($p in $recPaths) { $pathCounts[$p] = 1 + [int]$pathCounts[$p] }
                foreach ($a in $recAttrs) { $attrCounts[$a] = 1 + [int]$attrCounts[$a] }
                foreach ($n in $recNils) { $nilCounts[$n] = 1 + [int]$nilCounts[$n] }
                $inRecord = $false
                if (($recordCount % 250000) -eq 0) {
                    Write-Host ("  ... {0:N0} records" -f $recordCount) -ForegroundColor DarkGray
                }
                if ($Records -gt 0 -and $recordCount -ge $Records) { $stop = $true }
            }
            else {
                $stack.RemoveAt($stack.Count - 1)
            }
        }
    }
}
finally { $reader.Dispose() }

if ($recordCount -eq 0) { throw "no <$RecordElement> elements found -- is -RecordElement right?" }

function Write-CoverageRow {
    param([string]$Key, [int]$Count, [int]$Total, [string[]]$Wanted, [hashtable]$Nil)
    $pct = [math]::Round(100.0 * $Count / $Total, 2)
    $nilNote = ""
    if ($Nil -and $Nil.ContainsKey($Key)) { $nilNote = "  (nil on $($Nil[$Key]))" }
    $mark = ""
    if ($Wanted -contains $Key) { $mark = "   <= adapter reads this" }
    Write-Host ("{0,-46} {1,10} {2,7}%{3}{4}" -f $Key, $Count, $pct, $nilNote, $mark)
}

Write-Host ""
Write-Host "records inspected: $recordCount"
Write-Host ""
Write-Host "ELEMENT PATHS (records holding a non-empty value)"
Write-Host ("-" * 96)
foreach ($k in ($pathCounts.Keys | Sort-Object)) {
    Write-CoverageRow -Key $k -Count $pathCounts[$k] -Total $recordCount -Wanted $adapterPaths -Nil $nilCounts
}

Write-Host ""
Write-Host "ATTRIBUTES (records holding a non-empty value)"
Write-Host ("-" * 96)
foreach ($k in ($attrCounts.Keys | Sort-Object)) {
    Write-CoverageRow -Key $k -Count $attrCounts[$k] -Total $recordCount -Wanted $adapterAttrs -Nil $null
}

Write-Host ""
Write-Host "PATHS THE ADAPTER ASKS FOR BUT THIS FILE DOES NOT HAVE"
Write-Host ("-" * 96)
$missing = @()
foreach ($p in $adapterPaths) { if (-not $pathCounts.ContainsKey($p)) { $missing += "element   $p" } }
foreach ($a in $adapterAttrs) { if (-not $attrCounts.ContainsKey($a)) { $missing += "attribute $a" } }
if ($missing.Count -eq 0) {
    Write-Host "  none -- every path the adapter reads is present" -ForegroundColor Green
}
else {
    foreach ($m in $missing) { Write-Host "  MISS  $m" -ForegroundColor Red }
}

Write-Host ""
Write-Host "CODE VOCABULARIES (classification codes, not personal data -- see the header)"
Write-Host ("-" * 96)
foreach ($k in ($codeValues.Keys | Sort-Object)) {
    $vals = $codeValues[$k]
    Write-Host "$k  ($($vals.Count) distinct)"
    foreach ($v in ($vals.GetEnumerator() | Sort-Object -Property Value -Descending)) {
        Write-Host ("    {0,-44} {1,10}" -f $v.Key, $v.Value)
    }
}

Write-Host ""
Write-Host "Paste this whole report. It holds path names, counts and classification codes only."
