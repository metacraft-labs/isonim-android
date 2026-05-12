package com.metacraft.isonim.examples

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EX-M22 acceptance test.
 *
 * Drives the canonical `settings_app` scripted scenarios through a real
 * Android Activity on a real device, hitting the Nim composition root
 * via the [SettingsAppBridge] JNI namespace (which calls into
 * `libsettings_app.so`, cross-compiled from
 * `isonim-examples/settings_app/main_android_entry.nim`). Mirrors the
 * EX-M6 `TaskAppScenarioTest` for the settings demo. Every assertion
 * reads the real Android view tree — no mocks, no in-process MockJNI.
 *
 * The five canonical scenarios (matching EX-M10..EX-M20):
 *
 *   - A: basic — activate "appearance", toggle dark_mode.
 *   - B: empty — initial state matches catalog defaults (no
 *        interaction; verifies the initial mount).
 *   - C: all-groups — visit every group's header.
 *   - D: clamp — covered by parity test; here we just verify the
 *        number input renders for the active group.
 *   - E: choice-reject — covered by parity test; same rationale as D.
 *
 * D and E exercise paths through the VM that the parity test
 * (`test_settings_parity_across_renderers.nim`) already drives
 * deterministically; the Espresso surface here verifies the
 * end-to-end real-device wire-up for the click-driven paths (A, B, C).
 *
 * Architectural choice: the Activity is launched with an Intent extra
 * `demo=settings`, which routes `MainActivity` through the
 * [SettingsAppBridge] JNI bindings instead of the EX-M6 default
 * [TaskAppBridge] path. The two demos coexist in the same APK; only
 * the dispatch differs.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAppScenarioTest {

    private fun settingsIntent(): Intent {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Intent(ctx, MainActivity::class.java)
            .putExtra("demo", "settings")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun scenarioA_basic_activateAppearanceAndToggleDarkMode() {
        ActivityScenario.launch<MainActivity>(settingsIntent()).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)

            // Sanity: the settings_app root is displayed.
            onView(withContentDescription("settings_root"))
                .check(matches(isDisplayed()))
            onView(withContentDescription("settings_sheet_list"))
                .check(matches(isDisplayed()))
            onView(withContentDescription("settings_bottom_sheet"))
                .check(matches(isDisplayed()))

            // The default-active group is "appearance" (catalog
            // default). Its header is in the sheet list AND its items
            // are mounted in the bottom-sheet pane.
            onView(withContentDescription("settings_sheet_row_appearance"))
                .check(matches(isDisplayed()))

            // Tap appearance's header (idempotent on the
            // already-active group) to exercise the click path.
            onView(withContentDescription("settings_sheet_header_appearance"))
                .perform(click())

            // The bottom-sheet pane now carries appearance's items. The
            // "Dark mode" toggle is identified by its label slug.
            onView(withContentDescription("settings_toggle_dark_mode"))
                .check(matches(isDisplayed()))
            onView(withContentDescription("settings_toggle_dark_mode"))
                .perform(click())

            // After the click + rebuild, the toggle's container is
            // still displayed in the bottom-sheet pane.
            onView(withContentDescription("settings_toggle_dark_mode"))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun scenarioB_empty_initialStateRendersCatalogDefaults() {
        ActivityScenario.launch<MainActivity>(settingsIntent()).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            // No interaction — assert the initial render mounted the
            // expected default-active group with all three of its
            // items.
            onView(withContentDescription("settings_root"))
                .check(matches(isDisplayed()))
            onView(withContentDescription("settings_toggle_dark_mode"))
                .check(matches(isDisplayed()))
            onView(withContentDescription("settings_number_font_size"))
                .check(matches(isDisplayed()))
            onView(withContentDescription("settings_choice_theme"))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun scenarioC_allGroups_eachHeaderIsClickable() {
        ActivityScenario.launch<MainActivity>(settingsIntent()).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)

            // Click through every group header in catalog order. After
            // each click the bottom-sheet pane should still be visible
            // and the freshly-activated group's items should be
            // mounted. The catalog's group ids are
            // ("appearance", "editor", "notifications").
            for (groupId in listOf("appearance", "editor", "notifications")) {
                onView(withContentDescription("settings_sheet_header_$groupId"))
                    .perform(click())
                onView(withContentDescription("settings_bottom_sheet"))
                    .check(matches(isDisplayed()))
                onView(withContentDescription("settings_sheet_row_$groupId"))
                    .check(matches(isDisplayed()))
            }
        }
    }

    @Test
    fun scenarioD_clamp_numberInputRendersForActiveGroup() {
        // Espresso-level verification: the number input host is
        // present + visible. The clamp arithmetic (font_size=5 →
        // clamps to 10) is exercised end-to-end by the parity test
        // through the VM; here we verify the surface that the parity
        // test's VM-level write would normally drive is live in the
        // bottom-sheet pane on the real device.
        ActivityScenario.launch<MainActivity>(settingsIntent()).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            onView(withContentDescription("settings_sheet_header_appearance"))
                .perform(click())
            onView(withContentDescription("settings_number_font_size"))
                .check(matches(isDisplayed()))
            onView(withContentDescription("settings_number_input_font_size"))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun scenarioE_choiceReject_choiceSelectRendersForActiveGroup() {
        // Espresso-level verification: the choice select is present +
        // visible. The choice-rejection logic (vm.setChoice with
        // invalid option → VM unchanged) is exercised through the VM
        // path in the parity test.
        ActivityScenario.launch<MainActivity>(settingsIntent()).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            onView(withContentDescription("settings_sheet_header_appearance"))
                .perform(click())
            onView(withContentDescription("settings_choice_theme"))
                .check(matches(isDisplayed()))
            onView(withContentDescription("settings_choice_select_theme"))
                .check(matches(isDisplayed()))
        }
    }
}
