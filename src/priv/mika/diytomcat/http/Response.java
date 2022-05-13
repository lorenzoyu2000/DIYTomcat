package priv.mika.diytomcat.http;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class Response extends BaseResponse {

    private StringWriter stringWriter;
    private PrintWriter printWriter;
    private String contentType;
    // 存放二进制文件
    private byte[] body;

    private int status;

    private List<Cookie> cookies;

    private String redirectPath;

    public Response() {
        this.stringWriter = new StringWriter();
        this.printWriter = new PrintWriter(stringWriter);
        this.contentType = "text/html";
        this.cookies = new ArrayList<>();
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    public PrintWriter getWriter() {
        return printWriter;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public byte[] getBody() {
        if (null == body) {
            String content = stringWriter.toString();
            body = content.getBytes(StandardCharsets.UTF_8);
        }
        return body;
    }
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    public List<Cookie> getCookies() {
        return this.cookies;
    }
    /**
     * 把 Cookie集合转换成 cookie Header。
     */
    public String getCookiesHeader() {
        if (null == cookies) {
            return "";
        }
        String pattern = "EEE, d MMM yyyy HH:mm:ss 'GMT'";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern, Locale.ENGLISH);
        StringBuffer sb = new StringBuffer();
        for (Cookie cookie : getCookies()) {
            sb.append("\r\n");
            sb.append("Set-Cookie: ");
            sb.append(cookie.getName()+ "=" + cookie.getValue() + "; ");
            if (-1 != cookie.getMaxAge()) {
                sb.append("Expires=");
                Date now = new Date();
                Date expire = DateUtil.offset(now, DateField.SECOND, cookie.getMaxAge());
                sb.append(simpleDateFormat.format(expire));
                sb.append(";");
            }
            if (null != cookie.getPath()) {
                sb.append("Path=" + cookie.getPath());
            }
        }
        return sb.toString();
    }

    public String getRedirectPath() {
        return redirectPath;
    }

    @Override
    public void sendRedirect(String redirect) throws IOException {
        this.redirectPath = redirect;
    }

    @Override
    public void resetBuffer() {
        this.stringWriter.getBuffer().setLength(0);
    }
}
