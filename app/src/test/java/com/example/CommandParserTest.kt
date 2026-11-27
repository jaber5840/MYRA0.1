package com.myra.assistant

import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.SmartToolsHelper
import com.myra.assistant.model.AppCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun `strips wake word and parses sequential multi-commands`() {
        val commands = CommandParser.parseAll("Hey MYRA, turn on flashlight and open camera")
        assertEquals(2, commands.size)
        assertEquals(AppCommand.TYPE_FLASHLIGHT_ON, commands[0].type)
        assertEquals(AppCommand.TYPE_OPEN_CAMERA_BACK, commands[1].type)
    }

    @Test
    fun `parses WhatsApp message command with recipient and body`() {
        val commands = CommandParser.parseAll("MYRA, send a WhatsApp message to Rahim saying I am coming")
        assertEquals(1, commands.size)
        assertEquals(AppCommand.TYPE_WHATSAPP_MSG, commands[0].type)
        assertEquals("Rahim", commands[0].params["name"])
        assertEquals("I am coming", commands[0].params["message"])
    }

    @Test
    fun `parses confirm and cancel voice actions`() {
        assertEquals(AppCommand.TYPE_CONFIRM_ACTION, CommandParser.parse("Yes")?.type)
        assertEquals(AppCommand.TYPE_CANCEL_ACTION, CommandParser.parse("Cancel")?.type)
    }

    @Test
    fun `parses weather alarm and battery queries`() {
        assertEquals(AppCommand.TYPE_WEATHER, CommandParser.parse("What's the weather in Dhaka")?.type)
        assertEquals(AppCommand.TYPE_BATTERY_INFO, CommandParser.parse("Check battery percentage")?.type)
        val alarm = CommandParser.parse("Set an alarm for 6:30 AM")
        assertNotNull(alarm)
        assertEquals(AppCommand.TYPE_SET_ALARM, alarm?.type)
        assertEquals("6", alarm?.params?.get("hour"))
        assertEquals("30", alarm?.params?.get("minute"))
    }

    @Test
    fun `evaluates math expressions safely`() {
        val result = SmartToolsHelper.evaluateMath("(15 + 5) * 3 / 2")
        assertTrue(result.contains("30"))
    }
}
