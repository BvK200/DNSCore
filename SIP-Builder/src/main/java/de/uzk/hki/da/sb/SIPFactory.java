/*
  DA-NRW Software Suite | SIP-Builder
  Copyright (C) 2014 Historisch-Kulturwissenschaftliche Informationsverarbeitung
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

package de.uzk.hki.da.sb;

import gov.loc.repository.bagit.Bag;
import gov.loc.repository.bagit.BagFactory;
import gov.loc.repository.bagit.PreBag;
import gov.loc.repository.bagit.utilities.SimpleResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import javax.swing.JOptionPane;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;

import de.uzk.hki.da.metadata.ContractRights;
import de.uzk.hki.da.metadata.PremisXmlWriter;
import de.uzk.hki.da.pkg.SipArchiveBuilder;
import de.uzk.hki.da.pkg.CopyUtility;
import de.uzk.hki.da.pkg.NestedContentStructure;
import de.uzk.hki.da.utils.C;
import de.uzk.hki.da.utils.Utilities;
import de.uzk.hki.da.utils.formatDetectionService;

/**
 * The central SIP production class
 * 
 * @author Thomas Kleinke
 */

public class SIPFactory {
	
	private Logger logger = Logger.getLogger(SIPFactory.class);

	private String sourcePath = null;
	private String destinationPath = null;
	private KindOfSIPBuilding kindofSIPBuilding = null;
	private String name = null;
	private boolean createCollection;
	private String collectionName = null;
	private File collectionFolder = null;
	private ContractRights contractRights = new ContractRights();
	private File rightsSourcePremisFile = null;
	private SipBuildingProcess sipBuildingProcess;

	private boolean alwaysOverwrite;
	private boolean skippedFiles;
	private boolean ignoreZeroByteFiles = false;
	private boolean compress;

	private List<String> forbiddenFileExtensions = null;
	
	private File listCreationTempFolder = null;

	private MessageWriter messageWriter;
	private ProgressManager progressManager;

	private Feedback returnCode;


	public enum KindOfSIPBuilding { MULTIPLE_FOLDERS, SINGLE_FOLDER, NESTED_FOLDERS};

	/**
	 * Creates and starts a new SIP building process
	 */
	public void startSIPBuilding() {
		sipBuildingProcess = new SipBuildingProcess();
		sipBuildingProcess.start();
	}

	/**
	 * Creates a list of source folders
	 * 
	 * @param folderPath The main source folder path
	 * @throws Exception 
	 */

	HashMap<File, String> createFolderList(String folderPath) throws Exception {

		HashMap<File, String> folderListWithFolderNames = new HashMap<File, String>();
		File sourceFolder = new File(folderPath);

		switch (kindofSIPBuilding) {
		case MULTIPLE_FOLDERS:
			List<File> folderContent = Arrays.asList(sourceFolder.listFiles());
			for (File file : folderContent) {
				if (!file.isHidden() && file.isDirectory())
					folderListWithFolderNames.put(file, null);
			}
			break;

		case SINGLE_FOLDER:
			folderListWithFolderNames.put(sourceFolder, null);
			break;
			
		case NESTED_FOLDERS:
			NestedContentStructure ncs;
			try {
				TreeMap<File, String> metadataFileWithType = new formatDetectionService(sourceFolder).getMetadataFileWithType();
				if(!metadataFileWithType.isEmpty() &&
						(!metadataFileWithType.get(metadataFileWithType.firstKey()).equals(C.CB_PACKAGETYPE_METS))) {
					messageWriter.showMessage("Es wurde eine Metadatendatei des Typs "+metadataFileWithType.get(metadataFileWithType.firstKey())
							+" auf der obersten Ebene gefunden. "
							+ "\nBitte wählen Sie diese Option ausschließlich für die Erstellung von SIPs des Typs METS.");
				} else {
					ncs = new NestedContentStructure(sourceFolder);
					folderListWithFolderNames = ncs.getSipCandidates();
					if(folderListWithFolderNames.isEmpty()) {
						messageWriter.showMessage("Es wurde kein Unterverzeichnis mit einer METS-Metadatendatei gefunden.");
					}
					break;
				}
			} catch (IOException e) {
				throw new Exception(e);
			}
		default:
			break;
		}

		return folderListWithFolderNames;		
	}

