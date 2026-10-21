-keepattributes Signature,InnerClasses,EnclosingMethod

# Media3 1.8.0 PlaybackVideoGraphWrapper reflects this constructor; upstream keeps
# build()/setEnableReplayableCache() but omits the no-argument constructor.
-keepclassmembers class androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder {
    public <init>();
}
