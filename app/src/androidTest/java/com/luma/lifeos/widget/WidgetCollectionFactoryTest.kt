package com.luma.lifeos.widget

import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.domain.widget.WidgetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetCollectionFactoryTest {
    @Test fun adapterIdentityIncludesProviderAndWidgetInstance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = WidgetCollectionService.adapterIntent(context, 71, WidgetKind.HABITS)
        val second = WidgetCollectionService.adapterIntent(context, 72, WidgetKind.HABITS)
        val otherKind = WidgetCollectionService.adapterIntent(context, 71, WidgetKind.TASKS)

        assertEquals(71 to WidgetKind.HABITS, WidgetCollectionService.parseIdentity(first))
        assertNotEquals(first.data, second.data)
        assertNotEquals(first.data, otherKind.data)
        assertEquals(2, WidgetCollectionFactory(context, first).getViewTypeCount())
        assertEquals(1, WidgetCollectionFactory(context, otherKind).getViewTypeCount())
        assertEquals(true, WidgetCollectionFactory(context, first).hasStableIds())
    }

    @Test fun staleWidgetIdentityCannotExposeRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = WidgetCollectionService.adapterIntent(context, 73, WidgetKind.TASKS)
        val factory = WidgetCollectionFactory(context, intent)

        factory.onCreate()

        assertEquals(0, factory.getCount())
        assertNull(factory.getViewAt(0))
        assertEquals(1, factory.getViewTypeCount())
        factory.onDestroy()
    }
}
