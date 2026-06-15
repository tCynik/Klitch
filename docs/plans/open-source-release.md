# План подготовки к open-source публикации

## Результаты предварительного аудита

- `local.properties`, `.vscode/API.txt`, `claude-login.*` — никогда не коммитились ✅
- Секретов в `app`-модуле нет ✅
- Git-история чистая ✅
- Tile URL `tile.opentopomap.org` — публичный, не секрет ✅

---

## Фаза 0: Актуализация плана по текущему состоянию кода

Выполняется до начала изменений. Цель — зафиксировать что уже есть, что отсутствует, какие решения нужны.

### 0.1 — Инвентаризация корневых файлов

Проверить наличие/отсутствие:

| Файл | Ожидание | Текущий статус |
|---|---|---|
| `LICENSE` | должен быть | ❌ отсутствует |
| `NOTICE` | должен быть | ❌ отсутствует |
| `README.md` | должен быть | ❌ отсутствует |
| `keystore.properties` | НЕ должен быть в git | ✅ не коммитился |
| `keystore.properties.example` | должен быть | ❌ отсутствует |

```bash
ls -la | grep -E "LICENSE|NOTICE|README|keystore"
```

### 0.2 — Аудит `.gitignore`

Текущее состояние `.gitignore` (уже содержит):
```
.claude/settings.local.json
/claude-login.bat
/claude-login.ps1
/.vscode/API.txt       ← только один файл, не вся директория
local.properties
```

Чего не хватает:
```
*.jks
*.keystore
keystore.properties
.vscode/              ← сейчас исключён только API.txt
docs/archive/
docs/plans/
```

### 0.3 — Аудит tracked-файлов `docs/`

Документация перенесена из `.claude/` в `docs/` (в `.claude/` остались только `commands/` и `settings.json`).

Tracked поддиректории `docs/`:
```
docs/archive/    ← внутренние планы разработки — исключить
docs/plans/      ← активные планы — исключить
docs/features/   ← документация фич — оставить (полезно контрибьюторам)
docs/knowledge/  ← база знаний Meshtastic — оставить
docs/specs/      ← product brief — оставить
docs/research/   ← технические исследования — на усмотрение
docs/debug/      ← история дебага — исключить
```

Решение: убрать `archive/`, `plans/`, `debug/` из tracking.

```bash
# Команда для удаления из tracking (не удаляет файлы локально)
git rm -r --cached docs/archive/ docs/plans/ docs/debug/
```

### 0.4 — Аудит лицензионных хедеров

```bash
# Проверить .proto файлы (ожидаем: 0 хедеров)
grep -rl "SPDX\|Copyright" mesh/src/main/proto/ --include="*.proto" | wc -l

# Проверить .aidl файлы (ожидаем: 0 хедеров)
grep -rl "Copyright" mesh/src/main/aidl/ --include="*.aidl" | wc -l

# Проверить .kt файлы mesh БЕЗ хедера Meshtastic (оригинальные файлы)
grep -rL "Meshtastic LLC" mesh/src/main/kotlin/ --include="*.kt"
```

Ожидаемые результаты по данным аудита:
- `.proto` без хедеров: **24 файла**
- `.aidl` без хедеров: **5 файлов**
- `.kt` оригинальные (без Meshtastic LLC): **4 файла**

### 0.5 — Аудит `app/build.gradle.kts`

Проверить наличие signing config:
```bash
grep -n "signingConfig\|signingConfigs\|keystore" app/build.gradle.kts
```

Текущее состояние: **signing config отсутствует** — release собирается без подписи.
Нужно добавить условный блок (Фаза 2).

### 0.6 — Аудит `shared`-модуля на локальные пути

```bash
grep -rn "C:\\Users\|/Users/" shared/src/ --include="*.kt"
grep -rn "C:\\Users\|/Users/" shared/src/ --include="*.xml"
```

### 0.7 — Зафиксировать решения перед выполнением

По итогам фазы 0 подтвердить:
- [ ] Исключать `docs/archive/`, `docs/plans/`, `docs/debug/` из tracking? → **да**
- [ ] Оставлять `.claude/commands/`, `docs/features/`, `docs/knowledge/`, `docs/specs/`? → **да**
- [ ] Включать `docs/research/` в репо? → **решить**

---

## Фаза 1: Лицензионный комплаенс

### 1.1 — Добавить `LICENSE` в корень репо

Файл с текстом GPL-3.0: https://www.gnu.org/licenses/gpl-3.0.txt

### 1.2 — Добавить GPL-хедеры в `.proto` файлы (24 файла)

Директория: `mesh/src/main/proto/meshtastic/`

Хедер взять из оригинала `meshtastic/protobufs`. Формат:
```
// Copyright (c) Meshtastic LLC
// SPDX-License-Identifier: GPL-3.0-only
```

### 1.3 — Добавить GPL-хедеры в `.aidl` файлы (5 файлов)

Директория: `mesh/src/main/aidl/`

Те же хедеры — структура `IMeshService` соответствует оригиналу Meshtastic Android.

### 1.4 — Добавить `NOTICE` файл в корень

