![Build project](https://github.com/netty/netty/workflows/Build%20project/badge.svg)

# Netty Project

Netty is an asynchronous event-driven network application framework for rapid development of maintainable high 
performance protocol servers & clients.

## Links

* [Web Site](https://netty.io/)
* [Downloads](https://netty.io/downloads.html)
* [Documentation](https://netty.io/wiki/)
* [@netty_project](https://twitter.com/netty_project)
* [Official Discord server](https://discord.gg/q4aQ2XjaCa)

## How to build

For the detailed information about building and developing Netty, please visit [the developer guide](https://netty.io/wiki/developer-guide.html).  This page only gives very basic information.

You will require the following to build Netty:

* Latest stable [OpenJDK 11](https://adoptium.net/) for Netty 5, [OpenJDK 8](https://adoptium.net/) for older releases.
* Latest stable [Apache Maven](https://maven.apache.org/)
* If you are on Linux or MacOS, you need [additional development packages](https://netty.io/wiki/native-transports.html)
installed on your system, because you'll build the native transport.

Note that this is build-time requirement. JDK 5 (for 3.x) or 6 (for 4.0+ / 4.1+) are enough to run your Netty-based 
application.

## Branches to look

Development of released versions takes place in each branch whose name is identical to `<majorVersion>.<minorVersion>`. 
For example, the development of 3.9 and 4.1 resides in [the branch '3.9'](https://github.com/netty/netty/tree/3.9) and 
[the branch '4.1'](https://github.com/netty/netty/tree/4.1) respectively. Development for Netty 5 resides on the 
[main branch](https://github.com/netty/netty/tree/main).

## Usage with JDK 9+

Netty can be used in modular JDK9+ applications as a collection of automatic modules. The module names follow the
reverse-DNS style, and are derived from subproject names rather than root packages due to historical reasons. They
are listed below:

 * `io.netty5.all`
 * `io.netty5.buffer`
 * `io.netty5.codec`
 * `io.netty5.codec.dns`
 * `io.netty5.codec.http`
 * `io.netty5.codec.http2`
 * `io.netty5.codec.smtp`
 * `io.netty5.codec.xml`
 * `io.netty5.common`
 * `io.netty5.handler`
 * `io.netty5.handler.proxy`
 * `io.netty5.resolver`
 * `io.netty5.resolver.dns`
 * `io.netty5.transport`
 * `io.netty5.transport.epoll` (`native` omitted - reserved keyword in Java)
 * `io.netty5.transport.kqueue` (`native` omitted - reserved keyword in Java)
 * `io.netty5.transport.unix.common` (`native` omitted - reserved keyword in Java)



Automatic modules do not provide any means to declare dependencies, so you need to list each used module separately 
in your `module-info` file.
