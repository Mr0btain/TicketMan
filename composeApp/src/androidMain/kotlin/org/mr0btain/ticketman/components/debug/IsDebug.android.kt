

actual class BuildConfigImpl(private val applicationContext: Context) : BuildConfig {
    override fun isDebug(): Boolean {
        return 0 != applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
    }
}