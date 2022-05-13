package priv.mika.diytomcat.classloader;

import cn.hutool.core.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * WebappClassLoader 是专门用于加载某个 web 应用下的 class 和 jar 的。
 * 每个 Web 应用都有属于自己专属的 WebClassLoader，这样才可以做到同一个名称的类，在不同的 web 应用里，互不干扰。
 */
public class WebappClassLoader extends URLClassLoader {
    public WebappClassLoader(String docBase, ClassLoader commonClassLoader) {
        super(new URL[]{}, commonClassLoader);
        try {
            // 扫描 Context 对应的 docBase 下的 classes 和 lib
            File webinfFolder = new File(docBase, "WEB-INF");
            File classesFolder = new File(webinfFolder, "classes");
            File libFolder = new File(webinfFolder, "lib");

            URL url;
            // 把 classes 目录，通过 addURL 加进去。 注意，因为是目录，所以加的时候，要在结尾跟上 "/" , URLClassLoader 才会把它当作目录来处理
            url = new URL("file:" + classesFolder.getAbsolutePath() + "/");

            this.addURL(url);
            // 把 jar 通过 addURL 加进去
            // 遍历目录和子目录中的所有文件
            List<File> jarFiles = FileUtil.loopFiles(libFolder);

            for (File file : jarFiles) {
                url = new URL("file:" + file.getAbsolutePath());
                this.addURL(url);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
