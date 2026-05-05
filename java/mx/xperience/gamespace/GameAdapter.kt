/*
 * Copyright (C) 2026 The XPerience Project
 * SPDX-License-Identifier: Apache-2.0
 */
package mx.xperience.gamespace

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class GameAdapter(
    private val games: MutableList<GameModel>,
        private val onClick: (String) -> Unit,
        private val onLongClick: (String) -> Unit,
        private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_GAME = 0
        private const val TYPE_ADD_BUTTON = 1
    }

    override fun getItemViewType(position: Int): Int {
        // Si la posición es igual al tamaño de la lista, es el botón de añadir
        return if (position == games.size) TYPE_ADD_BUTTON else TYPE_GAME
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GAME) {
            val view = inflater.inflate(R.layout.item_game, parent, false)
            GameViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_game, parent, false) // Reusamos el layout
            AddViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        if (holder is GameViewHolder) {
            val game = games[position]
            holder.icon.setImageDrawable(game.icon)
            holder.name.text = game.name
            holder.itemView.setOnClickListener { onClick(game.packageName) }

            // LongClick to delete
            holder.itemView.setOnLongClickListener {
                onLongClick(game.packageName)
                true
            }

        } else if (holder is AddViewHolder) {
            // Configuramos el look de "Añadir"
            holder.name.text = context.getString(R.string.gs_add_game)
            holder.card.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            holder.card.setBackgroundResource(R.drawable.bg_add_game)
            holder.icon.setImageResource(android.R.drawable.ic_input_add) // Icono de +
            holder.icon.setPadding(40, 40, 40, 40) // Para que el + se vea centrado
            holder.icon.alpha = 0.6f

            holder.itemView.setOnClickListener { onAddClick() }
        }
    }

    override fun getItemCount() = games.size + 1

    class GameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_game)
        val icon: ImageView = view.findViewById(R.id.game_icon)
        val name: TextView = view.findViewById(R.id.game_name)
    }

    class AddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_game)
        val icon: ImageView = view.findViewById(R.id.game_icon)
        val name: TextView = view.findViewById(R.id.game_name)
    }
}
