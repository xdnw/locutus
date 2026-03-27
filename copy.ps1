param(
    # Accepts an array of file paths. ValueFromRemainingArguments allows you to just paste the space-separated list after the script name.
    [Parameter(Mandatory=$true, Position=0, ValueFromRemainingArguments=$true)]
    [string[]]$Files
)

# If the user pasted the entire list as a single quoted string, intelligently split it.
# This splits by spaces that are followed by a drive letter (e.g., " H:\") to prevent breaking paths with spaces.
if ($Files.Count -eq 1 -and $Files[0] -match '\s+[A-Za-z]:\\') {
    $Files = $Files[0] -split '\s+(?=[A-Za-z]:\\)'
}

# Array to hold our final processed lines
$allProcessedLines = @()

foreach ($file in $Files) {
    if (-not (Test-Path -LiteralPath $file)) {
        Write-Warning "File not found: $file"
        continue
    }

    # Read the entire file as a single string to easily handle multi-line comments
    $content = Get-Content -LiteralPath $file -Raw

    # 1. Remove block comments (/* ... */)
    # (?s) enables single-line mode so the dot (.) matches newline characters
    $content = $content -replace '(?s)/\*.*?\*/', ''

    # 2. Remove single-line comments (// ...)
    $content = $content -replace '//.*', ''

    # Split the cleaned content back into an array of lines
    $lines = $content -split "`r?`n"

    # 3. Process line-by-line (Trim and remove package/import)
    foreach ($line in $lines) {
        $trimmedLine = $line.Trim()

        # Skip empty lines
        if ([string]::IsNullOrWhiteSpace($trimmedLine)) {
            continue
        }

        # Skip package and import statements
        if ($trimmedLine -match '^(package|import)\b') {
            continue
        }

        $allProcessedLines += $trimmedLine
    }
    
    # Add an empty line between different files for readability
    $allProcessedLines += ""
}

# Combine all processed lines into a single chunk of text
$finalOutput = $allProcessedLines -join [Environment]::NewLine

# Copy the result to the clipboard
$finalOutput | Set-Clipboard

Write-Host "Successfully processed $($Files.Count) files and copied the chunk to your clipboard!" -ForegroundColor Green