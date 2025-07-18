package test

/**
 * A simple Kotlin class to test UAST symbol extraction
 */
class KotlinTestClass(val name: String) {
    private var age: Int = 0
    
    companion object {
        const val DEFAULT_NAME = "Unknown"
    }
    
    fun greet(): String {
        return "Hello, $name!"
    }
    
    fun setAge(newAge: Int) {
        age = newAge
    }
    
    inner class InnerClass {
        fun getOuterName() = name
    }
}

interface TestInterface {
    fun doSomething()
}

data class DataClass(
    val id: Long,
    val description: String
)

enum class Status {
    ACTIVE,
    INACTIVE,
    PENDING
}

fun topLevelFunction(x: Int, y: Int): Int {
    return x + y
}

val topLevelProperty = "Test"
