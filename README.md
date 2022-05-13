# DiyTomcat

## Tomcat

### 介绍

Tomcat 作为一个 「`Http` 服务器 + `Servlet` 容器」，对我们屏蔽了应用层协议和网络通信细节，给我们的是标准的 `Request` 和 `Response` 对象；对于具体的业务逻辑则作为变化点，交给我们来实现。我们使用了`SpringMVC` 之类的框架，可是却从来不需要考虑 `TCP` 连接、 `Http` 协议的数据处理与响应。就是因为 Tomcat 已经为我们做好了这些，我们只需要关注每个请求的具体业务逻辑。

- Server 对应的就是一个 Tomcat 实例。
- Service 默认只有一个，也就是一个 Tomcat 实例默认一个 Service。
- Connector：一个 Service 可能多个 连接器，接受不同连接协议。
- Container: 多个连接器对应一个容器，顶层容器其实就是 Engine。

每个组件都有对应的生命周期，需要启动，同时还要启动自己内部的子组件，比如一个 Tomcat 实例包含一个 Service，一个 Service 包含多个连接器和一个容器。而一个容器包含多个 Host， Host 内部可能有多个 Contex t 容器，而一个 Context 也会包含多个 Servlet，所以 Tomcat 利用组合模式管理组件每个组件，对待过个也想对待单个组一样对待。

Tomcat 的整体架构包含了两个核心组件连接器和容器。连接器负责对外交流，容器负责内部处理。

容器运用了**组合模式 管理容器、通过 观察者模式 发布启动事件达到解耦、开闭原则。骨架抽象类和模板方法抽象变与不变，变化的交给子类实现，从而实现代码复用，以及灵活的拓展**。使用责任链的方式处理请求，比如记录日志等。

Tomcat 的自定义类加载器 `WebAppClassLoader`为了隔离 Web 应用打破了双亲委托机制，它首先自己尝试去加载某个类，如果找不到再代理给父类加载器，其目的是优先加载 Web 应用自己定义的类。防止 Web 应用自己的类覆盖 JRE 的核心类，使用 ExtClassLoader 去加载，这样即打破了双亲委派，又能安全加载。	

### 连接器

运用了适配器和模板方法。

`Tomcat`支持的 `I/O` 模型有：

- `NIO`：非阻塞 `I/O`，采用 `Java NIO` 类库实现。
- `NIO2`：异步`I/O`，采用 `JDK 7` 最新的 `NIO2` 类库实现。
- `APR`：采用 `Apache`可移植运行库实现，是 `C/C++` 编写的本地库。

Tomcat 支持的应用层协议有：

- `HTTP/1.1`：这是大部分 Web 应用采用的访问协议。
- `AJP`：用于和 Web 服务器集成（如 Apache）。
- `HTTP/2`：HTTP 2.0 大幅度的提升了 Web 性能。

所以一个容器可能对接多个连接器。连接器对 `Servlet` 容器屏蔽了网络协议与 `I/O` 模型的区别，无论是 `Http` 还是 `AJP`，在容器中获取到的都是一个标准的 `ServletRequest` 对象。

细化连接器的功能需求就是：

- 监听网络端口。
- 接受网络连接请求。
- 读取请求网络字节流。
- 根据具体应用层协议（`HTTP/AJP`）解析字节流，生成统一的 `Tomcat Request` 对象。
- 将 `Tomcat Request` 对象转成标准的 `ServletRequest`。
- 调用 `Servlet`容器，得到 `ServletResponse`。
- 将 `ServletResponse`转成 `Tomcat Response` 对象。
- 将 `Tomcat Response` 转成网络字节流。
- 将响应字节流写回给浏览器。

### 容器

连接器负责外部交流，容器负责内部处理。连接器处理 Socket 通信和应用层协议的解析，得到 `Servlet`请求；而容器则负责处理 `Servlet`请求。

容器：顾名思义就是拿来装东西的， 所以 Tomcat 容器就是拿来装载 `Servlet`。

