plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    group = "cn.enaium.sdl"
    // <this library's version>-fixed.<pinned SDL_image release>
    version = "1.0.2-fixed.3.4.8"
}
