package com.staylog.staylog.domain.image.service.impl;

import com.staylog.staylog.domain.image.dto.ImageData;
import com.staylog.staylog.domain.image.dto.ImageDto;
import com.staylog.staylog.domain.image.dto.ImageResponse;
import com.staylog.staylog.domain.image.dto.ImageUpdateItemDto;
import com.staylog.staylog.domain.image.dto.ImageUpdateRequest;
import com.staylog.staylog.domain.image.mapper.ImageMapper;
import com.staylog.staylog.domain.image.service.ImageService;
import com.staylog.staylog.global.common.code.ErrorCode;
import com.staylog.staylog.global.common.dto.FileUploadDto;
import com.staylog.staylog.global.common.util.FileUtil;
import com.staylog.staylog.global.common.util.MessageUtil;
import com.staylog.staylog.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final ImageMapper imageMapper;
    private final MessageUtil messageUtil;

    @Value("${file.image-location}")
    private String uploadPath;

    // displayOrder 계산 로직 보호용 락 객체 -> DB 잠금으로 대체되어 더 이상 필요 없음
//	private final Object displaObjectLock = new Object();
    
    @Override
    @Transactional
    public ImageResponse saveImages(List<MultipartFile> files, String targetType, long targetId){
    	log.info("ImageService.saveImages 호출됨 - targetType: {}, targetId: {}", targetType, targetId);
    	String prefixedTargetType = "IMG_FROM_"+targetType;
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
        

        // 필요한 displayOrder 개수만큼 한 번에 예약하고 시작 번호를 가져온다.
        long startOrder = DisplayOrderRange(prefixedTargetType, targetId, files.size());
        long currentDisplayOrder = startOrder;
        
        List<ImageDto> newImages = new ArrayList<>();
        
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue; // 빈 파일은 스킵
            }
            
            FileUploadDto fileUploadDto = null;
    		try {
    			fileUploadDto = FileUtil.saveFile(file, uploadPath);
    			log.info("저장된 파일 경로"+uploadPath);
	        } catch (IOException e) {
	            log.error("이미지 업로드 중 IO 오류 발생", e);
	            // GlobalExceptionHandler에서 처리하도록 예외를 다시 던집니다.
	            String message = messageUtil.getMessage(ErrorCode.FILE_UPLOAD_FAILED.getMessageKey());
	            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, message, e);
	        } catch (IllegalArgumentException e) {
	            log.error("이미지 업로드 인자 오류 발생", e);
	            // GlobalExceptionHandler에서 처리하도록 예외를 다시 던집니다.
	            String message = messageUtil.getMessage(ErrorCode.INVALID_FILE_TYPE.getMessageKey());
	            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, message, e);
	        }
    		
            ImageDto imageDto = ImageDto.builder()
                    .imageType("IMG_ORIGINAL")
                    .targetType(prefixedTargetType)
                    .targetId(targetId)
                    .savedUrl(fileUploadDto.getRelativePath()) // savedUrl 필드에 FileUtil에서 반환된 상대 경로 저장
                    .originalName(fileUploadDto.getOriginalName())
                    .fileSize(String.valueOf(file.getSize()))
                    .mimeType(file.getContentType())
                    .displayOrder(currentDisplayOrder++)
                    .build();

            imageMapper.insertImage(imageDto);
            // 방금 삽입된 ImageDto를 반환 리스트에 추가
            newImages.add(imageDto);
        }
        
        // 새로 업로드된 ImageDto 리스트를 ImageResponse 리스트로 변환하여 반환.
        return buildImageResponse(targetType, targetId, newImages);
    }

    @Override
    @Transactional(readOnly = true)
    public ImageResponse getImagesByTarget(String targetType, long targetId) {
    	String prefixedTargetType = "IMG_FROM_"+targetType;
        List<ImageDto> images = imageMapper.selectImagesByTarget(prefixedTargetType, targetId);
        
        return buildImageResponse(targetType, targetId, images);
    }

	

	@Override
	@Transactional
	public void deleteImage(long imageId) {
		// 삭제할 이미지 정보 조회
		ImageDto imageInfo = imageMapper.selectImageById(imageId);
		if (imageInfo == null) {
			// 이미 삭제되었거나 존재하지 않는 이미지 처리
			log.warn("삭제하려는 이미지가 존재하지 않음. imageId: "+ imageId);
			return; // 예외처리 예정
		}
		
		// 물리적 파일 삭제 (올바른 url 전달)
		String fullPath = Paths.get(uploadPath, imageInfo.getSavedUrl()).toString();
		log.info("물리적 파일 삭제 시도: "+fullPath);
		FileUtil.deleteFile(fullPath);
		
		// DB에서 이미지 레코드 삭제
		imageMapper.deleteImage(imageId);
		
		// displayOrder 재정렬
		imageInfo.setDeletedDisplayOrder(imageInfo.getDisplayOrder());
		imageMapper.updateDisplayOrderAfterDelete(imageInfo);
		
		// 카운터 감소
		imageMapper.decrementCounter(imageInfo);
		
		log.info("이미지 삭제 완료. imageId: "+imageId);
		
	}

	@Override
	@Transactional
	public void deleteImagesByTarget(String targetType, long targetId) {
		String prefixedTargetType = "IMG_FROM_"+targetType;
		
		// 삭제할 이미지 목록 조회
		List<ImageDto> imagesInfo = imageMapper.selectImagesByTarget(prefixedTargetType, targetId);
		
		// 물리삭제
		for (ImageDto image : imagesInfo) {
	        String fullPath = Paths.get(uploadPath, image.getSavedUrl()).toString();
	        FileUtil.deleteFile(fullPath);
		}
		
		// 이미지 레코드 일괄 삭제
		ImageDto dto = new ImageDto();
	    dto.setTargetType(prefixedTargetType);
	    dto.setTargetId(targetId);
	    imageMapper.deleteImagesByTarget(dto);
	    imageMapper.deleteCounterByTarget(dto);
	    
	    log.info("일괄 이미지 삭제 완료. target: "+targetType+targetId);
	}

	@Override
	@Transactional
	public void updateImages(ImageUpdateRequest request, List<MultipartFile> files) {
		log.info("이미지 일괄 업데이트 시작. target: "+request.getTargetType()+request.getTargetId());
		String prefixedTargetType = "IMG_FROM_" + request.getTargetType();
		
		// 1. DB 에 저장된 현재 이미지 목록 조회
		List<ImageDto> existingImages = imageMapper.selectImagesByTarget(prefixedTargetType, request.getTargetId());
		Map<Long, ImageDto> existingImageMap = existingImages.stream()
				.collect(Collectors.toMap(ImageDto::getImageId, image -> image));
		
		// 2. 요청으로 들어온 최종 이미지 ID 목록
	    Set<Long> requestedImageIds = request.getImageOrders().stream()
						             .map(ImageUpdateItemDto::getImageId)
						             .filter(Objects::nonNull)
						             .collect(Collectors.toSet());
	    
	    // 3. 삭제할 이미지 목록 계산 (기존 목록에는 있는데, 최종 요청 목록에는 없는 이미지)
	    List<ImageDto> imagesToDelete = existingImages.stream()
							            .filter(image -> !requestedImageIds.contains(image.getImageId()))
							            .collect(Collectors.toList());
	    
	    log.info("삭제할 이미지 개수: "+imagesToDelete.size());
	    
	    // 3-1. 삭제 로직 구현
	    if (!imagesToDelete.isEmpty()) {
		    for (ImageDto image : imagesToDelete) {
		        // 물리적 파일 삭제
		        String fullPath = Paths.get(uploadPath, image.getSavedUrl()).toString();
		        FileUtil.deleteFile(fullPath);
		        
		        // DB 레코드 삭제
		        imageMapper.deleteImage(image.getImageId());
		    }
		}
	    
	    // 4. 추가/수정할 이미지 목록 처리
	    log.info("추가/수정할 이미지 개수: "+request.getImageOrders().size());
	    int newFileIndex = 0;
	    for (ImageUpdateItemDto item : request.getImageOrders()) {
	    	if (item.getImageId() == null) {
	    		// 4-1. 신규 이미지 추가
	    		if (files == null || files.size() <= newFileIndex) {
	    			// TODO: 공통 예외처리로 넘길 예정
	    			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "요청된 신규 이미지 개수와 실제 파일 개수가 일치하지 않습니다.");
	    		}
	    		MultipartFile file = files.get(newFileIndex++);
	    		
	    		// file 이 null 이거나 비어있을 경우를 대비한 방어 코드
	            if (file == null || file.isEmpty()) {
	                log.warn("요청된 신규 이미지 순서에 해당하는 파일이 비어있습니다. index: ", newFileIndex - 1);
	                continue; // 다음 항목으로 넘어감
	            }
	    		try {
		            FileUploadDto fileUploadDto = FileUtil.saveFile(file, uploadPath);
		            ImageDto newImage = ImageDto.builder()
		                    .imageType("IMG_ORIGINAL")
		                    .targetType(prefixedTargetType)
		                    .targetId(request.getTargetId())
		                    .savedUrl(fileUploadDto.getRelativePath())
		                    .originalName(fileUploadDto.getOriginalName())
		                    .fileSize(String.valueOf(file.getSize()))
		                    .mimeType(file.getContentType())
		                    .displayOrder(item.getDisplayOrder())
		                    .build();
		            imageMapper.insertImage(newImage);
		        } catch (IOException e) {
		        	// TODO: 공통 예외처리로 넘길 예정
		            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "파일 업로드에 실패했습니다.", e);
		        }
	    	} else {
		        // 4-2. 기존 이미지 순서 업데이트
		        ImageDto existingImage = existingImageMap.get(item.getImageId());
		        if (existingImage != null && existingImage.getDisplayOrder() != item.getDisplayOrder()) {
		            // 순서가 변경된 경우에만 UPDATE
		            existingImage.setDisplayOrder(item.getDisplayOrder());
		            imageMapper.updateImageDisplayOrder(existingImage);
		        }
		    }
	    }
	
	    // 5. 최종 카운터 업데이트
		long finalImageCount = request.getImageOrders().size();
		
		ImageDto counterDto = new ImageDto();
	    counterDto.setTargetType(prefixedTargetType);
	    counterDto.setTargetId(request.getTargetId());
		   
	    // 카운터가 존재하는지 확인 후, 없으면 생성/삭제, 있으면 업데이트
	    Long currentCounter = imageMapper.selectForUpdate(counterDto);
	    
	    if (currentCounter != null) {
	        if (finalImageCount > 0) {
	            // 카운터가 있고, 최종 이미지도 있으면 -> 카운터 값 업데이트
	        	counterDto.setCounterValue(finalImageCount);
	        	imageMapper.setCounterValue(counterDto);
	        } else {
	            // 카운터는 있는데, 최종 이미지가 없으면 -> 카운터 레코드 삭제
	        	imageMapper.deleteCounterByTarget(counterDto);
	        }
	    } else if (finalImageCount > 0) {
	        // 카운터가 없고, 최종 이미지가 있으면 -> 카운터 레코드 생성
	    	counterDto.setCounterValue(finalImageCount);
	    	imageMapper.createCounter(counterDto);
	    }
	    // 카운터도 없고, 최종 이미지도 없으면 -> 아무것도 안 함
	   
	    log.info("이미지 일괄 업데이트 완료. 최종 이미지 개수: "+finalImageCount);
	    
	}
	
	@Override
    @Transactional(readOnly = true)
	public Map<Long, ImageResponse> getImagesByTargets(String targetType, List<Long> targetIds) {
		String prefixedTargetType = "IMG_FROM_" + targetType;
		if (targetIds == null || targetIds.isEmpty()) {
			return new HashMap<>();
		}

        List<ImageDto> allImages = imageMapper.selectImagesByTargetIds(prefixedTargetType, targetIds);

        // targetId를 기준으로 ImageDto를 그룹화합니다.
        Map<Long, List<ImageDto>> groupedImages = allImages.stream()
                .collect(Collectors.groupingBy(ImageDto::getTargetId));

        Map<Long, ImageResponse> resultMap = new HashMap<>();
        for (Map.Entry<Long, List<ImageDto>> entry : groupedImages.entrySet()) {
            resultMap.put(entry.getKey(), buildImageResponse(targetType, entry.getKey(), entry.getValue()));
        }
        return resultMap;

	}
	
	/**
	 * 필요한 개수(count)만큼의 displayOrder 범위를 예약하고, 시작 번호를 반환.
	 * 
	 * @param targetType 접두사가 붙은 targetType
	 * @param targetId
	 * @param count 예약할 displayOrder의 개수
	 * @return 예약된 displayOrder의 시작 번호
	 */
	@SuppressWarnings("unused")
	private long DisplayOrderRange(String targetType, long targetId, int count) {
		ImageDto dto = new ImageDto();
		dto.setTargetType(targetType);
		dto.setTargetId(targetId);
		
		while(true) {
			// 1. 행을 잠그고 현재 카운터 값을 조회
			Long startOrder = imageMapper.selectForUpdate(dto);
			
			if (startOrder != null) {
				// 2a. 카운터가 존재하면, 필요한 만큼(count) 증가시킨 값을 DB에 저장
				dto.setCounterValue(startOrder + count);
				imageMapper.updateCounter(dto);
				return startOrder; // 시작 번호 반환
			} else {
				// 2b. 카운터가 없으면 새로 생성
				try {
					dto.setCounterValue(1L + count);
					imageMapper.createCounter(dto);
					return 1L; // 시작 번호는 1
				} catch (DuplicateKeyException e) {
					// 3. INSERT가 실패하면 (다른 트랜잭션이 방금 생성), 다시 시도
					log.warn("Race condition detected on counter creation for {}-{}, retrying.", targetType, targetId);
					// 다음 루프에서는 이미 생성된 카운터를 조회(selectForUpdate)하여 처리함.
					continue;
				}
			}
		}
	}
	
	/**
	 * 
	 * @param targetType
	 * @param targetId
	 * @param imageDtos
	 * @return
	 */
	private ImageResponse buildImageResponse(String targetType, long targetId, List<ImageDto> imageDtos) {
		List<ImageData> imageDataList = imageDtos.stream()
			.map(image -> ImageData.builder()
	            .imageId(image.getImageId())
	            .imageUrl("/images/" + image.getSavedUrl())
	            .displayOrder(image.getDisplayOrder())
	            .originalName(image.getOriginalName())
	            .build())
			.collect(Collectors.toList());
		
		return ImageResponse.builder()
	        .targetType(targetType)
	        .targetId(targetId)
	        .images(imageDataList)
	        .build();
	}

	@Override
	@Transactional
	public String uploadProfileImage(MultipartFile file, String targetType, Long targetId) {
		// 기존 프로필 이미지가 있는지 확인하고, 있다면 삭제
		deleteImagesByTarget(targetType, targetId);
		String prefixedTargetType = "IMG_FROM_"+targetType;
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
		// 새 이미지 업로드
		FileUploadDto fileUploadDto = null;
		try {
			fileUploadDto = FileUtil.saveFile(file, uploadPath);
			log.info("저장된 파일 경로"+uploadPath);
        } catch (IOException e) {
            log.error("이미지 업로드 중 IO 오류 발생", e);
            // GlobalExceptionHandler에서 처리하도록 예외를 다시 던집니다.
            String message = messageUtil.getMessage(ErrorCode.FILE_UPLOAD_FAILED.getMessageKey());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, message, e);
        } catch (IllegalArgumentException e) {
            log.error("이미지 업로드 인자 오류 발생", e);
            // GlobalExceptionHandler에서 처리하도록 예외를 다시 던집니다.
            String message = messageUtil.getMessage(ErrorCode.INVALID_FILE_TYPE.getMessageKey());
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, message, e);
        }
		String savedUrl = fileUploadDto.getRelativePath();
		
		// DB에 새 이미지 정보 저장
		ImageDto imageDto = ImageDto.builder()
                .imageType("IMG_ORIGINAL")
                .targetType(prefixedTargetType)
                .targetId(targetId)
                .savedUrl(fileUploadDto.getRelativePath()) // savedUrl 필드에 FileUtil에서 반환된 상대 경로 저장
                .originalName(fileUploadDto.getOriginalName())
                .fileSize(String.valueOf(file.getSize()))
                .mimeType(file.getContentType())
                .displayOrder(1)
                .build();
		imageMapper.insertImage(imageDto);
				
		return "/images/"+savedUrl;
	}

}