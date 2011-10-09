/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gis.spatial.pipes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.NoSuchElementException;

import org.geotools.data.neo4j.StyledImageExporter;
import org.geotools.filter.text.cql2.CQLException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.collections.rtree.filter.SearchAll;
import org.neo4j.collections.rtree.filter.SearchFilter;
import org.neo4j.cypher.javacompat.CypherParser;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.examples.AbstractJavaDocTestbase;
import org.neo4j.gis.spatial.EditableLayerImpl;
import org.neo4j.gis.spatial.Layer;
import org.neo4j.gis.spatial.SpatialDatabaseService;
import org.neo4j.gis.spatial.osm.OSMImporter;
import org.neo4j.gis.spatial.pipes.osm.OSMGeoPipeline;
import org.neo4j.kernel.impl.annotations.Documented;
import org.neo4j.test.ImpermanentGraphDatabase;
import org.neo4j.visualization.asciidoc.AsciidocHelper;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.util.AffineTransformation;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

public class GeoPipesTest extends AbstractJavaDocTestbase
{

    private static Layer osmLayer;
    private static EditableLayerImpl boxesLayer;
    private static EditableLayerImpl concaveLayer;
    private static EditableLayerImpl intersectionLayer;
    private static EditableLayerImpl equalLayer;
    private static StyledImageExporter exporter;

    private static OSMGeoPipeline startPipeline( Layer layer )
    {
        return startPipeline( layer, new SearchAll() );
    }

    private static OSMGeoPipeline startPipeline( Layer layer,
            SearchFilter filter )
    {
        return OSMGeoPipeline.start( layer, filter );
    }

    @Test
    public void find_all()
    {
        int count = 0;
        for ( GeoPipeFlow flow : startPipeline( osmLayer ).createWellKnownText() )
        {
            count++;

            assertEquals( 1, flow.getProperties().size() );
            String wkt = (String) flow.getProperties().get( "WellKnownText" );
            assertTrue( wkt.indexOf( "LINESTRING" ) == 0 );
        }

        assertEquals( 2, count );
    }

    @Test
    public void filter_by_osm_attribute()
    {
        GeoPipeline pipeline = startPipeline( osmLayer ).osmAttributeFilter(
                "name", "Storgatan" ).copyDatabaseRecordProperties();

        GeoPipeFlow flow = pipeline.next();
        assertFalse( pipeline.hasNext() );

        assertEquals( "Storgatan", flow.getProperties().get( "name" ) );
    }

    @Test
    public void filter_by_property()
    {
        GeoPipeline pipeline = startPipeline( osmLayer ).copyDatabaseRecordProperties().propertyFilter(
                "name", "Storgatan" );

        GeoPipeFlow flow = pipeline.next();
        assertFalse( pipeline.hasNext() );

        assertEquals( "Storgatan", flow.getProperties().get( "name" ) );
    }

    @Test
    public void filter_by_window_intersection()
    {
        assertEquals(
                1,
                startPipeline( osmLayer ).windowIntersectionFilter( 10, 40, 20,
                        56.0583531 ).count() );
    }

    @Test
    public void filter_by_cql_using_bbox() throws CQLException
    {
        assertEquals(
                1,
                startPipeline( osmLayer ).cqlFilter(
                        "BBOX(the_geom, 10, 40, 20, 56.0583531)" ).count() );
    }

    @Test
    public void filter_by_cql_using_property() throws CQLException
    {
        GeoPipeline pipeline = startPipeline( osmLayer ).cqlFilter(
                "name = 'Storgatan'" ).copyDatabaseRecordProperties();

        GeoPipeFlow flow = pipeline.next();
        assertFalse( pipeline.hasNext() );

        assertEquals( "Storgatan", flow.getProperties().get( "name" ) );
    }

    @Test
    public void traslate_geometries()
    {
        GeoPipeline original = startPipeline( osmLayer ).copyDatabaseRecordProperties().sort(
                "name" );

        GeoPipeline translated = startPipeline( osmLayer ).applyAffineTransform(
                AffineTransformation.translationInstance( 10, 25 ) ).copyDatabaseRecordProperties().sort(
                "name" );

        for ( int k = 0; k < 2; k++ )
        {
            Coordinate[] coords = original.next().getGeometry().getCoordinates();
            Coordinate[] newCoords = translated.next().getGeometry().getCoordinates();
            assertEquals( coords.length, newCoords.length );
            for ( int i = 0; i < coords.length; i++ )
            {
                assertEquals( coords[i].x + 10, newCoords[i].x, 0 );
                assertEquals( coords[i].y + 25, newCoords[i].y, 0 );
            }
        }
    }

