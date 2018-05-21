package redis.embedded;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import redis.embedded.util.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

public class RedisExecProvider {

    private final Map<OsArchitecture, RedisExecutor> executables = Maps.newHashMap();
    public final static Pattern REDIS_READY_PATTERN = Pattern.compile(
            "(?:The server is now ready to accept connections on port)" +   // 3.2.1, 2.8.24
                    "|(?:Ready to accept connections)"  + // 4.0.2
                    "|(?:Sentinel ID is)" +  // 3.2.1, 4.0.2
                    "|(?:Sentinel runid is)" // 2.8.24
    );

    /**
     * @return a new RedisExecProvider instance
     * @deprecated use {@link #build()} instead
     */
    @Deprecated
    public static RedisExecProvider defaultProvider() {
        return new RedisExecProvider();
    }

    public static RedisExecProvider build() {
        return new RedisExecProvider();
    }

    private RedisExecProvider() {
        initExecutables();
    }

    private void initExecutables() {
        executables.put(OsArchitecture.UNIX_x86_64, new RedisExecutor("redis-server-3.0.7", REDIS_READY_PATTERN));

        executables.put(OsArchitecture.MAC_OS_X_x86, new RedisExecutor("redis-server-3.0.7-darwin", REDIS_READY_PATTERN));
        executables.put(OsArchitecture.MAC_OS_X_x86_64, new RedisExecutor("redis-server-3.0.7-darwin", REDIS_READY_PATTERN));
    }

    public RedisExecProvider override(OS os, String executable) {
        Preconditions.checkNotNull(executable);
        for (Architecture arch : Architecture.values()) {
            override(os, arch, executable);
        }
        return this;
    }

    public RedisExecProvider override(OS os, Architecture arch, String executable) {
        Preconditions.checkNotNull(executable);
        executables.put(new OsArchitecture(os, arch), new RedisExecutor(executable, REDIS_READY_PATTERN));
        return this;
    }

    public RedisExecProvider override(OS os, Architecture arch, RedisExecutor redisExec) {
        Preconditions.checkNotNull(redisExec);
        executables.put(new OsArchitecture(os, arch), redisExec);
        return this;
    }

    public File get() throws IOException {
        OsArchitecture osArch = OsArchitecture.detect();
        String executablePath = executables.get(osArch).getExecutableName();
        return fileExists(executablePath) ?
                new File(executablePath) :
                JarUtil.extractExecutableFromJar(executablePath);

    }

    public Pattern getExecutableStartPattern() {
        OsArchitecture osArch = OsArchitecture.detect();
        return executables.get(osArch).getStartPattern();
    }

    public RedisExecProvider copy() {
        RedisExecProvider copy = new RedisExecProvider();

        for (OsArchitecture k : executables.keySet()) {
            copy.override(k.os(), k.arch(), executables.get(k));
        }

        return copy;
    }

    private boolean fileExists(String executablePath) {
        return new File(executablePath).exists();
    }
}
