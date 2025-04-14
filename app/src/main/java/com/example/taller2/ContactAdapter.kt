package com.example.taller2

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.taller2.databinding.ItemContactoBinding

class ContactAdapter(private val lista: List<ContactItem>) :
    RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemContactoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contacto = lista[position]
        with(holder.binding) {
            txtNumero.text = contacto.numero.toString()
            txtNombre.text = contacto.nombre
            if (contacto.fotoUri != null) {
                imgFoto.setImageURI(Uri.parse(contacto.fotoUri))
            } else {
                imgFoto.setImageResource(R.drawable.ic_contact)
            }
        }
    }

    override fun getItemCount(): Int = lista.size
}
