package com.perseitech.sailpilot.routing

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.io.ParseException
import org.locationtech.jts.io.WKTReader

object WktLandLoader {
    private val geometryFactory = GeometryFactory()
    private val wktReader = WKTReader(geometryFactory)
    private val preparedFactory = PreparedGeometryFactory()

    data class PreparedLand(
        val geom: Geometry,
        val prepared: PreparedGeometry
    )

    fun loadAndPrepare(wkt: String): PreparedLand {
        val geom = parseGeometry(wkt)
        val fixed = GeometryFixer.fix(geom)
        fixed.normalize()
        val prepared = preparedFactory.create(fixed)
        return PreparedLand(fixed, prepared)
    }

    private fun parseGeometry(wkt: String): Geometry {
        if (wkt.isBlank()) {
            throw IllegalArgumentException("WKT string is blank")
        }
        try {
            return wktReader.read(wkt)
        } catch (err: ParseException) {
            throw IllegalArgumentException("Invalid WKT input", err)
        }
    }
}
