package com.metacraft.isonim.android

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
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
    private val onDelete: (Task) -> Unit,
    private val isBranded: Boolean = false
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        if (isBranded) {
            // Branded: custom-drawn controls with isoTheme dimensions
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(MainActivity.INNER_PADDING), dp(MainActivity.INNER_PADDING),
                           dp(MainActivity.INNER_PADDING), dp(MainActivity.INNER_PADDING))
                val bg = GradientDrawable().apply {
                    setColor(MainActivity.COLOR_SURFACE)
                    cornerRadius = dp(MainActivity.BUTTON_RADIUS.toInt()).toFloat()
                }
                background = bg
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(MainActivity.GAP)
                    marginStart = dp(MainActivity.GAP)
                    marginEnd = dp(MainActivity.GAP)
                }
            }

            // Custom checkbox: 28x28 rounded rect
            val checkboxView = TextView(context).apply {
                id = View.generateViewId()
                gravity = Gravity.CENTER
                textSize = MainActivity.BODY_FONT_SIZE
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    dp(MainActivity.CHECKBOX_SIZE), dp(MainActivity.CHECKBOX_SIZE)
                )
            }
            row.addView(checkboxView)

            val titleView = TextView(context).apply {
                id = View.generateViewId()
                textSize = MainActivity.BODY_FONT_SIZE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(MainActivity.INNER_PADDING)
                    marginEnd = dp(MainActivity.GAP)
                }
            }
            row.addView(titleView)

            // Delete button: text cross in error color
            val deleteBtn = TextView(context).apply {
                id = View.generateViewId()
                text = "\u2715"
                textSize = 18f
                setTextColor(MainActivity.COLOR_ERROR)
                gravity = Gravity.CENTER
                setPadding(dp(MainActivity.GAP), dp(MainActivity.GAP),
                           dp(MainActivity.GAP), dp(MainActivity.GAP))
            }
            row.addView(deleteBtn)

            return TaskViewHolder(row, null, checkboxView, titleView, deleteBtn, null, isBranded = true)
        } else {
            // Native: standard Material controls
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

            return TaskViewHolder(row, checkBox, null, titleView, null, deleteBtn, isBranded = false)
        }
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task, onToggle, onDelete)
    }

    class TaskViewHolder(
        itemView: View,
        private val checkBox: CheckBox?,
        private val brandedCheckbox: TextView?,
        private val titleView: TextView,
        private val brandedDeleteBtn: TextView?,
        private val nativeDeleteBtn: ImageButton?,
        private val isBranded: Boolean
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(task: Task, onToggle: (Task) -> Unit, onDelete: (Task) -> Unit) {
            titleView.text = task.title

            if (isBranded) {
                val density = itemView.resources.displayMetrics.density
                fun dp(v: Int) = (v * density).toInt()

                // Custom checkbox appearance
                val checkBg = GradientDrawable().apply {
                    cornerRadius = dp(MainActivity.CHECKBOX_RADIUS.toInt()).toFloat()
                    if (task.isCompleted) {
                        setColor(MainActivity.COLOR_PRIMARY)
                    } else {
                        setColor(Color.TRANSPARENT)
                        setStroke(dp(2), MainActivity.COLOR_BORDER)
                    }
                }
                brandedCheckbox?.background = checkBg
                brandedCheckbox?.text = if (task.isCompleted) "\u2713" else ""
                brandedCheckbox?.setOnClickListener { onToggle(task) }

                titleView.setTextColor(
                    if (task.isCompleted) MainActivity.COLOR_TEXT_DISABLED
                    else MainActivity.COLOR_TEXT_PRIMARY
                )
                titleView.paintFlags = titleView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                titleView.alpha = 1.0f

                brandedDeleteBtn?.setOnClickListener { onDelete(task) }
            } else {
                checkBox?.setOnCheckedChangeListener(null)
                checkBox?.isChecked = task.isCompleted

                if (task.isCompleted) {
                    titleView.paintFlags = titleView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    titleView.alpha = 0.5f
                } else {
                    titleView.paintFlags = titleView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    titleView.alpha = 1.0f
                }

                checkBox?.setOnCheckedChangeListener { _, _ -> onToggle(task) }
                nativeDeleteBtn?.setOnClickListener { onDelete(task) }
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) =
            oldItem.title == newItem.title && oldItem.isCompleted == newItem.isCompleted
    }
}
