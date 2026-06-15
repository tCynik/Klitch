# Plan: App Rename MeshTactics → Klitch

**Date**: 2026-06-12
**Status**: Done
**Branch**: `project_rename_to_klitch` (already created)

## Summary

Full mechanical refactor: rename the application from "MeshTactics" to "Klitch" (Клич) and
change the root package from `ru.tcynik.meshtactics` to `ru.tcynik.klitch` across all modules.
No logic changes — purely a rename + package restructure. ~761 Kotlin files affected.

## Scope

**In scope:**
- `applicationId` and all `namespace` declarations in gradle files
- SQLDelight `packageName` configuration
- Source directory tree renames (`meshtactics/` → `klitch/`)
- All `package` and `import` declarations in `.kt` and `.aidl` files
- SQLDelight schema directory path
- Android resources: `app_name` string, `Theme.MeshTactics` style names
- `AndroidManifest.xml` theme references
- `.claude/settings.json` hardcoded package paths in bash test commands
- `CLAUDE.md` title and package references
- `settings.gradle.kts` `rootProject.name`

**Out of scope:**
- Renaming the physical project root directory `StudioProjects/MeshTactics/`
  (requires closing Android Studio; do manually after code changes are committed)
- Play Store / Firebase / app signing configuration (no store presence yet)
- ProGuard rules (currently no package-specific keep rules)

## Execution Order

Order is chosen so the project is **never in a partially-renamed state that compiles**
(all changes land in one atomic pass; build verification happens at the end).

---

### Step 0 — Git pre-flight

Before touching any files:

```powershell
git status          # must be clean — no uncommitted changes
git branch          # must show: * project_rename_to_klitch
git log --oneline -3  # sanity check — confirm you're on the right base
```

If `git status` is not clean — stash or commit the WIP first.

> **Why git mv in Step 4?**
> Plain directory rename leaves hundreds of "deleted + new untracked" in git.
> `git mv` marks files as **renamed** — history is preserved, `git log --follow` works,
> and the diff in the PR will show renames rather than a flood of deletions + additions.

---

### Step 1 — Build configuration files (5 files)

**Why first:** establishes the new `namespace` and `applicationId` the compiler will expect.

| File | Change |
|------|--------|
| `settings.gradle.kts` | `rootProject.name = "MeshTactics"` → `"Klitch"` |
| `app/build.gradle.kts` | `namespace` + `applicationId` → `ru.tcynik.klitch` |
| `mesh/build.gradle.kts` | `namespace` → `ru.tcynik.klitch.mesh` |
| `shared/build.gradle.kts` | `namespace` → `ru.tcynik.klitch.shared`; SQLDelight `packageName` → `ru.tcynik.klitch.data.local` |

---

### Step 2 — Android resources

| File | Change |
|------|--------|
| `app/src/main/res/values/strings.xml` | `app_name` value: `MeshTactics` → `Klitch` |
| `app/src/main/res/values/themes.xml` | Style names: `Theme.MeshTactics` → `Theme.Klitch`; `Theme.MeshTactics.Splash` → `Theme.Klitch.Splash`; `postSplashScreenTheme` reference |

---

### Step 3 — AndroidManifest.xml

| File | Change |
|------|--------|
| `app/src/main/AndroidManifest.xml` | `android:theme` refs: `@style/Theme.MeshTactics` → `@style/Theme.Klitch`; `@style/Theme.MeshTactics.Splash` → `@style/Theme.Klitch.Splash` |

---

### Step 4 — Rename source directories (git mv)

Use `git mv` to preserve history. Rename the terminal `meshtactics` segment to `klitch` in each path:

```
app/src/main/java/ru/tcynik/meshtactics/          → …/klitch/
app/src/test/java/ru/tcynik/meshtactics/           → …/klitch/
mesh/src/main/kotlin/ru/tcynik/meshtactics/        → …/klitch/
mesh/src/main/aidl/ru/tcynik/meshtactics/          → …/klitch/
shared/src/commonMain/kotlin/ru/tcynik/meshtactics/ → …/klitch/
shared/src/androidMain/kotlin/ru/tcynik/meshtactics/ → …/klitch/
shared/src/commonMain/sqldelight/ru/tcynik/meshtactics/ → …/klitch/
```

