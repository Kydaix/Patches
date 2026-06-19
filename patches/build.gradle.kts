group = "app.instagram.revanced"

patches {
    about {
        name = "Instagram ReVanced Patches"
        description = "Patches personnels pour Instagram (Instants : envoyer depuis la galerie)"
        source = "https://github.com/Kydaix/Patches"
        author = "Kydaix"
        contact = "gueguenhugo@proton.me"
        website = "https://github.com/Kydaix/Patches"
        license = "GNU General Public License v3.0"
    }
}

dependencies {
    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    // Android API stubs.
    compileOnly(project(":patches:stub"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters",
        )
    }
}