    @Test
    public void calculate_area()
    {
        GeoPipeline pipeline = startPipeline( boxesLayer ).calculateArea().sort(
                "Area" );

        assertEquals( (Double) pipeline.next().getProperties().get( "Area" ),
                1.0, 0 );
        assertEquals( (Double) pipeline.next().getProperties().get( "Area" ),
                8.0, 0 );
    }

    @Test
    public void calculate_length()
    {
        GeoPipeline pipeline = startPipeline( boxesLayer ).calculateLength().sort(
                "Length" );

        assertEquals( (Double) pipeline.next().getProperties().get( "Length" ),
                4.0, 0 );
        assertEquals( (Double) pipeline.next().getProperties().get( "Length" ),
                12.0, 0 );
    }

    @Test
    public void get_boundary_length()
    {
        GeoPipeline pipeline = startPipeline( boxesLayer ).toBoundary().createWellKnownText().calculateLength().sort(
                "Length" );

        GeoPipeFlow first = pipeline.next();
        GeoPipeFlow second = pipeline.next();
        assertEquals( "LINEARRING (12 56, 12 57, 13 57, 13 56, 12 56)",
                first.getProperties().get( "WellKnownText" ) );
        assertEquals( "LINEARRING (2 3, 2 5, 6 5, 6 3, 2 3)",
                second.getProperties().get( "WellKnownText" ) );
        assertEquals( (Double) first.getProperties().get( "Length" ), 4.0, 0 );
        assertEquals( (Double) second.getProperties().get( "Length" ), 12.0, 0 );
    }

    @Test
    public void get_buffer()
    {
        GeoPipeline pipeline = startPipeline( boxesLayer ).toBuffer( 0.1 ).createWellKnownText().calculateArea().sort(
                "Area" );

        assertTrue( ( (Double) pipeline.next().getProperties().get( "Area" ) ) > 1 );
        assertTrue( ( (Double) pipeline.next().getProperties().get( "Area" ) ) > 8 );
    }

    @Test
    public void get_centroid()
    {
        GeoPipeline pipeline = startPipeline( boxesLayer ).toCentroid().createWellKnownText().copyDatabaseRecordProperties().sort(
                "name" );

        assertEquals( "POINT (12.5 56.5)",
                pipeline.next().getProperties().get( "WellKnownText" ) );
        assertEquals( "POINT (4 4)",
                pipeline.next().getProperties().get( "WellKnownText" ) );
    }

    @Test
    public void get_convex_hull()
    {
        GeoPipeline pipeline = startPipeline( concaveLayer ).toConvexHull().createWellKnownText();

        assertEquals( "POLYGON ((0 0, 0 10, 10 10, 10 0, 0 0))",
                pipeline.next().getProperties().get( "WellKnownText" ) );

        print( pipeline );
    }

    @Test
    public void densify()
    {
        GeoPipeline pipeline = startPipeline( concaveLayer ).toConvexHull().densify(
                10 ).createWellKnownText();

        assertEquals(
                "POLYGON ((0 0, 0 5, 0 10, 5 10, 10 10, 10 5, 10 0, 5 0, 0 0))",
                pipeline.next().getProperties().get( "WellKnownText" ) );
    }

    @Test
    public void json()
    {
        GeoPipeline pipeline = startPipeline( boxesLayer ).createJson().copyDatabaseRecordProperties().sort(
                "name" );

        assertEquals(
                "{\"type\":\"Polygon\",\"coordinates\":[[[12,56],[12,57],[13,57],[13,56],[12,56]]]}",
                pipeline.next().getProperties().get( "GeoJSON" ) );
        assertEquals(
                "{\"type\":\"Polygon\",\"coordinates\":[[[2,3],[2,5],[6,5],[6,3],[2,3]]]}",
                pipeline.next().getProperties().get( "GeoJSON" ) );
    }

