package redis.embedded;

import com.google.common.io.Resources;
import org.junit.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.embedded.exceptions.*;
import redis.embedded.util.Architecture;
import redis.embedded.util.OS;

import java.util.concurrent.*;

import static org.junit.Assert.*;

public class RedisServerTest {

    private RedisServer redisServer;

    @After
    public void stopServer() {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }
    }

    @Test(timeout = 1500L)
    public void testSimpleRun() throws Exception {
        redisServer = new RedisServer(6379);
        redisServer.start();

        TimeUnit.SECONDS.sleep(1);

        redisServer.stop();
    }

    @Test(expected = EmbeddedRedisException.class)
    public void shouldNotAllowMultipleRunsWithoutStop() throws Exception {
        try {
            redisServer = new RedisServer(6379);
            redisServer.start();
            redisServer.start();
        } finally {
            redisServer.stop();
        }
    }

    @Test
    public void shouldAllowSubsequentRuns() throws Exception {
        redisServer = new RedisServer(6379);

        redisServer.start();
        redisServer.stop();

        redisServer.start();
        redisServer.stop();

        redisServer.start();
        redisServer.stop();
    }

    @Test
    public void testSimpleOperationsAfterRun() throws Exception {
        redisServer = new RedisServer(6379);
        redisServer.start();

        try (JedisPool pool = new JedisPool("localhost", 6379);
             Jedis jedis = pool.getResource()) {

            jedis.mset("abc", "1", "def", "2");

            assertEquals("1", jedis.mget("abc").get(0));
            assertEquals("2", jedis.mget("def").get(0));
            assertEquals(null, jedis.mget("xyz").get(0));
        }
    }

    @Test
    public void shouldIndicateInactiveBeforeStart() throws Exception {
        redisServer = new RedisServer(6379);

        assertFalse(redisServer.isActive());
    }

    @Test
    public void shouldIndicateActiveAfterStart() throws Exception {
        redisServer = new RedisServer(6379);
        redisServer.start();

        assertTrue(redisServer.isActive());
    }

    @Test
    public void shouldIndicateInactiveAfterStop() throws Exception {
        redisServer = new RedisServer(6379);

        redisServer.start();
        redisServer.stop();

        assertFalse(redisServer.isActive());
    }

    @Test
    public void shouldOverrideDefaultExecutable() throws Exception {
        RedisExecProvider customProvider = RedisExecProvider.defaultProvider()
                .override(OS.UNIX, Architecture.x86, Resources.getResource("redis-server-2.8.19-32").getFile())
                .override(OS.UNIX, Architecture.x86_64, Resources.getResource("redis-server-2.8.19").getFile())
                .override(OS.WINDOWS, Architecture.x86, Resources.getResource("redis-server-2.8.19.exe").getFile())
                .override(OS.WINDOWS, Architecture.x86_64, Resources.getResource("redis-server-2.8.19.exe").getFile())
                .override(OS.MAC_OS_X, Resources.getResource("redis-server-2.8.19").getFile());

        redisServer = new RedisServer.Builder()
                .redisExecProvider(customProvider)
                .build();
    }

    @Test(expected = RedisBuildingException.class)
    public void shouldFailWhenBadExecutableGiven() throws Exception {
        RedisExecProvider buggyProvider = RedisExecProvider.defaultProvider()
                .override(OS.UNIX, "some")
                .override(OS.WINDOWS, Architecture.x86, "some")
                .override(OS.WINDOWS, Architecture.x86_64, "some")
                .override(OS.MAC_OS_X, "some");

        redisServer = new RedisServer.Builder()
                .redisExecProvider(buggyProvider)
                .build();
    }
}
