package priv.mika.diytomcat.util;

import cn.hutool.http.HttpUtil;
import com.sun.deploy.util.SessionState;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
    /**
     * 工具类，来查看 HTTP 返回的各种信息
     */
public class MiniBrowser {

    public static byte[] getContentBytes(String url, Map<String,Object> params, boolean isGet) {
        return getContentBytes(url, false,params,isGet);
    }

    public static byte[] getContentBytes(String url, boolean gzip) {
        return getContentBytes(url, gzip,null,true);
    }

    public static byte[] getContentBytes(String url) {
        return getContentBytes(url, false,null,true);
    }

    public static String getContentString(String url, Map<String,Object> params, boolean isGet) {
        return getContentString(url,false,params,isGet);
    }

    public static String getContentString(String url, boolean gzip) {
        return getContentString(url, gzip, null, true);
    }

    public static String getContentString(String url) {
        return getContentString(url, false, null, true);
    }


    public static String getContentString(String url, boolean gzip, Map<String,Object> params, boolean isGet) {
        // 这里获取返回体具体内容的字节数组,请跟进去看
        byte[] result = getContentBytes(url, gzip, params,isGet);
        // getContentString 表示获取内容的字符串,我们获取到具体内容的字节数组后还需要进行编码
        if (null == result) {
            return null;
        }
        // 这里就是一个编码过程了,我这里跟源代码不同,用StandarCahrset这个类可以避免抛异常,
        // 这里引入一个知识,因为这个是个常量,jvm可以知道你会选的是utf-8,所以不要求你抛异常
        return new String(result, StandardCharsets.UTF_8).trim();
    }

    //  getContentBytes 返回二进制的 http 响应内容 （可简单理解为去掉头的 html 部分）
    public static byte[] getContentBytes(String url, boolean gzip, Map<String,Object> params, boolean isGet) {
        byte[] response = getHttpBytes(url, gzip, params, isGet);
        //这个doubleReturnq其实是这样来的:我们获取的返回值正常其实是这样的
        /**(响应头部分)
         *  xxxxx
         *  xxxxx
         *  xxxxx
         *
         *  (具体内容部分,在这个代码中是hello diytomcat)
         *  xxx
         */
        //也就是说响应头部分和具体内容部分其实隔了一行, \r表示回到行首\n表示换到下一行,
        // 那么\r\n就相当于说先到了空格一行的那一行的行首,接着又到了具体内容的那部分的行首
        byte[] doubleReturn = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        // 初始化一个记录值
        int pos = -1;
        for (int i = 0; i < response.length - doubleReturn.length; i++) {
            // 这里的意思就是不断去初始化一个数组(从原数组进行拷贝),目的其实是为了获取到\r\n这一行的起始位置
            byte[] temp = Arrays.copyOfRange(response, i, i + doubleReturn.length);

            // 来到这里,就是比较内容,当走到这里,说明temp这个字节数组的内容就是\r\n\r\n的内容了,说明我们找到了他的起始位置
            if (Arrays.equals(temp, doubleReturn)) {
                pos = i;
                break;
            }
        }

        if (-1 == pos) {
            return null;
        }
        // 接着pos就是\r\n\n的第一个\的这个位置,加上\r\n\r\n的长度,相当于来到了具体内容的其实位置
        pos += doubleReturn.length;

        byte[] result = Arrays.copyOfRange(response, pos, response.length);
        return result;
    }

    public static String getHttpString(String url,boolean gzip, Map<String,Object> params, boolean isGet) {
        byte[] bytes = getHttpBytes(url, gzip, params, isGet);
        return new String(bytes).trim();
    }

    public static String getHttpString(String url) {
        return getHttpString(url, false, null, true);
    }

    public static String getHttpString(String url,boolean gzip) {
        return getHttpString(url, gzip, null, true);
    }

    public static String getHttpString(String url, Map<String,Object> params, boolean isGet) {
        return getHttpString(url,false, params, isGet);
    }

