package bd.robin.hisabkhata.data

data class ShopBook(val id: String, val name: String, val plan: String)

fun parseBooks(raw: String, fallbackName: String): List<ShopBook> {
    if (raw.isBlank()) return listOf(ShopBook("main", fallbackName, "স্ট্যান্ডার্ড"))
    return raw.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 2 || parts[0].isBlank()) null
        else ShopBook(parts[0], parts[1], parts.getOrElse(2) { "স্ট্যান্ডার্ড" })
    }.toList().ifEmpty { listOf(ShopBook("main", fallbackName, "স্ট্যান্ডার্ড")) }
}

fun booksToString(books: List<ShopBook>): String =
    books.joinToString("\n") { "${it.id}\t${it.name}\t${it.plan}" }
