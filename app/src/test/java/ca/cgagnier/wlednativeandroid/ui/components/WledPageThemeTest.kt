package ca.cgagnier.wlednativeandroid.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WledPageThemeTest {
    @Test
    fun `sets WLED's own theme setting on the phone, light or dark`() {
        val light = WledPageTheme.script(light = true)
        assertTrue(light.contains("localStorage.setItem('wledUiCfg'"))
        assertTrue(light.contains("c.theme.base = 'light'"))
        assertTrue(light.contains("window.applyCfg()"))
        val dark = WledPageTheme.script(light = false)
        assertTrue(dark.contains("c.theme.base = 'dark'"))
        assertFalse(dark.contains("'light'"))
    }
}
