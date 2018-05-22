package redis.embedded;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import redis.embedded.exceptions.RedisBuildingException;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class RedisServer extends AbstractRedisInstance {

    private Pattern redisReadyPat = RedisExecProvider.DEFAULT_REDIS_READY_PATTERN;

    private static final int DEFAULT_REDIS_PORT = 6379;

    public RedisServer() throws IOException {
        this(DEFAULT_REDIS_PORT);
    }

    public RedisServer(int port) throws IOException {
        super(port);
        File executable = RedisExecProvider.build().get();
        this.args = Arrays.asList(
                executable.getAbsolutePath(),
                "--port", Integer.toString(port)
        );
    }

    public RedisServer(RedisExecProvider redisExecProvider, int port) throws IOException {
        super(port);
        this.redisReadyPat = redisExecProvider.getExecutableStartPattern();
        this.args = Arrays.asList(
                redisExecProvider.get().getAbsolutePath(),
                "--port", Integer.toString(port)
        );
    }

    RedisServer(List<String> args, int port) {
        super(port);
        this.args = new ArrayList<>(args);
    }

    void setRedisReadyPat(Pattern redisReadyPat) {
        Preconditions.checkNotNull(redisReadyPat);
        this.redisReadyPat = redisReadyPat;
    }

    /**
     * Prefer using {@code new RedisServer.Builder()} directly.
     *
     * @return a new Builder for this class
     */
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
        private static final String CONF_FILENAME = "embedded-redis-server";
        private static final long DEFAULT_STARTUP_TIMEOUT_MS = 3000;

        private File executable;
        private RedisExecProvider redisExecProvider = RedisExecProvider.build();
        private int port = 6379;
        private InetSocketAddress slaveOf;

        private String redisConf;
        private StringBuilder redisConfigBuilder;
        private boolean logProcessOutput;
        private long startupTimeoutMs = DEFAULT_STARTUP_TIMEOUT_MS;

        public Builder redisExecProvider(RedisExecProvider redisExecProvider) {
            this.redisExecProvider = redisExecProvider;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder slaveOf(String hostname, int port) {
            this.slaveOf = new InetSocketAddress(hostname, port);
            return this;
        }

        public Builder slaveOf(InetSocketAddress slaveOf) {
            this.slaveOf = slaveOf;
            return this;
        }

        public Builder configFile(String redisConf) {
            if (redisConfigBuilder != null) {
                throw new RedisBuildingException("Redis configuration is already partially build using setting(String) method!");
            }
            this.redisConf = redisConf;
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

        @SuppressWarnings("Duplicates")
        public Builder setting(String configLine) {
            if (redisConf != null) {
                throw new RedisBuildingException("Redis configuration is already set using redis conf file!");
            }

            if (redisConfigBuilder == null) {
                redisConfigBuilder = new StringBuilder();
            }

            redisConfigBuilder.append(configLine);
            redisConfigBuilder.append(LINE_SEPARATOR);
            return this;
        }

        public RedisServer build() {
            tryResolveConfAndExec();
            List<String> args = buildCommandArgs();
            RedisServer redisServer = new RedisServer(args, port);
            redisServer.setLogProcessOutput(logProcessOutput);
            redisServer.setStartupTimeoutMs(startupTimeoutMs);
            redisServer.setRedisReadyPat(redisExecProvider.getExecutableStartPattern());
            return redisServer;
        }

        public void reset() {
            this.executable = null;
            this.redisConfigBuilder = null;
            this.slaveOf = null;
            this.redisConf = null;
        }

        public RedisServer.Builder copy() {
            Builder newBuilder = new Builder();

            newBuilder.executable = (executable != null)
                    ? new File(executable.toURI())
                    : null;
            newBuilder.redisExecProvider = redisExecProvider.copy();
            newBuilder.port = port;
            newBuilder.slaveOf = (slaveOf != null)
                    ? new InetSocketAddress(slaveOf.getAddress(), slaveOf.getPort())
                    : null;
            newBuilder.redisConf = redisConf;
            newBuilder.redisConfigBuilder = (redisConfigBuilder != null)
                    ? new StringBuilder(redisConfigBuilder)
                    : null;
            newBuilder.logProcessOutput = logProcessOutput;

            return newBuilder;
        }

        /**
         * @return the configuration built using {@code #setting(String)}.
         */
        public String settings() {
            return redisConfigBuilder.toString();
        }

        private void tryResolveConfAndExec() {
            try {
                resolveConfAndExec();
            } catch (IOException e) {
                throw new RedisBuildingException("Could not build server instance", e);
            }
        }

        private void resolveConfAndExec() throws IOException {
            if (redisConf == null && redisConfigBuilder != null) {
                File redisConfigFile = File.createTempFile(resolveConfigName(), ".conf");
                redisConfigFile.deleteOnExit();
                Files.write(redisConfigBuilder.toString(), redisConfigFile, Charset.forName("UTF-8"));
                redisConf = redisConfigFile.getAbsolutePath();
            }

            try {
                executable = redisExecProvider.get();
            } catch (Exception e) {
                throw new RedisBuildingException("Failed to resolve executable", e);
            }
        }

        private String resolveConfigName() {
            return CONF_FILENAME + "_" + port;
        }

        private List<String> buildCommandArgs() {
            List<String> args = new ArrayList<>();
            args.add(executable.getAbsolutePath());

            if (!Strings.isNullOrEmpty(redisConf)) {
                args.add(redisConf);
            }

            args.add("--port");
            args.add(Integer.toString(port));

            if (slaveOf != null) {
                args.add("--slaveof");
                args.add(slaveOf.getHostName());
                args.add(Integer.toString(slaveOf.getPort()));
            }

            return args;
        }
    }
}
