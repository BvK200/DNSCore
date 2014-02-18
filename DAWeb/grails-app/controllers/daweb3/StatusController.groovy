package daweb3
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
 * Adds capability of getting information about archived objects (AIP)
 * or SIP by automated systems (JSON responses)  
 * 
 * evaluates at first status in the Queue, then AIP 
 * 
 * @Author Jens Peters 
 * @Author Sebastian Cuy
 */
import org.hibernate.criterion.CriteriaSpecification;

import grails.converters.JSON

class StatusController {

	def index() {
	
		def result = [:]
		def results = [:]
		
		def rList = null
		if (session.bauthuser == null) {
			log.error "Login failed";
			response.status = 403
			result = [status: "forbidden"]
			render result as JSON
			return
		}
		def contractor = Contractor.findByShortName(session.bauthuser)
		// listall objects of Contractor
		if (params.listallobjects) {
			def objects = Object.findAllByContractorAndObject_stateGreaterThan(contractor, 50)
			results.result = []
			objects.each()  { inst ->
				if (inst.object_state==100) result.status = "archived"
				else result.status = "archived - but check needed"
				result.urn = inst.urn
				result.contractor = inst.contractor.shortName
				result.origName = inst.origName
				def packages = []
				result.packages = packages
				inst.packages.each() {pack ->
						result.packages.add(pack.name)
				}
				result = [:]
				results.result.add(result)
			}
			render results as JSON
			return
		}
				
		if (params.urn ) {
			rList = QueueEntry.withCriteria() {
					createAlias('obj', 'o', 
					CriteriaSpecification.INNER_JOIN)
					createAlias('o.contractor', 'contractor', CriteriaSpecification.INNER_JOIN)
					eq("contractor.shortName", session.bauthuser)
					eq("o.urn", params.urn)
			};
			} else if (params.origName) {
			rList = QueueEntry.findAll("from QueueEntry as q where q.obj.contractor.shortName=:csn and q.obj.origName=:on",
             [on: params.origName,
				 csn: session.bauthuser]);
		} else if (params.identifier) {
			rList = QueueEntry.findAll("from QueueEntry as q where q.obj.contractor.shortName=:csn and q.obj.identifier=:idn",
			 [idn: params.identifier, 
				 csn: session.bauthuser]);
		}
		boolean hasAQueueEntry = false
		def queueResult = "in progress";
		// found a QueueEntry
		results.result = []
		rList.each()  { instance ->	
			result.urn = instance.obj.urn
			result.contractor = instance.obj.contractor.shortName;
			result.origName = instance.obj.origName
			result.identifier = instance.obj.identifier
			if (instance.status.endsWith("1")) {
				queueResult = "in progress error : (" + instance.status + ")"
			}
			if (instance.status.endsWith("0")) {
				queueResult = "in progress waiting : (" + instance.status + ")"
			}
			if (instance.status.endsWith("2")) {
				queueResult = "in progress working : (" + instance.status + ")"
			}
			result.status =queueResult
			
			hasAQueueEntry = true;
			results.result.add(result)
			result = [:]
			
		}  
		
		// we give precedence for queue Entries instead judt searching for an object
		if (hasAQueueEntry) {
			render results as JSON
			return
		} else {
		// search for an Object
		if (params.urn) {
				rList = Object.findAllByContractorAndUrnAndObject_stateBetween(contractor, params.urn,50,100)
		}
		if (params.origName) {
				rList = Object.findAllByContractorAndOrigNameAndObject_stateBetween(contractor, params.origName,50,100)
		} 
		if (params.identifier) {
				rList = Object.findAllByContractorAndIdentifierAndObject_stateBetween(contractor, params.identifier,50,100)	
		}
		// Found Object, must be true if we found anything (Queue or Object only)
		boolean foundObject = false;
		results.result = []
		rList.each()  { instance ->
				if (instance.object_state==100) result.status = "archived"
				else {
					result.status = "archived - but check needed"
				}
				result.urn = instance.urn
				result.contractor = instance.contractor.shortName
				result.origName = instance.origName
				result.identifier = instance.identifier
				def packages = []
				result.packages = packages;
				instance.packages.each() {pack ->
						result.packages.add(pack.name)
				} 
				results.result.add(result)
				result = [:]
				foundObject = true;
		} 
		// unknown item
		if (!foundObject && !hasAQueueEntry) {
		response.status = 404
		result = [ status : "not found"]
		render result as JSON
		return	
		}
		
		render results as JSON
		return
		}
	}
	def teaser() {
		
		
	}
	
	

}
