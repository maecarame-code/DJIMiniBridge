# DJIMiniBridge

Puente experimental entre una PC con Python, un telefono Android, DJI Mobile SDK v4 y un dron DJI.

```text
Python en PC
  -> WiFi HTTP/WebSocket
    -> telefono Android
      -> DJI Mobile SDK
        -> control DJI
          -> dron
```

Regla principal: Python pide, Android valida y Android decide. La PC no habla directamente con el dron.

## Estado actual

- Android prepara DJI manualmente con `Preparar DJI`.
- Conecta/desconecta dron desde un boton con estado.
- Muestra video estable con `TextureView`, `VideoFeeder` y `DJICodecManager`.
- Expone estado por HTTP en `8765`.
- Expone telemetria y comandos por WebSocket en `8766`.
- Soporta Virtual Stick seguro:
  - revisar disponibilidad
  - activar/desactivar manualmente
  - validar seguridad antes de comandos de vuelo
  - enviar `send_zero_stick` por 3 segundos sin movimiento real
  - ejecutar `emergency_stop`
- Registra auditoria simple por comando en Android y expone `lastCommandAudit`.

## Documentacion

- `docs/RESUMEN_ESTABLE.md`: estado estable confirmado.
- `docs/USO_DJI_SDK.md`: reglas y uso del SDK DJI.
- `docs/PYTHON_BRIDGE.md`: comunicacion PC Python -> telefono Android.
- `docs/V2_PLAN.md`: plan V2.

## Configuracion local

Este proyecto necesita una API key de DJI. No debe subirse al repositorio.

1. Copia `local.properties.example` como `local.properties`.
2. Ajusta `sdk.dir`.
3. Agrega tu API key:

```properties
dji.api.key=YOUR_DJI_API_KEY
```

`local.properties` esta ignorado por Git.

## Compilar

En Windows PowerShell:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

APK debug:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instalar en el telefono:

```powershell
& "C:\Users\maeca\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "C:\Users\maeca\Desktop\Codex\Sessions\DJIMiniBridge-DJI7\app\build\outputs\apk\debug\app-debug.apk"
```

## Flujo Android

1. `Preparar DJI`
2. `Conectar dron`
3. `Iniciar video`
4. `Iniciar puente PC`

Para pruebas reales, el telefono se conecta al control DJI por USB y la PC se conecta al telefono por WiFi.

## Python

Usa la IP que muestra la app al tocar `Iniciar puente PC`.

```powershell
python python_bridge\bridge_client.py IP_DEL_TELEFONO
python python_bridge\bridge_ws_client.py IP_DEL_TELEFONO --seconds 20
python python_bridge\dji_console.py IP_DEL_TELEFONO
```

Con el Python incluido en Codex:

```powershell
C:\Users\maeca\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe C:\Users\maeca\Desktop\Codex\Sessions\DJIMiniBridge-DJI7\python_bridge\dji_console.py IP_DEL_TELEFONO
```

## Comandos WebSocket

Operativos:

```text
get_status
start_video
stop_video
stop_test
disconnect_drone
```

Virtual Stick y seguridad:

```text
check_virtual_stick
enable_virtual_stick
disable_virtual_stick
can_accept_flight_command
send_zero_stick
emergency_stop
```

`send_zero_stick` no mueve el dron: envia pitch, roll, yaw y vertical en cero durante 3 segundos.

## Reglas importantes

- No cargar `Helper.install(...)` automaticamente en `Application`.
- No registrar DJI ni conectar dron automaticamente al abrir la app.
- No cambiar `compileOnly("com.dji:dji-sdk-provided:4.16.4")` a `implementation`.
- No usar `GONE` ni `INVISIBLE` sobre el `TextureView`; usar `alpha`.
- No enviar movimiento real sin nueva fase, limites y prueba segura.
- Mantener `emergency_stop` disponible antes de cualquier comando de vuelo.
- No trackear `tmp_dji_aar/`, `build/`, `.gradle/`, `local.properties` ni `__pycache__/`.
