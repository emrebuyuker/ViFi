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
