# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Si usas Retrofit
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepattributes Signature

# Si usas GSON (para JSON)
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
   @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# Para mantener vistas que usan onClick en XML
-keepclassmembers class * {
    public void *(android.view.View);
}

# Evitar remover anotaciones necesarias
-keepattributes *Annotation*

# Para Jetpack Compose (si lo estás usando)
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Eliminar logs (para hacer más liviano y seguro)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
# ------------------------------
# Reglas sugeridas por R8
# ------------------------------
############################
# Apache POI / OOXML / XMLBeans (para exportación Excel)
############################
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**

-keep class org.openxmlformats.schemas.** { *; }
-dontwarn org.openxmlformats.schemas.**

-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**

# Silenciar dependencias AWT/XML stream usadas indirectamente por POI
-dontwarn java.awt.**
-dontwarn javax.xml.stream.**

-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn java.awt.Color
-dontwarn java.awt.color.ColorSpace
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.geom.Dimension2D
-dontwarn java.awt.geom.Path2D
-dontwarn java.awt.geom.PathIterator
-dontwarn java.awt.geom.Point2D
-dontwarn java.awt.geom.Rectangle2D$Double
-dontwarn java.awt.geom.Rectangle2D
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ColorModel
-dontwarn java.awt.image.ComponentColorModel
-dontwarn java.awt.image.DirectColorModel
-dontwarn java.awt.image.IndexColorModel
-dontwarn java.awt.image.PackedColorModel
-dontwarn javax.xml.stream.Location
-dontwarn javax.xml.stream.XMLStreamException
-dontwarn javax.xml.stream.XMLStreamReader
-dontwarn net.sf.saxon.**
-dontwarn org.apache.batik.**
-dontwarn org.osgi.framework.**

############################
# OkHttp/Okio (si usas Retrofit)
############################
-dontwarn okhttp3.**
-dontwarn okio.**

############################
# ContentProvider constructors (por FileProvider)
############################
-keepclassmembers class * extends android.content.ContentProvider {
    public <init>();
}

############################
# Coroutines (silenciar warnings comunes)
############################
-dontwarn kotlinx.coroutines.**
