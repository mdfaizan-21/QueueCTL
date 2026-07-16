package com.queuectl.cli;

import com.queuectl.config.AppContext;
import com.queuectl.domain.Config;
import com.queuectl.exception.ConfigException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;

/**
 * CLI command: queuectl config set|get|list
 */
@Command(name = "config", description = "Manage runtime configuration.",
         subcommands = {
             ConfigCommand.SetCommand.class,
             ConfigCommand.GetCommand.class,
             ConfigCommand.ListConfigCommand.class
         })
public class ConfigCommand implements Runnable {

    @Override
    public void run() {
        // Show help when no subcommand given
        new picocli.CommandLine(this).usage(System.out);
    }

    @Command(name = "set", description = "Set a configuration value.")
    static class SetCommand implements Runnable {

        @Parameters(index = "0", description = "Config key (e.g., max-retries, backoff-base)")
        private String key;

        @Parameters(index = "1", description = "Config value")
        private String value;

        @Override
        public void run() {
            try {
                AppContext ctx = AppContext.initialize();
                ctx.configService().set(key, value);
                System.out.println("✓ Config set: " + key + " = " + value);
            } catch (ConfigException e) {
                System.err.println("✗ " + e.getMessage());
            } catch (Exception e) {
                System.err.println("✗ Failed to set config: " + e.getMessage());
            }
        }
    }

    @Command(name = "get", description = "Get a configuration value.")
    static class GetCommand implements Runnable {

        @Parameters(index = "0", description = "Config key")
        private String key;

        @Override
        public void run() {
            try {
                AppContext ctx = AppContext.initialize();
                String value = ctx.configService().get(key);
                System.out.println(key + " = " + value);
            } catch (ConfigException e) {
                System.err.println("✗ " + e.getMessage());
            } catch (Exception e) {
                System.err.println("✗ Failed to get config: " + e.getMessage());
            }
        }
    }

    @Command(name = "list", description = "List all configuration values.")
    static class ListConfigCommand implements Runnable {

        @Override
        public void run() {
            try {
                AppContext ctx = AppContext.initialize();
                List<Config> configs = ctx.configService().listAll();

                System.out.printf("%-20s %s%n", "KEY", "VALUE");
                System.out.println("-".repeat(40));
                for (Config config : configs) {
                    System.out.printf("%-20s %s%n", config.getKey(), config.getValue());
                }
            } catch (Exception e) {
                System.err.println("✗ Failed to list config: " + e.getMessage());
            }
        }
    }
}
