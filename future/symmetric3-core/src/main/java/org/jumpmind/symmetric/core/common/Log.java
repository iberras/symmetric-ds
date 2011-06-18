package org.jumpmind.symmetric.core.common;

abstract public class Log {

    protected Class<?> clazz;

    public abstract void log(LogLevel level, Throwable error, String msg, Object... params);

    public void log(LogLevel level, String msg, Object... params) {
        log(level, null, msg, params);
    }

    public void log(LogLevel level, Throwable error) {
        log(level, error, null);
    }

    public void debug(String msg, Object... params) {
        log(LogLevel.DEBUG, msg, params);
    }

    public void info(String msg, Object... params) {
        log(LogLevel.INFO, msg, params);
    }

    public void error(String msg, Object... params) {
        log(LogLevel.ERROR, msg, params);
    }

    public void error(Throwable ex) {
        log(LogLevel.ERROR, ex);
    }

    public abstract boolean isDebugEnabled();

    protected void initialize(Class<?> clazz) {
        this.clazz = clazz;
    }

}
