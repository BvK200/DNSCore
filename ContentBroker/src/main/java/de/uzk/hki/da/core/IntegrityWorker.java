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

/**
 * The package integrity.
 */
package de.uzk.hki.da.core;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.mail.MessagingException;

import org.hibernate.Session;
import org.slf4j.MDC;

import de.uzk.hki.da.grid.GridFacade;
import de.uzk.hki.da.model.Copy;
import de.uzk.hki.da.model.Node;
import de.uzk.hki.da.model.Object;
import de.uzk.hki.da.model.Package;
import de.uzk.hki.da.model.PreservationSystem;
import de.uzk.hki.da.model.StoragePolicy;
import de.uzk.hki.da.service.HibernateUtil;
import de.uzk.hki.da.service.Mail;
import de.uzk.hki.da.utils.StringUtilities;


/**
 * Scans the Integrity of AIP Files stored in the Grid.
 * Integrity is classified as number of Repls is reached
 * and Checksum is of all replicas is correct. 
 * 
 * @author Jens Peters
 *
 */
public class IntegrityWorker extends Worker{

	private String localNodeId;
	
	private PreservationSystem pSystem;
	private Node node;
	/** The irods grid connector. */
	private GridFacade gridFacade;

	public void init(){
		node = new Node(); 
		node.setId(Integer.parseInt(localNodeId));
		setpSystem(new PreservationSystem()); getPSystem().setId(1);
		Session session = HibernateUtil.openSession();
		session.beginTransaction();
		session.refresh(getPSystem());
		session.refresh(node);
		session.getTransaction().commit();
		session.close();
	}
	
	@Override
	public void setMDC() {
		MDC.put(WORKER_ID, "integrity");
	} 
	
	
	/**
	 * Checking for the AIPs related to this node.
	 * @author Daniel M. de Oliveira
	 * @author Jens Peters
	 */
	@Override
	public void scheduleTaskImplementation(){
		logger.trace("Scanning AIP s of node " + localNodeId );

		try {
			
			Object object = null;
			if ((object=fetchObjectForAudit(localNodeId))==null) { 
				logger.warn("Found no object to audit.") ;
				return;
			}
			
			Integer auditResult = checkObjectValidity(object);
			updateObject(object,auditResult);
			
			if (auditResult==Object.ObjectStatus.Error) {
				sendEmail(object);
			} else logger.info("checked OK : " + object.getIdentifier());
		
			
		} catch (Exception e) {
			logger.error("Error in integrityCheck schedule Task " + e.getMessage(),e);
		}
	}
	
	/**
	 * Determines which of the objects that the local node is responsible for 
	 * (since it holds the primary copies of them) is the one which
	 * has not been checked for the longest period of time. 
	 * 
	 * @return the next object that needs audit. null if there is no object in the database which meets the criteria.
	 * 
	 * @author Jens Peters
	 * @author Daniel M. de Oliveira
	 * 
	 */
	private synchronized Object fetchObjectForAudit(String localNodeId) {
		
		Session session = null;
		try {
			session = HibernateUtil.openSession();
			session.beginTransaction();

			Calendar now = Calendar.getInstance();
			now.add(Calendar.HOUR_OF_DAY, -24);
		
			@SuppressWarnings("rawtypes")
			List l = null;
			l = session.createQuery("from Object o where o.initial_node = ?1 and o.last_checked < ?2 and "
					+ "o.object_state != ?3 and o.object_state != ?4 and o.object_state >= 50"
					+ "order by o.last_checked asc")
					.setParameter("1", node.getName())
					.setTimestamp("2",now.getTime())
					.setParameter("3", Object.ObjectStatus.InWorkflow) // don't consider objects under work
					.setParameter("4", Object.ObjectStatus.UnderAudit) //           ||
							.setReadOnly(true).list();
			
			Object objectToAudit = (Object) l.get(0);
			for (Package p: objectToAudit.getPackages()) {
				for (Copy c: p.getCopies()) {
				}
			}
			// lock object
			objectToAudit.setObject_state(Object.ObjectStatus.UnderAudit);
			session.update(objectToAudit);
			session.getTransaction().commit();
			session.close();
			
			return objectToAudit;
		
		} catch (IndexOutOfBoundsException e){
			if (session!=null) session.close();
			return null;
		}
	}	
	
	
	/**
	 * Updates the object state, sets the current time, and updates
	 * the database object accordingly
	 * @param object
	 * @param auditResult
	 */
	private synchronized void updateObject(Object object,Integer auditResult){
		
		object.setLast_checked(new Date());
		object.setObject_state(auditResult);
		Session session = HibernateUtil.openSession();
		session.beginTransaction();
		session.update(object);
		session.getTransaction().commit();
		session.close();
	}
	
	
	
	
	
	
	/**
	 * Send email.
	 *
	 * @param obj the obj
	 */
	private void sendEmail(Object obj) {
		// send Mail to Admin with Package in Error
		logger.debug("Trying to send email");
		String subject = "[" + "da-nrw".toUpperCase() +  "] Problem Report für " + obj.getIdentifier() + " auf " + localNodeId;
		if (node.getAdmin().getEmailAddress() != null && !node.getAdmin().getEmailAddress().equals("")) {
			try {
				Mail.sendAMail( getPSystem().getAdmin().getEmailAddress() , node.getAdmin().getEmailAddress(), subject, "Es gibt ein Problem mit dem Objekt an Ihrem Knoten " + obj.getContractor().getShort_name()+ "/" + obj.getIdentifier());
			} catch (MessagingException e) {
				logger.error("Sending email problem report for " + obj.getIdentifier() + "failed");
			}
		} else {
			logger.error("Node Admin has no valid Email address!");
		}
	}
	
	