**Command pattern (PowerShell, run from project root):**
```powershell
git mv "app/src/main/java/ru/tcynik/meshtactics" "app/src/main/java/ru/tcynik/klitch"
git mv "app/src/test/java/ru/tcynik/meshtactics" "app/src/test/java/ru/tcynik/klitch"
git mv "mesh/src/main/kotlin/ru/tcynik/meshtactics" "mesh/src/main/kotlin/ru/tcynik/klitch"
git mv "mesh/src/main/aidl/ru/tcynik/meshtactics" "mesh/src/main/aidl/ru/tcynik/klitch"
git mv "shared/src/commonMain/kotlin/ru/tcynik/meshtactics" "shared/src/commonMain/kotlin/ru/tcynik/klitch"
git mv "shared/src/androidMain/kotlin/ru/tcynik/meshtactics" "shared/src/androidMain/kotlin/ru/tcynik/klitch"
git mv "shared/src/commonMain/sqldelight/ru/tcynik/meshtactics" "shared/src/commonMain/sqldelight/ru/tcynik/klitch"
```

> **Check before running**: verify `app/src/androidTest` exists and also contains `meshtactics/` — if so, add it to the list.

---

### Step 5 — Mass text replace in source files

Replace `ru.tcynik.meshtactics` → `ru.tcynik.klitch` in all `.kt`, `.aidl`, `.sq`, `.sqm` files.

**PowerShell command (run from project root):**
```powershell
Get-ChildItem -Path . -Recurse -Include "*.kt","*.aidl","*.sq","*.sqm" |
  Where-Object { $_.FullName -notmatch '\\build\\' } |
  ForEach-Object {
    $content = Get-Content $_.FullName -Raw -Encoding UTF8
    $updated = $content -replace 'ru\.tcynik\.meshtactics', 'ru.tcynik.klitch'
    if ($content -ne $updated) {
      [System.IO.File]::WriteAllText($_.FullName, $updated, [System.Text.Encoding]::UTF8)
      Write-Host "Updated: $($_.FullName)"
    }
  }
```

After running — spot-check 3–4 random `.kt` files to confirm `package ru.tcynik.klitch` is present.

---

### Step 6 — .claude configuration files

| File | Change |
|------|--------|
| `CLAUDE.md` | Line 1 heading; any `ru.tcynik.meshtactics` references; `**Package**: ru.tcynik.meshtactics` in skill context |
| `.claude/settings.json` | All hardcoded test class paths containing `ru.tcynik.meshtactics` (bash permission commands) |

These are plain text edits — use Find & Replace on the literal string `meshtactics` → `klitch`.

---

### Step 7 — Full old-name scan (before build)

Run this **before** the build to catch any remaining mentions of the old name in tracked source files.
Fix anything that appears — then proceed to Step 8.

**Package name (lowercase):**
```powershell
Get-ChildItem -Path . -Recurse -Include "*.kt","*.aidl","*.sq","*.sqm","*.kts","*.xml","*.pro" |
  Where-Object { $_.FullName -notmatch '\\build\\' -and $_.FullName -notmatch '\\.gradle\\' } |
  Select-String -Pattern "meshtactics" | Select-Object Path, LineNumber, Line
```

**App/class name (PascalCase):**
```powershell
Get-ChildItem -Path . -Recurse -Include "*.kt","*.kts","*.xml","*.md","*.json","*.pro" |
  Where-Object { $_.FullName -notmatch '\\build\\' -and $_.FullName -notmatch '\\.claude\\' } |
  Select-String -Pattern "MeshTactics" | Select-Object Path, LineNumber, Line
```

> `.claude/` is excluded from the second scan — plan and doc files may legitimately mention the old name.

**Expected:** both commands return no output.
If hits remain — fix manually, then re-run before moving to Step 8.

---

