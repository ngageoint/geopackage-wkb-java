package mil.nga.sf.wkb;

import java.io.IOException;
import java.nio.ByteOrder;

import mil.nga.sf.CircularString;
import mil.nga.sf.CompoundCurve;
import mil.nga.sf.Curve;
import mil.nga.sf.CurvePolygon;
import mil.nga.sf.Geometry;
import mil.nga.sf.GeometryCollection;
import mil.nga.sf.GeometryType;
import mil.nga.sf.LineString;
import mil.nga.sf.MultiLineString;
import mil.nga.sf.MultiPoint;
import mil.nga.sf.MultiPolygon;
import mil.nga.sf.Point;
import mil.nga.sf.Polygon;
import mil.nga.sf.PolyhedralSurface;
import mil.nga.sf.TIN;
import mil.nga.sf.Triangle;
import mil.nga.sf.util.ByteReader;
import mil.nga.sf.util.SFException;
import mil.nga.sf.util.filter.GeometryFilter;

/**
 * Well Known Binary reader
 * 
 * @author osbornb
 */
public class GeometryReader {

	/**
	 * 2.5D bit
	 */
	private static final long WKB25D = Long.decode("0x80000000");

	/**
	 * Read a geometry from the byte reader
	 * 
	 * @param reader
	 *            byte reader
	 * @return geometry
	 * @throws IOException
	 *             upon failure to read
	 */
	public static Geometry readGeometry(ByteReader reader) throws IOException {
		return readGeometry(reader, null, null);
	}

	/**
	 * Read a geometry from the byte reader
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @return geometry
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static Geometry readGeometry(ByteReader reader,
			GeometryFilter filter) throws IOException {
		return readGeometry(reader, filter, null);
	}

	/**
	 * Read a geometry from the byte reader
	 * 
	 * @param reader
	 *            byte reader
	 * @param expectedType
	 *            expected type
	 * @param <T>
	 *            geometry type
	 * @return geometry
	 * @throws IOException
	 *             upon failure to read
	 */
	public static <T extends Geometry> T readGeometry(ByteReader reader,
			Class<T> expectedType) throws IOException {
		return readGeometry(reader, null, expectedType);
	}

