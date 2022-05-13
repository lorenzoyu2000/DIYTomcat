package priv.mika.diytomcat.catalina;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.log.LogFactory;
import priv.mika.diytomcat.util.ServerXMLUtil;

import java.sql.Time;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Service {
    private String name;
    private Engine engine;
    private Server server;

    private List<Connector> connectors;

    public Service(Server server) {
        this.name = ServerXMLUtil.getServiceName();
        this.engine = new Engine(this);
        this.server = server;
        this.connectors = ServerXMLUtil.getConnectors(this);
    }
    public void start() {
        init();
    }

    public void init() {
        TimeInterval timeInterval = DateUtil.timer();
        for (Connector connector : connectors) {
            connector.init();
        }
        LogFactory.get().info("Initialization processed in {} ms",timeInterval.intervalMs());
        for (Connector connector : connectors) {
            connector.start();
        }
    }

    public Engine getEngine() {
        return engine;
    }

    public Server getServer() {
        return server;
    }
}
