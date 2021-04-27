import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Objects.isNull;

public final class AsyncHttpClient {

  public static void main(String[] args) throws Exception {
    Map<String, String> requestsToSend = new HashMap<>();
    requestsToSend.put("www.google.com", "/");
    requestsToSend.put("www.ya.ru", "/");


    Selector selector = Selector.open();
    SocketChannel s1 = SocketChannel.open();
    s1.configureBlocking(false);
    s1.connect(new InetSocketAddress("www.google.com", 80));
    s1.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE, "www.google.com");
    SocketChannel s2 = SocketChannel.open();
    s2.configureBlocking(false);
    s2.connect(new InetSocketAddress("www.ya.ru", 80));
    s2.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE, "www.ya.ru");
    ByteBuffer readBuffer = ByteBuffer.allocateDirect(1024 * 32);
    while (selector.isOpen()) {
      if (selector.select() > 0) {
        if (processReadySet(selector.selectedKeys(), readBuffer, requestsToSend)) {
          break;
        }
      }
    }
    s1.close();
    s2.close();
  }

  public static Boolean processReadySet(Set readySet, ByteBuffer readBuffer, Map<String, String> requestsToSend) throws Exception {
    SelectionKey key = null;
    Iterator iterator = readySet.iterator();
    while (iterator.hasNext()) {
      key = (SelectionKey) iterator.next();
      iterator.remove();
    }
    if (key.isConnectable()) {
      Boolean connected = processConnect(key);
      if (!connected) {
        return true;
      }
    }
    if (key.isReadable()) {
      SocketChannel sc = (SocketChannel) key.channel();
      int read = sc.read(readBuffer);
      readBuffer.flip();
      if (read == -1) {
        System.out.printf("%s closed the connection", key.attachment());
        key.cancel();
        return false;
      }
      byte[] blob = new byte[read];
      readBuffer.get(blob, 0, read);
      String result = new String(blob);
      System.out.printf("%s says: %s%n", key.attachment(), result);
    }
    if (key.isWritable()) {
      String request = requestsToSend.remove((String) key.attachment());
      if (isNull(request)) {
        return false;
      }
      String msg = format("GET %s HTTP/1.1\r\nHost: %s\r\n\r\n", request, key.attachment());
      System.out.printf("sending request to %s: %s%n", key.attachment(), request);
      SocketChannel sc = (SocketChannel) key.channel();
      ByteBuffer bb = ByteBuffer.wrap(msg.getBytes());
      sc.write(bb);
      key.interestOps(SelectionKey.OP_READ);
    }
    return false;
  }

  public static Boolean processConnect(SelectionKey key) {
    SocketChannel sc = (SocketChannel) key.channel();
    try {
      while (sc.isConnectionPending()) {
        sc.finishConnect();
      }
    } catch (IOException e) {
      key.cancel();
      e.printStackTrace();
      return false;
    }
    return true;
  }
}
