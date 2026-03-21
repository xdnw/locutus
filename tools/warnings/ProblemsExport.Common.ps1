$script:ProblemsRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$script:ProblemsRepoRootFull = [System.IO.Path]::GetFullPath($script:ProblemsRepoRoot)
if (-not $script:ProblemsRepoRootFull.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
    $script:ProblemsRepoRootFull += [System.IO.Path]::DirectorySeparatorChar
}
$script:ProblemsGeneratedPrefixes = @(
    "build/generated/",
    "build/generated-sources/",
    "build/generated-src/",
    "bin/generated-sources/",
    "jte-classes/"
)

function Resolve-ProblemsInputPath {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $script:ProblemsRepoRoot $Path))
}

function Get-ProblemPropertyValue {
    param(
        [Parameter(Mandatory)]
        [object]$Object,

        [Parameter(Mandatory)]
        [string[]]$Names
    )

    if ($null -eq $Object) {
        return $null
    }

    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]
        if ($null -ne $property) {
            return $property.Value
        }
    }

    return $null
}

function Convert-ProblemsSeverity {
    param(
        [Parameter(Mandatory)]
        [object]$Value
    )

    if ($null -eq $Value) {
        return ""
    }

    if ($Value -is [string]) {
        return $Value.Trim().ToLowerInvariant()
    }

    if ($Value -is [int] -or $Value -is [long]) {
        switch ([int]$Value) {
            8 { return "error" }
            4 { return "warning" }
            2 { return "info" }
            1 { return "hint" }
            default { return ([int]$Value).ToString() }
        }
    }

    return $Value.ToString().Trim().ToLowerInvariant()
}

function Get-ProblemsResourcePath {
    param(
        [Parameter(Mandatory)]
        [object]$Diagnostic
    )

    $resource = Get-ProblemPropertyValue -Object $Diagnostic -Names @("resource", "file", "filePath", "path", "uri")
    if ($null -eq $resource) {
        return $null
    }

    if ($resource -is [string]) {
        return $resource
    }

    $nested = Get-ProblemPropertyValue -Object $resource -Names @("fsPath", "path", "uri", "external")
    if ($null -ne $nested) {
        return $nested
    }

    return $resource.ToString()
}

