package priv.mika.diytomcat.catalina;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.LogFactory;
import priv.mika.diytomcat.util.Constant;
import priv.mika.diytomcat.util.ServerXMLUtil;
import priv.mika.diytomcat.watcher.WarFileWatcher;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Host {
    private String name;
    // 存放路径和Context 的映射
    private Map<String, Context> contextMap;
    private Engine engine;

    public Host(String name, Engine engine) {
        this.contextMap = new HashMap<>();
        this.name = name;
        this.engine = engine;
        scanContextsOnWebAppsFolder();
        scanContextsInServerXML();
        scanWarOnWebAppsFolder();

        new WarFileWatcher(this).start();
    }

    /**
     *  扫描server.xml文件
     */
    private  void scanContextsInServerXML() {
        List<Context> contexts = ServerXMLUtil.getContexts(this);
        for (Context context : contexts) {
            contextMap.put(context.getPath(), context);
        }
    }

    /**
     *  扫描 webapps 文件夹下的目录，对这些目录调用 loadContext 进行加载。
     */
    private  void scanContextsOnWebAppsFolder() {
        File[] folders = Constant.webappsFolder.listFiles();
        for (File folder : folders) {
            if (!folder.isDirectory()) {
                continue;
            }
            loadContext(folder);
        }
    }

    /**
     *  加载这个目录成为 Context 对象，并保存到contextMap
     */
    private  void loadContext(File folder) {
        String path = folder.getName();
        // 在文件目录前加 /，如为ROOT目录则设为 / 即默认访问目录
        if ("ROOT".equals(path)) {
            path = "/";
        } else {
            path = "/" + path;
        }

        String docBase = folder.getAbsolutePath();
        Context context = new Context(path, docBase, this, true);
        contextMap.put(context.getPath(), context);
    }
    /**
     * 1. 先保存 path, docBase, relodable 这些基本信息
     * 2. 调用 context.stop() 来暂停
     * 3. 把它从 contextMap 里删掉
     * 4. 根据刚刚保存的信息，创建一个新的context
     * 5. 设置到 contextMap 里
     * 6. 开始和结束打印日志
     */
    public void reload(Context context) {
        LogFactory.get().info("Reloading Context with name [{}] has started", context.getPath());
        String path = context.getPath();
        String docBase = context.getDocBase();
        boolean reloadable = context.isReloadable();
        context.stop();
        contextMap.remove(path);
        Context newContext = new Context(path, docBase, this, reloadable);
        contextMap.put(newContext.getPath(), newContext);
        LogFactory.get().info("Reloading Context with name [{}] has completed", context.getPath());

    }

    public Context getContext(String path) {
        return contextMap.get(path);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * 把一个文件夹加载为Context
     */
    public void load(File folder) {
        String path = folder.getName();
        if ("ROOT".equals(path)) {
            path = "/";
        } else {
            path = "/" + path;
        }
        String docBase = folder.getAbsolutePath();
        Context context = new Context(path, docBase, this, false);
        contextMap.put(context.getPath(), context);
    }
    /**
     * 把 war 文件解压为目录，并把文件夹加载为 Context
     */
    public void loadWar(File warFile) {
        String fileName = warFile.getName();
        String folderName = StrUtil.subBefore(fileName, ".", true);
        // 看看是否已经有对应的 Context了
        Context context = getContext("/" + folderName);
        if (null != context) {
            return;
        }
        // 先看是否已经有对应的文件夹
        File folder = new File(Constant.webappsFolder, folderName);
        if (folder.exists()) {
            return;
        }
        // 移动war文件，因为jar 命令只支持解压到当前目录下
        File tempWarFile = FileUtil.file(Constant.webappsFolder, folderName, fileName);
        File contextFolder = tempWarFile.getParentFile();
        contextFolder.mkdir();
        FileUtil.copyFile(warFile, tempWarFile);
        // 解压
        String command = "jar xvf " + fileName;
        Process p = RuntimeUtil.exec(null, contextFolder, command);
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 解压之后删除临时war
        tempWarFile.delete();
        // 然后创建新的 Context
        load(contextFolder);
    }

    /**
     * 扫描webapps 目录，处理所有的 war 文件
     */
    private void scanWarOnWebAppsFolder() {
        File folder = FileUtil.file(Constant.webappsFolder);
        File[] files = folder.listFiles();
        for (File file : files) {
            if (!file.getName().endsWith(".war")) {
                continue;
            }
            loadWar(file);
        }
    }
}
