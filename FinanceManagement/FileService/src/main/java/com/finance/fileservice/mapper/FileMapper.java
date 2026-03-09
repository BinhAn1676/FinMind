package com.finance.fileservice.mapper;

import com.finance.fileservice.dto.FileDto;
import com.finance.fileservice.entity.FileEntity;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {
    
    public FileDto mapToDto(FileEntity fileEntity) {
        if (fileEntity == null) {
            return null;
        }
        
        FileDto dto = new FileDto();
        dto.setId(fileEntity.getId());
        dto.setUserId(fileEntity.getUserId());
        dto.setPurpose(fileEntity.getPurpose());
        dto.setFileName(fileEntity.getFileName());
        dto.setOriginalFileName(fileEntity.getOriginalFileName());
        dto.setFileType(fileEntity.getFileType());
        dto.setFileSize(fileEntity.getFileSize());
        dto.setFileUrl(fileEntity.getFileUrl());
        dto.setDescription(fileEntity.getDescription());
        dto.setCreatedAt(fileEntity.getCreatedAt());
        dto.setUpdatedAt(fileEntity.getUpdatedAt());
        
        return dto;
    }
    
    public FileEntity mapToEntity(FileDto fileDto) {
        if (fileDto == null) {
            return null;
        }
        
        FileEntity entity = new FileEntity();
        entity.setId(fileDto.getId());
        entity.setUserId(fileDto.getUserId());
        entity.setPurpose(fileDto.getPurpose());
        entity.setFileName(fileDto.getFileName());
        entity.setOriginalFileName(fileDto.getOriginalFileName());
        entity.setFileType(fileDto.getFileType());
        entity.setFileSize(fileDto.getFileSize());
        entity.setFileUrl(fileDto.getFileUrl());
        entity.setDescription(fileDto.getDescription());
        entity.setCreatedAt(fileDto.getCreatedAt());
        entity.setUpdatedAt(fileDto.getUpdatedAt());
        
        return entity;
    }
}

