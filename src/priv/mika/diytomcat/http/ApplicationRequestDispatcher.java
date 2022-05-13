package priv.mika.diytomcat.http;

import priv.mika.diytomcat.catalina.HttpProcessor;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

public class ApplicationRequestDispatcher implements RequestDispatcher {

    private String uri;
    public ApplicationRequestDispatcher(String uri) {
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        this.uri = uri;
    }

/**
 * 关于上一页面reponse的body，影响下一页面的body问题
 * 由于这里的服务器跳转，我可以理解为，当一个带有服务器跳转的Servlet或者Jsp在HttpProcess处理的过程中，
 * 调用了新的HttpProcess。即在不使用Connecter生成新socket的情况下，服务器内部进行资源访问。
 * 那么，问题来，由于这里的Response和Request都是在Connector定义的。
 * 那么，在Connector调用HttpProcess然后又在HttpProcess内执行服务器跳转的过程中，reponse是一直没有变化的。
 * 那么，我第一个执行服务器跳转的Servlet或者jsp，reponse里面产生的body数据，就会随着服务器跳转，影响接下来将被跳转的节点。
 * 也就是说，假如我a.jsp内带有一个服务器跳转语句，同时，也有一个HelloWorld。然后跳转到b.jsp，
 * 同时b.jsp里也有个Hello World但是，由于这里没有对reponse进行处理。
 * 那么，b.jsp中在浏览器输出的信息，就会是两条HelloWorld。这显然不符合事实。
 * 所以，在reponse跳转之前，要对缓存区进行清空。以便上一界面内容不影响下一界面内容
 */
    @Override
    public void forward(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
        Request request = (Request) servletRequest;
        Response response = (Response) servletResponse;

        request.setUri(uri);

        HttpProcessor httpProcessor = new HttpProcessor();
        response.resetBuffer();
        httpProcessor.execute(request.getSocket(), request, response);
        request.setForwarded(true);

    }

    @Override
    public void include(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {

    }
}
