val doSomethingWithString by extra {
    fun(string : String): String {
        return string + "123"
    }
}

fun resolveIsCI() : String {
    return System.getenv("CI")
}

fun resolveBuildNum() : String {
    return System.getenv("BUILD_NUMBER")
}
