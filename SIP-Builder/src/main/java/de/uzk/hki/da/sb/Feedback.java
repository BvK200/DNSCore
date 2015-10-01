package de.uzk.hki.da.sb;

public enum Feedback {
	
	EXIT_AFTER_HELP(-1),
	SUCCESS(0),
	INVALID_PARAMETER(1),
	INVALID_PARAMETER_COMBINATION(2),
	NO_SOURCE_FOLDER(3),
	SOURCE_FOLDER_NOT_FOUND(4),
	NO_DESTINATION_FOLDER(5),
	SOURCE_AND_DESTINATION_IDENTITY(6),
	DESTINATION_FOLDER_IS_SUBFOLDER_OF_SOURCE_FOLDER(7),
	SOURCE_FOLDER_CONTAINS_NON_DIRECTORY_FILES(8),
	NO_SIP_NAME(9),
	INVALID_SIP_NAME(10),
	NO_COLLECTION_NAME(11),
	INVALID_COLLECTION_NAME(12),
	COLLECTION_ALREADY_EXISTS(13),
	FILELIST_NOT_FOUND(14),
	FILELIST_READ_ERROR(15),
	SIPLIST_NOT_FOUND(16),
	SIPLIST_READ_ERROR(17),
	PREMIS_FILE_NOT_FOUND(18),
	RIGHTS_FILE_NOT_FOUND(19),
	RIGHTS_FILE_READ_ERROR(20),
	STANDARD_RIGHTS_FILE_READ_ERROR(21),
	COPY_ERROR(22),			
	PREMIS_ERROR(23),
	BAGIT_ERROR(24),
	ARCHIVE_ERROR(25),
	MOVE_TO_COLLECTION_FOLDER_ERROR(26),
	DELETE_TEMP_FOLDER_WARNING(27),
	ZERO_BYTES_ERROR(28),
	ABORT(29),
	GUI_ERROR(30);
	
	private int value;
	
	Feedback(int value) {
		this.value = value;
	}
	
	public int toInt() {
		return value;
	}	
}