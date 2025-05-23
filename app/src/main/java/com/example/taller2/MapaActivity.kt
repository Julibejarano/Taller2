package com.example.taller2

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class MapaActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var textCoordenadas: TextView
    private lateinit var sensorManager: SensorManager
    private var sensorLuz: Sensor? = null
    private var modoOscuro = false
    private lateinit var locationManager: LocationManager
    private var ultimaUbicacion: Location? = null
    private lateinit var marcadorActual: Marker
    private var marcadorBusqueda: Marker? = null
    private var rutaActual: Polyline? = null
    private val REQUEST_PERMISO_UBICACION = 100
    private val OPENROUTE_API_KEY = "5b3ce3597851110001cf6248abff903c3c484657bfe4e929a5cd05e1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cargar la configuración de osmdroid
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_mapa)

        // Inicialización de vistas y botones
        mapView = findViewById(R.id.mapView)
        textCoordenadas = findViewById(R.id.textCoordenadas)
        val btnVerJson = findViewById<Button>(R.id.btnVerJson)
        val inputDireccion = findViewById<EditText>(R.id.inputDireccion)

        // Configuración del mapa
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Marcador que representa la ubicación actual del usuario
        marcadorActual = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(marcadorActual)

        // Inicializar el archivo JSON donde se almacenan las ubicaciones
        File(filesDir, "registro_ubicaciones.json").writeText("[]")

        // Verificar si se tienen permisos de ubicacion
        if (tienePermisoUbicacion()) {
            mostrarUbicacion()
            iniciarActualizaciones()
        } else {
            solicitarPermisoUbicacion()
        }

        // Registrar el sensor de luz
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorLuz = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        sensorLuz?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Accion del boton para ver el contenido del JSON
        btnVerJson.setOnClickListener { mostrarContenidoJson() }

        // Accion del campo de texto para buscar una direccion
        inputDireccion.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val direccion = inputDireccion.text.toString()
                if (direccion.isNotEmpty()) {
                    buscarDireccion(direccion)
                }
                true
            } else false
        }

        // Configura el evento de long press en el mapa
        configurarLongClickEnMapa()
    }

    private fun tienePermisoUbicacion() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun solicitarPermisoUbicacion() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_PERMISO_UBICACION)
    }

    // Responde a la solicitud de permisos
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISO_UBICACION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            mostrarUbicacion()
            iniciarActualizaciones()
        }
    }

    // Muestra la ubicación actual en el mapa
    private fun mostrarUbicacion() {
        // Se crea un overlay para mostrar la ubicación en el mapa
        val overlay = MyLocationNewOverlay(mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        mapView.overlays.add(overlay)

        // Espera el primer fix (coordenadas disponibles)
        overlay.runOnFirstFix {
            val punto = overlay.myLocation
            runOnUiThread {
                punto?.let {
                    ultimaUbicacion = Location("").apply {
                        latitude = it.latitude
                        longitude = it.longitude
                    }

                    // Ajusta el zoom y centra el mapa en la ubicacion
                    mapView.controller.setZoom(17.0)
                    mapView.controller.setCenter(it)

                    marcadorActual.position = it
                    marcadorActual.title = "Mi ubicación actual"
                    textCoordenadas.text = "Lat: %.5f, Lon: %.5f".format(it.latitude, it.longitude)
                }
            }
        }
    }

    // Inicia actualizaciones periódicas de ubicacion
    private fun iniciarActualizaciones() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (tienePermisoUbicacion()) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            // Actualizaciones de ubicacion cada 5 segundos y 5 metros
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, locationListener)
        }
    }

    // Listener para recibir las actualizaciones de ubicacion
    private val locationListener = LocationListener { location ->
        val anterior = ultimaUbicacion
        if (anterior == null || location.distanceTo(anterior) > 30) {
            ultimaUbicacion = location
            guardarUbicacionEnJson(location)

            runOnUiThread {
                val punto = GeoPoint(location.latitude, location.longitude)
                marcadorActual.position = punto
                mapView.controller.animateTo(punto)
                textCoordenadas.text = "Lat: %.5f, Lon: %.5f".format(punto.latitude, punto.longitude)
                mapView.invalidate()
            }
        }
    }

    // Buscar la direccion desde un texto proporcionado
    private fun buscarDireccion(texto: String) {
        try {
            val resultados = Geocoder(this).getFromLocationName(texto, 1)
            if (!resultados.isNullOrEmpty()) {
                val lugar = resultados[0]
                val punto = GeoPoint(lugar.latitude, lugar.longitude)

                if (lugar.latitude == 0.0 && lugar.longitude == 0.0) {
                    Toast.makeText(this, "Dirección inválida", Toast.LENGTH_SHORT).show()
                    return
                }

                marcadorBusqueda?.let { mapView.overlays.remove(it) }

                marcadorBusqueda = Marker(mapView).apply {
                    position = punto
                    title = texto
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }

                mapView.overlays.add(marcadorBusqueda)
                mapView.invalidate()

                // Asegurar que el mapa ya esa cargado
                mapView.postDelayed({
                    mapView.controller.setZoom(18.0)
                    mapView.controller.setCenter(punto)
                    mostrarDistanciaDesdeUbicacion(punto)
                    mostrarRutaDesdeUbicacion(punto)
                }, 200)
            } else {
                Toast.makeText(this, "Dirección no encontrada", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al buscar dirección", Toast.LENGTH_SHORT).show()
        }
    }

    // Configura el long press en el mapa para buscar una direccion desde las coordenadas
    private fun configurarLongClickEnMapa() {
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { buscarDireccionDesdeCoordenadas(it) }
                return true
            }
        }
        mapView.overlays.add(MapEventsOverlay(receiver))
    }

    // Buscar la direccion desde coordenadas y agregarla al mapa
    private fun buscarDireccionDesdeCoordenadas(geoPoint: GeoPoint) {
        try {
            val resultados = Geocoder(this).getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
            val direccion = resultados?.firstOrNull()?.getAddressLine(0) ?: "Dirección desconocida"

            marcadorBusqueda?.let { mapView.overlays.remove(it) }

            marcadorBusqueda = Marker(mapView).apply {
                position = geoPoint
                title = direccion
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }

            mapView.overlays.add(marcadorBusqueda)
            mapView.controller.setZoom(18.0)
            mapView.controller.setCenter(geoPoint)
            mapView.invalidate()

            mostrarDistanciaDesdeUbicacion(geoPoint)
            mostrarRutaDesdeUbicacion(geoPoint)

        } catch (e: Exception) {
            Toast.makeText(this, "Error al obtener dirección", Toast.LENGTH_SHORT).show()
        }
    }

    // Muestra la ruta desde la ubicacion actual hasta una ubicacion específica usando la API de OpenRouteService
    private fun mostrarRutaDesdeUbicacion(destino: GeoPoint) {
        val origen = ultimaUbicacion

        if (origen == null) {
            runOnUiThread {
                Toast.makeText(this, "Ubicación actual aún no disponible", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val cliente = OkHttpClient()
        val url = "https://api.openrouteservice.org/v2/directions/foot-walking"

        val jsonBody = """
        {
          "coordinates": [
            [${origen.longitude}, ${origen.latitude}],
            [${destino.longitude}, ${destino.latitude}]
          ]
        }
    """.trimIndent()

        val mediaType = "application/json".toMediaType()
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", OPENROUTE_API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        Thread {
            try {
                val response = cliente.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Error en la solicitud de ruta", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val json = JSONObject(responseBody)
                val features = json.getJSONArray("routes") // aquí es "routes", no "features"
                val summary = features.getJSONObject(0).getJSONObject("summary")
                val distancia = summary.getDouble("distance").toInt()

                val geometry = features.getJSONObject(0).getString("geometry")

                val puntos = decodePolyline(geometry)

                val ruta = Polyline().apply {
                    color = android.graphics.Color.BLUE
                    width = 8f
                    setPoints(puntos)
                }

                runOnUiThread {
                    rutaActual?.let { mapView.overlays.remove(it) }
                    rutaActual = ruta
                    mapView.overlays.add(ruta)
                    mapView.invalidate()
                    Toast.makeText(this, "Distancia real: $distancia metros", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error al obtener ruta: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    // Decodifica una cadena codificada en formato polilínea en una lista de puntos geograficos (GeoPoint).
    private fun decodePolyline(encoded: String): List<GeoPoint> {
        val poly = ArrayList<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        // Recorre la cadena codificada mientras no se haya alcanzado su longitud
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            // Decodificación de la latitud
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            //S repite el proceso para la longitud
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            // Convierte las coordenadas decodificadas en grados decimales
            val latF = lat / 1E5
            val lngF = lng / 1E5
            poly.add(GeoPoint(latF, lngF))
        }

        return poly // Retorna la lista de puntos geográficos decodificados
    }

    // Calcula y muestra la distancia desde la ubicacion actual hasta un destino.
    private fun mostrarDistanciaDesdeUbicacion(destino: GeoPoint) {
        ultimaUbicacion?.let {
            val origen = Location("").apply {
                latitude = it.latitude
                longitude = it.longitude
            }
            val destinoLoc = Location("").apply {
                latitude = destino.latitude
                longitude = destino.longitude
            }
            val distancia = origen.distanceTo(destinoLoc).toInt()
            Toast.makeText(this, "Distancia a marcador: $distancia metros", Toast.LENGTH_SHORT).show()
        }
    }

    // Guarda la ubicacion actual en un archivo JSON en el almacenamiento interno
    private fun guardarUbicacionEnJson(location: Location) {
        val archivo = File(filesDir, "registro_ubicaciones.json")
        val nuevaEntrada = JSONObject().apply {
            put("latitud", location.latitude)
            put("longitud", location.longitude)
            put("fecha_hora", obtenerFechaHoraActual())
        }
        val jsonArray = try {
            if (archivo.exists()) JSONArray(archivo.readText()) else JSONArray()
        } catch (e: Exception) {
            JSONArray()
        }
        jsonArray.put(nuevaEntrada)
        FileWriter(archivo, false).use { it.write(jsonArray.toString(2)) }
        Toast.makeText(this, "Ubicación guardada", Toast.LENGTH_SHORT).show()
    }

    private fun obtenerFechaHoraActual(): String {
        val formato = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formato.format(Date())
    }

    // Lee el contenido del archivo JSON que contiene las ubicaciones guardadas
    private fun leerArchivoJson(): String {
        return try {
            val archivo = File(filesDir, "registro_ubicaciones.json")
            if (archivo.exists()) archivo.readText() else "Archivo no encontrado."
        } catch (e: Exception) {
            "Error al leer archivo."
        }
    }

    // Muestra el contenido del archivo JSON en un cuadro de dialogo
    private fun mostrarContenidoJson() {
        val contenido = leerArchivoJson()
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Ubicaciones registradas")
            .setMessage(contenido.take(1000))
            .setPositiveButton("Cerrar", null)
            .create()
        dialog.show()
    }

    // SensorListener que monitorea el cambio en el sensor de luz para activar el modo oscuro o claro
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.values?.get(0)?.let { lux ->
                // Si la luz es baja, activa el modo oscuro en el mapa
                if (lux < 10 && !modoOscuro) {
                    mapView.setTileSource(TileSourceFactory.HIKEBIKEMAP)
                    mapView.tileProvider.clearTileCache()
                    mapView.invalidate()
                    modoOscuro = true
                    Toast.makeText(this@MapaActivity, "Modo oscuro activado", Toast.LENGTH_SHORT)
                        .show()
                // Si la luz es suficiente, activa el modo claro en el mapa
                } else if (lux >= 10 && modoOscuro) {
                    mapView.setTileSource(TileSourceFactory.MAPNIK)
                    mapView.tileProvider.clearTileCache()
                    mapView.invalidate()
                    modoOscuro = false
                    Toast.makeText(this@MapaActivity, "Modo claro activado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Registra el listener cuando la actividad se reanuda
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        sensorLuz?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    //Desregistra el listener cuando la actividad se pausa
    override fun onPause() {
        super.onPause()
        mapView.onPause()
        sensorManager.unregisterListener(sensorListener)
    }
}
