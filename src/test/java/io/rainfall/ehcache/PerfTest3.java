/*
 * Copyright 2014 Aurélien Broszniowski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rainfall.ehcache;

import io.rainfall.ObjectGenerator;
import io.rainfall.Runner;
import io.rainfall.Scenario;
import io.rainfall.ScenarioRun;
import io.rainfall.SyntaxException;
import io.rainfall.configuration.ConcurrencyConfig;
import io.rainfall.configuration.ReportingConfig;
import io.rainfall.ehcache.statistics.EhcacheResult;
import io.rainfall.ehcache3.CacheConfig;
import io.rainfall.ehcache3.CacheDefinition;
import io.rainfall.ehcache3.operation.PutVerifiedOperation;
import io.rainfall.generator.ByteArrayGenerator;
import io.rainfall.generator.LongGenerator;
import io.rainfall.generator.StringGenerator;
import io.rainfall.generator.VerifiedValueGenerator;
import io.rainfall.generator.VerifiedValueGenerator.VerifiedValue;
import io.rainfall.generator.sequence.Distribution;
import io.rainfall.statistics.StatisticsPeekHolder;
import io.rainfall.utils.SystemTest;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.impl.config.persistence.CacheManagerPersistenceConfiguration;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.openjdk.jol.info.GraphLayout;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static io.rainfall.Scenario.weighted;
import static io.rainfall.configuration.ReportingConfig.gcStatistics;
import static io.rainfall.configuration.ReportingConfig.html;
import static io.rainfall.configuration.ReportingConfig.report;
import static io.rainfall.configuration.ReportingConfig.text;
import static io.rainfall.ehcache.statistics.EhcacheResult.GET;
import static io.rainfall.ehcache.statistics.EhcacheResult.MISS;
import static io.rainfall.ehcache.statistics.EhcacheResult.PUT;
import static io.rainfall.ehcache3.Ehcache3Operations.putIfAbsent;
import static io.rainfall.ehcache3.CacheConfig.cacheConfig;
import static io.rainfall.ehcache3.CacheDefinition.cache;
import static io.rainfall.ehcache3.Ehcache3Operations.get;
import static io.rainfall.ehcache3.Ehcache3Operations.put;
import static io.rainfall.ehcache3.Ehcache3Operations.remove;
import static io.rainfall.ehcache3.Ehcache3Operations.removeForKeyAndValue;
import static io.rainfall.execution.Executions.during;
import static io.rainfall.execution.Executions.once;
import static io.rainfall.execution.Executions.times;
import static io.rainfall.generator.SequencesGenerator.atRandom;
import static io.rainfall.generator.SequencesGenerator.sequentially;
import static io.rainfall.generator.sequence.Distribution.GAUSSIAN;
import static io.rainfall.unit.Instance.instances;
import static io.rainfall.unit.TimeDivision.minutes;
import static io.rainfall.unit.TimeDivision.seconds;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder;
import static org.ehcache.config.builders.ResourcePoolsBuilder.newResourcePoolsBuilder;

/**
 * @author Aurelien Broszniowski
 */
@Category(SystemTest.class)
public class PerfTest3 {

  @Test
  @Ignore
  public void testVerifiedValue() {
    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig().threads(16).timeout(30, MINUTES);

    ObjectGenerator<Long> keyGenerator = new LongGenerator();
    ObjectGenerator<VerifiedValue> valueGenerator = new VerifiedValueGenerator<Long>(keyGenerator);
    long nbElements = 100;
    CacheConfigurationBuilder<Long, VerifiedValue> builder = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, VerifiedValue.class,
        newResourcePoolsBuilder().heap(nbElements, EntryUnit.ENTRIES)
            .build());

    CacheManager cacheManager = newCacheManagerBuilder()
        .withCache("one", builder.build())
        .build(true);

    Cache<Long, VerifiedValue> one = cacheManager.getCache("one", Long.class, VerifiedValue.class);

