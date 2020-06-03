package ru.nsu.fit.android.drawalk.modules.map

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.Context.LOCATION_SERVICE
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import ru.nsu.fit.android.drawalk.R

class MapFragment : IMapFragment(), OnMapReadyCallback {

    companion object {
        const val REQUEST_CHECK_SETTINGS = 128
    }

    private lateinit var map: GoogleMap
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var noGPSMessage: LinearLayout

    private val points: MutableList<LatLng> = ArrayList()
    private val myActivity: Activity by lazy { activity as Activity }
    private val locationManager: LocationManager by lazy {
        myActivity.getSystemService(LOCATION_SERVICE) as LocationManager
    }
    private var isDrawingModeOn = false

    private val gpsSwitchStateReceiver = object : BroadcastReceiver() {
        private var firstTimeChange = true
        override fun onReceive(context: Context, intent: Intent) {
            if (LocationManager.PROVIDERS_CHANGED_ACTION == intent.action) {
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkEnabled =
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                if (isGpsEnabled || isNetworkEnabled) {
                    if (firstTimeChange) {
                        firstTimeChange = false
                        moveToCurrentLocation()
                        showToast("GPS turned on first time")
                    }
                    showToast("GPS turned on")
                    noGPSMessage.visibility = View.GONE
                } else {
                    showToast("GPS turned off")
                    noGPSMessage.visibility = View.VISIBLE
                }
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return if (view == null) {
            inflater.inflate(R.layout.fragment_map, container, false)
        } else {
            view
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getMap()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                val rectOptions = PolylineOptions()
                    .color(Color.BLUE)
                    .width(5f)
                for (location in locationResult.locations) {
                    rectOptions.add(LatLng(location.latitude, location.longitude))
                }
                map.addPolyline(rectOptions)
            }
        }
        view.findViewById<TextView>(R.id.open_gps_settings_button)
            .setOnClickListener {
                openGPSSettingsScreen()
            }
        noGPSMessage = view.findViewById(R.id.no_gps_message)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION).apply {
            addAction(Intent.ACTION_PROVIDER_CHANGED)
        }
        myActivity.registerReceiver(gpsSwitchStateReceiver, filter)
    }

    private fun getMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map_fragment)
        if (mapFragment != null) {
            (mapFragment as SupportMapFragment).getMapAsync(this)
        } else {
            throw Exception("null map fragment")
        }
    }

    override fun tryToGetLocation() {
        val explanationMessage = activity?.getString(R.string.explanation_dialog_message)
        LocationPermissionCallback(activity as Activity, this)
            .requestPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                explanationMessage ?: ""
            )
    }

    override fun turnDrawingModeOn() {
        isDrawingModeOn = true
    }

    override fun turnDrawingModeOff() {
        isDrawingModeOn = false
        points.clear()
    }

    override fun cancelDrawing() {
        map.clear()
    }

    override fun stopDrawing() {
        TODO("Not yet implemented")
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        map = googleMap ?: throw Exception("got null GoogleMap in onMapReady")
        val explanationMessage = activity?.getString(R.string.explanation_dialog_message)
        LocationPermissionCallback(activity as Activity, this)
            .requestPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                explanationMessage ?: ""
            )
    }

    override fun addMarker(position: LatLng, title: String) {
        map.addMarker(MarkerOptions().position(position).title(title))
    }

    override fun moveCamera(position: LatLng) {
        map.moveCamera(CameraUpdateFactory.newLatLng(position))
    }

    override fun moveAndZoomCamera(position: LatLng, zoom: Float) {
        val cameraPosition = CameraPosition.Builder().target(position).zoom(zoom).build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun showToast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }

    private fun showSadMessage() {
        showToast("Can't open map without permission :(")
    }

    private fun showSadGPSMessage() {
        showToast("Не могу определить местоположение без подключения к GPS :(")
    }

    override fun handleSuccessfullyGetPermission() {
        map.isMyLocationEnabled = true
        map.setOnMyLocationClickListener { location ->
            showToast("Current location: $location")
        }
        val locationRequest = createLocationRequest()
        if (locationRequest == null) {
            showToast("get null locationRequest") //TODO: dialog?
        } else {
            startLocationUpdates(locationRequest)
        }
    }

    override fun handleCantGetPermission() {
        showSadMessage()
    }

    override fun onGPSCheckingSuccess() {
        moveToCurrentLocation()
    }

    override fun onGPSCheckingFailure() {
        showSadGPSMessage()
    }

    private fun createLocationRequest(): LocationRequest? {
        return LocationRequest.create()?.apply {
            interval = 10000    //location updates rate in milliseconds
            fastestInterval = 5000  //location updates fastest rate in milliseconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun startLocationUpdates(locationRequest: LocationRequest) {
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val locationSettingsRequest = builder.build()

        val client: SettingsClient = LocationServices.getSettingsClient(myActivity)
        client.checkLocationSettings(locationSettingsRequest)

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(myActivity).apply {
                requestLocationUpdates(locationRequest, object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        onLocationChanged(locationResult.lastLocation)
                    }
                }, Looper.myLooper())
            }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            showGPSDisabledDialogToUser()
            noGPSMessage.visibility = View.VISIBLE
        } else {
            moveToCurrentLocation()
            noGPSMessage.visibility = View.GONE
        }
    }

    fun onLocationChanged(location: Location) {
        if (isDrawingModeOn) {
            points.add(LatLng(location.latitude, location.longitude))
            redrawLine()
        }
    }

    private fun redrawLine() {
        map.clear()
        val options = PolylineOptions()
            .color(Color.RED)
            .width(10f)
            .geodesic(true)
            .addAll(points)
        map.addPolyline(options)
    }

    private fun moveToCurrentLocation() {
        fusedLocationProviderClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {               //FIXME: recheck location somehow
                    showToast(
                        "Lat is ${location.latitude} " +
                                "+ Lng is ${location.longitude}"
                    )
                    moveAndZoomCamera(LatLng(location.latitude, location.longitude), 15f)
                } else {
                    showToast("receive null location")
                }
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(
                            myActivity,
                            REQUEST_CHECK_SETTINGS
                        )
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
    }

    private fun showGPSDisabledDialogToUser() {
        AlertDialog.Builder(myActivity)
            .setMessage(getString(R.string.gps_explanation_dialog_message))
            .setPositiveButton(R.string.yes) { _, _ ->
                openGPSSettingsScreen()
            }
            .setNegativeButton(R.string.no) { _, _ ->
                showSadGPSMessage()
            }
            .create()
            .show()
    }

    private fun openGPSSettingsScreen() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

}