function Convert-ToRelativeProblemPath {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $candidate = $Path.Trim()
    if ($candidate.StartsWith("file:///", [System.StringComparison]::OrdinalIgnoreCase)) {
        $candidate = ([System.Uri]$candidate).LocalPath
    }

    $fullPath = Resolve-ProblemsInputPath -Path $candidate
    if ($fullPath.StartsWith($script:ProblemsRepoRootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        return ($fullPath.Substring($script:ProblemsRepoRootFull.Length) -replace '\\', '/')
    }

    return ($fullPath -replace '\\', '/')
}

function Get-ShortProblemPath {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $segments = @($Path -split '/')
    if ($segments.Count -le 6) {
        return $Path
    }

    $head = $segments[0..1]
    $tail = $segments[($segments.Count - 3)..($segments.Count - 1)]
    return (($head + '...' + $tail) -join '/')
}

function Get-ProblemDirectory {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $lastSlash = $Path.LastIndexOf('/')
    if ($lastSlash -lt 0) {
        return ""
    }

    return $Path.Substring(0, $lastSlash)
}

function Get-ShortProblemDirectory {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $directory = Get-ProblemDirectory -Path $Path
    if ([string]::IsNullOrWhiteSpace($directory)) {
        return ""
    }

    return Get-ShortProblemPath -Path $directory
}

function Get-ProblemLocation {
    param(
        [Parameter(Mandatory)]
        [object]$Diagnostic
    )

    $line = Get-ProblemPropertyValue -Object $Diagnostic -Names @("startLineNumber", "line")
    $range = Get-ProblemPropertyValue -Object $Diagnostic -Names @("range")
    if ($null -eq $line -and $null -ne $range) {
        $rangeStart = Get-ProblemPropertyValue -Object $range -Names @("start")
        if ($null -ne $rangeStart) {
            $line = Get-ProblemPropertyValue -Object $rangeStart -Names @("line")
            if ($null -ne $line) {
                $line = [int]$line + 1
            }
        }
    }

    if ($null -eq $line) {
        $line = 1
    }

    return [int]$line
}

function Get-ProblemMessageSummary {
    param(
        [Parameter(Mandatory)]
        [string]$Message
    )

    foreach ($line in ($Message -split "`r?`n")) {
        $trimmed = $line.Trim()
        if (-not [string]::IsNullOrWhiteSpace($trimmed)) {
            return $trimmed
        }
    }

    return $Message.Trim()
}

function Test-IsGeneratedProblemPath {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $normalized = $Path -replace '\\', '/'
    foreach ($prefix in $script:ProblemsGeneratedPrefixes) {
        if ($normalized.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $true
        }
    }

    return $false
}

function Get-ProblemRecordsFromNode {
    param(
        [Parameter(Mandatory)]
        [object]$Node
    )

    $records = [System.Collections.Generic.List[object]]::new()

    if ($null -eq $Node) {
        return $records.ToArray()
    }

    if ($Node -is [System.Collections.IEnumerable] -and -not ($Node -is [string])) {
        foreach ($item in $Node) {
            foreach ($record in Get-ProblemRecordsFromNode -Node $item) {
                $records.Add($record)
            }
        }
        return $records.ToArray()
    }

    $message = Get-ProblemPropertyValue -Object $Node -Names @("message", "text")
    $resourcePath = Get-ProblemsResourcePath -Diagnostic $Node

    if ($null -ne $message -and $null -ne $resourcePath) {
        $relativePath = Convert-ToRelativeProblemPath -Path $resourcePath
        $severity = Convert-ProblemsSeverity -Value (Get-ProblemPropertyValue -Object $Node -Names @("severity", "severityLevel"))
        $source = Get-ProblemPropertyValue -Object $Node -Names @("source", "owner")
        $messageSummary = Get-ProblemMessageSummary -Message ($message.ToString())
        $line = Get-ProblemLocation -Diagnostic $Node

        $records.Add([PSCustomObject]@{
            Path = $relativePath
            ShortPath = Get-ShortProblemPath -Path $relativePath
            Directory = Get-ProblemDirectory -Path $relativePath
            ShortDirectory = Get-ShortProblemDirectory -Path $relativePath
            FileName = [System.IO.Path]::GetFileName($relativePath)
            Line = $line
            Message = $messageSummary
            Severity = $severity
            Source = if ($null -eq $source) { "" } else { $source.ToString().Trim() }
        })
        return $records.ToArray()
    }

    foreach ($property in $Node.PSObject.Properties) {
        $value = $property.Value
        if ($value -is [System.Collections.IEnumerable] -and -not ($value -is [string])) {
            foreach ($record in Get-ProblemRecordsFromNode -Node $value) {
                $records.Add($record)
            }
        }
    }

    return $records.ToArray()
}

function Get-JavaWarningRecords {
    param(
        [Parameter(Mandatory)]
        [string]$InputPath,

        [switch]$IncludeGenerated
    )

    $resolvedInputPath = Resolve-ProblemsInputPath -Path $InputPath
    if (-not (Test-Path -LiteralPath $resolvedInputPath)) {
        throw ("Problems export not found: {0}" -f $resolvedInputPath)
    }

    $raw = Get-Content -LiteralPath $resolvedInputPath -Raw
    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw ("Problems export is empty: {0}" -f $resolvedInputPath)
    }

    try {
        $parsed = $raw | ConvertFrom-Json
    }
    catch {
        throw "Problems export must be JSON. Copy the Problems content to a JSON file first."
    }

    $records = @(Get-ProblemRecordsFromNode -Node $parsed)
    $records = @($records | Where-Object { $_.Path -match '\.java$' -and $_.Severity -eq 'warning' })
    $records = @($records | Where-Object {
        [string]::IsNullOrWhiteSpace($_.Source) -or $_.Source.IndexOf('java', [System.StringComparison]::OrdinalIgnoreCase) -ge 0
    })

    if (-not $IncludeGenerated) {
        $records = @($records | Where-Object { -not (Test-IsGeneratedProblemPath -Path $_.Path) })
    }

    return $records
}

function Test-ProblemMessageMatch {
    param(
        [Parameter(Mandatory)]
        [string]$Message,

        [Parameter(Mandatory)]
        [string]$Needle,

        [Parameter(Mandatory)]
        [ValidateSet('Contains', 'Exact', 'Regex')]
        [string]$MatchMode
    )

    switch ($MatchMode) {
        'Contains' {
            return $Message.IndexOf($Needle, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
        }
        'Exact' {
            return [string]::Equals($Message, $Needle, [System.StringComparison]::OrdinalIgnoreCase)
        }
        'Regex' {
            return $Message -match $Needle
        }
    }

    return $false
}

function Join-UniqueShortPaths {
    param(
        [Parameter(Mandatory)]
        [object[]]$Records,

        [int]$MaxItems = 3
    )

    $paths = @($Records | Select-Object -ExpandProperty ShortPath -Unique | Select-Object -First $MaxItems)
    return ($paths -join '; ')
}

function Join-UniqueNumbers {
    param(
        [Parameter(Mandatory)]
        [int[]]$Numbers
    )

    return ((@($Numbers | Sort-Object -Unique) | ForEach-Object { $_.ToString() }) -join ', ')
}
