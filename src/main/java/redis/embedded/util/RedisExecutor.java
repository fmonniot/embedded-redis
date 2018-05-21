package redis.embedded.util;

import java.util.regex.Pattern;

public class RedisExecutor {
    private String executableName;
    private Pattern startPattern;

    public RedisExecutor(String exeName, Pattern startPattern) {
        this.executableName = exeName;
        this.startPattern = startPattern;
    }

    public String getExecutableName() {
        return executableName;
    }

    public Pattern getStartPattern() {
        return startPattern;
    }

    public String getStartPatternAsString() {
        return startPattern.pattern();
    }

    public RedisExecutor copy() {
        return new RedisExecutor(executableName, startPattern);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RedisExecutor && ((RedisExecutor) obj).executableName.contentEquals(executableName) &&
                ((RedisExecutor)obj).startPattern.pattern().contentEquals(startPattern.pattern());
    }

    @Override
    public int hashCode() {
        return executableName.hashCode() + startPattern.pattern().hashCode();
    }
}
