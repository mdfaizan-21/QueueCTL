package com.queuectl;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import com.queuectl.cli.QueueCtlCommand;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test to verify project scaffolding compiles and the CLI framework initializes.
 */
class QueueCtlAppTest {

    @Test
    void testHelpReturnsZeroExitCode() {
        int exitCode = new CommandLine(new QueueCtlCommand()).execute("--help");
        assertEquals(0, exitCode, "Help command should return exit code 0");
    }

    @Test
    void testVersionReturnsZeroExitCode() {
        int exitCode = new CommandLine(new QueueCtlCommand()).execute("--version");
        assertEquals(0, exitCode, "Version command should return exit code 0");
    }

    @Test
    void testNoArgsReturnsZeroExitCode() {
        int exitCode = new CommandLine(new QueueCtlCommand()).execute();
        assertEquals(0, exitCode, "No-args invocation should return exit code 0");
    }
}