	/**
	 * Read a geometry from the byte reader
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param expectedType
	 *            expected type
	 * @param <T>
	 *            geometry type
	 * @return geometry
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static <T extends Geometry> T readGeometry(ByteReader reader,
			GeometryFilter filter, Class<T> expectedType) throws IOException {
		return readGeometry(reader, filter, null, expectedType);
	}

	/**
	 * Read a geometry from the byte reader
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param containingType
	 *            containing geometry type
	 * @param expectedType
	 *            expected type
	 * @param <T>
	 *            geometry type
	 * @return geometry
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static <T extends Geometry> T readGeometry(ByteReader reader,
			GeometryFilter filter, GeometryType containingType,
			Class<T> expectedType) throws IOException {

		ByteOrder originalByteOrder = reader.getByteOrder();

		// Read the byte order and geometry type
		GeometryTypeInfo geometryTypeInfo = readGeometryType(reader);

		GeometryType geometryType = geometryTypeInfo.getGeometryType();
		boolean hasZ = geometryTypeInfo.hasZ();
		boolean hasM = geometryTypeInfo.hasM();

		Geometry geometry = null;

		switch (geometryType) {

		case GEOMETRY:
			throw new SFException("Unexpected Geometry Type of "
					+ geometryType.name() + " which is abstract");
		case POINT:
			geometry = readPoint(reader, hasZ, hasM);
			break;
		case LINESTRING:
			geometry = readLineString(reader, filter, hasZ, hasM);
			break;
		case POLYGON:
			geometry = readPolygon(reader, filter, hasZ, hasM);
			break;
		case MULTIPOINT:
			geometry = readMultiPoint(reader, filter, hasZ, hasM);
			break;
		case MULTILINESTRING:
			geometry = readMultiLineString(reader, filter, hasZ, hasM);
			break;
		case MULTIPOLYGON:
			geometry = readMultiPolygon(reader, filter, hasZ, hasM);
			break;
		case GEOMETRYCOLLECTION:
		case MULTICURVE:
		case MULTISURFACE:
			geometry = readGeometryCollection(reader, filter, hasZ, hasM);
			break;
		case CIRCULARSTRING:
			geometry = readCircularString(reader, filter, hasZ, hasM);
			break;
		case COMPOUNDCURVE:
			geometry = readCompoundCurve(reader, filter, hasZ, hasM);
			break;
		case CURVEPOLYGON:
			geometry = readCurvePolygon(reader, filter, hasZ, hasM);
			break;
		case CURVE:
			throw new SFException("Unexpected Geometry Type of "
					+ geometryType.name() + " which is abstract");
		case SURFACE:
			throw new SFException("Unexpected Geometry Type of "
					+ geometryType.name() + " which is abstract");
		case POLYHEDRALSURFACE:
			geometry = readPolyhedralSurface(reader, filter, hasZ, hasM);
			break;
		case TIN:
			geometry = readTIN(reader, filter, hasZ, hasM);
			break;
		case TRIANGLE:
			geometry = readTriangle(reader, filter, hasZ, hasM);
			break;
		default:
			throw new SFException(
					"Geometry Type not supported: " + geometryType);
		}

		if (!filter(filter, containingType, geometry)) {
			geometry = null;
		}

		// If there is an expected type, verify the geometry is of that type
		if (expectedType != null && geometry != null
				&& !expectedType.isAssignableFrom(geometry.getClass())) {
			throw new SFException("Unexpected Geometry Type. Expected: "
					+ expectedType.getSimpleName() + ", Actual: "
					+ geometry.getClass().getSimpleName());
		}

		// Restore the byte order
		reader.setByteOrder(originalByteOrder);

		@SuppressWarnings("unchecked")
		T result = (T) geometry;

		return result;
	}

	/**
	 * Read the geometry type info
	 * 
	 * @param reader
	 *            byte reader
	 * @return geometry type info
	 * @throws IOException
	 *             upon failure to read
	 */
	public static GeometryTypeInfo readGeometryType(ByteReader reader)
			throws IOException {

		// Read the single byte order byte
		byte byteOrderValue = reader.readByte();
		ByteOrder byteOrder = byteOrderValue == 0 ? ByteOrder.BIG_ENDIAN
				: ByteOrder.LITTLE_ENDIAN;
		reader.setByteOrder(byteOrder);

		// Read the geometry type unsigned integer
		long unsignedGeometryTypeCode = reader.readUnsignedInt();

		// Check for 2.5D geometry types
		boolean hasZ = false;
		if (unsignedGeometryTypeCode > WKB25D) {
			hasZ = true;
			unsignedGeometryTypeCode -= WKB25D;
		}

		int geometryTypeCode = (int) unsignedGeometryTypeCode;

		// Determine the geometry type
		GeometryType geometryType = GeometryCodes
				.getGeometryType(geometryTypeCode);

		// Determine if the geometry has a z (3d) or m (linear referencing
		// system) value
		if (!hasZ) {
			hasZ = GeometryCodes.hasZ(geometryTypeCode);
		}
		boolean hasM = GeometryCodes.hasM(geometryTypeCode);

		GeometryTypeInfo geometryInfo = new GeometryTypeInfo(geometryTypeCode,
				geometryType, hasZ, hasM);

		return geometryInfo;
	}

	/**
	 * Read a Point
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return point
	 * @throws IOException
	 *             upon failure to read
	 */
	public static Point readPoint(ByteReader reader, boolean hasZ, boolean hasM)
			throws IOException {

		double x = reader.readDouble();
		double y = reader.readDouble();

		Point point = new Point(hasZ, hasM, x, y);

		if (hasZ) {
			double z = reader.readDouble();
			point.setZ(z);
		}

		if (hasM) {
			double m = reader.readDouble();
			point.setM(m);
		}

		return point;
	}

