package cc.thevar.acc

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform