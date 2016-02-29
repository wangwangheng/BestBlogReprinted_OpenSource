# 用 Retrofit 2 简化 HTTP 请求

来源:[Realm](https://realm.io/cn/news/droidcon-jake-wharton-simple-http-retrofit-2/)

[Retrofit]:http://square.github.io/retrofit/
[JakeWharton]:https://twitter.com/jakewharton
[JakeWharton-Twitter]:https://twitter.com/jakewharton
[DroidconSF]:http://sf.droidcon.com/
[Square]:https://squareup.com/
[Protobuf]:https://github.com/google/protobuf
[RxJava]:https://github.com/ReactiveX/RxJava
[Video-Section-Of-Performance]:javascript:presentz.changeChapter(0,142,true);javascript:presentz.changeChapter(0,142,true);
[Droidcon-Montreal]:https://youtu.be/WvyScM_S88c

[Retrofit][Retrofit] 作为简化 HTTP 请求的库，已经运行多年，2.0版本依然不辱使命的在做这些事情。不过 2.0 版本修复了一些长期影响开发者的设计，还加入了前所未有的强大特性。在 NYC 2015 的这一个分享中，[Jake Wharton][JakeWharton] 的演讲涵盖了所有 Retrofit 2.0 的新特性，全面介绍了 Retrofit 2.0 工作原理。

## About the Author: Jake Wharton

*Jake Wharton* 是工作在 Square 的工程师。过去五年一直在跟糟糕的代码和 API 作斗争。经常参加各类会议，讨论这种影响千万开发者的如同瘟疫般的设计。

[@jakewharton][JakeWharton-Twitter]

Save the date for [Droidcon SF][DroidconSF] in March — a conference with best-in-class presentations from leaders in all parts of the Android ecosystem.

## 简介 (0:00)

我叫 [Jake Wharton][JakeWharton]，现在在 [Square][Square] 工作。一个天真的人曾经说过：”Retrofit 2 将会在今年年底前放出。”，那个人，就是去年在纽约 DroidCon 上表态的我。然而，事实是 Retrofit 2 将会在今年年底放出，这次我保证！

[Retrofit][Retrofit] 5年前就开源了，是 Square 最早的开源项目之一。一开始的时候，Retrofit 只是我们用在各个开源项目里的福袋：比如说最早里面有晃动检测功能，HTTP Client，还有现在的 tap 库。多数功能都是 Bob Lee 完成的，我大概 3 年前开始接管这些工作。最终历经 3 年，完成了 1.0 版本，然后彻底开源。从那会儿到现在，已经 release 了 18 个版本了。

### Retrofit 1 不错的地方 (2:23)

Retrofit 里已经有很多不错的特性了。Retrofit 可以利用接口，方法和注解参数（parameter annotations）来声明式定义一个请求应该如何被创建。比如说，下面是一个如何请求 GitHub API 的例子：

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  List<Contributor> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}
```

Retrofit 背后的 HTTP client，以及序列化机制（JSON/XML 协议）都是可替换（pluggable）的，因此你可以选择合适自己的方案。Retrofit 最早出来的时候，只支持 Apache 的 HTTP client。在 1.0 放出前，我们增加了 URL connection，以及 OkHttp 的支持。如果你想要加入的其他的 HTTP client，都可以简单的加入。这个特性非常赞，让我们有能力去支持不同的自定义 client。

```
builder.setClient(new UrlConnectionClient());
builder.setClient(new ApacheClient());
builder.setClient(new OkClient());

builder.setClient(new CustomClient());
```

序列化功能也是可替换的。默认是用的 GSON，你当然也可以用 Jackson 来替换掉。

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  List<Contributor> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}

builder.setConverter(new GsonConverter());
builder.setConverter(new JacksonConverter());
```

如果你在用某些数据交换协议，比如 protocol buffer，Retrofit 也支持 Google 的 [protobuf][Protobuf]，也包括 XML 协议的转换（如果你自己不怕折腾）。

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  ContributorResponse repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}

