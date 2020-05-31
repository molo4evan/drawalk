package ru.nsu.fit.android.drawalk.modules.map

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    private var isDrawingModeOn = false
    private var isDrawingPolyline = false
    private lateinit var startPoint: LatLng
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val points: MutableList<LatLng> = ArrayList()

    private lateinit var myActivity: Activity

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
        myActivity = activity as Activity
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

    override fun onMapReady(googleMap: GoogleMap?) {
        map = googleMap ?: throw Exception("got null GoogleMap in onMapReady")
        val explanationMessage = activity?.getString(R.string.explanation_dialog_message)
        LocationPermissionCallback(activity as Activity, this)
            .requestPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                explanationMessage ?: ""
            )
//        Toast.makeText(activity, "Map Ready", Toast.LENGTH_SHORT).show()
//        map.setOnMapClickListener { point ->
//            if (isDrawingPolyline) {
//                val rectOptions = PolylineOptions()
//                    .color(Color.RED)
//                    .width(5f)
//                    .add(startPoint)
//                    .add(point)
//                map.addPolyline(rectOptions)
//                isDrawingPolyline = false
//            } else {
//                startPoint = point
//                isDrawingPolyline = true
//            }
//        }
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
        fusedLocationProviderClient.lastLocation    //TODO: check GPS
            .addOnSuccessListener { location ->
                showToast(
                    "Lat is ${location.latitude} " +
                            "+ Lng is ${location.longitude}"
                )
                moveAndZoomCamera(LatLng(location.latitude, location.longitude), 15f)
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
//        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
//        task.addOnSuccessListener { locationSettingsResponse ->
//
//        }
//        task.addOnFailureListener { exception ->
//            if (exception is ResolvableApiException) {
//                try {
//                    exception.startResolutionForResult(
//                        myActivity,
//                        REQUEST_CHECK_SETTINGS
//                    )
//                } catch (sendEx: IntentSender.SendIntentException) {
//                    // Ignore the error.
//                }
//            }
//        }
    }

    fun onLocationChanged(location: Location) {
        val msg = "Updated Location: " +
                location.latitude.toString() + "," +
                location.longitude.toString()
        //showToast(msg)
        if (isDrawingModeOn) {
            //val point = LatLng(location.latitude, location.longitude)
            points.add(LatLng(location.latitude, location.longitude))
            redrawLine()
//            val rectOptions = PolylineOptions()
//                .color(Color.RED)
//                .width(10f)
//            if (points.size > 10) {
//                for (point in points) {
//                    rectOptions.add(point)
//                }
//                map.addPolyline(rectOptions)
//                points.clear()
//            }
//            if (isDrawingPolyline) {
//                val rectOptions = PolylineOptions()
//                    .color(Color.RED)
//                    .width(10f)
//                    .add(startPoint)
//                    .add(point)
//                map.addPolyline(rectOptions)
//                isDrawingPolyline = false
//            } else {
//                startPoint = point
//                isDrawingPolyline = true
//            }
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

}

