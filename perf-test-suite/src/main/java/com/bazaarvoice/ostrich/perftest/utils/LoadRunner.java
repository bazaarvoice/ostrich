package com.bazaarvoice.ostrich.perftest.utils;

import com.bazaarvoice.ostrich.ThreadSafeServiceFactory;
import com.bazaarvoice.ostrich.perftest.core.Service;
import com.bazaarvoice.ostrich.perftest.core.SimpleServiceFactory;
import com.bazaarvoice.ostrich.pool.ServiceRunner;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;

import java.io.PrintStream;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class creates the service runner and runs the requested load as parsed via Arguments
 * This also prints/writes the report log and/or the statistics as requested in the Arguments
 */
public class LoadRunner {

    private final PrintStream _out;
    private final boolean _doPrintStats;
    private final long _totalRuntime;
    private final List<Thread> _workers;
    private final long _startTime;
    private final int _reportingIntervalSeconds;
    private final Arguments _arguments;

    private final Meter _serviceCreated;
    private final Meter _serviceDestroyed;
    private final Meter _serviceCalled;
    private final Meter _cacheMissed;
    private final Meter _serviceFailed;
    private final Meter _chaosCreated;
    private final Meter _stableCreated;
    private final Timer _checkoutTimer;
    private final Timer _checkinTimer;
    private final Timer _serviceTimer;
    private final Timer _totalTimer;
    private final Timer _evictionTimer;
    private final Timer _registerTimer;
    private final Timer _loadTimer;

    private long _counter = 0;

    public LoadRunner(Arguments arguments) {

        MetricRegistry metricRegistry = new MetricRegistry();
        ThreadSafeServiceFactory<Service<String, String>> serviceFactory = SimpleServiceFactory.newInstance(metricRegistry);
        ServiceRunner serviceRunner = new ServiceRunner(serviceFactory, metricRegistry, arguments);
        ChaosRunner chaosRunner = new ChaosRunner(serviceRunner.getServiceCache(), arguments, metricRegistry);

        _arguments = arguments;
        _out = arguments.getOutput();
        _doPrintStats = arguments.doPrintStats();
        _totalRuntime = arguments.getRunTimeSecond();
        _reportingIntervalSeconds = arguments.getReportingIntervalSeconds();

        _workers = Lists.newArrayList();
        _workers.addAll(serviceRunner.generateWorkers());
        _workers.addAll(chaosRunner.generateChaosWorkers());

        for (Thread thread : _workers) {
            thread.start();
        }

        _startTime = currentTimeSeconds();

        _serviceCreated = metricRegistry.meter("com.bazaarvoice.ostrich.perftest.core.SimpleServiceFactory.ServiceFactory.Created");
        _serviceDestroyed = metricRegistry.meter("com.bazaarvoice.ostrich.perftest.core.SimpleServiceFactory.ServiceFactory.Destroyed");
        _serviceCalled = metricRegistry.meter("com.bazaarvoice.ostrich.pool.ServiceRunner.ServiceRunner.Executed");
        _cacheMissed = metricRegistry.meter("com.bazaarvoice.ostrich.pool.ServiceRunner.ServiceRunner.Cache-Miss");
        _serviceFailed = metricRegistry.meter("com.bazaarvoice.ostrich.pool.ServiceRunner.ServiceRunner.Failure");
        _chaosCreated = metricRegistry.meter("com.bazaarvoice.ostrich.perftest.utils.ChaosRunner.ChaosRunner.Chaos");
        _stableCreated = metricRegistry.meter("com.bazaarvoice.ostrich.perftest.utils.ChaosRunner.ChaosRunner.Stable");
        _checkoutTimer = metricRegistry.timer("com.bazaarvoice.ostrich.pool.ServiceRunner.ServiceRunner.Checkout");
        _checkinTimer = metricRegistry.timer("com.bazaarvoice.ostrich.pool.ServiceRunner.ServiceRunner.Checkin");
        _serviceTimer = metricRegistry.timer("com.bazaarvoice.ostrich.perftest.core.SimpleServiceFactory.ServiceFactory.Timer");
        _totalTimer = metricRegistry.timer("com.bazaarvoice.ostrich.pool.ServiceRunner.ServiceRunner.Total");
        if(arguments.isRunSingletonMode()) {
            _evictionTimer = metricRegistry.timer("com.bazaarvoice.ostrich.pool.MultiThreadedClientServiceCache.SimpleService.eviction-time");
            _registerTimer = metricRegistry.timer("com.bazaarvoice.ostrich.pool.MultiThreadedClientServiceCache.SimpleService.register-time");
            _loadTimer = metricRegistry.timer("dummy");
        }
        else {
            _loadTimer = metricRegistry.timer("com.bazaarvoice.ostrich.pool.SingleThreadedClientServiceCache.SimpleService.load-time");
            _evictionTimer = _registerTimer = metricRegistry.timer("dummy");
        }

    }

