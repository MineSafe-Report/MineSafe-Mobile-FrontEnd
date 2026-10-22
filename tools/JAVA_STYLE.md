# Java formatting

Run `tools/format-java.ps1` from PowerShell to format the Android app's Java source and test files with the project's Android Studio style. The script uses an isolated formatter profile so it can run while Android Studio is open.

- Put opening block braces on the next line, including lambda blocks. Put `else`, `catch`, and `finally` on their own lines.
- Use four-space indentation and blank lines between logical steps. The formatter keeps up to two blank lines.
- Use `// CAPITAL HEADING` for section labels and `// Normal explanatory sentence.` for explanations.
- Keep imports in the order they were added. Do not run import optimization as part of formatting.

Formatting must not change code behavior or rename identifiers.
