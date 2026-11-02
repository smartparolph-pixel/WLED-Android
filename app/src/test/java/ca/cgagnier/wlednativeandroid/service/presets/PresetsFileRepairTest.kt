package ca.cgagnier.wlednativeandroid.service.presets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PresetsFileRepairTest {
    private val good = """{"0":{},"1":{"n":"Twinklefox","on":true,"seg":{"fx":80,"fxdef":true}},""" +
        """"6":{"n":"✨ Twinkle Tonight","ql":"✨","on":true,"playlist":{"ps":[1],"dur":[600],"transition":[10],""" +
        """"repeat":0,"end":0,"r":0}}}"""

    @Test
    fun validFileHasNoProblemAndIsKeptAsIs() {
        assertNull(PresetsFileRepair.problem(good))
        val result = PresetsFileRepair.repair(good)
        assertEquals(listOf("0", "1", "6"), result.presets.keys.toList())
        assertEquals(2, result.kept)
        assertEquals(0, result.dropped)
    }

    @Test
    fun leftoverTextAfterTheLastEntryIsDropped() {
        // A shorter write over a longer entry at the end of the file leaves its tail behind.
        val broken = good + """ard":true}}}"""
        assertNotNull(PresetsFileRepair.problem(broken))
        val result = PresetsFileRepair.repair(broken)
        assertEquals(listOf("0", "1", "6"), result.presets.keys.toList())
        assertNull(PresetsFileRepair.problem(result.toJson()))
    }

    @Test
    fun cutOffEntryInTheMiddleIsDroppedAndTheRestKept() {
        val broken = """{"0":{},"1":{"n":"Twinklefox","seg":{"fx":80,"fxd ,"2":{"n":"Fairy","seg":{"fx":49}},""" +
            """"3":{"n":"Glitter","seg":{"fx":87}}}"""
        val result = PresetsFileRepair.repair(broken)
        assertEquals(listOf("0", "2", "3"), result.presets.keys.toList())
        assertEquals(1, result.dropped)
        assertNull(PresetsFileRepair.problem(result.toJson()))
    }

    @Test
    fun repeatedIdKeepsTheFirstLikeTheFirmware() {
        val broken = """{"0":{},"5":{"n":"First"},"5":{"n":"Second"}}"""
        assertNotNull(PresetsFileRepair.problem(broken)) // Moshi rejects repeated keys
        val result = PresetsFileRepair.repair(broken)
        assertEquals(mapOf("n" to "First"), result.presets["5"])
        assertEquals(1, result.dropped)
    }

    @Test
    fun bracesInsideNamesDoNotConfuseTheScan() {
        val text = """{"0":{},"7":{"n":"My {fun} \"preset\"","seg":{"fx":9}}}"""
        val result = PresetsFileRepair.repair(text)
        assertEquals("My {fun} \"preset\"", (result.presets["7"] as Map<*, *>)["n"])
    }

    @Test
    fun emptyFileKeepsOnlyTheEmptyEntry() {
        val result = PresetsFileRepair.repair("")
        assertEquals("""{"0":{}}""", result.toJson())
        assertEquals(0, result.kept)
    }
}
