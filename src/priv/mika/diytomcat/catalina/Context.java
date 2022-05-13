package priv.mika.diytomcat.catalina;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.LogFactory;
import org.apache.jasper.JspC;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;
import priv.mika.diytomcat.classloader.WebappClassLoader;
import priv.mika.diytomcat.exception.WebConfigDuplicatedException;
import priv.mika.diytomcat.http.ApplicationContext;
import priv.mika.diytomcat.http.StandardServletConfig;
import priv.mika.diytomcat.util.Constant;
import priv.mika.diytomcat.util.ContextXMLUtil;
import priv.mika.diytomcat.watcher.ContextFileChangeWatcher;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * 代表一个应用
 */
public class Context {
    // 访问时输入的web项目名，文件所在目录名加上 /
    private String path;
    // 站点目录的绝对路径
    private String docBase;
    // 对应 XXX/WB-INF/web.xml 文件
    private File contextWebXmlFile;

    /**
     * @Description servlet 的映射为WB-INF/web.xml中的配置
     * 1. 地址对应 Servlet 的类名
     * 2. 地址对应 Servlet 的名称
     * 3. Servlet 的名称对应类全名
     * 4. Servlet 类名对应名称
     * url_servletClassName :{/hello=priv.mika.diytomcat.webappservlet.HelloServlet}
     * url_servletName :{/hello=HelloServlet}
     * servletName_className :{HelloServlet=priv.mika.diytomcat.webappservlet.HelloServlet}
     * className_servletName :{priv.mika.diytomcat.webappservlet.HelloServlet=HelloServlet}
     */
    private Map<String, String> url_servletClassName;
    private Map<String, String> url_servletName;
    private Map<String, String> servletName_className;
    private Map<String, String> className_servletName;

    // 一个Web应用，应该有一个自己独立的 WebappClassLoader
    private WebappClassLoader webappClassLoader;

    private Host host;
    private boolean reloadable;
    private ContextFileChangeWatcher contextFileChangeWatcher;

    private ServletContext servletContext;
    // 存放JavaWeb下的初始化信息
    private Map<String, Map<String, String>> servlet_className_init_params;
    // 用于存放哪些类需要做自启动
    private List<String> loadOnStartupServletClassNames;
    // 和 Filter 相关的配置
    private Map<String, List<String>> url_filterClassName;
    private Map<String, List<String>> url_FilterNames;
    private Map<String, String> filterName_className;
    private Map<String, String> className_filterName;
    private Map<String, Map<String, String>> filter_className_init_params;

    private Map<String, Filter> filterPool;

    private List<ServletContextListener> listeners;

    /**
     * 如果修改了 HelloServlet 导致 context 自动重载，那么访问 /javaweb/hello 这个地址，拿到的还是原来的对象吗？
     * 修改了 HelloServlet，monitor监听器检测到HelloServlet文件发生了变化，context对象的reload()方法被调用，导致host的reload()调用，
     * reload()方法将contextMap中的原来的context移除，
     * 再新建一个context对象添加到contextMap中（其实path和docBase参数同旧的context一样），即重载（重新新建了一个context）。
     * 由于context是新的，因此它的servletPool已经不是原来的了，
     * 而是新的空servletPool，再次访问同一个地址时，将重新put一个新的servlet对象。所以答案是拿到的不是原来的对象了。
     */
    private Map<Class<?>, HttpServlet> servletPool;

    public Context(String path, String docBase, Host host, boolean reloadable) {
        TimeInterval timeInterval = DateUtil.timer();
        this.path = path;
        this.docBase = docBase;
        this.contextWebXmlFile = new File(docBase, ContextXMLUtil.getWatchedResource());

        this.url_servletClassName = new HashMap<>();
        this.url_servletName = new HashMap<>();
        this.servletName_className = new HashMap<>();
        this.className_servletName = new HashMap<>();

        this.servletContext = new ApplicationContext(this);
        this.servletPool = new HashMap<>();
        this.servlet_className_init_params = new HashMap<>();

        this.loadOnStartupServletClassNames = new ArrayList<>();

        this.url_filterClassName = new HashMap<>();
        this.url_FilterNames = new HashMap<>();
        this.filterName_className = new HashMap<>();
        this.className_filterName = new HashMap<>();
        this.filter_className_init_params = new HashMap<>();

        this.filterPool = new HashMap<>();

        listeners=new ArrayList<ServletContextListener>();

        // 这里的 Thread.currentThread().getContextClassLoader() 就可以获取到 Bootstrap 里通过
        // Thread.currentThread().setContextClassLoader(commonClassLoader); 设置的 commonClassLoader.
        ClassLoader commonClassLoader = Thread.currentThread().getContextClassLoader();
        this.webappClassLoader = new WebappClassLoader(docBase, commonClassLoader);

        this.host = host;
        this.reloadable = reloadable;

        LogFactory.get().info("Deploying web application directory {}", this.docBase);
        deploy();
        LogFactory.get().info("Deployment of web application directory {} has finished in {} ms", this.docBase, timeInterval.intervalMs());

    }

