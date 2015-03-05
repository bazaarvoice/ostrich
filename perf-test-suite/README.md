# Performance Test Suite for Ostrich

## Objective

The point of this performance test is to be able to recreate Ostrich performance problems observed in 
 production, with the eventual aim of being able to fix those performance problems.

Anecdotally, services would run fine as long as the total number of requests-per-second to an Ostrich 
 service pool was low. As the requests per second to the Ostrich Service Pool increased, the performance
 of Ostrich would degrade, sometimes catastrophically.

This test has many parameters that can be adjusted

  * The requests-per-second to Ostrich
  * What work the "Ostrich client" does for each request
  * Ostrich "pool" parameters (How and when Ostrich will request new Client Objects for each Endpoint)

Note this test is specifically testing a "backend" component of Ostrich (the ServiceCache) that we've 
 identified as a problem. This does not test Ostrich as a whole

There should be a "black box" perf test for Ostrich in the future.   

## Defining Services and Caches

We have a fixed number of singleton services that performs cryptographic hash on a given String. A service 
factory provides a wrapper for those services and returned a wrapped service as and when requested by the 
 ServiceCache.
 
A service cache is initialized with various parameters such as max number of service instances, idle time 
 before evicting a (wrapped) service instance, policy to  adhere upon cache is exhausted, etc. A detailed 
 breakdown of available parameters are provided in the Parameters section.

## Notes on Compilation

The suite also takes in a parameter "ostrich.core.version" at compile time to determine which version of 
 ostrich core should be tested.

    mvn -q clean compile assembly:single -Dostrich.core.version=1.9.0

However, due to breaking changes between versions, i.e. 1.8 & 1.9 uses different version of metrics and
 subsequently different constructor of ServiceCache, this 'may' require you to make changes in the test 
 for successful compilation.


## Running The Suite

To determine the performance of Ostrich under varying loads, this test suite takes various parameters. They 
 allow us to set the load on the cache, i.e. # of worker threads using the cache, the load on each thread,
 i.e. how much work each worker thread must do, etc. These help us to determine the overhead of ostrich 
 under nominal to mediocre to somewhat heavy loads. 

After determining and setting those desired values the suite will run the desired number of threads and
 load, and will monitor the health and performance of the cache.

## Parameters

### Standard help/usage message 

      -h,--help                   Show this help message!
      
### ServiceCache specific parameters

      -e,--exhaust-action <arg>   Exhaust action when cache is exhausted,
                                  acceptable values are WAIT|FAIL|GROW, default
                                  is WAIT

      -i,--idle-time <arg>        Idle time before service cache should take
                                  evict action, default is 10

      -m,--max-instances <arg>    Max instances per end point in service cache,
                                  default is 10

      -g,--singleton-mode         Run with singleton policy mode, default is
                                  false

      -n,--eviction-ttl <arg>     Eviction TTL for bad clients in singleton
                                  policy mode, default is 5 second
                                  crunch hash, default is 1024 X 5 (5kb)

### Overall load tweaking parameters

      -t,--thread-size <arg>      # of workers threads to run, default is 100

      -w,--work-size <arg>        length of the string to generate randomly and
                                  crunch hash, default is 5120 (5kb)

### Chaos parameter to cause havoc on cache

      -c,--chaos-count <arg>      Number of chaos workers to use, default is 2

      -l,--chaos-interval <arg>   time (in seconds) to wait between chaos,
                                   default is 15

### I/O and runtime parameters

      -o,--output-file <arg>      Output file to use instead of STDOUT

      -r,--run-time <arg>         seconds to run before it kills worker running
                                  threads, default is 9223372036854775807
                                  (Long.MAX_VALUE)

      -s,--statistics             Output current running stats on STDOUT,
                                  illegal if --output-file is not provided

      -v,--report-every <arg>     Reports the running statistics every #
                                  seconds

### Recommended JVM parameters

      -Xms2048m                   Allocate enough heap space such that it does not
                                  ran out of memory
                                  2GB of more is recommended 

      -Djava.compiler=NONE        Disable JIT compiler to keep things consistent
                                  This will avoid surprise optimization and as a
                                  result consequent performance gain


## Interpreting the results

A CSV style output provides a number of metrics, it can either be STDOUT or if provided a (new) file. The 
 first row  (header) of the file specifies various data points in each columns. The drill down of the header 
 items are given below:
 
    - counter: reporting counter
    - cr-totl: creation total
    - cr-mnrt: creation mean rate
    - cr-1mrt: creation 1 min rate
    - cr-5mrt: creation 5 min rate
    - cr-15rt: creation 15 min rate
    - dt-totl: destruction total
    - dt-mnrt: destruction mean rate
    - dt-1mrt: destruction 1 min rate
    - dt-5mrt: destruction 5 min rate
    - dt-15rt: destruction 15 min rate
    - sc-totl: service called total
    - sc-mnrt: service called mean rate
    - sc-1mrt: service called 1 min rate
    - sc-5mrt: service called 5 min rate
    - sc-15rt: service called 15 min rate
    - co-totl: check out total
    - co-min: check out min time
    - co-max: check out max time
    - co-mean: check out mean time
    - co-1mrt: check out 1 min rate
    - co-5mrt: check out 5 min rate
    - co-15rt: check out 15 min rate
    - ci-totl: check in total
    - ci-min: check in min time
    - ci-max: check out max time
    - ci-mean: check out mean time
    - ci-1mrt: check out 1 min rate
    - ci-5mrt: check out 5 min rate
    - ci-15rt: check out 15 min rate
    - st-totl: service execution total count
    - st-min: service execution min time
    - st-max: service execution max time
    - st-mean: service execution mean time
    - st-1mrt: service execution 1 min time
    - st-5mrt: service execution 5 min time
    - st-15rt: service execution 15 min time
    - tt-totl: complete execution total count
    - tt-min: complete execution min time
    - tt-max: complete execution max time
    - tt-mean: complete execution mean time
    - tt-1mrt: complete execution 1 min rate
    - tt-5mrt: complete execution 5 min rate
    - tt-15rt: complete execution 15 min rate
 
