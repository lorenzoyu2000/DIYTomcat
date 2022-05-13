package priv.mika.diytomcat.http;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import java.util.*;

public class StandardSession implements HttpSession {
    // 用于在 session 中存放数据的
    private Map<String, Object> attributesMap;
    // 当前 session 的唯一id
    private String id;
    // 创建时间
    private long creationTime;
    // 最后一次访问时间，用于对 session 自动失效。 一般默认是30分钟，如果不登录， session 就会自动失效了。
    private long lastAccessedTime;
    private ServletContext servletContext;
    // 最大持续时间的分钟数
    private int maxInactiveInterval;

    public StandardSession(String jsessionid, ServletContext servletContext) {
        this.attributesMap = new HashMap<>();
        this.id = jsessionid;
        this.creationTime = System.currentTimeMillis();
        this.servletContext = servletContext;
    }

    @Override
    public long getCreationTime() {
        return this.creationTime;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    public void setLastAccessedTime(long lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public void setMaxInactiveInterval(int maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    @Override
    public int getMaxInactiveInterval() {
        return this.maxInactiveInterval;
    }

    @Override
    public HttpSessionContext getSessionContext() {
        return null;
    }

    @Override
    public Object getAttribute(String name) {
        return attributesMap.get(name);
    }

    @Override
    public Object getValue(String arg0) {
        return null;
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        Set<String> keys = attributesMap.keySet();
        return Collections.enumeration(keys);
    }

    @Override
    public String[] getValueNames() {
        return new String[0];
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributesMap.put(name, value);
    }

    @Override
    public void putValue(String s, Object o) {

    }

    @Override
    public void removeAttribute(String name) {
        attributesMap.remove(name);
    }

    @Override
    public void removeValue(String s) {

    }

    @Override
    public void invalidate() {
        attributesMap.clear();
    }

    @Override
    public boolean isNew() {
        return creationTime == lastAccessedTime;
    }
}
