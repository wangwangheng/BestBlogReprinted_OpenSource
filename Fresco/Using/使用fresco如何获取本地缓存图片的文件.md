# 使用fresco如何获取本地缓存图片的文件

来源:[http://blog.csdn.net/it_talk/article/details/50509548](http://blog.csdn.net/it_talk/article/details/50509548)

```
FileBinaryResource resource = (FileBinaryResource)Fresco.getImagePipelineFactory().getMainDiskStorageCache().getResource(new SimpleCacheKey(uri.toString()));
File file = resource.getFile();
```

fresco保存的缓存文件是以cnt结尾的，拿到文件后只要另存为jpg或png文件即可。

貌似上面这种方法不推荐，一下两个链接可以看下： 

[http://stackoverflow.com/questions/29772949/android-how-to-get-image-file-from-fresco-disk-cache/31610386#31610386](http://stackoverflow.com/questions/29772949/android-how-to-get-image-file-from-fresco-disk-cache/31610386#31610386)

[https://github.com/facebook/fresco/issues/80](https://github.com/facebook/fresco/issues/80)