### Step 8 — Build verification

```powershell
$env:JAVA_HOME = "C:\Users\tcynik\.jdks\jbr-21.0.7"; .\gradlew assembleDebug
```

**Expected result:** `BUILD SUCCESSFUL`. If errors appear:
- `Unresolved reference` → missed a package declaration (re-run Step 7 scan)
- `SQLDelight` errors → check `shared/build.gradle.kts` packageName and sqldelight dir path
- `Theme not found` → check themes.xml and AndroidManifest.xml

---

### Step 9 — Memory & config update

After successful build:

1. Update `CLAUDE.md` — package reference in project header (`**Package**: ru.tcynik.klitch`)
2. Update memory file `~/.claude/projects/.../memory/project_state.md` — package name

---

### Step 10 — Commit

`git mv` из Step 4 уже добавил переименования в индекс. Остальные изменённые файлы стейджим явно:

```powershell
# Проверяем итоговое состояние — все mv должны быть видны как renamed
git status

# Стейджим изменённые (не mv) файлы по имени
git add settings.gradle.kts
git add app/build.gradle.kts
git add mesh/build.gradle.kts
git add shared/build.gradle.kts
git add app/src/main/AndroidManifest.xml
git add app/src/main/res/values/strings.xml
git add app/src/main/res/values/themes.xml
git add CLAUDE.md
git add .claude/settings.json
# + любые другие файлы из git status, не охваченные выше
```

Финальная проверка перед коммитом:
```powershell
git diff --cached --stat   # должны видеть ~761 renamed + несколько modified
```

Сообщение коммита:
```
переименование приложения: MeshTactics → Klitch, пакет meshtactics → klitch
```

> **Не использовать** `git add -A` или `git add .` — можно случайно захватить артефакты сборки из `build/`.

---

## Git — рекомендации (ручные действия для пользователя)

### После коммита — публикация ветки

```powershell
git push origin project_rename_to_klitch
```

Открыть PR: `project_rename_to_klitch` → `main`.

В PR будет **очень длинный diff** (~761 файл). Это нормально — всё это переименования пакетов.
Для ревью достаточно убедиться, что:
- build прошёл в CI (или локально)
- оба скана из Step 7 пусты
- `applicationId` и `app_name` изменены корректно

### Переименование физической папки (опционально, после мержа)

> Делать только когда ветка смержена и Android Studio закрыта.

1. Закрыть Android Studio полностью
2. В Explorer: `StudioProjects\MeshTactics` → `StudioProjects\Klitch`
3. Открыть Android Studio → Open → выбрать новый путь
4. Если в терминале есть сохранённые `cd`-алиасы на старый путь — обновить
5. Сообщить Claude о смене пути — Claude обновит memory (путь фигурирует в `MEMORY.md`)

**Git-репозиторий при этом не затрагивается** — `remote origin` и содержимое `.git/` остаются
корректными; путь в `origin` (GitHub) не зависит от имени локальной папки.

### Если Android Studio показывает красные пакеты при чистой gradle-сборке

IDE кэширует пути к источникам. Лечится:
```
File → Invalidate Caches → Invalidate and Restart
```
или `.\gradlew clean` перед открытием проекта.

---

## Testing Criterion

- `.\gradlew assembleDebug` → `BUILD SUCCESSFUL`
- Оба скана из Step 7 возвращают пустой вывод
- Иконка приложения на устройстве/эмуляторе подписана **Klitch**

---

## Open Questions

- **androidTest directory**: может содержать путь `meshtactics/` — проверить перед Step 4.
- **Firebase / Crashlytics**: сейчас отсутствует; при добавлении `google-services.json` потребует обновления `package_name`.

## Change Log

- 2026-06-12: создан; добавлены Step 0 (git pre-flight), Step 7 (двойной сканер старого имени), Step 10 (детальный коммит), раздел git-рекомендаций для пользователя
- 2026-06-12: выполнен; 891 файл изменён (831 rename + 60 modified); BUILD SUCCESSFUL; коммит baa46f0; tokens: not recorded
