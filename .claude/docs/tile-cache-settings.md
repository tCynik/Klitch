# Tile Cache Settings

**Status**: Done (2026-04-29)
**Branch**: tiles_lifetime

## Summary

Настройки длительности кэша тайлов карты. На вкладке Map экрана настроек пользователь выбирает один из трёх режимов: Default, Month, Maximum. Режим управляет заголовком `Cache-Control: max-age` через OkHttp-интерцептор, подключённый к MapLibre. Режим Maximum показывает предупреждение о потреблении хранилища.

## Key Files

| Файл | Роль |
|---|---|
| `shared/domain/settings/model/TileCacheMode.kt` | Enum: DEFAULT / MONTH / MAXIMUM |
| `shared/domain/settings/repository/MapCacheSettingsRepository.kt` | Доменный интерфейс: get/set/flow |
| `shared/domain/settings/usecase/GetTileCacheModeUseCase.kt` | Sync use case |
| `shared/domain/settings/usecase/SetTileCacheModeUseCase.kt` | Suspend use case |
| `shared/domain/settings/usecase/ObserveTileCacheModeUseCase.kt` | Flow use case |
| `app/data/map/TileCacheInterceptor.kt` | OkHttp Interceptor; держит `AtomicReference<TileCacheMode>` |
| `app/data/map/TileCacheOkHttpConfigurator.kt` | Строит OkHttpClient + Cache; вызывает `HttpRequestUtil.setOkHttpClient()` + `OfflineManager` |
| `app/di/MapDataModule.kt` | DI-привязки: конфигуратор, репозиторий, use cases |
| `app/src/main/.../MyMeshApplication.kt` | `startKoin` → `configurator.applyTo(this)` после инициализации Koin |
| `app/src/main/res/values/strings.xml` | 11 новых строк `tile_cache_*` |
| `presentation/feature/settings/SettingsUiState.kt` | `tileCacheMode: TileCacheMode` |
| `presentation/feature/settings/SettingsViewModel.kt` | `onTileCacheModeSelected(mode)` |
| `presentation/feature/settings/SettingsScreen.kt` | `TileCacheModeSelector` (private fun) + warning dialog в `MapTabContent` |

## Architecture Decisions

### MapLibre OkHttp Injection

`HttpRequestUtil.setOkHttpClient()` вызывается в `MyMeshApplication.onCreate()` после `startKoin`, до первой композиции `MapView`. Это единственно корректный call site — maplibre-compose не предоставляет Compose-side hook.

Паттерн задокументирован в `/architect` → "MapLibre OkHttp Injection Pattern".

### Two-Cache Model

MapLibre держит два кэша одновременно:
- **OkHttp disk cache** (100 MB, файловый) — контролируется интерцептором
- **MapLibre ambient cache** (SQLite, по умолчанию 50 MB LRU) — поднят до 100 MB через `OfflineManager.setMaximumAmbientCacheSize` в том же `Application.onCreate()`

Без подъёма ambient cache режимы MONTH/MAXIMUM теряют эффективность после вытеснения плиток из SQLite.

### AtomicReference Interceptor (Variant B)

`TileCacheInterceptor` держит `AtomicReference<TileCacheMode>`. `SetTileCacheModeUseCase` вызывает `configurator.updateMode(mode)` — клиент перестраивать не нужно, изменение вступает в силу на следующем tile request без перезапуска приложения.

### Warning Dialog

Показывается каждый раз при выборе Maximum (не только один раз). Состояние `pendingMaximumConfirm` живёт в `MapTabContent`, не в селекторе. Отмена не меняет VM-state → selectedMode откатывается автоматически.

## String Resources

| Ключ | Значение |
|---|---|
| `tile_cache_section_label` | `Кэш тайлов` |
| `tile_cache_mode_default` | `По умолчанию` |
| `tile_cache_mode_default_desc` | `Заголовки сервера (~7 дней)` |
| `tile_cache_mode_month` | `Месяц` |
| `tile_cache_mode_month_desc` | `Хранить тайлы 30 дней` |
| `tile_cache_mode_maximum` | `Максимально` |
| `tile_cache_mode_maximum_desc` | `Хранить тайлы 1 год` |
| `tile_cache_maximum_warning_title` | `Большой объём хранилища` |
| `tile_cache_maximum_warning_message` | `Режим «Максимально» накапливает до 100 МБ тайлов на диске. Кэш не очищается автоматически.` |
| `tile_cache_maximum_warning_confirm` | `Продолжить` |
| `tile_cache_maximum_warning_dismiss` | `Отмена` |
