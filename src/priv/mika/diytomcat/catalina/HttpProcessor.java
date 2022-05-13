package priv.mika.diytomcat.catalina;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.log.LogFactory;
import priv.mika.diytomcat.http.Request;
import priv.mika.diytomcat.http.Response;
import priv.mika.diytomcat.servlets.DefaultServlet;
import priv.mika.diytomcat.servlets.InvokerServlet;
import priv.mika.diytomcat.servlets.JspServlet;
import priv.mika.diytomcat.util.Constant;
import priv.mika.diytomcat.util.SessionManager;
import priv.mika.diytomcat.util.WebXMLUtil;
import priv.mika.diytomcat.webappservlet.HelloServlet;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 把 Connector 里的处理工作接过来，简化Connector
 */
public class HttpProcessor {
    public void execute(Socket socket, Request request, Response response) {
        try {
            String uri = request.getUri();
            if (null == uri) {
                return;
            }
            prepareSession(request, response);
            Context context = request.getContext();
            String servletClassName = context.getServletClassName(uri);
            HttpServlet workingServlet;

            if (null != servletClassName) {
                workingServlet = InvokerServlet.getInstance();
            } else if(uri.endsWith(".jsp")) {
                workingServlet = JspServlet.getInstance();
            } else {
                workingServlet = DefaultServlet.getInstance();
            }
            /**
             * 因为 Servlet 的 service 方法是在 chain 里面调用的，所以原来的调用去掉，并且用一个 workingServlet 分别指向它们。
             * 接着通过 getMatchedFilters 获取对应的 Filters 集合， 再通过 filterChain 去调用它们
             */
            List<Filter> filters = request.getContext().getMatchedFilters(request.getUri());
            ApplicationFilterChain filterChain = new ApplicationFilterChain(filters, workingServlet);
            filterChain.doFilter(request, response);

            /**
             * 如果发现请求是 forwarded 的，后续就不处理了,
             * 否则会调用多次 handle200, 导致已经关闭的 socket 被使用就会抛出异常。
             */
            if (request.isForwarded()) {
                return;
            }

            if (Constant.CODE_200 == response.getStatus()) {
                handle200(socket, request,response);
                return;
            }

            if (Constant.CODE_302 == response.getStatus()) {
                handle302(socket, response);
                return;
            }

            if (Constant.CODE_404 == response.getStatus()) {
                handle404(socket, uri);
                return;
            }
        } catch (Exception e) {
            LogFactory.get().error(e);
            handle500(socket, e);
        } finally {
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 返回状态码200，并携带响应主体
     */
    private void handle200(Socket socket, Request request,Response response) throws IOException {
        OutputStream os = socket.getOutputStream();
        // 根据 response 对象上的 contentType ，组成返回的头信息，并且转换成字节数组。
        String contentType = response.getContentType();
        byte[] body = response.getBody();

        String cookiesHeader = response.getCookiesHeader();

        boolean gzip = isGzip(request, body, contentType);
        String headText;
        if (gzip) {
            headText = Constant.response_head_200_gzip;
        } else {
            headText = Constant.response_head_202;
        }
        headText = StrUtil.format(headText, contentType, cookiesHeader);

        System.out.println(headText + "" + new String(body));

        if (gzip) {
            body = ZipUtil.gzip(body);
        }

        byte[] head = headText.getBytes(StandardCharsets.UTF_8);

        // 拼接头信息和主题信息，成为一个响应字节数组
        byte[] responseBytes = new byte[head.length + body.length];
        ArrayUtil.copy(head, 0, responseBytes, 0, head.length);
        ArrayUtil.copy(body, 0, responseBytes, head.length, body.length);

        // 把这个响应字节数组返回浏览器。
        os.write(responseBytes);
        os.flush();
        os.close();
    }

    protected static void handle404(Socket socket, String uri) throws IOException {
        OutputStream os = socket.getOutputStream();
        String responseText = StrUtil.format(Constant.textFormat_404, uri, uri);
        responseText = Constant.response_head_404 + responseText;
        byte[] responseByte = responseText.getBytes(StandardCharsets.UTF_8);
        os.write(responseByte);
    }

    /**
     * 返回状态码500，并携带报错信息
     */
    protected void handle500(Socket socket, Exception e) {
        try {
            OutputStream os = socket.getOutputStream();

            // e.getStackTrace(); 拿到 Exception 的异常堆栈
            // 平时我们看到一个报错，都会打印哪个类的哪个方法，依次调用过来的信息。
            // 这个信息就放在这个 StackTrace里，是个 StackTraceElement 数组。
            StackTraceElement[] stackTraceElements = e.getStackTrace();

            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append(e.toString());
            stringBuffer.append("\r\n");
            for (StackTraceElement stackTraceElement : stackTraceElements) {
                stringBuffer.append("\t");
                stringBuffer.append(stackTraceElement.toString());
                stringBuffer.append("\r\n");
            }
            String msg = e.getMessage();
            if (null != msg && msg.length() > 20) {
                msg = msg.substring(0, 19);
            }
            System.out.println("msg :" + msg);
            System.out.println("e :" + e.toString());
            System.out.println("sb :" + stringBuffer.toString());
            String text = StrUtil.format(Constant.textFormat_500, msg, e.toString(), stringBuffer.toString());
            text = Constant.response_head_500 + text;
            byte[] responseByte = text.getBytes(StandardCharsets.UTF_8);
            os.write(responseByte);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private void handle302(Socket socket, Response response) throws IOException {
        OutputStream os = socket.getOutputStream();
        String redirectPath = response.getRedirectPath();
        String head_text = Constant.response_head_302;
        String header = StrUtil.format(head_text, redirectPath);
        byte[] responseBytes = header.getBytes(StandardCharsets.UTF_8);
        os.write(responseBytes);
    }

    /**
     * 准备session, 先通过 cookie拿到 jsessionid, 然后通过 SessionManager 创建 session, 并且设置在 requeset 上
     */
    public void prepareSession(Request request, Response response) {
        String jsessionid = request.getJSessionIdFromCookie();
        HttpSession session = SessionManager.getSession(jsessionid, request, response);
        request.setSession(session);
    }
    /**
     * 判断是否要进行gzip
     */
    private boolean isGzip(Request request, byte[] body, String mimeType) {
        String acceptEncodings = request.getHeader("Accept-Encoding");
        if (!StrUtil.containsAny(acceptEncodings, "gzip")) {
            return false;
        }

        Connector connector = request.getConnector();
        if (mimeType.contains(";")) {
            mimeType = StrUtil.subBefore(mimeType, ";", false);
        }
        if (!"on".equals(connector.getCompression())) {
            return false;
        }
        if (body.length < connector.getCompressionMinSize()) {
            return false;
        }
        // 判断当前浏览器是否为server.xml中设置的不需要压缩的浏览器
        String userAgents = connector.getNoCompressionUserAgents();
        String[] eachUserAgents = userAgents.split(",");
        for (String eachUserAgent : eachUserAgents) {
            eachUserAgent = eachUserAgent.trim();
            String userAgent = request.getHeader("User-Agent");
            if (StrUtil.containsAny(userAgent, eachUserAgent)) {
                return false;
            }
        }
        String mimeTypes = connector.getCompressibleMimeType();
        String[] eachMimeTypes = mimeTypes.split(",");
        for (String eachMimeType : eachMimeTypes) {
            if (mimeType.equals(eachMimeType)) {
                return true;
            }
        }
        return false;
    }
}