    @Test
    public void get_max_area()
    {
        GeoPipeline pipeline = startPipeline( boxesLayer ).calculateArea().getMax(
                "Area" );

        assertEquals( (Double) pipeline.next().getProperties().get( "Area" ),
                8.0, 0 );
    }

    @Test
    public void get_min_area()
    {
        GeoPipeline pipeline = startPipeline( boxesLayer ).calculateArea().getMin(
                "Area" );

        assertEquals( (Double) pipeline.next().getProperties().get( "Area" ),
                1.0, 0 );
    }

    @Test
    public void extract_osm_points()
    {
        int count = 0;
        GeoPipeline pipeline = startPipeline( osmLayer ).extractOsmPoints().createWellKnownText();
        for ( GeoPipeFlow flow : pipeline )
        {
            count++;

            assertEquals( 1, flow.getProperties().size() );
            String wkt = (String) flow.getProperties().get( "WellKnownText" );
            assertTrue( wkt.indexOf( "POINT" ) == 0 );
        }

        assertEquals( 24, count );
    }

    @Test
    public void break_up_all_geometries_into_points_and_make_density_islands_and_get_the_outer_linear_ring_of_the_density_islands_and_buffer_the_geometry_and_count_them()
    {
        assertEquals(
                1,
                startPipeline( osmLayer ).extractOsmPoints().groupByDensityIslands(
                        0.1 ).toConvexHull().toBuffer( 10 ).count() );
        System.out.println( startPipeline( osmLayer ).extractOsmPoints().groupByDensityIslands(
                0.1 ).toConvexHull().toBuffer( 10 ).count() );
    }

    @Test
    public void extract_points()
    {
        int count = 0;
        for ( GeoPipeFlow flow : startPipeline( boxesLayer ).extractPoints().createWellKnownText() )
        {
            count++;

            assertEquals( 1, flow.getProperties().size() );
            String wkt = (String) flow.getProperties().get( "WellKnownText" );
            assertTrue( wkt.indexOf( "POINT" ) == 0 );
        }

        assertEquals( 10, count );
    }

    @Test
    public void filter_by_null_property()
    {
        assertEquals(
                2,
                startPipeline( boxesLayer ).copyDatabaseRecordProperties().propertyNullFilter(
                        "address" ).count() );
        assertEquals(
                0,
                startPipeline( boxesLayer ).copyDatabaseRecordProperties().propertyNullFilter(
                        "name" ).count() );
    }

    @Test
    public void filter_by_not_null_property()
    {
        assertEquals(
                0,
                startPipeline( boxesLayer ).copyDatabaseRecordProperties().propertyNotNullFilter(
                        "address" ).count() );
        assertEquals(
                2,
                startPipeline( boxesLayer ).copyDatabaseRecordProperties().propertyNotNullFilter(
                        "name" ).count() );
    }

    @Test
    public void compute_distance() throws ParseException
    {
        WKTReader reader = new WKTReader( boxesLayer.getGeometryFactory() );

        GeoPipeline pipeline = startPipeline( boxesLayer ).calculateDistance(
                reader.read( "POINT (0 0)" ) ).sort( "Distance" );

        assertEquals(
                Math.round( (Double) pipeline.next().getProperty( "Distance" ) ),
                4 );
        assertEquals(
                Math.round( (Double) pipeline.next().getProperty( "Distance" ) ),
                57 );
    }

    @Test
    public void union_all()
    {
        GeoPipeline pipeline = startPipeline( intersectionLayer ).unionAll().createWellKnownText();

        assertEquals(
                "POLYGON ((0 0, 0 5, 2 5, 2 6, 4 6, 4 10, 10 10, 10 4, 6 4, 6 2, 5 2, 5 0, 0 0))",
                pipeline.next().getProperty( "WellKnownText" ) );

        try
        {
            pipeline.next();
            fail();
        }
        catch ( NoSuchElementException e )
        {
        }
    }

    @Test
    public void intersect_all()
    {
        GeoPipeline pipeline = startPipeline( intersectionLayer ).intersectAll().createWellKnownText();

        assertEquals( "POLYGON ((4 5, 5 5, 5 4, 4 4, 4 5))",
                pipeline.next().getProperty( "WellKnownText" ) );

        try
        {
            pipeline.next();
            fail();
        }
        catch ( NoSuchElementException e )
        {
        }
    }

