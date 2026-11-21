# ViFi release shrinking rules (R8). Libraries that ship consumer rules (Firebase, Hilt, Compose,
# Navigation, kotlinx-serialization, coroutines) need nothing here.

# Readable crash stack traces; the source file name itself is hidden.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Domain models are mapped by hand today (no reflection). When a Firestore DTO is read with
# toObject()/getValue(Class), keep its no-arg constructor and properties, e.g.:
# -keepclassmembers class com.salticusteam.vifi.core.data.**.dto.** { <init>(); *; }

# Type-safe navigation looks enum route arguments up by their serial name (Class.forName on the enum's
# fully-qualified name), so an enum used in a ViFiRoute must keep its class name. ViFiRoute.Exam carries ExamKind.
-keepnames enum com.salticusteam.vifi.core.model.ExamKind

# SMT-01 (AndroidMath): libmain.so builds com.pvporbit.freetype objects through JNI (FindClass + GetMethodID
# "<init>" + NewObject). The AAR ships no consumer rules, so without this R8 strips/renames the constructors and the
# app aborts with "JNI ... mid == null" on first draw.
-keep class com.pvporbit.freetype.** { *; }
