# FFmpeg的Android平台移植—编译篇

来源:[CSDN](http://blog.csdn.net/gobitan/article/details/22750719)

摘要：本文主要介绍将FFmpeg音视频编解码库移植到Android平台上的编译和基本测试过程。

## 环境准备：

Ubuntu12.04 TLS

android-ndk-r9d-linux-x86_64.tar.bz2

adt-bundle-windows-x86_64-20131030.zip


## 第一步：源代码下载

到FFmpeg官方网站[http://www.ffmpeg.org/](http://www.ffmpeg.org/)上去下载源代码，这里下载的源代码是最权威的。进入官网之后，选择”Download”进入下载页面，截止2014年3月28日止，最新的发布的稳定版本为FFmpeg2.2，代号”Muybridge”。选择该下方的”Downloadgzip tarball”进行下载，下载后的文件名为ffmpeg-2.2.tar.gz，大约8.3M。

## 第二步：在Linux环境下编译FFmpeg

在Windows平台可以采用VMplayer虚拟机上安装ubuntu的方式，本人也是采用这种方式。

本文以/home/dennis为根目录进行操作和说明：

将ffmpeg-2.2.tar.gz拷贝至根目录，然后执行如下解压命令将其解压：

```
$tar zxf ffmpeg-2.2.tar.gz
```

解压后将得到/home/dennis/ffmpeg-2.2目录。

### 修改ffmpeg-2.2/configure文件

如果直接按照未修改的配置进行编译，结果编译出来的so文件类似libavcodec.so.55.39.101，版本号位于so之后，Android上似乎无法加载。因此需要按如下修改：

将该文件中的如下四行：

```
SLIBNAME_WITH_MAJOR='$(SLIBNAME).$(LIBMAJOR)'

LIB_INSTALL_EXTRA_CMD='$$(RANLIB)"$(LIBDIR)/$(LIBNAME)"'

SLIB_INSTALL_NAME='$(SLIBNAME_WITH_VERSION)'

SLIB_INSTALL_LINKS='$(SLIBNAME_WITH_MAJOR)$(SLIBNAME)'
```

替换为：

```
SLIBNAME_WITH_MAJOR='$(SLIBPREF)$(FULLNAME)-$(LIBMAJOR)$(SLIBSUF)'

LIB_INSTALL_EXTRA_CMD='$$(RANLIB)"$(LIBDIR)/$(LIBNAME)"'

SLIB_INSTALL_NAME='$(SLIBNAME_WITH_MAJOR)'

SLIB_INSTALL_LINKS='$(SLIBNAME)'
```

### 编写build_android.sh脚本文件

FFmpeg可以说是一个包络音视频编解码及格式的超级霸。因此在编译前通常都需要进行配置，设置相应的环境变量等。

所有的配置选项都在ffmpeg-2.2/configure这个脚本文件中，可以通过执行如下命令来查看所有的配置选项：

```
$ ./configure –help
```

配置选项很多，也较为复杂，这里先把我需要的搞出来，然后有时间再慢慢看。

我们将需要的配置项和环境变量设置写成一个sh脚本文件来运行以便编译出Android平台需要的so文件出来。

build_android.sh的内容如下：

```
 #!/bin/bash  
NDK=/home/dennis/android-ndk-r9d  
SYSROOT=$NDK/platforms/android-9/arch-arm/  
TOOLCHAIN=$NDK/toolchains/arm-linux-androideabi-4.8/prebuilt/linux-x86_64  
  
function build_one  
{  
./configure \  
    --prefix=$PREFIX \  
    --enable-shared \  
    --disable-static \  
    --disable-doc \  
    --disable-ffserver \  
    --enable-cross-compile \  
    --cross-prefix=$TOOLCHAIN/bin/arm-linux-androideabi- \  
    --target-os=linux \  
    --arch=arm \  
    --sysroot=$SYSROOT \  
    --extra-cflags="-Os -fpic $ADDI_CFLAGS" \  
    --extra-ldflags="$ADDI_LDFLAGS" \  
    $ADDITIONAL_CONFIGURE_FLAG  
}  
CPU=arm  
PREFIX=$(pwd)/android/$CPU  
ADDI_CFLAGS="-marm"  
build_one  

```

这个脚本文件有几个地方需要注意：

(1)    NDK,SYSROOT和TOOLCHAIN这三个环境变量一定要换成你自己机器里的。

(2)    确保cross-prefix变量所指向的路径是存在的。

### 给build_android.sh增加可执行权限：

```
$chmod+x build_android.sh  
```

执行build_android.sh

```
$./build_android.sh  
```

配置该脚本完成对ffmpeg的配置，会生成config.h等配置文件，后面的编译会用到。如果未经过配置直接进行编译会提示无法找到config.h文件等错误。

```
$make  
$make install  
```

至此，会在/home/dennis/ffmpeg-2.2目录下生成一个android目录，其中/home/dennis/ffmpeg-2.2/android/arm/lib目录下的so库文件如下：

```
-rwxr-xr-x 1 dennisdennis   55208 Mar 29 16:26libavdevice-55.so  
-rwxr-xr-x 1 dennisdennis  632476 Mar 29 16:26 libavfilter-4.so  
-rwxr-xr-x 1 dennisdennis 1442948 Mar 29 16:26 libavformat-55.so  
-rwxr-xr-x 1 dennisdennis 7985396 Mar 29 16:26 libavcodec-55.so  
-rwxr-xr-x 1 dennisdennis   83356 Mar 29 16:26libswresample-0.so  
-rwxr-xr-x 1 dennisdennis  308636 Mar 29 16:26 libswscale-2.so  
-rwxr-xr-x 1 dennisdennis  300580 Mar 29 16:26libavutil-52.so  
```

注：以上列表去掉了符号链接文件和pkgconfig目录。

## 第三步：创建一个普通的Android工程

1、创建一个新的Android工程FFmpeg4Android

2、在工程根目录下创建jni文件夹

3、在jni下创建prebuilt目录，然后：

(1) 将上面编译成功的7个so文件放入到该目录下;

(2)将/home/dennis/ffmpeg-2.2/android/arm/include下的所有头文件夹拷贝到该目录下.

4、创建包含native方法的类，先在src下创建cn.dennishucd包，然后创建FFmpegNative.java类文件。主要包括加载so库文件和一个native测试方法两部分，其内容如下：

```
package cn.dennishucd;  
public class FFmpegNative {  
	static{
        System.loadLibrary("avutil-52");
        System.loadLibrary("avcodec-55");
        System.loadLibrary("swresample-0");
        System.loadLibrary("avformat-55");
        System.loadLibrary("swscale-2");
        System.loadLibrary("avfilter-3");
        System.loadLibrary("ffmpeg_codec");
    }
    publicnative int avcodec_find_decoder(int codecID); 
}  
```

5、用javah创建.头文件:

进入bin/classes目录，执行：javah-jni cn.dennishucd.FFmpegNative                 

6、会在当前目录产生cn_dennishucd_FFmpegNative.h的C头文件;

根据头文件名，建立相同名字才C源文件cn_dennishucd_FFmpegNative.c，在这个源文件中实现头文件中定义的方法，核心部分代码如下：

```
JNIEXPORT jint JNICALLJava_cn_dennishucd_FFmpegNative_avcodec_1find_1decoder
        (JNIEnv *env, jobject obj, jint codecID)
{
    AVCodec*codec = NULL;  

            /*register all formats and codecs */
    av_register_all();

    codec= avcodec_find_decoder(codecID);

    if(codec != NULL)
    {
        return0;
    }
    else
    {
        return-1;
    }
}
```

7、编写Android.mk，内容如下：

```
LOCAL_PATH := $(callmy-dir)

include $(CLEAR_VARS)
LOCAL_MODULE :=avcodec-55-prebuilt
LOCAL_SRC_FILES :=prebuilt/libavcodec-55.so
include$(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=avdevice-55-prebuilt
LOCAL_SRC_FILES :=prebuilt/libavdevice-55.so
include$(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=avfilter-4-prebuilt
LOCAL_SRC_FILES :=prebuilt/libavfilter-4.so
include$(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=avformat-55-prebuilt
LOCAL_SRC_FILES :=prebuilt/libavformat-55.so
include$(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=  avutil-52-prebuilt
LOCAL_SRC_FILES :=prebuilt/libavutil-52.so
include$(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=  avswresample-0-prebuilt
LOCAL_SRC_FILES :=prebuilt/libswresample-0.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE :=  swscale-2-prebuilt
LOCAL_SRC_FILES :=prebuilt/libswscale-2.so
include$(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE :=ffmpeg_codec
LOCAL_SRC_FILES :=cn_dennishucd_FFmpegNative.c

LOCAL_LDLIBS := -llog-ljnigraphics -lz -landroid
LOCAL_SHARED_LIBRARIES:= avcodec-55-prebuilt avdevice-55-prebuilt avfilter-4-prebuiltavformat-55-prebuilt avutil-52-prebuilt

include$(BUILD_SHARED_LIBRARY)
```

8、编写Application.mk[可省略]
9、编译so文件

打开cmd命令行，进入FFmpeg4Android\jni目录下，执行如下命令：

```
$ndk-build  
```

截止本步骤完成，将在FFmpeg4Android根目录下生成libs\armeabi目录，该目录除了包含上面的7个so之外，另外还生成了libffmpeg_codec.so文件。

10、添加库的加载方法

在FFmpegNative类中增加如下加载so库的代码：

```
static {
    System.loadLibrary("avutil-52");
    System.loadLibrary("avcodec-55");
    System.loadLibrary("swresample-0");
    System.loadLibrary("avformat-55");
    System.loadLibrary("swscale-2");
    System.loadLibrary("avfilter-3");
    System.loadLibrary("avdevice-55");
    System.loadLibrary("ffmpeg_codec");
}
```

11、修改layout/main.xml，给TextView增加id，以便在代码中操作它。

```
<?xml version="1.0" encoding="utf-8"?>  
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"  
    android:orientation="horizontal"  
    android:layout_width="fill_parent"  
    android:layout_height="fill_parent"  
    >  
  <TextView  
    android:id="@+id/textview_hello"  
    android:text="@string/hello"  
    android:layout_width="wrap_content"  
    android:layout_height="wrap_content"  
    android:layout_gravity="center"  
    />  
</LinearLayout>  
```

12、增加一个Activity实现类FFmpeg4AndroidActivity，在OnCreate方法中调用native函数将值传给TextView控件，打包运行即可。FFmpeg4AndroidActivity代码如下：

```
package cn.dennishucd;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public class FFmpeg4AndroidActivity extends Activity {
    @Override
    protectedvoid onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        TextViewtv = (TextView)this.findViewById(R.id.textview_hello);

        FFmpegNativeffmpeg = new FFmpegNative();
        intcodecID = 28; //28 is the H264 Codec ID  

        intres = ffmpeg.avcodec_find_decoder(codecID);

        if(res ==0) {
            tv.setText("Success!");
        }
        else{
            tv.setText("Failed!");
        }
    }
}
```

代码中的28是H264的编解码ID，可以在ffmpeg的源代码中找到，它是枚举类型定义的。在C语言中，可以换算为整型值。这里测试能否找到H264编解码，如果能找到，说明调用ffmpeg的库函数是成功的，这也表明我们编译的so文件是基本可用。

作者注：

[1] 本文编译的方法主要参考了参考资料 [1] 中的思路，这里要感谢作者的贡献;

[2] 后面的测试过程是参考了ffmpeg-2.1.4中的decoding_encoding.c例子;

[3] 关于如何使用pre-built参考了参考资料 [2] 中的思路;

[4] 这只是移植过程第一步，后面还会进一步分析ffmpeg的接口来调用其编解码库.

[5]Android.mk文件应该还可以优化，这是后面的工作.

[6] 整个过程完成还是耗费了我不少精力，如有转载请注明出处，多谢。本文的完整源代码可以在我的CSDN资源(http://download.csdn.net/detail/gobitan/7132037)下载，最新版本可跟踪我的github(https://github.com/dennishucd/FFmpeg4Android)。

## 参考资料：

[http://www.roman10.net/how-to-build-ffmpeg-with-ndk-r9/](http://www.roman10.net/how-to-build-ffmpeg-with-ndk-r9/)<br/>
[http://www.ciaranmccormack.com/creating-a-prebuilt-shared-library-on-android-using-the-ndk/](http://www.ciaranmccormack.com/creating-a-prebuilt-shared-library-on-android-using-the-ndk/)<br/>