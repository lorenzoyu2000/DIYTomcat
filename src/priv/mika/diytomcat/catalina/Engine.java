package priv.mika.diytomcat.catalina;

import priv.mika.diytomcat.util.ServerXMLUtil;

import java.util.List;
/**
 * @Description   Engine表示 servlet 引擎，用来处理 servlet 的请求
 */
public class Engine {
    private String defaultHost;
    private List<Host> hosts;
    private Service service;

    public Engine(Service service) {
        this.service = service;
        this.defaultHost = ServerXMLUtil.getEngineDefaultHost();
        this.hosts = ServerXMLUtil.getHosts(this);
        checkDefault();
    }

    public Host getDefaultHost() {
        for (Host host : hosts) {
            if (host.getName().equals(defaultHost)) {
                return host;
            }
        }
        return null;
    }

    private void checkDefault() {
        if (null == getDefaultHost()) {
            throw new RuntimeException("the defaultHost" + defaultHost + " does not exist!");
        }
    }

    public Service getService() {
        return service;
    }
}
