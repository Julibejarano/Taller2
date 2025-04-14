package com.example.taller2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taller2.databinding.ActivityContactosBinding

class ContactosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactosBinding
    private val REQUEST_CODE_CONTACTOS = 100
    private val contactos = mutableListOf<ContactItem>()

    // Inicializa la actividad y verifica los permisos de contacto.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Verificion de permiso para leer los contactos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CODE_CONTACTOS)
        } else {
            cargarContactos()
        }
    }

    // Maneja el resultado de la solicitud de permisos.
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_CONTACTOS && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cargarContactos()
        }
    }

    // Carga los contactos desde el dispositivo utilizando el ContentResolver.
    private fun cargarContactos() {
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,  // URI para acceder a los contactos
            null,
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"  // Ordenado por nombre
        )

        var index = 1
        cursor?.use {
            // Se obtiene los índices de las columnas que necesitamos: ID, nombre y URI de la foto
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nombreIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val tieneFotoIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

            while (it.moveToNext()) {
                // Se obtiene el nombre del contacto (si no tiene nombre, se pone "Sin nombre")
                val nombre = it.getString(nombreIndex) ?: "Sin nombre"
                // Se obtiene la URI de la foto del contacto
                val photoUri = it.getString(tieneFotoIndex)
                // Se añade el contacto a la lista
                contactos.add(ContactItem(index, nombre, photoUri))
                index++
            }
        }

        // Se configura el RecyclerView para mostrar la lista de contactos
        binding.recyclerContactos.layoutManager = LinearLayoutManager(this)
        binding.recyclerContactos.adapter = ContactAdapter(contactos)
    }
}
