package com.bazaarvoice.ostrich.perftest.utils;

import com.google.common.base.Strings;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.File;
import java.io.PrintStream;

import static com.bazaarvoice.ostrich.pool.ServiceCachingPolicy.ExhaustionAction;

/**
 * This class parses and holds onto the parsed variable.
 * It also handles bad arguments appropriately, and prints message to help rectify them.
 */
@SuppressWarnings ("deprecation")
public class Arguments {

    private int _threadSize = 100;
    private int _workSize = 1024 * 5;
    private long _runTimeSecond = Long.MAX_VALUE;
    private int _maxInstance = 10;
    private int _idleTimeSecond = 10;
    private boolean _runSingletonMode = false;
    private int _evictionTTL = 5;
    private ExhaustionAction _exhaustionAction = ExhaustionAction.WAIT;
    private int _reportingIntervalSeconds = 1;
    private PrintStream _output = System.out;
    private boolean _printStats = false;
    private int _chaosWorkers = 2;
    private int _chaosInterval = 15;


    public Arguments(String[] args) {
        parseArgs(args);
    }

    public int getThreadSize() {
        return _threadSize;
    }

    public int getWorkSize() {
        return _workSize;
    }

    public long getRunTimeSecond() {
        return _runTimeSecond;
    }

    public int getMaxInstance() {
        return _maxInstance;
    }

    public int getIdleTimeSecond() {
        return _idleTimeSecond;
    }

    public ExhaustionAction getExhaustionAction() {
        return _exhaustionAction;
    }

    public PrintStream getOutput() {
        return _output;
    }

    public boolean doPrintStats() {
        return _printStats;
    }

    public int getReportingIntervalSeconds() {
        return _reportingIntervalSeconds;
    }

    public boolean isRunSingletonMode() {
        return _runSingletonMode;
    }

    public int getEvictionTTL() {
        return _evictionTTL;
    }

    public int getChaosWorkers() {
        return _chaosWorkers;
    }

    public int getChaosInterval() {
        return _chaosInterval;
    }

    private void parseArgs(String[] args) {

        Options options = new Options();

        options.addOption("h", "help", false, "Show this help message!");

        options.addOption("t", "thread-size", true, "# of workers threads to run, default is 100");
        options.addOption("w", "work-size", true, "length of the string to generate randomly and crunch hash, default is 1024 X 5 (5kb)");
        options.addOption("r", "run-time", true, "seconds to run before it kills worker running threads, default is 9223372036854775807 (Long.MAX_VALUE)");

        options.addOption("m", "max-instances", true, "Max instances per end point in service cache, default is 10");
        options.addOption("i", "idle-time", true, "Idle time before service cache should take evict action, default is 10");
        options.addOption("e", "exhaust-action", true, "Exhaust action when cache is exhausted, acceptable values are WAIT|FAIL|GROW, default is WAIT");

        options.addOption("g", "singleton-mode", false, "Run with singleton policy mode, default is false");
        options.addOption("n", "eviction-ttl", true, "Eviction TTL for bad clients in singleton policy mode, default is 5 second");

        options.addOption("c", "chaos-count", true, "Number of chaos workers to use, default is 2");
        options.addOption("l", "chaos-interval", true, "time (in seconds) to wait between chaos, default is 15");

        options.addOption("o", "output-file", true, "Output file to use instead of STDOUT");
        options.addOption("v", "report-every", true, "Reports the running statistics every # seconds");
        options.addOption("s", "statistics", false, "Output current running stats on STDOUT, ignored if --output-file is not provided");

        String opt = null;
        String longOpt = null;
        String value = null;

        try {
            CommandLineParser commandLineParser = new BasicParser();
            CommandLine commandLine = commandLineParser.parse(options, args);

            if (commandLine.hasOption("h")) {
                help(options);
            }

            for (Option option : commandLine.getOptions()) {

                opt = option.getOpt();
                longOpt = option.getLongOpt();
                value = option.getValue();

                switch (opt) {
                    case "t":
                        _threadSize = Integer.parseInt(value);
                        break;
                    case "w":
                        _workSize = Integer.parseInt(value);
                        break;
                    case "r":
                        _runTimeSecond = Long.parseLong(value);
                        break;
                    case "m":
                        _maxInstance = Integer.parseInt(value);
                        break;
                    case "i":
                        _idleTimeSecond = Integer.parseInt(value);
                        break;
                    case "e":
                        _exhaustionAction = ExhaustionAction.valueOf(value);
                        break;
                    case "o":
                        _output = createPrintStream(value);
                        break;
                    case "g":
                        _runSingletonMode = true;
                        break;
                    case "n":
                        _evictionTTL = Integer.parseInt(value);
                        break;
                    case "s":
                        _printStats = true;
                        break;
                    case "v":
                        _reportingIntervalSeconds = Integer.parseInt(value);
                        break;
                    case "c":
                        _chaosWorkers = Integer.parseInt(value);
                        break;
                    case "l":
                        _chaosInterval = Integer.parseInt(value);
                        break;
                }
            }
            if (_output == System.out && _printStats) {
                throw new Exception("Cannot print both report log and statistics to STDOUT at the same time");
            }
        } catch (IllegalArgumentException ex) {
            printError(opt, longOpt, value);
            help(options);
        } catch (Exception ex) {
            printError(ex, opt, longOpt, value);
            help(options);
        }
    }

    private void help(Options options) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("Ostrich Performance Test Suite", options);
        System.exit(0);
    }

    private void printError(String opt, String longOpt, String value) {
        if (!Strings.isNullOrEmpty(opt) && !Strings.isNullOrEmpty(value)) {
            System.err.println(String.format("\"%s\" is not valid value for -%s / --%s", value, opt, longOpt));
        }
    }

    private void printError(Exception ex, String opt, String longOpt, String value) {
        System.err.println(ex.getMessage());
        printError(opt, longOpt, value);
    }

    private PrintStream createPrintStream(String filePath)
            throws Exception {
        File file = new File(filePath);
        if (!file.createNewFile()) {
            throw new Exception("Cannot create file: " + filePath);
        }
        return new PrintStream(file);
    }
}
