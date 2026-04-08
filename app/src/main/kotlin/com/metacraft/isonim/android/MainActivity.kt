package com.metacraft.isonim.android

import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors

data class Task(
    val id: Long,
    var title: String,
    var isCompleted: Boolean = false
)

enum class TaskFilter { ALL, ACTIVE, COMPLETED }

class MainActivity : AppCompatActivity() {
    private var nextId = 1L
    private val tasks = mutableListOf<Task>()
    private var currentFilter = TaskFilter.ALL
    private lateinit var adapter: TaskAdapter
    private lateinit var emptyLabel: TextView
    private lateinit var clearButton: MaterialButton
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SchedulerState.resumed()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0))
        }

        // --- Title ---
        val titleBar = TextView(this).apply {
            text = "Tasks"
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(48), dp(16), dp(8))
        }
        root.addView(titleBar)

        // --- Input Row ---
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            gravity = Gravity.CENTER_VERTICAL
        }

        val inputField = EditText(this).apply {
            hint = "What needs to be done?"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            setBackgroundResource(android.R.drawable.edit_text)
        }
        inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTask(inputField)
                true
            } else false
        }
        inputRow.addView(inputField)

        val addBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonFilledStyle).apply {
            setIconResource(android.R.drawable.ic_input_add)
            contentDescription = "Add task"
            setOnClickListener { addTask(inputField) }
        }
        inputRow.addView(addBtn)
        root.addView(inputRow)

        // --- RecyclerView ---
        adapter = TaskAdapter(
            onToggle = { task -> toggleTask(task) },
            onDelete = { task -> deleteTask(task) }
        )

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(recyclerView)

        // --- Empty Label ---
        emptyLabel = TextView(this).apply {
            text = "No tasks yet.\nTap + to add one."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0x88888888.toInt())
            setPadding(dp(16), dp(48), dp(16), dp(48))
        }
        root.addView(emptyLabel)

        // --- Filter Bar ---
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val toggleGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val filterAll = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "All"
            id = android.view.View.generateViewId()
        }
        val filterActive = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Active"
            id = android.view.View.generateViewId()
        }
        val filterCompleted = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Completed"
            id = android.view.View.generateViewId()
        }
        toggleGroup.addView(filterAll)
        toggleGroup.addView(filterActive)
        toggleGroup.addView(filterCompleted)
        toggleGroup.check(filterAll.id)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentFilter = when (checkedId) {
                    filterAll.id -> TaskFilter.ALL
                    filterActive.id -> TaskFilter.ACTIVE
                    filterCompleted.id -> TaskFilter.COMPLETED
                    else -> TaskFilter.ALL
                }
                refreshList()
            }
        }
        bottomBar.addView(toggleGroup)

        clearButton = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Clear Completed"
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0))
            setOnClickListener { clearCompleted() }
        }
        bottomBar.addView(clearButton)

        root.addView(bottomBar)
        setContentView(root)
        refreshList()
    }

    private fun addTask(inputField: EditText) {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return
        tasks.add(Task(id = nextId++, title = text))
        inputField.text.clear()
        refreshList()
    }

    private fun toggleTask(task: Task) {
        tasks.find { it.id == task.id }?.let { it.isCompleted = !it.isCompleted }
        refreshList()
    }

    private fun deleteTask(task: Task) {
        tasks.removeAll { it.id == task.id }
        refreshList()
    }

    private fun clearCompleted() {
        tasks.removeAll { it.isCompleted }
        refreshList()
    }

    private fun refreshList() {
        val filtered = when (currentFilter) {
            TaskFilter.ALL -> tasks.toList()
            TaskFilter.ACTIVE -> tasks.filter { !it.isCompleted }
            TaskFilter.COMPLETED -> tasks.filter { it.isCompleted }
        }
        adapter.submitList(filtered)
        val empty = filtered.isEmpty()
        recyclerView.visibility = if (empty) android.view.View.GONE else android.view.View.VISIBLE
        emptyLabel.visibility = if (empty) android.view.View.VISIBLE else android.view.View.GONE
        emptyLabel.text = when {
            empty && currentFilter == TaskFilter.ALL -> "No tasks yet.\nTap + to add one."
            empty && currentFilter == TaskFilter.ACTIVE -> "No active tasks.\nAll done!"
            empty && currentFilter == TaskFilter.COMPLETED -> "No completed tasks yet."
            else -> ""
        }
        clearButton.isEnabled = tasks.any { it.isCompleted }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        SchedulerState.paused()
    }

    override fun onResume() {
        super.onResume()
        SchedulerState.resumed()
    }
}

/** Kotlin-side scheduler state tracker.
 *  In production, this would call into Nim via JNI to pause/resume
 *  the native scheduler. For now, it tracks state for testing. */
object SchedulerState {
    var isPaused: Boolean = false
        private set

    fun paused() { isPaused = true }
    fun resumed() { isPaused = false }
    fun reset() { isPaused = false }
}
