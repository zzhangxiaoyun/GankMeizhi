# 为了便于学习，只精简，不混淆

-keepnames class * {
    *;
}


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


# Support Design Library

-keep class * extends android.support.design.widget.CoordinatorLayout$Behavior {
    *;
}