builder.setConverter(new ProtoConverter());
builder.setConverter(new WireConverter());

builder.setConverter(new SimpleXMLConverter());

builder.setConverter(new CustomConverter());
```

序列化部分跟 client 部分一样，都是可替换的。你如果想要引入或者实现自己的序列化组件，完全没有问题。

在发请求的实现上，你可以用的方法有很多，比如：同步发送请求——

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  List<Contributor> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}

List<Contributor> contributors =
    gitHubService.repoContributors("square", "retrofit");
```

——和异步发送，他们之间的区别就是异步发送要在最后一个参数上声明一个 callback 回调函数：

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  void repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo,
      Callback<List<Contributor>> cb);
}

service.repoContributors("square", "retrofit", new Callback<List<Contributor>>() {
  @Override void success(List<Contributor> contributors, Response response) {
    // ...
  }


  @Override void failure(RetrofitError error) {
    // ...
  }
});
```

——再到后来 1.0 后，我们还支持了 [RxJava][RxJava] ，被证明真的是个非常受欢迎的功能。

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Observable<List<Contributor>> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}

gitHubService.repoContributors("square", "retrofit")
    .subscribe(new Action1<List<Contributor>>() {
      @Override public void call(List<Contributor> contributors) {
        // ...
      }
    });
```

### Retrofit 1: 不够好的地方 (4:58)

不幸的是，没有一个库是完美的，Retrofit 也不例外。为了支持可替换的功能模块，我们必须嵌套大量的组件，类的数量极多以至于成为了一个痛处，一方面是因为整个库非常的脆弱，还有就是因为我们无法修改公开的 API 接口。

如果你想要操作某次请求返回的数据，比如说返回的 Header 部分或者 URL，你又同时想要操作序列化后的数据部分，这是 Retrofit 1.0 上是不可能实现的。

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  List<Contributor> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);

  @GET("/repos/{owner}/{repo}/contributors")
  Response repoContributors2(
      @Path("owner") String owner,
      @Path("repo") String repo);
}
```

在上面的这个 GitHub 的例子里，我们返回了一个 contributor 的列表，你可以用不同的 converter 去做反序列化。然而，如果说你要读取一个 reponse 的 header 部分。除非你设置一个 endpoint 来接管这个 reponse，不然你没有办法去读取这个 response。 由于 response header 数据里并没有反序列化后的对象，如果不做反序列化操作的话，那你也就无法拿到 contributor 对象了。

我刚才说过同步和异步，以及用起来非常棒的 RxJava，但是这些用起来却有些刻板。比如：我们在某些场景下既需要异步的调用，又需要同步的调用。在 Retrofit 1.0 里，你必须得声明两次这个方法，像下面这样：

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  List<Contributor> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);

  @GET("/repos/{owner}/{repo}/contributors")
  void repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo,
      Callback<List<Contributor>> cb);
}
```

RxJava 也有类似问题。但值得庆幸的是你在用 RxJava 的时候只用声明一次就行，为了实现这个，我们还在核心代码里增加了对 RxJava 的支持，以辅助返回 Observable 的对象。

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Observable<List<Contributor>> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}
```

我们可能已经熟悉如何在 Retrofit 里创建 Observable 对象。但是如果你需要一些其他对象呢？ 比如，我们没有计划支持用了 Guava 的 ListenableFuture，以及那些用了 Java 8 的 CompleteableFuture。毕竟，Retrofit 1 是基于还在用着 Java 6 的 Android 开发的。

Retrofit 1 里 Converter 工作的效率并不算是很高。下面是在 Retrofit 1 里创建自定义 Converter 的代码，非常简单：

```
interface Converter {
  Object fromBody(TypedInput body, Type type);
  TypedOutput toBody(Object object);
}
```

自定义 Converter 接收一个对象，然后返回一个格式化后的 HTTP 对象。问题是在我们传入了 Response 和一个我们想要转换的格式 Type 参数后，Converter 必须得搞清楚到底应该如何去反序列化，这部分的实现很复杂，而且耗时。尽管一些库做了对象的缓存，但依然效率很低。

```
interface GitHubService {
  @GET("/search/repositories")
  RepositoriesResponse searchRepos(
      @Query("q") String query,
      @Query("since") Date since);
}


