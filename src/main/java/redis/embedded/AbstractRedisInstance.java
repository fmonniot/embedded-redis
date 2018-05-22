package redis.embedded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.embedded.cluster.RedisCluster;
import redis.embedded.exceptions.EmbeddedRedisException;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.apache.commons.io.IOUtils.closeQuietly;

abstract class AbstractRedisInstance implements Redis {

    private static final Logger logger = LoggerFactory.getLogger(RedisCluster.class);

    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_MS = 10000;

    List<String> args = Collections.emptyList();
    private State state = new State();
    private Process redisProcess;
    private final int port;
    private boolean logProcessOutput;
    private long startupTimeoutMs = 3000;

    private LogsInputStream logsInput = new LogsInputStream();

    AbstractRedisInstance(int port) {
        this.port = port;
    }

    @Override
    public boolean isActive() {
        return state.isActive();
    }

    @Override
    public void start() throws EmbeddedRedisException {
        if (state.setActivating()) {
            try {
                redisProcess = createRedisProcessBuilder().start();

                awaitRedisServerReady();
            } catch (IOException | InterruptedException e) {
                throw new EmbeddedRedisException("Failed to start Redis instance", e);
            }
        } else {
            throw new EmbeddedRedisException("This redis server instance is already running...");
        }
    }

    @Override
    public InputStream errors() {
        return logsInput;
    }

    private void awaitRedisServerReady() throws IOException, InterruptedException {
        if (!state.isActivating()) {
            throw new RuntimeException("Internal error. State is not activating in await: " + state);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(redisProcess.getInputStream()));
        ScheduledExecutorService executor = setupStartupTimeoutCheck();
        try {
            // Wait 200ms to be sure redis have started (it can takes some time and that would cause
            // the exception below to throw.
            Thread.sleep(200);

            String outputLine;
            while (true) {
                outputLine = reader.readLine();
                if (outputLine == null) {
                    //Something goes wrong. Stream is ended before server was activated.
                    //Set state to failed
                    if (state.setFailed()) {
                        logger.error("Invalid state during wait: {}", state);
                        break;
                    } else {
                        throw new RuntimeException("Can't start redis server. Check logs for details.");
                    }
                }
                logsInput.appendLine(outputLine);
                if (logProcessOutput) {
                    logger.info(outputLine);
                }
                if (redisReadyPattern().matcher(outputLine).find()) {
                    if (!state.setActive()) {
                        throw new RuntimeException("Redis server was deactivated before receiving ready pattern log");
                    }
                    break;
                }
            }
        } finally {
            closeQuietly(reader);
            cancelStartupTimeoutCheck(executor);
        }
    }

    private ScheduledExecutorService setupStartupTimeoutCheck() {
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.schedule(new Runnable() {
            @Override
            public void run() {
                if (state.setDeactivatingByTimeout()) {
                    doStop();
                }
            }
        }, startupTimeoutMs, TimeUnit.MILLISECONDS);
        return scheduledExecutorService;
    }

    private void cancelStartupTimeoutCheck(ScheduledExecutorService executor) {
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                logger.warn("Error waiting for startup timeout check executor termination", e);
            }
        }
    }

    protected abstract Pattern redisReadyPattern();

    private ProcessBuilder createRedisProcessBuilder() {
        File executable = new File(args.get(0));
        ProcessBuilder pb = new ProcessBuilder(args)
                .directory(executable.getParentFile())
                .redirectErrorStream(true);
        return pb;
    }

    @Override
    public void stop() throws EmbeddedRedisException {
        if (state.setDeactivating()) {
            doStop();
            if (!state.setInactive()) {
                throw new RuntimeException("Internal error. Can't set redis state to inactiveState from " + state);
            }
        } else {
            logger.debug("Attempt to stop reddis while in {}", state);
        }
    }

    private void doStop() throws EmbeddedRedisException {
        if (!state.isDeactivating()) {
            throw new RuntimeException("Internal error. Can't stop redis because it's not in deactivating state. But " + state);
        }
        redisProcess.destroy();
        tryWaitFor();
    }

    private void tryWaitFor() {
        try {
            redisProcess.waitFor();
        } catch (InterruptedException e) {
            throw new EmbeddedRedisException("Failed to stop redis instance", e);
        }
    }

    @Override
    public List<Integer> ports() {
        return Collections.singletonList(port);
    }

    void setLogProcessOutput(boolean logProcessOutput) {
        this.logProcessOutput = logProcessOutput;
    }

    void setStartupTimeoutMs(long startupTimeoutMs) {
        this.startupTimeoutMs = startupTimeoutMs;
    }

    RedisState getState() {
        return state.getState();
    }
}
