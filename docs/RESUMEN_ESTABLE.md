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
& "C:\Users\maeca\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "C:\Users\maeca\Desktop\Codex\Sessions\DJIMiniBridge-DJI7\app\build\outputs\apk\debug\app-debug.apk"
```

## App Android estable

La aplicacion quedo estable cuando se separaron las acciones DJI por botones manuales.

Flujo estable:

1. `Preparar DJI`
2. `Conectar dron`
3. `Iniciar video`
4. `Iniciar puente PC`
5. `Detener prueba`
6. `Desconectar dron`
7. `Cerrar app`

La app no debe intentar conectar automaticamente al abrir. Eso fue importante para evitar comportamientos inestables.

La app mantiene la pantalla encendida mientras esta abierta para evitar timeout durante pruebas.

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
Invoke-RestMethod http://IP_DEL_TELEFONO:8765/status
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
virtualStickAvailable
virtualStickEnabled
flightControllerReady
lastCommandAudit
```

## Scripts Python

Se agregaron clientes Python para consultar el telefono:

- `python_bridge/bridge_client.py`
- `python_bridge/bridge_ws_client.py`
- `python_bridge/dji_console.py`

Uso esperado:

```powershell
python python_bridge\bridge_client.py IP_DEL_TELEFONO
python python_bridge\bridge_ws_client.py IP_DEL_TELEFONO --seconds 20
python python_bridge\dji_console.py IP_DEL_TELEFONO
```

La IP debe ser la que muestra la app Android al iniciar el puente PC.

Menu actual de la consola:

```text
1. Ver estado
2. Escuchar telemetria
3. Iniciar video
4. Detener video
5. Detener prueba
6. Desconectar dron
7. Revisar Virtual Stick
8. Activar Virtual Stick
9. Desactivar Virtual Stick
10. Revisar seguridad vuelo
11. Enviar cero Virtual Stick
12. Parada emergencia
13. Salir
```

## V2 segura confirmada

Se confirmo una primera capa de control seguro desde Python:

- `check_virtual_stick`
- `enable_virtual_stick`
- `disable_virtual_stick`
- `can_accept_flight_command`
- `send_zero_stick`
- `emergency_stop`

`send_zero_stick` envia pitch, roll, yaw y vertical en cero por 3 segundos. No mueve el dron.

Antes de comandos de vuelo, Android valida:

- dron conectado
- `FlightController` listo
- Virtual Stick disponible
- Virtual Stick activo
- bateria minima
- telemetria reciente
- altura dentro de limite
- distancia dentro de limite
- comando permitido
- duracion y velocidad maximas

## Auditoria de comandos

Cada comando WebSocket genera auditoria simple:

- comando recibido
- hora
- origen
- estado del dron
- estado Virtual Stick
- decision aceptado/rechazado
- razon
- respuesta enviada a Python

La ultima linea queda disponible en `lastCommandAudit`.

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
- Comunicacion WebSocket funcionando.
- Comandos operativos desde Python funcionando.
- Virtual Stick se puede revisar, activar y desactivar.
- Validacion de seguridad antes de vuelo funcionando.
- `send_zero_stick` funcionando sin movimiento real.
- `emergency_stop` funcionando.
- Auditoria de comandos funcionando.
- Flujo sin depender del USB para PC durante prueba real.

Esta es la base estable que no se debe romper al agregar control desde Python.
