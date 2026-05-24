# Uso del DJI SDK en DJIMiniBridge

Esta app Android usa DJI Mobile SDK v4.16.4 como puente entre el telefono, el control DJI y el dron.

## Flujo estable probado

1. `Preparar DJI`
   - Ejecuta prueba local, carga DJI y registra DJI en una secuencia visible.
   - Incluye pausas cortas entre pasos.
   - No inicia conexion al dron automaticamente.

2. `Conectar dron`
   - Ejecuta `DJISDKManager.getInstance().startConnectionToProduct()`.
   - Cuando el SDK detecta producto, llama `onProductConnect(...)`.

3. `Iniciar video`
   - Usa `VideoFeeder.getInstance().getPrimaryVideoFeed()`.
   - Usa `DJICodecManager` para decodificar video sobre un `TextureView`.
   - El `TextureView` debe mantenerse vivo. No usar `GONE` ni `INVISIBLE`; para ocultar la imagen se usa `alpha`.

4. `Iniciar puente PC`
   - Expone HTTP en `8765`.
   - Expone WebSocket en `8766`.
   - Muestra IP para usar desde Python.

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

La app mantiene la pantalla encendida mientras esta abierta para evitar timeout durante pruebas.

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
  - Verifica disponibilidad de Virtual Stick.
  - Activa/desactiva Virtual Stick.
  - Envia `FlightControlData(0, 0, 0, 0)` en `send_zero_stick` y `emergency_stop`.

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

### Virtual Stick

- `dji.common.flightcontroller.virtualstick.RollPitchControlMode`
  - Configurado en `VELOCITY`.

- `dji.common.flightcontroller.virtualstick.YawControlMode`
  - Configurado en `ANGULAR_VELOCITY`.

- `dji.common.flightcontroller.virtualstick.VerticalControlMode`
  - Configurado en `VELOCITY`.

- `dji.common.flightcontroller.virtualstick.FlightCoordinateSystem`
  - Configurado en `BODY`.

- `dji.common.flightcontroller.virtualstick.FlightControlData`
  - En DJI-12 solo se usa con valores cero:

```java
new FlightControlData(0f, 0f, 0f, 0f)
```

No hay comandos de movimiento real en esta fase.

## Puente PC / Python

La app incluye un servidor HTTP local:

- Puerto: `8765`
- Endpoint: `GET /status`

Tambien incluye WebSocket local:

- Puerto: `8766`
- Telemetria en vivo.
- Comandos operativos y de seguridad.

La comunicacion de prueba real debe ir por WiFi porque el USB del telefono se usa con el control DJI.

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
  "videoActive": true,
  "virtualStickAvailable": true,
  "virtualStickEnabled": false,
  "flightControllerReady": true,
  "lastCommandAudit": "respuesta hora=... comando=... decision=..."
}
```

## Seguridad antes de movimiento

Antes de aceptar comandos de vuelo, Android valida:

- dron conectado
- `FlightController` listo
- Virtual Stick disponible
- Virtual Stick activo
- bateria minima
- telemetria reciente
- altura dentro de limite
- distancia dentro de limite
- comando permitido
- duracion maxima
- velocidad maxima

Comando de verificacion:

```text
can_accept_flight_command
```

Comando de prueba sin movimiento:

```text
send_zero_stick
```

Comando de parada:

```text
emergency_stop
```

`send_zero_stick` envia valores cero por 3 segundos. Si algo falla, se debe usar `emergency_stop`.

## Auditoria

Cada comando WebSocket registra:

- comando recibido
- hora
- origen
- estado del dron
- estado Virtual Stick
- aceptado o rechazado
- razon
- respuesta enviada a Python

La ultima linea se expone como `lastCommandAudit`.

## Reglas importantes

- No cambiar `compileOnly("com.dji:dji-sdk-provided:4.16.4")` a `implementation`.
- No cargar `Helper.install(...)` automaticamente en `Application`.
- No registrar DJI ni conectar dron automaticamente al abrir la app.
- No poner overlays encima del `TextureView` hasta tener una prueba controlada.
- No destruir el `TextureView` para ocultar video; usar `alpha`.
- No enviar movimiento real hasta crear una fase nueva y probar limites.
- Mantener `emergency_stop` disponible antes de cualquier comando de vuelo.
