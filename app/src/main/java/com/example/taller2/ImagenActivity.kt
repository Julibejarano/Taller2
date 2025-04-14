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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botón Galería
        binding.btnGaleria.setOnClickListener {
            if (tienePermisoGaleria()) {
                abrirGaleria()
            } else {
                solicitarPermisoGaleria()
            }
        }

        // Botón Cámara
        binding.btnCamara.setOnClickListener {
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

    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALERIA)
    }

    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val archivoFoto = crearArchivoTemporal()
            uriFoto = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                archivoFoto
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uriFoto)
            startActivityForResult(intent, REQUEST_CAMARA)
        } else {
            Toast.makeText(this, "No hay aplicación de cámara disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun crearArchivoTemporal(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val nombreArchivo = "IMG_$timeStamp"
        val directorio = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(nombreArchivo, ".jpg", directorio)
    }

    private fun tienePermisoGaleria(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun solicitarPermisoGaleria() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                REQUEST_PERMISO_GALERIA
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_PERMISO_GALERIA
            )
        }
    }

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


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_GALERIA -> {
                    val uri = data?.data
                    if (uri != null) {
                        binding.imagenSeleccionada.setImageURI(uri)
                    } else {
                        Toast.makeText(this, "Error al seleccionar imagen", Toast.LENGTH_SHORT).show()
                    }
                }

                REQUEST_CAMARA -> {
                    binding.imagenSeleccionada.setImageURI(uriFoto)
                    guardarEnGaleria(uriFoto) // <- aquí la guardas en galería
                    Toast.makeText(this, "Foto tomada y guardada en galería con exito", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun guardarEnGaleria(uri: Uri) {
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Taller2") // Puedes cambiar "Taller2" por otro nombre
        }

        val resolver = contentResolver
        val nuevaUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        nuevaUri?.let { destinoUri ->
            resolver.openOutputStream(destinoUri).use { outputStream ->
                contentResolver.openInputStream(uri).use { inputStream ->
                    inputStream?.copyTo(outputStream!!, bufferSize = 4096)
                }
            }
        }
    }

}
