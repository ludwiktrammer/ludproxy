# LudProxy
A caching HTTP proxy I wrote for a class in 2012. I'm still pretty proud of it ;) It was written in Java with an aim for maximal compatibility with RFC 2616.

By default it displayes a GUI window with various diagnostic information about the connections and the cache state.

## Running the proxy
You can run the program using the following command:

```
java -jar ludproxy.jar
```

Optionally you can pass interface address and port as command line arguments. For example for the server to run on local port 5000 use the following command:

```
java -jar ludproxy.jar localhost 5000
```

Additionally the program supports the following command line switches:

**--quiet** no messages will be sent to stdout (errors will still be sent to stderr)

**--no-gui** the program will run without the graphical user interface. In this mode actions not needed by the proxy server itself (like geolocation and traceroute - normally displayed in the GUI window) are skipped.

## Proxy characteristics
### Support for persistent connections
LudProxy supports **persistent connections** with clients, including additional support for pipeling (which means it agrees for clients to send additional requests in the same connection, even before they receive earlier responses)

Part of the technology allowing for persistent connections support is the "chunked" Transfer Encoding support. LudProxy can not only read messages that use the encoding, but also can add the encoding to other messages on the fly (which is sometimes necessary when transferring a message prepared by an original server to be sent over non persistent connection, where there is no real need to specify the precise boundaries of the message).

### Adding/removing headers
LudProxy adds a *Via* header (with information about its host and protocol) to all messages that it proxies (or it appends the information to an existing *Via* header). It also sets the *Age* header with information about the message age (as defined by the RFC).

It removes all *Hop-By-Hop* headers (i.e. headers that only describe a connection between two particular machines), adding its own when necessary (particularly the *Connection* and *Transfer-Encoding* headers).

LudProxy also includes support for *Trailing Headers* (i.e. headers sent after message's body, which sometimes occur with *chunked* transfer-encoding).

### Error pages
When it is not possible to receive a proper response from a server (for example in a case of an unknown domain, unsuccessful connection etc.) LudProxy generates an HTTP response to the client with an appropriate error code (from the 4xx or 5xx ranges). The response includes a user friendly page with detailed information about the error, with additional diagnostic information (request and response headers).

All otherwise unhandled Java exceptions occurring during the LudProxy execution are also converted to user friendly error pages and sent to the client.

## The cache
### *Cache-Control* commands support
LudProxy respects commands in the *Cache-Control* header: *no-cache*, *no-store*, *private*, *must-revalidate*, *max-age*, *min-fresh*, *max-stale*. It also supports the *Pragma* and *Expires* headers.

The *freshness* of an element is calculated based on the algorithms for calculating age and freshness described in RFC 2016.

### Support for conditional requests
Conditional requests are used by LudProxy for communication with both servers and clients. If LudProxy has a fresh response in its cache and receives a conditional requests from a client (i.e. a request with *If-Match* and/or *If-Modified-Since* headers) it checks the condition and based on the result either generates a 304 Not Modified response or responds with a 200 response with content from the cache.

If LudProxy receives a request for which it contains a stale copy in its cache, it generates a conditional request to the server (with *If-Match* and/or *If-Modified-Since* headers) and depending on the response it gives the licent either its cached version or the new content newly received from the server.

### Semantic support for HTTP HEAD
LudProxy understands the meaning of the HTTP HEAD method. It uses its cache to  generate responses to such requests (based on earlier GET responses). It can also use responses to HEAD request to invalidate matching GET responses stored in its cache.

## The GUI interface
### The *Wstęp* tab
The first tab contains information about server's address and port (which should be set in the client's proxy configuration).

### The *Cache* tab
A table containing all elements currently stored in cache. The table includes information about element's URL, its age and freshness (which determines whether an element will be sent to a browser straight from the cache).
There are two buttons available underneath the table: "odśwież listę" (*refresh the list*) and "wyczyść cache" (*clear the cache*).

### The *Połączenia* (*Connections*) tab
#### The table
The table contains information about all connection with servers, from the moment LudProxy started. The table contains the following data:
* ID of a thread that performed the connection
* URL of the requested resource
* HTTP status code of the server response
* MIME type of the received resource
* Did LudProxy sent a conditional request (and if so: what was the result)
* The size of the response
* "Delay" i.e. time that passed between end of the request and beginning of the response

#### The map
The "Połączenia" (*Connections*) tab also contains a world map. Each line on the map symbolizes a sum of all connection with a particular server.

The **thickness** of a line is determined by the sum of all data received during the communication with a server. It starts as 1px, but every 50kb of received data adds another 1px to the thickness. 10px is the maximum thickness.

The **color** of a line is determined by the average delay during the communication with a particular server. The hue goes smoothly from turquoise (delay values close to 0 ms) to green, yellow and ends with red (values close to 500 ms and more).

If users highlights a connection (or multiple connections) in the table, the program highlights corresponding lines on the map. Other lines become "grayed out" and the highlighted lines get additional labels containing addresses of servers (white) and routers that take part in the connection (gray). The routers are identified using the traceroute command.

*Note:* even though the program supports both Unix *traceroute* and Windows *tracert* commands, it is not possible to tell if it fully works with all versions and implementations of those commands without a lot more testing. The software was tested on Mac OS X 10.7 Lion, Ubuntu 10.04 Lucid Lynx (after installation of the "traceroute" packet) and Windows 7.

![The GUI](https://raw.githubusercontent.com/ludwiktrammer/ludproxy/master/screenshot.png)
