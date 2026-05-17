## Research: MapLibre OkHttp Injection (maplibre-compose 0.12.1)

### Findings

- `HttpRequestUtil.setOkHttpClient()` is in `org.maplibre.android.module.http` (MapLibre Native Android), not in maplibre-compose.
- maplibre-compose 0.12.1 has **zero references** to `HttpRequestUtil` — it does not wrap or replace this API.
- `HttpRequestUtil` is directly callable from app code because maplibre-native is a transitive dependency of maplibre-compose.
- v0.12.1 changelog: only GeoJson serialization fix — no HTTP layer changes.
- **Correct call site: `Application.onCreate()`**, before any Activity/MapView starts. This is the safest placement — setting survives across MapView instances and avoids races in multi-activity or process-death scenarios.
- maplibre-compose exposes **no Compose-side lifecycle hook** for HTTP config (no CompositionLocal, no MapEffect initializer). Injection must happen before the first `MapView` composable enters composition.
- maplibre-compose 0.12.1 exposes **no cache configuration API** of its own.
- Underlying `OfflineManager` (package `org.maplibre.android.offline`) provides `setMaximumAmbientCacheSize(size, callback)` — directly callable alongside maplibre-compose. Default ambient cache = 50 MB.
- `OfflineManager::setMaximumAmbientCacheAge` does not exist in any released version (open GitHub issue #2300).

### Constraints for MeshTactics

- Call `HttpRequestUtil.setOkHttpClient(...)` in `MyMeshApplication.onCreate()` — confirmed safe.
- No compose-layer hook needed; no wrapping required.
- `TileCacheOkHttpConfigurator` builds the client once; `AtomicReference<TileCacheMode>` in interceptor handles live mode changes — no client rebuild required.
- `OfflineManager.setMaximumAmbientCacheSize` can be called in the same `Application.onCreate()` block if we want to raise the ambient cache ceiling above 50 MB default (optional for MVP).

### Open questions (unresolved)

- None — all Phase 0 questions answered.

### Sources

- MapLibre Native Android docs: https://maplibre.org/maplibre-native/android/api/
- maplibre-compose GitHub: https://github.com/maplibre/maplibre-compose
- AWS Location + MapLibre tutorial (setOkHttpClient call site example)