	/**
	 * Starts the progress manager and creates a progress manager job for each SIP to build
	 * 
	 * @param folderList The source folder list
	 * @return The method result as a Feedback enum
	 */
	private Feedback initializeProgressManager(List<File> folderList) {

		progressManager.reset();

		if (createCollection)
			progressManager.addJob(-1, collectionName, 0);

		int i = 0;
		for (File folder : folderList) {
			if (!folder.exists()) {
				logger.error("Folder " + folder.getAbsolutePath() + " does not exist anymore.");
				return Feedback.COPY_ERROR;
			}

			progressManager.addJob(i, folder.getName(), FileUtils.sizeOfDirectory(folder));
			i++;
		}

		progressManager.calculateProgressParts(createCollection);
		progressManager.createStartMessage();

		return Feedback.SUCCESS;
	}

	/**
	 * Creates a SIP out of the given source folder 
	 * 
	 * @param jobId The job ID
	 * @param sourceFolder The source folder
	 * @return The method result as a Feedback enum
	 */
	private Feedback buildSIP(int jobId, File sourceFolder, String newPackageName) {

		progressManager.startJob(jobId);
		Feedback feedback;
		
		String packageName = "";
		if(newPackageName!=null) {
			packageName = newPackageName;
		} else {
			packageName = getPackageName(sourceFolder);
		}
		
		String archiveFileName = packageName;
		if (compress)
			archiveFileName += ".tgz";
		else
			archiveFileName += ".tar";
		
		File archiveFile = new File(destinationPath + File.separator + archiveFileName);

		if (!checkForExistingSip(archiveFile)) {
			progressManager.skipJob(jobId);
			skippedFiles = true;
			return Feedback.SUCCESS;
		}

		if (Utilities.checkForZeroByteFiles(sourceFolder, packageName, messageWriter)) {
			if (!ignoreZeroByteFiles) {
				String message = "WARNING: Found zero byte files in folder " + sourceFolder + ":\n";
				for (String s : messageWriter.getZeroByteFiles()) {
					message += s;
					message += "\n";
				}
				logger.info(message);
				return Feedback.ZERO_BYTES_ERROR;
			}
		}

		File tempFolder = new File(destinationPath + File.separator + getTempFolderName());
		File packageFolder = new File(tempFolder, packageName);

		if ((feedback = copyFolder(jobId, sourceFolder, packageFolder)) != Feedback.SUCCESS) {
			rollback(tempFolder);
			return feedback;
		}

		if ((feedback = createPremisFile(jobId, packageFolder, packageName)) != Feedback.SUCCESS) { 
			rollback(tempFolder);
			return feedback;
		}

		if ((feedback = createBag(jobId, packageFolder)) != Feedback.SUCCESS) {
			rollback(tempFolder);
			return feedback;
		}

		if ((feedback = buildArchive(jobId, packageFolder, archiveFile)) != Feedback.SUCCESS) {
			rollback(tempFolder, archiveFile);
			return feedback;
		}

		if ((feedback = deleteTempFolder(jobId, tempFolder)) != Feedback.SUCCESS)
			return feedback;

		if (createCollection) {
			if ((feedback = moveSipToCollectionFolder(jobId, archiveFile)) != Feedback.SUCCESS)
				return feedback;
		}

		return Feedback.SUCCESS;
	}

	/**
	 * Copies the contents of the given source folder to a newly created temp folder
	 * 
	 * @param jobId The job ID
	 * @param sourceFolder The source folder
	 * @param tempFolder The temp folder
	 * @return The method result as a Feedback enum
	 */
	private Feedback copyFolder(int jobId, File sourceFolder, File tempFolder) {

		progressManager.copyProgress(jobId, 0);

		File dataFolder = new File(tempFolder, "data");
		dataFolder.mkdirs();

		CopyUtility copyUtility = new CopyUtility();
		copyUtility.setProgressManager(progressManager);
		copyUtility.setJobId(jobId);
		copyUtility.setSipBuildingProcess(sipBuildingProcess);

		try {
			if (!copyUtility.copyDirectory(sourceFolder, dataFolder, forbiddenFileExtensions))
				return Feedback.ABORT;
		} catch (Exception e) {
			logger.error("Failed to copy folder " + sourceFolder.getAbsolutePath() + " to " + tempFolder.getAbsolutePath(), e);
			return Feedback.COPY_ERROR;
		}

		return Feedback.SUCCESS;
	}