    /**
     * The window intersection pipe can take any 
     * feature collection
     * 
     * The layer
     * 
     * is resutling in a graph like
     * 
     * @@graph
     * 
     * intersected with an envelope like
     * 
     * @@s_intersecting_windows
     * 
     * will result in
     * 
     * @@intersecting_windows
     * 
     * 
     */
    @Documented
    @Test
    public void intersecting_windows()
    {

        // START SNIPPET: s_intersecting_windows
        GeoPipeline pipeline = startPipeline( intersectionLayer ).windowIntersectionFilter(
                new Envelope( -10, 50, -10, 50 ) );
        // END SNIPPET: s_intersecting_windows
        addImageSnippet( pipeline, getTitle() );
        

    }

    private String getTitle()
    {
        // TODO Auto-generated method stub
        return gen.get().getTitle().replace( " ", "_" );
    }

    @Test
    public void test_equality() throws Exception
    {
        WKTReader reader = new WKTReader( equalLayer.getGeometryFactory() );
        Geometry geom = reader.read( "POLYGON ((0 0, 0 5, 5 5, 5 0, 0 0))" );

        GeoPipeline pipeline = GeoPipeline.startEqualExactSearch( equalLayer,
                geom, 0 ).copyDatabaseRecordProperties();
        assertEquals( "equal", pipeline.next().getProperty( "name" ) );
        assertFalse( pipeline.hasNext() );

        pipeline = GeoPipeline.startEqualExactSearch( equalLayer, geom, 0.1 ).copyDatabaseRecordProperties().sort(
                "id" );
        assertEquals( "equal", pipeline.next().getProperty( "name" ) );
        assertEquals( "tolerance", pipeline.next().getProperty( "name" ) );
        assertFalse( pipeline.hasNext() );

        pipeline = GeoPipeline.startIntersectWindowSearch( equalLayer,
                geom.getEnvelopeInternal() ).equalNormFilter( geom, 0.1 ).copyDatabaseRecordProperties().sort(
                "id" );
        assertEquals( "equal", pipeline.next().getProperty( "name" ) );
        assertEquals( "tolerance", pipeline.next().getProperty( "name" ) );
        assertEquals( "different order", pipeline.next().getProperty( "name" ) );
        assertFalse( pipeline.hasNext() );

        pipeline = GeoPipeline.startIntersectWindowSearch( equalLayer,
                geom.getEnvelopeInternal() ).equalTopoFilter( geom ).copyDatabaseRecordProperties().sort(
                "id" );
        assertEquals( "equal", pipeline.next().getProperty( "name" ) );
        assertEquals( "different order", pipeline.next().getProperty( "name" ) );
        assertEquals( "topo equal", pipeline.next().getProperty( "name" ) );
        assertFalse( pipeline.hasNext() );
    }

