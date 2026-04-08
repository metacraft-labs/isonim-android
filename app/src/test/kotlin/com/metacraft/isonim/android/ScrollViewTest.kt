package com.metacraft.isonim.android

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class ScrollViewTest {
    @Before
    fun setUp() {
        NimBridge.reset()
    }

    @Test
    fun scrollViewCreation() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("ScrollView", ctx)
        val view = NimBridge.getView(handle)
        assertNotNull(view)
        assertTrue("Should be a ScrollView", view is ScrollView)
    }

    @Test
    fun scrollViewAcceptsChildViews() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("ScrollView", ctx)
        val sv = NimBridge.getView(handle) as ScrollView

        // Add a child view
        val child = FrameLayout(ctx)
        child.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 10000
        )
        sv.addView(child)

        assertEquals("ScrollView should have one child", 1, sv.childCount)
        assertTrue("Child should be a FrameLayout", sv.getChildAt(0) is FrameLayout)
    }

    @Test
    fun recyclerViewCreation() {
        val ctx = RuntimeEnvironment.getApplication()
        val handle = NimBridge.createView("RecyclerView", ctx)
        val view = NimBridge.getView(handle)
        assertNotNull(view)
        assertTrue("Should be a RecyclerView", view is RecyclerView)
        assertNotNull("Should have a LayoutManager", (view as RecyclerView).layoutManager)
    }

    @Test
    fun recyclerViewAdapterRecyclesCells() {
        val ctx = RuntimeEnvironment.getApplication()
        val rv = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
        }

        val adapter = NimBridge.NimRecyclerAdapter(
            itemCount = 100,
            createHolder = { TextView(ctx) },
            bindHolder = { view, pos -> (view as TextView).text = "Item $pos" }
        )
        rv.adapter = adapter

        // Lay out the RecyclerView with bounded size so recycling happens
        val widthSpec = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        rv.measure(widthSpec, heightSpec)
        rv.layout(0, 0, 500, 400)

        // Scroll to position 50 to force more creates
        rv.scrollToPosition(50)
        rv.measure(widthSpec, heightSpec)
        rv.layout(0, 0, 500, 400)

        assertTrue(
            "Should create fewer views than total items (recycling). Created: ${adapter.createCount}",
            adapter.createCount < 100
        )
    }

    @Test
    fun recyclerViewAdapterItemCountUpdate() {
        val ctx = RuntimeEnvironment.getApplication()
        val adapter = NimBridge.NimRecyclerAdapter(
            itemCount = 10,
            createHolder = { TextView(ctx) },
            bindHolder = { view, pos -> (view as TextView).text = "Item $pos" }
        )
        assertEquals(10, adapter.itemCount)

        adapter.items = 20
        assertEquals(20, adapter.itemCount)
    }

    @Test
    fun recyclerViewScrollToPosition() {
        val ctx = RuntimeEnvironment.getApplication()
        val rv = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
        }

        val boundPositions = mutableListOf<Int>()
        val adapter = NimBridge.NimRecyclerAdapter(
            itemCount = 100,
            createHolder = {
                TextView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 50
                    )
                }
            },
            bindHolder = { view, pos ->
                boundPositions.add(pos)
                (view as TextView).text = "Item $pos"
            }
        )
        rv.adapter = adapter

        // Layout and scroll
        val widthSpec = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
        rv.measure(widthSpec, heightSpec)
        rv.layout(0, 0, 500, 200)

        // scrollToPosition requests layout for that position
        rv.scrollToPosition(50)
        rv.measure(widthSpec, heightSpec)
        rv.layout(0, 0, 500, 200)

        // After scrolling to 50, position 50 should have been bound
        assertTrue(
            "Position 50 should have been bound after scrollToPosition(50)",
            boundPositions.contains(50)
        )
    }
}
