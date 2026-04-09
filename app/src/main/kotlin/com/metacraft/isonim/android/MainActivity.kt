package com.metacraft.isonim.android

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

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
    private lateinit var emptyLabel: TextView
    private lateinit var clearButton: View
    // Native flavor: RecyclerView
    private var adapter: TaskAdapter? = null
    private var recyclerView: RecyclerView? = null

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
        const val CHECKBOX_SIZE = 24
        const val ADD_BUTTON_SIZE = 48
        // Row dimensions (must match iOS exactly)
        const val ROW_HEIGHT = 56
        const val ROW_PADDING_H = 16
        const val ROW_PADDING_V = 12
        const val ROW_GAP = 8
        const val ROW_RADIUS = 12f
        const val DELETE_ICON_SIZE = 20f
    }

    private val isBranded: Boolean
        get() = BuildConfig.IS_BRANDED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SchedulerState.resumed()

        if (isBranded) {
            createBrandedNimUI()
        } else {
            createNativeUI()
        }
    }

    // -----------------------------------------------------------------------
    // Branded: Nim-driven UI via command buffer
    // -----------------------------------------------------------------------

    private var nimRootContainer: LinearLayout? = null
    // EditText lives in Kotlin so the soft keyboard works; Nim drives everything else
    private var nimInputField: EditText? = null

    private fun createBrandedNimUI() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
        }
        nimRootContainer = container

        // --- Kotlin-owned input row (keyboard needs a real EditText) ---
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(OUTER_PADDING), dp(GAP), dp(OUTER_PADDING), dp(GAP))
        }
        val inputField = EditText(this).apply {
            hint = "What needs to be done?"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE
            textSize = BODY_FONT_SIZE
            setTextColor(COLOR_TEXT_PRIMARY)
            val bg = GradientDrawable().apply {
                setColor(COLOR_SURFACE)
                cornerRadius = dp(BUTTON_RADIUS.toInt()).toFloat()
            }
            background = bg
            setPadding(dp(INNER_PADDING), dp(INNER_PADDING), dp(INNER_PADDING), dp(INNER_PADDING))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(GAP)
            }
        }
        inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                nimAddTask(inputField)
                true
            } else false
        }
        nimInputField = inputField
        inputRow.addView(inputField)

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
            setOnClickListener { nimAddTask(inputField) }
        }
        inputRow.addView(addBtn)

        container.addView(inputRow)

        // --- Nim-rendered content area (rebuilt on every state change) ---
        val nimContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        nimContent.id = View.generateViewId()
        nimContentContainer = nimContent
        container.addView(nimContent)

        setContentView(container)

        // Initial render from Nim
        renderNimUI()
    }

    private var nimContentContainer: LinearLayout? = null

    private fun nimAddTask(inputField: EditText) {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return
        NimBridge.nimSetInputText(text)
        NimBridge.nimAddTaskFromInput()
        inputField.text.clear()
        renderNimUI()
    }

    private fun renderNimUI() {
        val content = nimContentContainer ?: return
        // Clear old Nim-rendered views
        NimBridge.reset()
        content.removeAllViews()

        val dm = resources.displayMetrics
        val width = (dm.widthPixels / dm.density).toInt()
        val height = (dm.heightPixels / dm.density).toInt()
        NimBridge.nimBuildBrandedUI(width, height)

        val rootHandle = NimBridge.executeCommandBuffer(this) { callbackId ->
            NimBridge.nimHandleEvent(callbackId)
            // After any Nim callback, rebuild UI to reflect state changes
            renderNimUI()
        }

        val rootView = NimBridge.getView(rootHandle)
        if (rootView != null) {
            // Remove from any existing parent before adding
            (rootView.parent as? ViewGroup)?.removeView(rootView)
            content.addView(rootView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    // -----------------------------------------------------------------------
    // Native: pure-Kotlin Material Design UI (unchanged)
    // -----------------------------------------------------------------------

    private fun createNativeUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0))
        }

        // --- Title ---
        val titleBar = TextView(this).apply {
            text = "Tasks"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textSize = 28f
            setPadding(dp(16), dp(48), dp(16), dp(8))
        }
        root.addView(titleBar)

        // --- Input Row ---
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        val inputField = EditText(this).apply {
            hint = "What needs to be done?"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_DONE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(GAP)
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

        // --- Task List (RecyclerView) ---
        val nativeAdapter = TaskAdapter(
            onToggle = { task -> toggleTask(task) },
            onDelete = { task -> deleteTask(task) },
            isBranded = false
        )
        adapter = nativeAdapter
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = nativeAdapter
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        recyclerView = rv
        root.addView(rv)

        // --- Empty Label ---
        emptyLabel = TextView(this).apply {
            text = "No tasks yet.\nTap + to add one."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0x88888888.toInt())
            setPadding(dp(OUTER_PADDING), dp(48), dp(OUTER_PADDING), dp(48))
        }
        root.addView(emptyLabel)

        // --- Filter Bar ---
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

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
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0))
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
        // Only used for native (non-branded) path; branded uses renderNimUI()
        val filtered = when (currentFilter) {
            TaskFilter.ALL -> tasks.map { it.copy() }
            TaskFilter.ACTIVE -> tasks.filter { !it.isCompleted }.map { it.copy() }
            TaskFilter.COMPLETED -> tasks.filter { it.isCompleted }.map { it.copy() }
        }

        adapter?.submitList(filtered)
        val empty = filtered.isEmpty()
        recyclerView?.visibility = if (empty) View.GONE else View.VISIBLE
        emptyLabel.visibility = if (empty) View.VISIBLE else View.GONE

        emptyLabel.text = when {
            filtered.isEmpty() && currentFilter == TaskFilter.ALL -> "No tasks yet.\nTap + to add one."
            filtered.isEmpty() && currentFilter == TaskFilter.ACTIVE -> "No active tasks.\nAll done!"
            filtered.isEmpty() && currentFilter == TaskFilter.COMPLETED -> "No completed tasks yet."
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