	/**
	 * Creates the premis.xml file
	 * 
	 * @param jobId The job ID
	 * @param folder The temp folder
	 * @param packageName The package name
	 * @return The method result as a Feedback enum
	 */
	private Feedback createPremisFile(int jobId, File folder, String packageName) {

		progressManager.premisProgress(jobId, 0.0);

		File premisFile = new File(folder, "data" + File.separator + "premis.xml");

		PremisXmlWriter premisWriter = new PremisXmlWriter();
		try {
			if (rightsSourcePremisFile != null)
				premisWriter.createPremisFile(this, premisFile, rightsSourcePremisFile, packageName);
			else
				premisWriter.createPremisFile(this, premisFile, packageName);
		} catch (Exception e) {
			logger.error("Failed to create premis file " + premisFile.getAbsolutePath(), e);
			return Feedback.PREMIS_ERROR;
		}

		progressManager.premisProgress(jobId, 100.0);

		if (sipBuildingProcess.isAborted())
			return Feedback.ABORT;

		return Feedback.SUCCESS;
	}

	/**
	 * Creates BagIt checksums and metadata for the files in the given folder
	 * 
	 * @param jobId The job ID
	 * @param folder The temp folder
	 * @return The method result as a Feedback enum
	 */
	private Feedback createBag(int jobId, File folder) {

		progressManager.bagitProgress(jobId, 0.0);

		BagFactory bagFactory = new BagFactory();
		PreBag preBag = bagFactory.createPreBag(folder);
		preBag.makeBagInPlace(BagFactory.LATEST, false);
		progressManager.bagitProgress(jobId, 10.0);

		if (sipBuildingProcess.isAborted())
			return Feedback.ABORT;

		Bag bag = bagFactory.createBag(folder);
		progressManager.bagitProgress(jobId, 40.0);

		if (sipBuildingProcess.isAborted())
			return Feedback.ABORT;

		SimpleResult result = bag.verifyValid();
		if(result.isSuccess()) {
			progressManager.bagitProgress(jobId, 50.0);
			return Feedback.SUCCESS;
		}
		else {
			logger.error("Bag in folder " + folder.getAbsolutePath() + " is not valid.\n" +
					result.getErrorMessages());				
			return Feedback.BAGIT_ERROR;
		}
	}

	/**
	 * Creates a tar oder tgz archive file out of the given folder.
	 * The value of the field 'compress' determines if a tar or tgz file is created. 
	 * 
	 * @param jobId The job ID
	 * @param folder The folder to archive
	 * @param archiveFile The target archive file
	 * @return The method result as a Feedback enum
	 */
	private Feedback buildArchive(int jobId, File folder, File archiveFile) {

		progressManager.setJobFolderSize(jobId, FileUtils.sizeOfDirectory(folder));
		progressManager.archiveProgress(jobId, 0);

		SipArchiveBuilder archiveBuilder = null;
		try {
			archiveBuilder = new SipArchiveBuilder();
			archiveBuilder.setProgressManager(progressManager);
			archiveBuilder.setJobId(jobId);
			archiveBuilder.setSipBuildingProcess(sipBuildingProcess);
		} catch (Exception e) {
			logger.error("Failed to instantiate the ArchiveBuilder ", e);
			return Feedback.ABORT;
		}

		try {
			if (!archiveBuilder.archiveFolder(folder, archiveFile, true, compress))
				return Feedback.ABORT;
		} catch (Exception e) {
			logger.error("Failed to archive folder " + folder.getAbsolutePath() + " to archive " +
					archiveFile.getAbsolutePath(), e);
			return Feedback.ARCHIVE_ERROR;
		}

		return Feedback.SUCCESS;
	}

