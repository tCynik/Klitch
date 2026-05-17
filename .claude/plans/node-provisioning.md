# Plan: Node Provisioning (Фаза 2)

**Date**: 2026-04-20
**Status**: Done

## Goal

При подключении ноды к приложению — автоматически приводить ноду в соответствие
с настройками приложения: записать каналы из `LogicalChannel` на ноду и обновить
`LongName` ноды значением `AppUser.displayName`.

Принцип: **приложение — источник истины. Нода — тупое радио.**

---

## Что делает NodeProvisioningUseCase

```
При событии Connected (нода подключилась):
  1. Читаем список LogicalChannel из приложения
  2. Для каждого канала с MeshtasticBinding → writeChannel(index, name, pskBase64)
  3. Ждём загрузки MeshDeviceConfigModel (содержит текущий shortName ноды)
  4. Если AppUser.displayName непустой → writeOwner(displayName, shortName)
```

**Не делаем (отложено):**
- Сравнение с каналами на ноде (UI-диалог «синхронизировать?»)
- Очистка лишних слотов на ноде
- Запись `NodeSettings.shortName` (NodeSettings не реализованы)

---

## Архитектура

### NodeProvisioningUseCase

```
app/domain/mesh/usecase/NodeProvisioningUseCase.kt
```

Зависимости:
- `ObserveLogicalChannelsUseCase` — список каналов из приложения
- `ObserveAppUserUseCase` — профиль пользователя
- `ObserveDeviceConfigUseCase` — текущий конфиг ноды (нужен shortName)
- `WriteChannelUseCase` — записать канал на ноду
- `WriteOwnerUseCase` — записать LongName/ShortName на ноду

### Триггер в MainViewModel

```kotlin
if (!wasConnected) {
    // Уже существующая логика показа label...
    viewModelScope.launch { nodeProvisioning.provision() }
}
```

---

## DI

- `NodeProvisioningUseCase` → в `meshDataModule`
- `MainViewModel` получает `nodeProvisioning: NodeProvisioningUseCase`

---

## Definition of Done

- [x] При подключении ноды — каналы из приложения записываются на ноду
- [x] `AppUser.displayName` пушится в `LongName` ноды при подключении
- [x] Если каналов нет — нода не трогается
- [x] Если `displayName` пустой — owner не обновляется

---

## Открытые вопросы / TODO

- При следующей итерации: сравнивать каналы ноды с приложением и предлагать синхронизацию
- Запись `shortName` → после реализации `NodeSettings`

## Change Log

- 2026-04-20: создан
