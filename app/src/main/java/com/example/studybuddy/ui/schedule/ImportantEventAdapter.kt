package com.example.studybuddy.ui.schedule

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.R
import com.example.studybuddy.databinding.ItemImportantEventBinding
import com.example.studybuddy.model.ImportantEvent

class ImportantEventAdapter(private val onEventClicked: (ImportantEvent) -> Unit = {}) : RecyclerView.Adapter<ImportantEventAdapter.ViewHolder>() {

    private var items: List<ImportantEvent> = emptyList()

    fun submitList(newItems: List<ImportantEvent>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemImportantEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemImportantEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: ImportantEvent) {
            binding.eventTitle.text = event.title
            binding.eventDescription.text = event.description
            binding.eventTypeTag.text = event.type.uppercase()
            
            val context = binding.root.context
            val color = when (event.type.lowercase()) {
                "exam" -> ContextCompat.getColor(context, R.color.color_exam)
                "assignment" -> ContextCompat.getColor(context, R.color.color_assignment)
                else -> ContextCompat.getColor(context, R.color.color_other)
            }
            
            binding.eventTypeTag.backgroundTintList = ColorStateList.valueOf(color)
            binding.eventIcon.imageTintList = ColorStateList.valueOf(color)
            binding.root.strokeColor = color

            binding.root.setOnClickListener {
                onEventClicked(event)
            }
        }
    }
}