package nl.jjt.vorfahrtfahrradcompanion.criteria

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private val sampleCatalogueJson = """
    {"weightRange":{"min":1,"max":100},"importanceRange":{"min":0,"max":200},"defaultImportance":100,"criteria":[
      {"id":"WIDTH","kind":"SINGLE","values":["W_0_5","W_1","W_2","W_3","W_4"]},
      {"id":"ALLOWED_USERS","kind":"MULTI","values":["CARS","CYCLISTS","PEDESTRIANS"]},
      {"id":"FUTURE_CRITERION","kind":"UNKNOWN_KIND","values":["A","B"]}
    ]}
""".trimIndent()

class CriteriaTest {

    @Test
    fun parsesJsonToDomain() {
        val catalogue = json.decodeFromString<CatalogueDto>(sampleCatalogueJson).toDomain()

        assertEquals(3, catalogue.criteria.size)

        val width = catalogue.criteria[0]
        assertEquals("WIDTH", width.id)
        assertEquals(CriterionKind.SINGLE, width.kind)
        assertEquals(listOf("W_0_5", "W_1", "W_2", "W_3", "W_4"), width.values)

        val allowed = catalogue.criteria[1]
        assertEquals("ALLOWED_USERS", allowed.id)
        assertEquals(CriterionKind.MULTI, allowed.kind)

        // Unknown kind degrades to MULTI instead of failing
        assertEquals(CriterionKind.MULTI, catalogue.criteria[2].kind)
    }

    @Test
    fun urlBuildingWithPrefixedBase() {
        val url = URLBuilder().apply {
            takeFrom("http://host/api")
            appendPathSegments("admin", "evaluation-model", "criterion-catalogue")
        }.build()

        assertEquals("/api/admin/evaluation-model/criterion-catalogue", url.encodedPath)
    }
}
