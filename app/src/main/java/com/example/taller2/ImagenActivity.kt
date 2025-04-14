package com.example.taller2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.taller2.databinding.ActivityImagenBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ImagenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagenBinding
    private lateinit var uriFoto: Uri

    private val REQUEST_GALERIA = 1
    private val REQUEST_CAMARA = 2
    private val REQUEST_PERMISO_CAMARA = 200
    private val REQUEST_PERMISO_GALERIA = 201

    // Configura los botones para abrir la galería o la cámara, y solicita permisos si es necesario.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botón Galería
        binding.btnGaleria.setOnClickListener {
            // Verifica si la aplicación tiene permiso para acceder a la galería
            if (tienePermisoGaleria()) {
                abrirGaleria()
            } else {
                solicitarPermisoGaleria()
            }
        }

        // Botón Cámara
        binding.btnCamara.setOnClickListener {
            // Verifica si la aplicación tiene permiso para usar la cámara
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_PERMISO_CAMARA
                )
            } else {
                abrirCamara()
            }
        }
    }

    // Abre la galería
    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALERIA)
    }

    // Abrir la camara y captura la imagen
    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            // Si existe una aplicación para manejar la cámara, crea un archivo temporal
            val archivoFoto = crearArchivoTemporal()
            uriFoto = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider", // Proporciona un URI para el archivo temporal
                archivoFoto
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uriFoto) // Pasa el URI a la camara
            startActivityForResult(intent, REQUEST_CAMARA) // Inicia la camara
        } else {
            Toast.makeText(this, "No hay aplicación de cámara disponible", Toast.LENGTH_SHORT).show()
        }
    }

    // Crea un archivo temporal para la foto tomada
    private fun crearArchivoTemporal(): File {
        // Genera un nombre único para la foto usando un timestamp
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val nombreArchivo = "IMG_$timeStamp"
        val directorio = getExternalFilesDir(Environment.DIRECTORY_PICTURES) // Directorio de fotos
        return File.createTempFile(nombreArchivo, ".jpg", directorio) // Crea el archivo temporal
    }

    // Verifica si se tiene permiso para acceder a la galería
    private fun tienePermisoGaleria(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Para versiones más recientes de Android, se usa un permiso diferente para leer imágenes
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            // Para versiones anteriores, se usa el permiso de almacenamiento externo
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    // Solicita el permiso necesario para acceder a la galeria
    private fun solicitarPermisoGaleria() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES), // Solicita el permiso para leer imagenes
                REQUEST_PERMISO_GALERIA
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), // Solicita el permiso para leer almacenamiento
                REQUEST_PERMISO_GALERIA
            )
        }
    }

    // Maneja la respuesta de los permisos solicitados
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_PERMISO_CAMARA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    abrirCamara()
                } else {
                    Toast.makeText(this, "Se necesita permiso para usar la cámara", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_PERMISO_GALERIA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    abrirGaleria()
                } else {
                    Toast.makeText(this, "Se necesita permiso para acceder a la galería", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Maneja el resultado de las actividades de cámara o galería
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_GALERIA -> {
                    val uri = data?.data
                    if (uri != null) {
                        binding.imagenSeleccionada.setImageURI(uri) // Muestra la imagen seleccionada en la vista
                    } else {
                        Toast.makeText(this, "Error al seleccionar imagen", Toast.LENGTH_SHORT).show()
                    }
                }

                REQUEST_CAMARA -> {
                    binding.imagenSeleccionada.setImageURI(uriFoto) // Muestra la imagen tomada desde la cámara
                    guardarEnGaleria(uriFoto) // Guarda la foto en la galería
                    Toast.makeText(this, "Foto tomada y guardada en galería con exito", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Guarda la foto tomada en la galería
    private fun guardarEnGaleria(uri: Uri) {
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Taller2")
        }

        val resolver = contentResolver
        // Inserta la imagen en la galería y obtiene el URI de la nueva imagen
        val nuevaUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        nuevaUri?.let { destinoUri ->
            resolver.openOutputStream(destinoUri).use { outputStream ->
                contentResolver.openInputStream(uri).use { inputStream ->
                    inputStream?.copyTo(outputStream!!, bufferSize = 4096) // Copia la imagen a la nueva ubicación
                }
            }
        }
    }

}
