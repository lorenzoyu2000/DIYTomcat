package priv.mika.diytomcat.http;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import priv.mika.diytomcat.Bootstrap;
import priv.mika.diytomcat.catalina.*;
import priv.mika.diytomcat.util.MiniBrowser;
import sun.util.locale.LocaleUtils;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.Principal;
import java.util.*;

public class Request extends BaseRequest {
    private String requestString;
    private String uri;
    private Socket socket;
    private Context context;

    private String method;
    // 查询字符串
    private String queryString;
    // 用于存放头信息
    private Map<String, String> headerMap;

    private Cookie[] cookies;

    private HttpSession session;

    private Connector connector;

    private boolean forwarded;
    // 用于存放参数
    private Map<String, Object> attributesMap;

    /**
     * 参数Map
     * 默认是一个 name 对应多个value。只是常见情况是只对应一个，可是设计的时候，考虑兼容性需要设计为对应多个。
     */
    private Map<String, String[]> parameterMap;

    public Request (Socket socket, Connector connector) throws IOException {
        this.socket = socket;
        this.parameterMap = new HashMap<>();
        this.headerMap = new HashMap<>();
        this.connector = connector;
        this.attributesMap = new HashMap<>();

        parseHttpRequest();
        if (StrUtil.isEmpty(requestString)) {
            return;
        }
        parseUri();
        parseContext();
        parseMethod();

        if (!"/".equals(context.getPath())) {
            uri = StrUtil.removePrefix(uri, context.getPath());
            if (StrUtil.isEmpty(uri)) {
                uri = "/";
            }
        }
        parseParameters();
        parseHeaders();
        parseCookies();
        System.out.println(requestString);
    }

    /**
     * 解析Context的方法，通过获取uri中的信息来得到path。然后根据这个 path 来获取 Context 对象。
     */
    private void parseContext() {
        Service service = connector.getService();
        Engine engine = service.getEngine();
        context = engine.getDefaultHost().getContext(uri);

        if (null != context) {
            return;
        }

        String path = StrUtil.subBetween(uri, "/");

        if (null == path) {
            path = "/";
        } else {
            path = "/" + path;
        }

        context = engine.getDefaultHost().getContext(path);
        if (null == context) {
            context = engine.getDefaultHost().getContext("/");
        }
    }

    /**
     * 解析 http请求字符串，这里面就调用了 MiniBrowser里重构的 readBytes 方法。
     */
    private void parseHttpRequest() throws IOException {
        InputStream is = this.socket.getInputStream();
        byte[] bytes = MiniBrowser.readBytes(is, false);
        requestString = new String(bytes, "utf-8");
    }

    /**
     * 解析传来的uri
     */
    private void parseUri() {
        String temp;
        // 截取传送的uri
        temp = StrUtil.subBetween(requestString, " ");
        // 不携带参数就直接使用
        if (!StrUtil.contains(temp, '?')) {
            uri = temp;
            return;
        }
        temp = StrUtil.subBefore(temp, '?', false);
        uri = temp;
    }

    @Override
    public String getParameter(String name) {
        String[] values = parameterMap.get(name);
        if (null != values && 0 != values.length) {
            return values[0];
        }
        return null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return parameterMap;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return parameterMap.get(name);
    }
    /**
     * 根据 get 和 post 方式分别解析参数。 需要注意的是，参数Map里存放的值是 字符串数组类型
     */
    private void parseParameters() {
        if ("GET".equals(this.getMethod())) {
            String url = StrUtil.subBetween(requestString, " ");
            if (StrUtil.contains(url, '?')) {
                queryString = StrUtil.subAfter(url, '?', false);
            }
        }

        if ("POST".equals(this.getMethod())) {
            queryString = StrUtil.subAfter(requestString, "\r\n\r\n", false);
        }

        if (null == queryString) {
            return;
        }

        queryString = URLUtil.decode(queryString);
        String[] parameterValues = queryString.split("&");
        if (null != parameterValues) {
            for (String parameterValue : parameterValues) {
                String[] nameValues = parameterValue.split("=");
                String name = nameValues[0];
                String value = nameValues[1];
                String[] values = parameterMap.get(name);
                if (null == values) {
                    values = new String[]{value};
                    parameterMap.put(name, values);
                } else {
                    values = ArrayUtil.append(values, value);
                    parameterMap.put(name, values);
                }
            }
        }
    }

