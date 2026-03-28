package com.example.studybuddy.ui.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.databinding.ItemCourseProgressBinding
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse

class CourseProgressAdapter(
    private val onItemClick: (UserCourse) -> Unit
) : RecyclerView.Adapter<CourseProgressAdapter.ViewHolder>() {

    private var items: List<Triple<UserCourse, List<TopicDetail>, Int>> = emptyList()

    fun submitList(newItems: List<Triple<UserCourse, List<TopicDetail>, Int>>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCourseProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (userCourse, topics, progress) = items[position]
        holder.bind(userCourse, topics, progress)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemCourseProgressBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(userCourse: UserCourse, topics: List<TopicDetail>, progress: Int) {
            binding.courseNameTextView.text = userCourse.planId.replace("_plan", "").uppercase()
            binding.coursePieChart.setProgress(progress.toFloat())
            
            val currentTopic = topics.find { it.topicId == userCourse.currentTopicId }
            binding.progressStatusTextView.text = "Current: ${currentTopic?.topicName ?: "Completed"}"
            
            binding.root.setOnClickListener {
                onItemClick(userCourse)
            }
        }
    }
}