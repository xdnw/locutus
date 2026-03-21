# Warning Tools

These scripts summarize Java warnings from a Problems export instead of dumping raw JSON fields back at you.

## Input

1. In VS Code, open Problems.
2. Run `Problems: Copy All`.
3. Paste the JSON into `build/reports/warnings/problems-java.json`.

The scripts assume Java warnings only and shorten paths to save context.

## Summary script

Use this first to see the biggest warning families and the best batches to fix.

```powershell
pwsh -File tools/warnings/Summarize-JavaProblems.ps1 -InputPath build/reports/warnings/problems-java.json
```

It prints:
- top repeated messages
- top files by warning count
- suggested fix batches grouped by directory plus message

## Query script

Find all line numbers for one warning message:

```powershell
pwsh -File tools/warnings/Inspect-JavaProblems.ps1 -InputPath build/reports/warnings/problems-java.json -Mode Message -Message "unchecked conversion" -MatchMode Contains
```

List all warnings for one file:

```powershell
pwsh -File tools/warnings/Inspect-JavaProblems.ps1 -InputPath build/reports/warnings/problems-java.json -Mode File -FilePath "JteUtil.java"
```

## Notes

- Output strips noisy fields such as `code`, `source`, `severity`, `tag`, and raw detail blocks.
- Paths are workspace-relative and shortened to avoid wasting context.
- Generated-source warnings are excluded by default.
- If a warning only appears in generated output, trace it back to the owning template or generator input instead of editing generated files directly.
