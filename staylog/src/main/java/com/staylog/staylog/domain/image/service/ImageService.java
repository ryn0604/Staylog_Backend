package com.staylog.staylog.domain.image.service;

import com.staylog.staylog.domain.image.dto.ImageDto;
import com.staylog.staylog.domain.image.dto.ImageDto;
import com.staylog.staylog.domain.image.dto.ImageResponse;
import com.staylog.staylog.domain.image.dto.ImageUpdateRequest;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 이미지 저장 서비스 인터페이스
 * @author 고윤제
 */
public interface ImageService {
    ImageResponse saveImages(List<MultipartFile> files, String targetType, long targetId);
    void deleteImage(long imageId);
    /**
     * 지정된 target 에 속한 모든 이미지 일괄 삭제
     * @param targetType 이미지의 부모 엔티티 타입 (e.g., "BOARD", "STAY")
     * @param targetId 이미지의 부모 엔티티 ID
     */
    void deleteImagesByTarget(String targetType, long targetId);
    /**
    * 이미지 목록을 일괄적으로 업데이트. (추가, 삭제, 순서 변경)
    * @param request 업데이트할 이미지 정보 Dto
    * @param files 새로 추가할 이미지 파일 목록
    */
    void updateImages(ImageUpdateRequest request, List<MultipartFile> files);
    ImageResponse getImagesByTarget(String targetType, long targetId);

    /**
     * 여러 대상(targetId)에 대한 이미지 목록을 한 번에 조회합니다.
     * N+1 쿼리 문제를 해결하기 위해 사용됩니다.
     * @param targetType 이미지의 부모 엔티티 타입 (e.g., "BOARD", "ACCOMMODATION")
     * @param targetIds 조회할 대상 ID 목록
     * @param <T> targetId의 타입
     * @return 각 targetId에 해당하는 ImageResponse 맵
     */
    Map<Long, ImageResponse> getImagesByTargets(String targetType, List<Long> targetIds);
    
    /**
     * 프로필 이미지를 업로드(등록/수정)합니다.
     * @param file 업로드할 이미지 파일
     * @param targetType 이미지 타입 (e.g., "USER_PROFILE")
     * @param targetId 사용자 ID
     * @return 업로드된 이미지 URL (String)
     */
    String uploadProfileImage(MultipartFile file, String targetType, Long targetId);

}