	/**
	 * Read a Line String
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return line string
	 * @throws IOException
	 *             upon failure to read
	 */
	public static LineString readLineString(ByteReader reader, boolean hasZ,
			boolean hasM) throws IOException {
		return readLineString(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Line String
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return line string
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static LineString readLineString(ByteReader reader,
			GeometryFilter filter, boolean hasZ, boolean hasM)
			throws IOException {

		LineString lineString = new LineString(hasZ, hasM);

		int numPoints = reader.readInt();

		for (int i = 0; i < numPoints; i++) {
			Point point = readPoint(reader, hasZ, hasM);
			if (filter(filter, GeometryType.LINESTRING, point)) {
				lineString.addPoint(point);
			}
		}

		return lineString;
	}

	/**
	 * Read a Polygon
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return polygon
	 * @throws IOException
	 *             upon failure to read
	 */
	public static Polygon readPolygon(ByteReader reader, boolean hasZ,
			boolean hasM) throws IOException {
		return readPolygon(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Polygon
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return polygon
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static Polygon readPolygon(ByteReader reader, GeometryFilter filter,
			boolean hasZ, boolean hasM) throws IOException {

		Polygon polygon = new Polygon(hasZ, hasM);

		int numRings = reader.readInt();

		for (int i = 0; i < numRings; i++) {
			LineString ring = readLineString(reader, filter, hasZ, hasM);
			if (filter(filter, GeometryType.POLYGON, ring)) {
				polygon.addRing(ring);
			}
		}

		return polygon;
	}

	/**
	 * Read a Multi Point
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return multi point
	 * @throws IOException
	 *             upon failure to read
	 */
	public static MultiPoint readMultiPoint(ByteReader reader, boolean hasZ,
			boolean hasM) throws IOException {
		return readMultiPoint(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Multi Point
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return multi point
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static MultiPoint readMultiPoint(ByteReader reader,
			GeometryFilter filter, boolean hasZ, boolean hasM)
			throws IOException {

		MultiPoint multiPoint = new MultiPoint(hasZ, hasM);

		int numPoints = reader.readInt();

		for (int i = 0; i < numPoints; i++) {
			Point point = readGeometry(reader, filter, GeometryType.MULTIPOINT,
					Point.class);
			if (point != null) {
				multiPoint.addPoint(point);
			}
		}

		return multiPoint;
	}

	/**
	 * Read a Multi Line String
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return multi line string
	 * @throws IOException
	 *             upon failure to read
	 */
	public static MultiLineString readMultiLineString(ByteReader reader,
			boolean hasZ, boolean hasM) throws IOException {
		return readMultiLineString(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Multi Line String
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return multi line string
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static MultiLineString readMultiLineString(ByteReader reader,
			GeometryFilter filter, boolean hasZ, boolean hasM)
			throws IOException {

		MultiLineString multiLineString = new MultiLineString(hasZ, hasM);

		int numLineStrings = reader.readInt();

		for (int i = 0; i < numLineStrings; i++) {
			LineString lineString = readGeometry(reader, filter,
					GeometryType.MULTILINESTRING, LineString.class);
			if (lineString != null) {
				multiLineString.addLineString(lineString);
			}
		}

		return multiLineString;
	}

	/**
	 * Read a Multi Polygon
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return multi polygon
	 * @throws IOException
	 *             upon failure to read
	 */
	public static MultiPolygon readMultiPolygon(ByteReader reader, boolean hasZ,
			boolean hasM) throws IOException {
		return readMultiPolygon(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Multi Polygon
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return multi polygon
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static MultiPolygon readMultiPolygon(ByteReader reader,
			GeometryFilter filter, boolean hasZ, boolean hasM)
			throws IOException {

		MultiPolygon multiPolygon = new MultiPolygon(hasZ, hasM);

		int numPolygons = reader.readInt();

		for (int i = 0; i < numPolygons; i++) {
			Polygon polygon = readGeometry(reader, filter,
					GeometryType.MULTIPOLYGON, Polygon.class);
			if (polygon != null) {
				multiPolygon.addPolygon(polygon);
			}
		}

		return multiPolygon;
	}

	/**
	 * Read a Geometry Collection
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return geometry collection
	 * @throws IOException
	 *             upon failure to read
	 */
	public static GeometryCollection<Geometry> readGeometryCollection(
			ByteReader reader, boolean hasZ, boolean hasM) throws IOException {
		return readGeometryCollection(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Geometry Collection
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return geometry collection
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static GeometryCollection<Geometry> readGeometryCollection(
			ByteReader reader, GeometryFilter filter, boolean hasZ,
			boolean hasM) throws IOException {

		GeometryCollection<Geometry> geometryCollection = new GeometryCollection<Geometry>(
				hasZ, hasM);

		int numGeometries = reader.readInt();

		for (int i = 0; i < numGeometries; i++) {
			Geometry geometry = readGeometry(reader, filter,
					GeometryType.GEOMETRYCOLLECTION, Geometry.class);
			if (geometry != null) {
				geometryCollection.addGeometry(geometry);
			}
		}

		return geometryCollection;
	}

	/**
	 * Read a Circular String
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return circular string
	 * @throws IOException
	 *             upon failure to read
	 */
	public static CircularString readCircularString(ByteReader reader,
			boolean hasZ, boolean hasM) throws IOException {
		return readCircularString(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Circular String
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return circular string
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static CircularString readCircularString(ByteReader reader,
			GeometryFilter filter, boolean hasZ, boolean hasM)
			throws IOException {

		CircularString circularString = new CircularString(hasZ, hasM);

		int numPoints = reader.readInt();

		for (int i = 0; i < numPoints; i++) {
			Point point = readPoint(reader, hasZ, hasM);
			if (filter(filter, GeometryType.CIRCULARSTRING, point)) {
				circularString.addPoint(point);
			}
		}

		return circularString;
	}

	/**
	 * Read a Compound Curve
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return compound curve
	 * @throws IOException
	 *             upon failure to read
	 */
	public static CompoundCurve readCompoundCurve(ByteReader reader,
			boolean hasZ, boolean hasM) throws IOException {
		return readCompoundCurve(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Compound Curve
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return compound curve
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static CompoundCurve readCompoundCurve(ByteReader reader,
			GeometryFilter filter, boolean hasZ, boolean hasM)
			throws IOException {

		CompoundCurve compoundCurve = new CompoundCurve(hasZ, hasM);

		int numLineStrings = reader.readInt();

		for (int i = 0; i < numLineStrings; i++) {
			LineString lineString = readGeometry(reader, filter,
					GeometryType.COMPOUNDCURVE, LineString.class);
			if (lineString != null) {
				compoundCurve.addLineString(lineString);
			}
		}

		return compoundCurve;
	}

	/**
	 * Read a Curve Polygon
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return curve polygon
	 * @throws IOException
	 *             upon failure to read
	 */
	public static CurvePolygon<Curve> readCurvePolygon(ByteReader reader,
			boolean hasZ, boolean hasM) throws IOException {
		return readCurvePolygon(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Curve Polygon
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return curve polygon
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static CurvePolygon<Curve> readCurvePolygon(ByteReader reader,
			GeometryFilter filter, boolean hasZ, boolean hasM)
			throws IOException {

		CurvePolygon<Curve> curvePolygon = new CurvePolygon<Curve>(hasZ, hasM);

		int numRings = reader.readInt();

		for (int i = 0; i < numRings; i++) {
			Curve ring = readGeometry(reader, filter, GeometryType.CURVEPOLYGON,
					Curve.class);
			if (ring != null) {
				curvePolygon.addRing(ring);
			}
		}

		return curvePolygon;
	}

	/**
	 * Read a Polyhedral Surface
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return polyhedral surface
	 * @throws IOException
	 *             upon failure to read
	 */
	public static PolyhedralSurface readPolyhedralSurface(ByteReader reader,
			boolean hasZ, boolean hasM) throws IOException {
		return readPolyhedralSurface(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Polyhedral Surface
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return polyhedral surface
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static PolyhedralSurface readPolyhedralSurface(ByteReader reader,
			GeometryFilter filter, boolean hasZ, boolean hasM)
			throws IOException {

		PolyhedralSurface polyhedralSurface = new PolyhedralSurface(hasZ, hasM);

		int numPolygons = reader.readInt();

		for (int i = 0; i < numPolygons; i++) {
			Polygon polygon = readGeometry(reader, filter,
					GeometryType.POLYHEDRALSURFACE, Polygon.class);
			if (polygon != null) {
				polyhedralSurface.addPolygon(polygon);
			}
		}

		return polyhedralSurface;
	}

	/**
	 * Read a TIN
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return TIN
	 * @throws IOException
	 *             upon failure to read
	 */
	public static TIN readTIN(ByteReader reader, boolean hasZ, boolean hasM)
			throws IOException {
		return readTIN(reader, null, hasZ, hasM);
	}

	/**
	 * Read a TIN
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return TIN
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static TIN readTIN(ByteReader reader, GeometryFilter filter,
			boolean hasZ, boolean hasM) throws IOException {

		TIN tin = new TIN(hasZ, hasM);

		int numPolygons = reader.readInt();

		for (int i = 0; i < numPolygons; i++) {
			Polygon polygon = readGeometry(reader, filter, GeometryType.TIN,
					Polygon.class);
			if (polygon != null) {
				tin.addPolygon(polygon);
			}
		}

		return tin;
	}

	/**
	 * Read a Triangle
	 * 
	 * @param reader
	 *            byte reader
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return triangle
	 * @throws IOException
	 *             upon failure to read
	 */
	public static Triangle readTriangle(ByteReader reader, boolean hasZ,
			boolean hasM) throws IOException {
		return readTriangle(reader, null, hasZ, hasM);
	}

	/**
	 * Read a Triangle
	 * 
	 * @param reader
	 *            byte reader
	 * @param filter
	 *            geometry filter
	 * @param hasZ
	 *            has z flag
	 * @param hasM
	 *            has m flag
	 * @return triangle
	 * @throws IOException
	 *             upon failure to read
	 * @since 2.0.3
	 */
	public static Triangle readTriangle(ByteReader reader,
			GeometryFilter filter, boolean hasZ, boolean hasM)
			throws IOException {

		Triangle triangle = new Triangle(hasZ, hasM);

		int numRings = reader.readInt();

		for (int i = 0; i < numRings; i++) {
			LineString ring = readLineString(reader, filter, hasZ, hasM);
			if (filter(filter, GeometryType.TRIANGLE, ring)) {
				triangle.addRing(ring);
			}
		}

		return triangle;
	}

	/**
	 * Filter the geometry
	 * 
	 * @param filter
	 *            geometry filter or null
	 * @param containingType
	 *            containing geometry type
	 * @param geometry
	 *            geometry or null
	 * @return true if passes filter
	 */
	private static boolean filter(GeometryFilter filter,
			GeometryType containingType, Geometry geometry) {
		return filter == null || geometry == null
				|| filter.filter(containingType, geometry);
	}

}
