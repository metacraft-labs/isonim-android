package com.metacraft.isonim.android

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
    private lateinit var clearButton: View
    private lateinit var recyclerView: RecyclerView
    private var filterPillButtons: List<TextView> = emptyList()

    // isoTheme design tokens (identical to iOS)
    companion object {
        const val COLOR_PRIMARY = 0xFF6366F1.toInt()
        const val COLOR_BACKGROUND = 0xFFF8FAFC.toInt()
        const val COLOR_SURFACE = 0xFFFFFFFF.toInt()
        const val COLOR_TEXT_PRIMARY = 0xFF0F172A.toInt()
        const val COLOR_TEXT_SECONDARY = 0xFF64748B.toInt()
        const val COLOR_TEXT_DISABLED = 0xFFCBD5E1.toInt()
        const val COLOR_BORDER = 0xFFE2E8F0.toInt()
        const val COLOR_ERROR = 0xFFEF4444.toInt()

        const val OUTER_PADDING = 16
        const val INNER_PADDING = 12
        const val GAP = 8
        const val BUTTON_RADIUS = 8f
        const val CHECKBOX_RADIUS = 6f
        const val FILTER_PILL_RADIUS = 16f
        const val TITLE_FONT_SIZE = 32f
        const val BODY_FONT_SIZE = 16f
        const val CAPTION_FONT_SIZE = 14f
        const val ICON_FONT_SIZE = 24f
        const val CHECKBOX_SIZE = 28
        const val ADD_BUTTON_SIZE = 48
    }

    private val isBranded: Boolean
        get() = BuildConfig.IS_BRANDED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SchedulerState.resumed()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (isBranded) {
                setBackgroundColor(COLOR_BACKGROUND)
            } else {
                setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0))
            }
        }

        // --- Title ---
        val titleBar = TextView(this).apply {
            text = "Tasks"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (isBranded) {
                textSize = TITLE_FONT_SIZE
                setTextColor(COLOR_TEXT_PRIMARY)
                setPadding(dp(OUTER_PADDING), dp(48), dp(OUTER_PADDING), dp(GAP))
            } else {
                textSize = 28f
                setPadding(dp(16), dp(48), dp(16), dp(8))
            }
        }
        root.addView(titleBar)

        // --- Input Row ---
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (isBranded) {
                setPadding(dp(OUTER_PADDING), dp(GAP), dp(OUTER_PADDING), dp(GAP))
            } else {
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
        }

        val inputField = EditText(this).apply {
            hint = "What needs to be done?"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(GAP)
            }
            if (isBranded) {
                textSize = BODY_FONT_SIZE
                setTextColor(COLOR_TEXT_PRIMARY)
                val bg = GradientDrawable().apply {
                    setColor(COLOR_SURFACE)
                    cornerRadius = dp(BUTTON_RADIUS.toInt()).toFloat()
                }
                background = bg
                setPadding(dp(INNER_PADDING), dp(INNER_PADDING), dp(INNER_PADDING), dp(INNER_PADDING))
            } else {
                setBackgroundResource(android.R.drawable.edit_text)
            }
        }
        inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTask(inputField)
                true
            } else false
        }
        inputRow.addView(inputField)

        if (isBranded) {
            // Custom add button: 48dp filled rounded rect with "+" text
            val addBtn = TextView(this).apply {
                text = "+"
                textSize = ICON_FONT_SIZE
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                val bg = GradientDrawable().apply {
                    setColor(COLOR_PRIMARY)
                    cornerRadius = dp(BUTTON_RADIUS.toInt()).toFloat()
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(dp(ADD_BUTTON_SIZE), dp(ADD_BUTTON_SIZE))
                setOnClickListener { addTask(inputField) }
            }
            inputRow.addView(addBtn)
        } else {
            val addBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonFilledStyle).apply {
                setIconResource(android.R.drawable.ic_input_add)
                contentDescription = "Add task"
                setOnClickListener { addTask(inputField) }
            }
            inputRow.addView(addBtn)
        }
        root.addView(inputRow)

        // --- RecyclerView ---
        adapter = TaskAdapter(
            onToggle = { task -> toggleTask(task) },
            onDelete = { task -> deleteTask(task) },
            isBranded = isBranded
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
            if (isBranded) {
                setTextColor(COLOR_TEXT_SECONDARY)
            } else {
                setTextColor(0x88888888.toInt())
            }
            setPadding(dp(OUTER_PADDING), dp(48), dp(OUTER_PADDING), dp(48))
        }
        root.addView(emptyLabel)

        // --- Filter Bar ---
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            if (isBranded) {
                setPadding(dp(OUTER_PADDING), dp(GAP), dp(OUTER_PADDING), dp(OUTER_PADDING))
            } else {
                setPadding(dp(16), dp(8), dp(16), dp(16))
            }
        }

        if (isBranded) {
            // Custom filter pills: rounded buttons with 16dp radius
            val filterRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val pills = mutableListOf<TextView>()
            for (filter in TaskFilter.values()) {
                val pill = TextView(this).apply {
                    text = when (filter) {
                        TaskFilter.ALL -> "All"
                        TaskFilter.ACTIVE -> "Active"
                        TaskFilter.COMPLETED -> "Completed"
                    }
                    textSize = CAPTION_FONT_SIZE
                    gravity = Gravity.CENTER
                    setPadding(dp(INNER_PADDING), dp(GAP), dp(INNER_PADDING), dp(GAP))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(4) }
                    setOnClickListener {
                        currentFilter = filter
                        updateFilterPills()
                        refreshList()
                    }
                }
                pills.add(pill)
                filterRow.addView(pill)
            }
            filterPillButtons = pills
            updateFilterPills()
            bottomBar.addView(filterRow)

            clearButton = TextView(this).apply {
                text = "Clear Completed"
                textSize = CAPTION_FONT_SIZE
                setTextColor(COLOR_ERROR)
                gravity = Gravity.CENTER
                setPadding(0, dp(GAP), 0, 0)
                setOnClickListener { clearCompleted() }
            }
        } else {
            val toggleGroup = MaterialButtonToggleGroup(this).apply {
                isSingleSelection = true
                isSelectionRequired = true
            }
            val filterAll = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "All"
                id = View.generateViewId()
            }
            val filterActive = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Active"
                id = View.generateViewId()
            }
            val filterCompleted = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Completed"
                id = View.generateViewId()
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
        }
        bottomBar.addView(clearButton)

        root.addView(bottomBar)
        setContentView(root)
        refreshList()
    }

    private fun updateFilterPills() {
        for ((index, pill) in filterPillButtons.withIndex()) {
            val filter = TaskFilter.values()[index]
            val isActive = filter == currentFilter
            val bg = GradientDrawable().apply {
                cornerRadius = dp(FILTER_PILL_RADIUS.toInt()).toFloat()
                if (isActive) setColor(COLOR_PRIMARY) else setColor(Color.TRANSPARENT)
            }
            pill.background = bg
            pill.setTextColor(if (isActive) Color.WHITE else COLOR_TEXT_SECONDARY)
        }
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
        val hasCompleted = tasks.any { it.isCompleted }
        clearButton.isEnabled = hasCompleted
        clearButton.alpha = if (hasCompleted) 1.0f else 0.4f
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