	/**
	 * Deletes the temp folder and its contents
	 * 
	 * @param jobId The job ID
	 * @param folder The temp folder to delete
	 * @return The method result as a Feedback enum
	 */
	private Feedback deleteTempFolder(int jobId, File folder) {

		progressManager.deleteTempProgress(jobId, 0.0);

		try {
			FileUtils.deleteDirectory(folder);
		} catch (IOException e) {
			logger.warn("Failed to delete temp folder " + folder.getAbsolutePath(), e);
			return Feedback.DELETE_TEMP_FOLDER_WARNING;
		}

		progressManager.deleteTempProgress(jobId, 100.0);

		return Feedback.SUCCESS;
	}

	/**
	 * Moves the given archived SIP file to the collection folder
	 * 
	 * @param jobId The job ID
	 * @param archiveFile The SIP file
	 * @return The method result as a Feedback enum
	 */
	private Feedback moveSipToCollectionFolder(int jobId, File archiveFile) {

		try {
			FileUtils.moveFileToDirectory(archiveFile, new File(collectionFolder, "data"), false);
		} catch (IOException e) {
			logger.error("Failed to move file " + archiveFile.getAbsolutePath() + 
					" to folder " + collectionFolder.getAbsolutePath(), e);
			return Feedback.MOVE_TO_COLLECTION_FOLDER_ERROR;
		}

		return Feedback.SUCCESS;		
	}

	/**
	 * Checks if the given SIP file already exists at the destination path. If an existing SIP is found, the user
	 * may decide to overwrite it or abort the process.
	 * 
	 * @param archiveFileName The name of the folder to check
	 * @return true if no existing SIP for the given folderName is found or the user decides to overwrite the existing SIP
	 * @return false if a SIP for the given folderName already exists and the user decides to abort the SIP creation process
	 */
	private boolean checkForExistingSip(File sip){

		if (alwaysOverwrite)
			return true;

		if (sip.exists())
		{
			MessageWriter.UserInput userInput = messageWriter.showOverwriteDialog
					("Im Ordner \"" + destinationPath + "\" existiert bereits ein SIP mit\n" +
							"dem Namen \"" + FilenameUtils.getBaseName(sip.getAbsolutePath()) + "\".\n\n" +
							"Möchten Sie das bestehende SIP überschreiben?");
			switch (userInput) {
			case YES:
				return true;
			case NO:
				return false;
			case ALWAYS_OVERWRITE:
				alwaysOverwrite = true;
				return true;
			}
		}

		return true;
	}

	private String getPackageName(File folder) {

		String packageName;

		if (name != null && !name.equals(""))
			packageName = name;
		else
			packageName = folder.getName();

		return packageName;
	}

	private String getTempFolderName() {

		String baseName = "temp";

		if (!new File(destinationPath + File.separator + baseName).exists())
			return baseName;

		String tempFolderName;
		int i = 0;
		do {
			tempFolderName = baseName + "_" + i++;						
		} while (new File(destinationPath + File.separator + tempFolderName).exists());

		return tempFolderName;
	}

	private void rollback(File folder) {

		rollback(folder, null);
	}

	private void rollback(File folder, File archiveFile) {

		FileUtils.deleteQuietly(folder);

		if (archiveFile != null)
			FileUtils.deleteQuietly(archiveFile);				
	}

	/**
	 * This method is called by the SIP building process.
	 * It deletes partially created collections and aborts the progress manager.
	 */
	private void abortSipBuilding() {

		if (listCreationTempFolder != null && listCreationTempFolder.exists())
			FileUtils.deleteQuietly(listCreationTempFolder);
		
		FileUtils.deleteQuietly(collectionFolder);
		progressManager.abort();
	}

	/**
	 * Aborts the SIP building process
	 */
	public void abort() {
		sipBuildingProcess.abort();
	}

	/**
	 * @return true if the SIP building process is working, otherwise false
	 */
	public boolean isWorking() {

		if (sipBuildingProcess == null ||
				!sipBuildingProcess.isAlive())
			return false;
		else
			return true;			
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}

	public String getDestinationPath() {
		return destinationPath;
	}

	public void setDestinationPath(String destinationPath) {
		this.destinationPath = destinationPath;
	}

