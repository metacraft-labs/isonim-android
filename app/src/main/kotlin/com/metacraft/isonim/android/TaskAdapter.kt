package com.metacraft.isonim.android

import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private val onToggle: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }

        val checkBox = CheckBox(context).apply {
            id = View.generateViewId()
        }
        row.addView(checkBox)

        val titleView = TextView(context).apply {
            id = View.generateViewId()
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            }
        }
        row.addView(titleView)

        val deleteBtn = ImageButton(context).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundResource(android.R.color.transparent)
            contentDescription = "Delete task"
        }
        row.addView(deleteBtn)

        return TaskViewHolder(row, checkBox, titleView, deleteBtn)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task, onToggle, onDelete)
    }

    class TaskViewHolder(
        itemView: View,
        private val checkBox: CheckBox,
        private val titleView: TextView,
        private val deleteBtn: ImageButton
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(task: Task, onToggle: (Task) -> Unit, onDelete: (Task) -> Unit) {
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = task.isCompleted
            titleView.text = task.title

            if (task.isCompleted) {
                titleView.paintFlags = titleView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                titleView.alpha = 0.5f
            } else {
                titleView.paintFlags = titleView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                titleView.alpha = 1.0f
            }

            checkBox.setOnCheckedChangeListener { _, _ -> onToggle(task) }
            deleteBtn.setOnClickListener { onDelete(task) }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) =
            oldItem.title == newItem.title && oldItem.isCompleted == newItem.isCompleted
    }
}
