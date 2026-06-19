group = "app.instagram.revanced"

kotlin {
    compilerOptions {
        // L'API des patchs (fingerprint.method, etc.) utilise des context receivers.
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

patches {
    about {
        name = "Instagram ReVanced Patches"
        description = "Patches personnels pour Instagram (com.instagram.android)"
        source = "https://github.com/Kydaix/Patches"
        author = "Kydaix"
        contact = "gueguenhugo@proton.me"
        website = "https://github.com/Kydaix/Patches"
        license = "GNU General Public License v3.0"
    }
}
