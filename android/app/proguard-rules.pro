# Keep rules are added as libraries that need them are introduced (ObjectBox, Retrofit, ONNX Runtime...).

# ObjectBox: MyObjectBox, the entity classes and their generated Cursor / _ helper classes are found by name at runtime.
# Without these rules the release build (R8) can strip or rename them and the app closes as soon as the database is opened.
-keep class com.khojoai.app.data.db.** { *; }
-keep class io.objectbox.** { *; }
-dontwarn io.objectbox.**