    public void printLog() {

        long currentRuntime = currentTimeSeconds() - _startTime;

        _out.print(String.format("%d,", _counter++));

        _out.print(String.format("%d,%.2f,%.2f,%.2f,%.2f,",
                _serviceCreated.getCount(), _serviceCreated.getMeanRate(), _serviceCreated.getOneMinuteRate(),
                _serviceCreated.getFiveMinuteRate(), _serviceCreated.getFifteenMinuteRate()));
        _out.print(String.format("%d,%.2f,%.2f,%.2f,%.2f,",
                _serviceDestroyed.getCount(), _serviceDestroyed.getMeanRate(), _serviceDestroyed.getOneMinuteRate(),
                _serviceDestroyed.getFiveMinuteRate(), _serviceDestroyed.getFifteenMinuteRate()));
        _out.print(String.format("%d,%.2f,%.2f,%.2f,%.2f,",
                _serviceCalled.getCount(), _serviceCalled.getMeanRate(), _serviceCalled.getOneMinuteRate(),
                _serviceCalled.getFiveMinuteRate(), _serviceCalled.getFifteenMinuteRate()));

        Snapshot checkoutTimerSnapshot = _checkoutTimer.getSnapshot();
        Snapshot checkinTimerSnapshot = _checkinTimer.getSnapshot();
        Snapshot serviceTimerSnapshot = _serviceTimer.getSnapshot();
        Snapshot totalTimerSnapshot = _totalTimer.getSnapshot();

        _out.print(String.format("%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,",
                _checkoutTimer.getCount(), nsToMs(checkoutTimerSnapshot.getMin()),
                nsToMs(checkoutTimerSnapshot.getMax()), nsToMs(checkoutTimerSnapshot.getMean()),
                _checkoutTimer.getOneMinuteRate(), _checkoutTimer.getFiveMinuteRate(), _checkoutTimer.getFifteenMinuteRate()));
        _out.print(String.format("%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,",
                _checkinTimer.getCount(), nsToMs(checkinTimerSnapshot.getMin()),
                nsToMs(checkinTimerSnapshot.getMax()), nsToMs(checkinTimerSnapshot.getMean()),
                _checkinTimer.getOneMinuteRate(), _checkinTimer.getFiveMinuteRate(), _checkinTimer.getFifteenMinuteRate()));
        _out.print(String.format("%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,",
                _serviceTimer.getCount(), nsToMs(serviceTimerSnapshot.getMin()),
                nsToMs(serviceTimerSnapshot.getMax()), nsToMs(serviceTimerSnapshot.getMean()),
                _serviceTimer.getOneMinuteRate(), _serviceTimer.getFiveMinuteRate(), _serviceTimer.getFifteenMinuteRate()));
        _out.print(String.format("%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,",
                _totalTimer.getCount(), nsToMs(totalTimerSnapshot.getMin()),
                nsToMs(totalTimerSnapshot.getMax()), nsToMs(totalTimerSnapshot.getMean()),
                _totalTimer.getOneMinuteRate(), _totalTimer.getFiveMinuteRate(), _totalTimer.getFifteenMinuteRate()));


        _out.println();
        _out.flush();

        if (_doPrintStats) {
            System.out.print("\u001b[2J");

            System.out.println(new Date());

            System.out.println(String.format("Running %d seconds of %s with threads: %d, work size: %d, idle time: %d, " +
                            "max instance: %d, exhaust action: %s, singleton-mode: %s, eviction-ttl: %d, chaos-worker: %d, chaos-interval: %d",
                    currentRuntime, _arguments.getRunTimeSecond(), _arguments.getThreadSize(), _arguments.getWorkSize(),
                    _arguments.getIdleTimeSecond(), _arguments.getMaxInstance(), _arguments.getExhaustionAction().name(),
                    _arguments.isRunSingletonMode(), _arguments.getEvictionTTL(),
                    _arguments.getChaosWorkers(), _arguments.getChaosInterval()));

            System.out.println(String.format("Called count: %d\tCache Miss: %d\tFailed Count: %d\tService Created: %d" +
                            "\tService Destroyed: %d\tChaos: %d\tStable: %d\tRegister: %d\tEvict: %d\tLoad: %d",
                    _serviceCalled.getCount(), _cacheMissed.getCount(), _serviceFailed.getCount(),
                    _serviceCreated.getCount(), _serviceDestroyed.getCount(),
                    _chaosCreated.getCount(), _stableCreated.getCount(),
                    _registerTimer.getCount(), _evictionTimer.getCount(), _loadTimer.getCount()));

            System.out.println();

            System.out.println(String.format("\tcreated / destroyed\t-- 1-min: %3.2f/s / %3.2f/s" +
                            "\t5-min: %3.2f/s / %3.2f/s  \t15-min: %3.2f/s / %3.2f/s\tmean: %3.2f/s / %3.2f/s",
                    _serviceCreated.getOneMinuteRate(), _serviceDestroyed.getOneMinuteRate(),
                    _serviceCreated.getFiveMinuteRate(), _serviceDestroyed.getFiveMinuteRate(),
                    _serviceCreated.getFifteenMinuteRate(), _serviceDestroyed.getFifteenMinuteRate(),
                    _serviceCreated.getMeanRate(), _serviceDestroyed.getMeanRate()));

            System.out.println(String.format("\tchaos / stable\t\t-- 1-min: %3.2f/s / %3.2f/s" +
                            "\t5-min: %3.2f/s / %3.2f/s  \t15-min: %3.2f/s / %3.2f/s\tmean: %3.2f/s / %3.2f/s",
                    _chaosCreated.getOneMinuteRate(), _stableCreated.getOneMinuteRate(),
                    _chaosCreated.getFiveMinuteRate(), _stableCreated.getFiveMinuteRate(),
                    _chaosCreated.getFifteenMinuteRate(), _stableCreated.getFifteenMinuteRate(),
                    _chaosCreated.getMeanRate(), _stableCreated.getMeanRate()));

            System.out.println(String.format("\texecuted / failure\t-- 1-min: %3.2f/s / %3.2f/s" +
                            "\t5-min: %3.2f/s / %3.2f/s  \t15-min: %3.2f/s / %3.2f/s\tmean: %3.2f/s / %3.2f/s",
                    _serviceCalled.getOneMinuteRate(), _serviceFailed.getOneMinuteRate(),
                    _serviceCalled.getFiveMinuteRate(), _serviceFailed.getFiveMinuteRate(),
                    _serviceCalled.getFifteenMinuteRate(), _serviceFailed.getFifteenMinuteRate(),
                    _serviceCalled.getMeanRate(), _serviceFailed.getMeanRate()));

            System.out.println(String.format("\tservice / total\t\t-- 1-min: %3.2f/s / %3.2f/s" +
                            "\t5-min: %3.2f/s / %3.2f/s  \t15-min: %3.2f/s / %3.2f/s\tmean: %3.2f/s / %3.2f/s",
                    _serviceTimer.getOneMinuteRate(), _totalTimer.getOneMinuteRate(),
                    _serviceTimer.getFiveMinuteRate(), _totalTimer.getFiveMinuteRate(),
                    _serviceTimer.getFifteenMinuteRate(), _totalTimer.getFifteenMinuteRate(),
                    _serviceTimer.getMeanRate(), _totalTimer.getMeanRate()));

            System.out.println(String.format("\tcheckout / checkin\t-- 1-min: %3.2f/s / %3.2f/s" +
                            "\t5-min: %3.2f/s / %3.2f/s  \t15-min: %3.2f/s / %3.2f/s\tmean: %3.2f/s / %3.2f/s",
                    _checkoutTimer.getOneMinuteRate(), _checkinTimer.getOneMinuteRate(),
                    _checkoutTimer.getFiveMinuteRate(), _checkinTimer.getFiveMinuteRate(),
                    _checkoutTimer.getFifteenMinuteRate(), _checkinTimer.getFifteenMinuteRate(),
                    _checkoutTimer.getMeanRate(), _checkinTimer.getMeanRate()));

            System.out.println();

            System.out.println(String.format("\tService\t -- mean: %3.2fms\tmin: %3.2fms\tmax: %3.2fms\t75th: %3.2fms" +
                            "\t95th: %3.2fms\t98th: %3.2fms\t99th: %3.2fms\t999th: %3.2fms",
                    nsToMs(serviceTimerSnapshot.getMean()),
                    nsToMs(serviceTimerSnapshot.getMin()),
                    nsToMs(serviceTimerSnapshot.getMax()),
                    nsToMs(serviceTimerSnapshot.get75thPercentile()),
                    nsToMs(serviceTimerSnapshot.get95thPercentile()),
                    nsToMs(serviceTimerSnapshot.get98thPercentile()),
                    nsToMs(serviceTimerSnapshot.get99thPercentile()),
                    nsToMs(serviceTimerSnapshot.get999thPercentile())
            ));

            System.out.println(String.format("\tCheckout -- mean: %3.2fms\tmin: %3.2fms\tmax: %3.2fms\t75th: %3.2fms" +
                            "\t95th: %3.2fms\t98th: %3.2fms\t99th: %3.2fms\t999th: %3.2fms",
                    nsToMs(checkoutTimerSnapshot.getMean()),
                    nsToMs(checkoutTimerSnapshot.getMin()),
                    nsToMs(checkoutTimerSnapshot.getMax()),
                    nsToMs(checkoutTimerSnapshot.get75thPercentile()),
                    nsToMs(checkoutTimerSnapshot.get95thPercentile()),
                    nsToMs(checkoutTimerSnapshot.get98thPercentile()),
                    nsToMs(checkoutTimerSnapshot.get99thPercentile()),
                    nsToMs(checkoutTimerSnapshot.get999thPercentile())
            ));

            System.out.println(String.format("\tCheckin\t -- mean: %3.2fms\tmin: %3.2fms\tmax: %3.2fms\t75th: %3.2fms" +
                            "\t95th: %3.2fms\t98th: %3.2fms\t99th: %3.2fms\t999th: %3.2fms",
                    nsToMs(checkinTimerSnapshot.getMean()),
                    nsToMs(checkinTimerSnapshot.getMin()),
                    nsToMs(checkinTimerSnapshot.getMax()),
                    nsToMs(checkinTimerSnapshot.get75thPercentile()),
                    nsToMs(checkinTimerSnapshot.get95thPercentile()),
                    nsToMs(checkinTimerSnapshot.get98thPercentile()),
                    nsToMs(checkinTimerSnapshot.get99thPercentile()),
                    nsToMs(checkinTimerSnapshot.get999thPercentile())
            ));

            System.out.println(String.format("\tTotal\t -- mean: %3.2fms\tmin: %3.2fms\tmax: %3.2fms\t75th: %3.2fms" +
                            "\t95th: %3.2fms\t98th: %3.2fms\t99th: %3.2fms\t999th: %3.2fms",
                    nsToMs(totalTimerSnapshot.getMean()),
                    nsToMs(totalTimerSnapshot.getMin()),
                    nsToMs(totalTimerSnapshot.getMax()),
                    nsToMs(totalTimerSnapshot.get75thPercentile()),
                    nsToMs(totalTimerSnapshot.get95thPercentile()),
                    nsToMs(totalTimerSnapshot.get98thPercentile()),
                    nsToMs(totalTimerSnapshot.get99thPercentile()),
                    nsToMs(totalTimerSnapshot.get999thPercentile())
            ));

            System.out.flush();
        }

        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(_reportingIntervalSeconds));
        } catch (InterruptedException ignored) {
        }
    }

    public void printHeaders() {
        _out.print("counter,");
        _out.print(String.format("%s,%s,%s,%s,%s,", "cr-totl", "cr-mnrt", "cr-1mrt", "cr-5mrt", "cr-15rt"));
        _out.print(String.format("%s,%s,%s,%s,%s,", "dt-totl", "dt-mnrt", "dt-1mrt", "dt-5mrt", "dt-15rt"));
        _out.print(String.format("%s,%s,%s,%s,%s,", "sc-totl", "sc-mnrt", "sc-1mrt", "sc-5mrt", "sc-15rt"));
        _out.print(String.format("%s,%s,%s,%s,%s,%s,%s,", "co-totl", "co-min", "co-max", "co-mean", "co-1mrt", "co-5mrt", "co-15rt"));
        _out.print(String.format("%s,%s,%s,%s,%s,%s,%s,", "ci-totl", "ci-min", "ci-max", "ci-mean", "ci-1mrt", "ci-5mrt", "ci-15rt"));
        _out.print(String.format("%s,%s,%s,%s,%s,%s,%s,", "st-totl", "st-min", "st-max", "st-mean", "st-1mrt", "st-5mrt", "st-15rt"));
        _out.print(String.format("%s,%s,%s,%s,%s,%s,%s,", "tt-totl", "tt-min", "tt-max", "tt-mean", "tt-1mrt", "tt-5mrt", "tt-15rt"));
        _out.println();
    }

    public boolean shouldContinue() {

        long spent = currentTimeSeconds() - _startTime;
        boolean shouldContinue;

        if (spent >= _totalRuntime) {
            for (Thread t : _workers) {
                if (t.isAlive()) {
                    try {
                        t.interrupt();
                        t.join();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            shouldContinue = false;
        } else {
            int total = _workers.size();
            int done = 0;
            for (Thread t : _workers) {
                if (!t.isAlive()) {
                    done++;
                }
            }
            shouldContinue = (done != total);
        }

        if (!shouldContinue) {
            _out.close();
        }
        return shouldContinue;
    }

    public long currentTimeSeconds() {
        return TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    // Snapshot returns times in NS, this converts them to ms as we need
    private double nsToMs(double ns) {
        return ns / 1000000;
    }
}