    /**
     * 解析哪些类需要做自启动
     */
    public void parseLoadOnStartup(Document document) {
        Elements elements = document.select("load-on-startup");
        for (Element e : elements) {
            String loadOnStartupServletClassName = e.parent().select("servlet-class").text();
            loadOnStartupServletClassNames.add(loadOnStartupServletClassName);
        }
    }

    /**
     * 对这些类做自启动
     */
    public void handleLoadOnStartup() {
        for (String loadOnStartupServletClassName: loadOnStartupServletClassNames) {
            try {
                Class<?> clazz = webappClassLoader.loadClass(loadOnStartupServletClassName);
                getHttpServlet(clazz);
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | ServletException e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * 从 JavaWeb web.xml 中解析初始化参数
     */
    private void parseServletInitParams(Document document) {
        Elements servletClassNameElements = document.select("servlet-class");
        for (Element servletClassNameElement : servletClassNameElements) {
            String servletClassName = servletClassNameElement.text();
            Elements initElements = servletClassNameElement.parent().select("init-param");
            if (initElements.isEmpty())
                continue;
            Map<String, String> initParams = new HashMap<>();
            for (Element element : initElements) {
                String name = element.select("param-name").get(0).text();
                String value = element.select("param-value").get(0).text();
                initParams.put(name, value);
            }
            servlet_className_init_params.put(servletClassName, initParams);
        }
		System.out.println("class_name_init_params:" + servlet_className_init_params);
    }

    /**
     * 根据类对象来获取 servlet 对象
     */
    public synchronized HttpServlet getHttpServlet(Class<?> clazz) throws IllegalAccessException, InstantiationException, ServletException {
        HttpServlet httpServlet = servletPool.get(clazz);
        if (null == httpServlet) {
            httpServlet = (HttpServlet) clazz.newInstance();
            ServletContext servletContext = this.getServletContext();
            String className = clazz.getName();
            String servletName = className_servletName.get(className);
            Map<String, String> initParameters = servlet_className_init_params.get(className);
            ServletConfig servletConfig = new StandardServletConfig(servletContext, servletName, initParameters);
            httpServlet.init();
            servletPool.put(clazz, httpServlet);
        }
        return httpServlet;
    }

    public void destroyServlets() {
        Collection<HttpServlet> httpServlets = servletPool.values();
        for (HttpServlet servlet : httpServlets) {
            servlet.destroy();
        }
    }

    /**
     *   parseServletMapping 方法，把这些信息从 web.xml 中解析出来
     */
    private void parseServletMapping(Document document) {

        // url_ServletName
        Elements mappingurlElements = document.select("servlet-mapping url-pattern");
        for (Element mappingurlElement : mappingurlElements) {
            String urlPattern = mappingurlElement.text();
            String servletName = mappingurlElement.parent().select("servlet-name").first().text();
            url_servletName.put(urlPattern, servletName);
        }
        // servletName_className / className_servletName
        Elements servletNameElements = document.select("servlet servlet-name");
        for (Element servletNameElement : servletNameElements) {
            String servletName = servletNameElement.text();
            String servletClass = servletNameElement.parent().select("servlet-class").first().text();
            servletName_className.put(servletName, servletClass);
            className_servletName.put(servletClass, servletName);
        }
        // url_servletClassName
        Set<String> urls = url_servletName.keySet();
        for (String url : urls) {
            String servletName = url_servletName.get(url);
            String servletClassName = servletName_className.get(servletName);
            url_servletClassName.put(url, servletClassName);
        }
    }
    /**
     *   判断是否有重复，如果有就会抛出 WebConfigDuplicatedException 异常
     */
    private void checkDuplicated(Document document, String mapping, String desc) throws WebConfigDuplicatedException {
        Elements elements = document.select(mapping);
        // 判断逻辑是放入一个集合，然后把集合排序之后看两临两个元素是否相同
        List<String> contents = new ArrayList<>();
        for (Element element : elements) {
            contents.add(element.text());
        }

        Collections.sort(contents);

        for (int i = 0; i < contents.size() - 1; i++) {
            String contextCur = contents.get(i);
            String contextNext = contents.get(i + 1);
            if (contextCur.equals(contextNext)) {
                throw new WebConfigDuplicatedException(StrUtil.format(desc, contextCur));
            }
        }
    }

    private void checkDuplicated() throws WebConfigDuplicatedException {
        String xml = FileUtil.readUtf8String(Constant.contextXmlFile);
        Document document = Jsoup.parse(xml);
        checkDuplicated(document, "servlet-mapping url-pattern", "servlet url 重复,请保持其唯一性:{} ");
        checkDuplicated(document, "servlet servlet-name", "servlet 名称重复,请保持其唯一性:{} ");
        checkDuplicated(document, "servlet servlet-class", "servlet 类名重复,请保持其唯一性:{} ");
    }
    /**
     *  初始化方法
     *  先判断是否有 web.xml 文件，如果没有就返回了
     *  然后判断是否重复
     *  接着进行 web.xml 的解析
     */
    private void init() {
        if (!contextWebXmlFile.exists()) {
            return;
        }
        try {
            checkDuplicated();
        } catch (WebConfigDuplicatedException e) {
            e.printStackTrace();
            return;
        }
        String xml = FileUtil.readUtf8String(contextWebXmlFile);
        Document document = Jsoup.parse(xml);
        parseServletMapping(document);
        parseFilterMapping(document);
        parseFilterInitParams(document);
        parseServletInitParams(document);

        initFilter();

        parseLoadOnStartup(document);
        handleLoadOnStartup();

        fireEvent("init");
    }

    private void deploy() {
        loadListeners();
        init();
        if (reloadable) {
            contextFileChangeWatcher = new ContextFileChangeWatcher(this);
            contextFileChangeWatcher.start();
        }
        /**
         * 这里进行了JspRuntimeContext 的初始化，就是为了能够在jsp所转换的 java 文件里的
         * javax.servlet.jsp.JspFactory.getDefaultFactory() 这行能够有返回值
         */
        JspC c = new JspC();
        new JspRuntimeContext(servletContext, c);
    }

    public void stop() {
        webappClassLoader.stop();
        contextFileChangeWatcher.stop();
        destroyServlets();

        fireEvent("destroy");
    }
    
    /**
     * parseFilterMapping 方法，解析 web.xml 里面的 Filter 信息
     */
    public void parseFilterMapping(Document d) {
        // filter_url_name
        Elements mappingurlElements = d.select("filter-mapping url-pattern");
        for (Element mappingurlElement : mappingurlElements) {
            String urlPattern = mappingurlElement.text();
            String filterName = mappingurlElement.parent().select("filter-name").first().text();

            List<String> filterNames= url_FilterNames.get(urlPattern);
            if(null==filterNames) {
                filterNames = new ArrayList<>();
                url_FilterNames.put(urlPattern, filterNames);
            }
            filterNames.add(filterName);
        }
        // class_name_filter_name
        Elements filterNameElements = d.select("filter filter-name");
        for (Element filterNameElement : filterNameElements) {
            String filterName = filterNameElement.text();
            String filterClass = filterNameElement.parent().select("filter-class").first().text();
            filterName_className.put(filterName, filterClass);
            className_filterName.put(filterClass, filterName);
        }
        // url_filterClassName

        Set<String> urls = url_FilterNames.keySet();
        for (String url : urls) {
            List<String> filterNames = url_FilterNames.get(url);
            if(null == filterNames) {
                filterNames = new ArrayList<>();
                url_FilterNames.put(url, filterNames);
            }
            for (String filterName : filterNames) {
                String filterClassName = filterName_className.get(filterName);
                List<String> filterClassNames = url_filterClassName.get(url);
                if(null==filterClassNames) {
                    filterClassNames = new ArrayList<>();
                    url_filterClassName.put(url, filterClassNames);
                }
                filterClassNames.add(filterClassName);
            }
        }
    }

    /**
     * parseFilterInitParams 方法用于解析参数信息
     */
    private void parseFilterInitParams(Document d) {
        Elements filterClassNameElements = d.select("filter-class");
        for (Element filterClassNameElement : filterClassNameElements) {
            String filterClassName = filterClassNameElement.text();

            Elements initElements = filterClassNameElement.parent().select("init-param");
            if (initElements.isEmpty())
                continue;

            Map<String, String> initParams = new HashMap<>();

            for (Element element : initElements) {
                String name = element.select("param-name").get(0).text();
                String value = element.select("param-value").get(0).text();
                initParams.put(name, value);
            }

            filter_className_init_params.put(filterClassName, initParams);

        }
    }

    /**
     * 准备 initFilter 用于初始化Filter
     */
    private void initFilter() {
        Set<String> classNames = className_filterName.keySet();
        for (String className : classNames) {
            try {
                Class clazz =  this.getWebappClassLoader().loadClass(className);
                Map<String,String> initParameters = filter_className_init_params.get(className);
                String filterName = className_filterName.get(className);
                FilterConfig filterConfig = new StandardFilterConfig(servletContext, filterName, initParameters);
                Filter filter = filterPool.get(clazz);
                if(null==filter) {
                    filter = (Filter) ReflectUtil.newInstance(clazz);
                    filter.init(filterConfig);
                    filterPool.put(className, filter);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    /**
     * 三种匹配模式
     */
    private boolean match(String pattern, String uri) {
        // 完全匹配
        if(StrUtil.equals(pattern, uri)) {
            return true;
        }

        // /* 模式
        if(StrUtil.equals(pattern, "/*")) {
            return true;
        }

        // 后缀名 /*.jsp
        if(StrUtil.startWith(pattern, "/*.")) {
            String patternExtName = StrUtil.subAfter(pattern, '.', false);
            String uriExtName = StrUtil.subAfter(uri, '.', false);
            if(StrUtil.equals(patternExtName, uriExtName))
                return true;
        }
        // 其他模式就懒得管了
        return false;
    }

    /**
     * 获取匹配了的过滤器集合
     */
    public List<Filter> getMatchedFilters(String uri) {
        List<Filter> filters = new ArrayList<>();
        Set<String> patterns = url_filterClassName.keySet();
        Set<String> matchedPatterns = new HashSet<>();

        for (String pattern : patterns) {
            if(match(pattern,uri)) {
                matchedPatterns.add(pattern);
            }
        }
        Set<String> matchedFilterClassNames = new HashSet<>();
        for (String pattern : matchedPatterns) {
            List<String> filterClassName = url_filterClassName.get(pattern);
            matchedFilterClassNames.addAll(filterClassName);
        }
        for (String filterClassName : matchedFilterClassNames) {
            Filter filter = filterPool.get(filterClassName);
            filters.add(filter);
        }
        return filters;
    }

    public void addListener(ServletContextListener listener){
        listeners.add(listener);
    }

    /**
     * 从web.xml 中扫描监听器类
     */
    private void loadListeners() {
        try {
            if (!contextWebXmlFile.exists()) {
                return;
            }
            String xml = FileUtil.readUtf8String(contextWebXmlFile);
            Document document = Jsoup.parse(xml);
            Elements elements = document.select("listener listener-class");

            for (Element element : elements) {
                String listenerClassName = element.text();
                Class<?> clazz = this.getWebappClassLoader().loadClass(listenerClassName);
                ServletContextListener listener = (ServletContextListener)clazz.newInstance();
                addListener(listener);
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void fireEvent(String type) {
        ServletContextEvent event = new ServletContextEvent(servletContext);
        for (ServletContextListener servletContextListener : listeners) {
            if ("init".equals(type)) {
                servletContextListener.contextInitialized(event);
            }
            if ("destroy".equals(type)) {
                servletContextListener.contextDestroyed(event);
            }
        }
    }

    public void reload() {
        host.reload(this);
    }

    public String getServletClassName(String uri) {
        return url_servletClassName.get(uri);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDocBase() {
        return docBase;
    }

    public void setDocBase(String docBase) {
        this.docBase = docBase;
    }

    public WebappClassLoader getWebappClassLoader() {
        return webappClassLoader;
    }

    public boolean isReloadable() {
        return reloadable;
    }

    public void setReloadable(boolean reloadable) {
        this.reloadable = reloadable;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
        public String toString() {
            return "Context{" +
                    "path='" + path + '\'' +
                    ", docBase='" + docBase + '\'' +
                    '}';
        }
}
