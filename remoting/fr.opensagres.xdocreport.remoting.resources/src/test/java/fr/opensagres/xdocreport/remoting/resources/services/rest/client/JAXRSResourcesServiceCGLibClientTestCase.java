package fr.opensagres.xdocreport.remoting.resources.services.rest.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;

import javax.ws.rs.core.Application;

import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import fr.opensagres.xdocreport.core.io.IOUtils;
import fr.opensagres.xdocreport.remoting.resources.Data;
import fr.opensagres.xdocreport.remoting.resources.domain.BinaryData;
import fr.opensagres.xdocreport.remoting.resources.domain.Resource;
import fr.opensagres.xdocreport.remoting.resources.services.FileUtils;
import fr.opensagres.xdocreport.remoting.resources.services.ResourceComparator;
import fr.opensagres.xdocreport.remoting.resources.services.ResourcesService;
import fr.opensagres.xdocreport.remoting.resources.services.rest.JAXRSResourcesService;
import fr.opensagres.xdocreport.remoting.resources.services.rest.MockJAXRSResourcesApplication;

public class JAXRSResourcesServiceCGLibClientTestCase
{

    private static final int PORT = 9997;

    private static Server server;

    private static final String BASE_ADDRESS = "http://localhost:" + PORT;

    public static File srcFolder = new File( "src/test/resources/fr/opensagres/xdocreport/remoting/resources" );

    public static File tempFolder = new File( "target" );

    public static File resourcesFolder = new File( tempFolder, "resources" );

    @BeforeClass
    public static void startServer()
        throws Exception
    {

        // 1) Copy resources in the target folder.
        if ( resourcesFolder.exists() )
        {
            resourcesFolder.delete();
        }
        FileUtils.copyDirectory( srcFolder, resourcesFolder );

        // 2) Start Jetty Server

        ServletHolder servlet = new ServletHolder( CXFNonSpringJaxrsServlet.class );
        servlet.setInitParameter( "javax.ws.rs.Application", MockJAXRSResourcesApplication.class.getName() );

        servlet.setInitParameter( "timeout", "60000" );
        server = new Server( PORT );

        ServletContextHandler context = new ServletContextHandler( server, "/", ServletContextHandler.SESSIONS );

        context.addServlet( servlet, "/*" );
        server.start();

    }

    @Test
    public void name()
    {
        ResourcesService client = JAXRSClientFactory.create( BASE_ADDRESS, JAXRSResourcesService.class );
        String name = client.getName();
        Assert.assertEquals( "Test-RepositoryService", name );
    }

    @Test
    public void root()
        throws IOException
    {

        ResourcesService client = JAXRSClientFactory.create( BASE_ADDRESS, JAXRSResourcesService.class );
        Resource root = client.getRoot();

        // Document coming from the folder src/test/resources/fr/opensagres/xdocreport/resources/repository
        // See class MockRepositoryService
        Assert.assertNotNull( root );
        Assert.assertEquals( "resources", root.getName() );
        Assert.assertTrue( root.getChildren().size() >= 4 );

        // Sort the list of Resource because File.listFiles() doeesn' given the same order
        // between different OS.
        Collections.sort( root.getChildren(), ResourceComparator.INSTANCE );

        Assert.assertEquals( "Custom", root.getChildren().get( 0 ).getName() );
        Assert.assertEquals( Resource.FOLDER_TYPE, root.getChildren().get( 0 ).getType() );
        Assert.assertEquals( "Opensagres", root.getChildren().get( 1 ).getName() );
        Assert.assertEquals( Resource.FOLDER_TYPE, root.getChildren().get( 1 ).getType() );
        Assert.assertEquals( "Simple.docx", root.getChildren().get( 2 ).getName() );
        Assert.assertEquals( "Simple.odt", root.getChildren().get( 3 ).getName() );

    }

    @Test
    public void downloadARootFile()
        throws FileNotFoundException, IOException
    {
        String resourceId = "Simple.docx";
        ResourcesService client = JAXRSClientFactory.create( BASE_ADDRESS, JAXRSResourcesService.class );
        BinaryData document = client.download( resourceId );
        Assert.assertNotNull( document );
        Assert.assertNotNull( document.getContent() );
        createFile( document.getContent(), resourceId );
    }

    @Test
    public void downloadAFileInFolder()
        throws FileNotFoundException, IOException
    {
        String resourceId = "Custom%2FCustomSimple.docx";
        ResourcesService client = JAXRSClientFactory.create( BASE_ADDRESS, JAXRSResourcesService.class );
        BinaryData document = client.download( resourceId );
        Assert.assertNotNull( document );
        Assert.assertNotNull( document.getContent() );
        createFile( document.getContent(), resourceId );
    }

    private void createFile( byte[] flux, String filename )
        throws FileNotFoundException, IOException
    {
        File aFile = new File( tempFolder, this.getClass().getSimpleName() + "_" + filename );
        FileOutputStream fos = new FileOutputStream( aFile );
        fos.write( flux );
        fos.close();
    }

    @Test
    public void uploadARootFile()
        throws FileNotFoundException, IOException
    {
        String resourceId = "ZzzNewSimple_" + this.getClass().getSimpleName() + ".docx";
        ResourcesService client = JAXRSClientFactory.create( BASE_ADDRESS, JAXRSResourcesService.class );
        byte[] document = IOUtils.toByteArray( Data.class.getResourceAsStream( "Simple.docx" ) );

        BinaryData dataIn = new BinaryData();
        dataIn.setResourceId( resourceId );
        dataIn.setContent( document );

        client.upload( dataIn );

        // Test if file was uploaded in the target/resources folder
        Assert.assertTrue( new File( resourcesFolder, resourceId ).exists() );

        // Test if download with the resourceId returns a non null binary data.
        BinaryData downloadedDocument = client.download( resourceId );
        Assert.assertNotNull( downloadedDocument );
        Assert.assertNotNull( downloadedDocument.getContent() );
    }

    @Test
    public void uploadAFileInFolder()
        throws FileNotFoundException, IOException
    {
        String resourceId = "ZzzCustom____NewCustomSimple_" + this.getClass().getSimpleName() + ".docx";
        ResourcesService client = JAXRSClientFactory.create( BASE_ADDRESS, JAXRSResourcesService.class );
        byte[] document = IOUtils.toByteArray( Data.class.getResourceAsStream( "Simple.docx" ) );

        BinaryData dataIn = new BinaryData();
        dataIn.setResourceId( resourceId );
        dataIn.setContent( document );

        client.upload( dataIn );

        // Test if file was uploaded in the target/resources folder
        Assert.assertTrue( new File( resourcesFolder, "ZzzCustom/NewCustomSimple_" + this.getClass().getSimpleName()
            + ".docx" ).exists() );

        // Test if download with the resourceId returns a non null binary data.
        BinaryData downloadedDocument = client.download( resourceId );
        Assert.assertNotNull( downloadedDocument );
        Assert.assertNotNull( downloadedDocument.getContent() );
    }

    @AfterClass
    public static void stopServer()
        throws Exception
    {
        server.stop();
    }
}
