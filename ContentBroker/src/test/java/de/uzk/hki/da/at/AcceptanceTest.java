/*
  DA-NRW Software Suite | ContentBroker
  Copyright (C) 2013 Historisch-Kulturwissenschaftliche Informationsverarbeitung
  Universität zu Köln

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
package de.uzk.hki.da.at;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.hibernate.Session;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import de.uzk.hki.da.grid.DistributedConversionAdapter;
import de.uzk.hki.da.grid.GridFacade;
import de.uzk.hki.da.model.Node;
import de.uzk.hki.da.model.PreservationSystem;
import de.uzk.hki.da.model.StoragePolicy;
import de.uzk.hki.da.model.User;
import de.uzk.hki.da.model.WorkArea;
import de.uzk.hki.da.repository.MetadataIndex;
import de.uzk.hki.da.repository.RepositoryFacade;
import de.uzk.hki.da.service.HibernateUtil;
import de.uzk.hki.da.test.TESTHelper;
import de.uzk.hki.da.utils.C;
import de.uzk.hki.da.utils.Path;
import de.uzk.hki.da.utils.PropertiesUtils;

/**
 * @author Daniel M. de Oliveira
 */
public class AcceptanceTest {
	
	private static final String CONF_BEANS_XML = "conf/beans.xml";
	private static final String CI_WORKING_RESOURCE = "ciWorkingResource";
	private static final String CI_ARCHIVE_RESOURCE = "ciArchiveResource";
	private static final String BEAN_NAME_FAKE_REPOSITORY_FACADE = "fakeRepositoryFacade";
	private static final String BEAN_NAME_FAKE_METADATA_INDEX = "fakeMetadataIndex";
	protected static Node localNode;
	protected static GridFacade gridFacade;
	protected static RepositoryFacade repositoryFacade;
	protected static MetadataIndex metadataIndex;
	protected static DistributedConversionAdapter distributedConversionAdapter;
	protected static User testContractor;
	protected static PreservationSystem preservationSystem;
	protected static AcceptanceTestHelper ath = null;
	protected static StoragePolicy sp;
	
	/**
	 * @param gridImplBeanName bean name 
	 * @param dcaImplBeanName distributed conversion adapter beanName
	 * @return
	 */
	
	private static void instantiateGrid(Properties properties) {
		
		String gridImplBeanName = properties.getProperty("cb.implementation.grid");
		String dcaImplBeanName  = properties.getProperty("cb.implementation.distributedConversion");
		
		if (gridImplBeanName==null) gridImplBeanName="fakeGridFacade";
		if (dcaImplBeanName==null) dcaImplBeanName="fakeDistributedConversionAdapter";
		
		AbstractApplicationContext context =
				new FileSystemXmlApplicationContext(CONF_BEANS_XML);
		
		gridFacade = (GridFacade) context.getBean(gridImplBeanName);
		distributedConversionAdapter = (DistributedConversionAdapter) context.getBean(dcaImplBeanName);
		context.close();
	}
	
	private static void instantiateNode() {
		
		AbstractApplicationContext context = 
				new FileSystemXmlApplicationContext(CONF_BEANS_XML);
		localNode = (Node) context.getBean("localNode");
		
		Session session = HibernateUtil.openSession();
		session.beginTransaction();
		session.refresh(localNode);
		session.close();
		
		context.close();
	}
	
	private static void instantiateRepository(Properties properties) {
		
		String repImplBeanName=properties.getProperty("cb.implementation.repository");
		if (repImplBeanName==null) repImplBeanName=BEAN_NAME_FAKE_REPOSITORY_FACADE;
		
		AbstractApplicationContext context =
				new FileSystemXmlApplicationContext(CONF_BEANS_XML);
		repositoryFacade = (RepositoryFacade) context.getBean(repImplBeanName);
		context.close();
	}
	
	private static void instantiateMetadataIndex(Properties properties) {
		String indexImplBeanName=properties.getProperty("cb.implementation.index");
		if (indexImplBeanName==null) indexImplBeanName=BEAN_NAME_FAKE_METADATA_INDEX;
		AbstractApplicationContext context =
				new FileSystemXmlApplicationContext(CONF_BEANS_XML);
		metadataIndex = (MetadataIndex) context.getBean(indexImplBeanName);
		context.close();
	}
	
	/**
	 * The StoragePolicy is normally configured in the app,
	 * but for Packages regarding UC such as retrieval and audit
	 * a valid StoragePolicy has to be configured.
	 * @author Jens Peters
	 */
	private static void instantiateStoragePolicy() {
		sp = new StoragePolicy();
		sp.setMinNodes(1);
		sp.setWorkingResource(CI_WORKING_RESOURCE);
		sp.setReplDestinations(CI_ARCHIVE_RESOURCE);
		sp.setAdminEmail("noreply");
		sp.setGridCacheAreaRootPath(localNode.getGridCacheAreaRootPath().toString());
		sp.setCommonStorageRescName(CI_ARCHIVE_RESOURCE);
	}
	
	
	/**
	 * Gets the contractor.
	 *
	 * @param contractorShortName the contractor short name
	 * @return null if no contractor for short name could be found
	 */
	private static User getContractor(Session session, String contractorShortName) {
	
		@SuppressWarnings("rawtypes")
		List list;	
		list = session.createQuery("from User where short_name=?1")
	
				.setParameter("1",contractorShortName).setReadOnly(true).list();
		
		if (list.isEmpty())
			return null;
	
		return (User) list.get(0);
	}
	
	
	