    /**
     * getHttpBytes 返回二进制的 http 响应
     * 与请求地址建立连接的逻辑,是整个类的核心,其他方法都只是处理这个方法返回值的一些逻辑而已
     */
    public static byte[] getHttpBytes(String url,boolean gzip, Map<String,Object> params, boolean isGet) {
        String method = isGet ? "GET" : "POST";
        // 首先初始化一个返回值,这个返回值是一个字节数组,utf-8编码的
        byte[] result = null;
        try {
            // 通过url来new一个URL对象,这样你就不用自己去截取他的端口啊或者请求路径啥的,可以直接调他的方法获取
            URL u = new URL(url);
            // 开启一个socket链接,client指的就是你现在的这台计算机
            Socket client = new Socket();

            int port = u.getPort();
            if (port == -1) {
                port = 80;
            }

            // 通过一个host+端口,和这个url建立连接
            InetSocketAddress inetSocketAddress = new InetSocketAddress(u.getHost(), port);
            client.connect(inetSocketAddress, 1000);

            // 初始化请求头
            Map<String, String> requestHeaders = new HashMap<>();

            // 这几个参数都是http请求时会带上的请求头
            requestHeaders.put("Host", u.getHost() + ":" + port);
            requestHeaders.put("Accept", "text/html");
            requestHeaders.put("Connection", "close");
            requestHeaders.put("User-Agent", "StarMika browser / java1.8");

            // gzip是确定客户端或服务器端是否支持压缩
            if (gzip) {
                requestHeaders.put("Accept-Encoding", "gzip");
            }

            // 获取到path,如果没有的话就默认是/
            String path = u.getPath();
            if (path.length() == 0) {
                path = "/";
            }

            if (null != params && isGet) {
                // 将Map形式的Form表单数据转换为Url参数形式，不做编码
                String paramString = HttpUtil.toParams(params);
                path = path + "?" + paramString;
            }

            // 接着开始拼接请求的字符串,其实所谓的请求头和请求内容就是这么一串字符串拼接出来
            String firstLine = method + " " + path + " HTTP/1.1\r\n";

            StringBuffer httpRequestString = new StringBuffer();
            httpRequestString.append(firstLine);
            Set<String> headers = requestHeaders.keySet();

            // 遍历header的那个map进行拼接
            for (String header : headers) {
                String headerLine = header + ":" + requestHeaders.get(header) + "\r\n";
                httpRequestString.append(headerLine);
            }

            if(null != params && !isGet){
                String paramsString = HttpUtil.toParams(params);
                httpRequestString.append("\r\n");
                httpRequestString.append(paramsString);
            }

            /**走到这的时候,httpRequestString已经拼接好了,内容是:
             GET /diytomcat.html HTTP/1.1
             Accept:text/html
             Connection:close
             User-Agent:how2j mini browser / java1.8
             Host:static.how2j.cn:80
             */
            // 通过输出流,将这么一串字符串输出给连接的地址,后面的true是autoFlush,表示开始自动刷新
            PrintWriter printWriter = new PrintWriter(client.getOutputStream(), true);
            printWriter.println(httpRequestString);

            // 这时候你已经将需要的请求所需的字符串发给上面那个url了,
            // 其实所谓的http协议就是这样,
            // 你发给他这么一串符合规范的字符串,他就给你响应,接着他那边就给你返回数据
            InputStream inputStream = client.getInputStream();

            result = readBytes(inputStream, true);
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                result = e.toString().getBytes("utf-8");
            } catch (UnsupportedEncodingException unsupportedEncodingException) {
                unsupportedEncodingException.printStackTrace();
            }
        }
        return result;
    }
    /**
     * 读取浏览器发送来的信息
     * fully:表示是否完全读取
     * fully作用：
     * 在读取大文件时一次读取到的字节数可能不是1024，那么读取的文件就不完整，
     * 所以增加fully来保证即使一次读取小于这个数值也进行读取，直到读完
     */
    public static byte[] readBytes(InputStream is, boolean fully) throws IOException {
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        while (true) {
            int length = is.read(buffer);
/*           当服务器使用readBytes函数时，因为浏览器默认使用长链接，不会主动关闭连接，所以服务器不会读取到-1，
             所以需要使用if(!fully&&length!=buffer_size)来退出读取，
             这里默认浏览器读取服务器的数据是1024，1024, ... <1024的这种情况*/

            // 读到的长度如果是-1,说明没读到数据了,直接退出
            if (-1 == length) {
                break;
            }

            byteArrayOutputStream.write(buffer, 0, length);
/*           1.这里当buffer_size!=length时说明，已经读到了末尾，即只剩小于1024字节的内容了（由于已经写过了），
             所以这里可以退出了。这种情况是针对fully=flase, 服务器发送给浏览器的数据的读取是1024，1024, ... <1024，
             另一种情况，服务器发送给浏览器的数据的读取是<1024,<2014,<1024,
             为了让这种情况能够正常运行，需要fully=true。结合上面所述，这里的问题在于：
             1.我们怎么能确定服务器发给浏览器的数据的读取是第一种情况还是第二种情况。
             2.为什么能确定浏览器读取服务器的数据是1024，1024, ... <1024的这种情况？*/
            if (!fully && length != bufferSize) {
                break;
            }
        }

/*       通过方法,将这个输出流返回成字节数组result,为什么要用这个输出流来存返回的字节数组呢?
         因为如果你用数组的话其实你不能确定整个返回数据有多大*/
        byte[] result = byteArrayOutputStream.toByteArray();
        return result;
    }
}
