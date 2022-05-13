package priv.mika.diytomcat.classloader;

import cn.hutool.core.util.StrUtil;
import priv.mika.diytomcat.catalina.Context;
import priv.mika.diytomcat.util.Constant;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * 回顾一下 JspClassLoader 的特点：
 * 1. 一个 jsp 文件就对应一个 JspClassLoader
 * 2. 如果这个 jsp 文件修改了，那么就要换一个新的 JspClassLoader
 * 3. JspClassLoader 基于 由 jsp 文件转移并编译出来的 class 文件，进行类的加载
 */
public class JspClassLoader extends URLClassLoader {
    private static Map<String, JspClassLoader> map = new HashMap<>();

    private JspClassLoader(Context context) {
        super(new URL[]{}, context.getWebappClassLoader());
        try {
        String subFolder;
        String path = context.getPath();
        if ("/".equals(path)) {
            subFolder = "_";
        } else {
            subFolder = StrUtil.subAfter(path, '/', false);
        }
        File classesFolder = new File(Constant.workFolder, subFolder);
        URL url = new URL("file:" + classesFolder.getAbsolutePath() + "/");
        this.addURL(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 让Jsp和JspClassLoader 取消关联
     */
    public static void invalidJspClassLoader(String uri, Context context) {
        String key = context.getPath() + "/" + uri;
        map.remove(key);
    }

    public static JspClassLoader getJspClassLoader(String uri, Context context) {
        String key = context.getPath() + "/" + uri;
        JspClassLoader jspClassLoader = map.get(key);
        if (null == jspClassLoader) {
            jspClassLoader = new JspClassLoader(context);
            map.put(key, jspClassLoader);
        }
        return jspClassLoader;
    }
}