	/**
	 * @author Jens Peters
	 * @author Daniel M. de Oliveira
	 * @param obj the obj
	 * @return the new object state. Either archivedAndValidState or errorState.
	 */
	int checkObjectValidity(Object obj) {
		if (getPSystem().getMinRepls() == null || getPSystem().getMinRepls() ==0) throw new IllegalStateException("minNodes not set correctly!");
		
		logger.debug("Check Object "+ obj.getIdentifier());
		boolean completelyValid = true;		
		if (obj.getContractor()==null) {
			String err= "Could not determine valid Contractor for object " + obj.getIdentifier();
			logger.error(err);
			return Object.ObjectStatus.Error;
		}
		String dao_base = C.WA_AIP + "/"+obj.getContractor().getShort_name()+"/"+obj.getIdentifier()+"/"+obj.getIdentifier()+".pack_";
		for (Package pack : obj.getPackages()) {
			String dao = dao_base + pack.getName()+".tar"; 
			logger.debug("Checking: " + dao );
			logger.debug("Original Checksum in Package Table is " + pack.getChecksum());
			int copies = 0;
			String checksum_now_pc = gridFacade.reComputeAndGetChecksumInCustody(dao);
			if (StringUtilities.isNotSet(checksum_now_pc) || (!checksum_now_pc.equals(pack.getChecksum()))) {
				String err= "PRIMARY COPY in ERROR " + obj.getIdentifier();
				logger.error(err);
				return Object.ObjectStatus.Error;
			} 
			copies = 1;
			for (Copy copy : pack.getCopies() ) {
				if (!copy.getChecksum().equals(pack.getChecksum())) {
					String err= "SECONDARY COPY in ERROR "+ obj.getIdentifier();
					logger.error(err);
					completelyValid = false;
				} else copies ++;
			}
			if (copies<getPSystem().getMinRepls()) completelyValid = false; 
				
		}
		if (completelyValid) return Object.ObjectStatus.ArchivedAndValid;
		else return Object.ObjectStatus.Error;
	}
	/**
	 * Gets the irods grid connector.
	 *
	 * @return the irods grid connector
	 */
	public GridFacade getGridFacade() {
		return gridFacade;
	}

	/**
	 * Sets the irods grid connector.
	 *
	 * @param gridFacade the new irods grid connector
	 */
	public void setGridFacade(GridFacade gridFacade) {
		this.gridFacade = gridFacade;
	}

	public String getLocalNodeId() {
		return localNodeId;
	}

	public void setLocalNodeId(String localNodeId) {
		this.localNodeId = localNodeId;
	}


	public PreservationSystem getPSystem() {
		return pSystem;
	}


	public void setpSystem(PreservationSystem pSystem) {
		this.pSystem = pSystem;
	}

	/**
	 * For testing purposes only
	 * @param node
	 */
	public void setNode(Node node){
		this.node = node;
	}

}