Example:

    counter,cr-totl,cr-mnrt,cr-1mrt,cr-5mrt,cr-15rt,dt-totl,dt-mnrt,dt-1mrt,dt-5mrt,dt-15rt,\
        sc-totl,sc-mnrt,sc-1mrt,sc-5mrt,sc-15rt,co-totl,co-min,co-max,co-mean,co-1mrt,co-5mrt,co-15rt,\
        ci-totl,ci-min,ci-max,ci-mean,ci-1mrt,ci-5mrt,ci-15rt,st-totl,st-min,st-max,st-mean,st-1mrt,st-5mrt,st-15rt,\
        tt-totl,tt-min,tt-max,tt-mean,tt-1mrt,tt-5mrt,tt-15rt,
    0,100,599.86,0.00,0.00,0.00,0,0.00,0.00,0.00,0.00,0,0.00,0.00,0.00,0.00,100,0.10,33.07,11.70,0.00,0.00,0.00,0,\
        0.00,0.00,0.00,0.00,0.00,0.00,0,0.00,0.00,0.00,0.00,0.00,0.00,0,0.00,0.00,0.00,0.00,0.00,0.00,
    1,1516,1286.03,0.00,0.00,0.00,0,0.00,0.00,0.00,0.00,1495,1346.02,0.00,0.00,0.00,1513,0.07,\234.06,19.36,0.00,0.00,\
        0.00,1497,0.02,232.63,8.97,0.00,0.00,0.00,1513,0.15,443.13,29.43,0.00,0.00,0.00,1500,0.36,538.90,58.12,0.00,0.00,0.00,


#### Rolling statistics

If requested (via --statistics / -s) a rolling statistics output on STDOUT provides the following metrics:

    <Current date-time>
    Running ## seconds of <total-runtime> with threads: ##, work size: ##, idle time: ##, max instance: ##, \
            exhaust action: <>, singleton-mode: <>, eviction-ttl: ##, chaos-worker: ##, chaos-interval: ##
    Called count: ####    Cache Miss: ####    Failed Count: ####    Service Created: ####    Service Destroyed: #### \
            Chaos: ####    Stable: ####    Register: ####    Evict: ####    Load: ####

    created / destroyed   -- 1-min: #.##/s / #.##/s    5-min: #.##/s / #.##/s      15-min: #.##/s / #.##/s    mean: #.##/s / #.##/s
    chaos / stable        -- 1-min: #.##/s / #.##/s    5-min: #.##/s / #.##/s      15-min: #.##/s / #.##/s    mean: #.##/s / #.##/s
    executed / failure    -- 1-min: #.##/s / #.##/s    5-min: #.##/s / #.##/s      15-min: #.##/s / #.##/s    mean: #.##/s / #.##/s
    service / total       -- 1-min: #.##/s / #.##/s    5-min: #.##/s / #.##/s      15-min: #.##/s / #.##/s    mean: #.##/s / #.##/s
    checkout / checkin    -- 1-min: #.##/s / #.##/s    5-min: #.##/s / #.##/s      15-min: #.##/s / #.##/s    mean: #.##/s / #.##/s

    Service     -- mean: #.##ms    min: #.##ms    max: #.##ms    75th: #.##ms    95th: #.##ms    98th: #.##ms    99th: #.##ms    999th: #.##ms
    Checkout    -- mean: #.##ms    min: #.##ms    max: #.##ms    75th: #.##ms    95th: #.##ms    98th: #.##ms    99th: #.##ms    999th: #.##ms
    Checkin     -- mean: #.##ms    min: #.##ms    max: #.##ms    75th: #.##ms    95th: #.##ms    98th: #.##ms    99th: #.##ms    999th: #.##ms
    Total       -- mean: #.##ms    min: #.##ms    max: #.##ms    75th: #.##ms    95th: #.##ms    98th: #.##ms    99th: #.##ms    999th: #.##ms


## Running Example

### Build the assembled dependency included jar

    mvn clean compile assembly:single

This creates a shaded jar called test-suite-<VERSION>-jar-with-dependencies.jar with all dependencies included.

### Run the suite

#### For the following configuration:

    - Heap size: 2GB (or more, based on how many threads you want to run), JIT disabled
    - threads: 25, work size: 12000Bytes
    - cache idle time: 10 seconds, cache max instance per service: 10, cache exhaust action: GROW
    - print statistics, output ot file output.csv, report every 5 second, run for 30 second 

#### The command would be:

    java -Djava.compiler=NONE -Xms2048m -jar test-suite-<VERSION>-jar-with-dependencies.jar \
    --thread-size 25 --work-size 12000 \
    --idle-time 10 --max-instances 10 --exhaust-action GROW \
    --statistics --report-every 5 --run-time 30 --output-file output.csv

Or

    java -Djava.compiler=NONE -Xms2048m -jar test-suite-<VERSION>-jar-with-dependencies.jar -t 25 -w 12000 -i 10 -m 10 -e GROW -s -v 5 -r 30 -o output.csv