/search/repositories?q=retrofit&since=2015-08-27
/search/repositories?q=retrofit&since=20150827
```

有时候，声明式 API 会遇到一些小问题。比如就像上面的例子一样，你有个接口需要传入一个 Date，但是一个 Date 会有多种不同的格式表示。有的接口可能需要一个字符串，有的可能需要一个分隔开的日期表示（尤其是那些比日期要复杂很多的对象，可能会有更多的表示方法）。

以上，基本上就是 Retrofit 1 无力解决的需求了，我们要如何修复呢？

## Retrofit 2 (10:18)

开发 Retrofit2 的时候，我们希望我们定位和解决所有大家多年以来在 Retrofit 1 里遇到的那些问题。

### Call (10:30)
首先得提到的是：Retrofit2 有了新的类型。如果你熟悉用 OkHttp 做 API 请求，你可能比较熟悉其中的一个类：Call。现在， Retrofit 2 里也多了一个 call 方法。语法和 OkHttp 基本一模一样，唯一不同是这个函数知道如何做数据的反序列化。它知道如何将 HTTP 响应转换成对象。

另外，每一个 call 对象实例只能被用一次，所以说 request 和 response 都是一一对应的。你其实可以通过 Clone 方法来创建一个一模一样的实例，这个开销是很小的。比如说：你可以在每次决定发请求前 clone 一个之前的实例。

另一个大的进步是 2.0 同时支持了在一个类型中的同步和异步。同时，一个请求也可以被真正地终止。终止操作会对底层的 http client 执行 cancel 操作。即便是正在执行的请求，也能立即切断。

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}

Call<List<Contributor>> call =
    gitHubService.repoContributors("square", "retrofit");
```

这个 Call 对象是从你的 API 接口返回的参数化后的对象。调用跟接口名相同的函数名，你就会得到一个实例出来。我们可以直接调用它的 execute 方法，但是得留意一下，这个方法只能调用一次。

```
Call<List<Contributor>> call =
    gitHubService.repoContributors("square", "retrofit");

response = call.execute();

// This will throw IllegalStateException:
response = call.execute();

Call<List<Contributor>> call2 = call.clone();
// This will not throw:
response = call2.execute();
```

当你尝试调用第二次的时候，就会出现失败的错误。实际上，可以直接克隆一个实例，代价非常低。当你想要多次请求一个接口的时候，直接用 clone 的方法来生产一个新的，相同的可用对象吧。

想要实现异步，需要调用 enqueue 方法。现在，我们就能通过一次声明实现同步和异步了

```
Call<List<Contributor>> call =
    gitHubService.repoContributors("square", "retrofit");

call.enqueue(new Callback<List<Contributor>>() {
  @Override void onResponse(/* ... */) {
    // ...
  }

  @Override void onFailure(Throwable t) {
    // ...
  }
});
```

当你将一些异步请求压入队列后，甚至你在执行同步请求的时候，你可以随时调用 cancel 方法来取消请求：

```
Call<List<Contributor>> call =
    gitHubService.repoContributors("square", "retrofit");

call.enqueue(         );
// or...
call.execute();

// later...
call.cancel();
```

### Parameterized Response Object (13:48)
另一个新的特性是参数化的 Response 类型。 Response 对象增加了曾经一直被我们忽略掉的重要元数据：响应码（the reponse code），响应消息（the response message），以及读取相应头（headers）。

```
class Response<T> {
  int code();
  String message();
  Headers headers();

  boolean isSuccess();
  T body();
  ResponseBody errorBody();
  com.squareup.okhttp.Response raw();
}
```

