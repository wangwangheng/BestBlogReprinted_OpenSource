# Fresco源码解析 - 初始化过程分析

来源:[CSDN-一介码农](http://blog.csdn.net/feelang/article/details/45419097)

使用Fresco之前，一定先要进行初始化，一般初始化的工作会在`Application.onCreate()`完成，当然也可以在使用Drawee之前完成。

Fresco本身提供了两种初始化方式，一种是使用使用默认配置初始化，另一种是使用用户自定义配置。

如下代码是Fresco提供的两个初始化方法。第一个只需要提供一个Context参数，第二个还需要提供`ImagePipeline`的配置实例 - `ImagePipelineConfig`。

```
/** Initializes Fresco with the default config. */
public static void initialize(Context context) {
  ImagePipelineFactory.initialize(context);
  initializeDrawee(context);
}
```

```
/** Initializes Fresco with the specified config. */
public static void initialize(Context context, ImagePipelineConfig imagePipelineConfig) {
  ImagePipelineFactory.initialize(imagePipelineConfig);
  initializeDrawee(context);
}
```

先来分析一下第一种方式。

## 开始初始化

```
Fresco.initialized(context)
```

使用默认参数进行初始化

*com.facebook.drawee.backends.pipeline.Fresco*

```
/** Initializes Fresco with the default config. */
public static void initialize(Context context) {
    ImagePipelineFactory.initialize(context);
    initializeDrawee(context);
}
```

其中`ImagePipeline`负责获取图像数据，可以是网络图片，也可以是本地图片。这里用一个 Factory - `ImagePipelineFactory` 来创建默认的`ImagePipleline`。

## 创建`ImagePipeline`

*com.facebook.imagepipeline.core.ImagePipelineFactory*

```
/** Initializes {@link ImagePipelineFactory} with default config. */
public static void initialize(Context context) {
  initialize(ImagePipelineConfig.newBuilder(context).build());
}
```

`ImagePipelineConfig`为`ImagePipeline`的初始化工作提供了必需的参数，它的构建过程采用了`Builder`模式。

`ImagePipelineConfig`中包含了很多参数，因为我们调用`Fresco.initialize()`的时候值传递了一个`context`参数，所以`Fresco`还没有获取任何用户自定义的数据，因此全部使用默认值，`Builder`类只提供了构建的过程，而默认值则需要等到新建`ImagePipelineConfig`时创建。

通过`Builder`的部分源码可以看出，初始化一个`ImagePipeline`需要很多参数，这些参数的具体意义会在后续的博文中介绍。

```
public static class Builder {
  private Supplier<MemoryCacheParams> mBitmapMemoryCacheParamsSupplier;
  private CacheKeyFactory mCacheKeyFactory;
  private final Context mContext;
  private Supplier<MemoryCacheParams> mEncodedMemoryCacheParamsSupplier;
  private ExecutorSupplier mExecutorSupplier;
  private ImageCacheStatsTracker mImageCacheStatsTracker;
  private ImageDecoder mImageDecoder;
  private Supplier<Boolean> mIsPrefetchEnabledSupplier;
  private DiskCacheConfig mMainDiskCacheConfig;
  private MemoryTrimmableRegistry mMemoryTrimmableRegistry;
  private NetworkFetcher mNetworkFetcher;
  private PoolFactory mPoolFactory;
  private ProgressiveJpegConfig mProgressiveJpegConfig;
  private Set<RequestListener> mRequestListeners;
  private boolean mResizeAndRotateEnabledForNetwork = true;
  private DiskCacheConfig mSmallImageDiskCacheConfig;
  private AnimatedImageFactory mAnimatedImageFactory;

  // other methods
}
```

从 Fresco 的`initialize`方法中我们得知，`ImagePipelineConfig`是这么创建的：

```
ImagePipelineConfig.newBuilder(context).build())
```

而`Builder`并没有提供参数的默认值，那默认值肯定是在 buid() 方法完成赋值。

*com.facebook.imagepipeline.core.ImagePipelineFactory$Builder*

```
public ImagePipelineConfig build() {
  return new ImagePipelineConfig(this);
}
```

由以上代码可以看出，`build()`会创建一个`ImagePipelineConfig`，然后把`this`作为参数传给构造函数，而`ImagePipelineConfig`的构造函数就是根据`Builder`来初始化自己。

初始化的策略非常简单：

* 如果`builder`中的参数值为空，则使用默认值。
* 如果`builder`中的参数值不为空，则使用`Builder`提供的值。

可以通过一个具体的参数来看一下，如果`builder.mBitmapMemoryCacheParamsSupplier`为空，则`new DefaultBitmapMemoryCacheParamsSupplier()`，如果不空，则使用`builder.mBitmapMemoryCacheParamsSupplier`。

```
mBitmapMemoryCacheParamsSupplier =
    builder.mBitmapMemoryCacheParamsSupplier == null ?
        new DefaultBitmapMemoryCacheParamsSupplier(
            (ActivityManager) builder.mContext.getSystemService(Context.ACTIVITY_SERVICE)) :
        builder.mBitmapMemoryCacheParamsSupplier;
```

最后把这个`build`出来的`ImagePipelineConfig`实例传给`ImagePipelineFactory`的静态方法`initialize`，完成初始化。

```
/** Initializes {@link ImagePipelineFactory} with the specified config. */
public static void initialize(ImagePipelineConfig imagePipelineConfig) {
  sInstance = new ImagePipelineFactory(imagePipelineConfig);
}
```

`ImagePipelineFactory`的实例`sInstance`会在初始化`Drawee` 的时候用到。

## 初始化 Drawee

通过以上分析我们了解到，Fresco 会首先初始化`ImagePipeline`，并把`ImagePipeline`的实例保存在一个 `ImagePipelineFactory`类型的静态变量中 - `sInstance`；然后开始初始化`Drawee`。

```
private static void initializeDrawee(Context context) {
  sDraweeControllerBuilderSupplier = new PipelineDraweeControllerBuilderSupplier(context);
  SimpleDraweeView.initialize(sDraweeControllerBuilderSupplier);
}
```

首先，`new`一个`PipelineDraweeControllerBuilderSupplier`，它是`PipelineDraweeControllerBuilder`的一个`Supplier`。`Supplier`不是由`JDK`提供的，而是`Fresco`直接从`guava`中移过来的，代码简单，只提供了一个 get 方法。

```
/**
 * A class that can supply objects of a single type.  Semantically, this could
 * be a factory, generator, builder, closure, or something else entirely. No
 * guarantees are implied by this interface.
 *
 * @author Harry Heymann
 * @since 2.0 (imported from Google Collections Library)
 */
public interface Supplier<T> {
  /**
   * Retrieves an instance of the appropriate type. The returned object may or
   * may not be a new instance, depending on the implementation.
   *
   * @return an instance of the appropriate type
   */
  T get();
}
```

顾名思义，`Supplier`是一个提供者，用户包括但不限于`factory`, `generator`, `builder`, `closure`，接口方法 get() 用于返回它所提供的实例，需要注意的是，这个实例可以是新建的，也可以不是。

在这里，`PipelineDraweeControllerBuilderSupplier`的用法更像是一个`Factory`，它实现了`Supplier`接口。

```
public class PipelineDraweeControllerBuilderSupplier implements
    Supplier<PipelineDraweeControllerBuilder> {

  private final Context mContext;
  private final ImagePipeline mImagePipeline;
  private final PipelineDraweeControllerFactory mPipelineDraweeControllerFactory;
  private final Set<ControllerListener> mBoundControllerListeners;

  public PipelineDraweeControllerBuilderSupplier(Context context) {
    this(context, ImagePipelineFactory.getInstance());
  }

  public PipelineDraweeControllerBuilderSupplier(
      Context context,
      ImagePipelineFactory imagePipelineFactory) {
    this(context, imagePipelineFactory, null);
  }

  public PipelineDraweeControllerBuilderSupplier(
      Context context,
      ImagePipelineFactory imagePipelineFactory,
      Set<ControllerListener> boundControllerListeners) {
    mContext = context;
    mImagePipeline = imagePipelineFactory.getImagePipeline();
    mPipelineDraweeControllerFactory = new PipelineDraweeControllerFactory(
        context.getResources(),
        DeferredReleaser.getInstance(),
        imagePipelineFactory.getAnimatedDrawableFactory(),
        UiThreadImmediateExecutorService.getInstance());
    mBoundControllerListeners = boundControllerListeners;
  }

  @Override
  public PipelineDraweeControllerBuilder get() {
    return new PipelineDraweeControllerBuilder(
        mContext,
        mPipelineDraweeControllerFactory,
        mImagePipeline,
        mBoundControllerListeners);
  }
}
```

## 构造函数
`PipelineDraweeControllerBuilderSupplier(Context context)`，使用了在 Fresco 的`initalize`方法中通过`ImagePipelineFactoryBuilder`创建的`ImagePipelineFactory`的实例。

```
this(context, ImagePipelineFactory.getInstance());
```

`get`方法告诉我们，`ImagePipeline`会存储在`PipelineDraweeController`中，关于`Controller`可以参考 [Fresco源码解析-Hierarachy-View-Controller](Fresco源码解析-Hierarachy-View-Controller.md)。

同时`PipelineDraweeController`也会存储一个`mPipelineDraweeControllerFactory`。

```
public class PipelineDraweeControllerFactory {

  private Resources mResources;
  private DeferredReleaser mDeferredReleaser;
  private AnimatedDrawableFactory mAnimatedDrawableFactory;
  private Executor mUiThreadExecutor;

  public PipelineDraweeControllerFactory(
      Resources resources,
      DeferredReleaser deferredReleaser,
      AnimatedDrawableFactory animatedDrawableFactory,
      Executor uiThreadExecutor) {
    mResources = resources;
    mDeferredReleaser = deferredReleaser;
    mAnimatedDrawableFactory = animatedDrawableFactory;
    mUiThreadExecutor = uiThreadExecutor;
  }

  public PipelineDraweeController newController(
      Supplier<DataSource<CloseableReference<CloseableImage>>> dataSourceSupplier,
      String id,
      Object callerContext) {
    return new PipelineDraweeController(
        mResources,
        mDeferredReleaser,
        mAnimatedDrawableFactory,
        mUiThreadExecutor,
        dataSourceSupplier,
        id,
        callerContext);
  }
}
```

这个`mPipelineDraweeControllerFactory`会通过`newController`来创建一个`PipelineDraweeController`的实例。

到底，初始化的工作就完成了。

以上分析虽然简单，但是清楚地梳理了 Fresco 的初始化过程，不过任然是远远不够的，由以上代码可以看出，初始化对应组件（Drawee、ImagePipeline）时用到了很多的设计模式，如果不太熟悉这些设计模式，可能理解起来会比较吃力。更加关键的是，初始化对应的组件用到了大量的参数，每个参数背后又会牵扯到很多知识点，后续博文中，我们再来一一分析。


