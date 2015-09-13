# 为了便于学习，只精简，不混淆

-keepnames class * {
    *;
}


-dontwarn java.lang.invoke.*


# Butter Knife

-keep class butterknife.** { *; }
-dontwarn butterknife.internal.**
-keep class **$$ViewBinder { *; }

-keepclasseswithmembernames class * {
    @butterknife.* <fields>;
}

-keepclasseswithmembernames class * {
    @butterknife.* <methods>;
}


# Okio

-dontwarn okio.**


# Retrofit

-dontwarn retrofit.**
-keep class retrofit.** { *; }
-keepattributes Signature
-keepattributes Exceptions


# Glide

-keep public class * implements com.bumptech.glide.module.GlideModule


# Support Design Library

-keep class * extends android.support.design.widget.CoordinatorLayout$Behavior {
    *;
}


# Realm

-keep @io.realm.annotations.RealmModule class *
-dontwarn javax.**
-dontwarn io.realm.**


# Umeng

-keepclassmembers class * {
   public <init>(org.json.JSONObject);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn com.umeng.**
-dontwarn org.apache.http.**