同时还提供了一个很方便的函数来帮助你判断请求是否成功完成，其实就是检查了下响应码是不是 200。然后就拿到了响应的 body 部分，另外有一个单独的方法获取 error body。基本上就是出现一个返回码，然后调用相对应的函数的。只有当响应成功以后，我们会去做反序列化操作，然后将反序列化的结果放到 body 回调中去。如果出现了返回了网络成功响应（返回码：200）却最终返回 false 的情况，我们实际上是无法判断返回到底是什么的，只能将 ResponseBody（简单封装的了 content-type，length，以及 raw body部分） 类型交给你去处理。

以上是在声明接口时候的两个重大的改变。

### 动态 URL Parameter (16:33)
动态 URL 参数是让我头疼多年的一个问题，现在我们终于解决了！如果你向 GitHub 发出多个请求，收到一个响应，通常这个响应大概像下面这样：

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}

Call<List<Contributor>> call =
    gitHubService.repoContributors("square", "retrofit");
Response<List<Contributor>> response = call.execute();

// HTTP/1.1 200 OK
// Link: <https://api.github.com/repositories/892275/contributors?
page=2>; rel="next", <https://api.github.com/repositories/892275/
contributors?page=3>; rel="last"
// ...
```

要是你想做分页，你就得自己去分析这些 URL 了。GitHub 可能将 header link 地址列表里的数据已经缓存在服务器内存里了，当你去按他们指引的地址去请求的话，他们就不必费劲去从数据库里给你拿数据了，速度上也更快。但是，在 Retrofit 1.0 的时候，我们没有办法去直接执行 GitHub Server 返回在 header 里的请求地址。

用上我们新的 response 类型后，不止是我刚才提到的那些元数据，我们还可以写一些方法来读出自定义的字段，比如上面例子里的下一页的地址：

```
Response<List<Contributor>> response = call.execute();

// HTTP/1.1 200 OK
// Link: <https://api.github.com/repositories/892275/contributors?
page=2>; rel="next", <https://api.github.com/repositories/892275/
contributors?page=3>; rel="last"
// ...

String links = response.headers().get("Link");
String nextLink = nextFromGitHubLinks(links);

// https://api.github.com/repositories/892275/contributors?page=2
```

这个可能和上面的接口生成地址略有不同。

动态 URL 地址就是用在连续请求里的。在第一个请求之后，如果返回的结果里有指明下个请求的地址的话，在之前，你可能得单独写个 interface 来处理这种情况，现在就无需那么费事了。

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);

  @GET
  Call<List<Contributor>> repoContributorsPaginate(
      @Url String url);
}
```

Retrofit 2.0 有了新的 标注：@Url ，允许你直接传入一个请求的 URL。

有了这个方法后，我们就可以直接把刚才取出来的下一页的地址传入，是不是一切都流畅了很多：

````
String nextLink = nextFromGitHubLinks(links);

// https://api.github.com/repositories/892275/contributors?page=2

Call<List<Contributor>> nextCall =
    gitHubService.repoContributorsPaginate(nextLink);
```

这样的话，我们就能通过调用 repoContributorsPaginate 来获取第二页内容，然后通过第二页的 header 来请求第三页。你可能很多的 API 都见到过类似的设计，这在 Retrofit 1 里确实是个困扰很多人的大麻烦。

### 更多更有效的 Converters (19:31)
Retrofit 1 里有一个 converter 的问题。多数人可能没遇到过，是库内部的一个问题。在 Retrofit 2 里，我们已经解决了这个问题，同时开始支持多种 Converter 并存。

在之前，如果你遇到这种情况：一个 API 请求返回的结果需要通过 JSON 反序列化，另一个 API 请求需要通过 proto 反序列化，唯一的解决方案就是将两个接口分离开声明。

```
interface SomeProtoService {
  @GET("/some/proto/endpoint")
  Call<SomeProtoResponse> someProtoEndpoint();
}

