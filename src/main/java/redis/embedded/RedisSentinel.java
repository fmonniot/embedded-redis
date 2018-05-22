package redis.embedded;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import redis.embedded.exceptions.RedisBuildingException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RedisSentinel extends AbstractRedisInstance {
    private Pattern redisReadyPat = RedisExecProvider.DEFAULT_REDIS_READY_PATTERN;

    public RedisSentinel(List<String> args, int port) {
        super(port);
        this.args = new ArrayList<>(args);
    }

    void setRedisReadyPat(Pattern redisReadyPat) {
        Preconditions.checkNotNull(redisReadyPat);
        this.redisReadyPat = redisReadyPat;
    }

    @Deprecated
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Pattern redisReadyPattern() {
        return redisReadyPat;
    }

    @SuppressWarnings("unused")
    public static class Builder {
        private static final String LINE_SEPARATOR = System.getProperty("line.separator");
        private static final String CONF_FILENAME = "embedded-redis-sentinel";
        private static final String MASTER_MONITOR_LINE = "sentinel monitor %s 127.0.0.1 %d %d";
        private static final String DOWN_AFTER_LINE = "sentinel down-after-milliseconds %s %d";
        private static final String FAIL_OVER_LINE = "sentinel failover-timeout %s %d";
        private static final String PARALLEL_SYNCS_LINE = "sentinel parallel-syncs %s %d";
        private static final String PORT_LINE = "port %d";
        private static final long DEFAULT_STARTUP_TIMEOUT_MS = 3000;

        private File executable;
        private RedisExecProvider redisExecProvider = RedisExecProvider.defaultProvider();
        private Integer port = 26379;
        private int masterPort = 6379;
        private String masterName = "myMaster";
        private long downAfterMilliseconds = 60000L;
        private long failOverTimeout = 180000L;
        private int parallelSyncs = 1;
        private int quorumSize = 1;
        private String sentinelConf;

        private StringBuilder redisConfigBuilder;
        private boolean logProcessOutput;
        private long startupTimeoutMs = DEFAULT_STARTUP_TIMEOUT_MS;

        public Builder redisExecProvider(RedisExecProvider redisExecProvider) {
            this.redisExecProvider = redisExecProvider;
            return this;
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public Builder masterPort(Integer masterPort) {
            this.masterPort = masterPort;
            return this;
        }

        public Builder masterName(String masterName) {
            this.masterName = masterName;
            return this;
        }

        public Builder quorumSize(int quorumSize) {
            this.quorumSize = quorumSize;
            return this;
        }

        public Builder downAfterMilliseconds(Long downAfterMilliseconds) {
            this.downAfterMilliseconds = downAfterMilliseconds;
            return this;
        }

        public Builder failOverTimeout(Long failoverTimeout) {
            this.failOverTimeout = failoverTimeout;
            return this;
        }

        public Builder parallelSyncs(int parallelSyncs) {
            this.parallelSyncs = parallelSyncs;
            return this;
        }

        public Builder configFile(String redisConf) {
            if (redisConfigBuilder != null) {
                throw new RedisBuildingException("Redis configuration is already partially build using setting(String) method!");
            }
            this.sentinelConf = redisConf;
            return this;
        }

        @SuppressWarnings("Duplicates")
        public Builder setting(String configLine) {
            if (sentinelConf != null) {
                throw new RedisBuildingException("Redis configuration is already set using redis conf file!");
            }

            if (redisConfigBuilder == null) {
                redisConfigBuilder = new StringBuilder();
            }

            redisConfigBuilder.append(configLine);
            redisConfigBuilder.append(LINE_SEPARATOR);
            return this;
        }

        public Builder logProcessOutput() {
            this.logProcessOutput = true;
            return this;
        }

        public Builder startupTimeoutMs(long startupTimeoutMs) {
            this.startupTimeoutMs = startupTimeoutMs;
            return this;
        }

        public RedisSentinel build() {
            tryResolveConfAndExec();
            List<String> args = buildCommandArgs();
            RedisSentinel redisSentinel = new RedisSentinel(args, port);
            redisSentinel.setLogProcessOutput(logProcessOutput);
            redisSentinel.setStartupTimeoutMs(startupTimeoutMs);
            redisSentinel.setRedisReadyPat(redisExecProvider.getExecutableStartPattern());
            return redisSentinel;
        }

        private void tryResolveConfAndExec() {
            try {
                if (sentinelConf == null) {
                    resolveSentinelConf();
                }
                executable = redisExecProvider.get();
            } catch (Exception e) {
                throw new RedisBuildingException("Could not build sentinel instance", e);
            }
        }

        public void reset() {
            this.redisConfigBuilder = null;
            this.sentinelConf = null;
        }

        public void addDefaultReplicationGroup() {
            setting(String.format(MASTER_MONITOR_LINE, masterName, masterPort, quorumSize));
            setting(String.format(DOWN_AFTER_LINE, masterName, downAfterMilliseconds));
            setting(String.format(FAIL_OVER_LINE, masterName, failOverTimeout));
            setting(String.format(PARALLEL_SYNCS_LINE, masterName, parallelSyncs));
        }

        private void resolveSentinelConf() throws IOException {
            if (redisConfigBuilder == null) {
                addDefaultReplicationGroup();
            }
            setting(String.format(PORT_LINE, port));
            final String configString = redisConfigBuilder.toString();

            File redisConfigFile = File.createTempFile(resolveConfigName(), ".conf");
            redisConfigFile.deleteOnExit();
            Files.write(configString, redisConfigFile, Charset.forName("UTF-8"));
            sentinelConf = redisConfigFile.getAbsolutePath();
        }

        private String resolveConfigName() {
            return CONF_FILENAME + "_" + port;
        }

        private List<String> buildCommandArgs() {
            Preconditions.checkNotNull(sentinelConf);

            List<String> args = new ArrayList<>();
            args.add(executable.getAbsolutePath());
            args.add(sentinelConf);
            args.add("--sentinel");

            if (port != null) {
                args.add("--port");
                args.add(Integer.toString(port));
            }

            return args;
        }
    }
}
