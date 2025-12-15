package eu.kanade.tachiyomi.extension.all.booklore

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class BookloreFactory : SourceFactory {
    override fun createSources(): List<Source> {
        val firstBooklore = Booklore("")
        val bookloreCount = firstBooklore.preferences
            .getString(Booklore.PREF_EXTRA_SOURCES_COUNT, Booklore.PREF_EXTRA_SOURCES_DEFAULT)!!
            .toInt()

        // Booklore(""), Booklore("2"), Booklore("3"), ...
        return buildList(bookloreCount) {
            add(firstBooklore)

            for (i in 0 until bookloreCount) {
                add(Booklore("${i + 2}"))
            }
        }
    }
}
