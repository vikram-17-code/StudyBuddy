package com.example.studybuddy.ui.profile

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.databinding.ItemTopicStatusBinding
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse

class TopicStatusAdapter(
    private val onCompleteClick: (TopicDetail) -> Unit
) : RecyclerView.Adapter<TopicStatusAdapter.ViewHolder>() {

    private var items: List<Pair<TopicDetail, String>> = emptyList()
    private var currentUserCourse: UserCourse? = null

    fun submitList(topics: List<TopicDetail>, userCourse: UserCourse) {
        currentUserCourse = userCourse
        val currentTopicId = userCourse.currentTopicId
        val currentTopic = topics.find { it.topicId == currentTopicId }
        
        items = topics.sortedBy { it.topicOrder }.map { topic ->
            val status = when {
                userCourse.currentTopicId == "COMPLETED" -> "Completed"
                currentTopic == null -> "Upcoming"
                topic.topicOrder < currentTopic.topicOrder -> "Completed"
                topic.topicOrder == currentTopic.topicOrder -> "In Progress (Day ${userCourse.currentDayNumber}/${topic.requiredDays})"
                else -> "Upcoming"
            }
            topic to status
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopicStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (topic, status) = items[position]
        holder.bind(topic, status)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemTopicStatusBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(topic: TopicDetail, status: String) {
            binding.topicNameTextView.text = topic.topicName
            binding.durationTextView.text = "${topic.requiredDays} Days"
            binding.completionStatusTextView.text = status
            
            val iconRes = if (status == "Completed") {
                android.R.drawable.checkbox_on_background
            } else if (status.contains("In Progress")) {
                android.R.drawable.ic_menu_edit
            } else {
                android.R.drawable.checkbox_off_background
            }
            binding.statusIcon.setImageResource(iconRes)

            // Material Link
            binding.viewMaterialButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(topic.materialLink))
                it.context.startActivity(intent)
            }

            // Mark as complete logic - only show for current topic if not completed
            if (status.contains("In Progress")) {
                binding.markTopicCompleteButton.visibility = View.VISIBLE
                binding.markTopicCompleteButton.setOnClickListener {
                    onCompleteClick(topic)
                }
            } else {
                binding.markTopicCompleteButton.visibility = View.GONE
            }
        }
    }
}