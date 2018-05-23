package redis.embedded.util;

import java.util.regex.Pattern;

public class RedisExecutable {
    private String executableName;
    private Pattern startPattern;

    public static RedisExecutable build(String execName, Pattern startPattern) {
        return new RedisExecutable(execName, startPattern);
    }

    private RedisExecutable(String exeName, Pattern startPattern) {
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

    public RedisExecutable copy() {
        return new RedisExecutable(executableName, startPattern);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RedisExecutable && ((RedisExecutable) obj).executableName.contentEquals(executableName) &&
                ((RedisExecutable)obj).startPattern.pattern().contentEquals(startPattern.pattern());
    }

    @Override
    public int hashCode() {
        return executableName.hashCode() + startPattern.pattern().hashCode();
    }
}
