Default stance: assume you're allowed to refactor for clarity/root-cause fixes. Only optimize for minimal diffs when explicitly asked. Keep scope proportional to confidence, risk, and ownership.

Before editing, briefly state: goal, invariants to preserve, boundaries/ownership affected, key data flow, and what becomes simpler in the resulting code. Then explicitly name the shortcut solution you are not taking and give one sentence for why you are rejecting it. If making a structural change, also state which boundary or dependency becomes cleaner and what the main risk is. Prefer the structural fix that makes the code easiest to understand in isolation and removes the root cause — even if it touches more code.

During implementation: make behavior explicit; keep dependencies intentional and one-way; avoid reach-through/cycles; prefer deleting/merging/replacing over layering wrappers, flags, or special cases; add abstractions only if they remove real complexity and are understandable in isolation without needing to read callers.

Before creating something new, check whether the concept already exists under another name. Reuse or extend it when that improves clarity; if extension would force branching on the new case, keep it separate and note the overlap.

Do not shape production code solely for tests. When behavior or boundaries are unclear, treat tests as guardrails/spec; update or add them after changes, and keep the tree green.

If you notice you are exhibiting premature permission seeking behaviors, self correct. These are often preceeded by phrases such as:
- "not caused by my changes", "existing issue"
- "should I continue?", "want me to keep going?"
- "good stopping point", "natural checkpoint", "natural follow-ups"
- "known limitation", "future work"
- "continue in a new session", "getting long"

## Subagent Use
Do not spend main context on broad codebase discovery. If locating the relevant code will likely require more than two exploratory Read/Grep/Glob calls, delegate first.

Use:
- `copilot-file-reader` for narrow read-only lookup when the relevant file, symbol, or dependency is unknown but the scope is small.
- `copilot-code-indexer` for broad read-only indexing where shell search is better: usage scans, imports/packages, generated sources, changed files, or test discovery.
- `copilot-code-cartographer` before non-trivial refactors/roadmaps when boundaries, ownership, data flow, dependency direction, or overlapping concepts are unclear.
- `copilot-test-output-triager` for build/test/check output or logs.

Do not chain subagents by default. Escalate only if the prior result is insufficient: `file-reader` → `code-indexer` → `code-cartographer`.
Subagent findings are leads, not authority. Before editing, directly inspect the cited files/line ranges.
Direct main-agent Read/Grep is allowed for exact verification, trivial edits, and already-known files; not for broad discovery when a matching subagent exists.

When running under GitHub Copilot, do not use `.claude/agents/*` agents directly.
Use `.github/agents/copilot-*` agents only.

## Roadmap Continuity

- For roadmap work, use `docs/ACTIVE_FRONTIER.md` as the cross-session handoff.
- On a new session, read `docs/ACTIVE_FRONTIER.md` first and continue there if it still names a live workstream with remaining gaps.
- Only fall back to the latest targeted test or failing command, the latest edited files, the focused roadmap page for that family, and then `docs/roadmap.md` if the frontier file is missing, stale, or explicitly cleared.
- If you had to reconstruct the frontier, rewrite `docs/ACTIVE_FRONTIER.md` before broader discovery or implementation.
- Update `docs/ACTIVE_FRONTIER.md` at the end of every roadmap turn when the frontier, gaps, next command, or blocker changed.
- Do not replace this handoff with a generic ordered checklist or a first-unchecked-item rule.

## Completion Discipline

- The normal stop condition for roadmap work is that `docs/ACTIVE_FRONTIER.md` has no remaining acceptance gaps for the current workstream.
- Do not end a turn after one local improvement if that file still names an open acceptance gap.
- After each passing targeted test or benchmark, check the same active frontier again and keep going if any acceptance gap remains.
- Only stop with open gaps when `docs/ACTIVE_FRONTIER.md` records a concrete blocker with the exact failing command, file, or missing dependency, or when the user explicitly redirects the work.
- "Made progress" is not a valid stopping condition.