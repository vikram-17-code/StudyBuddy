package com.example.studybuddy.ui.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.studybuddy.databinding.ItemTopicSelectableBinding
import com.example.studybuddy.model.TopicDetail

class TopicListAdapter : RecyclerView.Adapter<TopicListAdapter.TopicViewHolder>() {

    private var topics: List<TopicDetail> = emptyList()

    fun submitList(newTopics: List<TopicDetail>) {
        topics = newTopics
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val binding = ItemTopicSelectableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TopicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        holder.bind(topics[position])
    }

    override fun getItemCount(): Int = topics.size

    inner class TopicViewHolder(private val binding: ItemTopicSelectableBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(topic: TopicDetail) {
            binding.topicNameTextView.text = topic.topicName
            binding.durationTextView.text = "${topic.requiredDays} Days"
        }
    }
}