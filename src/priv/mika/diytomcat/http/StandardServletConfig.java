package priv.mika.diytomcat.http;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * ServletConfig 是在 Servlet 初始化的时候，传递进去的参数对象。
 * StandardServletConfig 实现了 ServletConfig 接口，提供对应的方法。
 */
public class StandardServletConfig implements ServletConfig {

    private ServletContext servletContext;
    private Map<String, String> initParameters;
    private String servletName;

    public StandardServletConfig(ServletContext servletContext, String servletName,
                                 Map<String, String> initParameters) {
        this.servletContext = servletContext;
        this.servletName = servletName;
        this.initParameters = initParameters;
        if (null == initParameters) {
            initParameters = new HashMap<>();
        }
    }

    @Override
    public String getServletName() {
        return servletName;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public String getInitParameter(String name) {
        return initParameters.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(initParameters.keySet());
    }
}
