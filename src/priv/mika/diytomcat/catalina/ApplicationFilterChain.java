package priv.mika.diytomcat.catalina;

import cn.hutool.core.util.ArrayUtil;
import javafx.collections.transformation.FilteredList;
import priv.mika.diytomcat.http.ApplicationRequestDispatcher;

import javax.servlet.*;
import java.io.IOException;
import java.util.List;
/**
 * 实现的功能就是 chain demo 中的 FilterChain 的功能。
 *
 * 过滤器责任链对象：
 * filters: 过滤器数组
 * servlet: 执行业务的 servlet
 * pos: 当前正在使用的过滤器
 *
 */
public class ApplicationFilterChain implements FilterChain {
    private Filter[] filters;
    private Servlet servlet;
    int pos;

    public ApplicationFilterChain(List<Filter> filterList, Servlet servlet) {
        this.filters = ArrayUtil.toArray(filterList, Filter.class);
        this.servlet = servlet;
    }
    /**
     * 挨个执行所有的过滤器，执行结束之后，执行 servlet
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        if (pos < filters.length) {
            Filter filter = filters[pos++];
            filter.doFilter(request, response, this);
        } else {
            servlet.service(request, response);
        }
    }
}
