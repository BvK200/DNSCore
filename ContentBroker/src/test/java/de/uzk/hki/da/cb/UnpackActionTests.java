/*
  DA-NRW Software Suite | ContentBroker
  Copyright (C) 2013 Historisch-Kulturwissenschaftliche Informationsverarbeitung
  Universität zu Köln
  Copyright (C) 2014 LVRInfoKom
  Landschaftsverband Rheinland

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.uzk.hki.da.cb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.uzk.hki.da.core.IngestGate;
import de.uzk.hki.da.core.UserException;
import de.uzk.hki.da.model.Job;
import de.uzk.hki.da.model.Object;
import de.uzk.hki.da.utils.Files;
import de.uzk.hki.da.utils.Path;
import de.uzk.hki.da.utils.RelativePath;
import de.uzk.hki.da.utils.TESTHelper;


/**
 * The Class UnpackActionBagitAndDeltaTests.
 */
public class UnpackActionTests {

	private static final String INGEST = "ingest";
	private static final String IDENTIFIER = "identifier";
	private static final String CONF = "conf";
	private static final String BAGIT_PACKAGE = "bagitPackage.tgz";
	private static final String DUPLICATE_DOCUMENTS = "duplicateDocuments.tgz";
	private static final String WHEN_DUPLICATES = "whenDuplicatesAreNotDuplicates.tgz";
	private static final String SIDECAR_FILES = "sidecarFiles.tgz";

	private Path workAreaRootPath = new RelativePath("src/test/resources/cb/UnpackActionTests/");
	private Path ingestPath = Path.make(workAreaRootPath,"/ingest/TEST/");
	private Path csnPath = Path.make(workAreaRootPath,"/work/TEST/");

	private IngestGate gate = new IngestGate();

	private UnpackAction action = new UnpackAction();
	private Object o;

	/**
	 * Sets the up.
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@Before
	public void setUp() throws IOException{

		new File(CONF).mkdir();
		FileUtils.copyFileToDirectory(Files.PREMIS_XSD, new File(CONF));
		FileUtils.copyFileToDirectory(Files.XLINK_XSD, new File(CONF));
		
		o = TESTHelper.setUpObject(IDENTIFIER, new RelativePath(workAreaRootPath), new RelativePath(workAreaRootPath,INGEST));
		action.setLocalNode(o.getTransientNodeRef());
		
		gate.setWorkAreaRootPath(workAreaRootPath.toString());
		gate.setFreeDiskSpacePercent(5);
		gate.setFileSizeFactor(3);

		action.setJob(new Job());
		action.setObject(o);
		action.setIngestGate(gate);
	}

	/**
	 * Tear down.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@After
	public void tearDown() throws IOException{
		FileUtils.deleteQuietly(new File(CONF));
		
		FileUtils.deleteDirectory(Path.makeFile(csnPath,IDENTIFIER));
		if (Path.makeFile(ingestPath,BAGIT_PACKAGE).exists()) Path.makeFile(ingestPath,BAGIT_PACKAGE).delete();
		if (Path.makeFile(csnPath,BAGIT_PACKAGE).exists()) Path.makeFile(csnPath,BAGIT_PACKAGE).delete();
	}

	/**
	 * Test unpack std package.
	 * @throws IOException 
	 */
	@Test
	public void testUnpackStdPackage() throws IOException{
		FileUtils.copyFile(Path.makeFile(ingestPath,BAGIT_PACKAGE+"_"),Path.makeFile(ingestPath,BAGIT_PACKAGE));
		o.getPackages().get(0).setContainerName(BAGIT_PACKAGE);
		
		action.implementation();

		assertTrue(new File(o.getPath()+"/data").exists());
		assertTrue(new File(o.getPath()+"/bagit.txt").exists());
		assertTrue(new File(o.getPath()+"/manifest-md5.txt").exists());
		assertTrue(new File(o.getPath()+"/bag-info.txt").exists());
		assertTrue(new File(o.getPath()+"/tagmanifest-md5.txt").exists());
		assertTrue(new File(o.getPath()+"/data/140849.tif").exists());
		assertTrue(new File(o.getPath()+"/data/premis.xml").exists());
	}
	
	@Test
	public void testRejectPackageWithDuplicateDocumentNames() throws IOException{
		FileUtils.copyFile(Path.makeFile(ingestPath,DUPLICATE_DOCUMENTS+"_"),Path.makeFile(ingestPath,DUPLICATE_DOCUMENTS));
		o.getPackages().get(0).setContainerName(DUPLICATE_DOCUMENTS);
		
		try{
			action.implementation();
			fail();
		}
		catch(UserException e){
			System.out.println(e.getMessage());
			if (!e.getMessage().endsWith("2")) fail();
		}
	}
	
	@Test
	public void testWhenDuplicatesAreNotDuplicates() throws IOException{
		FileUtils.copyFile(Path.makeFile(ingestPath,WHEN_DUPLICATES+"_"),Path.makeFile(ingestPath,WHEN_DUPLICATES));
		o.getPackages().get(0).setContainerName(WHEN_DUPLICATES);
		
		try{
			action.implementation();
		}
		catch(UserException e){
			System.out.println(e.getMessage());
			fail();
		}
	}
	
	@Test
	public void acceptSidecarFiles() throws IOException{
		
		FileUtils.copyFile(Path.makeFile(ingestPath,SIDECAR_FILES+"_"),Path.makeFile(ingestPath,SIDECAR_FILES));
		o.getPackages().get(0).setContainerName(SIDECAR_FILES);
	
		action.setSidecarExtensions("xmp");
		try{
			action.implementation();
		}catch(UserException e){
			fail(e.getMessage());
		}
	}
	
	
	
	
	/**
	 * Test delete source package.
	 * @throws IOException 
	 */
	@Test
	public void testDeleteSourcePackage() throws IOException{
		FileUtils.copyFile(Path.makeFile(ingestPath,BAGIT_PACKAGE+"_"),Path.makeFile(ingestPath,BAGIT_PACKAGE));
		o.getPackages().get(0).setContainerName(BAGIT_PACKAGE);
		
		action.implementation();
		assertFalse(Path.makeFile(csnPath,BAGIT_PACKAGE).exists());
	} 

	
	

	@Test
	public void throwExceptionWhenPackageDoesntExist() throws IOException{

		o.getPackages().get(0).setContainerName("notExistent.tgz");
		
		try{		
			action.implementation();
			fail();
		}
		catch(Exception e){
			action.rollback();
			System.out.println("Exception caught as expected: "+e);	
		}		
	}
}