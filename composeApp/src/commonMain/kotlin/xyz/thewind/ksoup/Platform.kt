package xyz.thewind.ksoup

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform