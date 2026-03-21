[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$InputPath,

    [int]$TopMessages = 15,

    [int]$TopFiles = 10,

    [int]$TopBatches = 10,

    [switch]$IncludeGenerated
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "ProblemsExport.Common.ps1")

function Write-Section {
    param(
        [Parameter(Mandatory)]
        [string]$Title,

        [string[]]$Lines = @()
    )

    Write-Output $Title
    if ($Lines.Count -eq 0) {
        Write-Output "  (none)"
    } else {
        foreach ($line in $Lines) {
            Write-Output ("  {0}" -f $line)
        }
    }
    Write-Output ""
}

$records = @(Get-JavaWarningRecords -InputPath $InputPath -IncludeGenerated:$IncludeGenerated)
if ($records.Count -eq 0) {
    Write-Output "No Java warnings found in the Problems export."
    exit 0
}

$totalFiles = @($records | Select-Object -ExpandProperty Path -Unique).Count
$totalMessages = @($records | Select-Object -ExpandProperty Message -Unique).Count

$topMessageLines = @(
    $records |
    Group-Object -Property Message |
    Sort-Object -Property Count, Name -Descending |
    Select-Object -First $TopMessages |
    ForEach-Object {
        $files = @($_.Group | Select-Object -ExpandProperty Path -Unique)
        "{0} | {1} files | {2} | {3}" -f $_.Count, $files.Count, $_.Name, (Join-UniqueShortPaths -Records $_.Group)
    }
)

$topFileLines = @(
    $records |
    Group-Object -Property Path |
    Sort-Object -Property Count, Name -Descending |
    Select-Object -First $TopFiles |
    ForEach-Object {
        $messages = @($_.Group | Select-Object -ExpandProperty Message -Unique)
        $displayPath = ($_.Group | Select-Object -First 1).ShortPath
        $sampleMessages = ($messages | Select-Object -First 2) -join '; '
        if ($messages.Count -gt 2) {
            $sampleMessages += '; ...'
        }
        "{0} | {1} messages | {2} | {3}" -f $_.Count, $messages.Count, $displayPath, $sampleMessages
    }
)

$topBatchLines = @(
    $records |
    Group-Object -Property @{ Expression = { "{0}`n{1}" -f $_.Directory, $_.Message } } |
    Sort-Object -Property Count, Name -Descending |
    Select-Object -First $TopBatches |
    ForEach-Object {
        $first = $_.Group | Select-Object -First 1
        $files = @($_.Group | Select-Object -ExpandProperty Path -Unique)
        "{0} | {1} files | {2} | {3}" -f $_.Count, $files.Count, $first.ShortDirectory, $first.Message
    }
)

Write-Output ("Warnings: {0} total | {1} messages | {2} files" -f $records.Count, $totalMessages, $totalFiles)
Write-Output ""
Write-Section -Title "Top Messages" -Lines $topMessageLines
Write-Section -Title "Top Files" -Lines $topFileLines
Write-Section -Title "Suggested Batches" -Lines $topBatchLines
