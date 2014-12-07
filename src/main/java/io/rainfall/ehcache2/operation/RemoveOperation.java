package io.rainfall.ehcache2.operation;

import io.rainfall.AssertionEvaluator;
import io.rainfall.Configuration;
import io.rainfall.ObjectGenerator;
import io.rainfall.Operation;
import io.rainfall.SequenceGenerator;
import io.rainfall.TestException;
import io.rainfall.ehcache.statistics.EhcacheResult;
import io.rainfall.ehcache2.CacheConfig;
import io.rainfall.statistics.StatisticsHolder;
import io.rainfall.statistics.Task;
import net.sf.ehcache.Ehcache;

import java.util.List;
import java.util.Map;

import static io.rainfall.ehcache.statistics.EhcacheResult.EXCEPTION;
import static io.rainfall.ehcache.statistics.EhcacheResult.MISS;
import static io.rainfall.ehcache.statistics.EhcacheResult.REMOVE;

/**
 * @author Aurelien Broszniowski
 */
public class RemoveOperation<K, V> extends EhcacheOperation {

  @Override
  public void exec(final StatisticsHolder statisticsHolder, final Map<Class<? extends Configuration>,
      Configuration> configurations, final List<AssertionEvaluator> assertions) throws TestException {

    CacheConfig<K, V> cacheConfig = (CacheConfig<K, V>)configurations.get(CacheConfig.class);
    final long next = this.sequenceGenerator.next();
    List<Ehcache> caches = cacheConfig.getCaches();
    for (final Ehcache cache : caches) {
      statisticsHolder
          .measure(cache.getName(), new Task() {

            @Override
            public EhcacheResult definition() throws Exception {
              boolean removed;
              try {
                removed = cache.remove(keyGenerator.generate(next));
              } catch (Exception e) {
                return EXCEPTION;
              }
              if (removed) {
                return REMOVE;
              } else {
                return MISS;
              }
            }
          });
    }
  }
}
