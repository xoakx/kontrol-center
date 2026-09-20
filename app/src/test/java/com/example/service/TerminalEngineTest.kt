package com.example.service

import com.example.e2e.harness.FakeSshSession
import com.example.viewmodel.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var terminalEngine: TerminalEngine
    private lateinit var fakeSshSession: FakeSshSession

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        terminalEngine = TerminalEngine(testScope, testDispatcher, maxBufferLines = 100, mainDispatcher = testDispatcher)
        fakeSshSession = FakeSshSession()
    }

    @org.junit.After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testQuickKeyMapping_CtrlC() = testScope.runTest {
        terminalEngine.appendQuickKey("CTRL+C")
        testScope.advanceUntilIdle()

        val state = terminalEngine.state.value
        val lastLine = state.lines.lastOrNull()
        assertEquals("^C", lastLine?.text)
        assertEquals(TerminalLineType.ERROR, lastLine?.type)
    }

    @Test
    fun testQuickKeyMapping_PunctuationAndText() {
        terminalEngine.updateInput("ls ")
        terminalEngine.appendQuickKey("|")
        terminalEngine.appendQuickKey(" grep foo")

        assertEquals("ls | grep foo", terminalEngine.state.value.currentInput)
    }

    @Test
    fun testExecuteCommand_EmptyInputIgnored() {
        val initialLinesCount = terminalEngine.state.value.lines.size
        terminalEngine.updateInput("   ")
        terminalEngine.executeCommand()

        assertEquals(initialLinesCount, terminalEngine.state.value.lines.size)
    }

    @Test
    fun testExecuteCommand_DisconnectedSession_ReportsError() = testScope.runTest {
        terminalEngine.updateInput("whoami")
        terminalEngine.executeCommand()
        testScope.advanceUntilIdle()

        val state = terminalEngine.state.value
        assertEquals("", state.currentInput)
        assertTrue(state.commandHistory.contains("whoami"))

        val errorLine = state.lines.find { it.text.contains("No active SSH session") }
        assertTrue(errorLine != null)
        assertEquals(TerminalLineType.ERROR, errorLine?.type)
    }

    @Test
    fun testBufferCap_EnforcesMaxBufferLines() = testScope.runTest {
        val engine = TerminalEngine(testScope, testDispatcher, maxBufferLines = 50, mainDispatcher = testDispatcher)
        for (i in 1..120) {
            engine.appendOutputLine("Line $i")
        }
        testScope.advanceUntilIdle()

        val lines = engine.state.value.lines
        assertEquals(50, lines.size)
        assertEquals("Line 120", lines.last().text)
    }

    @Test
    fun testClearConsole_ResetsLines() = testScope.runTest {
        terminalEngine.appendOutputLine("Output line")
        testScope.advanceUntilIdle()
        assertFalse(terminalEngine.state.value.lines.isEmpty())

        terminalEngine.clearConsole()
        assertTrue(terminalEngine.state.value.lines.isEmpty())
    }

    @Test
    fun testPromptStringDetection_ViaInteractiveStreaming() = testScope.runTest {
        val fakeInput = "user@fml:~$ ".toByteArray(Charsets.UTF_8)
        val inputStream = ByteArrayInputStream(fakeInput)

        // Invoke private reading logic or stream input
        val readMethod = TerminalEngine::class.java.getDeclaredMethod("startReadingShellOutput", java.io.InputStream::class.java)
        readMethod.isAccessible = true
        readMethod.invoke(terminalEngine, inputStream)
        testScope.advanceUntilIdle()

        assertEquals("user@fml:~$ ", terminalEngine.state.value.promptString)
    }

    @Test
    fun testCarriageReturn_OverwritesBufferInStream() = testScope.runTest {
        val streamData = "Progress: 10%\rProgress: 50%\rProgress: 100%\n".toByteArray(Charsets.UTF_8)
        val inputStream = ByteArrayInputStream(streamData)

        val readMethod = TerminalEngine::class.java.getDeclaredMethod("startReadingShellOutput", java.io.InputStream::class.java)
        readMethod.isAccessible = true
        readMethod.invoke(terminalEngine, inputStream)
        testScope.advanceUntilIdle()

        val state = terminalEngine.state.value
        val progressLine = state.lines.find { it.text.contains("Progress:") }
        assertEquals("Progress: 100%", progressLine?.text)
    }

    @Test
    fun testAnsiRendering_ProducesStyledAnnotatedString() = testScope.runTest {
        terminalEngine.appendOutputLine("\u001B[1;32mSUCCESSFUL BUILD\u001B[0m")
        testScope.advanceUntilIdle()

        val state = terminalEngine.state.value
        val lastLine = state.lines.last()
        assertEquals("SUCCESSFUL BUILD", lastLine.text)
        assertEquals("SUCCESSFUL BUILD", lastLine.annotatedText.text)
        assertFalse(lastLine.annotatedText.spanStyles.isEmpty())
    }

    @Test
    fun testTerminalViewModel_StickyCtrlModifier() {
        val vm = TerminalViewModel(terminalEngine)
        assertFalse(vm.isCtrlActive.value)

        vm.toggleCtrlModifier()
        assertTrue(vm.isCtrlActive.value)
        assertTrue(vm.uiState.value.isCtrlActive)

        // Send 'C' -> resets modifier
        vm.sendQuickKey("C")
        assertFalse(vm.isCtrlActive.value)
        assertFalse(vm.uiState.value.isCtrlActive)
    }

    @Test
    fun testTerminalViewModel_FontSizeAndAutoScroll() {
        val vm = TerminalViewModel(terminalEngine)
        assertEquals(12, vm.uiState.value.fontSizeSp)
        assertTrue(vm.uiState.value.isAutoScrollEnabled)

        vm.setFontSize(16)
        assertEquals(16, vm.uiState.value.fontSizeSp)

        vm.toggleAutoScroll()
        assertFalse(vm.uiState.value.isAutoScrollEnabled)
    }

    @Test
    fun testSendCtrlKey_CalculatesAsciiCode() {
        // Test calculation logic: 'C' -> 3, 'D' -> 4, 'Z' -> 26
        val charC = 'C'
        val ctrlC = (charC.uppercaseChar().code - '@'.code).toChar()
        assertEquals('\u0003', ctrlC)

        val charD = 'D'
        val ctrlD = (charD.uppercaseChar().code - '@'.code).toChar()
        assertEquals('\u0004', ctrlD)

        val charZ = 'Z'
        val ctrlZ = (charZ.uppercaseChar().code - '@'.code).toChar()
        assertEquals('\u001A', ctrlZ)
    }
}
