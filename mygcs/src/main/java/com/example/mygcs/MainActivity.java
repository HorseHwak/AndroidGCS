package com.example.mygcs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.PointF;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.mission.item.command.YawCondition;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback{

    private static final String TAG = MainActivity.class.getSimpleName();

    MapFragment mNaverMapFragment = null;

    private int droneType = Type.TYPE_UNKNOWN;
    private Drone drone;
    private ControlTower controlTower;
    private final Handler handler = new Handler();
    private LatLong vehiclePosition;
    private NaverMap mMap;
    private Spinner modeSelector;
    private Marker marker = new Marker();
    private LatLng GPSvalue;

    private static Boolean MapLockable = false;

    PolylineOverlay polyline = new PolylineOverlay();
    ArrayList<LatLng> polyLatLng  = new ArrayList<LatLng>();

    private static int MapSelectCount = 0;
    private static int LandmarkSelectCount = 0;
    private static int MapLockCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        FragmentManager fm = getSupportFragmentManager();
        mNaverMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mNaverMapFragment == null) {
            mNaverMapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mNaverMapFragment).commit();
        }
        mNaverMapFragment.getMapAsync(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        Button Lockbtn = (Button) findViewById(R.id.mapLockSelect);

        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                Lockbtn.setVisibility(View.VISIBLE);
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                Lockbtn.setVisibility(View.INVISIBLE);
                alertUser("Drone Disconnected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateBattery();
                updateYAW();
                break;

            case AttributeEvent.STATE_UPDATED:
                updateArmButton();
                break;

            case AttributeEvent.GPS_POSITION:
                GPSLatLngUpdate();
                MapLockTap();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.HOME_UPDATED:
                break;

            case AttributeEvent.GPS_COUNT:
                updateSatellite();
                default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnConnect);
        if (isConnected) {
            connectButton.setText(getText(R.string.button_disconnect));
        } else {
            connectButton.setText(getText(R.string.button_connect));
        }
    }

    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(params);
        }
    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {
        switch (connectionStatus.getStatusCode()) {
            case LinkConnectionStatus.FAILED:
                Bundle extras = connectionStatus.getExtras();
                String msg = null;
                if (extras != null) {
                    msg = extras.getString(LinkConnectionStatus.EXTRA_ERROR_MSG);
                }
                alertUser("Connection Failed:" + msg);
                break;
        }
    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    protected void updateAltitude() {
        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    protected void updateSpeed() {
        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedTextView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updateSatellite() {
        TextView satelliteTextView = (TextView) findViewById(R.id.satelliteValueTextView);
        Gps droneGPS = this.drone.getAttribute(AttributeType.GPS);
        int countSatellite = droneGPS.getSatellitesCount();
        satelliteTextView.setText(String.format("%d", countSatellite));
    }

    protected void updateYAW(){
        TextView YAWTextView = (TextView) findViewById(R.id.yawalueTextView);
        Attitude droneYAW = this.drone.getAttribute(AttributeType.ATTITUDE);
        double yawValue = droneYAW.getYaw();
        YAWTextView.setText(String.format("%.1f", yawValue) + "deg");

    }

    protected  void updateBattery(){
        TextView voltageTextView = (TextView) findViewById(R.id.voltageValueTextView);
        Battery droneBattery = this.drone.getAttribute(AttributeType.BATTERY);
        double batteryValue = droneBattery.getBatteryVoltage();
        voltageTextView.setText(String.format("%.1f", batteryValue) + "V");
    }
    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    protected void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    public void GPSLatLngUpdate(){
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        vehiclePosition = droneGps.getPosition();
        GPSvalue = (new LatLng(vehiclePosition.getLatitude(), vehiclePosition.getLongitude()));

        polyLatLng.add(GPSvalue);
        polyline.setCoords(polyLatLng);
        polyline.setMap(mMap);

        Attitude droneYAW = this.drone.getAttribute(AttributeType.ATTITUDE);
        double yawValue = droneYAW.getYaw();
        float yawAngle = (float) yawValue;

        marker.setPosition(GPSvalue);
        marker.setAngle(yawAngle);
        marker.setAnchor(new PointF((float)0.5, (float)0.5));
        marker.setIcon(OverlayImage.fromResource(R.drawable.falcon));
        marker.setMap(mMap);

        Log.d("GPS값 : ", String.valueOf(GPSvalue));
    }

    public void MapLockSelect(View view){
        Button mapLockbutton = (Button) findViewById(R.id.mapLockButton);
        Button mapMovebutton = (Button) findViewById(R.id.mapMoveButton);

        if(MapLockCount == 0 ) {
            mapLockbutton.setVisibility(View.VISIBLE);
            mapMovebutton.setVisibility(View.VISIBLE);
            MapLockCount = 1;
        }
        else if(MapLockCount == 1){
            mapLockbutton.setVisibility(View.INVISIBLE);
            mapMovebutton.setVisibility(View.INVISIBLE);
            MapLockCount = 0;
        }
    }

    public void MapLock(View view){
        Button mapLockSelect = (Button) findViewById(R.id.mapLockSelect);
        Button mapLockbutton = (Button) findViewById(R.id.mapLockButton);
        Button mapMovebutton = (Button) findViewById(R.id.mapMoveButton);

        mapLockbutton.setVisibility(View.INVISIBLE);
        mapMovebutton.setVisibility(View.INVISIBLE);

        mapLockSelect.setText("맵 잠금");
        mapLockSelect.setBackgroundColor(0xFFED901F);

        MapLockable = true;
    }

    public void MapLockTap(){
        if(MapLockable == true) {
            Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
            vehiclePosition = droneGps.getPosition();
            GPSvalue = (new LatLng(vehiclePosition.getLatitude(), vehiclePosition.getLongitude()));

            CameraUpdate cameraUpdate = CameraUpdate.scrollTo(GPSvalue);
            mMap.moveCamera(cameraUpdate);
        }
    }
    public void MapMove(View view){
        Button mapLockSelect = (Button) findViewById(R.id.mapLockSelect);
        Button mapLockbutton = (Button) findViewById(R.id.mapLockButton);
        Button mapMovebutton = (Button) findViewById(R.id.mapMoveButton);

        mapLockbutton.setVisibility(View.INVISIBLE);
        mapMovebutton.setVisibility(View.INVISIBLE);

        mapLockSelect.setText("맵 이동");
        mapLockSelect.setBackgroundColor(0xFF49290F);
        show();

        MapLockable = false;
    }

    public void onMapselectButton(View view){

        Button generalMap = (Button) findViewById(R.id.generalMap);
        Button topographicalMap = (Button) findViewById(R.id.topographicalMap);
        Button satelliteMap = (Button) findViewById(R.id.satelliteMap);

        if(MapSelectCount == 0) {
            generalMap.setVisibility(View.VISIBLE);
            topographicalMap.setVisibility(View.VISIBLE);
            satelliteMap.setVisibility(View.VISIBLE);
            MapSelectCount = 1;
        }
        else if (MapSelectCount == 1 )
        {
            generalMap.setVisibility(View.INVISIBLE);
            topographicalMap.setVisibility(View.INVISIBLE);
            satelliteMap.setVisibility(View.INVISIBLE);
            MapSelectCount = 0;
        }
    }

    public void generalMap(View view){
        Button mapSelect = (Button) findViewById(R.id.selectMapMode);
        Button generalMap = (Button) findViewById(R.id.generalMap);
        Button topographicalMap = (Button) findViewById(R.id.topographicalMap);
        Button satelliteMap = (Button) findViewById(R.id.satelliteMap);

        generalMap.setVisibility(View.INVISIBLE);
        topographicalMap.setVisibility(View.INVISIBLE);
        satelliteMap.setVisibility(View.INVISIBLE);

        generalMap.setBackgroundColor(0xFFED901F);
        topographicalMap.setBackgroundColor(0xFF49290F);
        satelliteMap.setBackgroundColor(0xFF49290F);

        mMap.setMapType(NaverMap.MapType.Basic);
        mapSelect.setText("일반지도");
        mapSelect.setBackgroundColor(0xFFED901F);
    }

    public void topographicalMap(View view){
        Button mapSelect = (Button) findViewById(R.id.selectMapMode);
        Button generalMap = (Button) findViewById(R.id.generalMap);
        Button topographicalMap = (Button) findViewById(R.id.topographicalMap);
        Button satelliteMap = (Button) findViewById(R.id.satelliteMap);

        generalMap.setVisibility(View.INVISIBLE);
        topographicalMap.setVisibility(View.INVISIBLE);
        satelliteMap.setVisibility(View.INVISIBLE);

        generalMap.setBackgroundColor(0xFF49290F);
        topographicalMap.setBackgroundColor(0xFFED901F);
        satelliteMap.setBackgroundColor(0xFF49290F);

        mMap.setMapType(NaverMap.MapType.Terrain);
        mapSelect.setText("지형도");
        mapSelect.setBackgroundColor(0xFFED901F);
    }

    public void satelliteMap(View view){
        Button mapSelect = (Button) findViewById(R.id.selectMapMode);
        Button generalMap = (Button) findViewById(R.id.generalMap);
        Button topographicalMap = (Button) findViewById(R.id.topographicalMap);
        Button satelliteMap = (Button) findViewById(R.id.satelliteMap);

        generalMap.setVisibility(View.INVISIBLE);
        topographicalMap.setVisibility(View.INVISIBLE);
        satelliteMap.setVisibility(View.INVISIBLE);

        generalMap.setBackgroundColor(0xFF49290F);
        topographicalMap.setBackgroundColor(0xFF49290F);
        satelliteMap.setBackgroundColor(0xFFED901F);

        mMap.setMapType(NaverMap.MapType.Satellite);
        mapSelect.setText("위성지도");
        mapSelect.setBackgroundColor(0xFFED901F);
    }

    public void LandmarkSelectButton(View view){
        Button LandmarkOn = (Button) findViewById(R.id.landmarkOnButton);
        Button LandmarkOff = (Button) findViewById(R.id.landmarkOffButton);

        if(LandmarkSelectCount == 0){
            LandmarkOn.setVisibility(View.VISIBLE);
            LandmarkOff.setVisibility(View.VISIBLE);
            LandmarkSelectCount = 1;
        }
        else if(LandmarkSelectCount == 1){
            LandmarkOn.setVisibility(View.INVISIBLE);
            LandmarkOff.setVisibility(View.INVISIBLE);
            LandmarkSelectCount = 0;
        }
    }

    public void LandmarkOn(View view){
        Button LandmarkSelect = (Button) findViewById(R.id.selectLandmark);
        Button LandmarkOn = (Button) findViewById(R.id.landmarkOnButton);
        Button LandmarkOff = (Button) findViewById(R.id.landmarkOffButton);

        LandmarkOn.setVisibility(View.INVISIBLE);
        LandmarkOff.setVisibility(View.INVISIBLE);

        mMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
        LandmarkSelect.setText("지적도On");
        LandmarkSelect.setBackgroundColor(0xFFED901F);
    }
    public void LandmarkOff(View view){
        Button LandmarkSelect = (Button) findViewById(R.id.selectLandmark);
        Button LandmarkOn = (Button) findViewById(R.id.landmarkOnButton);
        Button LandmarkOff = (Button) findViewById(R.id.landmarkOffButton);

        LandmarkOn.setVisibility(View.INVISIBLE);
        LandmarkOff.setVisibility(View.INVISIBLE);

        mMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
        LandmarkSelect.setText("지적도Off");
        LandmarkSelect.setBackgroundColor(0xFF49290F);
    }

    public void clearBtn(View view){
        LandmarkOff(view);
        generalMap(view);
        polyline.setMap(null);
    }

    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(3, new AbstractCommandListener() {

                @Override
                public void onSuccess() {
                    alertUser("Taking off...");
                }

                @Override
                public void onError(int i) {
                    alertUser("Unable to take off.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to take off.");
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        } else {
            // Connected but not Armed
            VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to arm vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Arming operation timed out.");
                }
            });
        }
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.btnArmTakeOff);

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    void show()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("AlertDialog Title");
        builder.setMessage("AlertDialog Content");
        builder.setPositiveButton("우측버튼",
                (dialog, which) -> Toast.makeText(getApplicationContext(),"우측버튼 클릭됨",Toast.LENGTH_LONG).show());
        builder.setNegativeButton("좌측버튼",
                (dialog, which) -> Toast.makeText(getApplicationContext(),"좌측버튼 클릭됨",Toast.LENGTH_LONG).show());
        builder.show();

    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        mMap = naverMap;

        //각 장소별 경위도
        ArrayList<LatLng> Location  = new ArrayList<LatLng>();
        ArrayList<Marker> PlaceName = new ArrayList<Marker>();
        ArrayList<String> MarkerTag = new ArrayList<String>();


        Location.add(new LatLng(35.945357, 126.682163));
        Location.add(new LatLng(35.846715, 127.129386));
        Location.add(new LatLng(35.967587, 126.736843));
        Location.add(new LatLng(35.969449, 126.957322));

        MarkerTag.add("군산대학교");
        MarkerTag.add("전북대학교");
        MarkerTag.add("군산시청");
        MarkerTag.add("원광대학교");

        LatLngBounds latLngBounds = new LatLngBounds(Location.get(0), Location.get(1));

        //중간값 화면 잡기
        CameraUpdate cameraUpdate = CameraUpdate.fitBounds(latLngBounds);
        naverMap.moveCamera(cameraUpdate);

        //마커 찍기
        // for(int i = 0; i < Location.size(); i++){
        //    Marker marker = new Marker();
        //    marker.setPosition(Location.get(i));
        //    PlaceName.add(marker);
        // }
        // for (Marker marker : PlaceName){
        //    marker.setMap(naverMap);
        // }

        // 경로표시
        PolylineOverlay polyline = new PolylineOverlay();
        polyline.setCoords(Arrays.asList(
                Location.get(0),Location.get(1),Location.get(2),Location.get(3)
        ));

       // polyline.setMap(naverMap);
        polyline.setWidth(10);


        //클릭시 경위도표시
        naverMap.setOnMapLongClickListener((point, coord) -> {

            Toast.makeText(getApplicationContext(), "위도: " + coord.latitude + ", 경도: " + coord.longitude, Toast.LENGTH_SHORT).show();
        });
    }
}
