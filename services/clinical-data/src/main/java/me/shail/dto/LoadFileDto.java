package me.shail.dto;

import me.shail.model.LoadFile;

/**
 * @param rowCount     rows copied into the raw table
 * @param rejectedRows rows the merge could not promote into dbo
 */
public record LoadFileDto(String fileName, String checksum, Long fileSize, Integer rowCount, long rejectedRows) {

    public static LoadFileDto from(LoadFile file, long rejectedRows) {
        return file == null ? null
                : new LoadFileDto(file.fileName, file.checksum, file.fileSize, file.rowCount, rejectedRows);
    }
}
