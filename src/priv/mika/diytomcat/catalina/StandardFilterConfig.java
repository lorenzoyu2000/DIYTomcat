package priv.mika.diytomcat.catalina;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
/**
 * StandardFilterConfig 的主要作用是存放Filter 的初始化参数
 */
public class StandardFilterConfig implements FilterConfig {
    private ServletContext servletContext;
    private Map<String, String> initParameters;
    private String filterName;

    public StandardFilterConfig(ServletContext servletContext, String filterName,
                                Map<String, String> initParameters) {
        this.servletContext = servletContext;
        this.filterName = filterName;
        this.initParameters = initParameters;
        if (null == this.initParameters)
            this.initParameters = new HashMap<>();
    }

    @Override
    public String getInitParameter(String name) {
        return initParameters.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(initParameters.keySet());
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public String getFilterName() {
        return filterName;
    }
}
