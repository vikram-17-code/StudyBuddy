package com.example.studybuddy.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.databinding.ItemTaskBinding
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse

class TaskAdapter(
    private val showCompleteButton: Boolean = true,
    private val showDayInfo: Boolean = true,
    private val onTaskClick: (UserCourse) -> Unit = {},
    private val onCompleteClick: (UserCourse, TopicDetail) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    // Pair of UserCourse and TopicDetail, and a String for Course Name
    private var items: List<Triple<UserCourse, TopicDetail, String>> = emptyList()

    fun submitList(newItems: List<Triple<UserCourse, TopicDetail, String>>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val (userCourse, topic, courseName) = items[position]
        holder.bind(userCourse, topic, courseName)
    }

    override fun getItemCount(): Int = items.size

    inner class TaskViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(userCourse: UserCourse, topic: TopicDetail, courseName: String) {
            binding.courseNameTextView.text = courseName
            binding.topicNameTextView.text = topic.topicName
            
            if (showDayInfo) {
                binding.dayNumberTextView.visibility = View.VISIBLE
                binding.dayNumberTextView.text = "Day ${userCourse.currentDayNumber} of ${topic.requiredDays}"
            } else {
                binding.dayNumberTextView.visibility = View.GONE
            }

            binding.materialLinkTextView.text = if (topic.materialLink.isNotEmpty()) "View Study Material" else ""
            
            binding.root.setOnClickListener {
                onTaskClick(userCourse)
            }
            
            if (showCompleteButton) {
                binding.completeTaskButton.visibility = View.VISIBLE
                binding.completeTaskButton.setOnClickListener {
                    onCompleteClick(userCourse, topic)
                }
            } else {
                binding.completeTaskButton.visibility = View.GONE
            }
        }
    }
}