interface SomeJsonService {
  @GET("/some/json/endpoint")
  Call<SomeJsonResponse> someJsonEndpoint();
```

之所以搞得这么麻烦是因为一个 REST adapter 只能绑定一个 Converter 对象。我们费工夫去解决这个是因为：接口的声明是要语意化的。API 接口应该通过功能实现分组，比如： account 的接口，user 的接口，或者 Twitter 相关的接口。返回格式的差异不应该成为你分组时候的阻碍。

现在，你可以把他们都放在一起了：

```
interface SomeService {
  @GET("/some/proto/endpoint")
  Call<SomeProtoResponse> someProtoEndpoint();

  @GET("/some/json/endpoint")
  Call<SomeJsonResponse> someJsonEndpoint();
}
```

我大概提一下这个是怎么工作起来的，因为了解 Converter 的调用原理在写代码的时候很重要。

看我们上面的代码：第一个方法返回了一个 proto 对象。

```
SomeProtoResponse —> Proto? Yes!
```

原理很简单，其实就是对着每一个 converter 询问他们是否能够处理某种类型。我们问 proto 的 converter： “Hi, 你能处理 SomeProtoResponse 吗？”，然后它尽可能的去判断它是否可以处理这种类型。我们都知道：Protobuff 都是从一个名叫 message 或者 message lite 的类继承而来。所以，判断方法通常就是检查这个类是否继承自 message。

在面对 JSON 类型的时候，首先问 proto converter，proto converter 会发现这个不是继承子 Message 的，然后回复 no。紧接着移到下一个 JSON converter 上。JSON Converter 会回复说我可以！

```
SomeJsonResponse —> Proto? No! —> JSON? Yes!
```

因为 JSON 并没有什么继承上的约束。所以我们无法通过什么确切的条件来判断一个对象是否是 JSON 对象。以至于 JSON 的 converters 会对任何数据都回复说：我可以处理！这个一定要记住， JSON converter 一定要放在最后，不然会和你的预期不符。

另一个要注意的是，现在已经不提供默认的 converter 了。如果不显性的声明一个可用的 Converter 的话，Retrofit 是会报错的：提醒你没有可用的 Converter。因为核心代码已经不依赖序列化相关的第三方库了，我们依然提供对 Converter 的支持，不过你需要自己引入这些依赖，同时显性的声明 Retrofit 需要用的 Converter 有哪些。

### 更多可替换的执行机制 (22:38)
在此之前，Retrofit 有一个死板的 execution 流程。在 Retrofit 2 里，我们调整了整个流程，让它变得可替换（pluggable），同时允许多个。跟 converter 的工作原理很像。

比如说，你有一个方法返回了一个 Call 对象，Call 是内置的 Converter 类型。比如：Retrofit 2　的执行机制：

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);

...
```

现在，你可以自定义这些了。或者用我们提供的一个：

```
...

  @GET("/repos/{owner}/{repo}/contributors")
  Observable<List<Contributor>> repoContributors2(
      @Path("owner") String owner,
      @Path("repo") String repo);

  @GET("/repos/{owner}/{repo}/contributors")
  Future<List<Contributor>> repoContributors3(
      @Path("owner") String owner,
      @Path("repo") String repo);
}
```

Retrofit 2.0 依然支持 RxJava，但现在是分离的。（你如果想要一些别的特性，你也可以自己写一个）同时支持不同的 execution 是怎么实现的呢？

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(..);

  @GET("/repos/{owner}/{repo}/contributors")
  Observable<List<Contributor>> repoContributors2(..);

  @GET("/repos/{owner}/{repo}/contributors")
  Future<List<Contributor>> repoContributors3(..);
}
```

通过返回类型来判断需要调用哪个 exection。比如说：返回为 Call 的类型， 我们的整个执行机制会问：“Hey，你知不知道如何处理 Call ？”　如果是 RxJava，它就会说：“我不知道，我只知道　Observable 的处理方法。”。　随后，我们又问内部的 converter，他刚好回答说：“是的！我会！”。

```
call —> RxJava? No! —> Call? Yes!
```

Observable 也是同样的工作原理。我们同样问 RxJava，它就说：“我能处理这个”：

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(..);

  @GET("/repos/{owner}/{repo}/contributors")
  Observable<List<Contributor>> repoContributors2(..);

  @GET("/repos/{owner}/{repo}/contributors")
  Future<List<Contributor>> repoContributors3(..);
}
Observable —> RxJava? Yes!
```