Tomcat 设计了 4 种容器，分别是 `Engine`、`Host`、`Context`和 `Wrapper`。`Server` 代表 Tomcat 实例。

Tomcat 通过一种分层的架构，使得 Servlet 容器具有很好的灵活性。因为这里正好符合一个 Host 多个 Context， 一个 Context 也包含多个 Servlet，而每个组件都需要统一生命周期管理，所以组合模式设计这些容器。

`Wrapper` 表示一个 `Servlet` ，`Context` 表示一个 Web 应用程序，而一个 Web 程序可能有多个 `Servlet` ；`Host` 表示一个虚拟主机，或者说一个站点，一个 Tomcat 可以配置多个站点（Host）；一个站点（ Host） 可以部署多个 Web 应用；`Engine` 代表 引擎，用于管理多个站点（Host），一个 Service 只能有 一个 `Engine`。

Tomcat 就是用组合模式来管理这些容器的。具体实现方法是，**所有容器组件都实现了 `Container`接口，因此组合模式可以使得用户对单容器对象和组合容器对象的使用具有一致性**。这里单容器对象指的是最底层的 `Wrapper`，组合容器对象指的是上面的 `Context`、`Host`或者 `Engine`。

### 请求定位 Servlet 的过程

一个请求是如何定位到让哪个 `Wrapper` 的 `Servlet` 处理的？答案是，Tomcat 是用 Mapper 组件来完成这个任务的。

`Mapper` 组件的功能就是将用户请求的 `URL` 定位到一个 `Servlet`，它的工作原理是：`Mapper`组件里保存了 Web 应用的配置信息，其实就是**容器组件与访问路径的映射关系**，比如 `Host`容器里配置的域名、`Context`容器里的 `Web`应用路径，以及 `Wrapper`容器里 `Servlet` 映射的路径，你可以想象这些配置信息就是一个多层次的 `Map`。

当一个请求到来时，`Mapper` 组件通过解析请求 URL 里的域名和路径，再到自己保存的 Map 里去查找，就能定位到一个 `Servlet`。请你注意，一个请求 URL 最后只会定位到一个 `Wrapper`容器，也就是一个 `Servlet`。

假如有用户访问一个 URL，比如图中的`http://user.shopping.com:8080/order/buy`，Tomcat 如何将这个 URL 定位到一个 Servlet 呢？

1. **首先根据协议和端口号确定 Service 和 Engine**。Tomcat 默认的 HTTP 连接器监听 8080 端口、默认的 AJP 连接器监听 8009 端口。上面例子中的 URL 访问的是 8080 端口，因此这个请求会被 HTTP 连接器接收，而一个连接器是属于一个 Service 组件的，这样 Service 组件就确定了。我们还知道一个 Service 组件里除了有多个连接器，还有一个容器组件，具体来说就是一个 Engine 容器，因此 Service 确定了也就意味着 Engine 也确定了。
2. **根据域名选定 Host。** Service 和 Engine 确定后，Mapper 组件通过 URL 中的域名去查找相应的 Host 容器，比如例子中的 URL 访问的域名是`user.shopping.com`，因此 Mapper 会找到 Host2 这个容器。
3. **根据 URL 路径找到 Context 组件。** Host 确定以后，Mapper 根据 URL 的路径来匹配相应的 Web 应用的路径，比如例子中访问的是 /order，因此找到了 Context4 这个 Context 容器。
4. **根据 URL 路径找到 Wrapper（Servlet）。** Context 确定后，Mapper 再根据 web.xml 中配置的 Servlet 映射路径来找到具体的 Wrapper 和 Servlet。

## 简介

Tomcat是一个Web服务器（同时也是Servlet和JSP的容器），通过它我们可以很方便地接收和返回到请求，如果不用Tomcat，那我们需要自己写Socket来接收和返回请求。

