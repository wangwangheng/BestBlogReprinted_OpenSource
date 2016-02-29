# Facebook Rebound 弹性动画库 源码分析

来源:[markzhai's home](http://blog.zhaiyifan.cn/2015/09/10/Facebook-Rebound-%E5%BC%B9%E6%80%A7%E5%8A%A8%E7%94%BB%E5%BA%93-%E6%BA%90%E7%A0%81%E5%88%86%E6%9E%90/)

[demo-apk]:rebound-android-playground-debug.apk
[rebound-github]:https://github.com/facebook/rebound
[backboard]:https://github.com/tumblr/Backboard
[Hookean]:http://baike.baidu.com/view/127907.htm

## Rebound源码分析
对于想体验一下rebound的效果，又懒得clone和编译代码的，这里提供一个[demo apk][demo-apk]。

今天看到了tumblr发布了基于[rebound][rebound-github]的[Backboard][backboard]，本想直接分析一下Backboard对rebound做了些什么，不过考虑到rebound还没有仔细分析过，所以这里做一下源码分析。

对外部来说，首先接触的就是`SpringSystem`了，但在说它之前，先让我们看看Spring是什么。

## Spring
Spring通过可设置的**摩擦力(Friction)**和**张力(tension)**实现了[胡克定律][Hookean]，通过代码模拟了物理场景：

```
private static class PhysicsState {
  double position;
  double velocity;
}

private final PhysicsState mCurrentState = new PhysicsState();
private final PhysicsState mPreviousState = new PhysicsState();
private final PhysicsState mTempState = new PhysicsState();
private double mStartValue;
private double mEndValue;
```

每个spring从`mStartValue`到`mEndValue`进行运动，内部维护了当前状态、前值状态，以及临时状态，每个状态由通过位置和速度来描述，而运动的推进逻辑则在

```
void advance(double realDeltaTime)
```
`advance`方法中，`SpringSystem`会遍历由其管理的所有Spring实例，对它们进行`advance`。

## SpringListener

每个`Spring`内部都维护着一个`SpringListener`数组，这也是我们经常会需要去实现的一个接口：

```
public interface SpringListener {
  void onSpringUpdate(Spring spring);
  void onSpringAtRest(Spring spring);
  void onSpringActivate(Spring spring);
  void onSpringEndStateChange(Spring spring);
}
```

按照先后顺序：

* onSpringActivate在首次开始运动时候调用。
* onSpringUpdate在advance后调用，表示状态更新。
* onSpringAtRest在进入rest状态后调用。
* onSpringEndStateChange则略有不同，仅在`setEndValue`中被调用，且该Spring需要在运动中且新的endValue不等于原endValue。

## SpringSystem

`SpringSystem`继承了`BaseSpringSystem`，对外提供了一个静态create方法，并屏蔽了`Construtor`：

```
public static SpringSystem create() {
  return new SpringSystem(AndroidSpringLooperFactory.createSpringLooper());
}

private SpringSystem(SpringLooper springLooper) {
  super(springLooper);
}
```

可以看到create方法里面默认给了一个`SpringLooper`的工厂类创建实例（内部根据系统版本是否>=3.0返回了不同的子类实例），而`SpringLooper`顾名思义是一个`Looper`，做的就是不断地更新`SpringSystem`的状态，实际调用了`BaseSpringSystem的loop`方法：

```
/**
 * loop the system until idle
 * @param elapsedMillis elapsed milliseconds
 */
public void loop(double elapsedMillis) {
  for (SpringSystemListener listener : mListeners) {
    listener.onBeforeIntegrate(this);
  }
  advance(elapsedMillis);
  if (mActiveSprings.isEmpty()) {
    mIdle = true;
  }
  for (SpringSystemListener listener : mListeners) {
    listener.onAfterIntegrate(this);
  }
  if (mIdle) {
    mSpringLooper.stop();
  }
}
```

即通过每次`elapse`的时间，来把`system`往前`advance`（有点类似游戏里，每一帧的运动，如果不够快就会掉帧，这里对应地，`elapsedMillis`则可能会很大）。

大部分的逻辑其实在`BaseSpringSystem`:

```
public class BaseSpringSystem {

  private final Map<String, Spring> mSpringRegistry = 
  									new HashMap<String, Spring>();
  									
  private final Set<Spring> mActiveSprings = new CopyOnWriteArraySet<Spring>();
  
  private final SpringLooper mSpringLooper;
  
  private final CopyOnWriteArraySet<SpringSystemListener> mListeners = 
  								new CopyOnWriteArraySet<SpringSystemListener>();
  private boolean mIdle = true;
```

`mSpringRegistry`保存了所有由该`SpringSystem`管理的`Spring`实例，键值`String`则是`Spring`内的一个自增id，每个`Spring`实例的id都会不同。通过`createSpring`创建的`Spring`实例都会直接被加到该`HashMap`。

`mActiveSprings`内放的是被激活的`Spring`，实际在调用`Spring.java`:

```
public Spring setCurrentValue(double currentValue, boolean setAtRest);
public Spring setEndValue(double endValue);
public Spring setVelocity(double velocity);
```

三个方法的时候才会进行激活，且在实际loop过程中，也只会对激活的`Spring`进行`advance`。

`mSpringLooper`是该`SpringSystem`绑定的`Looper`。

`mListeners`是注册在该`SpringSystem`上的`SpringSystemListener`

```
public interface SpringSystemListener {
  void onBeforeIntegrate(BaseSpringSystem springSystem);
  void onAfterIntegrate(BaseSpringSystem springSystem);
}
```

会在`SpringSystem`的loop方法开始和结束时候调用`onBeforeIntegrate`以及`onAfterIntegrate`，比如可以在所有`Spring loop`完之后检查它们的值，并进行速度限制，暂停等操作，相对于绑定到`Spring`的`SpringListener`，这个更全局一些。

## SpringChain

顾名思义，`SpringChain`就是连锁`Spring`，由数个`Spring`结合而成，且两两相连，可以用来做一些连锁的效果，比如数个图片之间的牵引效果。

每个`SpringChain`都会有一个`control spring`来作为带头大哥，在链中前后的`Spring`都会被他们的前任所拉动。比如我们有 1 2 3 4 5五个`Spring`，选择3作为带头大哥，则3开始运动后，会分别拉动2和4，然后2会拉1，4则去拉动5。

```
private SpringChain(
    int mainTension,
    int mainFriction,
    int attachmentTension,
    int attachmentFriction) {
  mMainSpringConfig = SpringConfig.fromOrigamiTensionAndFriction(mainTension, mainFriction);
  mAttachmentSpringConfig =
      SpringConfig.fromOrigamiTensionAndFriction(attachmentTension, attachmentFriction);
  registry.addSpringConfig(mMainSpringConfig, "main spring " + id++);
  registry.addSpringConfig(mAttachmentSpringConfig, "attachment spring " + id++);
}
```

`SpringChain`有两个配置：

`ControlSpring`使用`mMainSpringConfig`。
其他`Spring`则使用`mAttachmentSpringConfig`。
在什么参数都不带的构造函数中，会默认给出如下参数

```
private static final int DEFAULT_MAIN_TENSION = 40;
private static final int DEFAULT_MAIN_FRICTION = 6;
private static final int DEFAULT_ATTACHMENT_TENSION = 70;
private static final int DEFAULT_ATTACHMENT_FRICTION = 10;
```

即`ControlSpring`摩擦力和张力都会相对小一些。

`SpringChain`本身实现了`SpringListener`，并使用那些接口来进行整个`chain`的更新。

```
@Override
public void onSpringUpdate(Spring spring) {
    // 获得control spring的索引，并更新前后Spring的endValue，从而触发连锁影响
    int idx = mSprings.indexOf(spring);
    SpringListener listener = mListeners.get(idx);
    int above = -1;
    int below = -1;
    if (idx == mControlSpringIndex) {
        below = idx - 1;
        above = idx + 1;
    } else if (idx < mControlSpringIndex) {
        below = idx - 1;
    } else if (idx > mControlSpringIndex) {
        above = idx + 1;
    }
    if (above > -1 && above < mSprings.size()) {
        mSprings.get(above).setEndValue(spring.getCurrentValue());
    }
    if (below > -1 && below < mSprings.size()) {
        mSprings.get(below).setEndValue(spring.getCurrentValue());
    }
    listener.onSpringUpdate(spring);
}

@Override
public void onSpringAtRest(Spring spring) {
    int idx = mSprings.indexOf(spring);
    mListeners.get(idx).onSpringAtRest(spring);
}

@Override
public void onSpringActivate(Spring spring) {
    int idx = mSprings.indexOf(spring);
    mListeners.get(idx).onSpringActivate(spring);
}

@Override
public void onSpringEndStateChange(Spring spring) {
    int idx = mSprings.indexOf(spring);
    mListeners.get(idx).onSpringEndStateChange(spring);
}
```

通常我们想要这个`SpringChain`进行运动会调用`mSpringChain.setControlSpringIndex(0).getControlSpring().setEndValue(1);`

`ControlSpring`便会开始运动，并调用到`SpringChain`作为`SpringListener`的那些方法，进而整个系统作为一个链开始运动。

## SpringConfiguratorView

`SpringConfiguratorView`继承了`FrameLayout`，如果体验过`demo apk`的同学，应该注意到屏幕底下上拉可以对`Spring`的参数进行配置，这就是由`SpringConfiguratorView`做的了。

## AnimationQueue

同样是用来做连锁动画的，不过`Backboard`没有用到这个，`Facebook`自己的例子也没有用过该类，以前做动画的时候用过这个，结果貌似是有什么坑，最后改成了`SpringChain`去实现。

`AnimationQueue`本身和`Rebound`没有任何关系，内部定义了接口

```
public interface Callback {
    void onFrame(Double value);
}
```

原理倒是有点像rebound。由于和rebound本身没关系，这里就不多说了。

