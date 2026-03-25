package com.example.studybuddy.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.databinding.ItemEnrolledCourseBinding
import com.example.studybuddy.model.UserCourse

class EnrolledCourseAdapter(
    private val onRemoveClick: (UserCourse) -> Unit,
    private val onItemClick: (UserCourse) -> Unit
) : RecyclerView.Adapter<EnrolledCourseAdapter.ViewHolder>() {

    private var courses: List<UserCourse> = emptyList()

    fun submitList(newCourses: List<UserCourse>) {
        courses = newCourses
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEnrolledCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(courses[position])
    }

    override fun getItemCount(): Int = courses.size

    inner class ViewHolder(private val binding: ItemEnrolledCourseBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(course: UserCourse) {
            val displayName = when(course.planId) {
                "daa_plan" -> "DAA"
                "java_plan" -> "Java"
                else -> course.planId
            }
            binding.courseNameTextView.text = displayName
            binding.slotTextView.text = "Slot: ${course.preferredSlot}"
            
            binding.removeButton.setOnClickListener {
                onRemoveClick(course)
            }
            
            binding.root.setOnClickListener {
                onItemClick(course)
            }
        }
    }
}