如果你没装相对应的 Converter，这就意味着我们无法验证响应的类型。比如：如果询问是否有办法能处理　Future，他们两个都会说：“不行”。

```
interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(..);

  @GET("/repos/{owner}/{repo}/contributors")
  Observable<List<Contributor>> repoContributors2(..);

  @GET("/repos/{owner}/{repo}/contributors")
  Future<List<Contributor>> repoContributors3(..);
}
```

```
Future —> RxJava? No! —> Call? No! —> Throw!
```

这将会返回一个 Exception，意味着这两个以及内置的机制都无法处理这种类型。关于工作原理我们随后会深入讨论。

### OkHttp 提供支持 (24:17).

Retrofit 2 现在开始依赖了 OkHttp 了，而且这部分不再支持替换。这是一件比较有争议的事情。但是希望我能证明为什么这是一个对的决定。

OkHttp 现在很小而且很聚焦，有很多好用的 API 接口。我们在 Retrofit 2 里都有对 OkHttp 的接口映射，也基本具备了我们需要的所有的特性，包括提到的所有的抽象类们。这些都超赞！这是压缩 Retrofit 库大小的一个法宝。我们最终减小了Retrofit 60% 的体积，同时又具有了更多的特性。

### OkHttp 提供支持 (以及 Okio!) (26:20)

另一个用 OkHttp 的好处就是我们能够在 Retrofit 2 把 OkHttp 以公开接口的方式直接导出。你可能在 error body 方法或者响应里见到过 response body 的内容。显然，我们在 Retrofit 的 Reponse 对象里直接返回了 OkHttp 的 Response body。我们正在导出这些类型，OkHttp 的类型基本上已经以更好更简洁的 API 替代 Retrofit 1.0 的一些接口。

OkHttp 的背后是一个叫做 Okio 的库，提供的 IO 支持。我之前在 [Droidcon Montreal][Droidcon-Montreal] 做过关于这个库的演讲。讨论过为什么它是众多 IO 库中更好的选择，还讨论了它为何极度高效，以及为什么你应该使用它们。演讲中我还提到 Retrofit 2 ，当时它还是脑海里的一个概念。现在 Retrofit 2 已经实现了。

### Retrofit 2 的效率 (27:31)

我做了这个图表来展示 Retrofit 相比 Retrofit 1 以及其他可能的方案要高效的多，这归功于刚刚提到的硬性依赖和那些抽象。我带大家来看一下上面视频中的这个表。所以一定要看我演讲的[这部分][Video-Section-Of-Performance]噢！

## 初始化 - Retrofit 类型 (31:24)

现在，让我们来看一下 Retrofit 的类型是如何替代 REST adapter 类型的，以及如何初始化。原来的方法叫做 endpoint, 不过现在我们称之为 baseUrl, baseUrl 就是你所请求的 Server 的 URL，下面是一个请求 GitHub Api 的例子：

```
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://api.github.com")
    .build();

interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}

GitHubService gitHubService = retrofit.create(GitHubService.class);
```

我们声明了自己的接口，我们称作创建方法，跟 Retrofit 1 里的是一致的。接下来，我们来生成一个接口的实现，以使这些接口方法可以直接被调用。

当我们调用 repoContributors 这个方法的时候，Retrofit 会创建这个 URL。如果我们传入 Square 和 Retrofit 字符串，分别作为 owner 和 repo 参数。我们就会得到这个 URL：`https://api.github.com/repos/square/retrofit/contributors`。在 Retrofit 内部，Retrofit 会用 OkHttp 的 HTTP URL 类型作为 基础的 URL ，然后 resolve 方法就会取出相对地址和 baseUrl 拼接起来，接着发起请求。接下来给你展示下改变 API 前缀，比如 V3。

