#  Fresco源码解析 - 创建一个ImagePipeline（一）

来源:[CSDN-一介码农](http://blog.csdn.net/feelang/article/details/45430497)

在[Fresco源码解析 - 初始化过程分析](Fresco源码解析-初始化过程分析.md)章节中，我们分析了Fresco的初始化过程，两个`initialize`方法中都用到了`ImagePipelineFactory`类。

`ImagePipelineFactory.initialize(context);`会创建一个所有参数都使用默认值的`ImagePipelineConfig`来初始化`ImagePipeline`。

`ImagePipelineFactory.initialize(imagePipelineConfig)`会首先用`imagePipelineConfig`创建一个`ImagePipelineFactory`的实例 - `sInstance`。

```
sInstance = new ImagePipelineFactory(imagePipelineConfig);
```

然后，初始化`Drawee`时，在`PipelineDraweeControllerBuilderSupplier`的构造方法中通过 `ImagePipelineFactory.getInstance()`获取这个实例。

*Fresco.java*

```
private static void initializeDrawee(Context context) {
  sDraweeControllerBuilderSupplier = new PipelineDraweeControllerBuilderSupplier(context);
  SimpleDraweeView.initialize(sDraweeControllerBuilderSupplier);
}
```

*PipelineDraweeControllerBuilderSupplier.java*

```
public PipelineDraweeControllerBuilderSupplier(Context context) {
  this(context, ImagePipelineFactory.getInstance());
}

public PipelineDraweeControllerBuilderSupplier(
    Context context,
    ImagePipelineFactory imagePipelineFactory) {
  this(context, imagePipelineFactory, null);
}
```

`PipelineDraweeControllerBuilderSupplier`还有一个构造方法，就是`this(context, imagePipelineFactory, null)`调用的构造方法。

```
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
```

其中，`mImagePipeline = imagePipelineFactory.getImagePipeline()`用于获取`ImagePipeline`的实例。

*ImagePipelineFactory.java*

```
public ImagePipeline getImagePipeline() {
  if (mImagePipeline == null) {
    mImagePipeline =
        new ImagePipeline(
            getProducerSequenceFactory(),
            mConfig.getRequestListeners(),
            mConfig.getIsPrefetchEnabledSupplier(),
            getBitmapMemoryCache(),
            getEncodedMemoryCache(),
            mConfig.getCacheKeyFactory());
  }
  return mImagePipeline;
}
```

可以看出`mImagePipeline`是一个单例，构造`ImagePipeline`时用到的`mConfig`就是本片最开始讲到的 `ImagePipelineConfig imagePipelineConfig`。

经过这个过程，一个`ImagePipeline`就被创建好了，下面我们具体解析一下`ImagePipeline`的每个参数。

因为`ImagePipelineFactory`用`ImagePipelineConfig`来创建一个`ImagePipeline`，我们首先分析一下`ImagePipelineConfig`的源码。

```
public class ImagePipelineConfig {
  private final Supplier<MemoryCacheParams> mBitmapMemoryCacheParamsSupplier;
  private final CacheKeyFactory mCacheKeyFactory;
  private final Context mContext;
  private final Supplier<MemoryCacheParams> mEncodedMemoryCacheParamsSupplier;
  private final ExecutorSupplier mExecutorSupplier;
  private final ImageCacheStatsTracker mImageCacheStatsTracker;
  private final AnimatedDrawableUtil mAnimatedDrawableUtil;
  private final AnimatedImageFactory mAnimatedImageFactory;
  private final ImageDecoder mImageDecoder;
  private final Supplier<Boolean> mIsPrefetchEnabledSupplier;
  private final DiskCacheConfig mMainDiskCacheConfig;
  private final MemoryTrimmableRegistry mMemoryTrimmableRegistry;
  private final NetworkFetcher mNetworkFetcher;
  private final PoolFactory mPoolFactory;
  private final ProgressiveJpegConfig mProgressiveJpegConfig;
  private final Set<RequestListener> mRequestListeners;
  private final boolean mResizeAndRotateEnabledForNetwork;
  private final DiskCacheConfig mSmallImageDiskCacheConfig;
  private final PlatformBitmapFactory mPlatformBitmapFactory;

  // other methods
}
```

![](fresco-imagepipeline-1.png)

上图可以看出，获取图像的第一站是`Memeory Cache`，然后是`Disk Cache`，最后是`Network`，而`Memory`和`Disk`都是缓存在本地的数据，`MemoryCacheParams`就用于表示它们的缓存策略。

*MemoryCacheParams.java*

```
/**
   * Pass arguments to control the cache's behavior in the constructor.
   *
   * @param maxCacheSize The maximum size of the cache, in bytes.
   * @param maxCacheEntries The maximum number of items that can live in the cache.
   * @param maxEvictionQueueSize The eviction queue is an area of memory that stores items ready
   *                             for eviction but have not yet been deleted. This is the maximum
   *                             size of that queue in bytes.
   * @param maxEvictionQueueEntries The maximum number of entries in the eviction queue.
   * @param maxCacheEntrySize The maximum size of a single cache entry.
   */
  public MemoryCacheParams(
      int maxCacheSize,
      int maxCacheEntries,
      int maxEvictionQueueSize,
      int maxEvictionQueueEntries,
      int maxCacheEntrySize) {
    this.maxCacheSize = maxCacheSize;
    this.maxCacheEntries = maxCacheEntries;
    this.maxEvictionQueueSize = maxEvictionQueueSize;
    this.maxEvictionQueueEntries = maxEvictionQueueEntries;
    this.maxCacheEntrySize = maxCacheEntrySize;
  }
```

关于每个参数的作用，注释已经写得很清楚，不再赘述。

`CacheKeyFactory`会为`ImageRequest`创建一个索引 - `CacheKey`。

```
/**
 * Factory methods for creating cache keys for the pipeline.
 */
public interface CacheKeyFactory {

  /**
   * @return {@link CacheKey} for doing bitmap cache lookups in the pipeline.
   */
  public CacheKey getBitmapCacheKey(ImageRequest request);

  /**
   * @return {@link CacheKey} for doing encoded image lookups in the pipeline.
   */
  public CacheKey getEncodedCacheKey(ImageRequest request);

  /**
   * @return a {@link String} that unambiguously indicates the source of the image.
   */
  public Uri getCacheKeySourceUri(Uri sourceUri);
}
```

`ExecutorSupplier`会根据`ImagePipeline`的使用场景获取不同的`Executor`。

```
public interface ExecutorSupplier {

  /** Executor used to do all disk reads, whether for disk cache or local files. */
  Executor forLocalStorageRead();

  /** Executor used to do all disk writes, whether for disk cache or local files. */
  Executor forLocalStorageWrite();

  /** Executor used for all decodes. */
  Executor forDecode();

  /** Executor used for all image transformations, such as transcoding, resizing, and rotating. */
  Executor forTransform();

  /** Executor used for background operations, such as postprocessing. */
  Executor forBackground();
}
```

`ImageCacheStatsTracker`作为`Cache`埋点工具，可以统计`Cache`的各种操作数据。

```
public interface ImageCacheStatsTracker {

  /** Called whenever decoded images are put into the bitmap cache. */
  public void onBitmapCachePut();

  /** Called on a bitmap cache hit. */
  public void onBitmapCacheHit();

  /** Called on a bitmap cache miss. */
  public void onBitmapCacheMiss();

  /** Called whenever encoded images are put into the encoded memory cache. */
  public void onMemoryCachePut();

  /** Called on an encoded memory cache hit. */
  public void onMemoryCacheHit();

  /** Called on an encoded memory cache hit. */
  public void onMemoryCacheMiss();

  /**
   * Called on an staging area hit.
   *
   * <p>The staging area stores encoded images. It gets the images before they are written
   * to disk cache.
   */
  public void onStagingAreaHit();

  /** Called on a staging area miss hit. */
  public void onStagingAreaMiss();

  /** Called on a disk cache hit. */
  public void onDiskCacheHit();

  /** Called on a disk cache miss. */
  public void onDiskCacheMiss();

  /** Called if an exception is thrown on a disk cache read. */
  public void onDiskCacheGetFail();

  /**
   * Registers a bitmap cache with this tracker.
   *
   * <p>Use this method if you need access to the cache itself to compile your stats.
   */
  public void registerBitmapMemoryCache(CountingMemoryCache<?, ?> bitmapMemoryCache);

  /**
   * Registers an encoded memory cache with this tracker.
   *
   * <p>Use this method if you need access to the cache itself to compile your stats.
   */
  public void registerEncodedMemoryCache(CountingMemoryCache<?, ?> encodedMemoryCache);
}
```

剩下的几个参数与Drawable关联比较大，我们下一篇再分析。

めっちゃ眠くて、寝て行っちゃうから、じゃねー。
