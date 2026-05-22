# DJIMiniBridge

Puente experimental entre una PC con Python, un telefono Android, DJI Mobile SDK v4 y un dron DJI.

Arquitectura objetivo:

```text
Python en PC
  -> WiFi HTTP/WebSocket
    -> telefono Android
      -> DJI Mobile SDK
        -> control DJI
          -> dron
```

## Estado actual

- Android conecta con DJI SDK de forma manual y por etapas.
- Lee modelo, bateria y telemetria.
- Muestra video estable usando `TextureView`, `VideoFeeder` y `DJICodecManager`.
- Expone estado a la PC por HTTP en el puerto `8765`.
- Expone telemetria y comandos simples por WebSocket en el puerto `8766`.
- Incluye clientes Python en `python_bridge/`.

## Documentacion

- `docs/RESUMEN_ESTABLE.md`: resumen de la base estable confirmada.
- `docs/USO_DJI_SDK.md`: reglas y flujo estable del SDK DJI.
- `docs/PYTHON_BRIDGE.md`: comunicacion PC Python -> telefono Android.

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

## Instalar en Android

```powershell
& "C:\Users\maeca\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "C:\Users\maeca\Desktop\Codex\Sessions\DJIMiniBridge\app\build\outputs\apk\debug\app-debug.apk"
```

## Flujo Android

1. `Cargar DJI`
2. `Registrar DJI`
3. `Conectar dron`
4. `Iniciar video`
5. `Iniciar puente PC`

Para prueba real, el telefono se conecta al control DJI por USB y la PC se conecta al telefono por WiFi.

## Python

Estado por HTTP:

```powershell
python python_bridge\bridge_client.py 192.168.100.24
```

Telemetria WebSocket:

```powershell
python python_bridge\bridge_ws_client.py 192.168.100.24 --seconds 20
```

Consola:

```powershell
python python_bridge\dji_console.py 192.168.100.24
```

Usa la IP que muestra la app al tocar `Iniciar puente PC`.

Guia completa:

```text
docs/PYTHON_BRIDGE.md
```

## Reglas importantes

- No cargar `Helper.install(...)` automaticamente en `Application`.
- No iniciar registro/conexion DJI automaticamente al abrir la app.
- No cambiar `compileOnly("com.dji:dji-sdk-provided:4.16.4")` a `implementation`.
- No usar `GONE` ni `INVISIBLE` sobre el `TextureView` del video; usar `alpha`.
- No trackear `tmp_dji_aar/`, `build/`, `.gradle/`, `local.properties` ni `__pycache__/`.