```
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://api.github.com/v3/")
    .build();

interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}
```

虽然说这不是 GitHub 真实的 API，但是真实世界里就是有很多 API 是由这样的前缀和路径组成的。调用相同的方法，被解析出来的 URL 将会是是这样的：`https://api.github.com/repos/square/retrofit/contributors`。可以看到在主机地址之后并没有v3 ，这是因为地址的 URL 是以一个**斜线**开始的，而在 HTTP 里，斜线开始的地址往往是绝对地址后缀路径。Retrofit 1 会因为语义化的约束，强制你加这个前缀斜线, 然后把 baseUrl 和相对地址拼接起来。现在，考虑到规范问题，我们已经对这两种地址加以区分。

```
interface GitHubService {
  @GET("repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}
```

如果有前缀 `/`就代表着是一个绝对路径。删除了那个前缀的`/`， 你将会得到正确的、包含了 v3 路径的全 URL。

```
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://api.github.com/v3/")
    .build();

interface GitHubService {
  @GET("repos/{owner}/{repo}/contributors")
  Call<List<Contributor>> repoContributors(
      @Path("owner") String owner,
      @Path("repo") String repo);
}

// https://api.github.com/v3/repos/square/retrofit/contributors
```

由于现在我们开始依赖`OkHttp`， 并没有 Http Client 层的抽象。现在是可以传递一个配置好的 OkHttp 实例的。比如：配置`interceptors`, 或者一个`SSL socket`工厂类， 或者`timeouts`的具体数值。 （OkHttp 有默认的超时机制，如果你不需要自定义，实际上不必进行任何设置，但是如果你想要去设置它们，下面是一个例子告诉你来怎么操作。）

```
OkHttpClient client = new OkHttpClient();
client.interceptors().add(..);

Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://api.github.com")
    .client(client)
    .build();
```

如果你要指明特定的 converter 或者 execute 机制，也是在这个时候加的。比如这会儿：我们可以给 GSON 设置一个或者多个 converter。也可以给 protocol buffer 设置一个 converter。

```
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://api.github.com")
    .addConverterFactory(GsonConverterFactory.create())
    .addConverterFactory(ProtoConverterFactory.create())
    .build();
```

我想要强调的是：添加 converter 的顺序很重要。按照这个顺序，我们将依次询问每一个 converter 能否处理一个类型。我上面写的其实是错的。如果我们试图反序列化一个 proto 格式，它其实会被当做 JSON 来对待。这显然不是我们想要的。我们需要调整下顺序，因为我们先要检查 proto buffer 格式，然后才是 JSON。

```
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://api.github.com")
    .addConverterFactory(ProtoConverterFactory.create())
    .addConverterFactory(GsonConverterFactory.create())
    .build();
Retrofit 的文档里可能还没这些，如果你想要使用 RxJava 来代替 call, 你需要一个 Call Adapter Factory:

Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://api.github.com")
    .addConverterFactory(ProtoConverterFactory.create())
    .addConverterFactory(GsonConverterFactory.create())
    .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
    .build();
```

Call Adapter Factory 是一个知道如何将 call 实例转换成其他类型的工厂类。目前，我们只有 RxJava 的类型，也就是将 Call 类型转换成 Observable 类型。如果你了解 RxJava, 其实还有一种新的`Observable`类型（一次只发射一个 item 的类型）。你可以用这个`call adapter factory`来转换到其中任意一种`Observable`。

## 扩展性 (36:50)

刚才提到的 Factory，也是可扩展的。这意味着你可以写属于自己的 Call Adapter Facotry。实现起来其实就是一个方法。传递给它一个类型，返回 null 代表拒绝，或者返回一个 converter 的实例。

