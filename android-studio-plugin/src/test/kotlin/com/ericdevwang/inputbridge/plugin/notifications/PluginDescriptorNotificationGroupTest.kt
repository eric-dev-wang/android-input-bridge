package com.ericdevwang.inputbridge.plugin.notifications

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node

class PluginDescriptorNotificationGroupTest {
    @Test
    fun notificationGroupIsRegisteredAsAnIntellijExtension() {
        val descriptor = requireNotNull(javaClass.classLoader.getResource("META-INF/plugin.xml"))
        val document = descriptor.openStream().use { stream ->
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(stream)
        }
        val extensions = document.getElementsByTagName("extensions").item(0) as Element
        val notificationGroups = extensions.getElementsByTagName("notificationGroup")
        val root = document.documentElement
        val topLevelNotificationGroups = (0 until root.childNodes.length).count { index ->
            val child = root.childNodes.item(index)
            child.nodeType == Node.ELEMENT_NODE && (child as Element).tagName == "notificationGroup"
        }

        assertEquals("com.intellij", extensions.getAttribute("defaultExtensionNs"))
        assertEquals(1, notificationGroups.length)
        assertSame(extensions, notificationGroups.item(0).parentNode)
        assertEquals(
            "Input Bridge",
            (notificationGroups.item(0) as Element).getAttribute("id"),
        )
        assertEquals("BALLOON", (notificationGroups.item(0) as Element).getAttribute("displayType"))
        assertEquals(0, topLevelNotificationGroups)
    }
}
