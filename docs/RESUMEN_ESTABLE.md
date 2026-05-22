# Resumen estable del proyecto DJIMiniBridge

Este documento resume lo que ya quedo funcionando de forma estable desde el inicio de las pruebas hasta el estado actual del proyecto.

## Objetivo del proyecto

Crear una aplicacion Android que sirva como puente entre:

```text
PC con Python
  -> WiFi HTTP/WebSocket
    -> telefono Android
      -> DJI Mobile SDK
        -> control DJI
          -> dron
```

La idea principal es que la PC pueda comunicarse con el telefono, y que el telefono use DJI Mobile SDK para leer estado del dron y, mas adelante, enviar instrucciones de forma controlada.

## Entorno estable confirmado

- Android Studio instalado.
- Java de Android Studio funcionando:

```text
C:\Program Files\Android\Android Studio\jbr
OpenJDK 21
```

- Gradle compila correctamente usando:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

- APK debug generado en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

- Instalacion por ADB confirmada usando:

```powershell
& "C:\Users\maeca\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "C:\Users\maeca\Desktop\Codex\Sessions\DJIMiniBridge\app\build\outputs\apk\debug\app-debug.apk"
```

## App Android estable

La aplicacion quedo estable cuando se separaron las acciones DJI por botones manuales.

Flujo estable:

1. `Cargar DJI`
2. `Registrar DJI`
3. `Conectar dron`
4. `Iniciar video`
5. `Iniciar puente PC`
6. `Detener prueba`
7. `Desconectar dron`
8. `Cerrar app`

La app no debe intentar conectar automaticamente al abrir. Eso fue importante para evitar comportamientos inestables.

## DJI SDK estable

Se usa DJI Mobile SDK v4.16.4.

Componentes confirmados:

- Registro manual del SDK.
- Conexion manual al producto DJI.
- Lectura de modelo del dron.
- Lectura de bateria.
- Lectura de voltaje.
- Lectura de telemetria basica.
- Lectura de altura.
- Lectura de distancia horizontal desde home.
- Lectura de velocidad vertical.
- Lectura de velocidad horizontal.

Reglas importantes:

- No mover `Helper.install(...)` al `Application`.
- No iniciar `registerApp(...)` automaticamente al abrir.
- No iniciar `startConnectionToProduct()` automaticamente al abrir.
- No cambiar `compileOnly("com.dji:dji-sdk-provided:4.16.4")` a `implementation`.

## Video estable

El video quedo funcionando usando:

- `TextureView`
- `VideoFeeder`
- `DJICodecManager`

Reglas que se aprendieron durante las pruebas:

- El video se debe iniciar manualmente.
- Si se detiene video, se debe desconectar el listener real de la camara.
- No basta con ocultar la imagen visualmente.
- No usar `GONE` ni `INVISIBLE` sobre el `TextureView`.
- Si se necesita ocultar el video, usar `alpha`.
- No poner overlays encima del video todavia.

## Orientacion de pantalla

La app debe trabajar en horizontal.

Cuando el telefono cambio de orientacion durante una prueba, los datos dejaron de mostrarse correctamente. Por estabilidad, la aplicacion se dejo fija en modo horizontal.

## Puente PC / telefono

Se confirmo que el USB del telefono debe quedar libre para el control DJI durante pruebas reales.

Por eso, la comunicacion PC -> telefono debe hacerse por WiFi.

La app Android expone:

- HTTP en puerto `8765`
- WebSocket en puerto `8766`

Endpoint estable:

```text
GET http://IP_DEL_TELEFONO:8765/status
```

Ejemplo confirmado:

```powershell
Invoke-RestMethod http://192.168.100.24:8765/status
```

Campos esperados:

```text
connection
model
batteryPercent
batteryVoltageMv
altitudeM
distanceM
verticalSpeed
horizontalSpeed
videoActive
```

## Scripts Python

Se agregaron clientes Python para consultar el telefono:

- `python_bridge/bridge_client.py`
- `python_bridge/bridge_ws_client.py`
- `python_bridge/dji_console.py`

Uso esperado:

```powershell
python python_bridge\bridge_client.py 192.168.100.24
python python_bridge\bridge_ws_client.py 192.168.100.24 --seconds 20
python python_bridge\dji_console.py 192.168.100.24
```

La IP debe ser la que muestra la app Android al iniciar el puente PC.

## Botones y comportamiento estable

`Detener prueba`:

- Detiene lectura activa de prueba.
- Detiene video.
- Detiene bateria.
- Detiene telemetria.
- Mantiene la aplicacion abierta.

`Desconectar dron`:

- Detiene la conexion DJI.
- Limpia estado del dron.
- Limpia video, bateria y telemetria.
- Es necesario porque `Reiniciar lectura` no aportaba valor.

`Cerrar app`:

- Detiene la prueba.
- Detiene el puente PC.
- Cierra la aplicacion.

## GitHub y limpieza de codigo

Se limpio el proyecto para GitHub.

Archivos que no deben subirse:

- `local.properties`
- `.gradle/`
- `build/`
- `app/build/`
- `.idea/`
- `tmp_dji_aar/`
- `__pycache__/`
- `*.pyc`

La API key real de DJI no debe estar en GitHub.

El proyecto usa:

- `local.properties` para desarrollo local.
- `local.properties.example` como plantilla segura.
- Variable de entorno `DJI_API_KEY` para servidores o integracion continua.

## Railway

Railway no es el entorno correcto para compilar esta app Android.

El error:

```text
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable
```

ocurre porque Railway no tiene Android SDK configurado por defecto.

Para este proyecto:

- GitHub guarda el codigo.
- CodeRabbit revisa el codigo.
- GitHub Actions seria mejor opcion si se quiere compilar APK en la nube.
- Railway solo tendria sentido mas adelante si se crea un backend web real.

## Flujo recomendado de trabajo

1. Mantener esta version como base estable.
2. Claude Code puede hacer cambios nuevos.
3. Codex audita los cambios antes de aceptarlos.
4. CodeRabbit CLI revisa antes de subir o antes de abrir PR.
5. Solo despues de pasar revision se sube a GitHub.

## Estado estable actual

Confirmado en pruebas:

- App abre estable.
- Permisos concedidos.
- Dron conectado.
- Lectura de conexion funcionando.
- Lectura de modelo funcionando.
- Lectura de bateria funcionando.
- Telemetria funcionando.
- Video estable funcionando.
- Puente PC por WiFi funcionando.
- Comunicacion HTTP funcionando.
- Flujo sin depender del USB para PC durante prueba real.

Esta es la base estable que no se debe romper al agregar control desde Python.
