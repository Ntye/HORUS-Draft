<#
.SYNOPSIS
  Copies the FIRST N records of a large watchlist feed into a small, well-formed extract, and reports
  the file's STRUCTURE (element names only).

.DESCRIPTION
  Ingesting the real feed takes hours, and the checks that would catch a mapping error -- the
  reject-fraction check and the capability gate -- only run once the whole file has streamed. This
  script lets you prove the mapping on a handful of records first.

  It streams the source file, so it never loads it into memory, and it prints only STRUCTURAL facts:
  the root element name, the repeating record element name, and how many records it copied. It never
  prints record content. The extract is written wherever you say; keep it outside the repository and
  treat it as licensed data, exactly like the feed itself (CLAUDE.md section 3).

  WHY THE ELEMENT NAME MATTERS: the XML reader is wired for a specific repeating element name
  (bootstrap/IngestionConfig: new XmlFormatReader("record")). If this script reports a different
  name, ingestion would find zero records, and that must be fixed before the real load.

.PARAMETER SourceFile
  The real feed. Read only; never modified.

.PARAMETER OutFile
  Where to write the extract. Use a path outside this repository.

.PARAMETER Records
  How many records to copy. Default 200 -- enough for the adapter to exercise every branch, while
  staying small enough to eyeball.

.EXAMPLE
  .\scripts\New-FeedExtract.ps1 -SourceFile C:\horus-data\world-check.xml -OutFile C:\horus-data\extract-200.xml
#>
param(
    [Parameter(Mandatory = $true)][string]$SourceFile,
    [Parameter(Mandatory = $true)][string]$OutFile,
    [int]$Records = 200
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$target = [System.IO.Path]::GetFullPath($OutFile)
if ($target.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to write feed-derived data inside the repository ($repoRoot). Choose a path outside it."
}
if (-not (Test-Path $SourceFile)) { throw "Source file not found: $SourceFile" }

# Pass 1: read the structure with a real XML parser, so the element names reported are correct even
# when the file uses namespaces or unusual formatting. Nothing is printed except element names.
$settings = New-Object System.Xml.XmlReaderSettings
$settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit   # XXE off, as the ingester does
$settings.XmlResolver = $null
$settings.IgnoreWhitespace = $true
$reader = [System.Xml.XmlReader]::Create($SourceFile, $settings)
try {
    if (-not $reader.MoveToContent()) { throw "the file has no XML content" }
    $rootName = $reader.Name
    $recordName = $null
    while ($reader.Read()) {
        if ($reader.NodeType -eq [System.Xml.XmlNodeType]::Element) { $recordName = $reader.Name; break }
    }
} finally { $reader.Dispose() }
if (-not $recordName) { throw "found the root element <$rootName> but no child element inside it" }

Write-Host "structure:"
Write-Host "  root element   : <$rootName>"
Write-Host "  record element : <$recordName>"
$expected = "record"
if ($recordName -eq $expected) {
    Write-Host "  -> matches the configured XmlFormatReader(`"$expected`"). No change needed." -ForegroundColor Green
} else {
    Write-Host "  -> MISMATCH: ingestion is configured for <$expected> and would find ZERO records." -ForegroundColor Red
    Write-Host "     Fix bootstrap/src/main/java/horus/bootstrap/IngestionConfig.java before loading." -ForegroundColor Red
}

# Pass 2: stream out the first N records verbatim. Text-level copy, so the extract keeps the exact
# bytes the parser will later see (entities, attribute order, nil markers) rather than a re-serialised
# approximation. Chunked reads keep memory flat regardless of file size.
$openTag = "<$recordName"
$closeTag = "</$recordName>"
$readerStream = New-Object System.IO.StreamReader($SourceFile, [System.Text.Encoding]::UTF8, $true)
$writer = New-Object System.IO.StreamWriter($target, $false, (New-Object System.Text.UTF8Encoding($false)))
$copied = 0
try {
    $buffer = New-Object char[] 65536
    $window = New-Object System.Text.StringBuilder
    $started = $false
    $done = $false

    while (-not $done) {
        $read = $readerStream.Read($buffer, 0, $buffer.Length)
        if ($read -le 0) { break }
        [void]$window.Append($buffer, 0, $read)
        $text = $window.ToString()

        if (-not $started) {
            $first = $text.IndexOf($openTag)
            if ($first -lt 0) { continue }
            # Preamble: everything up to the first record (declaration + root open tag, with its
            # namespace declarations) is copied as-is so the extract parses the same way.
            $writer.Write($text.Substring(0, $first))
            $text = $text.Substring($first)
            $started = $true
        }

        while ($true) {
            $end = $text.IndexOf($closeTag)
            if ($end -lt 0) { break }
            $end += $closeTag.Length
            $writer.Write($text.Substring(0, $end))
            $writer.Write("`n")
            $text = $text.Substring($end)
            $copied++
            if ($copied -ge $Records) { $done = $true; break }
        }
        [void]$window.Clear()
        [void]$window.Append($text)
    }
    $writer.Write("</$rootName>`n")
} finally {
    $writer.Dispose()
    $readerStream.Dispose()
}

if ($copied -eq 0) { throw "copied no records -- the file may use self-closing <$recordName/> elements" }

# Prove the extract is well formed before anyone tries to ingest it.
$check = [System.Xml.XmlReader]::Create($target, $settings)
try { while ($check.Read()) { } } finally { $check.Dispose() }

Write-Host "wrote $copied records to $target (well-formed XML)"
Write-Host ""
Write-Host "Next: load the EXTRACT under a throwaway source id so it never touches the real source's"
Write-Host "history, confirm the mapping, then reset and load the full feed. See testing-guide section 16."
