package com.example.studybuddy.ui.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.databinding.ItemCourseProgressBinding
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse

data class CourseProgressItem(
    val userCourse: UserCourse,
    val topics: List<TopicDetail>,
    val courseName: String,
    val progress: Int
)

class CourseProgressAdapter(
    private val onItemClick: (UserCourse) -> Unit
) : RecyclerView.Adapter<CourseProgressAdapter.ViewHolder>() {

    private var items: List<CourseProgressItem> = emptyList()

    fun submitList(newItems: List<CourseProgressItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCourseProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemCourseProgressBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CourseProgressItem) {
            binding.courseNameTextView.text = item.courseName
            binding.coursePieChart.setProgress(item.progress.toFloat())
            
            val currentTopic = item.topics.find { it.topicId == item.userCourse.currentTopicId }
            binding.progressStatusTextView.text = "Current: ${currentTopic?.topicName ?: "Completed"}"
            
            binding.root.setOnClickListener {
                onItemClick(item.userCourse)
            }
        }
    }
}