```
Third-party components:

Meshtastic Android
  Copyright (c) Meshtastic LLC
  License: GPL-3.0 — https://www.gnu.org/licenses/gpl-3.0.html
  Source: https://github.com/meshtastic/Meshtastic-Android

OSMBonusPack
  Copyright (c) MKergall
  License: LGPL-3.0 — https://www.gnu.org/licenses/lgpl-3.0.html
  Source: https://github.com/MKergall/osmbonuspack
```

### 1.5 — Copyright на 4 оригинальных mesh-файла

Файлы: `MeshModule.kt`, `Routes.kt`, `GeoSendPolicy.kt`, `MeshResources.kt`

Добавить в начало каждого:
```kotlin
// Copyright (c) 2025 tCynik — modifications under GPL-3.0
```

---

## Фаза 2: Приватность ключа подписи

### 2.1 — Создать `keystore.properties` (не коммитить)

Файл в корне репо:
```properties
storeFile=../meshtactics-release.jks
storePassword=REPLACE_ME
keyAlias=REPLACE_ME
keyPassword=REPLACE_ME
```

### 2.2 — Обновить `app/build.gradle.kts`

```kotlin
import java.util.Properties
import java.io.FileInputStream

android {
    signingConfigs {
        val keystoreFile = rootProject.file("keystore.properties")
        if (keystoreFile.exists()) {
            create("release") {
                val props = Properties().apply { load(FileInputStream(keystoreFile)) }
                storeFile = file(props["storeFile"] as String)
                storePassword = props["storePassword"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig != null) signingConfig = releaseConfig
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // ... остальное без изменений
}
```

Логика: без `keystore.properties` gradle не падает — release собирается без подписи.
Ревьюер делает `./gradlew assembleDebug` — debug keystore генерируется Android SDK автоматически.

### 2.3 — Добавить `keystore.properties.example`

Коммитится в репо как шаблон для разработчиков:
```properties
# Скопируйте этот файл в keystore.properties и заполните значения
storeFile=../your-keystore.jks
storePassword=
keyAlias=
keyPassword=
```

---

## Фаза 3: Репозиторная гигиена

### 3.1 — Обновить `.gitignore`

Добавить:
```gitignore
# Signing
keystore.properties
*.jks
*.keystore

# IDE
.vscode/

# Internal dev artifacts
docs/archive/
docs/plans/
docs/debug/
```

### 3.2 — Решить судьбу `.claude/` в git (97 tracked файлов)

Варианты:
- **Убрать всё**: `git rm -r --cached docs/archive/ docs/plans/ docs/debug/` + добавить в `.gitignore`
- **Оставить только features/ и knowledge/**: убрать `archive/`, `plans/`, `debug/`, `research/`

Рекомендация: убрать `archive/`, `plans/`, `debug/` — внутренние артефакты разработки.
`docs/features/` и `docs/knowledge/` — полезная документация для контрибьюторов, оставить.

### 3.3 — Проверить `shared`-модуль

```bash
grep -rn "C:\\Users\\|/Users/" shared/src --include="*.kt"
grep -rn "C:\\Users\\|/Users/" shared/src --include="*.xml"
```

Цель: убедиться, что нет захардкоженных локальных путей.

---

## Фаза 4: Документация

### 4.1 — `README.md`

Обязательные секции:
- Описание проекта и назначение
- Требования: Meshtastic-совместимое BLE-устройство, Android 7.0+
- Сборка:
  ```bash
  git clone <repo>
  cd MeshTactics
  ./gradlew assembleDebug
  ```
- Attribution:
  ```
  Based on Meshtastic Android — Copyright Meshtastic LLC — GPL-3.0
  https://github.com/meshtastic/Meshtastic-Android
  ```
- License: GPL-3.0

### 4.2 — После публикации: регистрация в Meshtastic third-party apps

URL: https://meshtastic.org/docs/software/third-party/

Органический трафик от Meshtastic-сообщества.

---

## Фаза 5: Финальная проверка

```bash
# Чистая сборка с нуля (без keystore.properties)
git clone <repo> /tmp/meshtactics-test
cd /tmp/meshtactics-test
./gradlew assembleDebug   # должна пройти без ошибок
./gradlew assembleRelease # должна пройти без ошибок (без подписи)
```

---

## Приоритизация

| Задача | Приоритет | Усилия |
|---|---|---|
| `LICENSE` файл | 🔴 Критично | 5 мин |
| `app/build.gradle.kts` — signing config | 🔴 Критично | 20 мин |
| `.gitignore` — ключи и `docs/plans`, `docs/archive`, `docs/debug` | 🔴 Критично | 10 мин |
| GPL-хедеры `.proto` + `.aidl` | 🔴 Критично | 1-2 ч |
| `NOTICE` файл | 🟡 Важно | 15 мин |
| `keystore.properties.example` | 🟡 Важно | 5 мин |
| Решить судьбу `docs/` в git (archive/plans/debug) | 🟡 Важно | 30 мин |
| `README.md` | 🟡 Важно | 1-2 ч |
| Проверка `shared`-модуля | 🟢 Низко | 15 мин |
| Регистрация Meshtastic third-party | 🟢 После публикации | 20 мин |