	public KindOfSIPBuilding getKindofSIPBuilding() {
		return kindofSIPBuilding;
	}

	public void setKindofSIPBuilding(KindOfSIPBuilding kindofSIPBuilding) {
		this.kindofSIPBuilding = kindofSIPBuilding;
	}

	public void setKindofSIPBuilding(String kindofSIPBuildingName) {
		this.kindofSIPBuilding = Utilities.translateKindOfSIPBuilding(kindofSIPBuildingName);
	}

	public ContractRights getContractRights() {
		return contractRights;
	}

	public void setContractRights(ContractRights contractRights) {
		this.contractRights = contractRights;
	}

	public void setProgressManager(ProgressManager progressManager) {
		this.progressManager = progressManager;
	}

	public void setMessageWriter(MessageWriter messageWriter) {
		this.messageWriter = messageWriter;
	}

	public boolean getCreateCollection() {
		return createCollection;
	}

	public void setCreateCollection(boolean createCollection) {
		this.createCollection = createCollection;
	}

	public String getCollectionName() {
		return collectionName;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public File getRightsSourcePremisFile() {
		return rightsSourcePremisFile;
	}

	public void setRightsSourcePremisFile(File rightsSourcePremisFile) {
		this.rightsSourcePremisFile = rightsSourcePremisFile;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getForbiddenFileExtensions() {
		return forbiddenFileExtensions;
	}

	public void setForbiddenFileExtensions(List<String> forbiddenFileExtensions) {
		this.forbiddenFileExtensions = forbiddenFileExtensions;
	}
	
	public File getListCreationTempFolder() {
		return listCreationTempFolder;
	}

	public void setListCreationTempFolder(File listCreationTempFolder) {
		this.listCreationTempFolder = listCreationTempFolder;
	}

	public void setIgnoreZeroByteFiles(boolean ignoreZeroByteFiles) {
		this.ignoreZeroByteFiles = ignoreZeroByteFiles;
	}

	public void setLogger(Logger logger) {
		this.logger = logger;
	}

	public Feedback getReturnCode() {
		return returnCode;
	}

	public boolean getCompress() {
		return compress;
	}

	public void setCompress(boolean compress) {
		this.compress = compress;
	}


	/**
	 * The SIP building procedure is run in its own thread to prevent GUI freezing
	 * 
	 * @author Thomas Kleinke
	 */
	public class SipBuildingProcess extends Thread {
		
		private boolean abortRequested = false;

		/**
		 * Creates one ore more SIPs as specified by the user
		 */
		public void run() {
			
			alwaysOverwrite = false;
			skippedFiles = false;				 
			messageWriter.resetZeroByteFiles();

			if (createCollection) {
				collectionFolder = new File(new File(destinationPath), collectionName);

				if (collectionFolder.exists()) {
					MessageWriter.UserInput answer =
							messageWriter.showCollectionOverwriteDialog("Eine Lieferung mit dem Namen \"" + collectionName + "\"" + 
									"existiert bereits.\n" +
									"Möchten Sie die bestehende Lieferung überschreiben?");

					switch (answer) {
					case YES:
						FileUtils.deleteQuietly(collectionFolder);
						break;
					case NO:
						progressManager.abort();
						return;
					default:
						break;
					}
				}
				
				new File(collectionFolder, "data").mkdirs();
			}

			HashMap<File, String> folderListWithNames = null;
			try {
				folderListWithNames = createFolderList(sourcePath);
				@SuppressWarnings("unchecked")
				HashMap<File, String> tmpFolderListWithNames = (HashMap<File, String>) folderListWithNames.clone();
				for(File f : folderListWithNames.keySet()) {
					String metadataType = "";
					try {
						TreeMap<File, String> metadataFileWithType = new formatDetectionService(f).getMetadataFileWithType();
						if(!metadataFileWithType.isEmpty()) {
							File file = metadataFileWithType.firstKey();
							metadataType = metadataFileWithType.get(file);
							if(metadataType!=C.CB_PACKAGETYPE_XMP) {
								if(!duplicateFileNames(f, tmpFolderListWithNames)) {
									Utilities.validateFileReferencesInMetadata(file, metadataType);	
								}
							}				
						} else {
							duplicateFileNames(f, tmpFolderListWithNames);
						}
					} catch (Error e) {
						if(metadataType.equals(C.CB_PACKAGETYPE_EAD)) {
							String msg = "Aus dem Verzeichnis "+f+" wird kein SIP erstellt. \n"+e.getMessage();
							messageWriter.showLongErrorMessage(msg);
							tmpFolderListWithNames.remove(f);
							returnCode = Feedback.WRONG_REFERENCES_IN_METADATA;
						} else {
							String msg = e.getMessage()+" \nMöchten Sie die SIP-Erstellung dennoch fortsetzen?";
							logger.error(msg);
							MessageWriter.UserInput answer = messageWriter.showWrongReferencesInMetadataDialog(msg);
							returnCode = Feedback.WRONG_REFERENCES_IN_METADATA;
							switch (answer) {
							case YES:
								break;
							case NO:
								messageWriter.showMessage("Aus dem Verzeichnis "+f+" wird kein SIP erstellt.");
								tmpFolderListWithNames.remove(f);
								break;
							default:
								break;
							}
						}
					}
				}
				folderListWithNames = tmpFolderListWithNames;
				if(folderListWithNames.isEmpty()) {
					abortSipBuilding();
					return;
				} 
			} catch (Exception e) {
				messageWriter.showLongErrorMessage("Das SIP konnte nicht erstellt werden.\n\n" +
						"Ihre Daten sind möglicherweise nicht valide: \n\n"+e.getMessage());
				returnCode = Feedback.INVALID_METADATA;
				abortSipBuilding();
				return;
			}
			List<File> folderList = new ArrayList<File>();
			for(File f : folderListWithNames.keySet()) {
				folderList.add(f);
			}
			if (initializeProgressManager(folderList) != Feedback.SUCCESS) {
				messageWriter.showMessage("Das SIP konnte nicht erstellt werden.\n\n" +
						"Der angegebene Ordner existiert nicht mehr. ", JOptionPane.ERROR_MESSAGE);
				abortSipBuilding();
				return;
			}

			int id = 0;
			for (File folder : folderListWithNames.keySet()) {
				returnCode = buildSIP(id, folder, folderListWithNames.get(folder));

				if (returnCode != Feedback.SUCCESS && returnCode != Feedback.DELETE_TEMP_FOLDER_WARNING)
					abortSipBuilding();

				switch(returnCode) {
				case COPY_ERROR:
					messageWriter.showMessage("Das SIP \"" + folder.getName() + "\" konnte nicht erstellt werden.\n\n" +
							"Während des Kopiervorgangs ist ein Fehler aufgetreten.", JOptionPane.ERROR_MESSAGE);
					return;
				case ZERO_BYTES_ERROR:
					messageWriter.showZeroByteFileMessage();
					return;
				case PREMIS_ERROR:
					messageWriter.showMessage("Das SIP \"" + folder.getName() + "\" konnte nicht erstellt werden.\n\n" +
							"Während der Erstellung der Premis-Datei ist ein Fehler aufgetreten.", JOptionPane.ERROR_MESSAGE);
					return;
				case BAGIT_ERROR:
					messageWriter.showMessage("Das SIP \"" + folder.getName() + "\" konnte nicht erstellt werden.\n\n" +
							"Während der Erzeugung des Bags ist ein Fehler aufgetreten.", JOptionPane.ERROR_MESSAGE);
					return;
				case ARCHIVE_ERROR:
					messageWriter.showMessage("Das SIP \"" + folder.getName() + "\" konnte nicht erstellt werden.\n\n" +
							"Während der tgz-Archivierung ist ein Fehler aufgetreten.", JOptionPane.ERROR_MESSAGE);
					return;
				case DELETE_TEMP_FOLDER_WARNING:
					messageWriter.showMessage("Während der Bereinigung temporärer Daten ist ein Fehler aufgetreten.\n\n" +
							"Bitte löschen Sie nicht benötigte verbleibende Verzeichnisse\n" +
							"im Ordner \"" + destinationPath + "\" manuell.", JOptionPane.ERROR_MESSAGE);
					break;
				case MOVE_TO_COLLECTION_FOLDER_ERROR:
					messageWriter.showMessage("Das SIP \"" + folder.getName() + "\" konnte der Lieferung nicht hinzugefügt werden.", JOptionPane.ERROR_MESSAGE);
					return;
				case ABORT:
					return;
				default:
					break;
				}

				id++;
			}
			
			if (listCreationTempFolder != null && listCreationTempFolder.exists())
				FileUtils.deleteQuietly(listCreationTempFolder);

			if (createCollection) {
				progressManager.startJob(-1);
				if (createBag(-1, collectionFolder) == Feedback.BAGIT_ERROR)
					messageWriter.showMessage("Die Lieferung \"" + collectionName + "\" konnte nicht erstellt werden.\n\n" +
							"Während der Erzeugung des Bags ist ein Fehler aufgetreten.", JOptionPane.ERROR_MESSAGE);
			}

			progressManager.createSuccessMessage(skippedFiles);

			if (ignoreZeroByteFiles && messageWriter.getZeroByteFiles().size() > 0) {
				String message = "WARNING: Found zero byte files:";
				for (String s : messageWriter.getZeroByteFiles()) {
					message += "\n";
					message += s;						 
				}
				logger.info(message);
				messageWriter.showZeroByteFileMessage();
			}
		}

		public void abort() {
			abortRequested = true;
		}

		public boolean isAborted() {
			return abortRequested;
		}
		
		private boolean duplicateFileNames(File f, HashMap<File, String> tmpFolderListWithNames) {
			Boolean dfn = false;
			HashMap<String, List<File>> duplicateFileNames = getFilesWithDuplicateFileNames(f);
			if(!duplicateFileNames.isEmpty()) {
				dfn = true;
				String msg = "Aus dem Verzeichnis "+f+" wird kein SIP erstellt. \nDer Ordner enthält gleichnamige Dateien: \n"+duplicateFileNames;
				messageWriter.showLongErrorMessage(msg);
				tmpFolderListWithNames.remove(f);
				returnCode = Feedback.DUPLICATE_FILENAMES;
			} 
			return dfn;
		}
		
		private HashMap<String, List<File>> getFilesWithDuplicateFileNames(File folder) {
			logger.debug("Search for duplicate file names in folde "+folder);
			HashMap<String, List<File>> duplicateFilenamesWithFiles = new HashMap<String, List<File>>();
			HashMap<String, File> filenamesWithFiles = new HashMap<String, File>();
			for(File f : folder.listFiles()) {
				logger.debug("Check file "+f);
				if(f.isDirectory()) {
					getFilesWithDuplicateFileNames(f);
				} else {
					
					File file = new File(f.getAbsolutePath());
					String ext = FilenameUtils.getExtension(file.getAbsolutePath());
					String relFilePathWithoutExtension = new File(f.getAbsolutePath()).getAbsolutePath().toString().
							replace(new File(f.getParent()).getAbsolutePath()+File.separator, "").
							replace(ext, "");
					logger.debug("relFilePathWithoutExtension: "+relFilePathWithoutExtension);
					
					if(filenamesWithFiles.get(relFilePathWithoutExtension)==null) {
						logger.debug("New file name "+relFilePathWithoutExtension);
						filenamesWithFiles.put(relFilePathWithoutExtension, f);
					} else {
						if(duplicateFilenamesWithFiles.get(relFilePathWithoutExtension)!=null) {
							logger.debug("One more file with file name " + relFilePathWithoutExtension
									+ "\nFirst file is "+duplicateFilenamesWithFiles.get(relFilePathWithoutExtension));
							duplicateFilenamesWithFiles.get(relFilePathWithoutExtension).add(f);
						} else {
							List<File> duplicateFilenames = new ArrayList<File>();
							logger.debug("Second file with the same file name. "
									+ "\n First file is "+filenamesWithFiles.get(relFilePathWithoutExtension));
							duplicateFilenames.add(filenamesWithFiles.get(relFilePathWithoutExtension));
							duplicateFilenames.add(f);
							duplicateFilenamesWithFiles.put(relFilePathWithoutExtension, duplicateFilenames);
						}
					}
				}
			}
			return duplicateFilenamesWithFiles;
		}
	};
}
