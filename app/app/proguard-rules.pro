# WorkManager builds its workers reflectively from the class name recorded at enqueue time.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keepattributes *Annotation*, InnerClasses, Signature

-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**