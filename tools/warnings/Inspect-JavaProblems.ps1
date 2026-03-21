[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$InputPath,

    [Parameter(Mandatory)]
    [ValidateSet("Message", "File")]
    [string]$Mode,

    [Alias("Message")]
    [string]$MessageQuery,

    [string]$FilePath,

    [Alias("MatchMode")]
    [ValidateSet("Contains", "Exact", "Regex")]
    [string]$TextMatchMode = "Contains",

    [switch]$IncludeGenerated
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "ProblemsExport.Common.ps1")

$records = @(Get-JavaWarningRecords -InputPath $InputPath -IncludeGenerated:$IncludeGenerated)

switch ($Mode) {
    "Message" {
        if ([string]::IsNullOrWhiteSpace($MessageQuery)) {
            throw "-Message is required when -Mode Message is used."
        }

        $warningRecords = @($records | Where-Object { Test-ProblemMessageMatch -Message $_.Message -Needle $MessageQuery -MatchMode $TextMatchMode })
        if ($warningRecords.Count -eq 0) {
            Write-Output ("No Java warnings matched message query: {0}" -f $MessageQuery)
            exit 0
        }

        $grouped = @($warningRecords | Group-Object -Property Path | Sort-Object -Property Name)
        Write-Output ("Message matches: {0} warnings | {1} files" -f $warningRecords.Count, $grouped.Count)
        Write-Output ""
        foreach ($group in $grouped) {
            $first = $group.Group | Select-Object -First 1
            $lines = Join-UniqueNumbers -Numbers @($group.Group | Select-Object -ExpandProperty Line)
            Write-Output ("{0}: {1}" -f $first.ShortPath, $lines)
        }
    }
    "File" {
        if ([string]::IsNullOrWhiteSpace($FilePath)) {
            throw "-FilePath is required when -Mode File is used."
        }

        $warningRecords = @($records | Where-Object { $_.Path.IndexOf($FilePath, [System.StringComparison]::OrdinalIgnoreCase) -ge 0 })
        if ($warningRecords.Count -eq 0) {
            Write-Output ("No Java warnings matched file query: {0}" -f $FilePath)
            exit 0
        }

        $files = @($warningRecords | Group-Object -Property Path | Sort-Object -Property Name)
        foreach ($fileGroup in $files) {
            $first = $fileGroup.Group | Select-Object -First 1
            Write-Output ("{0} | {1} warnings" -f $first.ShortPath, $fileGroup.Count)
            foreach ($messageGroup in @($fileGroup.Group | Group-Object -Property Message | Sort-Object -Property Count, Name -Descending)) {
                $lines = Join-UniqueNumbers -Numbers @($messageGroup.Group | Select-Object -ExpandProperty Line)
                Write-Output ("  {0} | {1} | {2}" -f $messageGroup.Count, $messageGroup.Name, $lines)
            }
            Write-Output ""
        }
    }
}