    try {
      long start = System.nanoTime();

      System.out.println("Cache Warmup");
      Runner.setUp(
          Scenario.scenario("Cache warm up phase")
              .exec(
                  put(keyGenerator, valueGenerator, sequentially(), cache("one", one))
              ))
          .executed(times(nbElements))
          .config(report(EhcacheResult.class))
          .config(concurrency)
          .config(cacheConfig(Long.class, VerifiedValue.class).cache("one", one)
          )
          .start();
      long end = System.nanoTime();
      System.out.println("Warmup length : " + (end - start) / 1000000L + "ms");


      System.out.println("Cache Test");
      StatisticsPeekHolder finalStats = Runner.setUp(
          Scenario.scenario("Cache test phase")
              .exec(
                  weighted(0.10,
                      new PutVerifiedOperation<Long, VerifiedValue>(keyGenerator, valueGenerator, sequentially(), cache("one", one))
                  ),
                  weighted(0.90, get(Long.class, VerifiedValue.class).using(keyGenerator, valueGenerator)
                      .sequentially())))
//          .warmup(during(1, TimeDivision.minutes))
          .executed(during(20, seconds))
          .config(concurrency)
          .config(report(EhcacheResult.class, new EhcacheResult[] { GET, PUT, MISS }).log(text()))
          .config(cacheConfig(Long.class, VerifiedValue.class).cache("one", one))
          .start();

      System.out.println("Nb errors : " + finalStats.getTotalAssertionsErrorsCount());

    } catch (SyntaxException e) {
      e.printStackTrace();
    } finally {
      cacheManager.close();
    }
  }

  @Test
  @Ignore
  public void testKeys() {
    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig().threads(16).timeout(30, MINUTES);

    ObjectGenerator<Long> keyGenerator = new ObjectGenerator<Long>() {
      @Override
      public Long generate(final Long seed) {
        System.out.println("seed = " + seed);
        return seed;
      }

      @Override
      public String getDescription() {
        return "";
      }
    };
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(1024);

    long nbElements = 100;
    CacheConfigurationBuilder<Long, Byte[]> builder = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, Byte[].class,
        newResourcePoolsBuilder().heap(nbElements, EntryUnit.ENTRIES).build());

    CacheManager cacheManager = newCacheManagerBuilder()
        .withCache("one", builder.build())
        .build(true);

    Cache<Long, Byte[]> one = cacheManager.getCache("one", Long.class, Byte[].class);

    try {
      long start = System.nanoTime();

      System.out.println("Warmup");
      Runner.setUp(
          Scenario.scenario("Cache warm up phase")
              .exec(put(keyGenerator, valueGenerator, sequentially(), cache("one", one))))
          .executed(times(nbElements))
          .config(concurrency)
          .config(report(EhcacheResult.class, new EhcacheResult[] { PUT }).log(text()))
          .config(cacheConfig(Long.class, Byte[].class)
              .cache("one", one)
          )
          .start();

      long end = System.nanoTime();

      System.out.println("verifying values");
      for (long seed = 2; seed < nbElements; seed++) {
        Object o = one.get(keyGenerator.generate(seed));
        if (o == null) System.out.println("null for key " + seed);
      }
      System.out.println("----------> Done");
    } catch (SyntaxException e) {
      e.printStackTrace();
    } finally {
      cacheManager.close();
    }

  }

  @Test
  @Ignore
  public void testTpsLimit() throws SyntaxException {
    CacheConfigurationBuilder<Long, Byte[]> builder = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, Byte[].class,
        newResourcePoolsBuilder().heap(250000, EntryUnit.ENTRIES).build());

    final CacheManager cacheManager = newCacheManagerBuilder()
        .withCache("one", builder.build())
        .build(true);

    final Cache<Long, Byte[]> one = cacheManager.getCache("one", Long.class, Byte[].class);

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig().threads(4).timeout(50, MINUTES);

    ObjectGenerator<Long> keyGenerator = new LongGenerator();
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(1000);

    EhcacheResult[] resultsReported = new EhcacheResult[] { GET, PUT, MISS };

    Scenario scenario = Scenario.scenario("Test phase").exec(
        put(keyGenerator, valueGenerator, sequentially(), 50000, cache("one", one))
    );

    System.out.println("----------> Test phase");
    Runner.setUp(scenario)
        .executed(once(4, instances), during(10, seconds))
        .config(concurrency,
            ReportingConfig.report(EhcacheResult.class, resultsReported)
                .log(text(), html()))
        .config(cacheConfig(Long.class, Byte[].class).cache("one", one)
        )
        .start();
    System.out.println("----------> Done");

    cacheManager.close();
  }

  @Test
  @Ignore
  public void testWarmup() throws SyntaxException {
    CacheConfigurationBuilder<Long, Byte[]> builder = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, Byte[].class,
        newResourcePoolsBuilder().heap(250000, EntryUnit.ENTRIES).build());

    final CacheManager cacheManager = newCacheManagerBuilder()
        .withCache("one", builder.build())
        .build(true);

    final Cache<Long, Byte[]> one = cacheManager.getCache("one", Long.class, Byte[].class);
    final Cache<Long, Byte[]> two = cacheManager.getCache("two", Long.class, Byte[].class);

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig().threads(4).timeout(50, MINUTES);

    ObjectGenerator<Long> keyGenerator = new LongGenerator();
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(1000);

    EhcacheResult[] resultsReported = new EhcacheResult[] { GET, PUT, MISS };

    Scenario scenario = Scenario.scenario("Test phase").exec(
        put(keyGenerator, valueGenerator, sequentially(), cache("one", one)),
        get(Long.class, byte[].class).using(keyGenerator, valueGenerator).sequentially()
    );

    System.out.println("----------> Test phase");
    StatisticsPeekHolder finalStats = Runner.setUp(
        scenario)
        .warmup(during(25, seconds))
        .executed(during(30, seconds))
        .config(concurrency,
            ReportingConfig.report(EhcacheResult.class, resultsReported).log(text(), html()))
        .config(cacheConfig(Long.class, Byte[].class).cache("one", one)
        )
        .start();
    System.out.println("----------> Done");

    cacheManager.close();
  }


  @Test
  @Ignore
  public void testHisto() throws SyntaxException {
    CacheConfigurationBuilder<Long, Byte[]> builder = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, Byte[].class,
        newResourcePoolsBuilder().heap(250000, EntryUnit.ENTRIES).build());

    final CacheManager cacheManager = newCacheManagerBuilder()
        .withCache("one", builder.build())
        .withCache("two", builder.build())
        .build(true);

    final Cache<Long, Byte[]> one = cacheManager.getCache("one", Long.class, Byte[].class);
    final Cache<Long, Byte[]> two = cacheManager.getCache("two", Long.class, Byte[].class);

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig()
        .threads(4).timeout(50, MINUTES);

    ObjectGenerator<Long> keyGenerator = new LongGenerator();
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(1000);

    EhcacheResult[] resultsReported = new EhcacheResult[] { GET, PUT, MISS };

    Scenario scenario = Scenario.scenario("Test phase").exec(
        put(keyGenerator, valueGenerator, sequentially(), cache("one", one), cache("two", two)),
        get(Long.class, byte[].class).using(keyGenerator, valueGenerator).sequentially()
    );

    System.out.println("----------> Warm up phase");
    Runner.setUp(scenario)
        .executed(during(15, seconds))
        .config(concurrency,
            ReportingConfig.report(EhcacheResult.class, resultsReported).log(text()))
        .config(cacheConfig(Long.class, Byte[].class).cache("one", one).cache("two", two)
        )
        .start();

    System.out.println("----------> Test phase");
    Runner.setUp(
        scenario)
        .executed(during(30, seconds))
        .config(concurrency,
            ReportingConfig.report(EhcacheResult.class, resultsReported).log(text(), html()))
        .config(cacheConfig(Long.class, Byte[].class).cache("one", one).cache("two", two)
        )
        .start();
    System.out.println("----------> Done");

    cacheManager.close();
  }

  @Test
  @Ignore
  public void testLoad() throws SyntaxException, InterruptedException {
    int nbCaches = 10;
    int nbElements = 500000;
    CacheConfigurationBuilder<Long, byte[]> configurationBuilder = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, byte[].class,
        newResourcePoolsBuilder().heap(nbElements, EntryUnit.ENTRIES).build());

    CacheManagerBuilder<CacheManager> cacheManagerBuilder = newCacheManagerBuilder();
    for (int i = 0; i < nbCaches; i++) {
      cacheManagerBuilder = cacheManagerBuilder.withCache("cache" + i, configurationBuilder.build());
    }
    CacheManager cacheManager = cacheManagerBuilder.build(true);

    CacheDefinition<Long, byte[]>[] cacheDefinitions = new CacheDefinition[nbCaches];
    CacheConfig<Long, byte[]> cacheConfig = cacheConfig(Long.class, byte[].class);
    for (int i = 0; i < nbCaches; i++) {
      String alias = "cache" + i;
      cacheDefinitions[i] = new CacheDefinition<Long, byte[]>(alias, cacheManager.getCache(alias, Long.class, byte[].class));
      cacheConfig.cache(alias, cacheManager.getCache(alias, Long.class, byte[].class));
    }

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig()
        .threads(4).timeout(50, MINUTES);

    ObjectGenerator<Long> keyGenerator = new LongGenerator();
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(1000);

    System.out.println("----------> Warm up phase");
    ScenarioRun run = Runner.setUp(
        Scenario.scenario("Warm up phase").exec(
            put(keyGenerator, valueGenerator, sequentially(), cacheDefinitions),
            get(Long.class, byte[].class).using(keyGenerator, valueGenerator).sequentially(),
            remove(Long.class, byte[].class).using(keyGenerator, valueGenerator).sequentially(),
            putIfAbsent(Long.class, byte[].class).using(keyGenerator, valueGenerator).sequentially()
        ))
        .executed(times(nbElements))
        .config(concurrency, ReportingConfig.report(EhcacheResult.class).log(text()))
        .config(cacheConfig);
    run.start();

    GraphLayout graphLayout = GraphLayout.parseInstance(run);
    System.out.println(graphLayout.totalSize());
    System.out.println("----------> Done");

    cacheManager.close();
  }

  @Test
  @Ignore
  public void testReplace() throws SyntaxException {
    CacheConfigurationBuilder<Long, Long> builder = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, Long.class,
        newResourcePoolsBuilder().heap(250000, EntryUnit.ENTRIES).build());

    final CacheManager cacheManager = newCacheManagerBuilder()
        .withCache("one", builder.build())
        .build(true);

    final Cache<Long, Long> one = cacheManager.getCache("one", Long.class, Long.class);

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig()
        .threads(4).timeout(50, MINUTES);

    int nbElements = 250000;
    ObjectGenerator<Long> keyGenerator = new LongGenerator();
    ObjectGenerator<Long> valueGenerator = new LongGenerator();

    ReportingConfig reportingConfig = ReportingConfig.report(EhcacheResult.class).log(text());
    CacheConfig<Long, Long> cacheConfig = cacheConfig(Long.class, Long.class).cache("one", one);
    Runner.setUp(
        Scenario.scenario("warmup phase").exec(
            put(keyGenerator, valueGenerator, sequentially(), cache("one", one)
            )))
        .executed(times(nbElements))
        .config(concurrency, reportingConfig)
        .config(cacheConfig)
        .start()
    ;
    Runner.setUp(
        Scenario.scenario("Test phase").exec(
            removeForKeyAndValue(Long.class, Long.class).using(keyGenerator, valueGenerator).sequentially()
        ))
        .executed(during(1, minutes))
        .config(concurrency, reportingConfig)
        .config(cacheConfig)
        .start()
    ;
    cacheManager.close();
  }

  @Test
  @Ignore
  public void testMemory() throws SyntaxException {
    int nbElements = 5000000;
    CacheConfigurationBuilder<Long, Long> builder = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, Long.class,
        newResourcePoolsBuilder().heap(nbElements, EntryUnit.ENTRIES).build());

    final CacheManager cacheManager = newCacheManagerBuilder()
        .withCache("one", builder.build())
        .build(true);

    final Cache<Long, Long> one = cacheManager.getCache("one", Long.class, Long.class);

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig()
        .threads(4).timeout(30, MINUTES);

    ObjectGenerator<Long> keyGenerator = new LongGenerator();
    ObjectGenerator<Long> valueGenerator = new LongGenerator();

    ReportingConfig reportingConfig = ReportingConfig.report(EhcacheResult.class).log(text());
    CacheConfig<Long, Long> cacheConfig = cacheConfig(Long.class, Long.class).cache("one", one);

    Runner.setUp(
        Scenario.scenario("Test phase").exec(
            put(keyGenerator, valueGenerator, atRandom(GAUSSIAN, 0, nbElements, nbElements / 10), cache("one", one))
        ))
        .executed(during(10, minutes))
        .config(concurrency, reportingConfig)
        .config(cacheConfig)
        .start()
    ;
    cacheManager.close();
  }

  @Test
  @Ignore
  public void tier() {
    int heap = 200000;
    int offheap = 1;
    int disk = 2;

    long nbElementsHeap = MemoryUnit.MB.toBytes(heap) / MemoryUnit.KB.toBytes(1);
    long nbElements = MemoryUnit.GB.toBytes(disk) / MemoryUnit.KB.toBytes(1);

    CacheConfigurationBuilder<String, byte[]> cacheBuilder = CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, byte[].class,
        newResourcePoolsBuilder()
            .heap(nbElementsHeap, EntryUnit.ENTRIES)
            .offheap(offheap, MemoryUnit.GB)
            .disk(disk, MemoryUnit.GB)
            .build());

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig()
        .threads(4).timeout(30, MINUTES);

    ObjectGenerator<String> keyGenerator = StringGenerator.fixedLength(10);
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(1024);

    CacheManager cacheManager = newCacheManagerBuilder()
        .with(new CacheManagerPersistenceConfiguration(new File("/data/PerfTest3")))
        .withCache("one", cacheBuilder.build())
        .build(true);

    Cache one = cacheManager.getCache("one", String.class, byte[].class);
    try {
      System.out.println("----------> Cache Warm up phase");
      long start = System.nanoTime();

      Runner.setUp(
          Scenario.scenario("Cache warm up phase")
              .exec(put(keyGenerator, valueGenerator, sequentially(), cache("one", one))))
          .executed(times(nbElements))
          .config(concurrency)
          .config(report(EhcacheResult.class, new EhcacheResult[] { PUT }).log(text(), html("warmup-tier")))
          .config(cacheConfig(String.class, byte[].class)
              .cache("one", one)
          )
          .start();

      long end = System.nanoTime();
      System.out.println("Warmup time = " + TimeUnit.NANOSECONDS.toMillis((end - start)) + "ms");

      Integer testLength = Integer.parseInt(System.getProperty("testLength", "7"));

      System.out.println("----------> Test phase");
      StatisticsPeekHolder finalStats = Runner.setUp(
          Scenario.scenario("Test phase")
              .exec(
                  weighted(0.90, get(String.class, byte[].class).using(keyGenerator, valueGenerator)
                      .atRandom(Distribution.GAUSSIAN, 0, nbElements, nbElements / 10)),
                  weighted(0.10, put(keyGenerator, valueGenerator, atRandom(Distribution.GAUSSIAN, 0, nbElements, nbElements / 10),
                      cache("one", one))
                  )))
          .warmup(during(3, minutes))
          .executed(during(testLength, minutes))
          .config(concurrency)
          .config(report(EhcacheResult.class, new EhcacheResult[] { PUT, GET, MISS })
              .log(text(), html("test-tier")))
          .config(cacheConfig(String.class, byte[].class)
              .cache("one", one)
          )
          .start();
      System.out.println("----------> Done");
    } catch (SyntaxException e) {
      e.printStackTrace();
    } finally {
      cacheManager.close();
    }
  }


  @Test
  @Ignore
  public void testBasic() {
    int heap = 200000;
    int offheap = 1;
    int disk = 2;

    long nbElementsHeap = MemoryUnit.MB.toBytes(heap) / MemoryUnit.KB.toBytes(1);
    long nbElements = MemoryUnit.GB.toBytes(disk) / MemoryUnit.KB.toBytes(1);

    CacheConfigurationBuilder<String, byte[]> cacheBuilder = CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, byte[].class,
        newResourcePoolsBuilder()
            .heap(nbElementsHeap, EntryUnit.ENTRIES)
            .offheap(offheap, MemoryUnit.GB)
            .build());

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig()
        .threads(4).timeout(30, MINUTES);

    ObjectGenerator<String> keyGenerator = StringGenerator.fixedLength(10);
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(1024);

    CacheManager cacheManager = newCacheManagerBuilder()
        .with(new CacheManagerPersistenceConfiguration(new File("/data/PerfTest3")))
        .withCache("one", cacheBuilder.build())
        .build(true);

    Cache one = cacheManager.getCache("one", String.class, byte[].class);
    try {
      System.out.println("----------> Cache Warm up phase");
      long start = System.nanoTime();

      Runner.setUp(
          Scenario.scenario("Cache warm up phase")
              .exec(put(keyGenerator, valueGenerator, sequentially(), cache("one", one))))
          .executed(times(nbElements))
          .config(concurrency)
          .config(report(EhcacheResult.class, new EhcacheResult[] { PUT }).log(text()))
          .config(cacheConfig(String.class, byte[].class)
              .cache("one", one)
          )
//          .start()
      ;

      long end = System.nanoTime();
      System.out.println("Warmup time = " + TimeUnit.NANOSECONDS.toMillis((end - start)) + "ms");

      Integer testLength = Integer.parseInt(System.getProperty("testLength", "7"));

      System.out.println("----------> Test phase");
      StatisticsPeekHolder finalStats = Runner.setUp(
          Scenario.scenario("Test phase")
              .exec(
                  weighted(0.90, get(String.class, byte[].class).using(keyGenerator, valueGenerator)
                      .atRandom(Distribution.GAUSSIAN, 0, nbElements, nbElements / 10)),
                  weighted(0.10, put(keyGenerator, valueGenerator, atRandom(Distribution.GAUSSIAN, 0, nbElements, nbElements / 10),
                      cache("one", one)))
              ))
          .warmup(during(30, seconds))
          .executed(during(2, minutes))
          .config(concurrency)
          .config(report(EhcacheResult.class, new EhcacheResult[] { PUT, GET, MISS })
              .collect(gcStatistics()).log(text(), html("test-basic")))
          .config(cacheConfig(String.class, byte[].class)
              .cache("one", one)
          )
          .start();
      System.out.println("----------> Done");
    } catch (SyntaxException e) {
      e.printStackTrace();
    } finally {
      cacheManager.close();
    }
  }

  @Test
  @Ignore
  public void testClustered() {
    int nbElements = 1000;
    CacheConfigurationBuilder<String, byte[]> cacheBuilder = CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, byte[].class,
        newResourcePoolsBuilder()
            .heap(nbElements, EntryUnit.ENTRIES)
            .build());

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig()
        .threads(4).timeout(30, MINUTES);

    ObjectGenerator<String> keyGenerator = StringGenerator.fixedLength(10);
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(1024);

    CacheManager cacheManager = newCacheManagerBuilder()
        .withCache("one", cacheBuilder.build())
        .build(true);

    Cache<String, byte[]> one = cacheManager.getCache("one", String.class, byte[].class);
    try {

      StatisticsPeekHolder finalStats = Runner.setUp(
          Scenario.scenario("Test phase")
              .exec(
                  weighted(0.50, get(String.class, byte[].class).using(keyGenerator, valueGenerator)
                      .atRandom(Distribution.GAUSSIAN, 0, nbElements, nbElements / 10)),
                  weighted(0.50, put(keyGenerator, valueGenerator, atRandom(Distribution.GAUSSIAN, 0, nbElements, nbElements / 10),
                      cache("one", one)))
              ))
          .warmup(during(30, seconds))
          .executed(during(2, minutes))
          .config(concurrency)
          .config(report(EhcacheResult.class, new EhcacheResult[] { PUT, GET, MISS })
              .log(text(), html("test-clustered")))
          .config(cacheConfig(String.class, byte[].class)
              .cache("one", one)
          )
          .start();
    } catch (SyntaxException e) {
      e.printStackTrace();
    } finally {
      cacheManager.close();
    }
  }


  @Test
  @Ignore
  public void testStatisticsCollectors() {
    int nbElements = 1000;

    CacheConfigurationBuilder<String, byte[]> cacheBuilder = CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, byte[].class,
        newResourcePoolsBuilder()
            .heap(nbElements, EntryUnit.ENTRIES)
            .build());

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig()
        .threads(4).timeout(30, MINUTES);

    ObjectGenerator<String> keyGenerator = StringGenerator.fixedLength(10);
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(10);

    CacheManager cacheManager = newCacheManagerBuilder()
        .withCache("one", cacheBuilder.build())
        .build(true);

    Cache one = cacheManager.getCache("one", String.class, byte[].class);
    try {
      Runner.setUp(
          Scenario.scenario("Test reporters")
              .exec(
                  put(keyGenerator, valueGenerator, sequentially(), cache("one", one)))
      )
          .executed(during(1, minutes))
          .config(concurrency)
          .config(report(EhcacheResult.class, new EhcacheResult[] { PUT })
              .collect(gcStatistics()).log(html()))
          .config(cacheConfig(String.class, byte[].class)
              .cache("one", one)
          )
          .start()
      ;


    } catch (SyntaxException e) {
      e.printStackTrace();
    } finally {
      cacheManager.close();
    }
  }

  @Test
  @Ignore
  public void testPartition() {
    int heap = 200000;
    int offheap = 1;
    int disk = 2;

    long nbElementsHeap = MemoryUnit.MB.toBytes(heap) / MemoryUnit.KB.toBytes(1);
    long nbElements = MemoryUnit.GB.toBytes(disk) / MemoryUnit.KB.toBytes(1);

    CacheConfigurationBuilder<String, byte[]> cacheBuilder = CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, byte[].class,
        newResourcePoolsBuilder()
            .heap(nbElementsHeap, EntryUnit.ENTRIES)
            .offheap(offheap, MemoryUnit.GB)
            .build());

    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig()
        .threads(4).timeout(30, MINUTES);

    ObjectGenerator<String> keyGenerator = StringGenerator.fixedLength(10);
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(1024);

    CacheManager cacheManager = newCacheManagerBuilder()
        .with(new CacheManagerPersistenceConfiguration(new File("/data/PerfTest3")))
        .withCache("one", cacheBuilder.build())
        .build(true);

    Cache one = cacheManager.getCache("one", String.class, byte[].class);
    try {

      System.out.println("----------> Test phase");
      StatisticsPeekHolder finalStats = Runner.setUp(
          Scenario.scenario("Test phase")
              .exec(
                  Scenario.weighted(0.9,
                      get(String.class, byte[].class).using(keyGenerator, valueGenerator)
                          .atRandom(Distribution.GAUSSIAN, 0, nbElements, nbElements / 10)
                  ),
                  Scenario.weighted(0.1,
                      put(keyGenerator, valueGenerator, atRandom(Distribution.GAUSSIAN, 0, nbElements, nbElements / 10),
                          cache("one", one))
                  ),
                  Scenario.fixed(
                      remove(String.class, byte[].class).using(keyGenerator, valueGenerator).
                          atRandom(Distribution.GAUSSIAN, 0, nbElements, nbElements / 10)
                  )
              ))
          .executed(during(2, minutes))
          .config(concurrency)
          .config(report(EhcacheResult.class, new EhcacheResult[] { PUT, GET, MISS })
              .log(text(), html("test-basic")))
          .config(cacheConfig(String.class, byte[].class)
              .cache("one", one)
          )
          .start();
      System.out.println("----------> Done");
    } catch (SyntaxException e) {
      e.printStackTrace();
    } finally {
      cacheManager.close();
    }
  }

  @Test
  @Ignore
  public void testMultipleCaches() {
    int eltCount = 200000;


    ConcurrencyConfig concurrency = ConcurrencyConfig.concurrencyConfig()
        .threads(4).timeout(30, MINUTES);

    ObjectGenerator<String> onekeyGenerator = StringGenerator.fixedLength(10);
    ObjectGenerator<Long> twokeyGenerator = new LongGenerator();
    ObjectGenerator<byte[]> valueGenerator = ByteArrayGenerator.fixedLength(1024);

    CacheManager cacheManager = newCacheManagerBuilder()
        .with(new CacheManagerPersistenceConfiguration(new File("/data/PerfTest3")))
        .withCache("one", CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, byte[].class,
            newResourcePoolsBuilder().heap(eltCount, EntryUnit.ENTRIES).build()).build())
        .withCache("two", CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, byte[].class,
            newResourcePoolsBuilder().heap(eltCount, EntryUnit.ENTRIES).build()).build())
        .build(true);

    Cache<String, byte[]> one = cacheManager.getCache("one", String.class, byte[].class);
    Cache<Long, byte[]> two = cacheManager.getCache("two", Long.class, byte[].class);
    try {
      System.out.println("----------> Cache Warm up phase");
      long start = System.nanoTime();

      Runner.setUp(
          Scenario.scenario("Cache warm up phase")
              .exec(
                  put(onekeyGenerator, valueGenerator, atRandom(GAUSSIAN, 0, eltCount, eltCount), cache("one", one)),
                  put(twokeyGenerator, valueGenerator, atRandom(GAUSSIAN, 0, eltCount, eltCount), cache("two", two))
              ))
          .executed(during(2, minutes))
          .config(concurrency)
          .config(report(EhcacheResult.class, new EhcacheResult[] { PUT }).log(text(), html()))
          .start()
      ;

      long end = System.nanoTime();
      System.out.println("Warmup time = " + TimeUnit.NANOSECONDS.toMillis((end - start)) + "ms");

      System.out.println("----------> Done");
    } catch (SyntaxException e) {
      e.printStackTrace();
    } finally {
      cacheManager.close();
    }
  }

}
