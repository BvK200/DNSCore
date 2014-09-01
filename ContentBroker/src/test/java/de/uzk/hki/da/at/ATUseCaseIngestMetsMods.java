/*
  DA-NRW Software Suite | ContentBroker
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

package de.uzk.hki.da.at;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.uzk.hki.da.model.Object;
import de.uzk.hki.da.utils.C;
import de.uzk.hki.da.utils.Path;
import de.uzk.hki.da.utils.TESTHelper;

/**
 * @author Polina Gubaidullina
 */
public class ATUseCaseIngestMetsMods extends Base{
	
	private static final String origName = 		"ATUseCaseIngestMetsMods";
	private Object object;
	private String METS_XPATH_EXPRESSION = 		"//mets:file";
//	private static final String MODS_NS = 		"http://www.loc.gov/mods/v3";
	private static Document metsDoc;
	
	
	@Before
	public void setUp() throws IOException{
		setUpBase();
		ingest(origName);
		object = retrievePackage(origName,"1");
		System.out.println("object identifier: "+object.getIdentifier());
	}
	
	
	
	
	@After
	public void tearDown(){
		try{
			new File("/tmp/"+object.getIdentifier()+".pack_1.tar").delete();
			FileUtils.deleteDirectory(new File("/tmp/"+object.getIdentifier()+".pack_1"));
		}catch(Exception e){
			System.out.println(e.getMessage());
		}
		
		TESTHelper.clearDB();
		cleanStorage();
	}
	
	
	
	
	@Test
	public void checkReferencesAndMimetype() throws JDOMException, FileNotFoundException, IOException {
		
		SAXBuilder builder = new SAXBuilder();
		
		metsDoc = builder.build
				(new FileReader(Path.make(localNode.getWorkAreaRootPath(),"pips", "public", "TEST", object.getIdentifier(), object.getPackage_type()+".xml").toFile()));

		
		XPath xPath = XPath.newInstance(METS_XPATH_EXPRESSION);
		
		@SuppressWarnings("rawtypes")
		List allNodes = xPath.selectNodes(metsDoc);
		
		for (java.lang.Object node : allNodes) {
			Element fileElement = (Element) node;
			Attribute attr = fileElement.getChild("FLocat", C.METS_NS).getAttribute("href", C.XLINK_NS);
			Attribute attrMT = fileElement.getAttribute("MIMETYPE");
			assertTrue(attr.getValue().contains("http://data.danrw.de/") && attr.getValue().endsWith(".jpg"));
			assertTrue(attrMT.getValue().equals("image/jpeg"));
		}
	}
}