	@BeforeClass
	public static void setUpAcceptanceTest() throws IOException{
		
		HibernateUtil.init("conf/hibernateCentralDB.cfg.xml");
		
		instantiateNode();
		if (localNode==null) throw new IllegalStateException("localNode could not be instantiated");
	
		System.out.println("localnode: "+localNode.getName());
		
		Properties properties = PropertiesUtils.read(new File("conf/config.properties"));
		instantiateGrid(properties);
		if (gridFacade==null) throw new IllegalStateException("gridFacade could not be instantiated");
		
		instantiateRepository(properties);
		if (repositoryFacade==null) throw new IllegalStateException("repositoryFacade could not be instantiated");
		
		instantiateMetadataIndex(properties);
		if (metadataIndex==null) throw new IllegalStateException("metadataIndex could not be instantiated");
	
		Session session = HibernateUtil.openSession();
		session.beginTransaction();
		testContractor = getContractor(session, "TEST");
	
		preservationSystem = (PreservationSystem) session.get(PreservationSystem.class, 1);
		session.close();
		instantiateStoragePolicy();
		ath = new AcceptanceTestHelper(gridFacade,localNode,testContractor,sp);
		
//		new CommandLineConnector().runCmdSynchronously(new String[] {"src/main/bash/rebuildIndex.sh"});
		cleanStorage();
		clearDB();
	}

	@AfterClass
	public static void tearDownAcceptanceTest() throws IOException{
//		new CommandLineConnector().runCmdSynchronously(new String[] {"src/main/bash/rebuildIndex.sh"});
//		cleanStorage();
//		clearDB();
	}
	

	private static void cleanStorage(){
		FileUtils.deleteQuietly(Path.makeFile(localNode.getWorkAreaRootPath(),"work",C.TEST_USER_SHORT_NAME));
		FileUtils.deleteQuietly(Path.makeFile(localNode.getWorkAreaRootPath(),"repl",C.TEST_USER_SHORT_NAME));
		FileUtils.deleteQuietly(Path.makeFile(localNode.getIngestAreaRootPath(),C.TEST_USER_SHORT_NAME));
		FileUtils.deleteQuietly(Path.makeFile(localNode.getGridCacheAreaRootPath(),WorkArea.AIP,C.TEST_USER_SHORT_NAME));
		FileUtils.deleteQuietly(Path.makeFile(localNode.getWorkAreaRootPath(),"pips","institution",C.TEST_USER_SHORT_NAME));
		FileUtils.deleteQuietly(Path.makeFile(localNode.getWorkAreaRootPath(),"pips","public",C.TEST_USER_SHORT_NAME));
		FileUtils.deleteQuietly(Path.makeFile(localNode.getUserAreaRootPath(),C.TEST_USER_SHORT_NAME,"outgoing"));
		
	
		
		distributedConversionAdapter.remove("work/TEST");
		distributedConversionAdapter.remove("aip/TEST");
		distributedConversionAdapter.remove("pips/institution/TEST");
		distributedConversionAdapter.remove("pips/public/TEST");
		
		distributedConversionAdapter.create("work/TEST");
		distributedConversionAdapter.create("aip/TEST");
		distributedConversionAdapter.create("pips/institution/TEST");
		distributedConversionAdapter.create("pips/public/TEST");
		
		Path.makeFile(localNode.getUserAreaRootPath(),C.TEST_USER_SHORT_NAME,"outgoing").mkdirs();
		Path.makeFile(localNode.getGridCacheAreaRootPath(),"aip",C.TEST_USER_SHORT_NAME).mkdirs();
		Path.makeFile(localNode.getIngestAreaRootPath(),C.TEST_USER_SHORT_NAME).mkdirs();
		Path.make(localNode.getWorkAreaRootPath(),"work",C.TEST_USER_SHORT_NAME).toFile().mkdirs();
		Path.make(localNode.getWorkAreaRootPath(),"repl",C.TEST_USER_SHORT_NAME).toFile().mkdirs();
		Path.makeFile(localNode.getWorkAreaRootPath(),"pips","public",C.TEST_USER_SHORT_NAME).mkdirs();
		Path.makeFile(localNode.getWorkAreaRootPath(),"pips","institution",C.TEST_USER_SHORT_NAME).mkdirs();
	}
	
	private static void clearDB() {
		TESTHelper.clearDB();
	}
}
