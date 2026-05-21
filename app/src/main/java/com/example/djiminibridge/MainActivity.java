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
import android.view.Gravity;
import android.view.TextureView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.secneo.sdk.Helper;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.battery.BatteryState;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.model.LocationCoordinate2D;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_DJI_PERMISSIONS = 1001;

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
    private Button stopVideoButton;
    private Button closeTestButton;
    private Button disconnectDroneButton;
    private Button bridgeButton;
    private TextureView videoView;
    private DjiVideoPreview videoPreview;
    private TextView productStatus;
    private TextView modelStatus;
    private TextView batteryStatus;
    private TextView telemetryStatus;
    private TextView bridgeStatus;
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
    private boolean registroSolicitado;
    private boolean djiCargado;
    private boolean djiRegistrado;
    private boolean dronConectado;
    private boolean videoIniciado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        ScrollView controlsScroll = new ScrollView(this);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(12), dp(12), dp(12));
        controls.setBackground(crearFondo(Color.rgb(22, 27, 32), dp(6)));
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                dp(300),
                LinearLayout.LayoutParams.MATCH_PARENT
        );

        startButton = new Button(this);
        startButton.setText("Prueba local");
        startButton.setOnClickListener(view -> status.setText("Pantalla estable"));
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
        connectDroneButton.setOnClickListener(view -> conectarDron());
        prepararBotonPanel(connectDroneButton);

        startVideoButton = new Button(this);
        startVideoButton.setText("Iniciar video");
        startVideoButton.setOnClickListener(view -> iniciarVideo());
        prepararBotonPanel(startVideoButton);

        stopVideoButton = new Button(this);
        stopVideoButton.setText("Detener prueba");
        stopVideoButton.setOnClickListener(view -> detenerPrueba());
        prepararBotonPanel(stopVideoButton);

        closeTestButton = new Button(this);
        closeTestButton.setText("Cerrar app");
        closeTestButton.setOnClickListener(view -> cerrarApp());
        prepararBotonPanel(closeTestButton);

        disconnectDroneButton = new Button(this);
        disconnectDroneButton.setText("Desconectar dron");
        disconnectDroneButton.setOnClickListener(view -> desconectarDron());
        prepararBotonPanel(disconnectDroneButton);

        bridgeButton = new Button(this);
        bridgeButton.setText("Iniciar puente PC");
        bridgeButton.setOnClickListener(view -> alternarPuentePC());
        prepararBotonPanel(bridgeButton);

        productStatus = crearLineaEstado("Conexion: sin conectar");
        modelStatus = crearLineaEstado("Modelo: --");
        batteryStatus = crearLineaEstado("Bateria: --");
        telemetryStatus = crearLineaEstado("H: 0.0m | D: 0.0m\nVS: 0.0 | HS: 0.0");
        bridgeStatus = crearLineaEstado("Puente PC: detenido");

        root.addView(videoView, videoParams);
        controls.addView(status, matchWidth());
        controls.addView(productStatus, matchWidth());
        controls.addView(modelStatus, matchWidth());
        controls.addView(batteryStatus, matchWidth());
        controls.addView(telemetryStatus, matchWidth());
        controls.addView(bridgeStatus, matchWidth());
        controls.addView(startButton, matchWidth());
        controls.addView(loadDjiButton, matchWidth());
        controls.addView(registerDjiButton, matchWidth());
        controls.addView(connectDroneButton, matchWidth());
        controls.addView(startVideoButton, matchWidth());
        controls.addView(stopVideoButton, matchWidth());
        controls.addView(closeTestButton, matchWidth());
        controls.addView(disconnectDroneButton, matchWidth());
        controls.addView(bridgeButton, matchWidth());
        controlsScroll.addView(controls);
        root.addView(controlsScroll, controlsParams);
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
        button.setTextSize(13);
        button.setBackground(crearFondo(Color.argb(190, 42, 48, 56), dp(4)));
        button.setMinHeight(dp(36));
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void cargarDJI() {
        if (djiCargado) {
            status.setText("DJI ya fue cargado");
            return;
        }

        try {
            Helper.install(getApplication());
            djiCargado = true;
            status.setText("Loader DJI cargado");
        } catch (Throwable throwable) {
            status.setText("Error cargando DJI: " + throwable.getClass().getSimpleName());
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
            status.setText("Iniciando DJI SDK...");
            registrarDJI();
        } else {
            status.setText("Permisos DJI requeridos");
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
                            runOnUiThread(() ->
                                    status.setText("DJI SDK registrado correctamente")
                            );
                            djiRegistrado = true;

                        } else {
                            runOnUiThread(() ->
                                    status.setText("Error DJI SDK: " + error.getDescription())
                            );
                        }
                    }

                    @Override
                    public void onProductConnect(BaseProduct product) {
                        connectedProduct = product;
                        dronConectado = true;
                        runOnUiThread(() -> {
                            status.setText("Drone conectado");
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
                            productStatus.setText("Conexion: desconectado");
                            modelStatus.setText("Modelo: --");
                            limpiarLecturaBateria();
                            limpiarLecturaTelemetria();
                            batteryStatus.setText("Bateria: --");
                            telemetryStatus.setText("H: 0.0m | D: 0.0m\nVS: 0.0 | HS: 0.0");
                            bridgeConnection = "desconectado";
                            bridgeModel = "--";
                            bridgeBatteryPercent = -1;
                            bridgeBatteryVoltage = 0;
                            limpiarDatosTelemetria();
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

    private void conectarDron() {
        if (!djiRegistrado) {
            status.setText("Primero registra DJI");
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
            productStatus.setText("Conexion: sin producto");
            modelStatus.setText("Modelo: --");
            batteryStatus.setText("Bateria: --");
            limpiarLecturaTelemetria();
            telemetryStatus.setText("H: 0.0m | D: 0.0m\nVS: 0.0 | HS: 0.0");
            bridgeConnection = "sin producto";
            bridgeModel = "--";
            bridgeBatteryPercent = -1;
            bridgeBatteryVoltage = 0;
            limpiarDatosTelemetria();
            return;
        }

        productStatus.setText(product.isConnected() ? "Conexion: conectado" : "Conexion: desconectado");
        modelStatus.setText("Modelo: " + product.getModel());
        bridgeConnection = product.isConnected() ? "conectado" : "desconectado";
        bridgeModel = String.valueOf(product.getModel());
        activarLecturaTelemetria(product);

        Battery battery = product.getBattery();
        if (battery == null) {
            limpiarLecturaBateria();
            batteryStatus.setText("Bateria: no disponible");
            return;
        }

        connectedBattery = battery;
        battery.setStateCallback(new BatteryState.Callback() {
            @Override
            public void onUpdate(BatteryState batteryState) {
                bridgeBatteryPercent = batteryState.getChargeRemainingInPercent();
                bridgeBatteryVoltage = batteryState.getVoltage();
                runOnUiThread(() -> batteryStatus.setText(
                        "Bateria: " + batteryState.getChargeRemainingInPercent() + "% | "
                                + batteryState.getVoltage() + " mV"
                ));
            }
        });
        batteryStatus.setText("Bateria: leyendo...");
    }

    private void activarLecturaTelemetria(BaseProduct product) {
        if (!(product instanceof Aircraft)) {
            limpiarLecturaTelemetria();
            telemetryStatus.setText("Telemetria: no disponible");
            return;
        }

        FlightController flightController = ((Aircraft) product).getFlightController();
        if (flightController == null) {
            limpiarLecturaTelemetria();
            telemetryStatus.setText("Telemetria: no disponible");
            return;
        }

        connectedFlightController = flightController;
        flightController.setStateCallback(new FlightControllerState.Callback() {
            @Override
            public void onUpdate(FlightControllerState state) {
                runOnUiThread(() -> actualizarTelemetria(state));
            }
        });
        telemetryStatus.setText("Telemetria: leyendo...");
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

        telemetryStatus.setText(String.format(
                java.util.Locale.US,
                "H: %.1fm | D: %.1fm\nVS: %.1f | HS: %.1f",
                altitude,
                distance,
                verticalSpeed,
                horizontalSpeed
        ));
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
    }

    private void detenerVideo() {
        if (videoPreview != null) {
            videoPreview.stop();
        }
        videoIniciado = false;
        videoView.setAlpha(0f);
    }

    private void detenerPrueba() {
        detenerVideo();
        limpiarLecturaBateria();
        limpiarLecturaTelemetria();
        connectedProduct = DJISDKManager.getInstance().getProduct();
        dronConectado = connectedProduct != null && connectedProduct.isConnected();
        productStatus.setText(dronConectado ? "Conexion: conectado" : "Conexion: sin conectar");
        modelStatus.setText("Modelo: --");
        batteryStatus.setText("Bateria: --");
        telemetryStatus.setText("H: 0.0m | D: 0.0m\nVS: 0.0 | HS: 0.0");
        bridgeConnection = dronConectado ? "conectado" : "sin conectar";
        bridgeModel = "--";
        bridgeBatteryPercent = -1;
        bridgeBatteryVoltage = 0;
        limpiarDatosTelemetria();
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
        productStatus.setText("Conexion: desconectado");
        modelStatus.setText("Modelo: --");
        batteryStatus.setText("Bateria: --");
        telemetryStatus.setText("H: 0.0m | D: 0.0m\nVS: 0.0 | HS: 0.0");
        bridgeConnection = "desconectado";
        bridgeModel = "--";
        bridgeBatteryPercent = -1;
        bridgeBatteryVoltage = 0;
        limpiarDatosTelemetria();
        status.setText("Dron desconectado");
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
            bridgeStatus.setText("HTTP: " + phoneIp + ":8765\nWS: " + phoneIp + ":8766");
            bridgeButton.setText("Detener puente PC");
            status.setText("Puente PC activo");
        } else {
            detenerPuentePC();
            bridgeStatus.setText("Puente PC: error");
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
        if (bridgeStatus != null) {
            bridgeStatus.setText("Puente PC: detenido");
        }
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
                + "\"videoActive\":" + videoIniciado
                + "}";
    }

    private String manejarComandoPuente(String command) {
        String normalized = command == null ? "" : command.toLowerCase(java.util.Locale.US);
        if (normalized.contains("get_status")) {
            return "{\"type\":\"status\",\"data\":" + getBridgeStatusJson() + "}";
        }
        if (normalized.contains("start_video")) {
            runOnUiThread(this::iniciarVideo);
            return "{\"type\":\"ack\",\"command\":\"start_video\"}";
        }
        if (normalized.contains("stop_video")) {
            runOnUiThread(this::detenerPrueba);
            return "{\"type\":\"ack\",\"command\":\"stop_video\"}";
        }
        return "{\"type\":\"error\",\"message\":\"unknown command\"}";
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
    }

    private void limpiarDatosTelemetria() {
        bridgeAltitude = 0f;
        bridgeDistance = 0f;
        bridgeVerticalSpeed = 0f;
        bridgeHorizontalSpeed = 0f;
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

