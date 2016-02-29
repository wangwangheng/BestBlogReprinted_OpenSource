# okio分析

来源:[www.baitouwei.com](http://www.baitouwei.com/2015/01/08/okio/)

本篇分析基于[Okio](https://github.com/square/okio)1.2.0

Okio是一个对原有的java.io和java.nio进行改进的IO库，使IO操作更加高效和方便。Okio的高效主要体现在三个方面：

* 一它对数据进行了分块处理，这样在大数据IO的时候可以以块为单位进行IO，这可以提高IO的吞吐率。
* 二它对这些数据块使用链表进行管理，这可以仅通过移动“指针”就进行数据的管理，而不用真正去处理数据，而且对扩容来说也十分方便。
* 三对闲置的块进行管理，通过一个块池（SegmentPool）的管理，避免系统GC和申请byte时的zero-fill。

其他的还有一些小细节上的优化，比如如果你把一个UTF-8的String转为ByteString，ByteString会保留一份对原来String的引用，这样当你下次需要decode这个String时，程序通过保留的引用直接返回对应的String，从而避免了转码过程。

Okio的方便主要体现在，它对数据的读取写入进行了封装，调用者可以十分方便的进行各种值(string,short,int,hex,utf-8,base64等等)的转化，还有一点就是它为所有的Source和Sink提供了超时操作，这在Java原生的IO里是没有的。

## Okio几个基础的类和接口：
* 类Segment

一个Segment相当于一个数据块（由一个byte数组构成），一般存在于一个双向循环队列（也就是buffer）或单链表（也就是SegmentPool）中,通过pop()和push(Segment segment)方法可以Segment进行入队和出队操作。Segment部分重要字段如下：

```
int SIZE：Segment大小，2kb
int pos：指向下一个可读的byte	
int limit：指向下一个可写的byte
Segment next：相当于链表的指针，指向下一个Segment
Segment prev：相当于链表的指针，指向上一个Segment
```

* 类SegmentPool

一个Segment池，由一个单向链表构成。该池负责Segment的回收和闲置Segment的管理，一般Segment都是从该池获取的。该池是线程安全的。

* 接口Sink，BufferedSink

Sink和java.io中的OutputStream类似，BufferedSink是一个对Sink进行扩展的接口。使用OutputStream时，在传输不同的数据是需要对OutputStream进行不同的包装，比如用DataOutputStream进行原始数据的IO，用BufferedOutputStream进行带缓存的数据IO，用OutputStreamWriter进行字符编码。对于Sink来说，只需要使用BufferedSink就可以实现以上所有的情况。

* 接口Source和BufferedSource

Source和java.io中的InputStream类似，BufferedSource是一个对Source进行扩展的接口。使用InputStream时，在传输不同的数据是需要对InputStream进行不同的包装，比如用DataInputStream进行原始数据的IO，用BufferedInputStream进行带缓存的数据IO，用OutputStreamReader进行字符编码。对于Source来说，只需要使用BufferedSource就可以实现以上所有的情况。

* 类Buffer

Buffer是一个不固定大小的byte序列（由一个节点为Segment的双向循环队列链表构成），它充当Sink、Source、InputStream和OutputStream间的高效缓存区，由于Buffer实现了BufferedSource和BufferedSink这两个接口，所以可以很方便的对其进行IO操作。Buffer在多线程编程里很有用，比如一个负责网络的线程可以通过这种方式和工作线程进行数据交换，但是又不发生数据的复制。

* 类ByteString

ByteString是一个固定大小的byte序列（由一个byte数组构成）。String是Java经常使用到的一个基本类型，ByteString对String进行了封装，为byte和String间的转换和String不同值间的转换（UTF-8编解码，Hex编解码，Base64编解码，ASCIll编解码）提供了十分方便的操作。

* 类AsyncTimeout

AsyncTimeout为所有的Source和Sink提供了超时功能，Java原生的IO并没有超时功能，而AsyncTimeout填补了这点。AsyncTimeout的实现原理是：AsyncTimeout相当于一个节点，每个节点都带有tiemout信息，程序维护一条由AsyncTimeout节点组成***优先队列链表***（剩余超时时间越小的排越前），然后通过后台的一个守护线程，不断的去轮询这条链表，如果对应节点超时就调用Interrupted进行中断，否则调用wait进行等待。

## Okio高效在哪里
* 前面说过Okio之所以高效是因为在底层的数据结构上，它维护了一个由Segment构成的链表循环队列，一个Segment相当于一个数据块。这样的好处很明显。因为在一块数据块的进行IO的过程中是没有中断的，相比于每次只读一个byte，单位时间内IO的数据量当然更高。那是不是Segment越大越好？当然不是。因为Segment内数据的IO还是以byte为单位的，如果Segment过大的话，数据就不能很好的进行分块。想象下把数据只分为一个大的Segment，那每次IO不就是以byte为单位了吗？那一个Segment的大小为多少比较合适，在我看来，最好和计算机中的一个页面大小一致。

* 另一方面，由于使用了链表，这使得数据的管理十分高效，因为只要移动指针就可以进行数据的移动。SegmentPool是Segment组成的单向链表，负责Segment的回收和闲置Segment的维护。

我们可以看看SegmentPool是如何维护闲置Segment的，SegmentPool提供了两个方法，take()用于获取一个闲置Segment，recycle(Segment segment)用于回收一个Segment。

```
Segment take() {
	synchronized (this) {
	if (next != null) {
	    Segment result = next;
	    next = result.next;
	    result.next = null;
	    byteCount -= Segment.SIZE;
	    return result;
	  }
	}
	return new Segment(); // Pool is empty. Don't zero-fill while holding a lock.
}
```

由于SegmentPool的next指向Pool中闲置的Segment，所以直接返回next指向的Segment就可以了，当没有闲置的Segment是就新建一个返回。

```
void recycle(Segment segment) {
    if (segment.next != null || segment.prev != null) throw new IllegalArgumentException();
    synchronized (this) {
      if (byteCount + Segment.SIZE > MAX_SIZE) return; // Pool is full.
      byteCount += Segment.SIZE;
      segment.next = next;
      segment.pos = segment.limit = 0;
      next = segment;
    }
}
```

在Pool不满的情况下，recycle只要将对应的segment插入到单向链表的头部（也就是next指向segment的前面）就相当于回收了该segment。

okio的高效主要体现在Buffer中，所以我们可以看看Buffer的实现。由于每个Segment里都有pos和limit两个下标，这和Java NIO里的Buffer有点像，只要通过对pos和limit进行操作，我们就可以判断当前Segment是否写满（limit==SIZE）、是否读完（pos==limit），这就提高了IO的效率了。再看看readFrom()和writeTo()方法都是尽量以块大小进行IO的。而且为了进行减少调用系统申请内存产生的消耗，Buffer使用了SegmentPool进行Segment的回收和申请。

## Okio方便在哪里
* Okio之所以方便主要体现在它对许多常用的操作进行了封装，主要体现在BufferedSink和BufferedSource接口为调用者提供了丰富的方法，想基本数据（short，int，long，string）的IO还有Sink和Source之间的转换等，它都提供了相应的方法；另一方面，Okio创建了一个新的数据类型ByteString，ByteString对String进行了封装，为byte和String间的转换和String不同值间的转换（UTF-8编解码，Hex编解码，Base64编解码，ASCIll编解码，大小端转换）提供了十分方便的操作，具体可以查看其相应的方法。
* Okio通过AsyncTimeout所有的Source和Sink提供了超时操作。Timeout是AsyncTimeout的基类，想看看Timeout实现了什么？Timeout对原本的概念进行可扩展，它有两个属性，Timeouts和Deadlines。
   * Timeouts：代表一个时间段，表示等待一个操作执行完毕的最长时间。Timeouts一般用来检测类似网络中断等问题。
   * Deadlines：代表一个时间点，表示一个job（包含多个操作）最长执行到某个时间点。Deadlines可以为一个job设定它的执行时间上限，比如一个对电池电量敏感的APP为了节省电池消耗，也许会在APP content的预加载上设置Deadlines。

AsyncTimeout的主要字段有：

```
private static AsyncTimeout head：私有静态变量head，用来当单链表的头
private AsyncTimeout next：指向下一个AsyncTiemout的节点
```

要实现对应Source和Sink的Timeout管理只需要管理这条AsyncTimeout优先队列链表就可以了。添加Timeout对应入队（scheduleTimeout方法），取消超时对应出队（cancelScheduledTimeout方法）。守护线程Watchdog，会不断的去轮询这条链表，如果对应节点超时就调用Interrupted进行中断，否则调用wait进行等待。

## Okio中的其他类：
* RealBufferedSink和RealBufferedSource，不带缓存的Sink和Source，实现方式是在每次write或read之后都调用emitCompleteSegments()方法（emitCompleteSegments()方法会将Buffer中的数据flush掉）。
* ForwardingSink和ForwardingSource，将调用委托给其他Sink或Source的抽象类，在子类化的时候有用。比如当需要实现一个匿名Sink或Source时，就可以用这个。
* GzipSink和GzipSource，实现了Gzip的Sink和Source。
* DeflaterSink和InflaterSource，实现了ZLIB压缩和解压的Sink和Source，在DeflaterSink这个类中，由于每次调用flush()程序都会对整个Buffer进行同步压缩，所以官方建议只在程序有必要时才主动去调用flush(),否则频繁的调用flush()会引起性能上的问题。

从上面这些类看出，我们完全可以对Sink和Source进行扩展，以实现一些针对不同compression或encryption的Sink和Source，比如可以编写针对H264的H264EnCodeSink和H264DeCodeSource。