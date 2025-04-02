package com.example.pomodorotimer.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pomodorotimer.R
import com.example.pomodorotimer.data.entity.PomodoroSession
import com.example.pomodorotimer.databinding.ItemPomodoroSessionBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PomodoroSessionAdapter : ListAdapter<PomodoroSession, PomodoroSessionAdapter.SessionViewHolder>(SessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemPomodoroSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SessionViewHolder(private val binding: ItemPomodoroSessionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(session: PomodoroSession) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val formattedDate = dateFormat.format(session.date)

            binding.tvSessionDate.text = "${binding.root.context.getString(R.string.completed_on)} $formattedDate"
            binding.tvSessionDuration.text = "${session.duration} ${binding.root.context.getString(R.string.minutes)}"
            binding.tvSessionStatus.text = if (session.completed) "Completed" else "Failed"
        }
    }

    class SessionDiffCallback : DiffUtil.ItemCallback<PomodoroSession>() {
        override fun areItemsTheSame(oldItem: PomodoroSession, newItem: PomodoroSession): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PomodoroSession, newItem: PomodoroSession): Boolean {
            return oldItem == newItem
        }
    }
}

