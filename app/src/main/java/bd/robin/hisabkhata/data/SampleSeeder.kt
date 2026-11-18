package bd.robin.hisabkhata.data

import bd.robin.hisabkhata.logic.Dates
import bd.robin.hisabkhata.logic.Kind
import java.util.UUID

object SampleSeeder {
    suspend fun seed(repo: LedgerRepository) {
        val now = System.currentTimeMillis()
        val today = Dates.today()
        val day0 = Dates.startOfDay(today)
        val day1 = Dates.startOfDay(today.minusDays(1))
        val day2 = Dates.startOfDay(today.minusDays(2))
        repo.saveParty(party("sample-karim", Kind.CUSTOMER, "করিম মিয়া", "01711111111", true, 2000, now))
        repo.saveParty(party("sample-rina", Kind.CUSTOMER, "রিনা বেগম", "01812222222", false, 0, now))
        repo.saveParty(party("sample-jamal", Kind.CUSTOMER, "জামাল উদ্দিন", "01913333333", true, 2500, now))
        repo.saveParty(party("sample-supplier", Kind.SUPPLIER, "মেসার্স করিম ট্রেডার্স", "01614444444", false, 0, now))
        repo.saveEntry(entry("sample-e1", "sample-karim", Kind.CREDIT, 1200, "চাল", day2, now))
        repo.saveEntry(entry("sample-e2", "sample-karim", Kind.PAYMENT, 200, "", day0, now))
        repo.saveEntry(entry("sample-e3", "sample-rina", Kind.CREDIT, 500, "তেল", day1, now))
        repo.saveEntry(entry("sample-e4", "sample-rina", Kind.PAYMENT, 500, "", day0, now))
        repo.saveEntry(entry("sample-e5", "sample-jamal", Kind.CREDIT, 3000, "মাসিক বাকি", day1, now))
        repo.saveEntry(entry("sample-e6", "sample-supplier", Kind.CREDIT, 800, "মাল", day2, now))
        repo.saveCash(cash("sample-c1", Kind.INCOME, "sale", 1500, "দোকান", day0, now))
        repo.saveCash(cash("sample-c2", Kind.EXPENSE, "rent", 500, "", day0, now))
        repo.saveCash(cash("sample-c3", Kind.EXPENSE, "food", 80, "", day1, now))
        repo.saveProduct(product("sample-p1", "মিনিকেট চাল (কেজি)", 70, 40, "8941100000017", now))
        repo.saveProduct(product("sample-p2", "সয়াবিন তেল (লিটার)", 175, 8, "8941100000024", now))
        repo.saveProduct(product("sample-p3", "সাবান", 45, 2, "8941100000031", now))
    }

    private fun party(id: String, type: String, name: String, phone: String, custom: Boolean, limit: Long, now: Long) =
        PartyEntity(id, type, name, phone, "ঢাকা", "", "", custom, limit, false, 0, Kind.VERIFY_NONE, now, now)

    private fun entry(id: String, party: String, type: String, amount: Long, note: String, date: Long, now: Long) =
        EntryEntity(id, party, type, amount, note, date, now, now)

    private fun cash(id: String, type: String, category: String, amount: Long, note: String, date: Long, now: Long) =
        CashEntity(id, type, category, amount, note, date, now, now)

    private fun product(id: String, name: String, price: Long, stock: Int, barcode: String, now: Long) =
        ProductEntity(id, name, price, stock, barcode, "", now, now)

    @Suppress("unused")
    private fun newId() = UUID.randomUUID().toString()
}
