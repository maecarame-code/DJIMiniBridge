package com.example.djiminibridge;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.secneo.sdk.Helper;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.battery.BatteryState;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.model.LocationCoordinate2D;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_DJI_PERMISSIONS = 1001;
    private static final int MIN_FLIGHT_COMMAND_BATTERY_PERCENT = 25;
    private static final long MAX_TELEMETRY_AGE_MS = 3000;
    private static final float MAX_SAFE_ALTITUDE_M = 30f;
    private static final float MAX_SAFE_DISTANCE_M = 50f;
    private static final int MAX_FLIGHT_COMMAND_DURATION_SECONDS = 3;
    private static final float MAX_FLIGHT_COMMAND_SPEED = 0.5f;
    private static final long ZERO_STICK_INTERVAL_MS = 100L;

    private static final String[] REQUESTED_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE
    };

    private TextView status;
    private Button startButton;
    private Button loadDjiButton;
    private Button registerDjiButton;
    private Button connectDroneButton;
    private Button startVideoButton;
    private Button closeTestButton;
    private Button bridgeButton;
    private TextureView videoView;
    private DjiVideoPreview videoPreview;
    private TextView summaryStatus;
    private BaseProduct connectedProduct;
    private Battery connectedBattery;
    private FlightController connectedFlightController;
    private BridgeHttpServer bridgeServer;
    private BridgeWebSocketServer bridgeWebSocketServer;
    private volatile String bridgeConnection = "sin conectar";
    private volatile String bridgeModel = "--";
    private volatile int bridgeBatteryPercent = -1;
    private volatile int bridgeBatteryVoltage = 0;
    private volatile float bridgeAltitude = 0f;
    private volatile float bridgeDistance = 0f;
    private volatile float bridgeVerticalSpeed = 0f;
    private volatile float bridgeHorizontalSpeed = 0f;
    private volatile String visibleBridgeHttp = "--";
    private volatile String visibleBridgeWs = "--";
    private volatile boolean virtualStickAvailable;
    private volatile boolean virtualStickEnabled;
    private volatile boolean flightControllerReady;
    private volatile long lastTelemetryAtMs;
    private boolean registroSolicitado;
    private boolean djiCargado;
    private boolean djiRegistrado;
    private boolean dronConectado;
    private boolean videoIniciado;
    private boolean preparacionEnCurso;
    private final Handler preparacionHandler = new Handler(Looper.getMainLooper());
    private final Object zeroStickLock = new Object();
    private volatile boolean zeroStickActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.setBackgroundColor(Color.rgb(12, 15, 18));

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setText("DJI Mini Bridge listo");
        status.setTextSize(16);
        status.setTextColor(Color.WHITE);
        status.setBackground(crearFondo(Color.rgb(45, 150, 20), dp(4)));
        status.setPadding(dp(8), dp(8), dp(8), dp(8));

        videoView = new TextureView(this);
        videoView.setAlpha(0f);
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
        );

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(8), dp(8), dp(8), dp(8));
        controls.setBackground(crearFondo(Color.rgb(22, 27, 32), dp(6)));
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                dp(230),
                LinearLayout.LayoutParams.MATCH_PARENT
        );

        startButton = new Button(this);
        startButton.setText("Preparar DJI");
        startButton.setOnClickListener(view -> prepararDJI());
        prepararBotonPanel(startButton);

        loadDjiButton = new Button(this);
        loadDjiButton.setText("Cargar DJI");
        loadDjiButton.setOnClickListener(view -> cargarDJI());
        prepararBotonPanel(loadDjiButton);

        registerDjiButton = new Button(this);
        registerDjiButton.setText("Registrar DJI");
        registerDjiButton.setOnClickListener(view -> iniciarDJI());
        prepararBotonPanel(registerDjiButton);

        connectDroneButton = new Button(this);
        connectDroneButton.setText("Conectar dron");
        connectDroneButton.setOnClickListener(view -> alternarConexionDron());
        prepararBotonPanel(connectDroneButton);

        startVideoButton = new Button(this);
        startVideoButton.setText("Iniciar video");
        startVideoButton.setOnClickListener(view -> alternarVideo());
        prepararBotonPanel(startVideoButton);

        closeTestButton = new Button(this);
        closeTestButton.setText("Cerrar app");
        closeTestButton.setOnClickListener(view -> cerrarApp());
        prepararBotonPanel(closeTestButton);

        bridgeButton = new Button(this);
        bridgeButton.setText("Iniciar puente PC");
        bridgeButton.setOnClickListener(view -> alternarPuentePC());
        prepararBotonPanel(bridgeButton);

        summaryStatus = crearLineaEstado(crearResumenEstado());
        summaryStatus.setTextSize(15);

        root.addView(videoView, videoParams);
        controls.addView(status, fixedHeight(dp(32)));
        controls.addView(summaryStatus, fixedHeight(dp(132)));
        controls.addView(startButton, fixedHeight(dp(31)));
        controls.addView(connectDroneButton, fixedHeight(dp(31)));
        controls.addView(startVideoButton, fixedHeight(dp(31)));
        controls.addView(bridgeButton, fixedHeight(dp(31)));
        controls.addView(closeTestButton, fixedHeight(dp(31)));
        root.addView(controls, controlsParams);
        setContentView(root);
    }

    private TextView crearLineaEstado(String text) {
        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.WHITE);
        view.setPadding(dp(8), dp(7), dp(8), dp(7));
        view.setBackground(crearFondo(Color.rgb(32, 38, 45), dp(4)));
        return view;
    }

    private void prepararBotonPanel(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setBackground(crearFondo(Color.argb(190, 42, 48, 56), dp(4)));
        button.setMinHeight(dp(31));
        button.setPadding(dp(6), 0, dp(6), 0);
    }

    private GradientDrawable crearFondo(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        background.setStroke(dp(1), Color.argb(120, 255, 255, 255));
        return background;
    }

    private LinearLayout.LayoutParams matchWidth() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams fixedHeight(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
        );
        params.setMargins(0, 0, 0, dp(3));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void actualizarResumenEstado() {
        if (summaryStatus != null) {
            summaryStatus.setText(crearResumenEstado());
        }
    }

    private String crearResumenEstado() {
        String batteryText = bridgeBatteryPercent >= 0
                ? bridgeBatteryPercent + "% | " + bridgeBatteryVoltage + " mV"
                : "--";

        return String.format(
                java.util.Locale.US,
                "Con: %s | Bat: %s\nH %.1fm | D %.1fm\nVS %.1f | HS %.1f\nHTTP: %s\nWS: %s",
                bridgeConnection,
                batteryText,
                bridgeAltitude,
                bridgeDistance,
                bridgeVerticalSpeed,
                bridgeHorizontalSpeed,
                visibleBridgeHttp,
                visibleBridgeWs
        );
    }

    private void prepararDJI() {
        if (preparacionEnCurso) {
            status.setText("Preparacion DJI en curso...");
            return;
        }

        preparacionEnCurso = true;
        startButton.setEnabled(false);
        status.setText("1/3 Prueba local...");
        preparacionHandler.postDelayed(() -> {
            status.setText("1/3 Prueba local OK");
            preparacionHandler.postDelayed(this::prepararPasoCargarDJI, 700L);
        }, 700L);
    }

    private void prepararPasoCargarDJI() {
        status.setText("2/3 Cargando DJI...");
        if (!cargarDJIInterno()) {
            finalizarPreparacion();
            return;
        }

        status.setText("2/3 Cargando DJI OK");
        preparacionHandler.postDelayed(this::prepararPasoRegistrarDJI, 700L);
    }

    private void prepararPasoRegistrarDJI() {
        status.setText("3/3 Registrando DJI...");
        preparacionHandler.postDelayed(() -> {
            if (!djiCargado) {
                status.setText("2/3 Cargando DJI fallo");
                finalizarPreparacion();
                return;
            }

            if (registroSolicitado || djiRegistrado) {
                status.setText(djiRegistrado ? "3/3 Registrando DJI OK" : "3/3 Registro DJI en curso...");
                finalizarPreparacion();
                return;
            }

            if (tienePermisosRequeridosDJI()) {
                registrarDJI();
                return;
            }

            status.setText("3/3 Esperando permisos DJI...");
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, REQUEST_DJI_PERMISSIONS);
        }, 700L);
    }

    private void finalizarPreparacion() {
        preparacionEnCurso = false;
        startButton.setEnabled(true);
    }

    private void cargarDJI() {
        if (cargarDJIInterno()) {
            status.setText(djiCargado ? "Loader DJI cargado" : "DJI ya fue cargado");
        }
    }

    private boolean cargarDJIInterno() {
        try {
            if (djiCargado) {
                return true;
            }
            Helper.install(getApplication());
            djiCargado = true;
            return true;
        } catch (Throwable throwable) {
            status.setText("2/3 Cargando DJI fallo: " + throwable.getClass().getSimpleName());
            return false;
        }
    }

    private void iniciarDJI() {
        if (!djiCargado) {
            status.setText("Primero toca Cargar DJI");
            return;
        }

        if (registroSolicitado) {
            status.setText("DJI SDK ya se esta iniciando...");
            return;
        }

        if (tienePermisosRequeridosDJI()) {
            status.setText("Iniciando DJI SDK...");
            registrarDJI();
        } else {
            status.setText("Esperando permisos DJI...");
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, REQUEST_DJI_PERMISSIONS);
        }
    }

    private boolean tienePermisosRequeridosDJI() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_DJI_PERMISSIONS && tienePermisosRequeridosDJI()) {
            status.setText(preparacionEnCurso ? "3/3 Registrando DJI..." : "Iniciando DJI SDK...");
            registrarDJI();
        } else {
            status.setText(preparacionEnCurso ? "3/3 Permisos DJI requeridos" : "Permisos DJI requeridos");
            finalizarPreparacion();
        }
    }

    private void registrarDJI() {
        registroSolicitado = true;
        startButton.setEnabled(false);

        DJISDKManager.getInstance().registerApp(
                getApplicationContext(),
                new DJISDKManager.SDKManagerCallback() {

                    @Override
                    public void onRegister(DJIError error) {
        if (error == DJISDKError.REGISTRATION_SUCCESS) {
                            runOnUiThread(() -> {
                                status.setText(preparacionEnCurso
                                        ? "3/3 Registrando DJI OK"
                                        : "DJI SDK registrado correctamente");
                                djiRegistrado = true;
                                finalizarPreparacion();
                            });

                        } else {
                            runOnUiThread(() -> {
                                status.setText(preparacionEnCurso
                                        ? "3/3 Registrando DJI fallo: " + error.getDescription()
                                        : "Error DJI SDK: " + error.getDescription());
                                finalizarPreparacion();
                            });
                        }
                    }

                    @Override
                    public void onProductConnect(BaseProduct product) {
                        connectedProduct = product;
                        dronConectado = true;
                        runOnUiThread(() -> {
                            status.setText("Drone conectado");
                            actualizarBotonConexion();
                            actualizarEstado();
                        });
                    }

                    @Override
                    public void onProductDisconnect() {
                        connectedProduct = null;
                        dronConectado = false;
                        detenerVideo();
                        runOnUiThread(() -> {
                            status.setText("Drone desconectado");
                            limpiarLecturaBateria();
                            limpiarLecturaTelemetria();
                            actualizarBotonConexion();
                            actualizarBotonVideo();
                            bridgeConnection = "desconectado";
                            bridgeModel = "--";
                            bridgeBatteryPercent = -1;
                            bridgeBatteryVoltage = 0;
                            limpiarDatosTelemetria();
                            limpiarEstadoVirtualStick();
                            actualizarResumenEstado();
                        });
                    }

                    @Override
                    public void onProductChanged(BaseProduct product) {}

                    @Override
                    public void onComponentChange(
                            BaseProduct.ComponentKey key,
                            BaseComponent oldComponent,
                            BaseComponent newComponent) {
                    }

                    @Override
                    public void onInitProcess(DJISDKInitEvent event, int totalProcess) {}

                    @Override
                    public void onDatabaseDownloadProgress(long current, long total) {}
                }
        );
    }

    private void alternarConexionDron() {
        if (dronConectado || (connectedProduct != null && connectedProduct.isConnected())) {
            desconectarDron();
        } else {
            conectarDron();
        }
    }

    private void conectarDron() {
        if (!djiRegistrado) {
            status.setText("Primero registra DJI");
            return;
        }

        connectedProduct = DJISDKManager.getInstance().getProduct();
        if (connectedProduct != null && connectedProduct.isConnected()) {
            dronConectado = true;
            status.setText("Drone conectado");
            actualizarBotonConexion();
            actualizarEstado();
            return;
        }

        status.setText("Conectando al dron...");
        DJISDKManager.getInstance().startConnectionToProduct();
    }

    private void actualizarEstado() {
        BaseProduct product = connectedProduct;
        if (product == null) {
            product = DJISDKManager.getInstance().getProduct();
            connectedProduct = product;
        }

        if (product == null) {
            limpiarLecturaTelemetria();
            bridgeConnection = "sin producto";
            bridgeModel = "--";
            bridgeBatteryPercent = -1;
            bridgeBatteryVoltage = 0;
            limpiarDatosTelemetria();
            limpiarEstadoVirtualStick();
            actualizarResumenEstado();
            return;
        }

        bridgeConnection = product.isConnected() ? "conectado" : "desconectado";
        bridgeModel = String.valueOf(product.getModel());
        actualizarResumenEstado();
        activarLecturaTelemetria(product);

        Battery battery = product.getBattery();
        if (battery == null) {
            limpiarLecturaBateria();
            bridgeBatteryPercent = -1;
            bridgeBatteryVoltage = 0;
            actualizarResumenEstado();
            return;
        }

        connectedBattery = battery;
        battery.setStateCallback(new BatteryState.Callback() {
            @Override
            public void onUpdate(BatteryState batteryState) {
                bridgeBatteryPercent = batteryState.getChargeRemainingInPercent();
                bridgeBatteryVoltage = batteryState.getVoltage();
                runOnUiThread(() -> actualizarResumenEstado());
            }
        });
        actualizarResumenEstado();
    }

    private void activarLecturaTelemetria(BaseProduct product) {
        if (!(product instanceof Aircraft)) {
            limpiarLecturaTelemetria();
            limpiarDatosTelemetria();
            limpiarEstadoVirtualStick();
            actualizarResumenEstado();
            return;
        }

        FlightController flightController = ((Aircraft) product).getFlightController();
        if (flightController == null) {
            limpiarLecturaTelemetria();
            limpiarDatosTelemetria();
            limpiarEstadoVirtualStick();
            actualizarResumenEstado();
            return;
        }

        connectedFlightController = flightController;
        flightControllerReady = true;
        virtualStickAvailable = flightController.isVirtualStickControlModeAvailable();
        flightController.setStateCallback(new FlightControllerState.Callback() {
            @Override
            public void onUpdate(FlightControllerState state) {
                runOnUiThread(() -> actualizarTelemetria(state));
            }
        });
        actualizarResumenEstado();
    }

    private void actualizarTelemetria(FlightControllerState state) {
        LocationCoordinate3D aircraftLocation = state.getAircraftLocation();
        float altitude = aircraftLocation != null ? aircraftLocation.getAltitude() : 0f;
        float verticalSpeed = state.getVelocityZ();
        float horizontalSpeed = (float) Math.sqrt(
                state.getVelocityX() * state.getVelocityX()
                        + state.getVelocityY() * state.getVelocityY()
        );
        float distance = calcularDistanciaHome(state.getHomeLocation(), aircraftLocation);
        bridgeAltitude = altitude;
        bridgeDistance = distance;
        bridgeVerticalSpeed = verticalSpeed;
        bridgeHorizontalSpeed = horizontalSpeed;
        lastTelemetryAtMs = System.currentTimeMillis();

        actualizarResumenEstado();
    }

    private float calcularDistanciaHome(LocationCoordinate2D home, LocationCoordinate3D aircraftLocation) {
        if (home == null || aircraftLocation == null || !home.isValid()
                || !LocationCoordinate2D.isValid(aircraftLocation.getLatitude(), aircraftLocation.getLongitude())) {
            return 0f;
        }

        double earthRadiusMeters = 6371000.0;
        double lat1 = Math.toRadians(home.getLatitude());
        double lat2 = Math.toRadians(aircraftLocation.getLatitude());
        double deltaLat = Math.toRadians(aircraftLocation.getLatitude() - home.getLatitude());
        double deltaLon = Math.toRadians(aircraftLocation.getLongitude() - home.getLongitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (float) (earthRadiusMeters * c);
    }

    private void iniciarVideo() {
        if (!dronConectado) {
            status.setText("Primero conecta el dron");
            return;
        }
        if (videoPreview == null) {
            videoPreview = new DjiVideoPreview(this, videoView);
        }
        videoView.setAlpha(1f);
        videoIniciado = videoPreview.start();
        if (!videoIniciado) {
            videoView.setAlpha(0f);
        }
        status.setText(videoIniciado ? "Video iniciado" : "Video no esta listo aun");
        actualizarBotonVideo();
    }

    private void alternarVideo() {
        if (videoIniciado) {
            detenerVideo();
            status.setText("Video detenido");
        } else {
            iniciarVideo();
        }
    }

    private void detenerVideo() {
        if (videoPreview != null) {
            videoPreview.stop();
        }
        videoIniciado = false;
        videoView.setAlpha(0f);
        actualizarBotonVideo();
    }

    private void detenerPrueba() {
        detenerVideo();
        limpiarLecturaBateria();
        limpiarLecturaTelemetria();
        connectedProduct = DJISDKManager.getInstance().getProduct();
        dronConectado = connectedProduct != null && connectedProduct.isConnected();
        actualizarBotonConexion();
        actualizarBotonVideo();
        bridgeConnection = dronConectado ? "conectado" : "sin conectar";
        bridgeModel = "--";
        bridgeBatteryPercent = -1;
        bridgeBatteryVoltage = 0;
        limpiarDatosTelemetria();
        limpiarEstadoVirtualStick();
        actualizarResumenEstado();
        status.setText("Prueba detenida");
    }

    private void desconectarDron() {
        detenerVideo();
        limpiarLecturaBateria();
        limpiarLecturaTelemetria();

        try {
            DJISDKManager.getInstance().stopConnectionToProduct();
        } catch (Throwable throwable) {
            status.setText("No se pudo desconectar: " + throwable.getClass().getSimpleName());
            return;
        }

        connectedProduct = null;
        dronConectado = false;
        actualizarBotonConexion();
        actualizarBotonVideo();
        bridgeConnection = "desconectado";
        bridgeModel = "--";
        bridgeBatteryPercent = -1;
        bridgeBatteryVoltage = 0;
        limpiarDatosTelemetria();
        limpiarEstadoVirtualStick();
        actualizarResumenEstado();
        status.setText("Dron desconectado");
    }

    private void actualizarBotonConexion() {
        if (connectDroneButton != null) {
            connectDroneButton.setText(dronConectado ? "Desconectar dron" : "Conectar dron");
        }
    }

    private void actualizarBotonVideo() {
        if (startVideoButton != null) {
            startVideoButton.setText(videoIniciado ? "Detener video" : "Iniciar video");
        }
    }

    private void cerrarApp() {
        detenerPrueba();
        detenerPuentePC();
        status.setText("Cerrando app...");
        finishAndRemoveTask();
    }

    private void alternarPuentePC() {
        if ((bridgeServer != null && bridgeServer.isRunning())
                || (bridgeWebSocketServer != null && bridgeWebSocketServer.isRunning())) {
            detenerPuentePC();
            status.setText("Puente PC detenido");
            return;
        }

        bridgeServer = new BridgeHttpServer(8765, this::getBridgeStatusJson);
        bridgeWebSocketServer = new BridgeWebSocketServer(8766, this::getBridgeStatusJson, this::manejarComandoPuente);

        boolean httpStarted = bridgeServer.start();
        boolean webSocketStarted = bridgeWebSocketServer.start();
        if (httpStarted && webSocketStarted) {
            String phoneIp = obtenerIpTelefono();
            visibleBridgeHttp = phoneIp + ":8765";
            visibleBridgeWs = phoneIp + ":8766";
            actualizarResumenEstado();
            bridgeButton.setText("Detener puente PC");
            status.setText("Puente PC activo");
        } else {
            detenerPuentePC();
            visibleBridgeHttp = "error";
            visibleBridgeWs = "error";
            actualizarResumenEstado();
            status.setText("No se pudo iniciar puente PC");
        }
    }

    private void detenerPuentePC() {
        if (bridgeServer != null) {
            bridgeServer.stop();
            bridgeServer = null;
        }
        if (bridgeWebSocketServer != null) {
            bridgeWebSocketServer.stop();
            bridgeWebSocketServer = null;
        }
        visibleBridgeHttp = "--";
        visibleBridgeWs = "--";
        actualizarResumenEstado();
        if (bridgeButton != null) {
            bridgeButton.setText("Iniciar puente PC");
        }
    }

    private String getBridgeStatusJson() {
        return "{"
                + "\"connection\":\"" + jsonEscape(bridgeConnection) + "\","
                + "\"model\":\"" + jsonEscape(bridgeModel) + "\","
                + "\"batteryPercent\":" + bridgeBatteryPercent + ","
                + "\"batteryVoltageMv\":" + bridgeBatteryVoltage + ","
                + "\"altitudeM\":" + formatNumber(bridgeAltitude) + ","
                + "\"distanceM\":" + formatNumber(bridgeDistance) + ","
                + "\"verticalSpeed\":" + formatNumber(bridgeVerticalSpeed) + ","
                + "\"horizontalSpeed\":" + formatNumber(bridgeHorizontalSpeed) + ","
                + "\"videoActive\":" + videoIniciado + ","
                + "\"virtualStickAvailable\":" + virtualStickAvailable + ","
                + "\"virtualStickEnabled\":" + virtualStickEnabled + ","
                + "\"flightControllerReady\":" + flightControllerReady
                + "}";
    }

    private String manejarComandoPuente(String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase(java.util.Locale.US);
        if ("get_status".equals(normalized)) {
            return "{\"type\":\"status\",\"data\":" + getBridgeStatusJson() + "}";
        }

        if ("start_video".equals(normalized)) {
            if (!dronConectado) {
                return crearRespuestaComando(false, "start_video", "dron no conectado");
            }
            runOnUiThread(this::iniciarVideo);
            return crearRespuestaComando(true, "start_video", "video iniciado");
        }

        if ("stop_video".equals(normalized)) {
            if (!videoIniciado) {
                return crearRespuestaComando(true, "stop_video", "video ya estaba detenido");
            }
            runOnUiThread(this::detenerVideo);
            return crearRespuestaComando(true, "stop_video", "video detenido");
        }

        if ("stop_test".equals(normalized)) {
            runOnUiThread(this::detenerPrueba);
            return crearRespuestaComando(true, "stop_test", "prueba detenida");
        }

        if ("disconnect_drone".equals(normalized)) {
            if (!dronConectado && (connectedProduct == null || !connectedProduct.isConnected())) {
                return crearRespuestaComando(false, "disconnect_drone", "dron no conectado");
            }
            runOnUiThread(this::desconectarDron);
            return crearRespuestaComando(true, "disconnect_drone", "dron desconectado");
        }

        if ("check_virtual_stick".equals(normalized)) {
            return manejarCheckVirtualStick();
        }

        if ("enable_virtual_stick".equals(normalized)) {
            return manejarSetVirtualStick(true, "enable_virtual_stick");
        }

        if ("disable_virtual_stick".equals(normalized)) {
            return manejarSetVirtualStick(false, "disable_virtual_stick");
        }

        if ("can_accept_flight_command".equals(normalized)) {
            return manejarCanAcceptFlightCommand();
        }

        if ("send_zero_stick".equals(normalized)) {
            return manejarSendZeroStick();
        }

        if ("emergency_stop".equals(normalized)) {
            return manejarEmergencyStop();
        }

        return crearRespuestaComando(false, normalized, "comando desconocido");
    }

    private String manejarCheckVirtualStick() {
        FlightController flightController = obtenerFlightControllerVirtualStick();
        if (flightController == null) {
            limpiarEstadoVirtualStick();
            return crearRespuestaComando(false, "check_virtual_stick", "flight controller no disponible");
        }

        flightControllerReady = true;
        virtualStickAvailable = flightController.isVirtualStickControlModeAvailable();

        return crearRespuestaComando(
                virtualStickAvailable,
                "check_virtual_stick",
                "available=" + virtualStickAvailable
                        + ", enabled=" + virtualStickEnabled
                        + ", flightControllerReady=" + flightControllerReady
        );
    }

    private String manejarSetVirtualStick(boolean enabled, String command) {
        FlightController flightController = obtenerFlightControllerVirtualStick();
        if (flightController == null) {
            limpiarEstadoVirtualStick();
            return crearRespuestaComando(false, command, "flight controller no disponible");
        }

        flightControllerReady = true;
        virtualStickAvailable = flightController.isVirtualStickControlModeAvailable();
        if (!virtualStickAvailable) {
            virtualStickEnabled = false;
            return crearRespuestaComando(false, command, "virtual stick no disponible");
        }

        if (enabled) {
            configurarModosVirtualStick(flightController);
        }

        AtomicReference<DJIError> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        flightController.setVirtualStickModeEnabled(enabled, error -> {
            result.set(error);
            latch.countDown();
        });

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                return crearRespuestaComando(false, command, "timeout configurando virtual stick");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return crearRespuestaComando(false, command, "interrumpido configurando virtual stick");
        }

        DJIError error = result.get();
        if (error != null) {
            virtualStickEnabled = !enabled && virtualStickEnabled;
            return crearRespuestaComando(false, command, error.getDescription());
        }

        virtualStickEnabled = enabled;
        return crearRespuestaComando(
                true,
                command,
                enabled ? "virtual stick activado sin movimiento" : "virtual stick desactivado"
        );
    }

    private FlightController obtenerFlightControllerVirtualStick() {
        if (connectedFlightController != null) {
            return connectedFlightController;
        }

        BaseProduct product = connectedProduct;
        if (product == null) {
            product = DJISDKManager.getInstance().getProduct();
            connectedProduct = product;
        }

        if (!(product instanceof Aircraft)) {
            return null;
        }

        FlightController flightController = ((Aircraft) product).getFlightController();
        connectedFlightController = flightController;
        return flightController;
    }

    private void configurarModosVirtualStick(FlightController flightController) {
        flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
        flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
        flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
        flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
    }

    private String manejarCanAcceptFlightCommand() {
        String rejection = validarComandoVueloSeguro("send_zero_stick", MAX_FLIGHT_COMMAND_DURATION_SECONDS, 0f);
        return crearRespuestaComando(
                rejection == null,
                "can_accept_flight_command",
                rejection == null ? "comando de vuelo permitido por validaciones actuales" : rejection
        );
    }

    private String validarComandoVueloSeguro(String command, int durationSeconds, float maxSpeed) {
        if (!"send_zero_stick".equals(command)) {
            return "comando no permitido: " + command;
        }
        if (durationSeconds <= 0 || durationSeconds > MAX_FLIGHT_COMMAND_DURATION_SECONDS) {
            return "duracion fuera de limite";
        }
        if (maxSpeed < 0f || maxSpeed > MAX_FLIGHT_COMMAND_SPEED) {
            return "velocidad fuera de limite";
        }
        if (!dronConectado || connectedProduct == null || !connectedProduct.isConnected()) {
            return "dron no conectado";
        }
        if (!flightControllerReady || connectedFlightController == null) {
            return "flight controller no listo";
        }
        if (!virtualStickAvailable) {
            return "virtual stick no disponible";
        }
        if (!virtualStickEnabled) {
            return "virtual stick no activo";
        }
        if (bridgeBatteryPercent < MIN_FLIGHT_COMMAND_BATTERY_PERCENT) {
            return "bateria baja o desconocida";
        }
        if (lastTelemetryAtMs <= 0 || System.currentTimeMillis() - lastTelemetryAtMs > MAX_TELEMETRY_AGE_MS) {
            return "telemetria no reciente";
        }
        if (bridgeAltitude < 0f || bridgeAltitude > MAX_SAFE_ALTITUDE_M) {
            return "altura fuera de limite";
        }
        if (bridgeDistance < 0f || bridgeDistance > MAX_SAFE_DISTANCE_M) {
            return "distancia fuera de limite";
        }
        return null;
    }

    private String manejarSendZeroStick() {
        synchronized (zeroStickLock) {
            if (zeroStickActive) {
                return crearRespuestaComando(false, "send_zero_stick", "send_zero_stick ya esta activo");
            }
            zeroStickActive = true;
        }

        String rejection = validarComandoVueloSeguro("send_zero_stick", MAX_FLIGHT_COMMAND_DURATION_SECONDS, 0f);
        if (rejection != null) {
            zeroStickActive = false;
            return crearRespuestaComando(false, "send_zero_stick", rejection);
        }

        FlightController flightController = obtenerFlightControllerVirtualStick();
        if (flightController == null) {
            zeroStickActive = false;
            limpiarEstadoVirtualStick();
            return crearRespuestaComando(false, "send_zero_stick", "flight controller no disponible");
        }

        int sends = (int) ((MAX_FLIGHT_COMMAND_DURATION_SECONDS * 1000L) / ZERO_STICK_INTERVAL_MS);
        for (int index = 0; index < sends; index++) {
            if (!zeroStickActive) {
                return crearRespuestaComando(false, "send_zero_stick", "detenido por emergency_stop");
            }

            DJIError error = enviarCeroVirtualStick(flightController, 1);
            if (error != null) {
                zeroStickActive = false;
                manejarEmergencyStop();
                return crearRespuestaComando(false, "send_zero_stick", "error enviando cero: " + error.getDescription());
            }

            try {
                Thread.sleep(ZERO_STICK_INTERVAL_MS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                zeroStickActive = false;
                manejarEmergencyStop();
                return crearRespuestaComando(false, "send_zero_stick", "interrumpido enviando cero");
            }
        }

        zeroStickActive = false;
        return crearRespuestaComando(true, "send_zero_stick", "ceros enviados por 3 segundos sin movimiento real");
    }

    private String manejarEmergencyStop() {
        zeroStickActive = false;
        FlightController flightController = obtenerFlightControllerVirtualStick();
        if (flightController == null) {
            runOnUiThread(this::detenerPrueba);
            return crearRespuestaComando(false, "emergency_stop", "flight controller no disponible; prueba detenida");
        }

        DJIError zeroError = enviarCeroVirtualStick(flightController, 2);
        if (zeroError == DJISDKError.COMMON_TIMEOUT) {
            runOnUiThread(this::detenerPrueba);
            return crearRespuestaComando(false, "emergency_stop", "timeout enviando cero; prueba detenida");
        }

        AtomicReference<DJIError> disableResult = new AtomicReference<>();
        CountDownLatch disableLatch = new CountDownLatch(1);
        flightController.setVirtualStickModeEnabled(false, error -> {
            disableResult.set(error);
            disableLatch.countDown();
        });

        try {
            disableLatch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        virtualStickEnabled = false;
        runOnUiThread(this::detenerPrueba);

        DJIError disableError = disableResult.get();
        if (zeroError != null) {
            return crearRespuestaComando(false, "emergency_stop", "cero fallo: " + zeroError.getDescription());
        }
        if (disableError != null) {
            return crearRespuestaComando(false, "emergency_stop", "cero enviado; desactivar fallo: " + disableError.getDescription());
        }
        return crearRespuestaComando(true, "emergency_stop", "cero enviado, virtual stick desactivado y prueba detenida");
    }

    private DJIError enviarCeroVirtualStick(FlightController flightController, int timeoutSeconds) {
        AtomicReference<DJIError> zeroResult = new AtomicReference<>();
        CountDownLatch zeroLatch = new CountDownLatch(1);
        flightController.sendVirtualStickFlightControlData(new FlightControlData(0f, 0f, 0f, 0f), error -> {
            zeroResult.set(error);
            zeroLatch.countDown();
        });

        try {
            if (!zeroLatch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                return DJIError.COMMON_TIMEOUT;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return DJIError.COMMON_SYSTEM_BUSY;
        }
        return zeroResult.get();
    }

    private String crearRespuestaComando(boolean ok, String command, String message) {
        return "{"
                + "\"type\":\"ack\","
                + "\"ok\":" + ok + ","
                + "\"command\":\"" + jsonEscape(command) + "\","
                + "\"message\":\"" + jsonEscape(message) + "\""
                + "}";
    }

    private String formatNumber(float value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String obtenerIpTelefono() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || wifiManager.getConnectionInfo() == null) {
            return "IP no disponible";
        }

        int ip = wifiManager.getConnectionInfo().getIpAddress();
        if (ip == 0) {
            return "IP no disponible";
        }

        return (ip & 0xff) + "."
                + ((ip >> 8) & 0xff) + "."
                + ((ip >> 16) & 0xff) + "."
                + ((ip >> 24) & 0xff);
    }

    private void limpiarLecturaBateria() {
        if (connectedBattery != null) {
            connectedBattery.setStateCallback(null);
            connectedBattery = null;
        }
    }

    private void limpiarLecturaTelemetria() {
        if (connectedFlightController != null) {
            connectedFlightController.setStateCallback(null);
            connectedFlightController = null;
        }
        flightControllerReady = false;
    }

    private void limpiarDatosTelemetria() {
        bridgeAltitude = 0f;
        bridgeDistance = 0f;
        bridgeVerticalSpeed = 0f;
        bridgeHorizontalSpeed = 0f;
        lastTelemetryAtMs = 0;
    }

    private void limpiarEstadoVirtualStick() {
        virtualStickAvailable = false;
        virtualStickEnabled = false;
        flightControllerReady = false;
    }

    @Override
    protected void onDestroy() {
        detenerVideo();
        limpiarLecturaBateria();
        limpiarLecturaTelemetria();
        detenerPuentePC();
        super.onDestroy();
    }
}

