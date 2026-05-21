# Uso del DJI SDK en DJIMiniBridge

Esta app Android usa DJI Mobile SDK v4.16.4 como puente entre el telefono, el control DJI y el dron.

## Flujo estable probado

1. `Cargar DJI`
   - Ejecuta `Helper.install(getApplication())`.
   - No se debe mover al `Application`, porque antes causo comportamiento inestable.

2. `Registrar DJI`
   - Ejecuta `DJISDKManager.getInstance().registerApp(...)`.
   - Solo registra el SDK.
   - No inicia conexion automaticamente.

3. `Conectar dron`
   - Ejecuta `DJISDKManager.getInstance().startConnectionToProduct()`.
   - Cuando el SDK detecta producto, llama `onProductConnect(...)`.

4. `Iniciar video`
   - Usa `VideoFeeder.getInstance().getPrimaryVideoFeed()`.
   - Usa `DJICodecManager` para decodificar video sobre un `TextureView`.
   - El `TextureView` debe mantenerse vivo. No usar `GONE` ni `INVISIBLE`; para ocultar la imagen se usa `alpha`.

5. `Detener prueba`
   - Detiene el listener de video.
   - Apaga lectura de bateria.
   - Apaga lectura de telemetria.
   - Limpia valores visuales.
   - Mantiene la app abierta.

6. `Desconectar dron`
   - Ejecuta `DJISDKManager.getInstance().stopConnectionToProduct()`.
   - Limpia video, bateria, telemetria y estado.

7. `Cerrar app`
   - Detiene prueba.
   - Detiene puente PC.
   - Cierra la app con `finishAndRemoveTask()`.

## Clases DJI usadas

### Registro y conexion

- `dji.sdk.sdkmanager.DJISDKManager`
  - `registerApp(...)`
  - `startConnectionToProduct()`
  - `stopConnectionToProduct()`
  - `getProduct()`

- `dji.sdk.base.BaseProduct`
  - Producto DJI conectado.
  - Se usa para leer modelo, bateria y estado de conexion.

- `dji.sdk.base.BaseComponent`
  - Usado por callbacks del SDK.

### Bateria

- `dji.sdk.battery.Battery`
  - Obtenida desde `product.getBattery()`.

- `dji.common.battery.BatteryState`
  - Usada con `battery.setStateCallback(...)`.
  - Datos actuales:
    - porcentaje: `getChargeRemainingInPercent()`
    - voltaje: `getVoltage()`

### Telemetria

- `dji.sdk.products.Aircraft`
  - Se valida con `product instanceof Aircraft`.
  - Permite acceder a `getFlightController()`.

- `dji.sdk.flightcontroller.FlightController`
  - Usa `setStateCallback(...)` para recibir telemetria.

- `dji.common.flightcontroller.FlightControllerState`
  - Datos actuales:
    - altura: `getAircraftLocation().getAltitude()`
    - velocidad X: `getVelocityX()`
    - velocidad Y: `getVelocityY()`
    - velocidad Z: `getVelocityZ()`
    - home: `getHomeLocation()`

- `dji.common.flightcontroller.LocationCoordinate3D`
  - Latitud, longitud y altura actual del dron.

- `dji.common.model.LocationCoordinate2D`
  - Ubicacion home.
  - Se usa para calcular distancia horizontal aproximada.

### Video

- `dji.sdk.camera.VideoFeeder`
  - Fuente de datos de video.
  - Se usa `getPrimaryVideoFeed().addVideoDataListener(...)`.

- `dji.sdk.codec.DJICodecManager`
  - Decodifica el video recibido por `VideoFeeder`.
  - Envia bytes con `sendDataToDecoder(...)`.

## Puente PC / Python

La app incluye un servidor HTTP local:

- Puerto: `8765`
- Endpoint: `GET /status`

Por USB se puede exponer a la PC con:

```powershell
& "C:\Users\maeca\AppData\Local\Android\Sdk\platform-tools\adb.exe" forward tcp:8765 tcp:8765
```

Ejemplo Python:

```python
import requests

data = requests.get("http://127.0.0.1:8765/status").json()
print(data)
```

Respuesta esperada:

```json
{
  "connection": "conectado",
  "model": "DJI ...",
  "batteryPercent": 68,
  "batteryVoltageMv": 3780,
  "altitudeM": 0.0,
  "distanceM": 0.0,
  "verticalSpeed": 0.0,
  "horizontalSpeed": 0.0,
  "videoActive": true
}
```

## Reglas importantes

- No cambiar `compileOnly("com.dji:dji-sdk-provided:4.16.4")` a `implementation`.
- No cargar `Helper.install(...)` automaticamente en `Application`.
- No registrar DJI ni conectar dron automaticamente al abrir la app.
- No poner overlays encima del `TextureView` hasta tener una prueba controlada.
- No destruir el `TextureView` para ocultar video; usar `alpha`.
- Mantener cada accion en botones separados para detectar fallos por capa.
