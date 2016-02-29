# Otto介绍

来源:[云在千峰](http://blog.chengyunfeng.com/?p=450#ixzz2mbSOkNrD)

[Otto](https://github.com/square/otto) 是Android系统的一个Event Bus模式类库。用来简化应用组件间的通信。关于Event Bus模式的详细情况，请[参考这里](http://yunfeng.sinaapp.com/?p=449)。
Otto的使用是比较简单的，先到项目主页下载源码：[https://github.com/square/otto](https://github.com/square/otto)

下载后的源码目录中包含一个library和sample目录， library目录是类库源代码；sample目录是示例代码。
主要使用com.squareup.otto.Bus类、@Produce、 @Subscribe 注解。

在组件的相关生命周期中通过Bus类的register 函数来注册，然后Bus类会扫描改类中带有`@Produce`和`@Subscribe`注解的函数。

`@Subscribe`注解告诉Bus该函数订阅了一个事件，该事件的类型为该函数的参数类型；而`@Produce`注解告诉Bus该函数是一个事件产生者，产生的事件类型为该函数的返回值。

可以在Activity或者Fragment的onResume函数中注册监听器；在onPause函数中取消注册：

```
@Override protected void onResume() {
    super.onResume();
 
    // Register outselves so that we can provide the initial value.
    BusProvider.getInstance().register(this);
  }
 
  @Override protected void onPause() {
    super.onPause();
 
    // Always unregister when an object no longer should be on the bus.
    BusProvider.getInstance().unregister(this);
  }

```

对于在[前面文章](http://yunfeng.sinaapp.com/?p=449)中介绍的场景，可以定义一个位置产生函数：

```
  @Produce public LocationChangedEvent produceLocationEvent() {
    // Provide an initial value for location based on the last known position.
    return new LocationChangedEvent(lastLatitude, lastLongitude);
  }
```

该函数的@Produce注解告诉Bus该函数可以产生一个类型为`LocationChangedEvent`的事件，当有该类型的事件向Bus注册的时候， Bus会调用该函数获取初始值，并使用该值来调用订阅该事件的函数。

而对于需要订阅该事件的地方，通过@Subscribe注解来告诉Bus：


```
  @Subscribe public void onLocationChanged(LocationChangedEvent event) {
    locationEvents.add(0, event.toString());
    if (adapter != null) {
      adapter.notifyDataSetChanged();
    }
  }
```

需要注意的是，不管是生产者还是订阅者都需要向Bus注册自己：

```
bus.register(this);
```

如果您认为在每个Activity或者Fragment的onResume和onPause函数中都需要调用bus.register(this)和bus.unregister(this)函数比较麻烦的话，可以通过一个Bus包装类来自动完成注册的工作，然后在您的类中只需要继承基类，并调用函数getScopedBus().register(…) 来注册需要的对象即可。详细情况参考示例代码：[https://gist.github.com/3057437](https://gist.github.com/3057437)

执行线程

Otto的事件调用默认是在主线程（应用的UI线程）中调用，

```
// 下面两种声明方式是一样的效果.
Bus bus1 = new Bus();
Bus bus2 = new Bus(ThreadEnforcer.MAIN);
```

如果您不关注在那个线程中执行事件函数，则可以通过 ThreadEnforcer.ANY 参数来初始化Bus对象，如果上面2个线程控制方式满足不了您的需求，您还可以通过实现ThreadEnforcer接口来定义自己的线程模型、或者使用[EventBus](https://github.com/greenrobot/EventBus)类库，我们将在下一篇文章中介绍EventBus和Otto的区别。