![图片](https://mmbiz.qpic.cn/mmbiz_png/2BGWl1qPxib2FPYiazSBurrVsbt1mkHFfDWRuvTkwHW1Hfn4NxTlJ3xJh4ibN8mL8W5lUdfVG4GUopptUrKC8hdpw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

![图片](https://mmbiz.qpic.cn/sz_mmbiz_jpg/2BGWl1qPxib1eeZCdw26aa5NVzKA9LYibOSQXk5PuCDGl6OJGmD9Flk8QEichQ86mqtWOeBGQ3WJvjOyht8yWm5KQ/640?wx_fmt=jpeg&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

## HTTP协议

### 概述

HTTP协议是客户端和服务器交互的一种通迅的格式。

例如:在浏览器点击一个链接，浏览器就为我打开这个链接的网页。

原理：当在浏览器中点击这个链接的时候，浏览器会向服务器发送一段文本，告诉服务器请求打开的是哪一个网页。服务器收到请求后，就返回一段文本给浏览器，浏览器会将该文本解析，然后显示出来。这段「文本」就是遵循HTTP协议规范的。

### 实现

使用Request和Response来接受返回消息。

**Request作用：**

1. 读取浏览器发送来的信息，设置字节数组使用 BIO 每次读取1M的数据写入到输出流中，读取文件到末尾后输入流会返回-1表示读取完成，这是if判断会break，读取完成返回字节数组。DiyTomcat没有实现长连接，
   而浏览器默认使用长连接，在发送完消息后不会返回-1来中断读取request信息，所以完整读取文件参数fully不能起到作用，只能false。
2. 分割URI，获取URI请求的web应用和所对应的应用下的文件，如果没有对应应用文件，则读取xml文件配置的默认文件。
3. 解析请求方法类型，根据方法类型对URI做不同的处理
4. 根据不同方法解析请求参数，放入HashMap
5. 解析请求中是否含有Cookies

**Response作用：**

1. 设置状态码，返回消息的格式
2. 返回body数据
3. 给浏览器发送cookie，并检查cookie是否过期，每个web应用对应唯一的cookie

## Cookie和Session

### cookie

服务端生成cookie并设置访问路径、存活时间，将cookie放在响应报文的`Set-Cookie`首部字段中。

浏览器接收cookie并保存在本地，在下一次发送请求请求报文时会携带这个cookie。

服务端会解析cookie，判断是否失效，没有失效则发送给应用。

### session

StandardSession用来创建session，拥有id、创建时间、最后一次访问时间、servletContext、最大持续时间。

SessionManager用HashMap存储session，并轮询查看HashMap中的session是否失效。

获取session的主逻辑
如果浏览器没有传递 jsessionid 过来，那么就创建一个新的session
如果浏览器传递过来的 jsessionid 无效，那么也创建一个新的 sessionid
否则就使用现成的session, 并且修改它的lastAccessedTime， 以及创建对应的 cookie

cookie存储sessionId，浏览器发送请求报文时从cookie中拿出sessionId到HashMap中寻找对应的session。

## 跳转

### 客户端跳转

客户端跳转分为 301 永久跳转，302 临时跳转。
访问应用servlet，调用response.sendRedirect，response读取跳转路径设置在响应头信息的Location中，再次进行访问Location中的位置。

1. 客户浏览器发送http请求
2. web服务器接受后发送302状态码响应和新的location给客户浏览器
3. 客户浏览器发现是302响应，则自动再发送一个新的http请求，请求url是新的location地址
4. 服务器根据此请求寻找资源并发送给客户。

### 服务端跳转

服务端跳转也叫请求转发（内部跳转），是RequestDispatcher接口的forward()方法来实现的；

而RequestDispatcher是通过调用HttpServletRequest对象的getRequestDispatcher()方法得到的。 

服务器端跳转页面的路径不会发生改变，所以可以request范围的属性。传输的信息不会丢失。

1. 客户浏览器发送http请求
2. web服务器接受此请求
3. 调用内部的一个方法在容器内部完成请求处理和转发动作
4. 将目标资源发送给客户；

转发的路径必须是同一个web容器下的url，其不能转向到其他的web路径上去，中间传递的是自己的容器内的request。在客户浏览器路径栏显示的仍然是其第一次访问的路径，也就是说客户是感觉不到服务器做了转发的。转发行为是浏览器只做了一次访问请求。

## 连接器

### 概述

连接器配置在 conf/server.xml 下

```
port 表示启动端口
compression 表示是否启动，当等于 "on" 的时候，表示启动
compressionMinSize 表示最小进行压缩的字节数，太小就没有必要压缩了，一般是 1024. 但是这里为了看到效果，故意设置成20，否则就看不到现象了。
noCompressionUserAgents： 这表示不进行压缩的浏览器
compressableMimeType： 这表示哪些 mimeType 才需要进行压缩-->
```

连接器用来接受 socket 请求。

keep-alive 的方式，NioEndpoint 的方式是建立一个连接之后，后续的数据都在这个连接上来回传输。 

diytomcat 考虑到难度问题，是每次请求都会发起一个新的链接，处理之后就关闭了。

EndPoint :即通信监听的接口，是具体Socket接收和发送处理器，是对传输层的抽象。

### 线程池

根据 conf/serverl.xml server service 下的connector元素标签中的port元素来配置启动端口，这样可以实现多端口，每个端口多线程的服务。

### Connector

Connector接受socket请求，把请求先封装为request对象，然后再由httpprocessor封装为servlet request对象，然后就交给servlet容器，就可以调用servlet.service()方法了（会先调用`servlet.init()`）

### httpprocessor

将HttpServletRequest发送给对应的servlet处理。

成功则返回状态码200并携带响应主体，请求应用不存在则返回404，服务器出错则返回500并打印异常堆栈的信息给客户端。

判断是否要进行压缩，判断当前浏览器是否为server.xml中设置的不需要压缩的浏览器。

## Catalina容器

用于存储针对每个虚拟机的应用配置。

Server, Service, Engine, Host, Context 的过程中，本质上就是把 server.xml 里的各个元素映射到上述这些类的实例里面去了， 借用的是 ServerXMLUtil 这个工具进行的。

### Server

Server代表服务器本身。

一个Tomcat中只有一个Server，一个Server可以包含多个Service，一个Service只有一个Engine，但是可以有多个Connectors，这是因为一个服务可以有多个连接，如同时提供Http和Https链接，也可以提供向相同协议不同端口的连接。

`server.xml` 是tomcat 服务器的核心配置文件，包含了`Tomcat`的 Servlet 容器（Catalina）的所有配置。由于配置的属性特别多，我们在这里主要讲解其中的一部分重要配置。

### Service

Service 是 Engine 的父节点，用于代表 tomcat 提供的服务。 它里面会有很多 Connector 对象

### Engine

在 tomcat 里 Engine表示 servlet 引擎，用来处理 servlet 的请求。

### Host

Host 的意思是虚拟主机。 通常都是 localhost, 即表示本机。

绝大部分tomcat都服务于一个 host，所以diytomcat没有支持多host。

改造为多host，那么Host类中的contextMap应该按照Host的名字来扫描获取，因为不同的Context可能属于不同的Host。

1. 扫描 webapps 文件夹下的目录，对这些目录调用 loadContext 进行加载。
2. loadContext 为webapps下的应用创建对应的context对象，并把应用访问路径作为key，context为value存入HashMap contextMap中。
3. 扫描 conf/server.xml 中配置的 Context 元素标签（虚拟目录），将访问路径和 context 应用存入contextMap中。

**虚拟目录：**

- 如果把所有web站点的目录都放在webapps下，可能导致磁盘空间不够用，也不利于对web站点目录的管理【如果存在非常多的web站点目录】
- 把web站点的目录分散到其他磁盘管理就需要配置虚拟目录【默认情况下，只有webapps下的目录才能被Tomcat自动管理成一个web站点】
- 把web应用所在目录交给web服务器管理，这个过程称之为虚拟目录的映射

## Servlet

servlet就是一个程序，用来响应任何类型的请求。

目前我们的 DiyTomcat 即处理 Servlet 又可能处理静态文件了，将来还会处理 jsp 文件。
按照 Tomcat 的做法，会分别创建3种 Servlet, 专门来处理这3种不同的资源：

1. InvokerServlet: 处理 Servlet
2. DefaultServlet: 处理静态资源
3. JspServlet 处理 jsp 文件

### 单例的Context

将陌生context放入HashMap servletPool中保存，在context不更新得情况下，下次来就把暂存的相同的servlet返回给它 （好像还有点redis缓存的感觉），加上syn 是保证当一个线程调用getServlet方法的时候，其他线程是不能调用它的

servlet为什么设计成单例模式。因为创建对象会有开销，这里如果不设计成单例，每次都会创建新servlet来处理，消耗了服务器资源。

浏览器多次对Servlet的请求，服务器只创建一个Servlet对象，也就是说，Servlet对象一旦创建了，就会驻留在内存中，为后续的请求做服务，直到服务器关闭。

> 修改context后拿不到原来的servlet

修改了 HelloServlet ，monitor监听器检测到HelloServlet 文件发生了变化，context对象的reload()方法被调用，导致host的reload()调用，reload()方法将contextMap中的原来的context移除，再新建一个context对象添加到contextMap中（其实path和docBase参数同旧的context一样），即重载（重新新建了一个context）。 由于context是新的，因此它的servletPool已经不是原来的了，而是新的空servletPool，再次访问同一个地址时，将重新put一个新的servlet对象。所以答案是拿到的不是原来的对象了。

> tomcat容器是如何创建servlet类实例？用到了什么原理

1. 当容器启动时，会读取在webapps目录下所有的web应用中的web.xml文件，然后对 xml文件进行解析，并读取servlet注册信息。然后，将每个应用中注册的servlet类都进行加载，并通过反射的方式实例化。（有时候也是在第一次请求时实例化）
2. 在servlet注册时加上1如果为正数，则在一开始就实例化，如果不写或为负数，则第一次请求实例化。

### InvokerServlet

service方法会利用反射创建servlet实例。在实例化 servlet 对象的时候，根据类全限定名通过context.getWebappClassLoader().loadClass() 去获取类对象，后面再根据这个类对象，实例化出 servlet 对象出来。

### DefaultServlet

- 什么叫做缺省Servlet？凡是在web.xml文件中找不到匹配的元素的URL，它们的访问请求都将交给缺省Servlet处理，也就是说，缺省Servlet用于处理所有其他Servlet都不处理的访问请求
- 既然我说了在web访问任何资源都是在访问Servlet，那么我访问静态资源【本地图片，本地HTML文件】也是在访问这个缺省Servlet【DefaultServlet】

根据访问应用路径来寻找对应的文件。

### 文件格式

文件有各种格式，比如 png, jpg, txt, html , exe 等等，但是浏览器却不能理解这些后缀名，它能理解的是 mime-type，比如 png 对应的 mime-type 是 image/png。 当它拿到的响应告诉它 mime-type 是 image/png 的时候，它就会按照对应的格式去理解和解析这个图片。

所以我们要把不同后缀名的文件，翻译成对应的 mime-type 然后在 http 响应中，用 Content-type 这个头信息告诉浏览器，这样浏览器就可以更好地进行解析工作了。

将文件格式放入conf/web.xml中，调用WebXMLUtil解析将文件扩展名和mimeType存入HashMap。根据后缀名获取mimeType第一次调用会初始化，如果找不到对应的，就默认返回 "text/html"，getMimeType这里做了 synchronized 线程安全的处理，因为会调用 initMimeType 进行初始化，如果两个线程同时来，那么可能导致被初始化两次。

## 类加载器

 java 的源文件是后缀名为 java 的文件。 这些 java 文件会被编译成 .class 文件。 当JVM ( java 虚拟机） 启动时候，就会把这些 .class 文件转转换为 JVM 可以识别的类。而转换过程就是由类加载器来完成的， 类加载器 就是扮演一个把硬盘上的 .class 文件转换为 虚拟机里的 “类” 的这么一种角色。

### CommonClassLoader

IDE导入的jar包在使用IDE的时候才会被AppClassLoader加载，如果不写CommonClassLoader那么在使用bat方式启动tomcat的时候就不会加载jar包。

### WebappClassLoader

WebappClassLoader 是专门用于加载某个 web 应用下的 class 和 jar 的。

每个 Web 应用都有属于自己专属的 WebClassLoader，这样才可以做到同一个名称的类，在不同的 web 应用里，互不干扰。

### 总结

![图片](https://mmbiz.qpic.cn/mmbiz_jpg/E44aHibktsKYexQU7CgibSMw73fSprowKUmPt3awtOIESGe0zYKThJx7hia0qUGqMClVOXahMNepiczaHYT7iaTwWhw/640?wx_fmt=jpeg&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

在初学时部署项目，我们是把war包放到tomcat的webapp下，这意味着一个tomcat可以运行多个Web应用程序

那假设我现在有两个Web应用程序，它们都有一个类，叫做User，并且它们的类全限定名都一样，比如都是com.yyy.User。但是他们的具体实现是不一样的。

Tomcat给每个 Web 应用创建一个类加载器实例（WebAppClassLoader），该加载器重写了loadClass方法，优先加载当前应用目录下的类，如果当前找不到了，才一层一层往上找。

那这样就做到了Web应用层级的隔离。

并不是Web应用程序下的所有依赖都需要隔离的，因为如果版本相同，没必要每个Web应用程序都独自加载一份啊。

做法也很简单，Tomcat就在WebAppClassLoader上加了个父类加载器（SharedClassLoader），如果WebAppClassLoader自身没有加载到某个类，那就委托SharedClassLoader去加载。

就是把需要应用程序之间需要共享的类放到一个共享目录下嘛

为了隔绝Web应用程序与Tomcat本身的类，又有类加载器(CatalinaClassLoader)来装载Tomcat本身的依赖

如果Tomcat本身的依赖和Web应用还需要共享，那么还有类加载器(CommonClassLoader)来装载进而达到共享。

## 热加载

热加载指的是当一个web项目的classes目录下的资源发生了变化，或者 lib 里的 jar 发生了变化，那么就会重新加载当前 Context， 那么就不需要重新启动 Tomcat 也能观察到 class 修改之后的效果。

创建一个监视器来监听应用目录下的文件，当修改、创建、删除特定的文件或者丢失事件时，重新创建一个新应用并删除旧应用来更新HashMap中的映射。

处理方法需要加上 synchronized 同步。 因为这是一个异步处理的，当文件发生变化，会发过来很多次事件。所以我们得一个一个事件的处理，否则搞不好就会让 Context 重载多次。

监视器监听的路径需要注册，而IDE build的时间是删除classes目录并创建新的，所以旧的注册目录就失效了，需要重新注册路径。

## Servlet Context

当Tomcat启动的时候，就会创建一个ServletContext对象。它代表着当前web站点

1. ServletContext既然代表着当前web站点，那么所有Servlet都共享着一个ServletContext对象，所以Servlet之间可以通过ServletContext实现通讯。
2. ServletConfig获取的是配置的是单个Servlet的参数信息，ServletContext可以获取的是配置整个web站点的参数信息
3. 利用ServletContext读取web站点的资源文件
4. 实现Servlet的转发【用ServletContext转发不多，主要用request转发】

## 自启动

在web.xml中配置load-on-startup表明这个servlet需要自启动，利用反射进行实例化。

不用再等三个servlet来处理请求了。

## GZIP

服务端和浏览器之间传递数据大部分时候都是 文本信息，作为文本信息可以有较大的压缩率。 那么在压缩之后，进行传输效率就会高不少，也节约服务器的带宽。压缩之后的数据到了浏览器再进行解压，就和原来一样了。

gzip 是一种压缩方式， tomcat 一般会使用这种方式。在响应报文首部字段设置`Content-Encoding:gzip`。

可以在server.xml中设置是否压缩、最小进行压缩的字节数、不进行压缩的浏览器、哪种类型的文件需要压缩。

## 过滤器

### 责任链模式

过滤器采用了责任链模式。

责任链模式的定义：使多个对象都有机会处理请求，从而避免请求的发送者和接受者之间的耦合关系， 将这个对象连成一条链，并沿着这条链传递该请求，直到有一个对象处理他为止。

对Web应用来说，**过滤器是一个驻留在服务器端的Web组件**，它可以截取客户端和服务器之间的请求与响应信息，并对这些信息进行过 滤。当Web容器接受到一个对资源的请求时，它将判断是否有过滤器与这个资源相关联。如果有，那么容器将把请求交给过滤器进行处理。在过滤器中，**你可以改 变请求的内容，或者重新设置请求的报头信息，然后再将请求发送给目标资源。当目标资源对请求作出响应时候，容器同样会将响应先转发给过滤器，再过滤器中， 你可以对响应的内容进行转换，然后再将响应发送到客户端。**

![图片](https://mmbiz.qpic.cn/sz_mmbiz_jpg/2BGWl1qPxib2ibKw6VP7pTWcmAKttDPkmYicvuShBleBG5s1icA4ianVKxeD4XXLBrPgXXwXgCgN7lHIKScJHdws5Wg/640?wx_fmt=jpeg&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)


## Context监听器

### 概述

**什么是监听器：**监听器就是一个实现特定接口的普通java程序，这个程序专门用于监听另一个java对象的方法调用或属性改变，当被监听对象发生上述事件后，监听器某个方法将立即被执行。

**监听器作用：**监听器可以用来检测网站的在线人数，统计网站的访问量等等

**在Tomcat中的作用：**Tomcat 具备监听器功能，当 Request, Session, Context 出现生命周期事件的时候，就会触发对应事件，如果相关事件被安装了监听器的话就会被触发。

应用了观察者设计模式

## war部署

### 静态war部署

所谓的静态部署就是tomcat 还没有启动的时候， war 就已经存在于 webapps 目录下了。

1. 扫描webapps 目录，处理所有的 war 文件
2. 把 war 文件解压为目录，并把文件夹加载为 Context

### 动态war部署

war 动态部署的概念就是 tomcat已经处于运行状态了，此时向 webapps 目录下扔一个 war 文件，就会自动解压并部署对应的 Context。

创建一个监视器，监控webapps文件，当发现创建了新的war文件时，就加载这个war文件

## JSP转义和编译

由于JSP在第一次访问是要经过JSP引擎翻译成Servlet才能运行。因此，在第一次访问JSP页面是比较慢。在Tomcat的文档中提供了一种通过ant将JSP页面翻译成.class文件再发布的方法，通过这种方法就可以有效地解决这个问题。

jsp 的处理有两个重要步骤， 一个是 先把 jsp 转移成 .java 文件。 然后再把这个 .java 文件编译成为 .class 文件。

按照Tomcat 的逻辑，当一个 jsp 被转译成为 .java 文件之后，会被保存在 %TOMCAT_HOME%/ work 这个目录下。

这里进行了JspRuntimeContext 的初始化，就是为了能够在jsp所转换的 java 文件里的 **javax.servlet.jsp.JspFactory.getDefaultFactory()** 这行能够有返回值

Jsp 转移和编译过程非常复杂，其中最核心的是用到了 JspC 这个类。