    private void addImageSnippet(
            GeoPipeline pipeline,
            String imgName )
    {
        gen.get().addSnippet( imgName, "\nimage::" + imgName + ".png[]\n" );
        try
        {
            
            exporter.saveImage( pipeline.toFeatureCollection(),
                    StyledImageExporter.createDefaultStyle(), new File(
                            imgName + ".png" ) );
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private static void load() throws Exception
    {
        SpatialDatabaseService spatialService = new SpatialDatabaseService( db );

        loadTestOsmData( "two-street.osm", 100 );
        osmLayer = spatialService.getLayer( "two-street.osm" );
        boxesLayer = (EditableLayerImpl) spatialService.getOrCreateEditableLayer( "boxes" );
        boxesLayer.setExtraPropertyNames( new String[] { "name" } );
        WKTReader reader = new WKTReader( boxesLayer.getGeometryFactory() );
        boxesLayer.add(
                reader.read( "POLYGON ((12 56, 12 57, 13 57, 13 56, 12 56))" ),
                new String[] { "name" }, new Object[] { "A" } );
        boxesLayer.add( reader.read( "POLYGON ((2 3, 2 5, 6 5, 6 3, 2 3))" ),
                new String[] { "name" }, new Object[] { "B" } );

        concaveLayer = (EditableLayerImpl) spatialService.getOrCreateEditableLayer( "concave" );
        reader = new WKTReader( concaveLayer.getGeometryFactory() );
        concaveLayer.add( reader.read( "POLYGON ((0 0, 2 5, 0 10, 10 10, 10 0, 0 0))" ) );

        intersectionLayer = (EditableLayerImpl) spatialService.getOrCreateEditableLayer( "intersection" );
        intersectionLayer.getCoordinateReferenceSystem();
        reader = new WKTReader( intersectionLayer.getGeometryFactory() );
        intersectionLayer.add( reader.read( "POLYGON ((0 0, 0 5, 5 5, 5 0, 0 0))" ) );
        intersectionLayer.add( reader.read( "POLYGON ((4 4, 4 10, 10 10, 10 4, 4 4))" ) );
        intersectionLayer.add( reader.read( "POLYGON ((2 2, 2 6, 6 6, 6 2, 2 2))" ) );

        equalLayer = (EditableLayerImpl) spatialService.getOrCreateEditableLayer( "equal" );
        equalLayer.setExtraPropertyNames( new String[] { "id", "name" } );
        reader = new WKTReader( intersectionLayer.getGeometryFactory() );
        equalLayer.add( reader.read( "POLYGON ((0 0, 0 5, 5 5, 5 0, 0 0))" ),
                new String[] { "id", "name" }, new Object[] { 1, "equal" } );
        equalLayer.add( reader.read( "POLYGON ((0 0, 0.1 5, 5 5, 5 0, 0 0))" ),
                new String[] { "id", "name" }, new Object[] { 2, "tolerance" } );
        equalLayer.add( reader.read( "POLYGON ((0 5, 5 5, 5 0, 0 0, 0 5))" ),
                new String[] { "id", "name" }, new Object[] { 3,
                        "different order" } );
        equalLayer.add(
                reader.read( "POLYGON ((0 0, 0 2, 0 4, 0 5, 5 5, 5 3, 5 2, 5 0, 0 0))" ),
                new String[] { "id", "name" }, new Object[] { 4, "topo equal" } );

    }

    private static void loadTestOsmData( String layerName, int commitInterval )
            throws Exception
    {
        String osmPath = "./" + layerName;
        System.out.println( "\n=== Loading layer " + layerName + " from "
                            + osmPath + " ===" );
        OSMImporter importer = new OSMImporter( layerName );
        importer.setCharset( Charset.forName( "UTF-8" ) );
        importer.importFile( db, osmPath );
        importer.reIndex( db, commitInterval );
    }

    @Before
    public void setUp()
    {
        gen.get().setGraph( db );
        parser = new CypherParser();
        engine = new ExecutionEngine( db );
        try
        {
            exporter.saveImage( startPipeline( intersectionLayer ).toFeatureCollection(),
                    StyledImageExporter.createDefaultStyle(), new File(
                            "intersectionLayer.png" ) );
        }
        catch ( IOException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @After
    public void doc()
    {
       String imgName = "graph"+getTitle();
       gen.get().addSnippet( "graph", AsciidocHelper.createGraphViz(
        imgName , graphdb(), imgName ) );
       gen.get().addSourceSnippets( GeoPipesTest.class, "s_"+getTitle() );
       gen.get().document( "target/docs", "examples" );
    }

    @BeforeClass
    public static void init()
    {
        db = new ImpermanentGraphDatabase( "target/"
                                           + System.currentTimeMillis() );
        db.cleanContent( true );
        try
        {
            load();
        }
        catch ( Exception e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        exporter = new StyledImageExporter( db );
        exporter.setExportDir( "target/docs/images/" );

    }

    private void print( GeoPipeline pipeline )
    {
        while ( pipeline.hasNext() )
        {
            print( pipeline.next() );
        }
    }

    private GeoPipeFlow print( GeoPipeFlow pipeFlow )
    {
        System.out.println( "GeoPipeFlow:" );
        for ( String key : pipeFlow.getProperties().keySet() )
        {
            System.out.println( key + "=" + pipeFlow.getProperties().get( key ) );
        }
        System.out.println( "-" );
        return pipeFlow;
    }
}