```
SomeJsonResponse

		class ProtoConverterFactory {
  			Converter<?> create(Type type);		null
		}

		class GsonConverterFactory {
			Converter<?> create(Type type);		Converter<?>
		}
```

看上面的例子，如果你给它传递一个 JSON 的 Respnose 类型，这个类型不是从 proto 继承而来，那么它就会说：”我不知道如何传递这个，所以我返回空值 null.』，然而，对于`GSON converter`而言，它通过返回一个实例来表明它可以处理这个类型的。这就是为什么这个类是一个工厂类，因为我们让它生产`converter`实例。

如果你想要做一些自定义，实现起来是非常容易的。Converter 的实现与之前的实现是非常相似的，尽管代替类型化的输入和输出，我们现在使用的是 OkHttp 的请求body 和响应 body。

```
interface Converter<T> {
  interface Factory {
	Converter <?> create(Type type);
  }

  T fromBody(ResponseBody body);
  RequestBody toBody(T value);
}
```

现在这已经高效的多，因为我们实际上可以查询那些 adapter。举个例子，GSON 有一个type adapter, 当我们请求 GSON Converter Factory，询问它是否可以处理某种请求的时候， converter factory 就开始查询这个 adapter, 它将以缓存的形式存在，当我们再次查询的时候，这个 adapter 就可以直接被使用了。这是一个非常小的成功，极力避免了不断地查询带来的损耗。

call adapter 有相同的模式。我们询问一个 call adapter factory 它是否可以处理某个类型，它将会以相同的方式回应。（例如：它会返回 null 来表达否）。它的API 是非常简单的。

```
interface CallAdapter<T> {
  interface Factory {
    CallAdapter<?> create(Type type);
  }

  Type responseType();
  Object adapt(Call<T> value);
}
```

我们有一个方法来来实现适配。传入一个 call 实例，返回了一个 observable，single 或者 future 等。 还有一种方法来获得这种 response 类型：当我们声明一系列 contributor 调用时， 我们没法自动把那些参数化的类型提取出来，因此我们基本上只是请求这个 call adapter 也返回这个 response type。如果你为这个变量创造了一个实例，我们会请求 call adapter, 它会将 contributor type 列表返回。

## 还在建设中 (40:05)

Retrofit 2 正在完善中！现在还不够完整，但是已经可以用了。我上面提到的点都是已经完成了的，那还有哪些未完成呢？

关于所谓的“参数 handler”我们现在还没有一个成熟的想法。我们未来想要让它有从 Guava 传递多个 map，或者数据类型及枚举类型。

日志功能还没有完成，在 Retrofit 1 里是有日志的，但是在 Retrofit 2 里面没有。依赖 OkHttp 的一个优点是你实际上可以使用一个 interceptor 来实现实际的底层的请求和响应日志。因此，对于原始请求和响应，我们并不需要它，但是我们很可能需要日志来记录 Java 类型。

如果你曾经使用过 mock 模块，你会发现它也还没被完成，但很快会完成的。

现在文档依然比较缺。

最后，在我有空的时候，我想在 Retrofit 2 里支持 WebSocket。在 2.0 里很可能无法实现，但是我想在后续的2.1 版本里会加入支持。

## Release? (41:31)

我保证过 Retrofit 2 今年会和大家见面，今年确实可以。至于具体哪天问世，我们不会做任何承诺。我不想再在 2016 的 DroidCons 上开相同的玩笑。因此今年一定会问世。我保证。至于2015年8月27日，我已经开放了一个2.0的测试版。

```
dependencies {
  compile 'com.squareup.retrofit:retrofit:2.0.0-beta1'
  compile 'com.squareup.retrofit:converter-gson:2.0.0-beta1'
  compile 'com.squareup.retrofit:adapter-rxjava:2.0.0-beta1'
}
```

你可以依赖它。它已经可以正常工作了，API 接口也相对稳定。Converter 和 converter 工厂方法未来可能会改变，但是总体来说是有用的。要是你有什么不喜欢或者有问题的地方，请联系我！谢谢！