    private void parseMethod() {
        this.method = StrUtil.subBefore(requestString, " ", false);
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getHeader(String name) {
        if (null == name) {
            return null;
        }
        name = name.toLowerCase();
        return headerMap.get(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set keys = headerMap.keySet();
        return Collections.enumeration(keys);
    }

    @Override
    public int getIntHeader(String name) {
        String value = headerMap.get(name);
        return Convert.toInt(value, 0);
    }

    /**
     * 从requestString 中解析出这些 header
     */
    public void parseHeaders() {
        StringReader stringReader = new StringReader(requestString);
        List<String> lines = new ArrayList<>();
        IoUtil.readLines(stringReader, lines);

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            // 请求体前有个空行，运行到这里就会退出
            if (0 == line.length()) {
                break;
            }

            String[] segs = line.split(":");
            String headerName = segs[0].toLowerCase();
            String headerValue = segs[1];
            headerMap.put(headerName, headerValue);

        }
    }

    public String getRequestString() {
        return requestString;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Context getContext() {
        return context;
    }

    @Override
    public ServletContext getServletContext() {
        return context.getServletContext();
    }

    @Override
    public String getRealPath(String path) {
        return context.getServletContext().getRealPath(path);
    }

    @Override
    public String getLocalAddr() {
        return socket.getLocalAddress().getHostAddress();
    }

    @Override
    public String getLocalName() {
        return socket.getLocalAddress().getHostName();
    }

    @Override
    public int getLocalPort() {
        return socket.getLocalPort();
    }

    @Override
    public String getProtocol() {
        return "HTTP:/1.1";
    }

    @Override
    public String getRemoteAddr() {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
        String temp = inetSocketAddress.getAddress().toString();
        return StrUtil.subAfter(temp ,"/", false);
    }

    @Override
    public int getRemotePort() {
        return socket.getPort();
    }

    @Override
    public String getScheme() {
        return "http";
    }

    @Override
    public String getServerName() {
        return getHeader("host").trim();
    }

    @Override
    public int getServerPort() {
        return getLocalPort();
    }

    @Override
    public String getContextPath() {
        String result = this.context.getPath();
        if ("/".equals(result)) {
            return "";
        }
        return result;
    }

    @Override
    public String getRequestURI() {
        return this.uri;
    }

    @Override
    public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0) {
            port = 80;
        }
        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((scheme.equals("http") && (port != 80) || (scheme.equals("https") && (port != 443)))) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());
        return url;
    }

    @Override
    public String getServletPath() {
        return uri;
    }

    @Override
    public Cookie[] getCookies() {
        return cookies;
    }

    private void parseCookies() {
        List<Cookie> cookieList = new ArrayList<>();
        String cookies = headerMap.get("cookie");
        if (null != cookies) {
            String[] pairs = StrUtil.split(cookies, ",");
            for (String pair : pairs) {
                if (StrUtil.isBlank(pair)) {
                    continue;
                }
                String[] segs = StrUtil.split(pair, "=");
                String name = segs[0].trim();
                String value = segs[1].trim();
                Cookie cookie = new Cookie(name, value);
                cookieList.add(cookie);
            }
        }
        this.cookies = ArrayUtil.toArray(cookieList, Cookie.class);
    }

    @Override
    public HttpSession getSession() {
        return session;
    }

    public void setSession(HttpSession session) {
        this.session = session;
    }
    /**
     * 从 cookie 中获取sessionid
     */
    public String getJSessionIdFromCookie() {
        if (null == cookies) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("JSESSIONID".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public Connector getConnector() {
        return connector;
    }

    public Socket getSocket() {
        return this.socket;
    }

    public boolean isForwarded() {
        return forwarded;
    }

    public void setForwarded(boolean forwarded) {
        this.forwarded = forwarded;
    }

    public RequestDispatcher getRequestDispatcher(String uri) {
        return new ApplicationRequestDispatcher(uri);
    }

    public void removeAttribute(String name) {
        attributesMap.remove(name);
    }
    public void setAttribute(String name, Object value) {
        attributesMap.put(name, value);
    }
    public Object getAttribute(String name) {
        return attributesMap.get(name);
    }
    public Enumeration<String> getAttributeNames() {
        Set<String> keys = attributesMap.keySet();
        return Collections.enumeration(keys);
    }
}
