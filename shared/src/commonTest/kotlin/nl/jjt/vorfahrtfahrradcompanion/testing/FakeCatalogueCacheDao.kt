package nl.jjt.vorfahrtfahrradcompanion.testing

import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheDao
import nl.jjt.vorfahrtfahrradcompanion.db.catalogue.CatalogueCacheEntity

/** The single-row catalogue cache, as one nullable row. */
class FakeCatalogueCacheDao(var row: CatalogueCacheEntity? = null) : CatalogueCacheDao {
    override suspend fun get(id: Int) = row

    override suspend fun upsert(entity: CatalogueCacheEntity) {
        row = entity
    }

    override suspend fun clear() {
        row = null
    }
}
