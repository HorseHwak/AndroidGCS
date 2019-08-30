package com.example.mygcs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolygonOverlay;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.naver.maps.map.util.MarkerIcons;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.MissionApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;
import com.o3dr.services.android.lib.util.MathUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback{

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;

    MapFragment mNaverMapFragment = null;

    private int droneType = Type.TYPE_UNKNOWN;
    private Drone drone;
    private ControlTower controlTower;
    private final Handler handler = new Handler();
    private LatLong vehiclePosition;
    private NaverMap mMap;
    private Spinner modeSelector;
    private Marker marker = new Marker();
    private Marker gotomaker = new Marker();
    private Marker Apoint = new Marker();
    private Marker Bpoint = new Marker();
    private LatLng GPSvalue;
    private static int takeoffAltitude = 3;
    private int interval = 5;

    private static boolean MapLockable = false;
    private boolean Toggle = false;

    PolylineOverlay polyline = new PolylineOverlay();
    ArrayList<LatLng> polyLatLng  = new ArrayList<LatLng>();

    RecyclerView mRecyclerView = null;
    RecyclerTextAdapter mAdapter = null;
    ArrayList<RecyclerItem> mList = new ArrayList<RecyclerItem>();
    ArrayList<LatLng> mPath = new ArrayList<>();
    ArrayList<LatLng> mPolygon = new ArrayList<>();

    private int MapSelectCount = 0;
    private int LandmarkSelectCount = 0;
    private int MapLockCount = 0;
    private int AltitudeCount = 0;
    private int ModeSelectCount = 0;
    private static int MissionButtonCount = 0;


    private int selectPin = 0;

    private double mRecentAltitude = 0;

    LatLong ApLat = null, BpLat = null;
    LatLng nApLat = null, nBpLat = null;
    PolylineOverlay pinPolyline = new PolylineOverlay();
    PolygonOverlay pinPolygon = new PolygonOverlay();


    final Handler timerhandler = new Handler(){
        public void handleMessage(Message msg){
           if( mList.size() >= 1) {
               mList.remove(0);
               mAdapter.notifyItemRemoved(0);
               mAdapter.notifyItemChanged(0);
           }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((TextView) parent.getChildAt(0)).setTextColor(0xFF00FF00);
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        mRecyclerView = findViewById(R.id.recycler);
        mAdapter = new RecyclerTextAdapter(mList);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        Timer timer = new Timer(true);
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                Log.v(TAG,"timer run");
                Message msg = timerhandler.obtainMessage();
                timerhandler.sendMessage(msg);
            }
        };
        timer.schedule(timerTask, 0, 5000);

        FragmentManager fm = getSupportFragmentManager();
        mNaverMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mNaverMapFragment == null) {
            mNaverMapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mNaverMapFragment).commit();
        }
        mNaverMapFragment.getMapAsync(this);
    }

    public void addItem(String log){
        RecyclerItem item = new RecyclerItem();

        item.setlog(log);

        mList.add(item);
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
                updateArmButton();
                onMissionSend();
                onMissionButtonTap();
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
                updateArmButton();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateBattery();
                longclickevent();
                break;

            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.STATE_UPDATED:
                updateArmButton();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                updateYAW();
                break;

            case AttributeEvent.GPS_POSITION:
                GPSLatLngUpdate();
                MapLockTap();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.MISSION_SENT:
                Mission_sent();
                break;

            case AttributeEvent.GPS_COUNT:
                updateSatellite();
                default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    public void onConnectTap() {
            ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(params);
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
        onConnectTap();
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    protected void alertUser(String message) {
        Handler handler = new Handler();

        if(mList.size() <= 2) {
            addItem("  ★ " + message);
            mAdapter.notifyDataSetChanged();
        }
        else if(mList.size() > 2) {
            addItem("  ★ " + message);
            mList.set(0, mList.get(1));
            mList.set(1, mList.get(2));
            mList.set(2, mList.get(3));
            mList.remove(3);
            mAdapter.notifyDataSetChanged();
        }
    }

    protected void updateAltitude() {
        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        mRecentAltitude = droneAltitude.getRelativeAltitude();
        altitudeTextView.setText(String.format("%3.1f", mRecentAltitude) + "m");
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

        if(yawValue < 0){
            yawValue += 360;
        }
        YAWTextView.setText(String.format("%.1f", yawValue) + "deg");

    }

    protected  void updateBattery(){
        TextView voltageTextView = (TextView) findViewById(R.id.voltageValueTextView);
        Battery droneBattery = this.drone.getAttribute(AttributeType.BATTERY);
        double batteryValue = droneBattery.getBatteryVoltage();
        voltageTextView.setText(String.format("%.1f", batteryValue) + "V");
        Log.d("ArrayList : ", String.valueOf(mList.size()));
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
        polyline.setColor(0xFFFFFF00);
        polyline.setMap(mMap);

        Attitude droneYAW = this.drone.getAttribute(AttributeType.ATTITUDE);
        double yawValue = droneYAW.getYaw();
        if(yawValue < 0){
            yawValue += 360;
        }
        float yawAngle = (float) yawValue;

        marker.setPosition(GPSvalue);
        marker.setAngle(yawAngle);
        marker.setAnchor(new PointF((float)0.5, (float)0.9));
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
        MapLockCount = 0;

        mapLockSelect.setText("맵 잠금");
        mapLockSelect.setBackground(ContextCompat.getDrawable(this, R.drawable.btnyellow));

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
        MapLockCount = 0;

        mapLockSelect.setText("맵 이동");
        mapLockSelect.setBackground(ContextCompat.getDrawable(this, R.drawable.btnbrown));

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
        MapSelectCount = 0;

        generalMap.setBackground(ContextCompat.getDrawable(this, R.drawable.btnyellow));
        topographicalMap.setBackground(ContextCompat.getDrawable(this, R.drawable.btnbrown));
        satelliteMap.setBackground(ContextCompat.getDrawable(this, R.drawable.btnbrown));

        mMap.setMapType(NaverMap.MapType.Basic);
        mapSelect.setText("일반지도");
        mapSelect.setBackground(ContextCompat.getDrawable(this, R.drawable.btnyellow));
    }

    public void topographicalMap(View view){
        Button mapSelect = (Button) findViewById(R.id.selectMapMode);
        Button generalMap = (Button) findViewById(R.id.generalMap);
        Button topographicalMap = (Button) findViewById(R.id.topographicalMap);
        Button satelliteMap = (Button) findViewById(R.id.satelliteMap);

        generalMap.setVisibility(View.INVISIBLE);
        topographicalMap.setVisibility(View.INVISIBLE);
        satelliteMap.setVisibility(View.INVISIBLE);
        MapSelectCount = 0;

        generalMap.setBackground(ContextCompat.getDrawable(this, R.drawable.btnbrown));
        topographicalMap.setBackground(ContextCompat.getDrawable(this, R.drawable.btnyellow));
        satelliteMap.setBackground(ContextCompat.getDrawable(this, R.drawable.btnbrown));

        mMap.setMapType(NaverMap.MapType.Terrain);
        mapSelect.setText("지형도");
        mapSelect.setBackground(ContextCompat.getDrawable(this, R.drawable.btnyellow));
    }

    public void satelliteMap(View view){
        Button mapSelect = (Button) findViewById(R.id.selectMapMode);
        Button generalMap = (Button) findViewById(R.id.generalMap);
        Button topographicalMap = (Button) findViewById(R.id.topographicalMap);
        Button satelliteMap = (Button) findViewById(R.id.satelliteMap);

        generalMap.setVisibility(View.INVISIBLE);
        topographicalMap.setVisibility(View.INVISIBLE);
        satelliteMap.setVisibility(View.INVISIBLE);
        MapSelectCount = 0;

        generalMap.setBackground(ContextCompat.getDrawable(this, R.drawable.btnbrown));
        topographicalMap.setBackground(ContextCompat.getDrawable(this, R.drawable.btnbrown));
        satelliteMap.setBackground(ContextCompat.getDrawable(this, R.drawable.btnyellow));

        mMap.setMapType(NaverMap.MapType.Satellite);
        mapSelect.setText("위성지도");
        mapSelect.setBackground(ContextCompat.getDrawable(this, R.drawable.btnyellow));
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
        LandmarkSelectCount = 0;

        mMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
        LandmarkSelect.setText("지적도On");
        LandmarkSelect.setBackground(ContextCompat.getDrawable(this, R.drawable.btnyellow));
    }

    public void LandmarkOff(View view){
        Button LandmarkSelect = (Button) findViewById(R.id.selectLandmark);
        Button LandmarkOn = (Button) findViewById(R.id.landmarkOnButton);
        Button LandmarkOff = (Button) findViewById(R.id.landmarkOffButton);

        LandmarkOn.setVisibility(View.INVISIBLE);
        LandmarkOff.setVisibility(View.INVISIBLE);
        LandmarkSelectCount = 0;

        mMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
        LandmarkSelect.setText("지적도Off");
        LandmarkSelect.setBackground(ContextCompat.getDrawable(this, R.drawable.btnbrown));
    }

    public void clearBtn(View view){
        polyline.setMap(null);
        gotomaker.setMap(null);
        polyLatLng.clear();
        mPath.clear();
        mPolygon.clear();

        Apoint.setMap(null);
        Bpoint.setMap(null);
        pinPolyline.setMap(null);
        pinPolygon.setMap(null);
    }

    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

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
            ControlApi.getApi(this.drone).takeoff(takeoffAltitude, new AbstractCommandListener() {

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
        }
        else {
            builder.setTitle("주의!!");
            builder.setMessage("확인 버튼을 누르시면 모터가 고속회전합니다.");
            builder.setPositiveButton("확인", (dialogInterface, i) -> {
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
            });
            builder.setNegativeButton("취소", (dialogInterface, i) -> {
                return;
            });


            AlertDialog alert = builder.create();
            alert.show();

            Button Positivebtn = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button Negativebtn = alert.getButton(AlertDialog.BUTTON_POSITIVE);

            Positivebtn.setTextColor(0xFF000000);
            Negativebtn.setTextColor(0xFF000000);
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

    private void setMission(Mission mMission) {
        MissionApi.getApi(this.drone).setMission(mMission,true);
    }

    private void pauseMission() {
        MissionApi.getApi(this.drone).pauseMission(null);
    }

    public void onMissionSend(){
        final Mission mMission = new Mission();

        for(int i = 0; i < mPath.size(); i++) {
            Waypoint waypoint = new Waypoint();
            waypoint.setDelay(1);

            LatLongAlt latLongAlt = new LatLongAlt(mPath.get(i).latitude, mPath.get(i).longitude, mRecentAltitude);
            waypoint.setCoordinate(latLongAlt);

            mMission.addMissionItem(waypoint);
        }

        final Button Missionsend = (Button) findViewById(R.id.btnMissionStart);
        Missionsend.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(Missionsend.getText().equals("임무전송")) {
                    Log.d("mPath : ", String.valueOf(mPath));
                    setMission(mMission);
                }
                else if(Missionsend.getText().equals("임무시작")){
                    setAutoMode();
                    Missionsend.setText("임무중지");
                }
                else if(Missionsend.getText().equals("임무중지")){
                    pauseMission();
                    setLoitermode();
                    Missionsend.setText("임무전송");
                }
            }
        });
    }

    private void setAutoMode(){
        VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_AUTO, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Set AutoMode Complete");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Fail to set AutoMode");
            }

            @Override
            public void onTimeout() {
                alertUser("Fail to set AutoMode");
            }
        });
    }

    private void setLoitermode(){
        VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LOITER, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Set LoiterMode Complete");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Fail to set LoiterMode");
            }

            @Override
            public void onTimeout() {
                alertUser("Fail to set LoiterMode");
            }
        });
    }

    private void Mission_sent(){
        alertUser("Mission Upload Complete");
        Button Missionsend = (Button) findViewById(R.id.btnMissionStart);
        Missionsend.setText("임무시작");
    }

    public void onMissionButtonTap(){
        Button modeViewer = (Button) findViewById(R.id.missionmodeselect);
        Button normal = (Button) findViewById(R.id.normalmode);
        Button flight = (Button) findViewById(R.id.rootflight);
        Button interval = (Button) findViewById(R.id.intervalmonitoring);
        Button area = (Button) findViewById(R.id.areamonitoring);

        normal.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View view) {
                modeViewer.setText("일반\n모드");
                MissionButtonCount = 0;

                normal.setVisibility(View.INVISIBLE);
                flight.setVisibility(View.INVISIBLE);
                interval.setVisibility(View.INVISIBLE);
                area.setVisibility(View.INVISIBLE);
                ModeSelectCount = 0;
            }
        });

        flight.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View view) {
                modeViewer.setText("경로\n비행");
                MissionButtonCount = 1;

                normal.setVisibility(View.INVISIBLE);
                flight.setVisibility(View.INVISIBLE);
                interval.setVisibility(View.INVISIBLE);
                area.setVisibility(View.INVISIBLE);
                ModeSelectCount = 0;
            }
        });

        interval.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View view) {
                modeViewer.setText("간격\n감시");
                MissionButtonCount = 2;

                normal.setVisibility(View.INVISIBLE);
                flight.setVisibility(View.INVISIBLE);
                interval.setVisibility(View.INVISIBLE);
                area.setVisibility(View.INVISIBLE);
                ModeSelectCount = 0;
            }
        });

        area.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View view) {
                modeViewer.setText("면적\n감시");
                MissionButtonCount = 3;

                normal.setVisibility(View.INVISIBLE);
                flight.setVisibility(View.INVISIBLE);
                interval.setVisibility(View.INVISIBLE);
                area.setVisibility(View.INVISIBLE);
                ModeSelectCount = 0;
            }
        });

    }

    public void missionmodeselect(View view){
        Button normal = (Button) findViewById(R.id.normalmode);
        Button flight = (Button) findViewById(R.id.rootflight);
        Button interval = (Button) findViewById(R.id.intervalmonitoring);
        Button area = (Button) findViewById(R.id.areamonitoring);

        if(ModeSelectCount == 0){
            normal.setVisibility(View.VISIBLE);
            flight.setVisibility(View.VISIBLE);
            interval.setVisibility(View.VISIBLE);
            area.setVisibility(View.VISIBLE);
            ModeSelectCount = 1;
        }
        else if(ModeSelectCount == 1){
            normal.setVisibility(View.INVISIBLE);
            flight.setVisibility(View.INVISIBLE);
            interval.setVisibility(View.INVISIBLE);
            area.setVisibility(View.INVISIBLE);
            ModeSelectCount = 0;
        }
    }

    public void setMissionInterval(View view){

        final EditText edittext = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("간격설정");
        builder.setMessage("간격 길이를 입력해주세요.");
        builder.setView(edittext);
        builder.setPositiveButton("입력",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        interval = Integer.parseInt(edittext.getText().toString());
                        alertUser("간격을 " + edittext.getText() + "m로 설정완료");
                    }
                });
        builder.setNegativeButton("취소",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
        builder.show();

    }



    public void altitudeView(View view){
        Button up = (Button) findViewById(R.id.upAltitude);
        Button down = (Button) findViewById(R.id.downAltitude);

        if(AltitudeCount == 0){
            up.setVisibility(View.VISIBLE);
            down.setVisibility(View.VISIBLE);
            AltitudeCount = 1;
        }
        else if(AltitudeCount == 1){
            up.setVisibility(View.INVISIBLE);
            down.setVisibility(View.INVISIBLE);
            AltitudeCount = 0;
        }
    }

    public void upAltitude(View view){
        Button AltitdeView = (Button) findViewById(R.id.takeOffAltitudeView);

        takeoffAltitude += 1;
        AltitdeView.setText(takeoffAltitude + "m\n이륙고도" );
    }

    public void downAltitude(View veiw){
        Button AltitdeView = (Button) findViewById(R.id.takeOffAltitudeView);

        takeoffAltitude -= 1;
        AltitdeView.setText(takeoffAltitude + "m\n이륙고도" );
    }

    public void longclickevent()
    {
        mMap.setOnMapLongClickListener((point, coord) -> {

            if(MissionButtonCount == 0) {
                State vehicleState = this.drone.getAttribute(AttributeType.STATE);
                LatLong clickGPS = new LatLong(coord.latitude, coord.longitude);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("주의!!");
                builder.setMessage("확인 버튼을 누르시면 기체가 클릭한 지점으로 이동합니다.");
                builder.setPositiveButton("확인", (dialogInterface, i) -> {
                    if(vehicleState.isFlying()) {
                        gotomaker.setPosition(new LatLng(coord.latitude, coord.longitude));
                        gotomaker.setIcon(MarkerIcons.BLACK);
                        gotomaker.setIconTintColor(Color.RED);
                        gotomaker.setMap(mMap);

                        VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_GUIDED);
                        ControlApi.getApi(this.drone).goTo(clickGPS, true, new AbstractCommandListener() {
                            @Override
                            public void onSuccess() {
                                alertUser("goto Point");
                            }

                            @Override
                            public void onError(int executionError) {
                                alertUser("Unable to go Point.");
                            }

                            @Override
                            public void onTimeout() {
                                alertUser("Unable to go Point.");
                            }
                        });
                    }
                });

                builder.setNegativeButton("취소", (dialogInterface, i) -> {
                    return;
                });
                AlertDialog alert = builder.create();
                alert.show();

                Button Positivebtn = alert.getButton(AlertDialog.BUTTON_NEGATIVE);
                Button Negativebtn = alert.getButton(AlertDialog.BUTTON_POSITIVE);

                Positivebtn.setTextColor(0xFF000000);
                Negativebtn.setTextColor(0xFF000000);

                Log.d("MissionButtonCount : ", String.valueOf(MissionButtonCount));
            }
            else if(MissionButtonCount == 2) {
                if (selectPin == 0) {
                    mPath.clear();
                    mPolygon.clear();
                    nApLat = new LatLng(coord.latitude, coord.longitude);
                    ApLat = new LatLong(coord.latitude, coord.longitude);
                    Apoint.setPosition(coord);
                    Apoint.setIcon(OverlayImage.fromResource(R.drawable.map_pin_64px));
                    Apoint.setMap(mMap);
                    pinPolyline.setMap(null);
                    pinPolygon.setMap(null);
                    selectPin = 1;
                    mPath.add(nApLat);
                    mPolygon.add(nApLat);
                } else if (selectPin == 1) {
                    nBpLat = new LatLng(coord.latitude, coord.longitude);
                    BpLat = new LatLong(coord.latitude, coord.longitude);
                    Bpoint.setPosition(coord);
                    Bpoint.setIcon(OverlayImage.fromResource(R.drawable.map_pin_64px_color));
                    Bpoint.setMap(mMap);

                    selectPin = 0;

                    mPath.add(nBpLat);
                    mPolygon.add(nBpLat);
                    for (int i = interval; i <= interval*10; i += interval) {
                        if (Toggle == false) {
                            mPath.add(new LatLng(MathUtils.newCoordFromBearingAndDistance(BpLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, i).getLatitude(), MathUtils.newCoordFromBearingAndDistance(BpLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, i).getLongitude()));
                            mPath.add(new LatLng(MathUtils.newCoordFromBearingAndDistance(ApLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, i).getLatitude(), MathUtils.newCoordFromBearingAndDistance(ApLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, i).getLongitude()));
                            Toggle = true;
                        } else if (Toggle == true) {
                            mPath.add(new LatLng(MathUtils.newCoordFromBearingAndDistance(ApLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, i).getLatitude(), MathUtils.newCoordFromBearingAndDistance(ApLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, i).getLongitude()));
                            mPath.add(new LatLng(MathUtils.newCoordFromBearingAndDistance(BpLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, i).getLatitude(), MathUtils.newCoordFromBearingAndDistance(BpLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, i).getLongitude()));
                            Toggle = false;
                        }
                    }

                    mPolygon.add(new LatLng(MathUtils.newCoordFromBearingAndDistance(BpLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, interval*10).getLatitude(), MathUtils.newCoordFromBearingAndDistance(BpLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, interval*10).getLongitude()));
                    mPolygon.add(new LatLng(MathUtils.newCoordFromBearingAndDistance(ApLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, interval*10).getLatitude(), MathUtils.newCoordFromBearingAndDistance(ApLat, MathUtils.getHeadingFromCoordinates(ApLat, BpLat) + 90, interval*10).getLongitude()));

                    Log.d("Array : ", String.valueOf(mPolygon));

                    pinPolygon.setCoords(mPolygon);
                    pinPolygon.setColor(0x4C87CEEB);
                    pinPolygon.setMap(mMap);

                    pinPolyline.setGlobalZIndex(250000);
                    pinPolyline.setCoords(mPath);
                    pinPolyline.setColor(Color.WHITE);
                    pinPolyline.setMap(mMap);

                    Toast.makeText(getApplicationContext(), "각도 : " + (MathUtils.getHeadingFromCoordinates(ApLat, BpLat)), Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,  @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        mMap = naverMap;

        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.NoFollow);

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


        //롱클릭시 경위도표시
        naverMap.setOnMapLongClickListener((point, coord) -> {

        });
    }
}
