# RecyclerView源码分析

来源:[学姐的技术成长之路](http://wangxinghe.me/blog/2016-03-06/recyclerview-analysis/#rd?sukey=a76cdd086edb4fced8c1e738b2e6ae82236dcce311b6dec28fad714a475979245045d52003182924b5466bf8941d72a6)

## 概述

之前面试的时候经常有人问是否用过RecyclerView，最近项目中也大量用到RecyclerView。对于有点追求的码工来时，当然不会满足于仅仅会使用这一层次，学姐就是一个有追求的妹纸。我先从普通的AdapterView和RecyclerView的比较说起，后面再详细介绍几个关键类。

### AdapterView vs. RecyclerView

* Item复用方面：RecyclerView内置了RecyclerViewPool、多级缓存、ViewHolder，而AdapterView需要 手动添加ViewHolder且复用功能也没RecyclerView更加完善

* 样式丰富方面：RecyclerView通过支持水平、垂直和表格列表及其他更复杂形式，而AdapterView只支持具体某一种

* 效果增强方面：RecyclerView内置了ItemDecoration和ItemAnimator，可以自定义绘制itemView之间的一些特殊UI或item项数据变化时的动画效果，而用AdapterView实现时采取的做法是将这些特殊UI作为itemView的一部分，设置可见不可见决定是否展现，且数据变化时的动画效果没有提供，实现起来比较麻烦

* 代码内聚方面：RecyclerView将功能密切相关的类写成内部类，如ViewHolder，Adapter，而AdapterView没有

## 1. Recycler

### （1）Recycler简介

Recycler用于管理已经废弃或与RecyclerView分离的（scrapped or detached）item view，便于重用。

Scrapped view指依附于RecyclerView，但被标记为可移除或可复用的view。

LayoutManager获取Adapter某一项的View时会使用Recycler。当复用的View有效（clean）时，View能直接被复用，反之若View失效（dirty）时，需要重新绑定View。对于有效的View，如果不主动调用request layout，则不需要重新测量大小就能复用

### （2）原理解析

在分析Recycler的复用原理之前，我们先了解下如下两个类：

* RecycledViewPool

RecyclerViewPool用于多个RecyclerView之间共享View。只需要创建一个RecyclerViewPool实例，然后调用RecyclerView的`setRecycledViewPool(RecycledViewPool)`方法即可。RecyclerView默认会创建一个RecyclerViewPool实例。

```
    public static class RecycledViewPool {
    private SparseArray<ArrayList<ViewHolder>> mScrap =
            new SparseArray<ArrayList<ViewHolder>>();
    private SparseIntArray mMaxScrap = new SparseIntArray();
    private int mAttachCount = 0;

    private static final int DEFAULT_MAX_SCRAP = 5;

    public void clear() {
        mScrap.clear();
    }

    public void setMaxRecycledViews(int viewType, int max) {
        mMaxScrap.put(viewType, max);
        final ArrayList<ViewHolder> scrapHeap = mScrap.get(viewType);
        if (scrapHeap != null) {
            while (scrapHeap.size() > max) {
                scrapHeap.remove(scrapHeap.size() - 1);
            }
        }
    }

    public ViewHolder getRecycledView(int viewType) {
        final ArrayList<ViewHolder> scrapHeap = mScrap.get(viewType);
        if (scrapHeap != null && !scrapHeap.isEmpty()) {
            final int index = scrapHeap.size() - 1;
            final ViewHolder scrap = scrapHeap.get(index);
            scrapHeap.remove(index);
            return scrap;
        }
        return null;
    }

    int size() {
        int count = 0;
        for (int i = 0; i < mScrap.size(); i ++) {
            ArrayList<ViewHolder> viewHolders = mScrap.valueAt(i);
            if (viewHolders != null) {
                count += viewHolders.size();
            }
        }
        return count;
    }

    public void putRecycledView(ViewHolder scrap) {
        final int viewType = scrap.getItemViewType();
        final ArrayList scrapHeap = getScrapHeapForType(viewType);
        if (mMaxScrap.get(viewType) <= scrapHeap.size()) {
            return;
        }
        if (DEBUG && scrapHeap.contains(scrap)) {
            throw new IllegalArgumentException("this scrap item already exists");
        }
        scrap.resetInternal();
        scrapHeap.add(scrap);
    }

    void attach(Adapter adapter) {
        mAttachCount++;
    }

    void detach() {
        mAttachCount--;
    }


    /**
     * Detaches the old adapter and attaches the new one.
     * <p>
     * RecycledViewPool will clear its cache if it has only one adapter attached and the new
     * adapter uses a different ViewHolder than the oldAdapter.
     *
     * @param oldAdapter The previous adapter instance. Will be detached.
     * @param newAdapter The new adapter instance. Will be attached.
     * @param compatibleWithPrevious True if both oldAdapter and newAdapter are using the same
     *                               ViewHolder and view types.
     */
    void onAdapterChanged(Adapter oldAdapter, Adapter newAdapter,
            boolean compatibleWithPrevious) {
        if (oldAdapter != null) {
            detach();
        }
        if (!compatibleWithPrevious && mAttachCount == 0) {
            clear();
        }
        if (newAdapter != null) {
            attach(newAdapter);
        }
    }

    private ArrayList<ViewHolder> getScrapHeapForType(int viewType) {
        ArrayList<ViewHolder> scrap = mScrap.get(viewType);
        if (scrap == null) {
            scrap = new ArrayList<ViewHolder>();
            mScrap.put(viewType, scrap);
            if (mMaxScrap.indexOfKey(viewType) < 0) {
                mMaxScrap.put(viewType, DEFAULT_MAX_SCRAP);
            }
        }
        return scrap;
    }
}
```

通过源码我们可以看出mScrap是一个<viewType, List>的映射，`mMaxScrap`是一个<viewType, maxNum>的映射，这两个成员变量代表可复用View池的基本信息。调用`setMaxRecycledViews(int viewType, int max)`时，当用于复用的`mScrap`中viewType对应的ViewHolder个数超过maxNum时，会从列表末尾开始丢弃超过的部分。调用`getRecycledView(int viewType)`方法时从`mScrap`中移除并返回viewType对应的List的末尾项。

* ViewCacheExtension

ViewCacheExtension是一个由开发者控制的可以作为View缓存的帮助类。调用`Recycler.getViewForPosition(int)`方法获取View时，Recycler先检查attached scrap和一级缓存，如果没有则检查`ViewCacheExtension.getViewForPositionAndType(Recycler, int, int)`，如果没有则检查RecyclerViewPool。注意：Recycler不会在这个类中做缓存View的操作，是否缓存View完全由开发者控制。

```
public abstract static class ViewCacheExtension {
    abstract public View getViewForPositionAndType(Recycler recycler, int position, int type);
}
```

现在大家熟悉了RecyclerViewPool和ViewCacheExtension的作用后，下面开始介绍Recycler。 如下是Recycler的几个关键成员变量和方法：

* `private ArrayList<ViewHolder> mAttachedScrap` `private ArrayList<ViewHolder> mChangedScrap`与RecyclerView分离的ViewHolder列表。

* `private ArrayList<ViewHolder> mCachedViews ViewHolder`缓存列表。

* `private ViewCacheExtension mViewCacheExtension`开发者控制的ViewHolder缓存

* `private RecycledViewPool mRecyclerPool`提供复用ViewHolder池。

* `public void bindViewToPosition(View view, int position)`将某个View绑定到Adapter的某个位置。

* `public View getViewForPosition(int position)` 获取某个位置需要展示的View，先检查是否有可复用的View，没有则创建新View并返回。具体过程为：

   * （1）检查mChangedScrap，若匹配到则返回相应holder 
   * （2）检查mAttachedScrap，若匹配到且holder有效则返回相应holder 
   * （3）检查mViewCacheExtension，若匹配到则返回相应holder 
   * （4）检查mRecyclerPool，若匹配到则返回相应holder 
   * （5）否则执行Adapter.createViewHolder()，新建holder实例 
   * （6）返回holder.itemView 
   * （7）注：以上每步匹配过程都可以匹配position或itemId（如果有stableId）
   
## 2. LayoutManager

LayoutManager主要作用是，测量和摆放RecyclerView中itemView，以及当itemView对用户不可见时循环复用处理。 通过设置Layout Manager的属性，可以实现水平滚动、垂直滚动、方块表格等列表形式。其内部类Properties包含了所需要的大部分属性

## 3. ViewHolder

对于传统的AdapterView，需要在实现的Adapter类中手动加ViewHolder，RecyclerView直接将ViewHolder内置，并在原来基础上功能上更强大。 ViewHolder描述RecylerView中某个位置的itemView和元数据信息，属于Adapter的一部分。其实现类通常用于保存findViewById的结果。 主要元素组成有：

```
public static abstract class ViewHolder {
    View itemView;//itemView
    int mPosition;//位置
    int mOldPosition;//上一次的位置
    long mItemId;//itemId
    int mItemViewType;//itemViewType
    int mPreLayoutPosition;
    int mFlags;//ViewHolder的状态标志
    int mIsRecyclableCount;
    Recycler mScrapContainer;//若非空，表明当前ViewHolder对应的itemView可以复用
｝
```

关于ViewHolder，我最想介绍的是mFlags。 

* **FLAG_BOUND**——ViewHolder已经绑定到某个位置，mPosition、mItemId、mItemViewType都有效 
* **FLAG_UPDATE**——ViewHolder绑定的View对应的数据过时需要重新绑定，mPosition、mItemId还是一致的 
* **FLAG_INVALID**ViewHolder绑定的View对应的数据无效，需要完全重新绑定不同的数据 
* **FLAG_REMOVED**——ViewHolder对应的数据已经从数据集移除 
* **FLAG_NOT_RECYCLABLE**——ViewHolder不能复用 
* **FLAG_RETURNED_FROM_SCRAP**——这个状态的ViewHolder会加到scrap list被复用。 
* **FLAG_CHANGED**——ViewHolder内容发生变化，通常用于表明有ItemAnimator动画 
* **FLAG_IGNORE**——ViewHolder完全由LayoutManager管理，不能复用 
* **FLAG_TMP_DETACHED**——ViewHolder从父RecyclerView临时分离的标志，便于后续移除或添加回来 
* **FLAG_ADAPTER_POSITION_UNKNOWN**——ViewHolder不知道对应的Adapter的位置，直到绑定到一个新位置 
* **FLAG_ADAPTER_FULLUPDATE**——方法addChangePayload(null)调用时设置

## 4. Adapter

和AdapterView中用到的BaseAdapter、ListAdapter等作用类似，都是作为itemView和data之间的适配器，将data绑定到某一个itemView上。差别在于，RecyclerView将Adapter内置作为其内部类，我认为将功能密切相关的类以内部类的形式定义使得代码内聚更好，更便于理解与阅读。

## 5. ItemDecoration

当我们想在某些item上加一些特殊的UI时，往往都是在itemView中先布局好，然后通过设置可见性来决定哪些位置显示不显示。RecyclerView将itemView和装饰UI分隔开来，装饰UI即ItemDecoration，主要用于绘制item间的分割线、高亮或者margin等。其源码如下：

```
public static abstract class ItemDecoration {
    //itemView绘制之前绘制，通常这部分UI会被itemView盖住
    public void onDraw(Canvas c, RecyclerView parent, State state) {
        onDraw(c, parent);
    }

    //itemView绘制之后绘制，这部分UI盖在itemView上面
    public void onDrawOver(Canvas c, RecyclerView parent, State state) {
        onDrawOver(c, parent);
    }

    //设置itemView上下左右的间距
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, State state) {
        getItemOffsets(outRect, ((LayoutParams) view.getLayoutParams()).getViewLayoutPosition(),
                parent);
    }
}
```

## 6. ItemAnimator

过去AdapterView的item项操作往往是没有动画的。现在RecyclerView的ItemAnimator使得item的动画实现变得简单而样式丰富，我们可以自定义item项不同操作（如添加，删除）的动画效果。

## 7. 触摸事件监听

关于Item项的手势监听事件，如单击和双击没有像其他AdapterView一样分别提供具体接口，但是RecyclerView提供OnItemTouchListener接口和SimpleOnItemTouchListener实现类，大家可以通过继承去实现自己想要的单击双击或其他事件监听。

ps：关于RecyclerView的具体使用，我提供一个链接供大家参考[RecyclerView技术栈](http://www.jianshu.com/p/16712681731e)。今天就先讲这么多，以后有新的体会会继续补充的，各位期待吧～

## 附录：

[RecyclerView技术栈](http://www.jianshu.com/p/16712681731e)