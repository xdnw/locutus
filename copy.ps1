param(
    [ValidateSet('Copy', 'Print')]
    [string]$OutputMode = 'Copy',

    [Parameter(Mandatory = $true, Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$Entries
)

function Expand-EntryArguments {
    param([string[]]$RawEntries)

    if ($RawEntries.Count -ne 1) {
        return $RawEntries
    }

    $single = $RawEntries[0]

    if ([string]::IsNullOrWhiteSpace($single)) {
        return $RawEntries
    }

    if (Test-Path -LiteralPath $single) {
        return $RawEntries
    }

    if ($single -notmatch '\s') {
        return $RawEntries
    }

    $matches = [regex]::Matches($single, '"(?:[^"]|"")*"|\S+')
    if ($matches.Count -le 1) {
        return $RawEntries
    }

    $expanded = @()
    foreach ($match in $matches) {
        $token = $match.Value
        if ($token.StartsWith('"') -and $token.EndsWith('"')) {
            $token = $token.Substring(1, $token.Length - 2).Replace('""', '"')
        }
        $expanded += $token
    }

    return $expanded
}

function Is-JavaIdentifierChar {
    param([char]$Char)
    return ([char]::IsLetterOrDigit($Char) -or $Char -eq '_' -or $Char -eq '$')
}

function Starts-WithWord {
    param(
        [string]$Text,
        [int]$Index,
        [string]$Word
    )

    if ($Index -lt 0 -or ($Index + $Word.Length) -gt $Text.Length) {
        return $false
    }

    if ($Text.Substring($Index, $Word.Length) -cne $Word) {
        return $false
    }

    $after = $Index + $Word.Length
    if ($after -lt $Text.Length -and (Is-JavaIdentifierChar $Text[$after])) {
        return $false
    }

    return $true
}

function Remove-JavaComments {
    param([string]$Text)

    $sb = New-Object System.Text.StringBuilder
    $state = 'Normal'

    for ($i = 0; $i -lt $Text.Length; $i++) {
        $c = $Text[$i]
        $next = if ($i + 1 -lt $Text.Length) { $Text[$i + 1] } else { [char]0 }
        $next2 = if ($i + 2 -lt $Text.Length) { $Text[$i + 2] } else { [char]0 }

        switch ($state) {
            'Normal' {
                if ($c -eq '"' -and $next -eq '"' -and $next2 -eq '"') {
                    [void]$sb.Append('"""')
                    $i += 2
                    $state = 'TextBlock'
                    continue
                }

                if ($c -eq '"') {
                    [void]$sb.Append($c)
                    $state = 'String'
                    continue
                }

                if ($c -eq "'") {
                    [void]$sb.Append($c)
                    $state = 'Char'
                    continue
                }

                if ($c -eq '/' -and $next -eq '/') {
                    [void]$sb.Append('  ')
                    $i++
                    $state = 'LineComment'
                    continue
                }

                if ($c -eq '/' -and $next -eq '*') {
                    [void]$sb.Append('  ')
                    $i++
                    $state = 'BlockComment'
                    continue
                }

                [void]$sb.Append($c)
            }

            'LineComment' {
                if ($c -eq "`r" -or $c -eq "`n") {
                    [void]$sb.Append($c)
                    $state = 'Normal'
                }
                else {
                    [void]$sb.Append(' ')
                }
            }

            'BlockComment' {
                if ($c -eq '*' -and $next -eq '/') {
                    [void]$sb.Append('  ')
                    $i++
                    $state = 'Normal'
                }
                elseif ($c -eq "`r" -or $c -eq "`n") {
                    [void]$sb.Append($c)
                }
                else {
                    [void]$sb.Append(' ')
                }
            }

            'String' {
                [void]$sb.Append($c)

                if ($c -eq '\' -and $i + 1 -lt $Text.Length) {
                    $i++
                    [void]$sb.Append($Text[$i])
                }
                elseif ($c -eq '"') {
                    $state = 'Normal'
                }
            }

            'Char' {
                [void]$sb.Append($c)

                if ($c -eq '\' -and $i + 1 -lt $Text.Length) {
                    $i++
                    [void]$sb.Append($Text[$i])
                }
                elseif ($c -eq "'") {
                    $state = 'Normal'
                }
            }

            'TextBlock' {
                if ($c -eq '"' -and $next -eq '"' -and $next2 -eq '"') {
                    [void]$sb.Append('"""')
                    $i += 2
                    $state = 'Normal'
                }
                else {
                    [void]$sb.Append($c)
                }
            }
        }
    }

    return $sb.ToString()
}

function Find-MatchingDelimiter {
    param(
        [string]$Text,
        [int]$OpenIndex,
        [char]$OpenChar,
        [char]$CloseChar
    )

    $depth = 1
    $state = 'Normal'

    for ($i = $OpenIndex + 1; $i -lt $Text.Length; $i++) {
        $c = $Text[$i]
        $next = if ($i + 1 -lt $Text.Length) { $Text[$i + 1] } else { [char]0 }
        $next2 = if ($i + 2 -lt $Text.Length) { $Text[$i + 2] } else { [char]0 }

        switch ($state) {
            'Normal' {
                if ($c -eq '"' -and $next -eq '"' -and $next2 -eq '"') {
                    $i += 2
                    $state = 'TextBlock'
                    continue
                }

                if ($c -eq '"') {
                    $state = 'String'
                    continue
                }

                if ($c -eq "'") {
                    $state = 'Char'
                    continue
                }

                if ($c -eq $OpenChar) {
                    $depth++
                    continue
                }

                if ($c -eq $CloseChar) {
                    $depth--
                    if ($depth -eq 0) {
                        return $i
                    }
                }
            }

            'String' {
                if ($c -eq '\' -and $i + 1 -lt $Text.Length) {
                    $i++
                }
                elseif ($c -eq '"') {
                    $state = 'Normal'
                }
            }

            'Char' {
                if ($c -eq '\' -and $i + 1 -lt $Text.Length) {
                    $i++
                }
                elseif ($c -eq "'") {
                    $state = 'Normal'
                }
            }

            'TextBlock' {
                if ($c -eq '"' -and $next -eq '"' -and $next2 -eq '"') {
                    $i += 2
                    $state = 'Normal'
                }
            }
        }
    }

    return -1
}

function Get-LineStartIndex {
    param(
        [string]$Text,
        [int]$Index
    )

    $i = $Index
    while ($i -gt 0 -and $Text[$i - 1] -ne "`r" -and $Text[$i - 1] -ne "`n") {
        $i--
    }
    return $i
}

function Get-PreviousLineStart {
    param(
        [string]$Text,
        [int]$CurrentLineStart
    )

    if ($CurrentLineStart -le 0) {
        return -1
    }

    $i = $CurrentLineStart - 1

    if ($Text[$i] -eq "`n" -and $i -gt 0 -and $Text[$i - 1] -eq "`r") {
        $i--
    }

    while ($i -gt 0 -and $Text[$i - 1] -ne "`r" -and $Text[$i - 1] -ne "`n") {
        $i--
    }

    return $i
}

function Get-LineTextAtStart {
    param(
        [string]$Text,
        [int]$LineStart
    )

    $i = $LineStart
    while ($i -lt $Text.Length -and $Text[$i] -ne "`r" -and $Text[$i] -ne "`n") {
        $i++
    }

    return $Text.Substring($LineStart, $i - $LineStart)
}

function Get-DeclarationStartIndex {
    param(
        [string]$Text,
        [int]$MethodNameIndex
    )

    $start = Get-LineStartIndex $Text $MethodNameIndex

    while ($true) {
        $prevStart = Get-PreviousLineStart $Text $start
        if ($prevStart -lt 0) {
            break
        }

        $prevLine = Get-LineTextAtStart $Text $prevStart
        $trimmed = $prevLine.Trim()

        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            break
        }

        if ($trimmed -match '[;{}]\s*$') {
            break
        }

        $start = $prevStart
    }

    return $start
}

function Is-ProbableMethodDeclaration {
    param(
        [string]$Text,
        [int]$MethodNameIndex
    )

    $i = $MethodNameIndex - 1
    while ($i -ge 0 -and [char]::IsWhiteSpace($Text[$i])) {
        $i--
    }

    if ($i -ge 0 -and ($Text[$i] -eq '.' -or $Text[$i] -eq '@')) {
        return $false
    }

    $end = $i
    while ($i -ge 0 -and (Is-JavaIdentifierChar $Text[$i])) {
        $i--
    }

    if ($end -ge ($i + 1)) {
        $word = $Text.Substring($i + 1, $end - $i)
        if ($word -in @('new', 'return', 'throw', 'case', 'if', 'for', 'while', 'switch', 'catch', 'this', 'super')) {
            return $false
        }
    }

    return $true
}

function Get-MethodDeclarations {
    param([string]$Text)

    $clean = Remove-JavaComments $Text
    $pattern = '(?<![A-Za-z0-9_$])([A-Za-z_$][A-Za-z0-9_$]*)\s*\('
    $matches = [regex]::Matches($clean, $pattern)

    $declarations = New-Object System.Collections.ArrayList
    $seen = @{}

    foreach ($match in $matches) {
        $name = $match.Groups[1].Value
        $nameIndex = $match.Groups[1].Index

        if (-not (Is-ProbableMethodDeclaration $clean $nameIndex)) {
            continue
        }

        $openParenIndex = $match.Index + $match.Value.LastIndexOf('(')
        $closeParenIndex = Find-MatchingDelimiter $clean $openParenIndex '(' ')'
        if ($closeParenIndex -lt 0) {
            continue
        }

        $scan = $closeParenIndex + 1
        while ($scan -lt $clean.Length -and [char]::IsWhiteSpace($clean[$scan])) {
            $scan++
        }

        if (Starts-WithWord $clean $scan 'throws') {
            $scan += 6
            while ($scan -lt $clean.Length -and $clean[$scan] -ne '{' -and $clean[$scan] -ne ';') {
                $scan++
            }
        }

        while ($scan -lt $clean.Length -and [char]::IsWhiteSpace($clean[$scan])) {
            $scan++
        }

        if ($scan -ge $clean.Length) {
            continue
        }

        $terminator = $clean[$scan]
        if ($terminator -ne '{' -and $terminator -ne ';') {
            continue
        }

        $startIndex = Get-DeclarationStartIndex $clean $nameIndex
        $signature = $clean.Substring($startIndex, $scan - $startIndex).Trim()
        if ([string]::IsNullOrWhiteSpace($signature)) {
            continue
        }

        $hasBody = $false
        $body = $null
        $endIndex = $scan

        if ($terminator -eq '{') {
            $closeBraceIndex = Find-MatchingDelimiter $clean $scan '{' '}'
            if ($closeBraceIndex -lt 0) {
                continue
            }

            $hasBody = $true
            $body = $clean.Substring($startIndex, $closeBraceIndex - $startIndex + 1).Trim()
            $endIndex = $closeBraceIndex
        }

        $dedupeKey = "${startIndex}:${endIndex}:${name}:${terminator}"
        if ($seen.ContainsKey($dedupeKey)) {
            continue
        }

        $seen[$dedupeKey] = $true
        [void]$declarations.Add([pscustomobject]@{
            Name      = $name
            Signature = $signature
            Body      = $body
            HasBody   = $hasBody
            Start     = $startIndex
        })
    }

    return @($declarations | Sort-Object Start)
}

function Get-ProcessedCodeLines {
    param([string]$Text)

    $processed = @()
    $lines = $Text -split "`r?`n"

    foreach ($line in $lines) {
        $trimmedLine = $line.Trim()

        if ([string]::IsNullOrWhiteSpace($trimmedLine)) {
            continue
        }

        if ($trimmedLine -match '^(package|import)\b') {
            continue
        }

        $processed += $trimmedLine
    }

    return $processed
}

function Get-NormalizedSignatureLine {
    param([string]$Text)

    $lines = @()
    foreach ($line in ($Text -split "`r?`n")) {
        $trimmed = $line.Trim()
        if (-not [string]::IsNullOrWhiteSpace($trimmed)) {
            $lines += $trimmed
        }
    }

    $joined = ($lines -join ' ')
    $joined = [regex]::Replace($joined, '\s+', ' ').Trim()
    $joined = $joined.TrimEnd('{', ';').Trim()

    return $joined
}

function Get-DisplayPath {
    param([string]$ResolvedPath)

    $fullPath = [System.IO.Path]::GetFullPath($ResolvedPath)
    $cwd = [System.IO.Path]::GetFullPath((Get-Location).ProviderPath)

    try {
        $cwdUri = New-Object System.Uri(($cwd.TrimEnd('\') + '\'))
        $fileUri = New-Object System.Uri($fullPath)

        if ($cwdUri.IsBaseOf($fileUri)) {
            $relative = [System.Uri]::UnescapeDataString($cwdUri.MakeRelativeUri($fileUri).ToString())
            return $relative.Replace('/', '/')
        }
    }
    catch {
    }

    return $fullPath.Replace('\', '/')
}

function Parse-InputSpec {
    param([string]$Entry)

    if (Test-Path -LiteralPath $Entry) {
        return [pscustomobject]@{
            Path = $Entry
            Mode = 'File'
            Name = $null
        }
    }

    $hashIndex = $Entry.LastIndexOf('#')
    if ($hashIndex -gt 0) {
        $pathPart = $Entry.Substring(0, $hashIndex)
        $methodPart = $Entry.Substring($hashIndex + 1)

        if ((Test-Path -LiteralPath $pathPart) -and $methodPart -match '^[A-Za-z_$][A-Za-z0-9_$]*$') {
            return [pscustomobject]@{
                Path = $pathPart
                Mode = 'MethodBody'
                Name = $methodPart
            }
        }
    }

    $atIndex = $Entry.LastIndexOf('@')
    if ($atIndex -gt 0) {
        $pathPart = $Entry.Substring(0, $atIndex)
        $methodPart = $Entry.Substring($atIndex + 1)

        if (Test-Path -LiteralPath $pathPart) {
            if ($methodPart -eq '*') {
                return [pscustomobject]@{
                    Path = $pathPart
                    Mode = 'AllSignatures'
                    Name = $null
                }
            }

            if ($methodPart -match '^[A-Za-z_$][A-Za-z0-9_$]*$') {
                return [pscustomobject]@{
                    Path = $pathPart
                    Mode = 'MethodSignature'
                    Name = $methodPart
                }
            }
        }
    }

    return [pscustomobject]@{
        Path = $Entry
        Mode = 'File'
        Name = $null
    }
}

function Get-RequestedChunksForFile {
    param(
        [string]$Text,
        [object[]]$Specs,
        [string]$DisplayPath
    )

    $chunks = New-Object System.Collections.ArrayList
    $seenChunks = @{}
    $declarations = $null

    $hasFullFile = $false
    foreach ($spec in $Specs) {
        if ($spec.Mode -eq 'File') {
            $hasFullFile = $true
            break
        }
    }

    if ($hasFullFile) {
        $lines = @(Get-ProcessedCodeLines (Remove-JavaComments $Text))
        if ($lines.Count -gt 0) {
            [void]$chunks.Add($lines)
        }
        return @($chunks)
    }

    foreach ($spec in $Specs) {
        if ($null -eq $declarations) {
            $declarations = @(Get-MethodDeclarations $Text)
        }

        switch ($spec.Mode) {
            'MethodBody' {
                $matches = @($declarations | Where-Object { $_.Name -eq $spec.Name -and $_.HasBody })
                if ($matches.Count -eq 0) {
                    Write-Warning "Method body '$($spec.Name)' not found in file: $DisplayPath"
                    continue
                }

                foreach ($match in $matches) {
                    $lines = @(Get-ProcessedCodeLines $match.Body)
                    if ($lines.Count -eq 0) {
                        continue
                    }

                    $key = 'B:' + ($lines -join "`n")
                    if (-not $seenChunks.ContainsKey($key)) {
                        $seenChunks[$key] = $true
                        [void]$chunks.Add($lines)
                    }
                }
            }

            'MethodSignature' {
                $matches = @($declarations | Where-Object { $_.Name -eq $spec.Name })
                if ($matches.Count -eq 0) {
                    Write-Warning "Method signature '$($spec.Name)' not found in file: $DisplayPath"
                    continue
                }

                foreach ($match in $matches) {
                    $line = Get-NormalizedSignatureLine $match.Signature
                    if ([string]::IsNullOrWhiteSpace($line)) {
                        continue
                    }

                    $key = 'S:' + $line
                    if (-not $seenChunks.ContainsKey($key)) {
                        $seenChunks[$key] = $true
                        [void]$chunks.Add(@($line))
                    }
                }
            }

            'AllSignatures' {
                if ($declarations.Count -eq 0) {
                    Write-Warning "No method signatures found in file: $DisplayPath"
                    continue
                }

                foreach ($match in $declarations) {
                    $line = Get-NormalizedSignatureLine $match.Signature
                    if ([string]::IsNullOrWhiteSpace($line)) {
                        continue
                    }

                    $key = 'S:' + $line
                    if (-not $seenChunks.ContainsKey($key)) {
                        $seenChunks[$key] = $true
                        [void]$chunks.Add(@($line))
                    }
                }
            }
        }
    }

    return @($chunks)
}

$Entries = @(Expand-EntryArguments $Entries)

$fileGroups = @{}
$fileOrder = New-Object System.Collections.ArrayList
$processedEntryCount = 0

foreach ($entry in $Entries) {
    $spec = Parse-InputSpec $entry

    if (-not (Test-Path -LiteralPath $spec.Path)) {
        Write-Warning "File not found: $($spec.Path)"
        continue
    }

    $resolvedPath = (Resolve-Path -LiteralPath $spec.Path).ProviderPath
    if (-not $fileGroups.ContainsKey($resolvedPath)) {
        $group = [pscustomobject]@{
            Path        = $resolvedPath
            DisplayPath = Get-DisplayPath $resolvedPath
            Specs       = New-Object System.Collections.ArrayList
        }

        $fileGroups[$resolvedPath] = $group
        [void]$fileOrder.Add($group)
    }

    [void]$fileGroups[$resolvedPath].Specs.Add($spec)
    $processedEntryCount++
}

$allOutputLines = New-Object System.Collections.ArrayList
$emittedFileCount = 0
$emittedChunkCount = 0

foreach ($group in $fileOrder) {
    $content = Get-Content -LiteralPath $group.Path -Raw
    $chunks = @(Get-RequestedChunksForFile -Text $content -Specs @($group.Specs) -DisplayPath $group.DisplayPath)

    if ($chunks.Count -eq 0) {
        continue
    }

    [void]$allOutputLines.Add("@@ $($group.DisplayPath)")

    $firstChunk = $true
    foreach ($chunk in $chunks) {
        if (-not $firstChunk) {
            [void]$allOutputLines.Add("")
        }

        foreach ($line in $chunk) {
            [void]$allOutputLines.Add($line)
        }

        $firstChunk = $false
        $emittedChunkCount++
    }

    [void]$allOutputLines.Add("@@END")
    [void]$allOutputLines.Add("")
    $emittedFileCount++
}

while ($allOutputLines.Count -gt 0 -and [string]::IsNullOrWhiteSpace($allOutputLines[$allOutputLines.Count - 1])) {
    $allOutputLines.RemoveAt($allOutputLines.Count - 1)
}

$finalOutput = $allOutputLines -join [Environment]::NewLine

if ([string]::IsNullOrWhiteSpace($finalOutput)) {
    Write-Warning "No output was produced."
    exit 1
}

if ($OutputMode -eq 'Copy') {
    $finalOutput | Set-Clipboard
    Write-Host "Processed $processedEntryCount input entr$(if ($processedEntryCount -eq 1) {'y'} else {'ies'}), emitted $emittedFileCount file block$(if ($emittedFileCount -eq 1) {''} else {'s'}), and copied $emittedChunkCount chunk$(if ($emittedChunkCount -eq 1) {''} else {'s'}) to the clipboard." -ForegroundColor Green
}
else {
    Write-Output $finalOutput
}