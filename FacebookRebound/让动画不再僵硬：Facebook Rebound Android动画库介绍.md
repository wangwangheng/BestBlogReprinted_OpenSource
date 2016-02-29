# 让动画不再僵硬：Facebook Rebound Android动画库介绍

来源:[markzhai's home](http://blog.zhaiyifan.cn/2015/03/13/%E8%AE%A9%E5%8A%A8%E7%94%BB%E4%B8%8D%E5%86%8D%E5%83%B5%E7%A1%AC%EF%BC%9AFacebook-Rebound-Android%E5%8A%A8%E7%94%BB%E5%BA%93%E4%BB%8B%E7%BB%8D/)

## introduction
official site：[http://facebook.github.io/rebound](http://facebook.github.io/rebound)

github : [https://github.com/facebook/rebound](https://github.com/facebook/rebound)

Rebound是facebook推出的一个弹性动画库，可以让动画看起来真实自然，像真实世界的物理运动，带有力的效果，使用的参数则是facebook的origami中使用的。

官网上有一个简单的JS版本来做demo，如果说到evernote、LinkedIn、flow等应用也在使用这个动画库，是不是会显得更厉害些呢。

具体效果，可以看看QQ空间 Android独立版客户端中，抽屉打开的icon效果，以及底部加号点开后的icon效果，是我当年在的时候做的。

## usage

```
Spring spring = mSpringSystem
        .createSpring()
        .setSpringConfig(SpringConfig.fromOrigamiTensionAndFriction(86, 7))
        .addListener(new SimpleSpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                float value = (float) spring.getCurrentValue();
                ViewHelper.setTranslationX(view, value);
            }
        });
```

上面的短短代码就可以给一个view加上自然的从左向右进入回弹效果。

类似地

```
Spring spring = mSpringSystem
        .createSpring()
        .setSpringConfig(SpringConfig.fromOrigamiTensionAndFriction(86, 7))
        .addListener(new SimpleSpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                float value = (float) spring.getCurrentValue();
                float scale = 1f - value;
                ViewHelper.setScaleX(mItemIconViewList.get(index), scale);
                ViewHelper.setScaleY(mItemIconViewList.get(index), scale);
            }
        });
```

就可以给view加上一个从小变大然后略有回弹的效果。

如果想要做很多view的连锁动画怎么办？Rebound也提供了SpringChain这个接口。

```
for (int i = 0; i < viewCount; i++) {
    final View view = new View(context);
    view.setLayoutParams(
            new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f));
    mSpringChain.addSpring(new SimpleSpringListener() {
        @Override
        public void onSpringUpdate(Spring spring) {
            float value = (float) spring.getCurrentValue();
            view.setTranslationX(value);
        }
    });
    int color = (Integer) evaluator.evaluate((float) i / (float) viewCount, startColor, endColor);
    view.setBackgroundColor(color);
    view.setOnTouchListener(new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return handleRowTouch(v, event);
        }
    });
    mViews.add(view);
    rootView.addView(view);
}

getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
    @Override
    public void onGlobalLayout() {
        getViewTreeObserver().removeOnGlobalLayoutListener(this);
        List<Spring> springs = mSpringChain.getAllSprings();
        for (int i = 0; i < springs.size(); i++) {
            springs.get(i).setCurrentValue(-mViews.get(i).getWidth());
        }
        postDelayed(new Runnable() {
            @Override
            public void run() {
                mSpringChain
                        .setControlSpringIndex(0)
                        .getControlSpring()
                        .setEndValue(0);
            }
        }, 500);
    }
});
```

就做出了一个view和view的牵引位移动画效果。

