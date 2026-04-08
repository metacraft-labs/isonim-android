package com.metacraft.isonim.android

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * Scenario-driven snapshot tests for the branded task manager UI.
 *
 * Renders the same 6 scenarios as isonim-cocoa (empty, three tasks,
 * one completed, all completed, filtered active, filtered completed)
 * using Paparazzi's host-side layoutlib — no emulator needed.
 *
 * First run: `./gradlew :app:recordPaparazziNativeDebug` creates golden PNGs.
 * Verify:    `./gradlew :app:verifyPaparazziNativeDebug` compares against them.
 */
class ScenarioSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "Theme.MaterialComponents.DayNight.NoActionBar"
    )

    // ── isoTheme design tokens (identical to iOS and MainActivity) ──

    private val PRIMARY = 0xFF6366F1.toInt()
    private val BACKGROUND = 0xFFF8FAFC.toInt()
    private val SURFACE = 0xFFFFFFFF.toInt()
    private val TEXT_PRIMARY = 0xFF0F172A.toInt()
    private val TEXT_SECONDARY = 0xFF64748B.toInt()
    private val TEXT_DISABLED = 0xFFCBD5E1.toInt()
    private val BORDER = 0xFFE2E8F0.toInt()
    private val ERROR = 0xFFEF4444.toInt()

    private val OUTER_PADDING = 16
    private val INNER_PADDING = 12
    private val GAP = 8
    private val ROW_HEIGHT = 56
    private val ROW_PADDING_H = 16
    private val ROW_PADDING_V = 12
    private val ROW_GAP = 8
    private val ROW_RADIUS = 12f
    private val CHECKBOX_SIZE = 24
    private val CHECKBOX_RADIUS = 6f
    private val BODY_FONT = 16f
    private val TITLE_FONT = 32f
    private val CAPTION_FONT = 14f
    private val ICON_FONT = 24f

    // ── Scenario definitions (mirror isonim/components/scenarios.nim) ──

    data class TaskScenario(
        val name: String,
        val tasks: List<Task>,
        val filter: TaskFilter = TaskFilter.ALL
    )

    private val scenarios = listOf(
        TaskScenario("empty", emptyList()),
        TaskScenario("three_tasks", listOf(
            Task(1, "Buy groceries"), Task(2, "Walk the dog"), Task(3, "Read a book")
        )),
        TaskScenario("one_completed", listOf(
            Task(1, "Buy groceries", true), Task(2, "Walk the dog"), Task(3, "Read a book")
        )),
        TaskScenario("all_completed", listOf(
            Task(1, "Buy groceries", true), Task(2, "Walk the dog", true), Task(3, "Read a book", true)
        )),
        TaskScenario("filtered_active", listOf(
            Task(1, "Buy groceries", true), Task(2, "Walk the dog"), Task(3, "Read a book")
        ), TaskFilter.ACTIVE),
        TaskScenario("filtered_completed", listOf(
            Task(1, "Buy groceries", true), Task(2, "Walk the dog"), Task(3, "Read a book")
        ), TaskFilter.COMPLETED),
    )

    // ── Branded view builder (replicates MainActivity.createBrandedRow logic) ──

    private fun buildBrandedView(scenario: TaskScenario): View {
        val context = paparazzi.context
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            setPadding(dp(OUTER_PADDING), dp(48), dp(OUTER_PADDING), dp(OUTER_PADDING))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        root.addView(TextView(context).apply {
            text = "Tasks"
            textSize = TITLE_FONT
            setTextColor(TEXT_PRIMARY)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(GAP))
        })

        // Input row
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(GAP), 0, dp(GAP))
        }
        inputRow.addView(EditText(context).apply {
            hint = "What needs to be done?"
            textSize = BODY_FONT
            val bg = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = dp(GAP).toFloat()
                setStroke(dp(1), BORDER)
            }
            background = bg
            setPadding(dp(INNER_PADDING), dp(INNER_PADDING), dp(INNER_PADDING), dp(INNER_PADDING))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(GAP)
            }
        })
        inputRow.addView(TextView(context).apply {
            text = "+"
            textSize = ICON_FONT
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(PRIMARY)
                cornerRadius = dp(GAP).toFloat()
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })
        root.addView(inputRow)

        // Filter visible tasks
        val filtered = when (scenario.filter) {
            TaskFilter.ALL -> scenario.tasks
            TaskFilter.ACTIVE -> scenario.tasks.filter { !it.isCompleted }
            TaskFilter.COMPLETED -> scenario.tasks.filter { it.isCompleted }
        }

        // Task rows or empty state
        if (filtered.isEmpty()) {
            root.addView(TextView(context).apply {
                text = "No tasks yet.\nTap + to add one."
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(TEXT_SECONDARY)
                setPadding(dp(OUTER_PADDING), dp(48), dp(OUTER_PADDING), dp(48))
            })
        } else {
            for (task in filtered) {
                root.addView(createBrandedRow(context, task, density))
            }
        }

        // Filter bar
        val filterBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(INNER_PADDING), 0, 0)
        }
        for (filter in TaskFilter.values()) {
            val label = when (filter) {
                TaskFilter.ALL -> "All"
                TaskFilter.ACTIVE -> "Active"
                TaskFilter.COMPLETED -> "Completed"
            }
            val isActive = filter == scenario.filter
            filterBar.addView(TextView(context).apply {
                text = label
                textSize = CAPTION_FONT
                gravity = Gravity.CENTER
                setPadding(dp(OUTER_PADDING), dp(GAP), dp(OUTER_PADDING), dp(GAP))
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    if (isActive) setColor(PRIMARY) else setColor(Color.TRANSPARENT)
                }
                background = bg
                setTextColor(if (isActive) Color.WHITE else TEXT_SECONDARY)
            })
        }
        root.addView(filterBar)

        // Clear completed
        root.addView(TextView(context).apply {
            text = "Clear Completed"
            textSize = CAPTION_FONT
            gravity = Gravity.CENTER
            setTextColor(ERROR)
            setPadding(0, dp(GAP), 0, 0)
            alpha = if (scenario.tasks.any { it.isCompleted }) 1.0f else 0.4f
        })

        return root
    }

    private fun createBrandedRow(
        context: android.content.Context,
        task: Task,
        density: Float
    ): LinearLayout {
        fun dp(v: Int) = (v * density).toInt()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ROW_PADDING_H), dp(ROW_PADDING_V), dp(ROW_PADDING_H), dp(ROW_PADDING_V))
            val bg = GradientDrawable().apply {
                setColor(SURFACE)
                cornerRadius = dp(ROW_RADIUS.toInt()).toFloat()
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT)
            ).apply { bottomMargin = dp(ROW_GAP) }
        }

        // Checkbox
        row.addView(TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = BODY_FONT
            setTextColor(Color.WHITE)
            text = if (task.isCompleted) "\u2713" else ""
            layoutParams = LinearLayout.LayoutParams(dp(CHECKBOX_SIZE), dp(CHECKBOX_SIZE))
            val bg = GradientDrawable().apply {
                cornerRadius = dp(CHECKBOX_RADIUS.toInt()).toFloat()
                if (task.isCompleted) setColor(PRIMARY) else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(2), BORDER)
                }
            }
            background = bg
        })

        // Label
        row.addView(TextView(context).apply {
            text = task.title
            textSize = BODY_FONT
            setTextColor(if (task.isCompleted) TEXT_DISABLED else TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(INNER_PADDING)
                marginEnd = dp(GAP)
            }
        })

        // Delete
        row.addView(TextView(context).apply {
            text = "\u2715"
            textSize = 18f
            setTextColor(ERROR)
            gravity = Gravity.CENTER
        })

        return row
    }

    // ── One test per scenario ──

    @Test fun snapshotEmpty() = paparazzi.snapshot { buildBrandedView(scenarios[0]) }
    @Test fun snapshotThreeTasks() = paparazzi.snapshot { buildBrandedView(scenarios[1]) }
    @Test fun snapshotOneCompleted() = paparazzi.snapshot { buildBrandedView(scenarios[2]) }
    @Test fun snapshotAllCompleted() = paparazzi.snapshot { buildBrandedView(scenarios[3]) }
    @Test fun snapshotFilteredActive() = paparazzi.snapshot { buildBrandedView(scenarios[4]) }
    @Test fun snapshotFilteredCompleted() = paparazzi.snapshot { buildBrandedView(scenarios[5]) }
}
