# 使用Dagger 2进行依赖注入

来源:[codethink.me](http://codethink.me/2015/08/06/dependency-injection-with-dagger-2/)

## 0. 前言
Dagger2是首个使用生成代码实现完整依赖注入的框架，极大减少了使用者的编码负担，本文主要介绍如何使用Dagger2进行依赖注入。如果你不还不了解依赖注入，请看[这一篇](http://codethink.me/2015/08/01/dependency-injection-theory/)。

## 1. 简单的依赖注入

首先我们构建一个简单Android应用。我们创建一个UserModel，然后将它显示到TextView中。这里的问题是，在创建UserModel的时候，我们使用了[前文](http://codethink.me/2015/08/01/dependency-injection-theory/)所说的hard init。一旦我们的UserModel的创建方式发生了改变（比如需要传入Context对象到构造函数），我们就需要修改所有创建UserModel的代码。而我们希望的是，对于UserModel的修改不影响其他模块的代码（比如这里的MainActivity）。

```
public class MainActivity extends ActionBarActivity {

	@Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        UserModel user = new UserModel();

        ((TextView) findViewById(R.id.user_desc_line)).setText(user.id + "\n" + user.name + "\n" + user.gender);

    }
    ...
}
```

### 1.1 构建依赖

我们首先想到的是，将创建UserModel的代码独立出来，这样可以保证MainActivity的代码不被修改。Dagger2中，这个负责提供依赖的组件被称为Module。我们构建的ActivityModule代码如下所示。

```
@Module
public class ActivityModule {

    @Provides UserModel provideUserModel() {
        return new UserModel();
    }
}
```

可以看到，我们使用@Module标识类型为module，并用@Provides标识提供依赖的方法。

### 1.2 构建Injector

有了提供依赖的组件，我们还需要将依赖注入到需要的对象中。连接提供依赖和消费依赖对象的组件被称为Injector。Dagger2中，我们将其称为component。ActivityComponent代码如下：

```
@Component(modules = ActivityModule.class)
public interface ActivityComponent {
    void inject(MainActivity activity);
}
```

可以看到，Component是一个使用@Component标识的Java interface。interface的inject方法需要一个消耗依赖的类型对象作为参数。

**注意：这里必须是真正消耗依赖的类型MainActivity，而不可以写成其父类，比如Activity。因为Dagger2在编译时生成依赖注入的代码，会到inject方法的参数类型中寻找可以注入的对象，但是实际上这些对象存在于MainActivity，而不是Activity中。如果函数声明参数为Activity，Dagger2会认为没有需要注入的对象。当真正在MainActivity中创建Component实例进行注入时，会直接执行按照Activity作为参数生成的inject方法，导致所有注入都失败。(是的，我是掉进这个坑了。)**

### 1.3 完成依赖注入

最后，我们需要在MainActivity中构建Injector对象，完成注入。这部分代码如下所示。

```
public class MainActivity extends ActionBarActivity {
    private ActivityComponent mActivityComponent;

    @Inject UserModel userModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mActivityComponent = DaggerActivityComponent.builder().activityModule(new ActivityModule()).build();
        mActivityComponent.inject(this);
        ((TextView) findViewById(R.id.user_desc_line)).setText(userModel.id + "\n" + userModel.name + "\n" + userModel.gender);
    }
    ...
}
```

首先，我们使用@Inject标志被注入的对象userModel（注意userModel不能为private），之后通过Dagger2生成的实现了我们提供的ActivityComponent接口类DaggerActivityComponent创建component，调用其inject方法完成注入。
至此，我们使用Dagger实现了最简单的依赖注入。

## 2. 多层依赖
除了上面这种最简单的形式，Dagger2还可以使用component作为component的依赖，实现多层级的依赖注入。

### 2.1 构建依赖

我们新创建一个名为ShoppingCartModel的Domain Model。并按照1.1的方法构建其Module如下。

```
@Module
public class ContainerModule {
    @Provides ShoppingCartModel provideCartModel() {
        return new ShoppingCartModel();
    }
}
```

### 2.2 构建Injector

与1.2不同的是，我们的Injector提供的依赖不仅来自ContainerModule，我们还需要使用之前的ActivityComponent提供的UserModel依赖。

```
@Component(dependencies = ActivityComponent.class, modules = ContainerModule.class)
public interface ContainerComponent {
    void inject(MainActivity mainActivity);
}
```

所以如代码所示，我们在component后增加ActivityComponent了dependencies参数，使得一个Component成为了另一个Component的依赖。

### 2.3 低级Component提供依赖

目前的ActivityComponent代码如下所示。可以看到其只提供了inject方法，而没有提供需要的UserModel依赖。我们需要的是将ActivityModule提供的UserModel传递给依赖ActivityComponent的ContainerComponent。

```
@Component(modules = ActivityModule.class)
public interface ActivityComponent {
    void inject(MainActivity activity);
}
```

修改后代码如下：

```
@Component(modules = ActivityModule.class)
public interface ActivityComponent {
//    void inject(MainActivity activity);
    UserModel userModel();
}
```

可以看到，我们为接口增加了提供UserModel依赖的方法，同时，如果不需要使它直接进行注入，可以去掉其inject方法，此时该Component只作为一种依赖的组织模块。

最后，MainActivity中进行依赖注入的代码如下。

```
public class MainActivity extends ActionBarActivity {
    private ActivityComponent mActivityComponent;

    @Inject
    UserModel userModel;

    @Inject
    ShoppingCartModel cartModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mActivityComponent = DaggerActivityComponent.builder().activityModule(new ActivityModule()).build();
        ContainerComponent containerComponent = DaggerContainerComponent.builder().activityComponent(mActivityComponent).containerModule(new ContainerModule()).build();

        containerComponent.inject(this);

        ((TextView) findViewById(R.id.user_desc_line)).setText(userModel.id + "\n" + userModel.name + "\n" + userModel.gender + "\n" + cartModel.total);
    }
    ...
}
```

## 3. 最后
本文试图用最简单的例子介绍Android中如何使用Dagger2进行依赖注入，因此有很多Dagger2的特性并未涉及，比如@Scope注释，以及Dagger2自动生成代码的分析调试。关于Dagger2更深入的特性的分析，还需要在大量使用后再做出总结。

## 参考
[Dagger 2](http://google.github.io/dagger/)<br/>
[Tasting Dagger 2 on Android](http://fernandocejas.com/2015/04/11/tasting-dagger-2-on-android/)<br/>
[Dependency injection with Dagger 2 - the API](http://frogermcs.github.io/dependency-injection-with-dagger-2-the-api/)<br/>