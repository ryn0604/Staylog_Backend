package com.staylog.staylog.domain.mypage.service.impl;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staylog.staylog.domain.auth.dto.EmailVerificationDto;
import com.staylog.staylog.domain.auth.mapper.EmailMapper;
//import com.staylog.staylog.domain.auth.service.AuthService;
import com.staylog.staylog.domain.mypage.dto.MemberInfoDto;
import com.staylog.staylog.domain.mypage.dto.response.BookingInfoResponse;
import com.staylog.staylog.domain.mypage.dto.response.ReviewInfoResponse;
import com.staylog.staylog.domain.mypage.mapper.MypageMapper;
import com.staylog.staylog.domain.mypage.service.MypageService;
import com.staylog.staylog.global.common.code.ErrorCode;
import com.staylog.staylog.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 마이페이지 서비스 구현 클래스
 * @author 오미나
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MypageServiceImpl implements MypageService {

    private final MypageMapper mypageMapper;
    //비밀번호 변경 위해서 추가함:
    private final PasswordEncoder passwordEncoder;
    //private final AuthService authService;
    private final EmailMapper emailMapper;
    
    /**
     * 회원정보 조회
     * @param userId 조회할 회원의 고유번호(PK)
     * @return 회원의 상세정보 담은 MemberInfoResponse DTO
     * @throws BusinessException 회원정보가 존재하지 않을 경우 USER_NOT_FOUND 예외 발생
     */
    @Override
    public MemberInfoDto getMemberInfo(Long userId) {
        log.info("회원 정보 조회: userId={}", userId);
        
        MemberInfoDto dto = mypageMapper.selectMemberInfo(userId);
        if(dto == null) {
        	log.warn("회원 정보가 존재하지 않음: userId={}", userId);
        	throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return dto;
    }

    
    /**
     * 회원정보 수정
     * @param req 수정할 회원정보 담은 UpdateMemberInfoRequest DTO
     * @throws BusinessException 수정할 회원이 존재하지 않을 경우 USER_NOT_FOUND 예외 발생
     */    
    @Override
    public void updateMemberInfo(MemberInfoDto dto) {
        log.info("회원 정보 수정: userId={}", dto.getUserId());
        
        // 기존 이메일과 새 이메일이 다를 때만 인증을 검사하도록 조건을 추가
        // 기존 회원 정보 조회
        MemberInfoDto currentInfo = mypageMapper.selectMemberInfo(dto.getUserId());
        if (currentInfo == null) {
            log.warn("회원 정보가 존재하지 않음: userId={}", dto.getUserId());
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 이메일 변경 시에만 인증 여부 확인
        if (dto.getEmail() != null && !dto.getEmail().isEmpty() 
        		&& !dto.getEmail().equals(currentInfo.getEmail())){
        	
        	EmailVerificationDto verification = emailMapper.findVerificationByEmail(dto.getEmail());
        	if(verification == null || !"Y".equals(verification.getIsVerified())) {
        		log.warn("이메일 인증 미완료 상태에서 변경 시도: {}", dto.getEmail());
        		throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        	}
        	log.info("이메일 인증 완료 확인: {}", dto.getEmail());
        	// 인증 후 사용된 인증 기록 제거 후
        	emailMapper.deleteVerificationByEmail(dto.getEmail());
        }	
        
        // 비밀번호 변경 요청 시, 암호화 처리 (password 필드 1개를 재활용하여 비밀번호 변경 요청이 있을 경우만 암호화 처리)
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
        	log.info("비밀번호 변경 요청 확인 및 암호화 진행");
            String encodedPw = passwordEncoder.encode(dto.getPassword());
            dto.setPassword(encodedPw);
        }
        
        // DB에 업데이트 
        int updatedRows = mypageMapper.updateMemberInfo(dto);
        	if(updatedRows == 0) {
        		log.warn("회원 정보 수정 실패 - 해당 유저 없음: userId={}", dto.getUserId());
        		throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        	}
 
        	log.info("회원 정보 수정 완료: userId={}", dto.getUserId());
    }
    
    
    /**
     * 회원 탈퇴
     * @param userId 탈퇴 처리할 회원의 고유 번호
     * @throws BusinessException 해당 회원이 존재하지 않을 경우 USER_NOT_FOUND 예외 발생
     */
//    @Override
//    public void deleteMemberInfo(Long userId) {
//        log.info("회원 탈퇴 처리: userId={}", userId);
//        int deleted = mypageMapper.deleteMemberInfo(userId);
//        	if(deleted == 0) {
//        		throw new BusinessException(ErrorCode.USER_NOT_FOUND);
//        	}
//        	log.info("회원 탈퇴 완료: userId={}", userId);
//    }

 
    /**
     * 예약정보 조회
     * @param userId 예약내역을 조회할 회원의 고유 번호
     * @param status 예약상태 필터(upcoming, completed, canceled)
     * @return 조건에 해당하는 예약 목록 List
     */
    @Override
    public List<BookingInfoResponse> getBookings(Long userId, String status) {
        log.info("예약 내역 조회 시: userId={}, status={}", userId, status);
        List<BookingInfoResponse> bookings = null;
        try {
        	bookings = mypageMapper.selectBookings(userId, status);
        	log.info("예약 내역 조회 성공. 결과 개수: {}", bookings != null ? bookings.size() : 0);
        }catch (Exception e) {
           log.error("예약 내역 조회 중 오류 발생: userId={}, status={}", userId, status, e);
            // 필요하다면 예외를 다시 던지거나, 빈 리스트 반환 등 처리
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "예약 내역 조회 실패");
        }
        return bookings;
    }

    /**
     * 예약 상세 조회
     * 사용자의 특정 예약 건에 대한 상세 정보를 조회
     * 전달받은 userId(회원 PK)와 bookingId(예약 PK)를 기준으로 예약 상세 데이터를 조회
     * @param userId    예약 상세 정보를 조회할 회원의 고유 번호(PK)
     * @param bookingId 상세 조회할 예약의 고유 번호(PK)
     * @return 예약 상세 정보가 담긴 BookingInfoResponse DTO
     * @throws BusinessException 예약이 존재하지 않거나 본인 소유가 아닐 경우 BOOKING_NOT_FOUND 예외 발생
     */
     @Override
     public BookingInfoResponse getBookingDetail(Long userId, Long bookingId) {
         log.info("예약 상세 조회: userId={}, bookingId={}", userId, bookingId);
         BookingInfoResponse bookingDetail = mypageMapper.selectBookingDetail(userId, bookingId);
         if (bookingDetail == null) {
             // 본인의 예약이 아니거나, 존재하지 않는 예약일 경우
             throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
         }
         return bookingDetail;
     }
    
    /**
     * 리뷰 내역 조회
     * type 값에 따라 작성 가능한 리뷰(writable) 또는 내가 쓴 리뷰(written)를 구분
     * @param userId 리뷰 내역을 조회할 회원의 고유 번호
     * @param type 리뷰 구분 타입 (writable / written)
     * @return 조건에 해당하는 리뷰 목록 List
     */
    @Override
    public List<ReviewInfoResponse> getReviews(Long userId, String type) {
    	log.info("리뷰 내역 조회 시작: userId={}, type={}", userId, type);
        List<ReviewInfoResponse> reviews = null;
        try {
            reviews = mypageMapper.selectReviews(userId, type);
            log.info("리뷰 내역 조회 성공. 결과 개수: {}", reviews != null ? reviews.size() : 0);       
        } catch (Exception e) {
            log.error("리뷰 내역 조회 중 오류 발생: userId={}, type={}", userId, type, e);
            // 필요하다면 예외를 다시 던지거나, 빈 리스트 반환 등 처리
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "리뷰 내역 조회 실패");
        }
        return reviews;
    }

    /**
     * 문의내역 조회
     * type 값에 따라 FAQ, 내 문의 내역, 답변 대기 등의 목록을 구분
     * @param userId 문의 내역을 조회할 회원의 고유 번호
     * @param type 문의 구분 타입 (faq / my / waiting)
     * @return 조건에 해당하는 문의 목록 List
     */
//    @Override
//    public List<InquiryInfoResponse> getInquiries(Long userId, String type) {
//        log.info("문의 내역 조회: userId={}, type={}", userId, type);
//        return mypageMapper.selectInquiry(userId, type);
//    }
//
//    /**
//     * 1:1 문의 작성
//     * @param req 문의 제목, 내용, 작성자 정보가 담긴 InquiryWriteRequest DTO
//     */
//    @Override
//    public void writeInquiry(InquiryWriteRequest req) {
//        log.info("문의 작성: userId={}", req.getUserId());
//        mypageMapper.insertInquiry(req);
//    }
    
}

